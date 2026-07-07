package com.soll.presentation.screens.tasks

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.SyncQueueEntity
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.data.repository.TaskCacheRepository
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollLearningItem
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollRoadmap
import com.soll.domain.soll.SollRoadmapLine
import com.soll.domain.soll.SollSourceItem
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoardCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject
import java.io.IOException
import retrofit2.HttpException

enum class TaskWorkspaceMode(val label: String) {
    TASKS("Задачи"),
    INSIGHTS("Инсайты"),
    ROADMAP("Roadmap"),
    SOURCES("Источники"),
}

enum class TaskTab {
    ALL,
    TODAY,
    BLOCKED,
    INBOX,
    STALE,
    DEFERRED,
    IDEAS,
    DONE,
}

enum class TaskPriorityFilter(val label: String) {
    ALL("Все"),
    A("A"),
    B("B"),
    C("C"),
    D("D"),
}

enum class InsightStatusFilter(val label: String, val apiStatus: String?) {
    PENDING("Новые", "pending"),
    DONE("Готово", "done"),
    IGNORED("Скрытые", "ignored"),
    ALL("Все", null),
}

data class TaskBoardUiState(
    val today: List<SollTask> = emptyList(),
    val blocked: List<SollTask> = emptyList(),
    val inbox: List<SollTask> = emptyList(),
    val stale: List<SollTask> = emptyList(),
    val deferred: List<SollTask> = emptyList(),
    val doneRecent: List<SollTask> = emptyList(),
    val isLoading: Boolean = false,
    val actionTaskId: String? = null,
    val evidenceTaskId: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val isShowingCache: Boolean = false,
    val pendingEvidenceTaskIds: Set<String> = emptySet(),
    val pendingTaskActionIds: Set<String> = emptySet(),
    val selectedMode: TaskWorkspaceMode = TaskWorkspaceMode.TASKS,
    val selectedTab: TaskTab = TaskTab.ALL,
    val selectedPriority: TaskPriorityFilter = TaskPriorityFilter.ALL,
    val searchQuery: String = "",
    val visibleTasks: List<SollTask> = emptyList(),
    val ideaCount: Int = 0,
    val taskIndex: List<SollTask> = emptyList(),
    val ideaTaskIndex: List<SollTask> = emptyList(),
    val insights: List<SollLearningItem> = emptyList(),
    val selectedInsightStatus: InsightStatusFilter = InsightStatusFilter.PENDING,
    val roadmap: SollRoadmap? = null,
    val sources: List<SollMonitoredSource> = emptyList(),
    val sourceItems: List<SollSourceItem> = emptyList(),
    val selectedSourceId: String? = null,
    val sourceItemTaskId: String? = null,
    val roadmapLineTaskKey: String? = null,
    val workspaceLoading: Boolean = false,
    val taskCounts: SollTaskBoardCounts? = null,
    val taskBoardLimitPerSection: Int? = null,
    val requestedTaskBoardLimitPerSection: Int = DEFAULT_TASK_BOARD_SECTION_LIMIT,
) {
    val openCount: Int
        get() = taskCounts?.openCount ?: displayedOpenCount

    val displayedOpenCount: Int
        get() = today.size + blocked.size + inbox.size + stale.size + deferred.size

    val doneCount: Int
        get() = taskCounts?.doneRecent ?: displayedDoneCount

    val displayedDoneCount: Int
        get() = doneRecent.size

    val totalCount: Int
        get() = openCount + doneCount

    val displayedTotalCount: Int
        get() = displayedOpenCount + displayedDoneCount

    val hasLimitedOpenSections: Boolean
        get() = taskCounts?.let { displayedOpenCount < it.openCount } == true

    val hasLimitedDoneSection: Boolean
        get() = taskCounts?.let { displayedDoneCount < it.doneRecent } == true

    val hasLimitedSections: Boolean
        get() = hasLimitedOpenSections || hasLimitedDoneSection

    val hasLimitedSelectedTaskSection: Boolean
        get() = when (selectedTab) {
            TaskTab.ALL -> hasLimitedOpenSections
            TaskTab.TODAY -> taskCounts?.let { today.size < it.today } == true
            TaskTab.BLOCKED -> taskCounts?.let { blocked.size < it.blocked } == true
            TaskTab.INBOX -> taskCounts?.let { inbox.size < it.inbox } == true
            TaskTab.STALE -> taskCounts?.let { stale.size < it.stale } == true
            TaskTab.DEFERRED -> taskCounts?.let { deferred.size < it.deferred } == true
            TaskTab.IDEAS -> false
            TaskTab.DONE -> hasLimitedDoneSection
        }

    val selectedDisplayedTaskCount: Int
        get() = when (selectedTab) {
            TaskTab.ALL -> displayedOpenCount
            TaskTab.TODAY -> today.size
            TaskTab.BLOCKED -> blocked.size
            TaskTab.INBOX -> inbox.size
            TaskTab.STALE -> stale.size
            TaskTab.DEFERRED -> deferred.size
            TaskTab.IDEAS -> ideaCount
            TaskTab.DONE -> displayedDoneCount
        }

    val selectedTaskCount: Int
        get() = when (selectedTab) {
            TaskTab.ALL -> openCount
            TaskTab.TODAY -> taskCounts?.today ?: today.size
            TaskTab.BLOCKED -> taskCounts?.blocked ?: blocked.size
            TaskTab.INBOX -> taskCounts?.inbox ?: inbox.size
            TaskTab.STALE -> taskCounts?.stale ?: stale.size
            TaskTab.DEFERRED -> taskCounts?.deferred ?: deferred.size
            TaskTab.IDEAS -> ideaCount
            TaskTab.DONE -> doneCount
        }

    val pendingEvidenceCount: Int
        get() = pendingEvidenceTaskIds.size

    val pendingTaskActionCount: Int
        get() = pendingTaskActionIds.size

    val canLoadMoreTasks: Boolean
        get() = hasLimitedSelectedTaskSection && requestedTaskBoardLimitPerSection < MAX_TASK_BOARD_SECTION_LIMIT
}

