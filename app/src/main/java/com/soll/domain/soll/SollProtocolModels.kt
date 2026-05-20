package com.soll.domain.soll

import com.soll.domain.device.GadgetDiscoveryContract
import com.soll.domain.device.GadgetDiscoveryMethod
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SollProtocolContract {
    const val VERSION = "soll-protocol-v1"
    const val DEVICE_TOKEN_REFRESH_ENDPOINT = "POST /api/v1/devices/token/refresh"
}

data class SollProtocolSchema(
    val version: String,
    val auth: SollProtocolAuth = SollProtocolAuth(),
    val gadgetCommandRoutes: List<String> = emptyList(),
    val androidTransport: SollProtocolTransport = SollProtocolTransport(),
    val workerContracts: Map<String, SollProtocolWorkerContract> = emptyMap(),
    val gadgetDiscovery: SollGadgetDiscoverySchema?,
    val warnings: List<String> = validateSollProtocolSchema(
        version = version,
        auth = auth,
        gadgetCommandRoutes = gadgetCommandRoutes,
        androidTransport = androidTransport,
        workerContracts = workerContracts,
        gadgetDiscovery = gadgetDiscovery,
    ),
) {
    val compatible: Boolean
        get() = warnings.isEmpty()
}

data class SollProtocolBootstrap(
    val version: String,
    val auth: SollProtocolAuth = SollProtocolAuth(),
    val transport: SollProtocolTransport = SollProtocolTransport(),
    val workerContracts: Map<String, SollProtocolWorkerContract> = emptyMap(),
    val warnings: List<String> = validateSollProtocolBootstrap(version, auth, transport, workerContracts),
) {
    val compatible: Boolean
        get() = warnings.isEmpty()
}

data class SollProtocolAuth(
    val pairingEndpoint: String = "",
    val challengeEndpoint: String = "",
    val tokenEndpoint: String = "",
    val tokenRefreshEndpoint: String = "",
    val tokenType: String = "",
    val refreshRule: String = "",
)

data class SollProtocolTransport(
    val recommendedAuth: String = "",
    val poll: List<String> = emptyList(),
    val push: List<String> = emptyList(),
)

data class SollProtocolWorkerContract(
    val owner: String = "",
    val auth: String = "",
    val requiredScopes: List<String> = emptyList(),
    val leaseSecondsDefault: Int = 0,
    val pollIntervalSeconds: Int = 0,
    val lifecycle: List<String> = emptyList(),
)

data class SollGadgetDiscoverySchema(
    val version: String,
    val primaryOrder: List<String>,
    val mdnsServiceTypes: List<String>,
    val ssdpHeaderNames: List<String>,
    val wifiSsidPrefixes: List<String>,
    val defaultSetupHost: String,
    val deviceJsonEndpoint: String,
    val deviceJsonRecommendedFields: List<String>,
)

fun validateSollProtocolSchema(
    version: String,
    auth: SollProtocolAuth,
    gadgetCommandRoutes: List<String>,
    androidTransport: SollProtocolTransport,
    workerContracts: Map<String, SollProtocolWorkerContract>,
    gadgetDiscovery: SollGadgetDiscoverySchema?,
): List<String> {
    val warnings = mutableListOf<String>()
    when {
        version.isBlank() -> warnings += "Сервер не отдал версию основного протокола."
        version != SollProtocolContract.VERSION -> {
            warnings += "Версия протокола сервера $version, Android ожидает ${SollProtocolContract.VERSION}."
        }
    }
    val requiredGadgetCommandRoutes = listOf(
        "GET /api/v1/gadgets/{device_id}/commands",
        "POST /api/v1/gadgets/{device_id}/commands",
        "GET /api/v1/gadgets/{device_id}/commands/pending",
        "POST /api/v1/gadgets/{device_id}/commands/{command_id}/result",
        "GET /api/v1/mesh/outbox",
        "GET /api/v1/mesh/outbox/next",
        "POST /api/v1/mesh/outbox/{outbound_id}/ack",
        "POST /api/v1/mesh/outbox/{outbound_id}/retry",
    )
    val missingCommandRoutes = requiredGadgetCommandRoutes.filterNot(gadgetCommandRoutes::contains)
    if (missingCommandRoutes.isNotEmpty()) {
        warnings += "В server gadget:commands нет маршрутов: ${missingCommandRoutes.joinToString()}."
    }
    warnings += validateSollProtocolBootstrap(
        version = version,
        auth = auth,
        transport = androidTransport,
        workerContracts = workerContracts,
    )
        .filterNot { warning ->
            warning.startsWith("Сервер не отдал версию") || warning.startsWith("Версия протокола")
        }

    if (gadgetDiscovery == null) {
        warnings += "В ответе сервера нет блока gadget_discovery."
        return warnings
    }

    warnings += gadgetDiscovery.compatibilityWarnings()
    return warnings
}

