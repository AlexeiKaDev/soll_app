package com.soll.presentation.screens.tasks

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.SyncQueueEntity
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.data.repository.TaskCacheRepository
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class TaskBoardUiState(
    val today: List<SollTask> = emptyList(),
    val inbox: List<SollTask> = emptyList(),
    val stale: List<SollTask> = emptyList(),
    val doneRecent: List<SollTask> = emptyList(),
    val isLoading: Boolean = false,
    val actionTaskId: String? = null,
    val evidenceTaskId: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val isShowingCache: Boolean = false,
    val pendingEvidenceTaskIds: Set<String> = emptySet(),
) {
    val openCount: Int
        get() = today.size + inbox.size + stale.size

    val pendingEvidenceCount: Int
        get() = pendingEvidenceTaskIds.size
}

@HiltViewModel
class TaskBoardViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val syncQueueRepository: SollSyncQueueRepository,
    private val taskCacheRepository: TaskCacheRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskBoardUiState(isLoading = true))
    val uiState: StateFlow<TaskBoardUiState> = _uiState.asStateFlow()

    init {
        observePendingTaskEvidence()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            sollGateway.getTaskBoard().fold(
                onSuccess = { board ->
                    taskCacheRepository.replaceWith(board)
                    _uiState.update {
                        it.copy(
                            today = board.today,
                            inbox = board.inbox,
                            stale = board.stale,
                            doneRecent = board.doneRecent,
                            isLoading = false,
                            message = null,
                            isError = false,
                            isShowingCache = false,
                        )
                    }
                },
                onFailure = { error ->
                    val cachedBoard = taskCacheRepository.getCachedBoard()
                    if (cachedBoard.openCount > 0 || cachedBoard.doneRecent.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                today = cachedBoard.today,
                                inbox = cachedBoard.inbox,
                                stale = cachedBoard.stale,
                                doneRecent = cachedBoard.doneRecent,
                                isLoading = false,
                                message = "Сервер недоступен. Показан локальный кэш задач.",
                                isError = false,
                                isShowingCache = true,
                            )
                        }
                        return@fold
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "Не удалось загрузить задачи Soll",
                            isError = true,
                            isShowingCache = false,
                        )
                    }
                }
            )
        }
    }

    fun moveToToday(task: SollTask) {
        runTaskAction(task, "Задача перенесена на сегодня") {
            sollGateway.moveTaskToToday(task.id)
        }
    }

    fun startTask(task: SollTask) {
        runTaskAction(task, "Задача взята в работу") {
            sollGateway.setTaskStatus(task.id, "in_progress")
        }
    }

    fun completeTask(task: SollTask) {
        runTaskAction(task, "Задача закрыта") {
            sollGateway.completeTask(task.id)
        }
    }

    fun deferTask(task: SollTask) {
        runTaskAction(task, "Задача отложена") {
            sollGateway.deferTask(task.id)
        }
    }

    fun rejectTask(task: SollTask) {
        runTaskAction(task, "Задача отклонена") {
            sollGateway.rejectTask(task.id)
        }
    }

    fun attachEvidence(task: SollTask, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(evidenceTaskId = task.id, message = null, isError = false) }

            val uploadResult = sollGateway.uploadRawFile(uri)
            if (uploadResult.isFailure) {
                val error = uploadResult.exceptionOrNull()
                runCatching {
                    syncQueueRepository.enqueueRawFile(
                        uri = uri,
                        reason = error?.message,
                        taskId = task.id,
                        taskTitle = task.title,
                    )
                    syncQueueRepository.enqueueRawNote(
                        title = "Вложение к задаче: ${task.title}",
                        content = task.evidenceNoteContent(uploadedFilename = null),
                        tags = listOf("task-evidence", "queued"),
                        reason = error?.message,
                        taskId = task.id,
                        taskTitle = task.title,
                    )
                }

                _uiState.update {
                    it.copy(
                        evidenceTaskId = null,
                        message = "Сервер недоступен. Вложение сохранено в очередь синхронизации.",
                        isError = false,
                    )
                }
                return@launch
            }

            val upload = uploadResult.getOrThrow()
            val noteResult = sollGateway.createRawNote(
                title = "Вложение к задаче: ${task.title}",
                content = task.evidenceNoteContent(uploadedFilename = upload.filename),
                tags = listOf("task-evidence", "task-${task.id.take(8)}"),
            )

            noteResult.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            evidenceTaskId = null,
                            message = "Вложение отправлено в raw и привязано заметкой к задаче",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    syncQueueRepository.enqueueRawNote(
                        title = "Вложение к задаче: ${task.title}",
                        content = task.evidenceNoteContent(uploadedFilename = upload.filename),
                        tags = listOf("task-evidence", "task-${task.id.take(8)}"),
                        reason = error.message,
                        taskId = task.id,
                        taskTitle = task.title,
                    )
                    _uiState.update {
                        it.copy(
                            evidenceTaskId = null,
                            message = "Файл загружен, заметка привязки сохранена в очередь.",
                            isError = false,
                        )
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun runTaskAction(
        task: SollTask,
        successMessage: String,
        action: suspend () -> Result<SollTask>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionTaskId = task.id, message = null, isError = false) }
            action().fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            actionTaskId = null,
                            message = successMessage,
                            isError = false,
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    refreshAfterTaskConflict(error.message ?: "Не удалось изменить задачу")
                }
            )
        }
    }

    private fun observePendingTaskEvidence() {
        viewModelScope.launch {
            syncQueueRepository.observeRecentItems(limit = 100).collectLatest { items ->
                val taskIds = items
                    .filter { it.status in OPEN_SYNC_STATUSES }
                    .mapNotNull { it.taskIdOrNull() }
                    .toSet()
                _uiState.update { it.copy(pendingEvidenceTaskIds = taskIds) }
            }
        }
    }

    private suspend fun refreshAfterTaskConflict(reason: String) {
        sollGateway.getTaskBoard().fold(
            onSuccess = { board ->
                taskCacheRepository.replaceWith(board)
                _uiState.update {
                    it.copy(
                        today = board.today,
                        inbox = board.inbox,
                        stale = board.stale,
                        doneRecent = board.doneRecent,
                        isLoading = false,
                        actionTaskId = null,
                        message = "$reason. Доска обновлена с сервера.",
                        isError = true,
                        isShowingCache = false,
                    )
                }
            },
            onFailure = {
                val cachedBoard = taskCacheRepository.getCachedBoard()
                _uiState.update {
                    it.copy(
                        today = cachedBoard.today,
                        inbox = cachedBoard.inbox,
                        stale = cachedBoard.stale,
                        doneRecent = cachedBoard.doneRecent,
                        isLoading = false,
                        actionTaskId = null,
                        message = "$reason. Сервер недоступен, показан локальный кэш.",
                        isError = true,
                        isShowingCache = true,
                    )
                }
            },
        )
    }

    private fun SollTask.evidenceNoteContent(uploadedFilename: String?): String = buildString {
        append("Задача: $title\n")
        append("ID: $id\n")
        append("Статус: $status\n")
        append("Приоритет: $priority\n")
        projectName?.takeIf { it.isNotBlank() }?.let { append("Проект: $it\n") }
        dueDate?.takeIf { it.isNotBlank() }?.let { append("Дата: $it\n") }
        sourceRef.takeIf { it.isNotBlank() }?.let { append("Источник: $it\n") }
        if (uploadedFilename != null) {
            append("Вложение: raw/$uploadedFilename\n")
        } else {
            append("Вложение: файл ожидает отправки из мобильной очереди\n")
        }
        if (description.isNotBlank()) {
            append("\nОписание:\n")
            append(description)
            append("\n")
        }
    }

    private fun SyncQueueEntity.taskIdOrNull(): String? =
        runCatching {
            val payload = JSONObject(payloadJson)
            payload.optString("task_id").takeIf { it.isNotBlank() }
        }.getOrNull()

    private companion object {
        val OPEN_SYNC_STATUSES = setOf(
            SyncQueueEntity.STATUS_PENDING,
            SyncQueueEntity.STATUS_RUNNING,
            SyncQueueEntity.STATUS_FAILED,
        )
    }
}
