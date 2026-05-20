package com.soll.domain.device

import com.soll.domain.scanner.ScannerDevicePairingParser
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

enum class GadgetScreenMode {
    DEVICE_LIST,
    DISCOVERY,
    DEVICE_DETAIL,
}

enum class GadgetDeviceDetailTab(val title: String) {
    SENSORS("Датчики"),
    CONTROL("Управление"),
    PARAMETERS("Параметры"),
    SCHEDULES("Расписания"),
    AUTOMATION("Авто"),
    DIAGNOSTICS("Диагностика"),
    EVENTS("События"),
}

enum class GadgetDiscoveryMethod(
    val title: String,
    val shortTitle: String,
    val description: String,
    val planned: Boolean = false,
) {
    LAN_MDNS(
        title = "mDNS / NSD",
        shortTitle = "mDNS",
        description = "Поиск Soll/Aquik устройств в текущей локальной сети.",
    ),
    LAN_SSDP(
        title = "SSDP",
        shortTitle = "SSDP",
        description = "UPnP-поиск в локальной сети, если mDNS недоступен.",
    ),
    WIFI_AP(
        title = "Wi-Fi AP",
        shortTitle = "AP",
        description = "Поиск точек настройки AQUIK/Soll рядом с телефоном.",
    ),
    QR(
        title = "QR / код",
        shortTitle = "QR",
        description = "Вставка QR-кода или строки привязки устройства.",
    ),
    MANUAL(
        title = "Вручную",
        shortTitle = "IP",
        description = "Проверка устройства по IP, host или WebSocket URL.",
    ),
    BLE_PLANNED(
        title = "BLE",
        shortTitle = "BLE",
        description = "Bluetooth-привязка будет включена после проверки прошивки и GATT-контракта.",
        planned = true,
    ),
    SMARTCONFIG_PLANNED(
        title = "SmartConfig",
        shortTitle = "Smart",
        description = "SmartConfig оставлен как подготовленный канал, но не основной рабочий поток.",
        planned = true,
    ),
}

object GadgetDiscoveryContract {
    const val VERSION = "soll-gadget-discovery-v1"
    val primaryOrder = listOf(
        GadgetDiscoveryMethod.LAN_MDNS,
        GadgetDiscoveryMethod.LAN_SSDP,
        GadgetDiscoveryMethod.WIFI_AP,
        GadgetDiscoveryMethod.QR,
        GadgetDiscoveryMethod.MANUAL,
    )
    val mdnsServiceTypes = listOf("_soll-gadget._tcp", "_aquik._tcp", "_ws._tcp")
    val setupSsidPrefixes = listOf("AQUIK_", "AQUIK-", "SOLL_", "SOLL-", "Soll-")
    const val defaultSetupHost = AquikProvisioningDefaults.setupApHost
    const val deviceJsonPath = "/device.json"
}

data class GadgetDiscoveryCandidate(
    val id: String,
    val displayName: String,
    val profileId: String = AquikDeviceProfile.ID,
    val method: GadgetDiscoveryMethod,
    val host: String? = null,
    val port: Int = 81,
    val path: String = "ws",
    val token: String = "",
    val chip: String? = null,
    val firmware: String? = null,
    val mac: String? = null,
    val apSsid: String? = null,
    val rssi: Int? = null,
    val capabilities: List<String> = emptyList(),
    val rawJson: String = "",
    val discoveredAt: Long = System.currentTimeMillis(),
) {
    val canAdd: Boolean
        get() = !host.isNullOrBlank()

    fun endpointText(): String =
        host?.let { DeviceEndpoint.normalize(it, port, path).url() }
            ?: apSsid
            ?: "endpoint не определен"

    fun toConnectionConfig(profile: DeviceProfile): DeviceConnectionConfig {
        val candidateHost = requireNotNull(host?.takeIf { it.isNotBlank() }) {
            "У кандидата нет host для подключения"
        }
        val endpoint = DeviceEndpoint.normalize(candidateHost, port, path)
        return DeviceConnectionConfig(
            profile = profile,
            host = endpoint.storageHost(),
            port = endpoint.port,
            path = endpoint.path.ifBlank { "ws" },
            token = token,
        )
    }
}