fun validateSollProtocolBootstrap(
    version: String,
    auth: SollProtocolAuth,
    transport: SollProtocolTransport,
    workerContracts: Map<String, SollProtocolWorkerContract>,
): List<String> {
    val warnings = mutableListOf<String>()
    when {
        version.isBlank() -> warnings += "Сервер не отдал версию bootstrap протокола."
        version != SollProtocolContract.VERSION -> {
            warnings += "Версия протокола bootstrap $version, Android ожидает ${SollProtocolContract.VERSION}."
        }
    }
    if (auth.tokenRefreshEndpoint != SollProtocolContract.DEVICE_TOKEN_REFRESH_ENDPOINT) {
        warnings += "В auth нет корректного token_refresh для обновления device bearer."
    }
    if (!auth.tokenType.contains("device", ignoreCase = true)) {
        warnings += "Auth token_type не похож на device-bearer."
    }
    if (!auth.refreshRule.contains("invalidated", ignoreCase = true) && !auth.refreshRule.contains("rotat", ignoreCase = true)) {
        warnings += "Auth refresh_rule не фиксирует ротацию старого bearer."
    }
    if (!transport.recommendedAuth.contains("device bearer", ignoreCase = true)) {
        warnings += "Android transport должен рекомендовать device bearer."
    }
    listOf(
        "GET /api/v1/android/sync-status",
        "GET /api/v1/mesh/outbox/next",
    ).forEach { route ->
        if (route !in transport.poll) {
            warnings += "Android transport не содержит poll маршрут: $route."
        }
    }
    warnings += validateWorkerContract(
        name = "android_mesh_outbox_worker",
        contract = workerContracts["android_mesh_outbox_worker"],
        requiredScopes = listOf("gadget:commands", "status:read"),
        lifecycle = listOf("queued", "sent", "acked", "failed"),
    )
    warnings += validateWorkerContract(
        name = "gadget_command_worker",
        contract = workerContracts["gadget_command_worker"],
        requiredScopes = listOf("gadget:commands"),
        lifecycle = listOf("pending", "claimed", "acked", "done", "failed", "expired"),
    )
    return warnings
}

private fun validateWorkerContract(
    name: String,
    contract: SollProtocolWorkerContract?,
    requiredScopes: List<String>,
    lifecycle: List<String>,
): List<String> {
    if (contract == null) {
        return listOf("В protocol bootstrap нет worker contract: $name.")
    }
    val warnings = mutableListOf<String>()
    if (!contract.auth.contains("device bearer", ignoreCase = true)) {
        warnings += "$name должен работать от device bearer."
    }
    val missingScopes = requiredScopes.filterNot(contract.requiredScopes::contains)
    if (missingScopes.isNotEmpty()) {
        warnings += "$name не содержит scopes: ${missingScopes.joinToString()}."
    }
    val missingLifecycle = lifecycle.filterNot(contract.lifecycle::contains)
    if (missingLifecycle.isNotEmpty()) {
        warnings += "$name не содержит lifecycle: ${missingLifecycle.joinToString()}."
    }
    if (contract.pollIntervalSeconds <= 0) {
        warnings += "$name не отдал poll_interval_seconds."
    }
    if (contract.leaseSecondsDefault <= 0) {
        warnings += "$name не отдал lease_seconds_default."
    }
    return warnings
}

