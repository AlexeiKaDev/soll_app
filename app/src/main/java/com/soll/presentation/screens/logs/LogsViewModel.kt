package com.soll.presentation.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.dao.CommandLogDao
import com.soll.data.local.dao.MessageLogDao
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.local.entity.MessageLogEntity
import com.soll.data.repository.AssistantEventRepository
import com.soll.data.repository.AssistantMemoryRepository
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.assistant.memory.AssistantMemory
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobRunner
import com.soll.domain.tool.ToolJobStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class LogsUiState(
    val selectedTab: Int = 0,
    val messageLogs: List<MessageLogEntity> = emptyList(),
    val commandLogs: List<CommandLogEntity> = emptyList(),
    val toolJobs: List<ToolJob> = emptyList(),
    val notifications: List<SollNotification> = emptyList(),
    val memories: List<AssistantMemory> = emptyList(),
    val unreadNotifications: Int = 0,
    val isLoading: Boolean = true,
    val isLoadingJobs: Boolean = true,
    val isLoadingNotifications: Boolean = true,
    val isLoadingMemories: Boolean = true,
    val jobsError: String? = null,
    val notificationsError: String? = null,
    val memoriesError: String? = null,
    val memoryExportText: String? = null,
    val isSendingMemoryToSoll: Boolean = false,
    val memorySyncMessage: String? = null,
    val isSendingAssistantEventsToSoll: Boolean = false,
    val assistantEventSyncMessage: String? = null,
    val notificationFeedbackBusy: Set<String> = emptySet(),
    val notificationFeedbackQueued: Set<String> = emptySet(),
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val messageLogDao: MessageLogDao,
    private val commandLogDao: CommandLogDao,
    private val toolJobStore: ToolJobStore,
    private val toolJobRunner: ToolJobRunner,
    private val notificationCenter: SollNotificationCenter,
    private val assistantMemoryRepository: AssistantMemoryRepository,
    private val assistantEventRepository: AssistantEventRepository,
    private val syncQueueRepository: SollSyncQueueRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
        observeToolJobs()
        observeNotifications()
        observeMemories()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            messageLogDao.getRecentLogs(100).collect { messages ->
                _uiState.update { it.copy(messageLogs = messages, isLoading = false) }
            }
        }

        viewModelScope.launch {
            commandLogDao.getRecentLogs(100).collect { commands ->
                _uiState.update { it.copy(commandLogs = commands) }
            }
        }
    }

    private fun observeToolJobs() {
        viewModelScope.launch {
            toolJobStore.getRecentJobs(limit = 100)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingJobs = false,
                            jobsError = "Не удалось загрузить задачи: ${error.message}",
                        )
                    }
                }
                .collect { jobs ->
                    _uiState.update {
                        it.copy(
                            toolJobs = jobs,
                            isLoadingJobs = false,
                            jobsError = null,
                        )
                    }
                }
        }
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            notificationCenter.observeRecent(limit = 100)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingNotifications = false,
                            notificationsError = "Не удалось загрузить уведомления: ${error.message}",
                        )
                    }
                }
                .collect { notifications ->
                    _uiState.update {
                        it.copy(
                            notifications = notifications,
                            isLoadingNotifications = false,
                            notificationsError = null,
                        )
                    }
                }
        }
        viewModelScope.launch {
            notificationCenter.observeUnreadCount()
                .catch { }
                .collect { unread ->
                    _uiState.update { it.copy(unreadNotifications = unread) }
                }
        }
    }

    private fun observeMemories() {
        viewModelScope.launch {
            assistantMemoryRepository.observeRecent(limit = 100)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMemories = false,
                            memoriesError = "Не удалось загрузить память: ${error.message}",
                        )
                    }
                }
                .collect { memories ->
                    _uiState.update {
                        it.copy(
                            memories = memories,
                            isLoadingMemories = false,
                            memoriesError = null,
                        )
                    }
                }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun clearLogs() {
        viewModelScope.launch {
            messageLogDao.deleteAll()
            commandLogDao.deleteAll()
            toolJobStore.deleteFinishedJobs()
            notificationCenter.deleteAll()
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            notificationCenter.markRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notificationCenter.markAllRead()
        }
    }

    fun sendNotificationFeedback(notification: SollNotification, decision: String) {
        if (notification.id in _uiState.value.notificationFeedbackBusy ||
            notification.id in _uiState.value.notificationFeedbackQueued
        ) return
        _uiState.update {
            it.copy(
                notificationFeedbackBusy = it.notificationFeedbackBusy + notification.id,
                notificationsError = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                syncQueueRepository.enqueueAssistantFeedback(
                    entityType = "notification",
                    entityId = notification.feedbackEntityId(),
                    decision = decision,
                )
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            notificationFeedbackBusy = it.notificationFeedbackBusy - notification.id,
                            notificationFeedbackQueued = it.notificationFeedbackQueued + notification.id,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            notificationFeedbackBusy = it.notificationFeedbackBusy - notification.id,
                            notificationsError = error.message ?: "Не удалось сохранить отзыв",
                        )
                    }
                }
            )
        }
    }

    fun exportMemory() {
        viewModelScope.launch {
            runCatching { assistantMemoryRepository.exportAsMarkdown() }
                .onSuccess { exportText ->
                    _uiState.update { it.copy(memoryExportText = exportText) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(memoriesError = "Не удалось экспортировать память: ${error.message}") }
                }
        }
    }

    fun sendMemorySummaryToSoll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingMemoryToSoll = true, memorySyncMessage = null) }
            runCatching { assistantMemoryRepository.sendSummaryToSoll() }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isSendingMemoryToSoll = false,
                            memorySyncMessage = result.message,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSendingMemoryToSoll = false,
                            memoriesError = error.message ?: "Не удалось отправить summary памяти в Soll",
                        )
                    }
                }
        }
    }

    fun sendAssistantEventsSummaryToSoll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingAssistantEventsToSoll = true, assistantEventSyncMessage = null) }
            runCatching { assistantEventRepository.sendSafeSummaryToSoll(limit = 100) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isSendingAssistantEventsToSoll = false,
                            assistantEventSyncMessage = result.message,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSendingAssistantEventsToSoll = false,
                            memoriesError = error.message ?: "Не удалось отправить summary событий в Soll",
                        )
                    }
                }
        }
    }

    fun clearMemorySyncMessage() {
        _uiState.update { it.copy(memorySyncMessage = null, assistantEventSyncMessage = null) }
    }

    fun closeMemoryExport() {
        _uiState.update { it.copy(memoryExportText = null) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            runCatching { assistantMemoryRepository.delete(id) }
                .onFailure { error ->
                    _uiState.update { it.copy(memoriesError = "Не удалось удалить запись памяти: ${error.message}") }
                }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            runCatching { assistantMemoryRepository.deleteAll() }
                .onFailure { error ->
                    _uiState.update { it.copy(memoriesError = "Не удалось очистить память: ${error.message}") }
                }
        }
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            runCatching {
                toolJobRunner.cancel(jobId, "Отменено пользователем")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(jobsError = "Не удалось отменить задачу: ${error.message}")
                }
            }
        }
    }
}

internal fun SollNotification.feedbackEntityId(): String =
    payloadJson
        ?.let { payload -> runCatching { JSONObject(payload).optString("event_id") }.getOrNull() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: dedupeKey?.trim()?.takeIf { it.isNotBlank() }
        ?: id
