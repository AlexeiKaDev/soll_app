package com.soll.data.device

import com.soll.domain.device.AquikDeviceProfile
import com.soll.domain.device.DeviceCommandResponse
import com.soll.domain.device.DeviceAuthMode
import com.soll.domain.device.DeviceConnectionConfig
import com.soll.domain.device.DeviceConnectionState
import com.soll.domain.device.DeviceConnectionStatus
import com.soll.domain.device.DeviceConnector
import com.soll.domain.device.DeviceLedType
import com.soll.domain.device.DevicePumpType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class WebSocketDeviceConnector @Inject constructor(
    okHttpClient: OkHttpClient,
) : DeviceConnector {
    private val client = okHttpClient.newBuilder()
        .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestId = AtomicLong(0)
    private val pendingById = ConcurrentHashMap<Long, PendingCommand>()
    private val pendingByCommand = ConcurrentHashMap<String, PendingCommand>()
    private val pendingQueue = ConcurrentLinkedQueue<PendingCommand>()

    private val _state = MutableStateFlow(DeviceConnectionState())
    override val state: StateFlow<DeviceConnectionState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var currentConfig: DeviceConnectionConfig? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var manualDisconnect = false
    private var hasConnectedOnce = false

    override suspend fun connect(config: DeviceConnectionConfig): Result<Unit> = runCatching {
        currentConfig = config
        manualDisconnect = false
        reconnectJob?.cancel()
        webSocket?.cancel()

        val openSignal = CompletableDeferred<Unit>()
        val endpointUrl = config.endpointUrl()
        _state.value = DeviceConnectionState(
            status = DeviceConnectionStatus.CONNECTING,
            deviceId = config.deviceId,
            endpointUrl = endpointUrl,
            message = "Подключение к $endpointUrl",
            reconnectAttempt = reconnectAttempts,
        )

        val request = Request.Builder().url(endpointUrl).build()
        webSocket = client.newWebSocket(
            request,
            listener(
                config = config,
                openSignal = openSignal,
            )
        )

        withTimeout(CONNECTION_TIMEOUT_MS) { openSignal.await() }

        when (config.profile.authMode) {
            DeviceAuthMode.NONE -> {
                _state.value = _state.value.copy(
                    status = DeviceConnectionStatus.CONNECTED,
                    message = "WebSocket подключен",
                )
            }
            DeviceAuthMode.TOKEN -> {
                val token = config.token.trim()
                require(token.isNotBlank()) { "Токен устройства не задан" }
                val authResult = authenticate(token)
                if (authResult.isFailure) {
                    throw authResult.exceptionOrNull() ?: IllegalStateException("Авторизация устройства не выполнена")
                }
                _state.value = _state.value.copy(
                    status = DeviceConnectionStatus.AUTHENTICATED,
                    message = "Устройство авторизовано",
                )
            }
        }
    }

    override fun disconnect() {
        manualDisconnect = true
        reconnectJob?.cancel()
        failPending(IllegalStateException("Соединение закрыто"))
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _state.value = _state.value.copy(
            status = DeviceConnectionStatus.DISCONNECTED,
            message = "Отключено",
            reconnectAttempt = 0,
        )
    }

    override suspend fun authenticate(token: String): Result<DeviceCommandResponse> =
        executeProfileCommand(
            command = AquikDeviceProfile.COMMAND_AUTH,
            params = JSONObject().put("token", token),
            topLevel = mapOf("token" to token),
        )

    override suspend fun getInfo(): Result<DeviceCommandResponse> {
        val primary = executeProfileCommand(AquikDeviceProfile.COMMAND_GET_INFO)
        return if (primary.isSuccess) {
            primary
        } else {
            executeProfileCommand(AquikDeviceProfile.COMMAND_GET_INFO_LEGACY)
        }
    }

    override suspend fun getConfig(): Result<DeviceCommandResponse> {
        val primary = executeProfileCommand(AquikDeviceProfile.COMMAND_GET_CONFIG)
        return if (primary.isSuccess) {
            primary
        } else {
            executeProfileCommand(AquikDeviceProfile.COMMAND_GET_SETTINGS)
        }
    }

    override suspend fun getSensors(): Result<DeviceCommandResponse> =
        executeProfileCommand(AquikDeviceProfile.COMMAND_GET_SENSORS)

    override suspend fun getActuators(): Result<DeviceCommandResponse> =
        executeProfileCommand(AquikDeviceProfile.COMMAND_GET_ACTUATORS)

    override suspend fun executeCommand(
        command: String,
        paramsJson: String,
    ): Result<DeviceCommandResponse> {
        val params = runCatching {
            JSONObject(paramsJson.ifBlank { "{}" })
        }.getOrElse { error ->
            return Result.failure(IllegalArgumentException("Некорректный JSON параметров: ${error.message}"))
        }
        return executeProfileCommand(command = command, params = params)
    }

    override suspend fun setPump(type: DevicePumpType, enabled: Boolean): Result<DeviceCommandResponse> =
        executeProfileCommand(
            command = AquikDeviceProfile.COMMAND_SET_PUMP,
            params = JSONObject()
                .put("type", type.wireName)
                .put("state", enabled),
            topLevel = mapOf(
                "type" to type.wireName,
                "state" to enabled,
            ),
        )

    override suspend fun setFan(enabled: Boolean): Result<DeviceCommandResponse> =
        executeProfileCommand(
            command = AquikDeviceProfile.COMMAND_SET_FAN,
            params = JSONObject().put("state", enabled),
            topLevel = mapOf("state" to enabled),
        )

    override suspend fun setLed(type: DeviceLedType, value: Int): Result<DeviceCommandResponse> =
        executeProfileCommand(
            command = AquikDeviceProfile.COMMAND_SET_LED,
            params = JSONObject()
                .put("type", type.wireName)
                .put("value", value.coerceIn(0, 255)),
            topLevel = mapOf(
                "type" to type.wireName,
                "value" to value.coerceIn(0, 255),
            ),
        )

    private suspend fun executeProfileCommand(
        command: String,
        params: JSONObject = JSONObject(),
        topLevel: Map<String, Any> = emptyMap(),
    ): Result<DeviceCommandResponse> = runCatching {
        val raw = sendCommand(command, params, topLevel)
        val response = raw.toCommandResponse(command)
        if (!response.success) {
            throw IllegalStateException(response.error ?: "Команда $command завершилась ошибкой")
        }
        response
    }

    private suspend fun sendCommand(
        command: String,
        params: JSONObject,
        topLevel: Map<String, Any>,
    ): JSONObject {
        val socket = webSocket ?: throw IllegalStateException("WebSocket не подключен")
        val id = requestId.incrementAndGet()
        val request = JSONObject()
            .put("cmd", command)
            .put("id", id)
            .put("params", params)
            .put("timestamp", System.currentTimeMillis())
        topLevel.forEach { (key, value) -> request.put(key, value) }

        val pending = PendingCommand(
            id = id,
            command = command,
            response = CompletableDeferred(),
        )
        pendingById[id] = pending
        pendingByCommand[command] = pending
        pendingQueue.add(pending)

        val sent = socket.send(request.toString())
        if (!sent) {
            removePending(pending)
            throw IllegalStateException("Не удалось отправить команду $command")
        }

        return try {
            withTimeout(COMMAND_TIMEOUT_MS) { pending.response.await() }
        } finally {
            removePending(pending)
        }
    }

    private fun listener(
        config: DeviceConnectionConfig,
        openSignal: CompletableDeferred<Unit>,
    ): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            hasConnectedOnce = true
            reconnectAttempts = 0
            _state.value = DeviceConnectionState(
                status = DeviceConnectionStatus.CONNECTED,
                deviceId = config.deviceId,
                endpointUrl = config.endpointUrl(),
                message = "WebSocket подключен",
            )
            openSignal.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { JSONObject(text) }
                .onSuccess { completePending(it) }
                .onFailure { error ->
                    failOldestPending(
                        IllegalStateException("Некорректный JSON от устройства: ${error.message}")
                    )
                }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _state.value = _state.value.copy(
                status = DeviceConnectionStatus.DISCONNECTED,
                message = "Соединение закрыто: $reason",
            )
            if (!manualDisconnect && hasConnectedOnce) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val message = t.message ?: "ошибка сети"
            _state.value = _state.value.copy(
                status = DeviceConnectionStatus.ERROR,
                message = message,
            )
            if (!openSignal.isCompleted) openSignal.completeExceptionally(t)
            failPending(t)
            if (!manualDisconnect && hasConnectedOnce) scheduleReconnect()
        }
    }

    private fun completePending(response: JSONObject) {
        val pending = when {
            response.has("id") -> pendingById[response.optLong("id")]
            response.optString("type") == AquikDeviceProfile.COMMAND_AUTH -> {
                pendingByCommand[AquikDeviceProfile.COMMAND_AUTH]
            }
            else -> pendingQueue.peek()
        }

        if (pending != null && pending.response.complete(response)) {
            removePending(pending)
        }
    }

    private fun failOldestPending(error: Throwable) {
        pendingQueue.poll()?.let { pending ->
            pending.response.completeExceptionally(error)
            removePending(pending)
        }
    }

    private fun failPending(error: Throwable) {
        pendingQueue.toList().forEach { pending ->
            pending.response.completeExceptionally(error)
            removePending(pending)
        }
    }

    private fun removePending(pending: PendingCommand) {
        pendingById.remove(pending.id)
        pendingByCommand.remove(pending.command, pending)
        pendingQueue.remove(pending)
    }

    private fun scheduleReconnect() {
        val config = currentConfig ?: return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _state.value = _state.value.copy(
                status = DeviceConnectionStatus.ERROR,
                message = "Повторное подключение не удалось",
            )
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts += 1
            val delayMs = BASE_RECONNECT_DELAY_MS * (1L shl (reconnectAttempts - 1)).coerceAtMost(8L)
            _state.value = _state.value.copy(
                status = DeviceConnectionStatus.CONNECTING,
                message = "Повторное подключение через ${delayMs / 1000} с",
                reconnectAttempt = reconnectAttempts,
            )
            delay(delayMs)
            connect(config)
        }
    }

    private data class PendingCommand(
        val id: Long,
        val command: String,
        val response: CompletableDeferred<JSONObject>,
    )

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 5_000L
        const val PING_INTERVAL_MS = 30_000L
        const val BASE_RECONNECT_DELAY_MS = 1_500L
        const val MAX_RECONNECT_ATTEMPTS = 3
    }
}

private fun JSONObject.toCommandResponse(command: String): DeviceCommandResponse {
    val type = optString("type").takeIf { it.isNotBlank() }
    val error = optStringOrNull("error")
        ?: optStringOrNull("details")
        ?: optStringOrNull("message")?.takeIf { type == "error" || optBoolean("success", true).not() }
    val success = when {
        type == "error" -> false
        has("success") -> optBoolean("success")
        error != null -> false
        else -> true
    }
    val data = when (val value = opt("data")) {
        is JSONObject -> value.toString()
        is JSONArray -> value.toString()
        null, JSONObject.NULL -> optJSONObject("sensors")?.toString() ?: toString()
        else -> value.toString()
    }
    return DeviceCommandResponse(
        requestId = if (has("id")) optLong("id") else null,
        command = optString("cmd").takeIf { it.isNotBlank() } ?: type ?: command,
        success = success,
        dataJson = data,
        error = error,
        rawJson = toString(),
        timestamp = if (has("timestamp")) optLong("timestamp") else null,
    )
}

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotBlank() } else null
