package com.soll.presentation.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.AssistantEventRepository
import com.soll.data.repository.AssistantMemoryRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.data.repository.TaskCacheRepository
import com.soll.data.repository.ToolJobRepository
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.proactive.ProactiveSignalSnapshot
import com.soll.domain.assistant.proactive.ProactiveSuggestion
import com.soll.domain.assistant.proactive.ProactiveSuggestionAction
import com.soll.domain.assistant.proactive.ProactiveSuggestionFeedback
import com.soll.domain.assistant.proactive.ScenarioDetector
import com.soll.domain.assistant.proactive.SuggestionEngine
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val isRunning: Boolean = false,
    val hasToken: Boolean = false,
    val botUsername: String? = null,
    val messagesProcessed: Long = 0,
    val uptime: String = "",
    val lastError: String? = null,
    val activeToolJobs: Int = 0,
    val pendingTasks: Int = 0,
    val openSyncQueueItems: Int = 0,
    val proactiveSuggestions: List<ProactiveSuggestion> = emptyList(),
    val healthItems: List<HealthItemUiState> = emptyList(),
    val recentAssistantEvents: List<AssistantEvent> = emptyList(),
    val recentToolJobs: List<ToolJob> = emptyList(),
    val isLoading: Boolean = false
)

data class HealthItemUiState(
    val title: String,
    val detail: String,
    val level: HealthLevel,
    val action: HealthAction? = null,
)

enum class HealthLevel {
    OK,
    WARNING,
    ERROR,
}

enum class HealthAction {
    APP_SETTINGS,
    BATTERY_SETTINGS,
    NOTIFICATION_SETTINGS,
    WRITE_SETTINGS,
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val assistantEventRepository: AssistantEventRepository,
    private val assistantMemoryRepository: AssistantMemoryRepository,
    private val taskCacheRepository: TaskCacheRepository,
    private val syncQueueRepository: SollSyncQueueRepository,
    private val toolJobRepository: ToolJobRepository,
    private val notificationCenter: SollNotificationCenter,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val scenarioDetector = ScenarioDetector()

