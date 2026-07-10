package com.soll.presentation.screens.todo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.FieldMapRepository
import com.soll.domain.soll.SollDailyTask
import com.soll.domain.soll.SollDailyTaskDetail
import com.soll.domain.soll.SollDailyTaskList
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollSourceItem
import com.soll.domain.soll.SollSourceScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class DailyTodoTab {
    TASKS,
    SOURCES,
}

data class DailyTodoUiState(
    val selectedTab: DailyTodoTab = DailyTodoTab.TASKS,
    val tasks: List<SollDailyTask> = emptyList(),
    val sourcePath: String = "",
    val isLoading: Boolean = true,
    val isAdding: Boolean = false,
    val actionTaskId: String? = null,
    val deletingTaskId: String? = null,
    val attachmentTaskId: String? = null,
    val selectedTaskDetail: SollDailyTaskDetail? = null,
    val detailLoading: Boolean = false,
    val researchTaskId: String? = null,
    val sources: List<SollMonitoredSource> = emptyList(),
    val selectedSourceId: String? = null,
    val sourceItems: List<SollSourceItem> = emptyList(),
    val sourceLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val addSuccessVersion: Long = 0L,
)

@HiltViewModel
class DailyTodoViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val fieldMapRepository: FieldMapRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DailyTodoUiState())
    val uiState: StateFlow<DailyTodoUiState> = _uiState.asStateFlow()
    private val sourceItemsCache = mutableMapOf<String, List<SollSourceItem>>()

    init {
        refresh()
    }

    fun selectTab(tab: DailyTodoTab) {
        _uiState.update { it.copy(selectedTab = tab, message = null, isError = false) }
        if (tab == DailyTodoTab.SOURCES && _uiState.value.sources.isEmpty()) {
            loadSources(showLoading = true)
        }
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            }
            sollGateway.getTodayDailyTasks().fold(
                onSuccess = { list ->
                    val visibleTasks = list.tasks.withoutCompletedDailyTasks()
                    _uiState.update {
                        val selectedDetail = it.selectedTaskDetail
                        val updatedSelectedDetail = selectedDetail?.updatedWith(visibleTasks)
                        val selectedTaskMissing = selectedDetail != null && updatedSelectedDetail == null
                        it.copy(
                            tasks = visibleTasks,
                            sourcePath = list.sourcePath,
                            selectedTaskDetail = updatedSelectedDetail,
                            detailLoading = if (selectedTaskMissing) false else it.detailLoading,
                            researchTaskId = if (selectedTaskMissing) null else it.researchTaskId,
                            isLoading = false,
                            message = null,
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "Не удалось загрузить список дел",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun refreshCurrent() {
        if (_uiState.value.selectedTab == DailyTodoTab.SOURCES) {
            loadSources(showLoading = true)
        } else {
            refresh(showLoading = true)
        }
    }

    fun addTask(text: String, attachmentUri: Uri?, attachLocation: Boolean = true) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            _uiState.update { it.copy(message = "Введите дело", isError = true) }
            return
        }

        viewModelScope.launch {
            val existingTaskIds = _uiState.value.tasks.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    isAdding = true,
                    attachmentTaskId = null,
                    message = null,
                    isError = false,
                )
            }

            var locationWarning: String? = null
            val locationLabel = if (attachLocation) {
                runCatching { fieldMapRepository.publishCurrentLocationToSoll() }
                    .getOrElse { error ->
                        locationWarning = error.message ?: "геопозиция недоступна"
                        ""
                    }
            } else {
                ""
            }

            val createdList = sollGateway.addTodayDailyTask(cleanText, locationLabel)
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isAdding = false,
                            message = error.message ?: "Не удалось добавить дело",
                            isError = true,
                        )
                    }
                    return@launch
            }

            var currentTasks = createdList.tasks.withoutCompletedDailyTasks()
            var currentSourcePath = createdList.sourcePath
            val createdTaskId = createdList.createdTaskId
                ?: findCreatedTaskId(createdList.tasks, existingTaskIds)
            var message = "Дело добавлено"
            var isError = false

            if (attachmentUri != null) {
                if (createdTaskId.isNullOrBlank()) {
                    message = "Дело добавлено, но файл не прикреплен: не найден ID дела"
                    isError = true
                } else {
                    _uiState.update {
                        it.copy(
                            tasks = currentTasks,
                            sourcePath = currentSourcePath,
                            attachmentTaskId = createdTaskId,
                        )
                    }
                    sollGateway.uploadTodayDailyTaskAttachment(createdTaskId, attachmentUri).fold(
                        onSuccess = { attachment ->
                            message = "Дело добавлено. ${attachment.analysisStatus.dailyAttachmentMessage()}"
                            sollGateway.getTodayDailyTasks().onSuccess { refreshed ->
                                currentTasks = refreshed.tasks.withoutCompletedDailyTasks()
                                currentSourcePath = refreshed.sourcePath
                            }
                        },
                        onFailure = { error ->
                            message = "Дело добавлено, но файл не прикреплен: ${error.message ?: "ошибка загрузки"}"
                            isError = true
                        },
                    )
                }
            }

            locationWarning?.let {
                message = "$message. Геопозиция недоступна"
            }

            _uiState.update {
                it.copy(
                    tasks = currentTasks,
                    sourcePath = currentSourcePath,
                    isAdding = false,
                    attachmentTaskId = null,
                    message = message,
                    isError = isError,
                    addSuccessVersion = it.addSuccessVersion + 1,
                )
            }
        }
    }

    fun setTaskDone(task: SollDailyTask, done: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionTaskId = task.id, message = null, isError = false) }
            callDailyTaskReferences(task) { taskRef ->
                sollGateway.updateTodayDailyTask(taskRef, done)
            }.fold(
                onSuccess = { list ->
                    val visibleTasks = list.tasks.withoutCompletedDailyTasks()
                    _uiState.update {
                        val detail = it.selectedTaskDetail
                        val updatedDetail = detail?.updatedWith(visibleTasks)
                        val selectedTaskMissing = detail != null && updatedDetail == null
                        it.copy(
                            tasks = visibleTasks,
                            sourcePath = list.sourcePath,
                            selectedTaskDetail = updatedDetail,
                            detailLoading = if (selectedTaskMissing) false else it.detailLoading,
                            researchTaskId = if (selectedTaskMissing) null else it.researchTaskId,
                            actionTaskId = null,
                            message = if (done) "Дело закрыто" else "Дело возвращено",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionTaskId = null,
                            message = error.message ?: "Не удалось обновить дело",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun openTask(task: SollDailyTask) {
        viewModelScope.launch {
            loadTaskDetail(task, showLoading = true)
        }
    }

    fun closeTaskDetail() {
        _uiState.update { it.copy(selectedTaskDetail = null, detailLoading = false, researchTaskId = null) }
    }

    fun refreshSelectedTaskDetail(showLoading: Boolean = true) {
        val task = _uiState.value.selectedTaskDetail?.task ?: return
        viewModelScope.launch {
            loadTaskDetail(task, showLoading = showLoading)
        }
    }

    fun deleteTask(task: SollDailyTask) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingTaskId = task.id, message = null, isError = false) }
            deleteDailyTaskWithReferences(task).fold(
                onSuccess = { list ->
                    val visibleTasks = list.tasks.withoutCompletedDailyTasks()
                    _uiState.update {
                        val selectedDetail = it.selectedTaskDetail
                        val updatedSelectedDetail = selectedDetail?.updatedWith(visibleTasks)
                        val selectedTaskMissing = selectedDetail != null && updatedSelectedDetail == null
                        it.copy(
                            tasks = visibleTasks,
                            sourcePath = list.sourcePath,
                            selectedTaskDetail = updatedSelectedDetail,
                            detailLoading = if (selectedTaskMissing) false else it.detailLoading,
                            researchTaskId = if (selectedTaskMissing) null else it.researchTaskId,
                            deletingTaskId = null,
                            message = "Дело удалено",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            deletingTaskId = null,
                            message = error.message ?: "Не удалось удалить дело",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun researchSelectedTask(publishLocation: Boolean = true) {
        val task = _uiState.value.selectedTaskDetail?.task ?: return
        val taskId = task.id
        viewModelScope.launch {
            _uiState.update { it.copy(researchTaskId = taskId, message = null, isError = false) }
            var locationWarning: String? = null
            if (publishLocation) {
                runCatching { fieldMapRepository.publishCurrentLocationToSoll() }
                    .onFailure { error ->
                        locationWarning = error.message ?: "Геопозиция недоступна"
                    }
            }
            callDailyTaskReferences(task) { taskRef ->
                sollGateway.researchTodayDailyTask(taskRef)
            }.fold(
                onSuccess = { detail ->
                    val message = detail.research?.summary ?: "Поиск завершен"
                    _uiState.update {
                        it.copy(
                            selectedTaskDetail = detail,
                            researchTaskId = null,
                            message = locationWarning?.let { warning -> "$message. $warning" } ?: message,
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            researchTaskId = null,
                            message = error.message ?: "Не удалось выполнить поиск по делу",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun attachFile(task: SollDailyTask, uri: Uri) {
        viewModelScope.launch {
            val detailTaskId = _uiState.value.selectedTaskDetail?.task?.id
            _uiState.update { it.copy(attachmentTaskId = task.id, message = null, isError = false) }
            callDailyTaskReferences(task) { taskRef ->
                sollGateway.uploadTodayDailyTaskAttachment(taskRef, uri)
            }.fold(
                onSuccess = { attachment ->
                    val refreshedList = sollGateway.getTodayDailyTasks().getOrNull()
                    val refreshedDetail = if (detailTaskId == task.id) {
                        getTaskDetailWithReferences(task).getOrNull()
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            tasks = refreshedList?.tasks?.withoutCompletedDailyTasks() ?: it.tasks,
                            sourcePath = refreshedList?.sourcePath ?: it.sourcePath,
                            selectedTaskDetail = refreshedDetail ?: it.selectedTaskDetail,
                            attachmentTaskId = null,
                            message = attachment.analysisStatus.dailyAttachmentMessage(),
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            attachmentTaskId = null,
                            message = error.message ?: "Не удалось прикрепить файл",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun loadSources(sourceId: String? = _uiState.value.selectedSourceId, showLoading: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceLoading = showLoading, message = null, isError = false) }
            sollGateway.listSources(SollSourceScope.DAILY_TODO).fold(
                onSuccess = { sources ->
                    val sourceIds = sources.mapTo(mutableSetOf()) { it.id }
                    sourceItemsCache.keys.retainAll(sourceIds)
                    val selected = sourceId?.takeIf { it in sourceIds } ?: sources.firstOrNull()?.id
                    var itemLoadMessage: String? = null
                    val items = selected?.let { id ->
                        sourceItemsCache[id] ?: sollGateway.listSourceItems(id, limit = 20).fold(
                            onSuccess = { loaded ->
                                sourceItemsCache[id] = loaded
                                loaded
                            },
                            onFailure = { error ->
                                itemLoadMessage = error.message ?: "Не удалось загрузить материалы источника"
                                emptyList()
                            },
                        )
                    }.orEmpty()
                    _uiState.update {
                        it.copy(
                            sources = sources,
                            selectedSourceId = selected,
                            sourceItems = items,
                            sourceLoading = false,
                            message = itemLoadMessage,
                            isError = itemLoadMessage != null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = error.message ?: "Не удалось загрузить источники",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun selectSource(source: SollMonitoredSource) {
        val cachedItems = sourceItemsCache[source.id]
        _uiState.update {
            it.copy(
                selectedSourceId = source.id,
                sourceItems = cachedItems ?: emptyList(),
                sourceLoading = cachedItems == null,
                message = null,
                isError = false,
            )
        }
        if (cachedItems == null) {
            loadSourceItems(source.id)
        }
    }

    fun createSource(name: String, target: String, sourceType: String = "web") {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceLoading = true, message = null, isError = false) }
            sollGateway.createSource(name, target, SollSourceScope.DAILY_TODO, sourceType).fold(
                onSuccess = {
                    sourceItemsCache.clear()
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = "Источник добавлен",
                            isError = false,
                        )
                    }
                    loadSources()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = error.message ?: "Не удалось добавить источник",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun updateSource(source: SollMonitoredSource, name: String, description: String, tagsText: String, enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceLoading = true, message = null, isError = false) }
            sollGateway.updateSource(
                sourceId = source.id,
                name = name,
                description = description,
                tags = tagsText.parseTags(),
                enabled = enabled,
            ).fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            sources = it.sources.map { existing ->
                                if (existing.id == updated.id) updated else existing
                            },
                            selectedSourceId = updated.id,
                            sourceLoading = false,
                            message = "Источник обновлен",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = error.message ?: "Не удалось обновить источник",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun deleteSource(source: SollMonitoredSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceLoading = true, message = null, isError = false) }
            sollGateway.deleteSource(source.id).fold(
                onSuccess = {
                    sourceItemsCache.remove(source.id)
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            selectedSourceId = null,
                            message = "Источник удален",
                            isError = false,
                        )
                    }
                    loadSources(sourceId = null)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = error.message ?: "Не удалось удалить источник",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun checkSource(source: SollMonitoredSource) {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceLoading = true, message = null, isError = false) }
            sollGateway.checkSource(source.id).fold(
                onSuccess = { changed ->
                    sourceItemsCache.remove(source.id)
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = if (changed) "Источник обновлен" else "Новых материалов нет",
                            isError = false,
                        )
                    }
                    loadSources(source.id)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceLoading = false,
                            message = error.message ?: "Не удалось проверить источник",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private suspend fun getTaskDetailWithReferences(task: SollDailyTask): Result<SollDailyTaskDetail> =
        callDailyTaskReferences(task) { taskRef ->
            sollGateway.getTodayDailyTaskDetail(taskRef)
        }

    private suspend fun deleteDailyTaskWithReferences(task: SollDailyTask): Result<SollDailyTaskList> {
        val deleteResult = callDailyTaskReferences(task) { taskRef ->
            sollGateway.deleteTodayDailyTask(taskRef)
        }
        if (deleteResult.isSuccess) {
            return deleteResult
        }
        val deleteError = deleteResult.exceptionOrNull()
        if (deleteError != null && !deleteError.isDailyTaskReferenceError()) {
            return deleteResult
        }
        return callDailyTaskReferences(task) { taskRef ->
            sollGateway.updateTodayDailyTask(taskRef, done = true)
        }
    }

    private suspend fun <T> callDailyTaskReferences(
        task: SollDailyTask,
        call: suspend (String) -> Result<T>,
    ): Result<T> {
        var lastError: Throwable? = null
        for (taskRef in dailyTaskReferenceCandidates(task)) {
            val result = call(taskRef)
            if (result.isSuccess) {
                return result
            }
            val error = result.exceptionOrNull() ?: IllegalStateException("Не удалось выполнить действие")
            lastError = error
            if (!error.isDailyTaskReferenceError()) {
                return Result.failure(error)
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Не удалось выполнить действие"))
    }

    private fun dailyTaskReferenceCandidates(task: SollDailyTask): List<String> {
        val references = mutableListOf<String>()
        fun addReference(value: String) {
            val clean = value.trim()
            if (clean.isNotBlank() && clean !in references) {
                references += clean
            }
        }

        addReference(task.id)
        val tasks = _uiState.value.tasks
        val indexById = tasks.indexOfFirst { it.id == task.id }
        val taskIndex = if (indexById >= 0) {
            indexById
        } else {
            tasks.indexOfFirst { it.line == task.line && it.text == task.text }
        }
        if (taskIndex >= 0) {
            addReference("task-${taskIndex + 1}")
        }
        return references
    }

    private suspend fun loadTaskDetail(task: SollDailyTask, showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(detailLoading = true, message = null, isError = false) }
        }
        getTaskDetailWithReferences(task).fold(
            onSuccess = { detail ->
                _uiState.update {
                    it.copy(
                        selectedTaskDetail = detail,
                        detailLoading = false,
                        message = null,
                        isError = false,
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    if (error.isDailyTaskReferenceError()) {
                        it.copy(
                            selectedTaskDetail = task.toFallbackDetail(sourcePath = it.sourcePath),
                            detailLoading = false,
                            message = null,
                            isError = false,
                        )
                    } else {
                        it.copy(
                            detailLoading = false,
                            message = error.message ?: "Не удалось открыть дело",
                            isError = true,
                        )
                    }
                }
            },
        )
    }

    private fun loadSourceItems(sourceId: String) {
        viewModelScope.launch {
            val items = sollGateway.listSourceItems(sourceId, limit = 20).getOrElse { error ->
                _uiState.update {
                    if (it.selectedSourceId == sourceId) {
                        it.copy(
                            sourceLoading = false,
                            message = error.message ?: "Не удалось загрузить материалы источника",
                            isError = true,
                        )
                    } else {
                        it
                    }
                }
                return@launch
            }
            sourceItemsCache[sourceId] = items
            _uiState.update {
                if (it.selectedSourceId == sourceId) {
                    it.copy(
                        sourceItems = items,
                        sourceLoading = false,
                        message = null,
                        isError = false,
                    )
                } else {
                    it
                }
            }
        }
    }
}

private fun findCreatedTaskId(tasks: List<SollDailyTask>, existingTaskIds: Set<String>): String? =
    tasks.lastOrNull { it.id !in existingTaskIds }?.id ?: tasks.lastOrNull()?.id

private fun List<SollDailyTask>.withoutCompletedDailyTasks(): List<SollDailyTask> =
    filterNot { it.done }

private fun SollDailyTask.toFallbackDetail(sourcePath: String): SollDailyTaskDetail =
    SollDailyTaskDetail(
        date = "",
        sourcePath = sourcePath,
        task = this,
    )

private fun SollDailyTaskDetail.updatedWith(tasks: List<SollDailyTask>): SollDailyTaskDetail? {
    val currentTaskId = task.id
    return tasks.firstOrNull { it.id == currentTaskId }?.let { updatedTask ->
        copy(task = updatedTask)
    }
}

private fun Throwable.isDailyTaskReferenceError(): Boolean =
    this is HttpException && code() in setOf(400, 404)

private fun String.parseTags(): List<String> =
    split(",", ";", "#")
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinct()

private fun String.dailyAttachmentMessage(): String =
    when (this) {
        "parsed" -> "Файл прикреплен и разобран"
        "ocr_only" -> "Фото прикреплено, текст распознан"
        "vision_unavailable" -> "Фото прикреплено, для объекта нужна локальная vision-модель"
        "unsupported" -> "Файл прикреплен, анализ недоступен"
        else -> "Вложение прикреплено"
    }
