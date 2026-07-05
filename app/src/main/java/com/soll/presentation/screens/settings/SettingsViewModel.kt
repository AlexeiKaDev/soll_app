package com.soll.presentation.screens.settings

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.notification.SystemNotificationImportanceMode
import com.soll.data.notification.SystemNotificationPreferences
import com.soll.data.repository.DeviceQaRepository
import com.soll.data.repository.GadgetServerSyncScheduler
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.data.service.AndroidPushTokenRegistrar
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.assistant.RiskTier
import com.soll.domain.assistant.isRisky
import com.soll.domain.deviceqa.DeviceQaCheck
import com.soll.domain.deviceqa.DeviceQaCheckId
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.soll.SollAndroidSyncStatus
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollTaskBoard
import com.soll.ui.theme.SollThemeVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isBatteryOptimizationDisabled: Boolean = false,
    val riskyCapabilitiesEnabled: Boolean = true,
    val capabilityGroups: List<CapabilityGroupUiState> = emptyList(),
    val sollServerUrl: String = "",
    val sollApiPathPrefix: String = "",
    val sollAccessToken: String = "",
    val sollSyncIntervalMinutes: String = "60",
    val sollWifiOnlyUpload: Boolean = true,
    val sollHealthStatus: String? = null,
    val sollHealthMessage: String? = null,
    val isCheckingSollHealth: Boolean = false,
    val isSyncingSoll: Boolean = false,
    val sollSyncSummary: String? = null,
    val voiceRequiresUnlockedDevice: Boolean = true,
    val voiceRequiresHeadset: Boolean = false,
    val voiceLocalOnly: Boolean = false,
    val voiceWakePhraseRequired: Boolean = false,
    val proactiveSuggestionsEnabled: Boolean = true,
    val proactiveSuggestionsDailyLimit: Int = 3,
    val proactiveSystemDeliveryEnabled: Boolean = false,
    val systemNotificationImportanceMode: SystemNotificationImportanceMode = SystemNotificationImportanceMode.DEFAULT_AND_HIGH,
    val systemNotificationChannels: List<SystemNotificationChannelUiState> = emptyList(),
    val sollPushTokenRegisteredAt: Long = 0L,
    val sollPushTokenLastError: String = "",
    val isRetryingSollPushToken: Boolean = false,
    val assistantMemoryEnabled: Boolean = true,
    val deviceQaChecks: List<DeviceQaCheck> = emptyList(),
    val appThemeVariant: SollThemeVariant = SollThemeVariant.default,
    val isPostingDeviceQaNotification: Boolean = false,
    val deviceQaReport: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

data class CapabilityGroupUiState(
    val riskTier: RiskTier,
    val items: List<CapabilityItemUiState>,
)

data class CapabilityItemUiState(
    val id: String,
    val name: String,
    val description: String,
    val configuredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val blockedByGlobalRiskToggle: Boolean,
    val requiresConfirmation: Boolean,
    val auditRequired: Boolean,
    val permissions: List<String>,
)