    init {
        observeServiceState()
        observeAssistantEvents()
        observeToolJobs()
        observeSyncQueue()
        checkToken()
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            while (isActive) {
                val serverConfigured = settingsRepository.sollServerUrl.isNotBlank()
                val hasServerAuth = settingsRepository.sollDeviceAccessToken.isNotBlank() ||
                    settingsRepository.sollAccessToken.isNotBlank()
                val activeToolJobs = toolJobRepository.countActiveJobs()
                val pendingTasks = runCatching { taskCacheRepository.getCachedBoard().openCount }
                    .getOrDefault(_uiState.value.pendingTasks)

                _uiState.update { state ->
                    state.copy(
                        isRunning = serverConfigured,
                        hasToken = hasServerAuth,
                        messagesProcessed = pendingTasks.toLong(),
                        uptime = if (serverConfigured) "sync active" else "",
                        activeToolJobs = activeToolJobs,
                        pendingTasks = pendingTasks,
                        healthItems = buildHealthItems(
                            isRunning = serverConfigured,
                            hasToken = hasServerAuth,
                            activeToolJobs = activeToolJobs,
                        )
                    ).withProactiveSuggestions()
                }
                deliverProactiveSuggestionsIfEnabled()
                delay(1000)
            }
        }
    }

    private fun checkToken() {
        viewModelScope.launch {
            val hasToken = settingsRepository.sollDeviceAccessToken.isNotBlank() ||
                settingsRepository.sollAccessToken.isNotBlank()
            _uiState.update {
                it.copy(
                    hasToken = hasToken,
                    healthItems = buildHealthItems(
                        isRunning = it.isRunning,
                        hasToken = hasToken,
                        activeToolJobs = it.activeToolJobs,
                    )
                ).withProactiveSuggestions()
            }
            deliverProactiveSuggestionsIfEnabled()
        }
    }

    private fun observeAssistantEvents() {
        viewModelScope.launch {
            assistantEventRepository.getRecentEvents(limit = 5).collect { events ->
                _uiState.update { it.copy(recentAssistantEvents = events) }
            }
        }
    }

    private fun observeToolJobs() {
        viewModelScope.launch {
            toolJobRepository.getRecentJobs(limit = 5).collect { jobs ->
                _uiState.update { it.copy(recentToolJobs = jobs).withProactiveSuggestions() }
                deliverProactiveSuggestionsIfEnabled()
            }
        }
    }

    private fun observeSyncQueue() {
        viewModelScope.launch {
            syncQueueRepository.observeOpenCount().collect { openCount ->
                _uiState.update {
                    it.copy(openSyncQueueItems = openCount).withProactiveSuggestions()
                }
                deliverProactiveSuggestionsIfEnabled()
            }
        }
    }

    fun startBot() {
        openAppSettings()
        _uiState.update {
            it.copy(lastError = "Старый Android-бот перенесен в архив. Используйте сервер Soll и чат приложения.")
        }
    }

    fun stopBot() {
        _uiState.update { it.copy(lastError = "Старый фоновый бот уже отключен в Android.") }
    }

    fun runHealthAction(action: HealthAction) {
        when (action) {
            HealthAction.APP_SETTINGS -> openAppSettings()
            HealthAction.BATTERY_SETTINGS -> openBatterySettings()
            HealthAction.NOTIFICATION_SETTINGS -> openNotificationSettings()
            HealthAction.WRITE_SETTINGS -> openWriteSettings()
        }
    }

    fun acceptProactiveSuggestion(suggestionId: String) {
        val suggestion = _uiState.value.proactiveSuggestions.firstOrNull { it.id == suggestionId } ?: return
        settingsRepository.recordProactiveSuggestionFeedback(
            suggestionId = suggestion.id,
            feedback = ProactiveSuggestionFeedback.ACCEPTED,
        )
        refreshProactiveSuggestions()
        logSuggestionFeedback(suggestion, ProactiveSuggestionFeedback.ACCEPTED)
        rememberAcceptedSuggestion(suggestion)
        performSuggestionAction(suggestion.action)
    }

    fun dismissProactiveSuggestion(suggestionId: String) {
        val suggestion = _uiState.value.proactiveSuggestions.firstOrNull { it.id == suggestionId } ?: return
        settingsRepository.recordProactiveSuggestionFeedback(
            suggestionId = suggestion.id,
            feedback = ProactiveSuggestionFeedback.DISMISSED,
        )
        refreshProactiveSuggestions()
        logSuggestionFeedback(suggestion, ProactiveSuggestionFeedback.DISMISSED)
    }

    fun snoozeProactiveSuggestion(suggestionId: String) {
        val suggestion = _uiState.value.proactiveSuggestions.firstOrNull { it.id == suggestionId } ?: return
        settingsRepository.recordProactiveSuggestionFeedback(
            suggestionId = suggestion.id,
            feedback = ProactiveSuggestionFeedback.SNOOZED,
        )
        refreshProactiveSuggestions()
        logSuggestionFeedback(suggestion, ProactiveSuggestionFeedback.SNOOZED)
    }

    private fun performSuggestionAction(action: ProactiveSuggestionAction) {
        when (action) {
            ProactiveSuggestionAction.NONE -> Unit
            ProactiveSuggestionAction.START_BOT -> openAppSettings()
            ProactiveSuggestionAction.OPEN_APP_SETTINGS -> openAppSettings()
            ProactiveSuggestionAction.OPEN_BATTERY_SETTINGS -> openBatterySettings()
        }
    }

    private fun logSuggestionFeedback(
        suggestion: ProactiveSuggestion,
        feedback: ProactiveSuggestionFeedback,
    ) {
        viewModelScope.launch {
            assistantEventRepository.logEvent(
                AssistantEvent(
                    type = "proactive_suggestion_${feedback.name.lowercase()}",
                    source = "home",
                    summary = "${feedback.label()}: ${suggestion.title}",
                    payloadJson = JSONObject()
                        .put("suggestion_id", suggestion.id)
                        .put("priority", suggestion.priority.name.lowercase())
                        .put("confidence", suggestion.confidence.toDouble())
                        .put("action", suggestion.action.name.lowercase())
                        .toString(),
                )
            )
        }
    }

    private fun rememberAcceptedSuggestion(suggestion: ProactiveSuggestion) {
        viewModelScope.launch {
            assistantMemoryRepository.rememberAcceptedSuggestion(suggestion)
        }
    }

    private fun refreshProactiveSuggestions() {
        _uiState.update { it.withProactiveSuggestions() }
    }

    private fun HomeUiState.withProactiveSuggestions(): HomeUiState =
        copy(proactiveSuggestions = buildProactiveSuggestions(this))

    private fun buildProactiveSuggestions(state: HomeUiState): List<ProactiveSuggestion> {
        if (!settingsRepository.proactiveSuggestionsEnabled) return emptyList()

        val now = System.currentTimeMillis()
        val localDateTime = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val snapshot = ProactiveSignalSnapshot(
            nowMillis = now,
            hourOfDay = localDateTime.hour,
            dayOfWeek = localDateTime.dayOfWeek,
            hasToken = state.hasToken,
            botRunning = state.isRunning,
            batteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            systemNotificationsEnabled = notificationCenter.canPostSystemNotifications(),
            activeToolJobs = state.activeToolJobs,
            failedRecentToolJobs = state.recentToolJobs.count {
                it.status == ToolJobStatus.FAILED || it.status == ToolJobStatus.BLOCKED
            },
            pendingTasks = state.pendingTasks,
            openSyncQueueItems = state.openSyncQueueItems,
            sollServerConfigured = settingsRepository.sollServerUrl.isNotBlank(),
        )
        return SuggestionEngine(settingsRepository.proactiveSuggestionsDailyLimit)
            .buildSuggestions(
                scenarios = scenarioDetector.detect(snapshot),
                isSuppressed = { suggestionId ->
                    settingsRepository.isProactiveSuggestionSuppressed(suggestionId, now)
                },
            )
    }

    private suspend fun deliverProactiveSuggestionsIfEnabled() {
        if (!settingsRepository.proactiveSystemDeliveryEnabled) {
            return
        }

        _uiState.value.proactiveSuggestions.forEach { suggestion ->
            if (
                settingsRepository.proactiveSystemDeliveryEnabled &&
                settingsRepository.shouldDeliverProactiveSuggestion(suggestion.id, DELIVERY_SYSTEM)
            ) {
                settingsRepository.recordProactiveSuggestionDelivered(suggestion.id, DELIVERY_SYSTEM)
                notificationCenter.post(
                    SollNotificationRequest(
                        channel = SollNotificationChannel.EVENTS,
                        type = "proactive_suggestion",
                        source = "home",
                        title = suggestion.title,
                        message = suggestion.detail,
                        payloadJson = JSONObject()
                            .put("suggestion_id", suggestion.id)
                            .put("priority", suggestion.priority.name.lowercase())
                            .put("action", suggestion.action.name.lowercase())
                            .toString(),
                        priority = suggestion.priority.toNotificationPriority(),
                        showSystem = true,
                        onlyAlertOnce = true,
                        systemNotificationId = suggestion.id.hashCode() and Int.MAX_VALUE,
                    )
                )
            }
        }
    }

    private fun buildHealthItems(
        isRunning: Boolean,
        hasToken: Boolean,
        activeToolJobs: Int,
    ): List<HealthItemUiState> {
        val items = mutableListOf<HealthItemUiState>()

        items += HealthItemUiState(
            title = "Доступ к серверу",
            detail = if (hasToken) "Device/API token настроен" else "Нужен device-token или API token",
            level = if (hasToken) HealthLevel.OK else HealthLevel.ERROR,
            action = if (hasToken) null else HealthAction.APP_SETTINGS,
        )

        items += HealthItemUiState(
            title = "Сервер Soll",
            detail = if (isRunning) "URL сервера настроен" else "URL сервера не задан",
            level = if (isRunning) HealthLevel.OK else HealthLevel.WARNING,
        )

        items += HealthItemUiState(
            title = "Оптимизация батареи",
            detail = if (isBatteryOptimizationIgnored()) "Отключена для Soll" else "Может задерживать sync и уведомления",
            level = if (isBatteryOptimizationIgnored()) HealthLevel.OK else HealthLevel.WARNING,
            action = if (isBatteryOptimizationIgnored()) null else HealthAction.BATTERY_SETTINGS,
        )

        items += HealthItemUiState(
            title = "Фоновая синхронизация",
            detail = if (isRunning) "WorkManager активен для сервера" else "Включится после настройки сервера",
            level = if (isRunning) HealthLevel.OK else HealthLevel.WARNING,
            action = if (isRunning) null else HealthAction.APP_SETTINGS,
        )

        val missingPermissions = missingRuntimePermissionLabels()
        items += HealthItemUiState(
            title = "Разрешения команд",
            detail = if (missingPermissions.isEmpty()) {
                "Нужные runtime-разрешения выданы"
            } else {
                "Не хватает: ${missingPermissions.joinToString(", ")}"
            },
            level = if (missingPermissions.isEmpty()) HealthLevel.OK else HealthLevel.WARNING,
            action = if (missingPermissions.isEmpty()) null else HealthAction.APP_SETTINGS,
        )

        items += HealthItemUiState(
            title = "Системные уведомления",
            detail = if (notificationCenter.canPostSystemNotifications()) {
                "Разрешены, центр уведомлений работает"
            } else {
                "Системный показ выключен, события сохраняются в логах"
            },
            level = if (notificationCenter.canPostSystemNotifications()) HealthLevel.OK else HealthLevel.WARNING,
            action = if (notificationCenter.canPostSystemNotifications()) null else HealthAction.NOTIFICATION_SETTINGS,
        )

        val canWriteSettings = Settings.System.canWrite(application)
        items += HealthItemUiState(
            title = "Управление настройками системы",
            detail = if (canWriteSettings) "Яркость можно менять" else "Изменение яркости заблокировано",
            level = if (canWriteSettings) HealthLevel.OK else HealthLevel.WARNING,
            action = if (canWriteSettings) null else HealthAction.WRITE_SETTINGS,
        )

        items += HealthItemUiState(
            title = "Задачи инструментов",
            detail = if (activeToolJobs == 0) "Активных задач нет" else "Активных задач: $activeToolJobs",
            level = if (activeToolJobs == 0) HealthLevel.OK else HealthLevel.WARNING,
        )

        return items
    }

    private fun missingRuntimePermissionLabels(): List<String> {
        val permissions = mutableListOf(
            "камера" to Manifest.permission.CAMERA,
            "микрофон" to Manifest.permission.RECORD_AUDIO,
            "SMS" to Manifest.permission.READ_SMS,
            "отправка SMS" to Manifest.permission.SEND_SMS,
            "звонки" to Manifest.permission.READ_CALL_LOG,
            "контакты" to Manifest.permission.READ_CONTACTS,
            "геолокация" to Manifest.permission.ACCESS_FINE_LOCATION,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += "уведомления" to Manifest.permission.POST_NOTIFICATIONS
            permissions += "изображения" to Manifest.permission.READ_MEDIA_IMAGES
            permissions += "видео" to Manifest.permission.READ_MEDIA_VIDEO
            permissions += "аудио" to Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += "хранилище" to Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += "Bluetooth" to Manifest.permission.BLUETOOTH_CONNECT
        }

        val missing = permissions
            .distinctBy { it.second }
            .filter { (_, permission) ->
                ContextCompat.checkSelfPermission(application, permission) != PackageManager.PERMISSION_GRANTED
            }
            .map { it.first }
            .toMutableList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            missing += "все файлы"
        }

        return missing
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(application.packageName)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { application.startActivity(requestIntent) }
            .recoverCatching { application.startActivity(fallbackIntent) }
    }

    private fun openNotificationSettings() {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { application.startActivity(notificationIntent) }
            .recoverCatching { openAppSettings() }
    }

    private fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun refreshToken() {
        checkToken()
    }

    private fun ProactiveSuggestionFeedback.label(): String =
        when (this) {
            ProactiveSuggestionFeedback.ACCEPTED -> "Принято"
            ProactiveSuggestionFeedback.DISMISSED -> "Скрыто"
            ProactiveSuggestionFeedback.SNOOZED -> "Отложено"
        }

    private companion object {
        const val DELIVERY_SYSTEM = "system"
    }
}

private fun com.soll.domain.assistant.proactive.ProactiveSuggestionPriority.toNotificationPriority(): SollNotificationPriority =
    when (this) {
        com.soll.domain.assistant.proactive.ProactiveSuggestionPriority.HIGH -> SollNotificationPriority.HIGH
        com.soll.domain.assistant.proactive.ProactiveSuggestionPriority.MEDIUM -> SollNotificationPriority.DEFAULT
        com.soll.domain.assistant.proactive.ProactiveSuggestionPriority.LOW -> SollNotificationPriority.LOW
    }
