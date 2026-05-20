package com.soll.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.soll.domain.device.AquikProvisioningDefaults
import com.soll.domain.device.GadgetDiscoveryCandidate
import com.soll.domain.device.GadgetDiscoveryContract
import com.soll.domain.device.GadgetDiscoveryMethod
import com.soll.domain.device.GadgetDiscoveryPayloadParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class GadgetDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    fun scan(method: GadgetDiscoveryMethod): Flow<GadgetDiscoveryCandidate> =
        when (method) {
            GadgetDiscoveryMethod.LAN_MDNS -> scanMdns()
            GadgetDiscoveryMethod.LAN_SSDP -> scanSsdp()
            GadgetDiscoveryMethod.WIFI_AP -> scanSetupAccessPoints()
            GadgetDiscoveryMethod.QR,
            GadgetDiscoveryMethod.MANUAL,
            GadgetDiscoveryMethod.BLE_PLANNED,
            GadgetDiscoveryMethod.SMARTCONFIG_PLANNED -> flow { }
        }

    suspend fun discoverManual(rawHost: String): GadgetDiscoveryCandidate {
        val endpoint = com.soll.domain.device.DeviceEndpoint.normalize(rawHost, 81, "ws")
        return fetchDeviceJsonCandidate(
            host = endpoint.host,
            method = GadgetDiscoveryMethod.MANUAL,
            fallbackPort = endpoint.port,
            fallbackPath = endpoint.path.ifBlank { "ws" },
        ).getOrElse {
            GadgetDiscoveryCandidate(
                id = endpoint.deviceId(com.soll.domain.device.AquikDeviceProfile.ID),
                displayName = "Гаджет ${endpoint.host}",
                method = GadgetDiscoveryMethod.MANUAL,
                host = endpoint.host,
                port = endpoint.port,
                path = endpoint.path.ifBlank { "ws" },
            )
        }
    }

    private fun scanMdns(timeoutMs: Long = MDNS_TIMEOUT_MS): Flow<GadgetDiscoveryCandidate> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        val seen = ConcurrentHashMap.newKeySet<String>()
        var stopped = false

        MDNS_SERVICE_TYPES.forEach { serviceType ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) = Unit
                override fun onDiscoveryStopped(type: String) = Unit
                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolveNsdService(nsdManager, serviceInfo) { resolved ->
                        launch(Dispatchers.IO) {
                            val host = resolved.host?.hostAddress ?: return@launch
                            val candidate = buildNsdCandidate(resolved, host)
                            val httpPort = resolved.attributes.httpPort() ?: resolved.port.takeIf { it > 0 }
                            val enriched = fetchDeviceJsonCandidate(
                                host = host,
                                method = GadgetDiscoveryMethod.LAN_MDNS,
                                fallbackPort = candidate.port,
                                fallbackPath = candidate.path,
                                httpPort = httpPort,
                            ).getOrDefault(candidate)
                            if (seen.add(enriched.id)) trySend(enriched)
                        }
                    }
                }

                override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                    close(IOException("mDNS не запустился: $errorCode"))
                }

                override fun onStopDiscoveryFailed(type: String, errorCode: Int) = Unit
            }
            listeners += listener
            runCatching {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { error ->
                if (listeners.size == 1) close(error)
            }
        }

        launch {
            delay(timeoutMs)
            if (!stopped) {
                stopped = true
                listeners.forEach { listener ->
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
                close()
            }
        }

        awaitClose {
            if (!stopped) {
                stopped = true
                listeners.forEach { listener ->
                    runCatching { nsdManager.stopServiceDiscovery(listener) }
                }
            }
        }
    }

    private fun resolveNsdService(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo,
        onResolved: (NsdServiceInfo) -> Unit,
    ) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceResolved(info: NsdServiceInfo) = onResolved(info)
        }
        runCatching { nsdManager.resolveService(serviceInfo, listener) }
    }

    private fun buildNsdCandidate(serviceInfo: NsdServiceInfo, host: String): GadgetDiscoveryCandidate =
        GadgetDiscoveryPayloadParser.candidateFromNsdService(
            serviceName = serviceInfo.serviceName,
            attributes = serviceInfo.attributes,
            host = host,
            port = serviceInfo.port.takeIf { it > 0 } ?: 81,
            method = GadgetDiscoveryMethod.LAN_MDNS,
        )

    private fun scanSsdp(timeoutMs: Long = SSDP_TIMEOUT_MS): Flow<GadgetDiscoveryCandidate> = flow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifiManager.createMulticastLock("SollGadgetSSDP").apply { setReferenceCounted(false) }
        val seen = mutableSetOf<String>()
        try {
            runCatching { lock.acquire() }
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_SOCKET_TIMEOUT_MS
                val packetBytes = SSDP_SEARCH_REQUEST.toByteArray()
                val address = InetAddress.getByName(SSDP_MULTICAST_ADDRESS)
                val startedAt = System.currentTimeMillis()
                while (System.currentTimeMillis() - startedAt < timeoutMs) {
                    val searchPacket = DatagramPacket(packetBytes, packetBytes.size, address, SSDP_PORT)
                    socket.send(searchPacket)
                    val receiveUntil = System.currentTimeMillis() + SSDP_SOCKET_TIMEOUT_MS
                    while (System.currentTimeMillis() < receiveUntil) {
                        val buffer = ByteArray(4096)
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(responsePacket)
                        } catch (_: SocketTimeoutException) {
                            break
                        }
                        val message = String(responsePacket.data, 0, responsePacket.length)
                        if (!message.looksLikeSollGadgetSsdp()) continue
                        val headers = GadgetDiscoveryPayloadParser.parseSsdpHeaders(message)
                        val sourceHost = responsePacket.address.hostAddress ?: continue
                        val fromHeaders = GadgetDiscoveryPayloadParser.candidateFromSsdpHeaders(headers, sourceHost)
                        val deviceJsonUrl = headers["LOCATION"]?.let(GadgetDiscoveryPayloadParser::deviceJsonUrlFromSsdpLocation)
                        val candidate = fetchDeviceJsonCandidate(
                            host = fromHeaders.host ?: sourceHost,
                            method = GadgetDiscoveryMethod.LAN_SSDP,
                            fallbackPort = fromHeaders.port,
                            fallbackPath = fromHeaders.path,
                            deviceJsonUrl = deviceJsonUrl,
                        ).getOrDefault(fromHeaders)
                        if (seen.add(candidate.id)) emit(candidate)
                    }
                    delay(SSDP_SEARCH_INTERVAL_MS)
                }
            }
        } finally {
            runCatching { if (lock.isHeld) lock.release() }
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun scanSetupAccessPoints(timeoutMs: Long = WIFI_SCAN_DELAY_MS): Flow<GadgetDiscoveryCandidate> = flow {
        if (!canReadWifiScanResults()) {
            throw SecurityException("Для поиска AP нужны разрешения Wi-Fi/геолокации и включенная геолокация на телефоне.")
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiManager.startScan()
        delay(timeoutMs)
        @Suppress("DEPRECATION")
        val networks = wifiManager.scanResults
            .filter { result -> result.SSID.matchesSetupSsid() }
            .groupBy { it.SSID }
            .map { (_, results) -> results.maxByOrNull { it.level } ?: results.first() }
            .sortedByDescending { it.level }
        networks.forEach { result ->
            emit(
                GadgetDiscoveryCandidate(
                    id = "ap:${result.SSID}:${result.BSSID}".lowercase(),
                    displayName = result.SSID,
                    method = GadgetDiscoveryMethod.WIFI_AP,
                    host = null,
                    port = 81,
                    path = "ws",
                    apSsid = result.SSID,
                    rssi = result.level,
                    capabilities = listOf("WIFI_AP", "PROVISIONING"),
                    rawJson = result.capabilities.orEmpty(),
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun canReadWifiScanResults(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return fineLocation && nearbyWifi
    }

    private suspend fun fetchDeviceJsonCandidate(
        host: String,
        method: GadgetDiscoveryMethod,
        fallbackPort: Int,
        fallbackPath: String,
        httpPort: Int? = null,
        deviceJsonUrl: String? = null,
    ): Result<GadgetDiscoveryCandidate> = runCatching {
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            for (url in deviceJsonUrls(host = host, httpPort = httpPort, explicitUrl = deviceJsonUrl)) {
                runCatching {
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}")
                        }
                        val body = response.body?.string().orEmpty()
                        GadgetDiscoveryPayloadParser.candidateFromDeviceJson(
                            jsonText = body,
                            fallbackHost = host,
                            method = method,
                            fallbackPort = fallbackPort,
                            fallbackPath = fallbackPath,
                        )
                    }
                }.onSuccess { candidate ->
                    return@withContext candidate
                }.onFailure { error -> lastError = error }
            }
            throw IOException("device.json не найден", lastError)
        }
    }

    private fun deviceJsonUrls(
        host: String,
        httpPort: Int?,
        explicitUrl: String?,
    ): List<String> {
        val cleanHost = host.trim().trimEnd('/')
        return buildList {
            explicitUrl?.takeIf { it.isNotBlank() }?.let(::add)
            httpPort
                ?.takeIf { it > 0 && it != 80 }
                ?.let { add("http://$cleanHost:$it/device.json") }
            add("http://$cleanHost/device.json")
        }.distinct()
    }

    private fun String?.matchesSetupSsid(): Boolean {
        val value = this?.trim().orEmpty()
        return GadgetDiscoveryContract.setupSsidPrefixes.any { prefix -> value.startsWith(prefix, ignoreCase = true) } ||
            value.equals(AquikProvisioningDefaults.setupApSsid, ignoreCase = true)
    }

    private fun String.looksLikeSollGadgetSsdp(): Boolean {
        val lower = lowercase()
        return lower.contains("aquik") ||
            lower.contains("soll") ||
            lower.contains("x-device-id") ||
            lower.contains("urn:aquik-iot") ||
            lower.contains("urn:aquik:device:controller:1") ||
            lower.contains("schemas-aquik") ||
            lower.contains("urn:soll")
    }

    private fun Map<String, ByteArray>.httpPort(): Int? {
        val decoded = mapValues { (_, value) -> value.decodeToString() }
        return decoded["http_port"]?.toIntOrNull()
            ?: decoded["httpPort"]?.toIntOrNull()
    }

    private companion object {
        val MDNS_SERVICE_TYPES = GadgetDiscoveryContract.mdnsServiceTypes
        const val MDNS_TIMEOUT_MS = 10_000L
        const val SSDP_TIMEOUT_MS = 8_000L
        const val WIFI_SCAN_DELAY_MS = 3_500L
        const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val SSDP_SOCKET_TIMEOUT_MS = 1_000
        const val SSDP_SEARCH_INTERVAL_MS = 1_500L
        val SSDP_SEARCH_REQUEST = """
            M-SEARCH * HTTP/1.1
            HOST: $SSDP_MULTICAST_ADDRESS:$SSDP_PORT
            MAN: "ssdp:discover"
            MX: 2
            ST: ssdp:all


        """.trimIndent().replace("\n", "\r\n")
    }
}