data class SystemNotificationChannelUiState(
    val channel: SollNotificationChannel,
    val enabled: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val capabilityRegistry: CapabilityRegistry,
    private val sollRepository: SollRepository,
    private val deviceQaRepository: DeviceQaRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.update { state ->
            state.copy(
                isBatteryOptimizationDisabled = checkBatteryOptimization(),
                riskyCapabilitiesEnabled = settingsRepository.isRiskyCapabilitiesEnabled(),
                capabilityGroups = buildCapabilityGroups(),
                sollServerUrl = settingsRepository.sollServerUrl,
                sollApiPathPrefix = settingsRepository.sollApiPathPrefix,
                sollAccessToken = settingsRepository.sollAccessToken,
                sollSyncIntervalMinutes = settingsRepository.sollSyncIntervalMinutes.toString(),
                sollWifiOnlyUpload = settingsRepository.sollWifiOnlyUpload,
                voiceRequiresUnlockedDevice = settingsRepository.voiceRequiresUnlockedDevice,
                voiceRequiresHeadset = settingsRepository.voiceRequiresHeadset,
                voiceLocalOnly = settingsRepository.voiceLocalOnly,
                voiceWakePhraseRequired = settingsRepository.voiceWakePhraseRequired,
                proactiveSuggestionsEnabled = settingsRepository.proactiveSuggestionsEnabled,
                proactiveSuggestionsDailyLimit = settingsRepository.proactiveSuggestionsDailyLimit,
                proactiveSystemDeliveryEnabled = settingsRepository.proactiveSystemDeliveryEnabled,
                systemNotificationImportanceMode = settingsRepository.systemNotificationImportanceMode,
                systemNotificationChannels = buildSystemNotificationChannels(),
                sollPushTokenRegisteredAt = settingsRepository.sollPushTokenRegisteredAt,
                sollPushTokenLastError = settingsRepository.sollPushTokenLastError,
                assistantMemoryEnabled = settingsRepository.assistantMemoryEnabled,
                deviceQaChecks = deviceQaRepository.checks(),
                appThemeVariant = SollThemeVariant.fromStorage(settingsRepository.appThemeVariant),
            )
        }
    }

    fun setRiskyCapabilitiesEnabled(enabled: Boolean) {
        settingsRepository.setRiskyCapabilitiesEnabled(enabled)
        refreshCapabilitySettings()
    }

    fun setCapabilityEnabled(capabilityId: String, enabled: Boolean) {
        settingsRepository.setCapabilityEnabled(capabilityId, enabled)
        refreshCapabilitySettings()
    }

    fun updateSollServerUrl(value: String) {
        _uiState.update {
            it.copy(
                sollServerUrl = value,
                sollHealthStatus = null,
                sollHealthMessage = null,
                sollSyncSummary = null,
            )
        }
    }

    fun updateSollApiPathPrefix(value: String) {
        _uiState.update {
            it.copy(
                sollApiPathPrefix = value,
                sollHealthStatus = null,
                sollHealthMessage = null,
                sollSyncSummary = null,
            )
        }
    }

    fun updateSollAccessToken(value: String) {
        _uiState.update {
            it.copy(
                sollAccessToken = value,
                sollHealthStatus = null,
                sollHealthMessage = null,
                sollSyncSummary = null,
            )
        }
    }

    fun updateSollSyncInterval(value: String) {
        val filtered = value.filter { it.isDigit() }.take(4)
        _uiState.update { it.copy(sollSyncIntervalMinutes = filtered.ifBlank { "5" }) }
    }

    fun setSollWifiOnlyUpload(enabled: Boolean) {
        settingsRepository.sollWifiOnlyUpload = enabled
        GadgetServerSyncScheduler.schedule(application, settingsRepository)
        _uiState.update { it.copy(sollWifiOnlyUpload = enabled) }
    }

    fun setVoiceRequiresUnlockedDevice(enabled: Boolean) {
        settingsRepository.voiceRequiresUnlockedDevice = enabled
        _uiState.update { it.copy(voiceRequiresUnlockedDevice = enabled) }
    }

    fun setVoiceRequiresHeadset(enabled: Boolean) {
        settingsRepository.voiceRequiresHeadset = enabled
        _uiState.update { it.copy(voiceRequiresHeadset = enabled) }
    }

    fun setVoiceLocalOnly(enabled: Boolean) {
        settingsRepository.voiceLocalOnly = enabled
        _uiState.update { it.copy(voiceLocalOnly = enabled) }
    }

    fun setVoiceWakePhraseRequired(enabled: Boolean) {
        settingsRepository.voiceWakePhraseRequired = enabled
        _uiState.update { it.copy(voiceWakePhraseRequired = enabled) }
    }

    fun setProactiveSuggestionsEnabled(enabled: Boolean) {
        settingsRepository.proactiveSuggestionsEnabled = enabled
        _uiState.update { it.copy(proactiveSuggestionsEnabled = enabled) }
    }

    fun setProactiveSuggestionsDailyLimit(limit: Int) {
        val safeLimit = limit.coerceIn(1, 6)
        settingsRepository.proactiveSuggestionsDailyLimit = safeLimit
        _uiState.update { it.copy(proactiveSuggestionsDailyLimit = safeLimit) }
    }

    fun setProactiveSystemDeliveryEnabled(enabled: Boolean) {
        settingsRepository.proactiveSystemDeliveryEnabled = enabled
        _uiState.update { it.copy(proactiveSystemDeliveryEnabled = enabled) }
    }

    fun setSystemNotificationImportanceMode(mode: SystemNotificationImportanceMode) {
        settingsRepository.systemNotificationImportanceMode = mode
        _uiState.update { it.copy(systemNotificationImportanceMode = mode) }
    }

    fun setSystemNotificationChannelEnabled(channel: SollNotificationChannel, enabled: Boolean) {
        settingsRepository.setSystemNotificationChannelEnabled(channel, enabled)
        _uiState.update { it.copy(systemNotificationChannels = buildSystemNotificationChannels()) }
    }

    fun retryAndroidPushTokenRegistration() {
        settingsRepository.sollPushTokenLastError = ""
        _uiState.update {
            it.copy(
                sollPushTokenLastError = "",
                isRetryingSollPushToken = true,
                message = "Запрошена повторная регистрация push-токена",
                isError = false,
            )
        }
        AndroidPushTokenRegistrar.registerCurrentToken(
            application,
            reason = "settings_manual_retry",
            force = true,
        ) {
            viewModelScope.launch {
                val lastError = settingsRepository.sollPushTokenLastError
                val registeredAt = settingsRepository.sollPushTokenRegisteredAt
                _uiState.update {
                    it.copy(
                        sollPushTokenRegisteredAt = registeredAt,
                        sollPushTokenLastError = lastError,
                        isRetryingSollPushToken = false,
                        message = if (lastError.isBlank()) {
                            "Push-токен проверен сервером"
                        } else {
                            "Push-токен не зарегистрирован"
                        },
                        isError = lastError.isNotBlank(),
                    )
                }
            }
        }
    }

    fun setAssistantMemoryEnabled(enabled: Boolean) {
        settingsRepository.assistantMemoryEnabled = enabled
        _uiState.update { it.copy(assistantMemoryEnabled = enabled) }
    }

    fun setAppThemeVariant(variant: SollThemeVariant) {
        settingsRepository.appThemeVariant = variant.storageKey
        _uiState.update { it.copy(appThemeVariant = variant) }
    }

    fun refreshDeviceQa() {
        _uiState.update {
            it.copy(
                isBatteryOptimizationDisabled = checkBatteryOptimization(),
                deviceQaChecks = deviceQaRepository.checks(),
            )
        }
    }

    fun postDeviceQaNotification() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPostingDeviceQaNotification = true, message = null, isError = false) }
            runCatching {
                deviceQaRepository.postTestNotification()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isPostingDeviceQaNotification = false,
                        deviceQaChecks = deviceQaRepository.checks(),
                        message = "Тестовое уведомление отправлено",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isPostingDeviceQaNotification = false,
                        deviceQaChecks = deviceQaRepository.checks(),
                        message = "Не удалось отправить уведомление: ${error.message ?: "ошибка"}",
                        isError = true,
                    )
                }
            }
        }
    }

    fun markDeviceQaPassed(id: DeviceQaCheckId) {
        deviceQaRepository.recordManualResult(id, passed = true)
        refreshDeviceQa()
    }

    fun markDeviceQaProblem(id: DeviceQaCheckId) {
        deviceQaRepository.recordManualResult(id, passed = false)
        refreshDeviceQa()
    }

    fun clearDeviceQaResult(id: DeviceQaCheckId) {
        deviceQaRepository.clearManualResult(id)
        refreshDeviceQa()
    }

    fun showDeviceQaReport() {
        val checks = deviceQaRepository.checks()
        _uiState.update {
            it.copy(
                deviceQaChecks = checks,
                deviceQaReport = deviceQaRepository.buildReport(),
            )
        }
    }

    fun dismissDeviceQaReport() {
        _uiState.update { it.copy(deviceQaReport = null) }
    }

    fun shareDeviceQaReport() {
        val report = _uiState.value.deviceQaReport ?: deviceQaRepository.buildReport()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Отчет Device QA Soll App")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        val chooser = Intent.createChooser(sendIntent, "Поделиться отчетом").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            application.startActivity(chooser)
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    message = "Не удалось открыть отправку отчета: ${error.message ?: "ошибка"}",
                    isError = true,
                )
            }
        }
    }

    fun saveSollSettings() {
        persistSollSettings(showMessage = true)
    }

    fun resetSollEndpointToRecommended() {
        settingsRepository.resetSollEndpointToRecommended()
        _uiState.update {
            it.copy(
                sollServerUrl = settingsRepository.sollServerUrl,
                sollApiPathPrefix = settingsRepository.sollApiPathPrefix,
                sollHealthStatus = null,
                sollHealthMessage = null,
                sollSyncSummary = null,
                message = "Рекомендованный адрес сервера подставлен в поля",
                isError = false,
            )
        }
    }

    private fun persistSollSettings(showMessage: Boolean) {
        val state = _uiState.value
        val interval = state.sollSyncIntervalMinutes.toIntOrNull()?.coerceIn(5, 1440) ?: 60
        settingsRepository.sollServerUrl = state.sollServerUrl
        settingsRepository.sollApiPathPrefix = state.sollApiPathPrefix
        settingsRepository.sollAccessToken = state.sollAccessToken
        settingsRepository.sollSyncIntervalMinutes = interval
        settingsRepository.sollWifiOnlyUpload = state.sollWifiOnlyUpload
        GadgetServerSyncScheduler.schedule(application, settingsRepository)
        SollServerSyncScheduler.schedule(application, settingsRepository)
        _uiState.update {
            val nextState = it.copy(
                sollSyncIntervalMinutes = interval.toString(),
            )
            if (showMessage) {
                nextState.copy(
                    message = "Настройки сервера Soll сохранены",
                    isError = false,
                )
            } else {
                nextState
            }
        }
    }

    fun checkSollHealth() {
        persistSollSettings(showMessage = false)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCheckingSollHealth = true,
                    sollHealthStatus = null,
                    sollHealthMessage = null,
                    sollSyncSummary = null,
                )
            }

            sollRepository.getHealth().fold(
                onSuccess = { health ->
                    _uiState.update {
                        it.copy(
                            isCheckingSollHealth = false,
                            sollHealthStatus = health.statusText(),
                            sollHealthMessage = health.statusMessage(),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCheckingSollHealth = false,
                            sollHealthStatus = "Недоступен",
                            sollHealthMessage = error.message ?: "Не удалось подключиться к серверу Soll",
                        )
                    }
                }
            )
        }
    }

    fun syncSollNow() {
        persistSollSettings(showMessage = false)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSyncingSoll = true,
                    sollHealthStatus = null,
                    sollHealthMessage = null,
                    sollSyncSummary = null,
                )
            }

            sollRepository.getAndroidSyncStatus().fold(
                onSuccess = { status ->
                    _uiState.update {
                        it.copy(
                            isSyncingSoll = false,
                            sollHealthStatus = status.health.statusText(),
                            sollHealthMessage = status.health.statusMessage(status),
                            sollSyncSummary = status.syncSummary(),
                            message = if (status.fromCache) {
                                "Сервер недоступен. Показан последний кэш Soll."
                            } else {
                                "Синхронизация Soll выполнена"
                            },
                            isError = status.fromCache,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSyncingSoll = false,
                            sollHealthStatus = "Недоступен",
                            sollHealthMessage = error.message ?: "Не удалось подключиться к серверу Soll",
                            sollSyncSummary = "Синхронизация не выполнена.",
                            isError = true,
                        )
                    }
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openAutoStartSettings() {
        // Try to open auto-start settings for various OEMs
        val intents = listOf(
            // Xiaomi
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Huawei
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            // Oppo
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            // Vivo
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // Samsung - open battery settings
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            // Fallback to app info
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${application.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                application.startActivity(intent)
                return
            } catch (e: Exception) {
                continue
            }
        }

        // Final fallback
        openAppSettings()
    }

    fun refreshBatteryStatus() {
        refreshDeviceQa()
    }

    private fun refreshCapabilitySettings() {
        _uiState.update {
            it.copy(
                riskyCapabilitiesEnabled = settingsRepository.isRiskyCapabilitiesEnabled(),
                capabilityGroups = buildCapabilityGroups(),
            )
        }
    }

    private fun buildCapabilityGroups(): List<CapabilityGroupUiState> {
        val riskyCapabilitiesEnabled = settingsRepository.isRiskyCapabilitiesEnabled()
        return capabilityRegistry.capabilities
            .groupBy { it.riskTier }
            .toSortedMap(compareBy { it.ordinal })
            .map { (riskTier, capabilities) ->
                CapabilityGroupUiState(
                    riskTier = riskTier,
                    items = capabilities.map { capability ->
                        val configuredEnabled = settingsRepository.isCapabilityEnabled(capability)
                        val blockedByGlobal = capability.riskTier.isRisky() && !riskyCapabilitiesEnabled
                        CapabilityItemUiState(
                            id = capability.id,
                            name = capability.name,
                            description = capability.description,
                            configuredEnabled = configuredEnabled,
                            effectiveEnabled = configuredEnabled && !blockedByGlobal,
                            blockedByGlobalRiskToggle = blockedByGlobal,
                            requiresConfirmation = capability.requiresConfirmation,
                            auditRequired = capability.auditRequired,
                            permissions = capability.requiredAndroidPermissions,
                        )
                    }
                )
            }
    }

    private fun buildSystemNotificationChannels(): List<SystemNotificationChannelUiState> =
        SystemNotificationPreferences.FILTERABLE_CHANNELS.map { channel ->
            SystemNotificationChannelUiState(
                channel = channel,
                enabled = settingsRepository.isSystemNotificationChannelEnabled(channel),
            )
        }

    private fun checkBatteryOptimization(): Boolean {
        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(application.packageName)
    }

    private fun SollHealth.statusText(): String =
        when (status.lowercase()) {
            "healthy" -> "Работает"
            "degraded" -> "Работает с проблемами"
            else -> status
        }

    private fun SollHealth.statusMessage(syncStatus: SollAndroidSyncStatus? = null): String = buildString {
        if (syncStatus?.fromCache == true) {
            append("Кэш. ")
        }
        append("Хранилище: ${if (vaultAccessible) "доступно" else "недоступно"}")
        append(", планировщик: ${if (schedulerRunning) "запущен" else "остановлен"}")
        append(", задач планировщика: $jobsCount")
        syncStatus?.briefing?.filename?.takeIf { it.isNotBlank() }?.let { filename ->
            append(", брифинг: $filename")
        }
        if (!syncStatus?.warnings.isNullOrEmpty()) {
            append(". ${syncStatus?.warnings?.joinToString("; ")}")
        }
    }

    private fun SollTaskBoard.syncSummary(): String = buildString {
        append("Получены задачи: сегодня ${today.size}, входящих ${inbox.size}, блок ${blocked.size}, отложенных ${deferred.size}, зависших ${stale.size}, открытых всего $openCount.")
        today.firstOrNull()?.let { task ->
            append(" Ближайшая задача: ${task.title}")
        }
    }

    private fun SollAndroidSyncStatus.syncSummary(): String {
        val protocolSummary = protocol?.let { bootstrap ->
            val workerCount = bootstrap.workerContracts.size
            val refreshStatus = if (bootstrap.auth.tokenRefreshEndpoint.isNotBlank()) "refresh есть" else "refresh нет"
            val status = if (bootstrap.compatible) "протокол OK" else "протокол требует проверки"
            " $status: workers=$workerCount, $refreshStatus."
        }.orEmpty()
        val cacheSummary = if (fromCache) " Данные из локального кэша." else ""
        return tasks.syncSummary() + protocolSummary + cacheSummary
    }

}
