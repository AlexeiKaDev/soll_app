package com.soll.domain.device

import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

data class DeviceProfile(
    val id: String,
    val name: String,
    val transport: DeviceTransport,
    val authMode: DeviceAuthMode,
    val commandSchemaVersion: String,
    val capabilities: List<String>,
)

enum class DeviceTransport {
    WEBSOCKET,
}

enum class DeviceAuthMode {
    NONE,
    TOKEN,
}

data class KnownDevice(
    val id: String,
    val profileId: String,
    val name: String,
    val host: String,
    val port: Int,
    val path: String,
    val transport: DeviceTransport,
    val authMode: DeviceAuthMode,
    val lastStatus: String,
    val lastSeenAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun endpoint(): DeviceEndpoint =
        DeviceEndpoint.normalize(host = host, port = port, path = path)

    fun endpointUrl(): String = endpoint().url()
}

data class DeviceConnectionConfig(
    val profile: DeviceProfile,
    val host: String,
    val port: Int = 81,
    val path: String = "ws",
    val token: String = "",
) {
    fun endpoint(): DeviceEndpoint =
        DeviceEndpoint.normalize(host = host, port = port, path = path)

    val deviceId: String = endpoint().deviceId(profile.id)

    fun endpointUrl(): String = endpoint().url()
}

data class DeviceEndpoint(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
) {
    fun url(): String {
        val cleanPath = path.trim().trim('/').takeIf { it.isNotBlank() }
        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(":")
            append(port)
            cleanPath?.let { append("/").append(it) }
        }
    }

    fun storageHost(): String =
        if (scheme == DEFAULT_SCHEME) {
            host
        } else {
            "$scheme://$host:$port"
        }

    fun deviceId(profileId: String): String {
        val schemePrefix = if (scheme == DEFAULT_SCHEME) "" else "$scheme://"
        return "$profileId:$schemePrefix${host.lowercase()}:$port"
    }

    companion object {
        const val DEFAULT_SCHEME = "ws"

        fun normalize(
            host: String,
            port: Int,
            path: String,
        ): DeviceEndpoint {
            val cleanHost = host.trim().trimEnd('/')
            val cleanPath = path.trim().trim('/')
            if (cleanHost.contains("://")) {
                return fromUrl(cleanHost, port, cleanPath)
            }

            val slashIndex = cleanHost.indexOf('/')
            val authority = if (slashIndex >= 0) cleanHost.substring(0, slashIndex) else cleanHost
            val hostPath = if (slashIndex >= 0) cleanHost.substring(slashIndex + 1).trim('/') else ""
            val parsedPort = authority.substringAfterLast(':', "")
                .toIntOrNull()
                ?.takeIf { authority.count { char -> char == ':' } == 1 }
            val parsedHost = if (parsedPort != null) authority.substringBeforeLast(':') else authority
            return DeviceEndpoint(
                scheme = DEFAULT_SCHEME,
                host = parsedHost.trim().lowercase(),
                port = parsedPort ?: port,
                path = hostPath.ifBlank { cleanPath },
            )
        }

        private fun fromUrl(
            rawUrl: String,
            fallbackPort: Int,
            fallbackPath: String,
        ): DeviceEndpoint {
            val uri = try {
                URI(rawUrl)
            } catch (_: URISyntaxException) {
                return normalize(rawUrl.substringAfter("://"), fallbackPort, fallbackPath)
            }
            val scheme = uri.scheme.toWebSocketScheme()
            val host = (uri.host ?: uri.rawAuthority?.substringAfter('@')?.substringBefore(':')).orEmpty()
            val path = uri.rawPath
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
                ?.let { rawPath ->
                    uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "$rawPath?$it" } ?: rawPath
                }
                ?: fallbackPath
            val resolvedPort = when {
                uri.port in 1..65535 -> uri.port
                else -> defaultPortFor(scheme)
            }
            return DeviceEndpoint(
                scheme = scheme,
                host = host.trim().lowercase(),
                port = resolvedPort,
                path = path,
            )
        }

        private fun String?.toWebSocketScheme(): String =
            when (this?.lowercase()) {
                "wss", "https" -> "wss"
                "ws", "http" -> "ws"
                else -> DEFAULT_SCHEME
            }

        private fun defaultPortFor(scheme: String): Int =
            if (scheme == "wss") 443 else 80
    }
}