object GadgetDiscoveryPayloadParser {
    fun candidateFromDeviceJson(
        jsonText: String,
        fallbackHost: String,
        method: GadgetDiscoveryMethod,
        fallbackPort: Int = 81,
        fallbackPath: String = "ws",
    ): GadgetDiscoveryCandidate {
        val root = JSONObject(jsonText.ifBlank { "{}" })
        val websocketEndpoint = root.optString("websocketUrl")
            .ifBlank { root.optString("webSocketUrl") }
            .ifBlank { root.optString("wsUrl") }
        val endpoint = if (websocketEndpoint.isNotBlank()) {
            DeviceEndpoint.normalize(websocketEndpoint, fallbackPort, fallbackPath)
        } else {
            DeviceEndpoint.normalize(
                host = root.optString("ipAddress").ifBlank { root.optString("ip") }.ifBlank { fallbackHost },
                port = root.optInt(
                    "wsPort",
                    root.optInt(
                        "ws_port",
                        root.optInt(
                            "websocketPort",
                            root.optInt("websocket_port", fallbackPort),
                        ),
                    ),
                ),
                path = root.optString("path").ifBlank { fallbackPath },
            )
        }
        val profileId = normalizeProfileId(
            root.optString("profileId")
                .ifBlank { root.optString("profile") }
                .ifBlank { root.optString("deviceProfile") }
        )
        val deviceId = root.optString("deviceId")
            .ifBlank { root.optString("id") }
            .ifBlank { root.optString("device_id") }
            .ifBlank { "${profileId}:${endpoint.host}:${endpoint.port}" }
        val name = root.optString("deviceName")
            .ifBlank { root.optString("name") }
            .ifBlank { root.optString("hostname") }
            .ifBlank { deviceId }
        val chip = root.optString("boardType")
            .ifBlank { root.optString("chipType") }
            .ifBlank { root.optString("chip") }
            .ifBlank { null }
        val firmware = root.optString("firmwareVersion")
            .ifBlank { root.optString("version") }
            .ifBlank { null }
        val mac = root.optString("macAddress")
            .ifBlank { root.optString("mac") }
            .ifBlank { null }
        return GadgetDiscoveryCandidate(
            id = stableCandidateId(profileId, deviceId, endpoint.host, mac),
            displayName = name,
            profileId = profileId,
            method = method,
            host = endpoint.host,
            port = endpoint.port,
            path = endpoint.path.ifBlank { fallbackPath },
            token = root.optString("token"),
            chip = chip,
            firmware = firmware,
            mac = mac,
            apSsid = root.optString("apSSID").ifBlank { root.optString("apSsid") }.ifBlank { null },
            rssi = root.optNullableInt("rssi"),
            capabilities = root.optStringList("capabilities"),
            rawJson = root.toString(),
        )
    }

    fun candidateFromPairingText(rawValue: String): GadgetDiscoveryCandidate? {
        parseAquikSetupQr(rawValue)?.let { return it }
        val payload = ScannerDevicePairingParser.parse(rawValue) ?: return null
        val profileId = normalizeProfileId(payload.profileId)
        val endpoint = DeviceEndpoint.normalize(payload.host, payload.port, payload.path)
        return GadgetDiscoveryCandidate(
            id = stableCandidateId(profileId, endpoint.deviceId(profileId), endpoint.host, null),
            displayName = "Гаджет ${endpoint.host}",
            profileId = profileId,
            method = GadgetDiscoveryMethod.QR,
            host = endpoint.host,
            port = endpoint.port,
            path = endpoint.path.ifBlank { "ws" },
            token = payload.token,
            rawJson = rawValue,
        )
    }

