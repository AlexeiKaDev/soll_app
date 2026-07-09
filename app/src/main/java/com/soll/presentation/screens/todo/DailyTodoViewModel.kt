package com.soll.presentation.screens.todo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.FieldMapRepository
import com.soll.domain.soll.SollDailyTask
import com.soll.domain.soll.SollDailyTaskDetail
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
                    _uiState.update {
                        val selectedDetail = it.selectedTaskDetail
                        val updatedSelectedTask = selectedDetail?.let { detail ->
                            list.tasks.firstOrNull { task -> task.id == detail.task.id }
                        }
                        it.copy(
                            tasks = list.tasks,
                            sourcePath = list.sourcePath,
                            selectedTaskDetail = if (selectedDetail != null && updatedSelectedTask != null) {
                                selectedDetail.copy(task = updatedSelectedTask)
                            } else {
                                selectedDetail
                            },
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

            var currentTasks = createdList.tasks
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
                                currentTasks = refreshed.tasks
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
                )
            }
        }
    }

    fun setTaskDone(task: SollDailyTask, done: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionTaskId = task.id, message = null, isError = false) }
            sollGateway.updateTodayDailyTask(task.id, done).fold(
                onSuccess = { list ->
                    _uiState.update {
                        val detail = it.selectedTaskDetail
                        val selectedTask = detail?.let { current ->
                            list.tasks.firstOrNull { item -> item.id == current.task.id }
                        }
                        it.copy(
                            tasks = list.tasks,
                            sourcePath = list.sourcePath,
                            selectedTaskDetail = if (detail != null && selectedTask != null) {
                                detail.copy(task = selectedTask)
                            } else {
                                detail
                            },
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
            loadTaskDetail(task.id, showLoading = true)
        }
    }

    fun closeTaskDetail() {
        _uiState.update { it.copy(selectedTaskDetail = null, detailLoading = false, researchTaskId = null) }
    }

    fun refreshSelectedTaskDetail(showLoading: Boolean = true) {
        val taskId = _uiState.value.selectedTaskDetail?.task?.id ?: return
        viewModelScope.launch {
            loadTaskDetail(taskId, showLoading = showLoading)
        }
    }

    fun researchSelectedTask() {
        val taskId = _uiState.value.selectedTaskDetail?.task?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(researchTaskId = taskId, message = null, isError = false) }
            sollGateway.researchTodayDailyTask(taskId).fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            selectedTaskDetail = detail,
                            researchTaskId = null,
                            message = detail.research?.summary ?: "Поиск завершен",
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
            sollGateway.uploadTodayDailyTaskAttachment(task.id, uri).fold(
                onSuccess = { attachment ->
                    val refreshedList = sollGateway.getTodayDailyTasks().getOrNull()
                    val refreshedDetail = if (detailTaskId == task.id) {
                        sollGateway.getTodayDailyTaskDetail(task.id).getOrNull()
                    } else {
                        null
                    }
                    _uiState.update {
                        it.copy(
                            tasks = refreshedList?.tasks ?: it.tasks,
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

    private suspend fun loadTaskDetail(taskId: String, showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(detailLoading = true, message = null, isError = false) }
        }
        sollGateway.getTodayDailyTaskDetail(taskId).fold(
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
                    it.copy(
                        detailLoading = false,
                        message = error.message ?: "Не удалось открыть дело",
                        isError = true,
                    )
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