data class DeviceConnectionState(
    val status: DeviceConnectionStatus = DeviceConnectionStatus.DISCONNECTED,
    val deviceId: String? = null,
    val endpointUrl: String? = null,
    val message: String? = null,
    val reconnectAttempt: Int = 0,
)

enum class DeviceConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED,
    ERROR,
}

data class DeviceCommandResponse(
    val requestId: Long?,
    val command: String,
    val success: Boolean,
    val dataJson: String,
    val error: String?,
    val rawJson: String,
    val timestamp: Long?,
)

data class DeviceTelemetry(
    val deviceId: String,
    val values: List<DeviceSensorValue>,
    val rawJson: String,
    val timestamp: Long,
)

enum class GadgetRouteStatus(val label: String) {
    NOT_CONFIGURED("Сервер не задан"),
    SYNCING("Обновление"),
    ONLINE("Сервер доступен"),
    STALE("Данные устарели"),
    ERROR("Ошибка сервера"),
}

data class GadgetCloudSnapshot(
    val id: String,
    val name: String,
    val profileId: String,
    val enabled: Boolean,
    val firmwareVersion: String = "",
    val localIp: String? = null,
    val uptimeMs: Long? = null,
    val capabilities: List<String> = emptyList(),
    val heartbeatPayload: Map<String, Any?> = emptyMap(),
    val lastHeartbeatAt: String?,
    val lastTelemetryAt: String?,
    val latestTelemetry: Map<String, Any?>,
    val latestEventType: String?,
    val latestEventSummary: String?,
    val stale: Boolean,
    val updatedAt: String?,
)

data class GadgetCloudEvent(
    val id: String,
    val gadgetId: String,
    val type: String,
    val summary: String,
    val payload: Map<String, Any?>,
    val createdAt: String,
)

data class GadgetCloudCommand(
    val id: String,
    val gadgetId: String,
    val command: String,
    val params: Map<String, Any?>,
    val status: String,
    val reason: String,
    val result: Map<String, Any?>,
    val riskLevel: String = "read_only",
    val approvalId: String = "",
    val createdAt: String,
    val expiresAt: String?,
    val completedAt: String?,
) {
    val accepted: Boolean
        get() = status in setOf("pending", "done", "sent", "approval_required", "manual_ready")
}

data class GadgetCloudHistoryPoint(
    val metric: String,
    val value: Any?,
    val createdAt: String,
)

data class GadgetCloudHistory(
    val gadgetId: String,
    val metric: String?,
    val points: List<GadgetCloudHistoryPoint>,
)

data class DeviceSensorValue(
    val key: String,
    val label: String,
    val value: String,
    val status: DeviceSensorStatus = DeviceSensorStatus.UNKNOWN,
)

enum class DeviceSensorStatus(val label: String) {
    NORMAL("Норма"),
    WARNING("Внимание"),
    CRITICAL("Критично"),
    UNKNOWN("Нет оценки")
}

enum class DevicePumpType(val wireName: String, val label: String) {
    AIR("air", "Воздушный насос"),
    WATER("water", "Водяной насос"),
}

enum class DeviceLedType(val wireName: String, val label: String) {
    FULL("full", "Полный спектр"),
    WHITE("white", "Белый LED"),
}

data class DeviceActuatorSnapshot(
    val airPump: Boolean? = null,
    val waterPump: Boolean? = null,
    val fan: Boolean? = null,
    val fullLed: Int? = null,
    val whiteLed: Int? = null,
)

data class GadgetKeyValue(
    val label: String,
    val value: String,
)

data class GadgetConfigSummary(
    val items: List<GadgetKeyValue> = emptyList(),
    val rawJson: String = "",
)

data class GadgetScheduleItem(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val type: String,
    val time: String,
    val action: String,
)

data class GadgetScheduleSummary(
    val items: List<GadgetScheduleItem> = emptyList(),
    val rawJson: String = "",
)

data class GadgetAutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val sensorKey: String,
    val operator: String,
    val threshold: String,
    val action: String,
)

data class GadgetAutomationSummary(
    val items: List<GadgetAutomationRule> = emptyList(),
    val rawJson: String = "",
)

data class GadgetDiagnosticSummary(
    val items: List<GadgetKeyValue> = emptyList(),
    val rawJson: String = "",
)

data class DeviceEvent(
    val id: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val type: String,
    val summary: String,
    val payloadJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