fun SollGadgetDiscoverySchema.compatibilityWarnings(): List<String> {
    val warnings = mutableListOf<String>()
    if (version != GadgetDiscoveryContract.VERSION) {
        warnings += "Версия поиска гаджетов сервера $version, Android ожидает ${GadgetDiscoveryContract.VERSION}."
    }

    val expectedOrder = GadgetDiscoveryContract.primaryOrder.map { it.contractKey() }
    val missingMethods = expectedOrder.filterNot(primaryOrder::contains)
    if (missingMethods.isNotEmpty()) {
        warnings += "В server discovery нет методов: ${missingMethods.joinToString()}."
    }

    val missingMdns = GadgetDiscoveryContract.mdnsServiceTypes.filterNot(mdnsServiceTypes::contains)
    if (missingMdns.isNotEmpty()) {
        warnings += "В server mDNS нет service type: ${missingMdns.joinToString()}."
    }

    val headerText = ssdpHeaderNames.joinToString(separator = " ")
    val requiredHeaderMarkers = listOf("AQUIK-DEVICE-ID", "X-DEVICE-ID", "AQUIK-WS-PORT", "X-WS-PORT")
    val missingHeaders = requiredHeaderMarkers.filterNot { marker ->
        headerText.contains(marker, ignoreCase = true)
    }
    if (missingHeaders.isNotEmpty()) {
        warnings += "В server SSDP нет обязательных заголовков: ${missingHeaders.joinToString()}."
    }

    val missingSsidPrefixes = GadgetDiscoveryContract.setupSsidPrefixes.filterNot(wifiSsidPrefixes::contains)
    if (missingSsidPrefixes.isNotEmpty()) {
        warnings += "В server Wi-Fi AP нет SSID-префиксов: ${missingSsidPrefixes.joinToString()}."
    }
    if (defaultSetupHost.isNotBlank() && defaultSetupHost != GadgetDiscoveryContract.defaultSetupHost) {
        warnings += "Адрес AP настройки сервера $defaultSetupHost, Android ожидает ${GadgetDiscoveryContract.defaultSetupHost}."
    }

    if (deviceJsonEndpoint != GadgetDiscoveryContract.deviceJsonPath) {
        warnings += "Endpoint metadata сервера $deviceJsonEndpoint, Android ожидает ${GadgetDiscoveryContract.deviceJsonPath}."
    }
    val requiredDeviceJsonFields = listOf("websocketUrl", "websocketPort", "path", "capabilities")
    val missingDeviceJsonFields = requiredDeviceJsonFields.filterNot(deviceJsonRecommendedFields::contains)
    if (missingDeviceJsonFields.isNotEmpty()) {
        warnings += "В server device.json нет рекомендованных полей: ${missingDeviceJsonFields.joinToString()}."
    }

    return warnings
}

private fun GadgetDiscoveryMethod.contractKey(): String =
    when (this) {
        GadgetDiscoveryMethod.LAN_MDNS -> "mdns"
        GadgetDiscoveryMethod.LAN_SSDP -> "ssdp"
        GadgetDiscoveryMethod.WIFI_AP -> "wifi_ap"
        GadgetDiscoveryMethod.QR -> "qr"
        GadgetDiscoveryMethod.MANUAL -> "manual"
        GadgetDiscoveryMethod.BLE_PLANNED -> "ble"
        GadgetDiscoveryMethod.SMARTCONFIG_PLANNED -> "smartconfig"
    }

fun buildSollDeviceTokenSignature(
    pairingSecret: String,
    deviceId: String,
    challengeId: String,
    challenge: String,
    nonce: String,
): String {
    val secretHash = MessageDigest.getInstance("SHA-256")
        .digest(pairingSecret.toByteArray(Charsets.UTF_8))
    val message = "$deviceId:$challengeId:$challenge:$nonce"
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secretHash, "HmacSHA256"))
    return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString(separator = "") { byte ->
        "%02x".format(byte)
    }
}
