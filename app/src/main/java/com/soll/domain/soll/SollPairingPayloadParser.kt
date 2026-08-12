package com.soll.domain.soll

import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class SollPairingPayload(
    val serverUrl: String,
    val apiPathPrefix: String,
    val accessToken: String = "",
    val deviceId: String = "",
    val pairingSecret: String = "",
    val clientId: String = "",
    val sessionId: String = "",
) {
    val usesRelayBearerAuth: Boolean
        get() = accessToken.isNotBlank() && deviceId.isBlank() && pairingSecret.isBlank()
}

object SollPairingPayloadParser {
    private const val PAYLOAD_TYPE = "soll_android_pairing"

    fun parse(raw: String?): SollPairingPayload? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        return if (value.startsWith("{")) {
            parseJson(value)
        } else {
            parseDeepLink(value)
        }
    }

    private fun parseDeepLink(raw: String): SollPairingPayload? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "soll" || uri.host != "pair") return null
        val params = uri.rawQuery
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                val key = pieces.getOrNull(0)?.decodeQueryPart().orEmpty()
                val value = pieces.getOrNull(1)?.decodeQueryPart().orEmpty()
                key.takeIf { it.isNotBlank() }?.let { it to value }
            }
            ?.toMap()
            ?: emptyMap()

        val type = params["type"].orEmpty()
        if (type.isNotBlank() && type != PAYLOAD_TYPE) return null

        return buildPayload(
            serverUrl = params["server_url"].orEmpty(),
            apiPathPrefix = params["api_path_prefix"].orEmpty(),
            accessToken = params["access_token"].orEmpty(),
            deviceId = params["device_id"].orEmpty(),
            pairingSecret = params["pairing_secret"].orEmpty().ifBlank {
                params["device_pairing_secret"].orEmpty()
            },
            clientId = params["client_id"].orEmpty(),
            sessionId = params["session_id"].orEmpty(),
        )
    }

    private fun parseJson(raw: String): SollPairingPayload? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val type = json.optString("type")
        if (type.isNotBlank() && type != PAYLOAD_TYPE) return null

        return buildPayload(
            serverUrl = json.firstString("server_url", "serverUrl", "base_url", "baseUrl"),
            apiPathPrefix = json.firstString("api_path_prefix", "apiPathPrefix", "prefix"),
            accessToken = json.firstString("access_token", "accessToken", "bearer", "token"),
            deviceId = json.firstString("device_id", "deviceId"),
            pairingSecret = json.firstString("pairing_secret", "pairingSecret", "device_pairing_secret"),
            clientId = json.firstString("client_id", "clientId"),
            sessionId = json.firstString("session_id", "sessionId"),
        )
    }

    private fun buildPayload(
        serverUrl: String,
        apiPathPrefix: String,
        accessToken: String,
        deviceId: String,
        pairingSecret: String,
        clientId: String,
        sessionId: String,
    ): SollPairingPayload? {
        val cleanServerUrl = serverUrl.trim()
        val scheme = runCatching { URI(cleanServerUrl).scheme?.lowercase() }.getOrNull()
        if (scheme !in setOf("http", "https")) return null

        val cleanPrefix = apiPathPrefix.trim().trim('/')
        val cleanToken = accessToken.trim()
        val cleanDeviceId = deviceId.trim()
        val cleanPairingSecret = pairingSecret.trim()
        if (cleanPrefix.isBlank()) return null
        if (cleanToken.isBlank() && (cleanDeviceId.isBlank() || cleanPairingSecret.isBlank())) return null

        return SollPairingPayload(
            serverUrl = cleanServerUrl,
            apiPathPrefix = cleanPrefix,
            accessToken = cleanToken,
            deviceId = cleanDeviceId,
            pairingSecret = cleanPairingSecret,
            clientId = clientId.trim(),
            sessionId = sessionId.trim(),
        )
    }

    private fun JSONObject.firstString(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun String.decodeQueryPart(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}
