package com.soll.presentation.screens.tasks

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.soll.domain.soll.SollTaskInput
import com.soll.domain.soll.SollTaskProject
import kotlinx.coroutines.Job
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
import com.soll.domain.soll.SollSourceItemsPage
import com.soll.domain.soll.SollSourceScope
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
    SOURCES("Источники Soll"),
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

enum class SourcesSubTab(val label: String) {
    LIST("Источники"),
    MATERIALS("Материалы"),
}

data class TaskBoardUiState(
    val projects: List<SollTaskProject> = emptyList(),
    val selectedProjectId: String? = null,
    val pageTasks: List<SollTask>? = null,
    val pageTotal: Int? = null,
    val pageHasMore: Boolean = false,
    val pageNextOffset: Int = 0,
    val pageLoading: Boolean = false,
    val detailTask: SollTask? = null,
    val editorVisible: Boolean = false,
    val editorTaskId: String? = null,
    val editorInput: SollTaskInput = SollTaskInput(""),
    val editorSaving: Boolean = false,
    val editorError: String? = null,
    val workspaceActionRevision: Int = 0,
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
    val selectedSourcesSubTab: SourcesSubTab = SourcesSubTab.LIST,
    val sourceItemTaskId: String? = null,
    val sourceItemsHasMore: Boolean = false,
    val sourceItemsTotal: Int = 0,
    val sourceItemsLoadingMore: Boolean = false,
    val sourceDisabledReason: String = "",
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

    val routedOpenTaskCount: Int
        get() = openTasksRaw().count { it.hasRoutingContext() }

    val canLoadMoreTasks: Boolean
        get() = if (pageTasks != null) pageHasMore else hasLimitedSelectedTaskSection && requestedTaskBoardLimitPerSection < MAX_TASK_BOARD_SECTION_LIMIT
}