    fun candidateFromNsdService(
        serviceName: String,
        attributes: Map<String, ByteArray>,
        host: String,
        port: Int,
        method: GadgetDiscoveryMethod,
    ): GadgetDiscoveryCandidate {
        val decoded = attributes.mapValues { (_, value) -> value.decodeToString() }
        val profileId = normalizeProfileId(decoded["profileId"] ?: decoded["profile"] ?: decoded["profile_id"].orEmpty())
        val wsPort = decoded["ws_port"]?.toIntOrNull()
            ?: decoded["wsPort"]?.toIntOrNull()
            ?: decoded["websocketPort"]?.toIntOrNull()
            ?: decoded["websocket_port"]?.toIntOrNull()
            ?: port.takeIf { it > 0 }
            ?: 81
        val path = decoded["path"].orEmpty().ifBlank { "ws" }
        val deviceId = decoded["id"]
            ?: decoded["deviceId"]
            ?: decoded["device_id"]
            ?: "$profileId:$host:$wsPort"
        return GadgetDiscoveryCandidate(
            id = stableCandidateId(profileId, deviceId, host, decoded["mac"]),
            displayName = decoded["name"] ?: decoded["deviceName"] ?: serviceName.ifBlank { deviceId },
            profileId = profileId,
            method = method,
            host = host,
            port = wsPort,
            path = path,
            chip = decoded["board"] ?: decoded["model"] ?: decoded["chip"] ?: decoded["chipType"],
            firmware = decoded["version"] ?: decoded["fw"] ?: decoded["firmwareVersion"],
            mac = decoded["mac"],
            capabilities = decoded["capabilities"]?.split(',', ';')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty(),
            rawJson = JSONObject(decoded).toString(),
        )
    }

    fun candidateFromSsdpHeaders(headers: Map<String, String>, sourceHost: String): GadgetDiscoveryCandidate {
        val profileId = normalizeProfileId(headers["X-PROFILE-ID"] ?: headers["X-SOLL-PROFILE"].orEmpty())
        val location = headers["LOCATION"].orEmpty()
        val locationEndpoint = location.takeIf { it.isNotBlank() }?.let(::endpointFromHttpLocation)
        val host = locationEndpoint?.host ?: sourceHost
        val wsPort = headers["X-WS-PORT"]?.toIntOrNull()
            ?: headers["X-WEBSOCKET-PORT"]?.toIntOrNull()
            ?: headers["AQUIK-WS-PORT"]?.toIntOrNull()
            ?: headers["AQUIK-WEBSOCKET-PORT"]?.toIntOrNull()
            ?: 81
        val path = (headers["X-WS-PATH"] ?: headers["AQUIK-WS-PATH"]).orEmpty().ifBlank { "ws" }
        val mac = headers["X-MAC-ADDRESS"] ?: headers["AQUIK-MAC"] ?: headers["X-AQUIK-MAC"]
        val chip = headers["X-BOARD-TYPE"] ?: headers["AQUIK-CHIP-TYPE"] ?: headers["X-AQUIK-CHIP-TYPE"]
        val firmware = headers["X-FIRMWARE-VERSION"] ?: headers["AQUIK-FW-VERSION"] ?: headers["X-AQUIK-FW-VERSION"]
        val deviceId = headers["X-DEVICE-ID"]
            ?: headers["AQUIK-DEVICE-ID"]
            ?: headers["X-AQUIK-DEVICE-ID"]
            ?: headers["USN"]?.substringAfter("uuid:")?.substringBefore("::")
            ?: "$profileId:$host:$wsPort"
        val capabilities = (headers["X-CAPABILITIES"] ?: headers["AQUIK-CAPABILITIES"] ?: headers["X-AQUIK-CAPABILITIES"])
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("WIFI", "WEBSOCKET", "SSDP")
        return GadgetDiscoveryCandidate(
            id = stableCandidateId(profileId, deviceId, host, mac),
            displayName = headers["X-DEVICE-NAME"] ?: headers["AQUIK-DEVICE-NAME"] ?: deviceId,
            profileId = profileId,
            method = GadgetDiscoveryMethod.LAN_SSDP,
            host = host,
            port = wsPort,
            path = path,
            chip = chip,
            firmware = firmware,
            mac = mac,
            capabilities = capabilities,
            rawJson = JSONObject(headers).toString(),
        )
    }

