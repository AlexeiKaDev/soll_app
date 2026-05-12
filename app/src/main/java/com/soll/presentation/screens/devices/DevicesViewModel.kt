package com.soll.presentation.screens.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.device.DeviceProvisioningClient
import com.soll.data.device.WebSocketDeviceConnector
import com.soll.data.repository.DeviceRepository
import com.soll.data.repository.SettingsRepository
import com.soll.domain.soll.SollGateway
import com.soll.domain.device.AquikDeviceProfile
import com.soll.domain.device.AquikProvisioningDefaults
import com.soll.domain.device.BuiltInDeviceProfiles
import com.soll.domain.device.DeviceCommandResponse
import com.soll.domain.device.DeviceConnectionConfig
import com.soll.domain.device.DeviceConnectionState
import com.soll.domain.device.DeviceConnectionStatus
import com.soll.domain.device.DeviceEvent
import com.soll.domain.device.DeviceAuthMode
import com.soll.domain.device.DeviceLedType
import com.soll.domain.device.DevicePumpType
import com.soll.domain.device.DeviceProfile
import com.soll.domain.device.DeviceTelemetry
import com.soll.domain.device.GadgetConfigSummary
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.GadgetDiagnosticSummary
import com.soll.domain.device.GadgetPayloadParser
import com.soll.domain.device.GadgetRouteStatus
import com.soll.domain.device.GadgetScheduleSummary
import com.soll.domain.device.KnownDevice
import com.soll.domain.device.DeviceEndpoint
import com.soll.domain.assistant.CapabilityRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val hostInput: String = "",
    val portInput: String = "81",
    val pathInput: String = "ws",
    val tokenInput: String = "",
    val profiles: List<DeviceProfile> = emptyList(),
    val selectedProfileId: String = AquikDeviceProfile.ID,
    val knownDevices: List<KnownDevice> = emptyList(),
    val connectionState: DeviceConnectionState = DeviceConnectionState(),
    val selectedDeviceId: String? = null,
    val isBusy: Boolean = false,
    val isProvisioningBusy: Boolean = false,
    val provisioningApHostInput: String = AquikProvisioningDefaults.setupApHost,
    val provisioningSsidInput: String = "",
    val provisioningPasswordInput: String = "",
    val provisioningTimeoutInput: String = AquikProvisioningDefaults.defaultSmartConfigTimeoutSec.toString(),
    val provisioningResultText: String = "",
    val infoText: String = "",
    val configText: String = "",
    val configSummary: GadgetConfigSummary = GadgetConfigSummary(),
    val scheduleSummary: GadgetScheduleSummary = GadgetScheduleSummary(),
    val diagnosticSummary: GadgetDiagnosticSummary = GadgetDiagnosticSummary(),
    val actuatorText: String = "",
    val airPumpEnabled: Boolean = false,
    val waterPumpEnabled: Boolean = false,
    val fanEnabled: Boolean = false,
    val fullLedValue: Float = 0f,
    val whiteLedValue: Float = 0f,
    val telemetry: DeviceTelemetry? = null,
    val serverSnapshots: List<GadgetCloudSnapshot> = emptyList(),
    val selectedServerGadgetId: String? = null,
    val selectedServerSnapshot: GadgetCloudSnapshot? = null,
    val serverTelemetry: DeviceTelemetry? = null,
    val serverEvents: List<GadgetCloudEvent> = emptyList(),
    val serverRouteStatus: GadgetRouteStatus = GadgetRouteStatus.NOT_CONFIGURED,
    val serverStatusMessage: String = "Укажите URL сервера Soll в настройках, чтобы видеть удаленную телеметрию.",
    val events: List<DeviceEvent> = emptyList(),
    val message: String? = null,
    val isError: Boolean = false,
    val isServerBusy: Boolean = false,
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val connector: WebSocketDeviceConnector,
    private val provisioningClient: DeviceProvisioningClient,
    private val settingsRepository: SettingsRepository,
    private val sollGateway: SollGateway,
    private val capabilityRegistry: CapabilityRegistry,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()
    private var eventsJob: Job? = null

    init {
        viewModelScope.launch {
            deviceRepository.ensureBuiltInProfiles()
        }
        viewModelScope.launch {
            deviceRepository.observeProfiles().collectLatest { profiles ->
                _uiState.update { state ->
                    state.copy(
                        profiles = profiles,
                        selectedProfileId = state.selectedProfileId
                            .takeIf { id -> profiles.any { it.id == id } }
                            ?: profiles.firstOrNull()?.id
                            ?: AquikDeviceProfile.ID,
                    )
                }
            }
        }
        viewModelScope.launch {
            deviceRepository.observeKnownDevices().collectLatest { devices ->
                _uiState.update { it.copy(knownDevices = devices) }
            }
        }
        viewModelScope.launch {
            connector.state.collectLatest { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        if (settingsRepository.sollServerUrl.isNotBlank()) {
            _uiState.update {
                it.copy(
                    serverRouteStatus = GadgetRouteStatus.STALE,
                    serverStatusMessage = "Серверный маршрут готов к проверке.",
                )
            }
            refreshServerGadgets(silent = true)
        }
    }

    fun updateHost(value: String) {
        _uiState.update { it.copy(hostInput = value.trim(), message = null) }
    }

    fun updatePort(value: String) {
        _uiState.update { it.copy(portInput = value.filter { char -> char.isDigit() }.take(5), message = null) }
    }

    fun updatePath(value: String) {
        _uiState.update { it.copy(pathInput = value.trim().trim('/'), message = null) }
    }

    fun updateToken(value: String) {
        _uiState.update { it.copy(tokenInput = value, message = null) }
    }

    fun selectProfile(profileId: String) {
        val profile = _uiState.value.profiles.firstOrNull { it.id == profileId }
            ?: BuiltInDeviceProfiles.byId(profileId)
            ?: return
        _uiState.update {
            it.copy(
                selectedProfileId = profile.id,
                tokenInput = if (profile.authMode == DeviceAuthMode.NONE) "" else it.tokenInput,
                message = null,
            )
        }
    }

    fun updateProvisioningApHost(value: String) {
        _uiState.update { it.copy(provisioningApHostInput = value.trim(), message = null) }
    }

    fun updateProvisioningSsid(value: String) {
        _uiState.update { it.copy(provisioningSsidInput = value, message = null) }
    }

    fun updateProvisioningPassword(value: String) {
        _uiState.update { it.copy(provisioningPasswordInput = value, message = null) }
    }

    fun updateProvisioningTimeout(value: String) {
        _uiState.update {
            it.copy(
                provisioningTimeoutInput = value.filter { char -> char.isDigit() }.take(3),
                message = null,
            )
        }
    }

    fun selectDevice(device: KnownDevice) {
        _uiState.update {
            it.copy(
                selectedDeviceId = device.id,
                selectedProfileId = device.profileId,
                hostInput = device.host,
                portInput = device.port.toString(),
                pathInput = device.path,
                tokenInput = settingsRepository.getDeviceAuthToken(device.id),
                message = null,
            )
        }
        observeEvents(device.id)
    }

    fun configureWifiViaSetupAp() {
        val state = _uiState.value
        val host = state.provisioningApHostInput.trim()
        val ssid = state.provisioningSsidInput.trim()
        val password = state.provisioningPasswordInput
        if (!ensureProvisioningInput(host, ssid)) return
        if (!ensureDeviceCapability("provisioning:$host")) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProvisioningBusy = true, message = null, isError = false) }
            provisioningClient.configureWifi(host, ssid, password).fold(
                onSuccess = { result ->
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = "provisioning:$host",
                            type = "wifi_configure",
                            summary = "Provisioning: Wi-Fi отправлен на Aquik AP для SSID $ssid",
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isProvisioningBusy = false,
                            provisioningResultText = result.rawJson,
                            message = "Wi-Fi отправлен. Переподключите телефон к домашней сети и найдите новый IP гаджета.",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    logProvisioningFailure(host, "wifi_configure_failed", error.message ?: "ошибка")
                    _uiState.update {
                        it.copy(
                            isProvisioningBusy = false,
                            message = error.message ?: "Не удалось отправить Wi-Fi на гаджет",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun startSmartConfigOnSetupAp() {
        val host = _uiState.value.provisioningApHostInput.trim()
        val timeout = _uiState.value.provisioningTimeoutInput.toIntOrNull()
            ?.coerceIn(10, 180)
            ?: AquikProvisioningDefaults.defaultSmartConfigTimeoutSec
        if (host.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "Укажите host точки настройки",
                    isError = true,
                )
            }
            return
        }
        if (!ensureDeviceCapability("provisioning:$host")) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProvisioningBusy = true, message = null, isError = false) }
            provisioningClient.startSmartConfig(host, timeout).fold(
                onSuccess = { result ->
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = "provisioning:$host",
                            type = "smartconfig_start",
                            summary = "Provisioning: SmartConfig запущен на $timeout с",
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isProvisioningBusy = false,
                            provisioningResultText = result.rawJson,
                            message = "SmartConfig запущен на устройстве",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    logProvisioningFailure(host, "smartconfig_failed", error.message ?: "ошибка")
                    _uiState.update {
                        it.copy(
                            isProvisioningBusy = false,
                            message = error.message ?: "Не удалось запустить SmartConfig",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun refreshProvisioningStatus() {
        val host = _uiState.value.provisioningApHostInput.trim()
        if (host.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "Укажите host точки настройки",
                    isError = true,
                )
            }
            return
        }
        if (!ensureDeviceCapability("provisioning:$host")) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProvisioningBusy = true, message = null, isError = false) }
            provisioningClient.getConnectionStatus(host).fold(
                onSuccess = { result ->
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = "provisioning:$host",
                            type = "provisioning_status",
                            summary = "Provisioning: статус точки настройки обновлен",
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isProvisioningBusy = false,
                            provisioningResultText = result.rawJson,
                            message = "Статус настройки обновлен",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    logProvisioningFailure(host, "provisioning_status_failed", error.message ?: "ошибка")
                    _uiState.update {
                        it.copy(
                            isProvisioningBusy = false,
                            message = error.message ?: "Не удалось получить статус настройки",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun connect() {
        val config = buildConfigOrShowError() ?: return
        if (!ensureDeviceCapability(config.deviceId)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }

            val token = _uiState.value.tokenInput.trim()
            if (config.profile.authMode == DeviceAuthMode.TOKEN && token.isNotBlank()) {
                settingsRepository.setDeviceAuthToken(config.deviceId, token)
            }
            val savedToken = token.ifBlank { settingsRepository.getDeviceAuthToken(config.deviceId) }
            val connectConfig = config.copy(
                token = if (config.profile.authMode == DeviceAuthMode.TOKEN) savedToken else "",
            )

            deviceRepository.upsertManualDevice(connectConfig, DeviceConnectionStatus.CONNECTING)
            connector.connect(connectConfig).fold(
                onSuccess = {
                    val status = connector.state.value.status
                    deviceRepository.upsertManualDevice(connectConfig, status)
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = connectConfig.deviceId,
                            type = "connected",
                            summary = "Подключен гаджет ${connectConfig.endpointUrl()}",
                        )
                    )
                    _uiState.update {
                        it.copy(
                            selectedDeviceId = connectConfig.deviceId,
                            isBusy = false,
                            message = "Гаджет подключен",
                            isError = false,
                        )
                    }
                    observeEvents(connectConfig.deviceId)
                    refreshInfo()
                    refreshSensors()
                },
                onFailure = { error ->
                    deviceRepository.upsertManualDevice(connectConfig, DeviceConnectionStatus.ERROR)
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = connectConfig.deviceId,
                            type = "connect_failed",
                            summary = "Подключение не удалось: ${error.message ?: "ошибка"}",
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = error.message ?: "Не удалось подключиться к устройству",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun disconnect() {
        val deviceId = _uiState.value.selectedDeviceId
        connector.disconnect()
        viewModelScope.launch {
            deviceId?.let {
                deviceRepository.updateDeviceStatus(it, DeviceConnectionStatus.DISCONNECTED)
            }
        }
    }

    fun refreshServerGadgets(silent: Boolean = false) {
        if (settingsRepository.sollServerUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    serverRouteStatus = GadgetRouteStatus.NOT_CONFIGURED,
                    serverStatusMessage = "Укажите URL сервера Soll в настройках.",
                    isServerBusy = false,
                )
            }
            return
        }
        if (!ensureDeviceCapability("soll-server")) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isServerBusy = true,
                    serverRouteStatus = GadgetRouteStatus.SYNCING,
                    serverStatusMessage = "Читаю серверные гаджеты...",
                )
            }
            sollGateway.getGadgetSnapshots().fold(
                onSuccess = { snapshots ->
                    val current = _uiState.value
                    val selected = snapshots.firstOrNull { it.id == current.selectedServerGadgetId }
                        ?: snapshots.firstOrNull { it.id == current.selectedDeviceId }
                        ?: snapshots.firstOrNull()
                    _uiState.update {
                        it.copy(
                            serverSnapshots = snapshots,
                            selectedServerGadgetId = selected?.id,
                            selectedServerSnapshot = selected,
                            serverTelemetry = selected?.let(GadgetPayloadParser::telemetry),
                            serverRouteStatus = snapshots.routeStatus(),
                            serverStatusMessage = snapshots.serverStatusText(),
                            isServerBusy = false,
                            message = if (!silent) "Серверные гаджеты обновлены" else it.message,
                            isError = false,
                        )
                    }
                    selected?.id?.let { refreshServerEvents(it, silent = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isServerBusy = false,
                            serverRouteStatus = GadgetRouteStatus.ERROR,
                            serverStatusMessage = error.message ?: "Не удалось получить гаджеты с сервера Soll",
                            message = if (!silent) error.message ?: "Не удалось получить гаджеты с сервера Soll" else it.message,
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun selectServerSnapshot(snapshot: GadgetCloudSnapshot) {
        _uiState.update {
            it.copy(
                selectedServerGadgetId = snapshot.id,
                selectedServerSnapshot = snapshot,
                serverTelemetry = GadgetPayloadParser.telemetry(snapshot),
                serverStatusMessage = snapshot.serverStatusText(),
            )
        }
        refreshServerEvents(snapshot.id, silent = true)
    }

    fun refreshSelectedServerGadget() {
        val gadgetId = _uiState.value.selectedServerGadgetId
        if (gadgetId.isNullOrBlank()) {
            refreshServerGadgets()
            return
        }
        if (!ensureDeviceCapability(gadgetId)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isServerBusy = true,
                    serverRouteStatus = GadgetRouteStatus.SYNCING,
                    serverStatusMessage = "Обновляю состояние $gadgetId...",
                )
            }
            sollGateway.getGadgetLatest(gadgetId).fold(
                onSuccess = { snapshot ->
                    _uiState.update {
                        val snapshots = it.serverSnapshots.replaceSnapshot(snapshot)
                        it.copy(
                            serverSnapshots = snapshots,
                            selectedServerGadgetId = snapshot.id,
                            selectedServerSnapshot = snapshot,
                            serverTelemetry = GadgetPayloadParser.telemetry(snapshot),
                            serverRouteStatus = snapshots.routeStatus(),
                            serverStatusMessage = snapshot.serverStatusText(),
                            isServerBusy = false,
                            message = "Серверное состояние обновлено",
                            isError = false,
                        )
                    }
                    refreshServerEvents(snapshot.id, silent = true)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isServerBusy = false,
                            serverRouteStatus = GadgetRouteStatus.ERROR,
                            serverStatusMessage = error.message ?: "Не удалось обновить серверное состояние",
                            message = error.message ?: "Не удалось обновить серверное состояние",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun refreshInfo() {
        runDeviceCommand(
            summary = "Информация гаджета обновлена",
            command = { connector.getInfo() },
            onSuccess = { response ->
                _uiState.update { it.copy(infoText = GadgetPayloadParser.prettyJson(response.dataJson)) }
            },
        )
    }

    fun refreshConfig() {
        runDeviceCommand(
            summary = "Конфигурация гаджета обновлена",
            command = { connector.getConfig() },
            onSuccess = { response ->
                _uiState.update {
                    it.copy(
                        configText = GadgetPayloadParser.prettyJson(response.dataJson),
                        configSummary = GadgetPayloadParser.config(response),
                    )
                }
            },
        )
    }

    fun refreshSchedules() {
        runDeviceCommand(
            summary = "Расписания гаджета обновлены",
            command = { connector.executeCommand(AquikDeviceProfile.COMMAND_GET_SCHEDULES) },
            onSuccess = { response ->
                _uiState.update {
                    it.copy(scheduleSummary = GadgetPayloadParser.schedules(response))
                }
            },
        )
    }

    fun scanI2c() {
        runDeviceCommand(
            summary = "Диагностика I2C обновлена",
            command = { connector.executeCommand(AquikDeviceProfile.COMMAND_SCAN_I2C) },
            onSuccess = { response ->
                _uiState.update {
                    it.copy(diagnosticSummary = GadgetPayloadParser.diagnostics(response))
                }
            },
        )
    }

    fun refreshSensors() {
        runDeviceCommand(
            summary = "Телеметрия гаджета обновлена",
            command = { connector.getSensors() },
            onSuccess = { response ->
                val deviceId = _uiState.value.selectedDeviceId ?: connector.state.value.deviceId ?: "manual"
                _uiState.update { it.copy(telemetry = GadgetPayloadParser.telemetry(response, deviceId)) }
            },
        )
    }

    fun refreshActuators() {
        runDeviceCommand(
            summary = "Состояние актуаторов обновлено",
            command = { connector.getActuators() },
            onSuccess = { response ->
                val snapshot = GadgetPayloadParser.actuators(response)
                _uiState.update {
                    it.copy(
                        actuatorText = GadgetPayloadParser.prettyJson(response.dataJson),
                        airPumpEnabled = snapshot.airPump ?: it.airPumpEnabled,
                        waterPumpEnabled = snapshot.waterPump ?: it.waterPumpEnabled,
                        fanEnabled = snapshot.fan ?: it.fanEnabled,
                        fullLedValue = snapshot.fullLed?.toFloat() ?: it.fullLedValue,
                        whiteLedValue = snapshot.whiteLed?.toFloat() ?: it.whiteLedValue,
                    )
                }
            },
        )
    }

    fun setAirPump(enabled: Boolean) {
        setPump(DevicePumpType.AIR, enabled)
    }

    fun setWaterPump(enabled: Boolean) {
        setPump(DevicePumpType.WATER, enabled)
    }

    fun setFan(enabled: Boolean) {
        runDeviceCommand(
            summary = "Вентилятор ${enabled.onOffText()}",
            command = { connector.setFan(enabled) },
            onSuccess = { response ->
                _uiState.update {
                    it.copy(
                        fanEnabled = enabled,
                        actuatorText = GadgetPayloadParser.prettyJson(response.dataJson),
                    )
                }
            },
        )
    }

    fun updateFullLedValue(value: Float) {
        _uiState.update { it.copy(fullLedValue = value.coerceIn(0f, 255f)) }
    }

    fun updateWhiteLedValue(value: Float) {
        _uiState.update { it.copy(whiteLedValue = value.coerceIn(0f, 255f)) }
    }

    fun applyFullLedValue() {
        setLed(DeviceLedType.FULL, _uiState.value.fullLedValue.toInt().coerceIn(0, 255))
    }

    fun applyWhiteLedValue() {
        setLed(DeviceLedType.WHITE, _uiState.value.whiteLedValue.toInt().coerceIn(0, 255))
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun observeEvents(deviceId: String) {
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            deviceRepository.observeEvents(deviceId).collectLatest { events ->
                _uiState.update { it.copy(events = events) }
            }
        }
    }

    private fun refreshServerEvents(gadgetId: String, silent: Boolean) {
        viewModelScope.launch {
            sollGateway.getGadgetEvents(gadgetId, limit = 20).fold(
                onSuccess = { events ->
                    _uiState.update { it.copy(serverEvents = events) }
                },
                onFailure = { error ->
                    if (!silent) {
                        _uiState.update {
                            it.copy(
                                serverStatusMessage = error.message ?: "Не удалось получить события сервера",
                                isError = true,
                            )
                        }
                    }
                },
            )
        }
    }

    private fun runDeviceCommand(
        summary: String,
        command: suspend () -> Result<DeviceCommandResponse>,
        onSuccess: (DeviceCommandResponse) -> Unit,
    ) {
        val deviceId = _uiState.value.selectedDeviceId ?: connector.state.value.deviceId
        if (!ensureDeviceCapability(deviceId)) return
        if (deviceId == null || connector.state.value.status == DeviceConnectionStatus.DISCONNECTED) {
            _uiState.update {
                it.copy(
                    message = "Сначала подключите гаджет",
                    isError = true,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
            command().fold(
                onSuccess = { response ->
                    onSuccess(response)
                    deviceRepository.updateDeviceStatus(deviceId, connector.state.value.status)
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = deviceId,
                            type = response.command,
                            summary = summary,
                            payloadJson = response.rawJson,
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = summary,
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    deviceRepository.updateDeviceStatus(deviceId, DeviceConnectionStatus.ERROR)
                    deviceRepository.logEvent(
                        DeviceEvent(
                            deviceId = deviceId,
                            type = "command_failed",
                            summary = "Команда гаджета не выполнена: ${error.message ?: "ошибка"}",
                        )
                    )
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = error.message ?: "Команда гаджета не выполнена",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    private fun setPump(type: DevicePumpType, enabled: Boolean) {
        runDeviceCommand(
            summary = "${type.label} ${enabled.onOffText()}",
            command = { connector.setPump(type, enabled) },
            onSuccess = { response ->
                _uiState.update {
                    when (type) {
                        DevicePumpType.AIR -> it.copy(
                            airPumpEnabled = enabled,
                            actuatorText = GadgetPayloadParser.prettyJson(response.dataJson),
                        )
                        DevicePumpType.WATER -> it.copy(
                            waterPumpEnabled = enabled,
                            actuatorText = GadgetPayloadParser.prettyJson(response.dataJson),
                        )
                    }
                }
            },
        )
    }

    private fun setLed(type: DeviceLedType, value: Int) {
        runDeviceCommand(
            summary = "${type.label}: $value/255",
            command = { connector.setLed(type, value) },
            onSuccess = { response ->
                _uiState.update {
                    when (type) {
                        DeviceLedType.FULL -> it.copy(
                            fullLedValue = value.toFloat(),
                            actuatorText = GadgetPayloadParser.prettyJson(response.dataJson),
                        )
                        DeviceLedType.WHITE -> it.copy(
                            whiteLedValue = value.toFloat(),
                            actuatorText = GadgetPayloadParser.prettyJson(response.dataJson),
                        )
                    }
                }
            },
        )
    }

    private fun buildConfigOrShowError(): DeviceConnectionConfig? {
        val state = _uiState.value
        val host = state.hostInput.trim()
        val port = state.portInput.toIntOrNull()?.coerceIn(1, 65535)
        val profile = state.profiles.firstOrNull { it.id == state.selectedProfileId }
            ?: BuiltInDeviceProfiles.byId(state.selectedProfileId)
            ?: AquikDeviceProfile.profile
        val endpoint = DeviceEndpoint.normalize(
            host = host,
            port = port ?: 0,
            path = state.pathInput.ifBlank { "ws" },
        )
        if (host.isBlank() || endpoint.host.isBlank() || endpoint.port !in 1..65535) {
            _uiState.update {
                it.copy(
                    message = "Укажите IP/host и порт гаджета",
                    isError = true,
                )
            }
            return null
        }
        return DeviceConnectionConfig(
            profile = profile,
            host = endpoint.storageHost(),
            port = endpoint.port,
            path = endpoint.path.ifBlank { "ws" },
            token = state.tokenInput.trim(),
        )
    }

    private fun ensureProvisioningInput(host: String, ssid: String): Boolean {
        if (host.isBlank() || ssid.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "Укажите host точки настройки и SSID домашней Wi-Fi сети",
                    isError = true,
                )
            }
            return false
        }
        return true
    }

    private fun logProvisioningFailure(host: String, type: String, message: String) {
        viewModelScope.launch {
            deviceRepository.logEvent(
                DeviceEvent(
                    deviceId = "provisioning:$host",
                    type = type,
                    summary = "Provisioning: $message",
                )
            )
        }
    }

    private fun ensureDeviceCapability(deviceId: String?): Boolean {
        val decision = capabilityRegistry.checkCommand(DEVICE_CAPABILITY_ID)
        if (decision.allowed) return true

        val message = decision.message.ifBlank {
            "Подключение к гаджетам заблокировано политикой возможностей."
        }
        _uiState.update {
            it.copy(
                message = "$message Включите: Настройки -> Возможности -> Железо двойного назначения -> Гаджеты.",
                isError = true,
            )
        }
        viewModelScope.launch {
            deviceRepository.logEvent(
                DeviceEvent(
                    deviceId = deviceId ?: "device-policy",
                    type = "capability_blocked",
                    summary = message,
                )
            )
        }
        return false
    }

    private companion object {
        const val DEVICE_CAPABILITY_ID = "devices"
    }
}

private fun Boolean.onOffText(): String = if (this) "включен" else "выключен"

private fun List<GadgetCloudSnapshot>.routeStatus(): GadgetRouteStatus = when {
    isEmpty() -> GadgetRouteStatus.STALE
    any { !it.stale } -> GadgetRouteStatus.ONLINE
    else -> GadgetRouteStatus.STALE
}

private fun List<GadgetCloudSnapshot>.serverStatusText(): String = when {
    isEmpty() -> "На сервере пока нет привязанных ESP-гаджетов."
    any { !it.stale } -> "Сервер Soll получает свежие данные."
    else -> "Сервер отвечает, но данные гаджетов устарели."
}

private fun GadgetCloudSnapshot.serverStatusText(): String =
    if (stale) {
        "Данные $name устарели. Последняя телеметрия: ${lastTelemetryAt ?: "нет"}."
    } else {
        "Свежие данные $name. Последняя телеметрия: ${lastTelemetryAt ?: "нет"}."
    }

private fun List<GadgetCloudSnapshot>.replaceSnapshot(snapshot: GadgetCloudSnapshot): List<GadgetCloudSnapshot> =
    if (any { it.id == snapshot.id }) {
        map { if (it.id == snapshot.id) snapshot else it }
    } else {
        listOf(snapshot) + this
    }