@HiltViewModel
class TaskBoardViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val syncQueueRepository: SollSyncQueueRepository,
    private val taskCacheRepository: TaskCacheRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskBoardUiState(isLoading = true))
    val uiState: StateFlow<TaskBoardUiState> = _uiState.asStateFlow()
    private val sourceItemsCache = mutableMapOf<String, SollSourceItemsPage>()
    private var pageJob: Job? = null
    private var pageGeneration = 0
    private var editorRequestId: String = savedStateHandle["tasks.editor.request_id"] ?: java.util.UUID.randomUUID().toString()

    init {
        if (savedStateHandle.contains("tasks.editor.title")) {
            _uiState.update { it.copy(editorVisible = savedStateHandle["tasks.editor.visible"] ?: false, editorTaskId = savedStateHandle["tasks.editor.task_id"],
                editorInput = SollTaskInput(savedStateHandle["tasks.editor.title"] ?: "", savedStateHandle["tasks.editor.description"] ?: "",
                    savedStateHandle["tasks.editor.priority"] ?: "B", savedStateHandle["tasks.editor.project_id"] ?: "",
                    savedStateHandle["tasks.editor.project_name"] ?: "", savedStateHandle["tasks.editor.due_date"] ?: "")) }
        }
        _uiState.update { it.copy(
            searchQuery = savedStateHandle["tasks.search"] ?: "",
            selectedProjectId = savedStateHandle["tasks.project"],
            selectedTab = runCatching { TaskTab.valueOf(savedStateHandle["tasks.section"] ?: "ALL") }.getOrDefault(TaskTab.ALL),
            selectedPriority = runCatching { TaskPriorityFilter.valueOf(savedStateHandle["tasks.priority"] ?: "ALL") }.getOrDefault(TaskPriorityFilter.ALL),
        ) }
        observePendingTaskEvidence()
        refresh()
        observeServerUpdates()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) launch {
                sollGateway.getTaskProjects().onSuccess { projects -> _uiState.update { it.copy(projects = projects).deriveTaskList() } }
            }
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            }
            val sectionLimit = _uiState.value.requestedTaskBoardLimitPerSection
            sollGateway.getTaskBoard(limitPerSection = sectionLimit).fold(
                onSuccess = { board ->
                    val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
                    // A bounded board must not evict pages already saved for offline use.
                    val adjustedBoard = taskCacheRepository.mergeBoard(board, pendingStatuses)
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
                            pageTasks = null,
                            pageTotal = null,
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
            refreshSelectedWorkspace(showLoading = showLoading)
            if (_uiState.value.selectedMode == TaskWorkspaceMode.TASKS && !_uiState.value.isShowingCache) loadTaskPage()
        }
    }

    fun openTask(task: SollTask) { _uiState.update { it.copy(detailTask = task) } }
    fun closeTask() { _uiState.update { it.copy(detailTask = null) } }

    fun openEditor(task: SollTask? = null) {
        val state = _uiState.value
        val hasMatchingDraft = state.editorTaskId == task?.id && (state.editorInput.title.isNotBlank() || state.editorInput.description.isNotBlank())
        val input = if (hasMatchingDraft) state.editorInput else if (task != null) SollTaskInput(task.title, task.description, task.priority.normalizedTaskPriorityLabel(),
            task.projectId ?: _uiState.value.projects.firstOrNull { it.name == task.projectName }?.id.orEmpty(), task.projectName.orEmpty(), task.dueDate.orEmpty()) else
            _uiState.value.editorInput.takeIf { _uiState.value.editorTaskId == null } ?: SollTaskInput("")
        _uiState.update { it.copy(editorVisible = true, editorTaskId = task?.id, editorInput = input, editorError = null) }
        savedStateHandle["tasks.editor.task_id"] = task?.id
        savedStateHandle["tasks.editor.visible"] = true
        updateEditor(input)
    }

    fun closeEditor() {
        if (_uiState.value.editorSaving) return
        _uiState.update { it.copy(editorVisible = false) }
        savedStateHandle["tasks.editor.visible"] = false
    }

    fun updateEditor(input: SollTaskInput) {
        if (_uiState.value.editorSaving) return
        if (input != _uiState.value.editorInput) editorRequestId = java.util.UUID.randomUUID().toString()
        _uiState.update { it.copy(editorInput = input, editorError = null) }
        savedStateHandle["tasks.editor.title"] = input.title
        savedStateHandle["tasks.editor.description"] = input.description
        savedStateHandle["tasks.editor.priority"] = input.priority
        savedStateHandle["tasks.editor.project_id"] = input.projectId
        savedStateHandle["tasks.editor.project_name"] = input.projectName
        savedStateHandle["tasks.editor.due_date"] = input.dueDate
        savedStateHandle["tasks.editor.request_id"] = editorRequestId
    }

    fun saveEditor() {
        val state = _uiState.value
        if (state.editorSaving) return
        val input = state.editorInput
        val error = taskInputError(input)
        if (error != null) { _uiState.update { it.copy(editorError = error) }; return }
        _uiState.update { it.copy(editorSaving = true, editorError = null) }
        viewModelScope.launch {
            val result = state.editorTaskId?.let { sollGateway.updateTaskDetails(it, input) }
                ?: sollGateway.createTask(input, editorRequestId)
            result.fold(onSuccess = { task ->
                taskCacheRepository.mergePage(listOf(task))
                _uiState.update { it.copy(editorVisible = false, editorSaving = false, editorInput = SollTaskInput(""),
                    editorTaskId = null, detailTask = task, message = if (state.editorTaskId == null) "Задача создана" else "Задача обновлена", isError = false) }
                savedStateHandle.keys().filter { it.startsWith("tasks.editor.") }.forEach { savedStateHandle.remove<Any?>(it) }
                editorRequestId = java.util.UUID.randomUUID().toString()
                refresh(showLoading = false)
            }, onFailure = { failure ->
                _uiState.update { it.copy(editorSaving = false, editorError = failure.taskWorkspaceMessage()) }
            })
        }
    }

    fun selectProject(projectId: String?) {
        savedStateHandle["tasks.project"] = projectId
        _uiState.update { it.copy(selectedProjectId = projectId).deriveTaskList() }
        loadTaskPage(reset = true)
    }

    private fun loadTaskPage(reset: Boolean = false, append: Boolean = false, debounce: Boolean = false) {
        pageJob?.cancel()
        val generation = ++pageGeneration
        val state = _uiState.value
        val offset = if (append) state.pageNextOffset else 0
        val target = if (append || reset) 80 else maxOf(80, state.pageTasks?.size ?: 0)
        _uiState.update { it.copy(pageLoading = true, pageTasks = if (reset && it.pageTasks != null) emptyList() else it.pageTasks) }
        pageJob = viewModelScope.launch {
            if (debounce) delay(300)
            var rows = emptyList<SollTask>()
            var total = 0
            var hasMore = false
            var cursor = offset
            do {
                val result = sollGateway.getTaskPage(state.selectedTab.name.lowercase(), state.searchQuery,
                    state.selectedProjectId, state.selectedPriority.name.takeUnless { it == "ALL" }, cursor)
                val page = result.getOrElse { failure ->
                    if (generation == pageGeneration) _uiState.update {
                        // Older servers retain the existing board until their task routes are deployed.
                        it.copy(pageLoading = false, message = if (it.pageTasks != null) failure.taskWorkspaceMessage() else it.message,
                            isError = it.pageTasks != null).deriveTaskList()
                    }
                    return@launch
                }
                if (generation != pageGeneration) return@launch
                rows = (rows + page.tasks).distinctBy { it.id }
                cursor += page.tasks.size
                total = page.total
                hasMore = page.hasMore && page.tasks.isNotEmpty()
            } while (!append && rows.size < target && hasMore)
            val pending = syncQueueRepository.getPendingTaskActionStatuses()
            rows = rows.map { task -> pending[task.id]?.let { task.copy(status = it) } ?: task }
            taskCacheRepository.mergePage(rows)
            _uiState.update {
                val loaded = if (append) (it.pageTasks.orEmpty() + rows).distinctBy { task -> task.id } else rows
                it.copy(pageTasks = loaded, pageTotal = total, pageHasMore = hasMore, pageNextOffset = cursor, pageLoading = false,
                    detailTask = it.detailTask?.let { task -> loaded.firstOrNull { row -> row.id == task.id } ?: task }).deriveTaskList()
            }
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
        if (_uiState.value.evidenceTaskId != null || task.id in _uiState.value.pendingEvidenceTaskIds) return
        _uiState.update { it.copy(evidenceTaskId = task.id, message = null, isError = false) }
        viewModelScope.launch {
            sollGateway.uploadTaskEvidence(task.id, uri).fold(onSuccess = { updated ->
                taskCacheRepository.mergePage(listOf(updated))
                _uiState.update { it.copy(evidenceTaskId = null, detailTask = updated,
                    pageTasks = it.pageTasks?.map { row -> if (row.id == updated.id) updated else row },
                    message = "Вложение добавлено к задаче", isError = false) }
                refresh(showLoading = false)
            }, onFailure = { error ->
                if (error.isRetryableTaskActionFailure()) {
                    try {
                        syncQueueRepository.enqueueRawFile(uri = uri, reason = error.message, taskId = task.id, taskTitle = task.title)
                        _uiState.update { it.copy(evidenceTaskId = null, message = "Нет связи. Вложение сохранено в очередь синхронизации.", isError = false) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        _uiState.update { it.copy(evidenceTaskId = null, message = "Не удалось сохранить вложение в очередь. Проверь свободное место и повтори.", isError = true) }
                    }
                } else _uiState.update { it.copy(evidenceTaskId = null, message = error.taskWorkspaceMessage(), isError = true) }
            })
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun selectMode(mode: TaskWorkspaceMode) {
        _uiState.update { it.copy(selectedMode = mode) }
        when (mode) {
            TaskWorkspaceMode.TASKS -> loadTaskPage()
            TaskWorkspaceMode.INSIGHTS -> loadInsights()
            TaskWorkspaceMode.ROADMAP -> loadRoadmap()
            TaskWorkspaceMode.SOURCES -> loadSources()
        }
    }

    fun selectTab(tab: TaskTab) {
        savedStateHandle["tasks.section"] = tab.name
        _uiState.update { it.copy(selectedTab = tab).deriveTaskList() }
        loadTaskPage(reset = true)
    }

    fun selectPriority(priority: TaskPriorityFilter) {
        savedStateHandle["tasks.priority"] = priority.name
        _uiState.update { it.copy(selectedPriority = priority).deriveTaskList() }
        loadTaskPage(reset = true)
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle["tasks.search"] = query
        _uiState.update { it.copy(searchQuery = query).deriveTaskList() }
        loadTaskPage(reset = true, debounce = true)
    }

    /** Open the canonical task even when it lies beyond the loaded page or current filters. */
    fun focusTask(taskId: String) {
        val id = taskId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            sollGateway.getTask(id).fold(onSuccess = { openTask(it) }, onFailure = { failure ->
                val cached = _uiState.value.taskIndex.firstOrNull { it.id == id }
                if (cached != null) openTask(cached) else _uiState.update { it.copy(message = failure.message ?: "Задача не найдена", isError = true) }
            })
        }
    }

    fun loadMoreTasks() {
        if (_uiState.value.pageLoading) return
        if (_uiState.value.pageTasks != null) { loadTaskPage(append = true); return }
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
        if (_uiState.value.workspaceLoading) return
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
        if (_uiState.value.workspaceLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.addRoadmapLine(stageId, line, text).fold(
                onSuccess = { roadmap ->
                    _uiState.update {
                        it.copy(
                            roadmap = roadmap,
                            workspaceLoading = false,
                            message = "Roadmap обновлен",
                            workspaceActionRevision = it.workspaceActionRevision + 1,
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
        if (_uiState.value.workspaceLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.updateRoadmapLine(stageId, oldLine, line, text).fold(
                onSuccess = { roadmap ->
                    _uiState.update {
                        it.copy(
                            roadmap = roadmap,
                            workspaceLoading = false,
                            message = "Строка roadmap обновлена",
                            workspaceActionRevision = it.workspaceActionRevision + 1,
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
        if (_uiState.value.workspaceLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.deleteRoadmapLine(stageId, line).fold(
                onSuccess = { roadmap ->
                    _uiState.update {
                        it.copy(
                            roadmap = roadmap,
                            workspaceLoading = false,
                            message = "Пункт roadmap архивирован",
                            workspaceActionRevision = it.workspaceActionRevision + 1,
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = error.message ?: "Не удалось архивировать пункт roadmap",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun createTaskFromRoadmapLine(stageId: String, line: SollRoadmapLine) {
        if (_uiState.value.workspaceLoading) return
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
            sollGateway.listSources(SollSourceScope.PROJECT_SOLL).fold(
                onSuccess = { sources ->
                    val sourceIds = sources.mapTo(mutableSetOf()) { it.id }
                    sourceItemsCache.keys.retainAll(sourceIds)
                    val selected = sourceId?.takeIf { it in sourceIds } ?: sources.firstOrNull()?.id
                    val page = selected?.let { id ->
                        sourceItemsCache[id] ?: sollGateway.listSourceItemsPage(id, limit = SOURCE_ITEM_PAGE_SIZE)
                            .getOrNull()
                            ?.also { loaded -> sourceItemsCache[id] = loaded }
                    }
                    _uiState.update {
                        it.copy(
                            sources = sources,
                            selectedSourceId = selected,
                            sourceItems = page?.items.orEmpty(),
                            sourceItemsHasMore = page?.hasMore == true,
                            sourceItemsTotal = page?.total ?: 0,
                            sourceDisabledReason = page?.disabledReason.orEmpty(),
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
        val cachedPage = sourceItemsCache[source.id]
        _uiState.update {
            it.copy(
                selectedSourceId = source.id,
                selectedSourcesSubTab = SourcesSubTab.MATERIALS,
                sourceItems = cachedPage?.items.orEmpty(),
                sourceItemsHasMore = cachedPage?.hasMore == true,
                sourceItemsTotal = cachedPage?.total ?: 0,
                sourceDisabledReason = cachedPage?.disabledReason.orEmpty(),
                workspaceLoading = cachedPage == null,
                message = null,
                isError = false,
            )
        }
        if (cachedPage == null) {
            loadSourceItems(source.id)
        }
    }

    fun selectSourcesSubTab(tab: SourcesSubTab) {
        _uiState.update { it.copy(selectedSourcesSubTab = tab) }
    }

    fun createSource(name: String, target: String, sourceType: String = "web") {
        if (_uiState.value.workspaceLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(workspaceLoading = true, message = null, isError = false) }
            sollGateway.createSource(name, target, SollSourceScope.PROJECT_SOLL, sourceType).fold(
                onSuccess = {
                    sourceItemsCache.clear()
                    _uiState.update {
                        it.copy(
                            workspaceLoading = false,
                            message = "Источник добавлен",
                            workspaceActionRevision = it.workspaceActionRevision + 1,
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
        if (_uiState.value.workspaceLoading) return
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
                            workspaceActionRevision = it.workspaceActionRevision + 1,
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
        if (_uiState.value.workspaceLoading) return
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
                            workspaceActionRevision = it.workspaceActionRevision + 1,
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
        if (_uiState.value.workspaceLoading) return
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
        if (_uiState.value.workspaceLoading) return
        viewModelScope.launch {
            val projectSource = _uiState.value.sources.firstOrNull { source ->
                source.id == sourceId && source.scope == SollSourceScope.PROJECT_SOLL
            }
            if (projectSource == null) {
                _uiState.update {
                    it.copy(
                        sourceItemTaskId = null,
                        message = "Материал не относится к источникам Soll",
                        isError = true,
                    )
                }
                return@launch
            }
            if (!projectSource.enabled || !item.canCreateTask) {
                _uiState.update {
                    it.copy(
                        sourceItemTaskId = null,
                        message = if (!projectSource.enabled) {
                            "Источник отключен: создание задач недоступно"
                        } else {
                            "Материал уже обработан: ${item.lastStatus}"
                        },
                        isError = true,
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(sourceItemTaskId = item.itemId, message = null, isError = false) }
            sollGateway.createTaskFromSourceItem(sourceId, item.itemId).fold(
                onSuccess = {
                    sourceItemsCache[sourceId]?.let { cached ->
                        sourceItemsCache[sourceId] = cached.copy(
                            items = cached.items.map { cachedItem ->
                                if (cachedItem.itemId == item.itemId) cachedItem.copy(lastStatus = "task_created") else cachedItem
                            },
                        )
                    }
                    _uiState.update {
                        it.copy(
                            sourceItemTaskId = null,
                            sourceItems = it.sourceItems.map { current ->
                                if (current.itemId == item.itemId) current.copy(lastStatus = "task_created") else current
                            },
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
            val page = sollGateway.listSourceItemsPage(sourceId, limit = SOURCE_ITEM_PAGE_SIZE).getOrElse { error ->
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
            sourceItemsCache[sourceId] = page
            _uiState.update {
                if (it.selectedSourceId == sourceId) {
                    it.copy(
                        sourceItems = page.items,
                        sourceItemsHasMore = page.hasMore,
                        sourceItemsTotal = page.total,
                        sourceDisabledReason = page.disabledReason,
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

    fun loadMoreSourceItems() {
        val sourceId = _uiState.value.selectedSourceId ?: return
        val cached = sourceItemsCache[sourceId] ?: return
        if (!cached.hasMore || cached.nextCursor.isBlank() || _uiState.value.sourceItemsLoadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(sourceItemsLoadingMore = true, message = null, isError = false) }
            sollGateway.listSourceItemsPage(
                sourceId = sourceId,
                cursor = cached.nextCursor,
                limit = SOURCE_ITEM_PAGE_SIZE,
            ).fold(
                onSuccess = { page ->
                    val merged = (cached.items + page.items).distinctBy { it.itemId }
                    val updated = page.copy(items = merged)
                    sourceItemsCache[sourceId] = updated
                    _uiState.update {
                        if (it.selectedSourceId == sourceId) {
                            it.copy(
                                sourceItems = merged,
                                sourceItemsHasMore = page.hasMore,
                                sourceItemsTotal = page.total,
                                sourceItemsLoadingMore = false,
                                sourceDisabledReason = page.disabledReason,
                            )
                        } else {
                            it.copy(sourceItemsLoadingMore = false)
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sourceItemsLoadingMore = false,
                            message = error.message ?: "Не удалось загрузить следующие материалы",
                            isError = true,
                        )
                    }
                },
            )
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
        if (_uiState.value.actionTaskId != null || task.id in _uiState.value.pendingTaskActionIds) return
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
                onSuccess = { updatedTask ->
                    _uiState.update {
                        it.copy(
                            detailTask = it.detailTask?.let { selected -> if (selected.id == updatedTask.id) updatedTask else selected },
                            pageTasks = it.pageTasks?.map { row -> if (row.id == updatedTask.id) updatedTask else row },
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
                pageTasks = it.pageTasks?.map { row -> if (row.id == task.id) row.copy(status = optimisticStatus) else row },
                detailTask = it.detailTask?.let { selected -> if (selected.id == task.id) selected.copy(status = optimisticStatus) else selected },
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
                if (_uiState.value.actionTaskId == null && !_uiState.value.isLoading && !_uiState.value.editorSaving && !_uiState.value.workspaceLoading) {
                    when (_uiState.value.selectedMode) {
                        TaskWorkspaceMode.SOURCES -> loadSources(showLoading = false)
                        else -> refresh(showLoading = false)
                    }
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
    val base = pageTasks ?: when (selectedTab) {
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
        visibleTasks = base
            .filter { selectedProjectId == null || it.projectId == selectedProjectId ||
                it.projectName == projects.firstOrNull { project -> project.id == selectedProjectId }?.name }
            .filterByPriority(selectedPriority)
            .filterByQuery(searchQuery),
        ideaCount = ideaTaskIndex.size,
    )
}

internal fun taskInputError(input: SollTaskInput): String? = when {
    input.title.isBlank() -> "Напиши название задачи."
    input.title.trim().length > 255 -> "Название должно быть не длиннее 255 символов."
    input.description.length > 12000 -> "Описание должно быть не длиннее 12000 символов."
    input.priority !in listOf("A", "B", "C", "D") -> "Выбери приоритет A, B, C или D."
    input.dueDate.isNotBlank() && runCatching { java.time.LocalDate.parse(input.dueDate) }.isFailure -> "Выбери корректную дату."
    else -> null
}

internal fun Throwable.taskWorkspaceMessage(): String = when {
    this is IOException -> "Нет связи с сервером. Черновик сохранён — повтори попытку после подключения."
    this is HttpException && code() == 401 -> "Не удалось подтвердить подключение устройства. Обнови соединение и повтори попытку."
    this is HttpException && code() == 403 -> "Для устройства не разрешено это действие. Проверь его права на сервере."
    this is HttpException && code() == 404 -> "Метод недоступен на сервере. Для новых действий требуется обновление сервера Soll."
    this is HttpException && code() == 409 -> "Данные изменились на сервере. Обнови список и повтори действие."
    this is HttpException && code() >= 500 -> "Сервер не смог выполнить действие. Черновик сохранён — повтори попытку позже."
    else -> message ?: "Не удалось сохранить. Повтори попытку."
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
    return id.contains(needle, ignoreCase = true) ||
        title.contains(needle, ignoreCase = true) ||
        description.contains(needle, ignoreCase = true) ||
        projectName.orEmpty().contains(needle, ignoreCase = true) ||
        sourceRef.contains(needle, ignoreCase = true) ||
        status.contains(needle, ignoreCase = true) ||
        priority.contains(needle, ignoreCase = true) ||
        tags.any { tag -> tag.contains(needle, ignoreCase = true) } ||
        assignedNodeId.orEmpty().contains(needle, ignoreCase = true) ||
        routingState.contains(needle, ignoreCase = true) ||
        requiredCapabilities.any { capability -> capability.contains(needle, ignoreCase = true) }
}

internal fun SollTask.hasRoutingContext(): Boolean =
    assignedNodeId?.isNotBlank() == true ||
        routingState.isNotBlank() ||
        requiredCapabilities.isNotEmpty()

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
private const val SOURCE_ITEM_PAGE_SIZE = 50
private const val TASK_REFRESH_INTERVAL_MS = 30_000L