    fun parseSsdpHeaders(message: String): Map<String, String> =
        message
            .lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) return@mapNotNull null
                line.substring(0, index).trim().uppercase() to line.substring(index + 1).trim()
            }
            .toMap()

    fun deviceJsonUrlFromSsdpLocation(location: String): String? {
        val uri = runCatching { URI(location) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val scheme = uri.scheme?.takeIf { it.equals("https", ignoreCase = true) }?.lowercase() ?: "http"
        val port = uri.port
            .takeIf { it > 0 && !((scheme == "http" && it == 80) || (scheme == "https" && it == 443)) }
            ?.let { ":$it" }
            .orEmpty()
        return "$scheme://$host$port/device.json"
    }

    private fun endpointFromHttpLocation(location: String): DeviceEndpoint? {
        val uri = runCatching { URI(location) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        return DeviceEndpoint.normalize(host, 81, "ws")
    }

    private fun parseAquikSetupQr(rawValue: String): GadgetDiscoveryCandidate? {
        val uri = runCatching { URI(rawValue.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("aquik", ignoreCase = true) || !uri.host.equals("setup", ignoreCase = true)) {
            return null
        }
        val params = uri.rawQuery.parseQueryParams()
        val ssid = params["ssid"]?.takeIf { it.isNotBlank() } ?: return null
        val deviceId = params["id"].orEmpty().ifBlank { "AQUIK_${ssid.takeLast(6).uppercase()}" }
        return GadgetDiscoveryCandidate(
            id = stableCandidateId(AquikDeviceProfile.ID, deviceId, ssid, null),
            displayName = deviceId,
            profileId = AquikDeviceProfile.ID,
            method = GadgetDiscoveryMethod.QR,
            host = params["host"] ?: params["ip"],
            port = params["port"]?.toIntOrNull() ?: 81,
            path = params["path"].orEmpty().ifBlank { "ws" },
            token = params["token"].orEmpty(),
            chip = params["chip"],
            firmware = params["fw"] ?: params["version"],
            apSsid = ssid,
            capabilities = listOf("WIFI_AP", "PROVISIONING", "QR"),
            rawJson = rawValue,
        )
    }

    private fun JSONObject.optStringList(name: String): List<String> {
        val value = opt(name) ?: return emptyList()
        return when (value) {
            is JSONArray -> (0 until value.length())
                .mapNotNull { index -> value.optString(index).takeIf { it.isNotBlank() } }
            is String -> value.split(',', ';').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }

    private fun String?.parseQueryParams(): Map<String, String> {
        if (isNullOrBlank()) return emptyMap()
        return split("&")
            .mapNotNull { pair ->
                val key = pair.substringBefore("=", "").decodeUrl().lowercase()
                val value = pair.substringAfter("=", "").decodeUrl()
                if (key.isBlank()) null else key to value
            }
            .toMap()
    }

    private fun String.decodeUrl(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun normalizeProfileId(raw: String): String =
        when (raw.trim().lowercase()) {
            "", "aquik", "aquik-v2" -> AquikDeviceProfile.ID
            "generic", "esp", "esp-ws", "esp-websocket", GenericEspWebSocketProfile.ID -> GenericEspWebSocketProfile.ID
            else -> raw.trim().ifBlank { AquikDeviceProfile.ID }
        }

    private fun stableCandidateId(
        profileId: String,
        deviceId: String,
        host: String,
        mac: String?,
    ): String =
        listOf(profileId, mac?.lowercase()?.ifBlank { null } ?: deviceId.lowercase(), host.lowercase())
            .joinToString(":")
}
