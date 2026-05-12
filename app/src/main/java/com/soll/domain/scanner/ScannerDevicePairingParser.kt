package com.soll.domain.scanner

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

data class ScannerDevicePairingPayload(
    val host: String,
    val port: Int = 81,
    val path: String = "ws",
    val token: String = "",
    val profileId: String = DEFAULT_PROFILE_ID,
)

object ScannerDevicePairingParser {
    fun parse(rawValue: String): ScannerDevicePairingPayload? {
        val value = rawValue.trim()
        if (value.isBlank()) return null
        return parseJson(value)
            ?: parseUri(value)
            ?: parseKeyValue(value)
            ?: parsePlainHost(value)
    }

    private fun parseJson(value: String): ScannerDevicePairingPayload? {
        if (!value.startsWith("{")) return null
        val root = runCatching { JSONObject(value) }.getOrNull() ?: return null
        val profileId = root.optString("profileId")
            .ifBlank { root.optString("profile") }
            .normalizeProfileId()
            ?: return null
        val host = root.optString("host")
            .ifBlank { root.optString("ip") }
            .ifBlank { root.optString("address") }
        return payloadOrNull(
            host = host,
            port = root.optInt("port", 81),
            path = root.optString("path", "ws"),
            token = root.optString("token"),
            profileId = profileId,
        )
    }

    private fun parseUri(value: String): ScannerDevicePairingPayload? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val params = uri.rawQuery.parseQueryParams()
        val profileId = (params["profileid"] ?: params["profile"]).normalizeProfileId() ?: return null
        return when (scheme) {
            "ws", "wss", "http", "https" -> payloadOrNull(
                host = uri.host.orEmpty(),
                port = if (uri.port > 0) uri.port else params["port"]?.toIntOrNull() ?: 81,
                path = uri.path.orEmpty(),
                token = params["token"].orEmpty(),
                profileId = profileId,
            )
            "aquik", "soll-device" -> payloadOrNull(
                host = params["host"] ?: params["ip"] ?: uri.host.orEmpty(),
                port = params["port"]?.toIntOrNull() ?: if (uri.port > 0) uri.port else 81,
                path = params["path"] ?: uri.path.orEmpty(),
                token = params["token"].orEmpty(),
                profileId = profileId,
            )
            else -> null
        }
    }

    private fun parseKeyValue(value: String): ScannerDevicePairingPayload? {
        val body = value.substringAfter(":", value)
        if (!body.contains("=")) return null
        val params = body
            .split(';', '&', ',', '\n')
            .mapNotNull { part ->
                val key = part.substringBefore("=", "").trim()
                val paramValue = part.substringAfter("=", "").trim()
                if (key.isBlank() || paramValue.isBlank()) null else key.lowercase() to paramValue
            }
            .toMap()
        return payloadOrNull(
            host = params["host"] ?: params["ip"] ?: params["address"].orEmpty(),
            port = params["port"]?.toIntOrNull() ?: 81,
            path = params["path"].orEmpty(),
            token = params["token"].orEmpty(),
            profileId = (params["profileid"] ?: params["profile"]).normalizeProfileId() ?: return null,
        )
    }

    private fun parsePlainHost(value: String): ScannerDevicePairingPayload? {
        if (value.all(Char::isDigit)) return null
        if (!value.contains(".") && !value.contains(":")) return null
        val host = value.substringBefore(":").trim()
        val port = value.substringAfter(":", "").toIntOrNull() ?: 81
        return payloadOrNull(host = host, port = port, path = "ws", token = "")
    }

    private fun payloadOrNull(
        host: String,
        port: Int,
        path: String,
        token: String,
        profileId: String = DEFAULT_PROFILE_ID,
    ): ScannerDevicePairingPayload? {
        val cleanHost = host.trim().removePrefix("ws://").removePrefix("http://").trim('/')
        if (cleanHost.isBlank() || port !in 1..65535) return null
        return ScannerDevicePairingPayload(
            host = cleanHost,
            port = port,
            path = path.trim().trim('/').ifBlank { "ws" },
            token = token.trim(),
            profileId = profileId,
        )
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
}

private const val DEFAULT_PROFILE_ID = "aquik-v2"

private fun String?.normalizeProfileId(): String? {
    val value = this?.trim()?.lowercase().orEmpty()
    if (value.isBlank()) return DEFAULT_PROFILE_ID
    return when (value) {
        "aquik", "aquik-v2" -> "aquik-v2"
        "generic", "esp", "esp-ws", "esp-websocket", "generic-esp-websocket" -> "generic-esp-websocket"
        else -> null
    }
}
