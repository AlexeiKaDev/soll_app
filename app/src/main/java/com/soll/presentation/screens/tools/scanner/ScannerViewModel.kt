package com.soll.presentation.screens.tools.scanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.ScanItemEntity
import com.soll.data.local.entity.ScanSessionEntity
import com.soll.data.repository.DeviceRepository
import com.soll.data.repository.GadgetServerSyncScheduler
import com.soll.data.repository.ScanAddResult
import com.soll.data.repository.ScannerRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.data.repository.TaskCacheRepository
import com.soll.data.service.AndroidPushTokenRegistrar
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.device.AquikDeviceProfile
import com.soll.domain.device.BuiltInDeviceProfiles
import com.soll.domain.device.DeviceConnectionConfig
import com.soll.domain.device.DeviceConnectionStatus
import com.soll.domain.device.DeviceEvent
import com.soll.domain.securitylab.SensitivePayloadRedactor
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollPairingPayload
import com.soll.domain.soll.SollPairingPayloadParser
import com.soll.domain.soll.SollTask
import com.soll.domain.scanner.ScanConfirmationGate
import com.soll.domain.scanner.ScannerDevicePairingParser
import com.soll.domain.scanner.ScannerSettings
import com.soll.domain.tool.ToolHandler
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobProgressSink
import com.soll.domain.tool.ToolJobResult
import com.soll.domain.tool.ToolJobRunner
import com.soll.domain.tool.ToolJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class ScannerUiState(
    val session: ScanSessionEntity? = null,
    val items: List<ScanItemEntity> = emptyList(),
    val input: String = "",
    val selectedIds: Set<String> = emptySet(),
    val cameraEnabled: Boolean = false,
    val cameraStatus: String? = null,
    val settings: ScannerSettings = ScannerSettings(),
    val showSettings: Boolean = false,
    val taskCandidates: List<SollTask> = emptyList(),
    val showTaskPicker: Boolean = false,
    val isExporting: Boolean = false,
    val isActionRunning: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val scannerRepository: ScannerRepository,
    private val settingsRepository: SettingsRepository,
    private val taskCacheRepository: TaskCacheRepository,
    private val deviceRepository: DeviceRepository,
    private val sollGateway: SollGateway,
    private val toolJobRunner: ToolJobRunner,
    private val capabilityRegistry: CapabilityRegistry,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()
    private val confirmationGate = ScanConfirmationGate()
    private var pairingBarcodeInFlight = false

    init {
        _uiState.update { it.copy(settings = settingsRepository.getScannerSettings()) }
        viewModelScope.launch {
            val session = scannerRepository.ensureSession()
            _uiState.update { it.copy(session = session) }
            scannerRepository.observeItems(session.id).collectLatest { items ->
                _uiState.update { state ->
                    state.copy(
                        items = items,
                        selectedIds = state.selectedIds.intersect(items.map { it.id }.toSet()),
                    )
                }
            }
        }
    }

    fun updateInput(value: String) {
        _uiState.update { it.copy(input = value, message = null, isError = false) }
    }

    fun addManualScan() {
        if (!ensureScannerCapability()) return
        val value = _uiState.value.input
        val settings = _uiState.value.settings
        viewModelScope.launch {
            runCatching {
                scannerRepository.addScan(
                    rawValue = value,
                    duplicatePolicy = settings.duplicatePolicy,
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        input = "",
                        selectedIds = it.selectedIds + result.item.id,
                        message = result.manualMessage(),
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        message = error.message ?: "Не удалось добавить скан",
                        isError = true,
                    )
                }
            }
        }
    }

    fun setCameraEnabled(
        enabled: Boolean,
        requireScannerCapability: Boolean = true,
        cameraStatus: String? = null,
    ) {
        if (enabled && requireScannerCapability && !ensureScannerCapability()) return
        pairingBarcodeInFlight = false
        confirmationGate.reset()
        _uiState.update {
            it.copy(
                cameraEnabled = enabled,
                cameraStatus = if (enabled) {
                    cameraStatus ?: "Наведи камеру на код. Нужно 2 совпадения подряд."
                } else {
                    null
                },
            )
        }
    }

    fun showCameraPermissionDenied() {
        confirmationGate.reset()
        _uiState.update {
            it.copy(
                cameraEnabled = false,
                cameraStatus = null,
                message = "Разреши камеру для сканирования",
                isError = true,
            )
        }
    }

    fun handleCameraBarcode(rawValue: String, format: String, pairingOnly: Boolean = false) {
        if (pairingOnly) {
            handlePairingCameraBarcode(rawValue)
            return
        }
        if (!pairingOnly && !ensureScannerCapability()) {
            setCameraEnabled(false)
            return
        }
        val result = confirmationGate.observe(rawValue = rawValue, format = format)
        if (result.ignoredByCooldown || result.value.isBlank()) return
        if (!result.confirmed) {
            _uiState.update {
                it.copy(
                    cameraStatus = "Подтверждаю ${result.matchCount}/${result.requiredMatches}: ${result.value}",
                    message = null,
                    isError = false,
                )
            }
            return
        }

        viewModelScope.launch {
            val pairingPayload = SollPairingPayloadParser.parse(result.value)
            if (pairingPayload != null) {
                applySollPairingPayload(pairingPayload, reason = "scanner_qr_pairing")
                return@launch
            }
            if (pairingOnly) {
                _uiState.update {
                    it.copy(
                        cameraStatus = "Это не QR pairing Soll",
                        message = "Наведи камеру на QR из Desktop",
                        isError = true,
                    )
                }
                return@launch
            }

            val settings = _uiState.value.settings
            runCatching {
                scannerRepository.addScan(
                    rawValue = result.value,
                    detectedFormat = result.format,
                    duplicatePolicy = settings.duplicatePolicy,
                )
            }.onSuccess { addResult ->
                _uiState.update {
                    it.copy(
                        selectedIds = it.selectedIds + addResult.item.id,
                        cameraStatus = addResult.cameraMessage(),
                        message = null,
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        cameraStatus = null,
                        message = error.message ?: "Не удалось добавить скан с камеры",
                        isError = true,
                    )
                }
            }
        }
    }

    private fun handlePairingCameraBarcode(rawValue: String) {
        val value = rawValue.trim()
        if (value.isBlank() || pairingBarcodeInFlight) return

        val pairingPayload = SollPairingPayloadParser.parse(value)
        if (pairingPayload == null) {
            _uiState.update {
                it.copy(
                    cameraStatus = "QR найден, но это не pairing Soll",
                    message = "Открой QR pairing в Desktop и наведи камеру на него",
                    isError = true,
                )
            }
            return
        }

        pairingBarcodeInFlight = true
        _uiState.update {
            it.copy(
                cameraStatus = "QR pairing найден",
                message = "Применяю настройки Soll из QR",
                isError = false,
            )
        }
        applySollPairingPayload(pairingPayload, reason = "scanner_qr_pairing")
    }

    private fun applySollPairingPayload(payload: SollPairingPayload, reason: String) {
        settingsRepository.applySollPairingPayload(payload)
        GadgetServerSyncScheduler.schedule(appContext, settingsRepository)
        SollServerSyncScheduler.schedule(appContext, settingsRepository)
        _uiState.update {
            it.copy(
                cameraEnabled = false,
                cameraStatus = "Soll QR применен",
                isActionRunning = true,
                message = "Настройки Soll применены из QR. Регистрирую push-токен",
                isError = false,
            )
        }
        AndroidPushTokenRegistrar.registerCurrentToken(
            appContext,
            reason = reason,
            force = true,
        ) {
            viewModelScope.launch {
                val lastError = settingsRepository.sollPushTokenLastError
                _uiState.update {
                    it.copy(
                        isActionRunning = false,
                        message = if (lastError.isBlank()) {
                            "Soll QR применен. Push-токен зарегистрирован"
                        } else {
                            "Soll QR применен, push-токен не зарегистрирован"
                        },
                        isError = lastError.isNotBlank(),
                    )
                }
            }
        }
    }

    fun toggleSelected(id: String) {
        _uiState.update {
            val next = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = next)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedIds = it.items.map { item -> item.id }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun updateScannerSettings(settings: ScannerSettings) {
        settingsRepository.saveScannerSettings(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun toggleTaskPicker() {
        if (!ensureCapabilities(SCANNER_CAPABILITY_ID, RAW_CAPABILITY_ID)) return
        if (_uiState.value.showTaskPicker) {
            _uiState.update { it.copy(showTaskPicker = false) }
            return
        }
        viewModelScope.launch {
            val board = taskCacheRepository.getCachedBoard()
            val tasks = (board.today + board.blocked + board.inbox + board.stale + board.deferred)
                .distinctBy { it.id }
                .take(12)
            _uiState.update {
                it.copy(
                    taskCandidates = tasks,
                    showTaskPicker = true,
                    message = if (tasks.isEmpty()) "В кеше задач нет открытых задач. Сначала синхронизируй задачи." else null,
                    isError = tasks.isEmpty(),
                )
            }
        }
    }

    fun attachSelectedToTask(task: SollTask) {
        if (!ensureCapabilities(SCANNER_CAPABILITY_ID, RAW_CAPABILITY_ID)) return
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(message = "Выбери сканы для задачи", isError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionRunning = true, message = null, isError = false) }
            runCatching {
                val items = scannerRepository.getItems(ids)
                require(items.isNotEmpty()) { "Выбранные сканы не найдены" }
                val note = sollGateway.createRawNote(
                    title = "Сканы к задаче: ${task.title}",
                    content = buildTaskAttachmentContent(task, items),
                    tags = listOf("scanner", "task-evidence", "task-${task.id.take(8)}"),
                ).getOrThrow()
                scannerRepository.markExported(items.map { it.id })
                note
            }.onSuccess { note ->
                _uiState.update {
                    it.copy(
                        isActionRunning = false,
                        showTaskPicker = false,
                        selectedIds = emptySet(),
                        message = "Сканы прикреплены к задаче: ${note.filename}",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionRunning = false,
                        message = error.message ?: "Не удалось прикрепить сканы к задаче",
                        isError = true,
                    )
                }
            }
        }
    }

    fun pairSelectedDevice() {
        if (!ensureCapabilities(SCANNER_CAPABILITY_ID, DEVICE_CAPABILITY_ID)) return
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(message = "Выбери QR или код устройства", isError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionRunning = true, message = null, isError = false) }
            runCatching {
                val items = scannerRepository.getItems(ids)
                val pair = items.asSequence()
                    .mapNotNull { item ->
                        ScannerDevicePairingParser.parse(item.rawValue)
                            ?.let { payload -> item to payload }
                    }
                    .firstOrNull()
                    ?: error("В выбранных сканах нет данных устройства")
                val payload = pair.second
                val profile = BuiltInDeviceProfiles.byId(payload.profileId) ?: AquikDeviceProfile.profile
                val config = DeviceConnectionConfig(
                    profile = profile,
                    host = payload.host,
                    port = payload.port,
                    path = payload.path,
                    token = payload.token,
                )
                deviceRepository.ensureBuiltInProfiles()
                val device = deviceRepository.upsertManualDevice(config, DeviceConnectionStatus.DISCONNECTED)
                if (payload.token.isNotBlank()) {
                    settingsRepository.setDeviceAuthToken(device.id, payload.token)
                }
                deviceRepository.logEvent(
                    DeviceEvent(
                        deviceId = device.id,
                        type = "paired_from_scanner",
                        summary = "Гаджет добавлен из сканера: ${device.endpointUrl()}",
                        payloadJson = SensitivePayloadRedactor.redactSecrets(pair.first.rawValue),
                    )
                )
                device
            }.onSuccess { device ->
                _uiState.update {
                    it.copy(
                        isActionRunning = false,
                        message = "Гаджет добавлен: ${device.endpointUrl()}",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isActionRunning = false,
                        message = error.message ?: "Не удалось добавить гаджет",
                        isError = true,
                    )
                }
            }
        }
    }

    fun exportSelected() {
        if (!ensureCapabilities(SCANNER_CAPABILITY_ID, RAW_CAPABILITY_ID)) return
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(message = "Выбери сканы для экспорта", isError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, message = null, isError = false) }
            val job = toolJobRunner.run(
                toolId = SCANNER_EXPORT_TOOL_ID,
                inputJson = JSONObject().put("count", ids.size).toString(),
                handler = object : ToolHandler {
                    override val toolId: String = SCANNER_EXPORT_TOOL_ID

                    override suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult {
                        progress.updateProgress(20, "Готовлю выбранные сканы")
                        val items = scannerRepository.getItems(ids)
                        require(items.isNotEmpty()) { "Выбранные сканы не найдены" }
                        progress.updateProgress(60, "Отправляю в raw Soll")
                        val note = sollGateway.createRawNote(
                            title = "Сканы ${items.size}",
                            content = buildRawContent(items),
                            tags = listOf("scanner", "barcode"),
                        ).getOrThrow()
                        scannerRepository.markExported(items.map { it.id })
                        return ToolJobResult(
                            outputJson = JSONObject()
                                .put("filename", note.filename)
                                .put("path", note.path)
                                .put("count", items.size)
                                .toString(),
                            logText = "Сканы экспортированы: ${note.filename}",
                        )
                    }
                },
            )
            val ok = job.status == ToolJobStatus.SUCCESS
            _uiState.update {
                it.copy(
                    isExporting = false,
                    selectedIds = if (ok) emptySet() else it.selectedIds,
                    message = if (ok) job.logText.ifBlank { "Сканы экспортированы" } else job.logText.ifBlank { "Экспорт не выполнен" },
                    isError = !ok,
                )
            }
        }
    }

    fun ensureScannerCapability(): Boolean = ensureCapabilities(SCANNER_CAPABILITY_ID)

    private fun ensureCapabilities(vararg capabilityIds: String): Boolean {
        val blocked = capabilityIds
            .asSequence()
            .map { capabilityRegistry.checkCommand(it) }
            .firstOrNull { !it.allowed }
            ?: return true
        val message = blocked.message.ifBlank {
            "Действие заблокировано политикой возможностей."
        }
        _uiState.update {
            it.copy(
                cameraEnabled = if (blocked.capability?.id == SCANNER_CAPABILITY_ID) false else it.cameraEnabled,
                cameraStatus = if (blocked.capability?.id == SCANNER_CAPABILITY_ID) null else it.cameraStatus,
                isExporting = false,
                isActionRunning = false,
                message = "$message Включите нужную возможность в настройках.",
                isError = true,
            )
        }
        return false
    }

    private fun buildRawContent(items: List<ScanItemEntity>): String = buildString {
        append("# Сканы\n\n")
        items.forEach { item ->
            append("- `${item.normalizedValue}`")
            append(" — ${item.format}")
            if (item.count > 1) append(", повторов: ${item.count}")
            append("\n")
        }
    }

    private fun buildTaskAttachmentContent(task: SollTask, items: List<ScanItemEntity>): String = buildString {
        append("# Сканы к задаче\n\n")
        append("- Задача: ${task.title}\n")
        append("- ID: ${task.id}\n")
        task.projectName?.takeIf { it.isNotBlank() }?.let { append("- Проект: $it\n") }
        append("\n## Сканы\n\n")
        items.forEach { item ->
            append("- `${item.normalizedValue}`")
            append(" — ${item.format}")
            if (item.count > 1) append(", повторов: ${item.count}")
            append("\n")
        }
    }

    private fun ScanAddResult.manualMessage(): String =
        when {
            !duplicate -> "Скан добавлен"
            stored -> "Дубликат обновлен: ${item.normalizedValue}"
            else -> "Дубликат уже есть: ${item.normalizedValue}"
        }

    private fun ScanAddResult.cameraMessage(): String =
        when {
            !duplicate -> "Код добавлен: ${item.normalizedValue}"
            stored -> "Дубликат обновлен: ${item.normalizedValue}"
            else -> "Дубликат пропущен: ${item.normalizedValue}"
        }

    private companion object {
        const val SCANNER_EXPORT_TOOL_ID = "scanner_export"
        const val SCANNER_CAPABILITY_ID = "scanner"
        const val RAW_CAPABILITY_ID = "raw"
        const val DEVICE_CAPABILITY_ID = "devices"
    }
}