@HiltViewModel
class TaskBoardViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val syncQueueRepository: SollSyncQueueRepository,
    private val taskCacheRepository: TaskCacheRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskBoardUiState(isLoading = true))
    val uiState: StateFlow<TaskBoardUiState> = _uiState.asStateFlow()
    private val sourceItemsCache = mutableMapOf<String, List<SollSourceItem>>()

    init {
        observePendingTaskEvidence()
        refresh()
        observeServerUpdates()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            }
            val sectionLimit = _uiState.value.requestedTaskBoardLimitPerSection
            sollGateway.getTaskBoard(limitPerSection = sectionLimit).fold(
                onSuccess = { board ->
                    val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
                    val adjustedBoard = taskCacheRepository.replaceWith(board, pendingStatuses)
                    _uiState.update {
                        it.copy(
                            today = adjustedBoard.today,
                            blocked = adjustedBoard.blocked,
                            inbox = adjustedBoard.inbox,
                            stale = adjustedBoard.stale,
                            deferred = adjustedBoard.deferred,
                            doneRecent = adjustedBoard.doneRecent,
                            taskCounts = adjustedBoard.counts,
                            taskBoardLimitPerSection = adjustedBoard.limitPerSection,
                            isLoading = false,
                            message = null,
                            isError = false,
                            isShowingCache = false,
                        ).rebuildTaskIndex().deriveTaskList()
                    }
                },
                onFailure = { error ->
                    val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
                    val cachedBoard = taskCacheRepository.getCachedBoard(pendingStatuses)
                    if (cachedBoard.openCount > 0 || cachedBoard.doneRecent.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                today = cachedBoard.today,
                                blocked = cachedBoard.blocked,
                                inbox = cachedBoard.inbox,
                                stale = cachedBoard.stale,
                                deferred = cachedBoard.deferred,
                                doneRecent = cachedBoard.doneRecent,
                                taskCounts = cachedBoard.counts,
                                taskBoardLimitPerSection = cachedBoard.limitPerSection,
                                isLoading = false,
                                message = "Сервер недоступен. Показан локальный кэш задач.",
                                isError = false,
                                isShowingCache = true,
                            ).rebuildTaskIndex().deriveTaskList()
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
            refreshSelectedWorkspace(showLoading = false)
        }
    }

    fun moveToToday(task: SollTask) {
        runTaskAction(
            task = task,
            successMessage = "Задача перенесена на сегодня",
            queueAction = SollSyncQueueRepository.TASK_ACTION_MOVE_TO_TODAY,
            optimisticStatus = "today",
        ) { taskId ->
            sollGateway.moveTaskToToday(taskId)
        }
    }

    fun startTask(task: SollTask) {
        runTaskAction(
            task = task,
            successMessage = "Задача взята в работу",
            queueAction = SollSyncQueueRepository.TASK_ACTION_SET_STATUS,
            optimisticStatus = "in_progress",
            targetStatus = "in_progress",
        ) { taskId ->
            sollGateway.setTaskStatus(taskId, "in_progress")
        }
    }

    fun completeTask(task: SollTask) {
        runTaskAction(
            task = task,
            successMessage = "Задача закрыта",
            queueAction = SollSyncQueueRepository.TASK_ACTION_COMPLETE,
            optimisticStatus = "done",
        ) { taskId ->
            sollGateway.completeTask(taskId)
        }
    }

    fun deferTask(task: SollTask) {
        runTaskAction(
            task = task,
            successMessage = "Задача отложена",
            queueAction = SollSyncQueueRepository.TASK_ACTION_DEFER,
            optimisticStatus = "deferred",
        ) { taskId ->
            sollGateway.deferTask(taskId)
        }
    }

    fun rejectTask(task: SollTask) {
        runTaskAction(
            task = task,
            successMessage = "Задача отклонена",
            queueAction = SollSyncQueueRepository.TASK_ACTION_REJECT,
            optimisticStatus = "rejected",
        ) { taskId ->
            sollGateway.rejectTask(taskId)
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

    fun selectMode(mode: TaskWorkspaceMode) {
        _uiState.update { it.copy(selectedMode = mode) }
        when (mode) {
            TaskWorkspaceMode.TASKS -> Unit
            TaskWorkspaceMode.INSIGHTS -> loadInsights()
            TaskWorkspaceMode.ROADMAP -> loadRoadmap()
            TaskWorkspaceMode.SOURCES -> loadSources()
        }
    }

    fun selectTab(tab: TaskTab) {
        _uiState.update { it.copy(selectedTab = tab).deriveTaskList() }
    }

    fun selectPriority(priority: TaskPriorityFilter) {
        _uiState.update { it.copy(selectedPriority = priority).deriveTaskList() }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query).deriveTaskList() }
    }

    fun loadMoreTasks() {
        val nextLimit = (_uiState.value.requestedTaskBoardLimitPerSection * 2)
            .coerceAtMost(MAX_TASK_BOARD_SECTION_LIMIT)
        if (nextLimit == _uiState.value.requestedTaskBoardLimitPerSection) {
            return
        }
        _uiState.update { it.copy(requestedTaskBoardLimitPerSection = nextLimit) }
        refresh(showLoading = true)
    }

    fun loadInsights(showLoading: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = showLoading, message = null, isError = false) }
            val status = _uiState.value.selectedInsightStatus.apiStatus
            sollGateway.getLearningItems(status = status, limit = 100).fold(
                onSuccess = { insights ->
                    _uiState.update { it.copy(insights = insights, workspaceLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось загрузить инсайты",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun selectInsightStatus(filter: InsightStatusFilter) {
        _uiState.update { it.copy(selectedInsightStatus = filter) }
        loadInsights()
    }

    fun markInsight(item: SollLearningItem, status: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.updateLearningItemStatus(item.id, status).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            insights = it.insights.filterNot { insight -> insight.id == item.id },
                            workspaceLoading = false,
                            message = if (status == "ignored") "Инсайт скрыт" else "Инсайт обновлен",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось обновить инсайт",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun createTaskFromInsight(item: SollLearningItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.createTaskFromLearningItem(item.id).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            insights = it.insights.filterNot { insight -> insight.id == item.id },
                            workspaceLoading = false,
                            message = "Задача создана из инсайта",
                            isError = false,
                        )
                    }
                    refresh(showLoading = false)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось создать задачу",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun loadRoadmap(showLoading: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = showLoading, message = null, isError = false) }
            sollGateway.getRoadmap().fold(
                onSuccess = { roadmap ->
                    _uiState.update { it.copy(roadmap = roadmap, workspaceLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось загрузить roadmap",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun addRoadmapLine(stageId: String, line: String, text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.addRoadmapLine(stageId, line, text).fold(
                onSuccess = { roadmap ->
                    _uiState.update {
                        it.copy(
                            roadmap = roadmap,
                            workspaceLoading = false,
                            message = "Roadmap обновлен",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось обновить roadmap",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun updateRoadmapLine(stageId: String, oldLine: String, line: String, text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.updateRoadmapLine(stageId, oldLine, line, text).fold(
                onSuccess = { roadmap ->
                    _uiState.update {
                        it.copy(
                            roadmap = roadmap,
                            workspaceLoading = false,
                            message = "Строка roadmap обновлена",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось обновить строку roadmap",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun deleteRoadmapLine(stageId: String, line: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.deleteRoadmapLine(stageId, line).fold(
                onSuccess = { roadmap ->
                    _uiState.update {
                        it.copy(
                            roadmap = roadmap,
                            workspaceLoading = false,
                            message = "Строка roadmap удалена",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось удалить строку roadmap",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun createTaskFromRoadmapLine(stageId: String, line: SollRoadmapLine) {
        val lineKey = roadmapLineTaskKey(stageId, line.line)
        viewModelScope.launch {
            _uiState.update { it.copy(roadmapLineTaskKey = lineKey, message = null, isError = false) }
            sollGateway.createTaskFromRoadmapLine(stageId, line.line).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            roadmapLineTaskKey = null,
                            message = "Задача создана из roadmap",
                            isError = false,
                        )
                    }
                    refresh(showLoading = false)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            roadmapLineTaskKey = null,
                            message = error.message ?: "Не удалось создать задачу из roadmap",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun loadSources(sourceId: String? = _uiState.value.selectedSourceId, showLoading: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = showLoading, message = null, isError = false) }
            sollGateway.listSources().fold(
                onSuccess = { sources ->
                    val sourceIds = sources.mapTo(mutableSetOf()) { it.id }
                    sourceItemsCache.keys.retainAll(sourceIds)
                    val selected = sourceId?.takeIf { it in sourceIds } ?: sources.firstOrNull()?.id
                    val items = selected?.let { id ->
                        sourceItemsCache[id] ?: sollGateway.listSourceItems(id, limit = 20)
                            .getOrElse { emptyList() }
                            .also { loaded -> sourceItemsCache[id] = loaded }
                    }.orEmpty()
                    _uiState.update {
                        it.copy(
                            sources = sources,
                            selectedSourceId = selected,
                            sourceItems = items,
                            workspaceLoading = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
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
                workspaceLoading = cachedItems == null,
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
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.createSource(name, target, sourceType).fold(
                onSuccess = {
                    sourceItemsCache.clear()
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = "Источник добавлен",
                            isError = false,
                        )
                    }
                    loadSources()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
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
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
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
                            workspaceLoading = false,
                            message = "Источник обновлен",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
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
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.deleteSource(source.id).fold(
                onSuccess = {
                    sourceItemsCache.remove(source.id)
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
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
                            workspaceLoading = false,
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
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.checkSource(source.id).fold(
                onSuccess = { changed ->
                    sourceItemsCache.remove(source.id)
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = if (changed) "Источник обновлен" else "Новых материалов нет",
                            isError = false,
                        )
                    }
                    loadSources(source.id)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось проверить источник",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun createTaskFromSourceItem(sourceId: String, item: SollSourceItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(sourceItemTaskId = item.itemId, message = null, isError = false) }
            sollGateway.createTaskFromSourceItem(sourceId, item.itemId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            sourceItemTaskId = null,
                            message = "Задача создана из материала",
                            isError = false,
                        )
                    }
                    refresh(showLoading = false)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceItemTaskId = null,
                            message = error.message ?: "Не удалось создать задачу из материала",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    private fun loadSourceItems(sourceId: String) {
        viewModelScope.launch {
            val items = sollGateway.listSourceItems(sourceId, limit = 20).getOrElse { error ->
                _uiState.update {
                    if (it.selectedSourceId == sourceId) {
                        it.copy(
                            workspaceLoading = false,
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
                        workspaceLoading = false,
                        message = null,
                        isError = false,
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun runTaskAction(
        task: SollTask,
        successMessage: String,
        queueAction: String,
        optimisticStatus: String,
        targetStatus: String? = null,
        action: suspend (taskId: String) -> Result<SollTask>,
    ) {
        viewModelScope.launch {
            val cleanTaskId = task.id.trim()
            if (cleanTaskId.isBlank()) {
                _uiState.update {
                    it.copy(
                        actionTaskId = null,
                        message = "У задачи нет ID. Обнови список задач с сервера и повтори действие.",
                        isError = true,
                    )
                }
                return@launch
            }
            val actionTask = task.copy(id = cleanTaskId)
            _uiState.update { it.copy(actionTaskId = cleanTaskId, message = null, isError = false) }
            action(cleanTaskId).fold(
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
                    if (error.isRetryableTaskActionFailure()) {
                        queueOfflineTaskAction(
                            task = actionTask,
                            queueAction = queueAction,
                            targetStatus = targetStatus,
                            optimisticStatus = optimisticStatus,
                            reason = error.message,
                            successMessage = successMessage,
                        )
                    } else {
                        refreshAfterTaskConflict(error.message ?: "Не удалось изменить задачу")
                    }
                }
            )
        }
    }

    private suspend fun queueOfflineTaskAction(
        task: SollTask,
        queueAction: String,
        targetStatus: String?,
        optimisticStatus: String,
        reason: String?,
        successMessage: String,
    ) {
        val queued = try {
            syncQueueRepository.enqueueTaskAction(
                taskId = task.id,
                taskTitle = task.title,
                action = queueAction,
                targetStatus = targetStatus,
                reason = reason,
            )
            val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
            taskCacheRepository.applyOptimisticTaskStatus(
                task = task,
                status = optimisticStatus,
                pendingStatuses = pendingStatuses,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            refreshAfterTaskConflict("Не удалось поставить действие в очередь: ${error.message ?: "ошибка"}")
            return
        }

        val board = queued
        _uiState.update {
            it.copy(
                today = board.today,
                blocked = board.blocked,
                inbox = board.inbox,
                stale = board.stale,
                deferred = board.deferred,
                doneRecent = board.doneRecent,
                taskCounts = board.counts,
                taskBoardLimitPerSection = board.limitPerSection,
                isLoading = false,
                actionTaskId = null,
                message = "$successMessage. Сервер недоступен, действие поставлено в очередь.",
                isError = false,
                isShowingCache = true,
            ).rebuildTaskIndex().deriveTaskList()
        }
    }

    private fun observePendingTaskEvidence() {
        viewModelScope.launch {
            syncQueueRepository.observeRecentItems(limit = 100).collectLatest { items ->
                val evidenceTaskIds = items
                    .filter { it.status in OPEN_SYNC_STATUSES }
                    .filter { it.kind in EVIDENCE_SYNC_KINDS }
                    .mapNotNull { it.taskIdOrNull() }
                    .toSet()
                val taskActionIds = items
                    .filter { it.status in OPEN_SYNC_STATUSES }
                    .filter { it.kind == SyncQueueEntity.KIND_TASK_ACTION }
                    .mapNotNull { it.taskIdOrNull() }
                    .toSet()
                _uiState.update {
                    it.copy(
                        pendingEvidenceTaskIds = evidenceTaskIds,
                        pendingTaskActionIds = taskActionIds,
                    )
                }
            }
        }
    }

    private fun refreshSelectedWorkspace(showLoading: Boolean) {
        if (_uiState.value.workspaceLoading) return
        when (_uiState.value.selectedMode) {
            TaskWorkspaceMode.TASKS -> Unit
            TaskWorkspaceMode.INSIGHTS -> loadInsights(showLoading = showLoading)
            TaskWorkspaceMode.ROADMAP -> loadRoadmap(showLoading = showLoading)
            TaskWorkspaceMode.SOURCES -> loadSources(showLoading = showLoading)
        }
    }

    private fun observeServerUpdates() {
        viewModelScope.launch {
            while (isActive) {
                delay(TASK_REFRESH_INTERVAL_MS)
                if (_uiState.value.actionTaskId == null && !_uiState.value.isLoading) {
                    refresh(showLoading = false)
                }
            }
        }
    }

    private suspend fun refreshAfterTaskConflict(reason: String) {
        val sectionLimit = _uiState.value.requestedTaskBoardLimitPerSection
        sollGateway.getTaskBoard(limitPerSection = sectionLimit).fold(
            onSuccess = { board ->
                val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
                val adjustedBoard = taskCacheRepository.replaceWith(board, pendingStatuses)
                _uiState.update {
                    it.copy(
                        today = adjustedBoard.today,
                        blocked = adjustedBoard.blocked,
                        inbox = adjustedBoard.inbox,
                        stale = adjustedBoard.stale,
                        deferred = adjustedBoard.deferred,
                        doneRecent = adjustedBoard.doneRecent,
                        taskCounts = adjustedBoard.counts,
                        taskBoardLimitPerSection = adjustedBoard.limitPerSection,
                        isLoading = false,
                        actionTaskId = null,
                        message = "$reason. Доска обновлена с сервера.",
                        isError = true,
                        isShowingCache = false,
                    ).rebuildTaskIndex().deriveTaskList()
                }
            },
            onFailure = {
                val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
                val cachedBoard = taskCacheRepository.getCachedBoard(pendingStatuses)
                _uiState.update {
                    it.copy(
                        today = cachedBoard.today,
                        blocked = cachedBoard.blocked,
                        inbox = cachedBoard.inbox,
                        stale = cachedBoard.stale,
                        deferred = cachedBoard.deferred,
                        doneRecent = cachedBoard.doneRecent,
                        taskCounts = cachedBoard.counts,
                        taskBoardLimitPerSection = cachedBoard.limitPerSection,
                        isLoading = false,
                        actionTaskId = null,
                        message = "$reason. Сервер недоступен, показан локальный кэш.",
                        isError = true,
                        isShowingCache = true,
                    ).rebuildTaskIndex().deriveTaskList()
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

    private fun Throwable.isRetryableTaskActionFailure(): Boolean {
        if (this is IOException) return true
        val httpError = this as? HttpException ?: return false
        return httpError.code() == 408 || httpError.code() == 429 || httpError.code() >= 500
    }

    private companion object {
        val OPEN_SYNC_STATUSES = setOf(
            SyncQueueEntity.STATUS_PENDING,
            SyncQueueEntity.STATUS_RUNNING,
            SyncQueueEntity.STATUS_FAILED,
        )
        val EVIDENCE_SYNC_KINDS = setOf(
            SyncQueueEntity.KIND_RAW_NOTE,
            SyncQueueEntity.KIND_RAW_FILE,
        )
    }
}

internal fun TaskBoardUiState.deriveTaskList(): TaskBoardUiState {
    val base = when (selectedTab) {
        TaskTab.ALL -> openTasksRaw()
        TaskTab.TODAY -> today
        TaskTab.BLOCKED -> blocked
        TaskTab.INBOX -> inbox
        TaskTab.STALE -> stale
        TaskTab.DEFERRED -> deferred
        TaskTab.IDEAS -> ideaTaskIndex
        TaskTab.DONE -> doneRecent
    }
    return copy(
        visibleTasks = base.filterByPriority(selectedPriority).filterByQuery(searchQuery),
        ideaCount = ideaTaskIndex.size,
    )
}

internal fun TaskBoardUiState.rebuildTaskIndex(): TaskBoardUiState {
    val allTasks = allTasksRaw()
    val ideas = openTasksRaw().filter { it.isIdeaTask() }
    return copy(
        taskIndex = allTasks,
        ideaTaskIndex = ideas,
        ideaCount = ideas.size,
    )
}

private fun TaskBoardUiState.openTasksRaw(): List<SollTask> = buildList {
    val seen = LinkedHashSet<String>()

    fun addUnique(tasks: List<SollTask>) {
        tasks.forEach { task ->
            if (seen.add(task.id)) {
                add(task)
            }
        }
    }

    addUnique(today)
    addUnique(blocked)
    addUnique(inbox)
    addUnique(stale)
    addUnique(deferred)
}

private fun TaskBoardUiState.allTasksRaw(): List<SollTask> = buildList {
    val seen = LinkedHashSet<String>()

    fun addUnique(tasks: List<SollTask>) {
        tasks.forEach { task ->
            if (seen.add(task.id)) {
                add(task)
            }
        }
    }

    addUnique(today)
    addUnique(blocked)
    addUnique(inbox)
    addUnique(stale)
    addUnique(deferred)
    addUnique(doneRecent)
}

internal fun roadmapLineTaskKey(stageId: String, line: String): String =
    "${stageId.trim()}::${line.trim()}"

private fun List<SollTask>.filterByPriority(filter: TaskPriorityFilter): List<SollTask> =
    if (filter == TaskPriorityFilter.ALL) {
        this
    } else {
        filter { it.priority.normalizedTaskPriorityLabel() == filter.label }
    }

private fun List<SollTask>.filterByQuery(query: String): List<SollTask> {
    val needle = query.trim()
    if (needle.isBlank()) return this
    return filter { task -> task.matchesTaskQuery(needle) }
}

internal fun SollTask.matchesTaskQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true
    return title.contains(needle, ignoreCase = true) ||
        description.contains(needle, ignoreCase = true) ||
        projectName.orEmpty().contains(needle, ignoreCase = true) ||
        sourceRef.contains(needle, ignoreCase = true) ||
        status.contains(needle, ignoreCase = true) ||
        priority.contains(needle, ignoreCase = true) ||
        tags.any { tag -> tag.contains(needle, ignoreCase = true) }
}

private fun SollTask.isIdeaTask(): Boolean =
    IDEA_TASK_MARKERS.any { marker -> matchesTaskQuery(marker) }

private fun String.normalizedTaskPriorityLabel(): String =
    when (trim().uppercase()) {
        "", "B", "P2" -> "B"
        "A", "P1" -> "A"
        "C", "P3" -> "C"
        "D", "P4" -> "D"
        else -> trim().uppercase()
    }

private fun String.parseTags(): List<String> =
    split(',', ';', '\n')
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinct()

private val IDEA_TASK_MARKERS = listOf("idea", "идея", "ideas", "opportunity", "ниша")
private const val DEFAULT_TASK_BOARD_SECTION_LIMIT = 80
private const val MAX_TASK_BOARD_SECTION_LIMIT = 500
private const val TASK_REFRESH_INTERVAL_MS = 30_000L
