package com.soll.domain.soll

import java.net.URI

enum class SollPairingAuthMode {
    DEVICE,
    BEARER,
    MISSING,
}

data class SollPairingVerification(
    val endpointLabel: String = "",
    val authMode: SollPairingAuthMode = SollPairingAuthMode.MISSING,
) {
    val isReady: Boolean
        get() = endpointLabel.isNotBlank() && authMode != SollPairingAuthMode.MISSING
}

fun sollPairingVerification(
    serverUrl: String,
    apiPathPrefix: String,
    userAccessToken: String,
    deviceId: String,
    pairingSecret: String,
    deviceAccessToken: String,
): SollPairingVerification {
    val authMode = when {
        deviceAccessToken.isNotBlank() || (deviceId.isNotBlank() && pairingSecret.isNotBlank()) ->
            SollPairingAuthMode.DEVICE
        userAccessToken.isNotBlank() -> SollPairingAuthMode.BEARER
        else -> SollPairingAuthMode.MISSING
    }
    return SollPairingVerification(
        endpointLabel = safeSollPairingEndpointLabel(serverUrl, apiPathPrefix),
        authMode = authMode,
    )
}

internal fun safeSollPairingEndpointLabel(serverUrl: String, apiPathPrefix: String): String {
    val parsed = runCatching { URI(serverUrl.trim()) }.getOrNull() ?: return ""
    val scheme = parsed.scheme?.lowercase().orEmpty()
    val host = parsed.host.orEmpty()
    if (scheme !in setOf("http", "https") || host.isBlank()) return ""

    val origin = runCatching {
        URI(scheme, null, host, parsed.port, null, null, null).toString().trimEnd('/')
    }.getOrNull().orEmpty()
    val prefix = apiPathPrefix.trim().trim('/')
    return when {
        origin.isBlank() -> ""
        prefix.isBlank() -> origin
        else -> "$origin/$prefix"
    }
}
