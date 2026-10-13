package com.soll.presentation.screens.tasks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.soll.SollLearningItem
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollRoadmapLine
import com.soll.domain.soll.SollRoadmapReadiness
import com.soll.domain.soll.SollRoadmapStage
import com.soll.domain.soll.SollSourceItem
import com.soll.domain.soll.SollTask
import com.soll.ui.components.PassiveChip
import com.soll.ui.components.RemoteLinkPreviewImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBoardScreen(
    title: String = "Задачи",
    initialMode: TaskWorkspaceMode = TaskWorkspaceMode.TASKS,
    visibleModes: List<TaskWorkspaceMode> = listOf(
        TaskWorkspaceMode.TASKS,
        TaskWorkspaceMode.INSIGHTS,
        TaskWorkspaceMode.ROADMAP,
        TaskWorkspaceMode.SOURCES,
    ),
    onBack: (() -> Unit)? = null,
    focusTaskId: String? = null,
    viewModel: TaskBoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(focusTaskId) {
        focusTaskId?.takeIf { it.isNotBlank() }?.let(viewModel::focusTask)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var evidenceTask by remember { mutableStateOf<SollTask?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSections by remember { mutableStateOf(false) }
    var showCreateSourceDialog by remember { mutableStateOf(false) }
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val task = evidenceTask
        evidenceTask = null
        if (task != null && uri != null) {
            viewModel.attachEvidence(task, uri)
        }
    }

    LaunchedEffect(initialMode) {
        if (uiState.selectedMode != initialMode) {
            viewModel.selectMode(initialMode)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.takeUnless { uiState.isError }?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (uiState.editorVisible) {
        TaskEditorSheet(uiState.editorInput, uiState.projects, uiState.editorTaskId != null,
            uiState.editorSaving, uiState.editorError, viewModel::updateEditor, viewModel::closeEditor, viewModel::saveEditor)
    } else uiState.detailTask?.let { task ->
        TaskDetailSheet(task = task, busy = uiState.actionTaskId != null,
            pendingAction = task.id in uiState.pendingTaskActionIds,
            pendingEvidence = task.id in uiState.pendingEvidenceTaskIds,
            evidenceBusy = uiState.evidenceTaskId == task.id,
            onDismiss = viewModel::closeTask, onEdit = { viewModel.openEditor(task) },
            onAttach = { evidenceTask = task; evidencePicker.launch("*/*") },
            onToday = { viewModel.moveToToday(task) }, onStart = { viewModel.startTask(task) },
            onDone = { viewModel.completeTask(task) }, onDefer = { viewModel.deferTask(task) },
            onReject = { viewModel.rejectTask(task) })
    }
    if (showFilters) TaskFilterSheet(uiState, viewModel::selectPriority, viewModel::selectProject, { showFilters = false })
    LaunchedEffect(uiState.workspaceActionRevision) { showCreateSourceDialog = false }

    if (showCreateSourceDialog) {
        CreateSourceDialog(
            isSaving = uiState.workspaceLoading,
            onDismiss = { showCreateSourceDialog = false },
            onCreate = { name, target ->
                viewModel.createSource(name, target)
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedMode == TaskWorkspaceMode.TASKS) {
                ExtendedFloatingActionButton(onClick = { viewModel.openEditor() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Задача") })
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (uiState.selectedMode == TaskWorkspaceMode.SOURCES &&
                        uiState.selectedSourcesSubTab == SourcesSubTab.LIST
                    ) {
                        IconButton(onClick = { showCreateSourceDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить источник")
                        }
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            TaskWorkspaceTabs(
                selectedMode = uiState.selectedMode,
                visibleModes = visibleModes,
                onSelect = viewModel::selectMode,
            )

            if (uiState.isShowingCache || uiState.pendingTaskActionCount > 0 || uiState.pendingEvidenceCount > 0) {
                Text(if (uiState.isShowingCache) "Нет связи · показаны сохранённые задачи" else "Есть действия в очереди синхронизации",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uiState.pageLoading && uiState.selectedMode == TaskWorkspaceMode.TASKS) LinearProgressIndicator(Modifier.fillMaxWidth())

            uiState.message
                ?.takeIf { uiState.isError }
                ?.let { ErrorMessage(it) }

            if (uiState.workspaceLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (uiState.selectedMode) {
                    TaskWorkspaceMode.TASKS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = uiState.searchQuery, onValueChange = viewModel::updateSearchQuery,
                                    modifier = Modifier.weight(1f), singleLine = true, label = { Text("Поиск задач") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = { if (uiState.searchQuery.isNotBlank()) IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                                    } }, shape = RoundedCornerShape(16.dp))
                                IconButton(onClick = { showFilters = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Фильтры задач")
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Box {
                                    TextButton(onClick = { showSections = true }) {
                                        Text(uiState.selectedTab.label()); Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp))
                                    }
                                    DropdownMenu(expanded = showSections, onDismissRequest = { showSections = false }) {
                                        TaskTab.entries.forEach { tab -> DropdownMenuItem(text = { Text(tab.label()) },
                                            onClick = { viewModel.selectTab(tab); showSections = false }) }
                                    }
                                }
                                Text("${uiState.pageTotal ?: uiState.selectedTaskCount} задач", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (uiState.selectedPriority != TaskPriorityFilter.ALL || uiState.selectedProjectId != null) {
                                TextButton(onClick = { showFilters = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(listOfNotNull(uiState.selectedPriority.label.takeUnless { uiState.selectedPriority == TaskPriorityFilter.ALL },
                                        uiState.selectedProjectId?.let { selected -> uiState.projects.firstOrNull { it.id == selected }?.name ?: "Проект недоступен" }).joinToString(" · "))
                                }
                            }

                            val tasks = uiState.visibleTasks
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                if (tasks.isEmpty() && !uiState.isLoading && !uiState.pageLoading) {
                                    EmptyTasks(tab = uiState.selectedTab)
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(tasks, key = { it.taskListKey() }, contentType = { "task" }) { task ->
                                            CompactTaskRow(task = task,
                                                pending = task.id in uiState.pendingTaskActionIds || task.id in uiState.pendingEvidenceTaskIds,
                                                onOpen = { viewModel.openTask(task) })
                                        }
                                        if (uiState.canLoadMoreTasks) {
                                            item(key = "load-more-tasks", contentType = "load-more") {
                                                OutlinedButton(onClick = viewModel::loadMoreTasks,
                                                    enabled = !uiState.pageLoading && !uiState.isLoading, modifier = Modifier.fillMaxWidth()) {
                                                    Text(if (uiState.pageLoading) "Загружаю…" else "Загрузить ещё")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    TaskWorkspaceMode.INSIGHTS -> InsightsMode(uiState = uiState, viewModel = viewModel)
                    TaskWorkspaceMode.ROADMAP -> RoadmapMode(uiState = uiState, viewModel = viewModel)
                    TaskWorkspaceMode.SOURCES -> SourcesMode(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun TaskWorkspaceTabs(
    selectedMode: TaskWorkspaceMode,
    visibleModes: List<TaskWorkspaceMode>,
    onSelect: (TaskWorkspaceMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleModes.forEach { mode ->
            val selected = selectedMode == mode
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(mode) }
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(2.dp),
                        )
                )
            }
        }
    }
}

@Composable
private fun InsightsMode(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "insight-filters", contentType = "insight-filters") {
            InsightStatusFilters(
                selected = uiState.selectedInsightStatus,
                onSelect = viewModel::selectInsightStatus,
            )
        }
        if (uiState.insights.isEmpty() && !uiState.workspaceLoading) {
            item(key = "empty-insights", contentType = "empty") {
                EmptyWorkspace(text = "Инсайтов в этом статусе нет")
            }
        }
        items(uiState.insights, key = { it.id }) { item ->
            InsightCard(
                item = item,
                onCreateTask = { viewModel.createTaskFromInsight(item) },
                onDone = { viewModel.markInsight(item, "done") },
                onIgnore = { viewModel.markInsight(item, "ignored") },
            )
        }
    }
}

@Composable
private fun InsightStatusFilters(
    selected: InsightStatusFilter,
    onSelect: (InsightStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InsightStatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun InsightCard(
    item: SollLearningItem,
    onCreateTask: () -> Unit,
    onDone: () -> Unit,
    onIgnore: () -> Unit,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (item.nextAction.isNotBlank()) {
                Text(item.nextAction, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassiveChip(text = item.status)
                PassiveChip(text = "Сигналов: ${item.seenCount}")
                item.sourceRef.takeIf { it.isNotBlank() }?.let { PassiveChip(text = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreateTask) { Text("В задачу") }
                OutlinedButton(onClick = onDone) { Text("Готово") }
                TextButton(onClick = onIgnore) { Text("Скрыть") }
            }
        }
    }
}

@Composable
private fun RoadmapMode(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
    var editStage by remember { mutableStateOf<String?>(null) }
    var editLine by remember { mutableStateOf<SollRoadmapLine?>(null) }
    var deleteLine by remember { mutableStateOf<Pair<String, SollRoadmapLine>?>(null) }
    var readinessExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.workspaceActionRevision) { editStage = null; deleteLine = null }
    editStage?.let { stageId -> RoadmapEditorDialog(stageId, editLine, uiState.workspaceLoading,
        uiState.message?.takeIf { uiState.isError }, { editStage = null }, { line, text ->
            val previous = editLine
            if (previous == null) viewModel.addRoadmapLine(stageId, line, text)
            else viewModel.updateRoadmapLine(stageId, previous.line, line, text)
        }) }
    deleteLine?.let { (stageId, line) -> AlertDialog(onDismissRequest = { deleteLine = null },
        title = { Text("Архивировать пункт Roadmap?") }, text = { Text("${line.text}\n\nПункт останется в истории.") },
        confirmButton = { TextButton(onClick = { viewModel.deleteRoadmapLine(stageId, line.line) }, enabled = !uiState.workspaceLoading) { Text("Архивировать") } },
        dismissButton = { TextButton(onClick = { deleteLine = null }, enabled = !uiState.workspaceLoading) { Text("Отмена") } }) }
    val roadmap = uiState.roadmap
    if (roadmap == null) {
        EmptyWorkspace(text = "Roadmap еще не загружен")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "roadmap-head", contentType = "roadmap-head") {
            RoadmapHeaderCard(
                currentStage = roadmap.stages.firstOrNull { it.id == roadmap.currentStage }?.label ?: roadmap.currentStage,
                updated = roadmap.updated,
            )
        }
        if (!roadmap.editSupported) {
            item(key = "roadmap-edit-unavailable") {
                Text("Редактирование станет доступно после обновления сервера.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (roadmap.readiness.isNotEmpty()) {
            item(key = "roadmap-readiness-title", contentType = "roadmap-readiness-title") {
                TextButton(onClick = { readinessExpanded = !readinessExpanded }, modifier = Modifier.fillMaxWidth()) {
                    Text("Готовность по направлениям (${roadmap.readiness.size})", modifier = Modifier.weight(1f))
                    Icon(if (readinessExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (readinessExpanded) "Свернуть готовность" else "Показать готовность")
                }
            }
            if (readinessExpanded) {
                items(
                    items = roadmap.readiness,
                    key = { item -> "readiness:${item.area}" },
                    contentType = { "roadmap-readiness" },
                ) { item ->
                    RoadmapReadinessCard(item = item)
                }
            }
        }
        roadmap.stages.forEach { stage ->
            item(key = "stage:${stage.id}", contentType = "roadmap-stage") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { RoadmapStageHeader(stage = stage) }
                    IconButton(onClick = { editLine = null; editStage = stage.id }, enabled = roadmap.editSupported && !uiState.workspaceLoading) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить пункт в ${stage.label}")
                    }
                }
            }
            items(
                items = stage.lines,
                key = { line -> "line:${stage.id}:${line.line}" },
                contentType = { "roadmap-line" },
            ) { line ->
                Column {
                    RoadmapLineCard(item = line, isCreatingTask = uiState.roadmapLineTaskKey == roadmapLineTaskKey(stage.id, line.line),
                        onCreateTask = { viewModel.createTaskFromRoadmapLine(stage.id, line) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editLine = line; editStage = stage.id }, enabled = roadmap.editSupported && !uiState.workspaceLoading) { Text("Изменить") }
                        if (line.status !in setOf("declined", "replaced")) {
                            TextButton(onClick = { deleteLine = stage.id to line }, enabled = roadmap.editSupported && !uiState.workspaceLoading) { Text("В архив") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadmapHeaderCard(
    currentStage: String,
    updated: String?,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Текущий этап",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currentStage,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            updated?.takeIf { it.isNotBlank() }?.let { value ->
                PassiveChip(text = "Обновлено: $value")
            }
        }
    }
}

@Composable
private fun RoadmapReadinessCard(item: SollRoadmapReadiness) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.area,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            PassiveChip(text = "Готовность: ${item.percent.coerceIn(0, 100)}%")
            LinearProgressIndicator(
                progress = { item.percent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            if (item.gap.isNotBlank()) {
                Text(
                    item.gap,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoadmapStageHeader(
    stage: SollRoadmapStage,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stage.label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PassiveChip(text = when (stage.status) {
                "done" -> "Завершён"
                "current" -> "Сейчас"
                "next" -> "Далее"
                else -> "Запланирован"
            })
        }
    }
}

@Composable
private fun RoadmapLineCard(
    item: SollRoadmapLine,
    isCreatingTask: Boolean,
    onCreateTask: () -> Unit,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        RoadmapLineRow(
            item = item,
            isCreatingTask = isCreatingTask,
            onCreateTask = onCreateTask,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
internal fun RoadmapLineRow(
    item: SollRoadmapLine,
    isCreatingTask: Boolean,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = when (item.status) {
                "active" -> "В работе"
                "blocked" -> "Заблокирован"
                "delivered" -> "Выполнен"
                "deferred" -> "Отложен"
                "declined" -> "В архиве"
                "replaced" -> "Заменён"
                else -> "Запланирован"
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = item.text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.comment.isNotBlank()) {
            Text(item.comment, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCreatingTask) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                if (item.status !in setOf("declined", "replaced")) {
                    TextButton(onClick = onCreateTask) { Text("В задачу") }
                }
            }
        }
    }
}

@Composable
private fun SourcesMode(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = uiState.selectedSourcesSubTab.ordinal, edgePadding = 12.dp) {
            SourcesSubTab.entries.forEach { tab ->
                Tab(
                    selected = uiState.selectedSourcesSubTab == tab,
                    onClick = { viewModel.selectSourcesSubTab(tab) },
                    text = {
                        Text(
                            if (tab == SourcesSubTab.MATERIALS && uiState.sourceItemsTotal > 0) {
                                "${tab.label} (${uiState.sourceItemsTotal})"
                            } else {
                                tab.label
                            }
                        )
                    },
                )
            }
        }

        when (uiState.selectedSourcesSubTab) {
            SourcesSubTab.LIST -> SourcesListTab(uiState = uiState, viewModel = viewModel)
            SourcesSubTab.MATERIALS -> SourceMaterialsTab(uiState = uiState, viewModel = viewModel)
        }
    }
}

@Composable
private fun SourcesListTab(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
    var editingSource by remember { mutableStateOf<SollMonitoredSource?>(null) }
    var deletingSource by remember { mutableStateOf<SollMonitoredSource?>(null) }
    LaunchedEffect(uiState.workspaceActionRevision) { editingSource = null; deletingSource = null }
    editingSource?.let { source -> SourceEditorDialog(source, uiState.workspaceLoading,
        uiState.message?.takeIf { uiState.isError }, { editingSource = null }, { name, description, tags, enabled ->
            viewModel.updateSource(source, name, description, tags, enabled)
        }) }
    deletingSource?.let { source -> AlertDialog(onDismissRequest = { deletingSource = null },
        title = { Text("Удалить источник?") }, text = { Text(source.name) },
        confirmButton = { TextButton(onClick = { viewModel.deleteSource(source) }, enabled = !uiState.workspaceLoading) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deletingSource = null }, enabled = !uiState.workspaceLoading) { Text("Отмена") } }) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (uiState.sources.isEmpty() && !uiState.workspaceLoading) {
            item(key = "empty-sources", contentType = "empty") {
                EmptyWorkspace(text = "Источников Soll пока нет")
            }
        }
        items(uiState.sources, key = { it.id }) { source ->
            SourceCard(
                source = source,
                selected = source.id == uiState.selectedSourceId,
                onSelect = { viewModel.selectSource(source) },
                onCheck = { viewModel.checkSource(source) },
                onEdit = { editingSource = source }, onDelete = { deletingSource = source },
                busy = uiState.workspaceLoading,
            )
        }
    }
}

@Composable
private fun SourceMaterialsTab(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
    if (uiState.selectedSourceId == null) {
        EmptyWorkspace(text = "Сначала откройте источник на вкладке «Источники»")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (uiState.sourceItems.isEmpty() && !uiState.workspaceLoading) {
            item(key = "empty-source-items", contentType = "empty") {
                EmptyWorkspace(text = "Материалов пока нет")
            }
        }
        val sourceEnabled = uiState.sources
            .firstOrNull { it.id == uiState.selectedSourceId }
            ?.enabled == true && uiState.sourceDisabledReason.isBlank()
        items(uiState.sourceItems, key = { it.itemId }) { item ->
            SourceItemCard(
                item = item,
                sourceEnabled = sourceEnabled,
                isCreatingTask = uiState.sourceItemTaskId == item.itemId,
                onCreateTask = {
                    uiState.selectedSourceId?.let { sourceId ->
                        viewModel.createTaskFromSourceItem(sourceId, item)
                    }
                },
            )
        }
        if (uiState.sourceItemsHasMore) {
            item(key = "source-items-more", contentType = "source-items-more") {
                Button(
                    onClick = viewModel::loadMoreSourceItems,
                    enabled = !uiState.sourceItemsLoadingMore,
                ) {
                    if (uiState.sourceItemsLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Загрузить ещё")
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: SollMonitoredSource,
    selected: Boolean,
    onSelect: () -> Unit,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
) {
    var showActions by remember(source.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        source.target,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PassiveChip(
                    text = when {
                        !source.enabled -> "выкл"
                        selected -> "открыт"
                        else -> source.lastResult
                    },
                )
            }
            if (source.description.isNotBlank()) {
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassiveChip(text = source.sourceType)
                PassiveChip(text = "Материалов: ${source.itemsSeen}")
                if (source.newItemsLastCheck > 0) {
                    PassiveChip(text = "+${source.newItemsLastCheck}")
                }
                source.tags.forEach { tag -> PassiveChip(text = tag) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onSelect, enabled = !busy) { Text("Открыть") }
                Button(onClick = onCheck, enabled = source.enabled && !busy) { Text("Проверить") }
                Box {
                    IconButton(onClick = { showActions = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, contentDescription = "Управление источником") }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { showActions = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { showActions = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItemCard(
    item: SollSourceItem,
    sourceEnabled: Boolean,
    isCreatingTask: Boolean,
    onCreateTask: () -> Unit,
) {
    val imageUrl = item.linkPreview["image_url"]?.toString().orEmpty()
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (imageUrl.isNotBlank()) {
                RemoteLinkPreviewImage(url = imageUrl)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                item.summary.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                item.contentPreview.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    PassiveChip(text = item.lastStatus.sourceItemStatusLabel())
                    PassiveChip(text = item.actionability.sourceItemActionabilityLabel())
                    PassiveChip(text = item.usefulness)
                    item.evidenceLevel.takeIf { it.isNotBlank() }?.let { level ->
                        PassiveChip(text = "evidence: $level")
                    }
                    PassiveChip(text = item.deliveryStatus.sourceItemDeliveryLabel())
                    if (item.needsDeepDive) {
                        PassiveChip(text = "глубокий разбор")
                    }
                    item.linkPreview["site_name"]?.toString()?.takeIf { it.isNotBlank() }?.let { PassiveChip(text = it) }
                }
                item.visibleReason.takeIf { it.isNotBlank() }?.let { reason ->
                    Text(
                        reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.safeNextStep.takeIf { it.isNotBlank() && !item.isTerminal }?.let { nextStep ->
                    Text(
                        nextStep,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    item.auditRef.takeIf { it.isNotBlank() }?.let { PassiveChip(text = "audit: ${it.substringAfterLast('/')}" ) }
                    item.evidenceRef.takeIf { it.isNotBlank() }?.let { PassiveChip(text = "evidence ref: ${it.substringAfterLast('/')}" ) }
                    item.verificationArtifact.takeIf { it.isNotBlank() }?.let { PassiveChip(text = "evidence: ${it.substringAfterLast('/')}" ) }
                }
                when {
                    !sourceEnabled -> PassiveChip(text = "Источник отключен")
                    item.isTerminal -> PassiveChip(text = "Обработано: ${item.lastStatus.sourceItemStatusLabel()}")
                    !item.canCreateTask -> PassiveChip(text = "Требуется ручная проверка")
                    else -> Button(
                        onClick = onCreateTask,
                        enabled = !isCreatingTask,
                    ) {
                        if (isCreatingTask) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("В задачу")
                    }
                }
            }
        }
    }
}

private fun String.sourceItemStatusLabel(): String = when (trim().lowercase()) {
    "new" -> "новый"
    "changed" -> "обновлён"
    "task_created" -> "задача создана"
    "implemented", "done" -> "внедрён"
    "deferred", "source_processing_deferred", "source_review_deferred" -> "отложен"
    "blocked" -> "заблокирован"
    "duplicate" -> "дубликат"
    "rejected" -> "отклонён"
    "ignored", "suppressed" -> "пропущен"
    else -> ifBlank { "неизвестно" }
}

private fun String.sourceItemActionabilityLabel(): String = when (trim().lowercase()) {
    "implementation_ready" -> "готов к реализации"
    "discard" -> "не использовать"
    else -> "требует анализа"
}

internal fun String.sourceItemDeliveryLabel(): String = when (trim().lowercase()) {
    "notified" -> "уведомление создано"
    "delivered", "sent" -> "доставлено"
    "not_notified", "pending", "queued" -> "не доставлено"
    "failed" -> "ошибка доставки"
    else -> "доставка неизвестна"
}

@Composable
private fun EmptyWorkspace(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreateSourceDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить источник") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, target) },
                enabled = target.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Добавить")
            }
        },
        dismissButton = {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Отмена")
            }
        },
    )
}

@Composable
internal fun TaskDetailSection(task: SollTask) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DetailRow(label = "ID", value = task.id)
        if (task.sourceRef.isNotBlank()) {
            DetailRow(label = "Источник", value = task.sourceRef)
        }
        if (task.tags.isNotEmpty()) {
            DetailRow(label = "Теги", value = task.tags.joinToString(", "))
        }
        if (task.projectName?.isNotBlank() == true) {
            DetailRow(label = "Проект", value = task.projectName)
        }
        task.dueDate?.takeIf { it.isNotBlank() }?.let { dueDate ->
            DetailRow(label = "Дата", value = dueDate)
        }
        task.routingState.takeIf { it.isNotBlank() }?.let { routingState ->
            DetailRow(label = "Маршрут", value = routingState.routingStateLabel())
        }
        task.assignedNodeId?.takeIf { it.isNotBlank() }?.let { nodeId ->
            DetailRow(label = "Нода", value = nodeId)
        }
        if (task.requiredCapabilities.isNotEmpty()) {
            DetailRow(label = "Нужно", value = task.requiredCapabilities.joinToString(", "))
        }
        task.executionPhase.takeIf { it.isNotBlank() }?.let { DetailRow("Исполнение", it.executionPhaseLabel()) }
        if (task.executionAttempts > 0) DetailRow("Попытки", task.executionAttempts.toString())
        task.executionRunId.takeIf { it.isNotBlank() }?.let { DetailRow("Запуск", it) }
        task.commitSha.takeIf { it.isNotBlank() }?.let { DetailRow("Commit", it) }
        task.baseSha.takeIf { it.isNotBlank() }?.let { DetailRow("Основа", it) }
        task.rollbackSha.takeIf { it.isNotBlank() }?.let { DetailRow("Откат", it) }
        if (task.acceptanceCriteria.isNotEmpty()) DetailRow("Приёмка", task.acceptanceCriteria.joinToString("\n"))
        if (task.testPlan.isNotEmpty()) DetailRow("Проверки", task.testPlan.joinToString("\n"))
        task.truthDetailRows().forEach { row ->
            DetailRow(label = row.label, value = row.value)
        }
    }
}

internal data class TaskTruthDetailRow(
    val label: String,
    val value: String,
)

internal fun SollTask.truthDetailRows(): List<TaskTruthDetailRow> = buildList {
    completionResult.trim().takeIf { it.isNotBlank() }?.let { verdict ->
        add(TaskTruthDetailRow(label = "Вердикт", value = verdict.replace('_', ' ')))
    }
    completionKind.trim().takeIf { it.isNotBlank() }?.let { kind ->
        add(TaskTruthDetailRow(label = "Тип", value = kind.replace('_', ' ')))
    }
    (completionEvidence + outcomeArtifacts)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .takeIf(List<String>::isNotEmpty)
        ?.let { evidence ->
            add(TaskTruthDetailRow(label = "Доказательства", value = evidence.joinToString("\n")))
        }
    shortHoldReason()?.let { reason ->
        val label = when (status.trim().lowercase()) {
            "blocked" -> "Причина блока"
            "deferred" -> "Причина отсрочки"
            else -> "Причина ожидания"
        }
        add(TaskTruthDetailRow(label = label, value = reason))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskActions(
    status: String,
    taskId: String,
    isActionRunning: Boolean,
    onMoveToToday: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onDefer: () -> Unit,
    onReject: () -> Unit,
) {
    val visibility = taskActionVisibility(status = status, taskId = taskId)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isActionRunning) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        if (!visibility.hasTaskId) {
            PassiveChip(text = "Нет ID")
        } else {
            if (visibility.canMoveToToday) {
                OutlinedButton(onClick = onMoveToToday, enabled = !isActionRunning) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сегодня")
                }
            }

            if (visibility.canStart) {
                Button(onClick = onStart, enabled = !isActionRunning) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Начать")
                }
            }

            if (visibility.canComplete) {
                Button(onClick = onDone, enabled = !isActionRunning) {
                    Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Готово")
                }
            }

            if (visibility.canDefer) {
                OutlinedButton(onClick = onDefer, enabled = !isActionRunning) {
                    Text("Отложить")
                }
            }

            if (visibility.canReject) {
                TextButton(onClick = onReject, enabled = !isActionRunning) {
                    Text("Отклонить")
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun EmptyTasks(tab: TaskTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = tab.emptyText(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun TaskTab.emptyText(): String =
    when (this) {
        TaskTab.ALL -> "Задач нет"
        TaskTab.TODAY -> "На сегодня задач нет"
        TaskTab.BLOCKED -> "Заблокированных задач нет"
        TaskTab.INBOX -> "Входящих задач нет"
        TaskTab.STALE -> "Зависших задач нет"
        TaskTab.DEFERRED -> "Отложенных задач нет"
        TaskTab.IDEAS -> "Идей пока нет"
        TaskTab.DONE -> "Недавних закрытых задач нет"
    }

internal fun String.statusLabel(): String =
    when (this) {
        "inbox" -> "входящая"
        "today" -> "сегодня"
        "in_progress" -> "в работе"
        "done" -> "готово"
        "blocked" -> "заблокирована"
        "deferred" -> "отложена"
        "stale" -> "зависла"
        "rejected" -> "отклонена"
        else -> this
    }

private fun String.routingStateLabel(): String =
    when (trim()) {
        "waiting_for_android_adb_node" -> "ждет Android/ADB-ноду"
        "delegated_active" -> "делегировано активной ноде"
        "queued" -> "в очереди"
        "applied" -> "применено"
        "failed" -> "ошибка маршрута"
        else -> trim().replace('_', ' ')
    }

private fun String.executionPhaseLabel(): String =
    when (trim()) {
        "queued" -> "в очереди"
        "planning" -> "планирование"
        "ready" -> "готово к запуску"
        "leased" -> "исполнитель назначен"
        "running" -> "Codex работает"
        "validating" -> "проверка"
        "committed" -> "commit создан"
        "integrating" -> "интеграция"
        "succeeded" -> "готово"
        "retry_wait" -> "повтор"
        "needs_user" -> "нужно решение"
        "failed" -> "ошибка"
        "cancelled" -> "отменено"
        "reverted" -> "откачено"
        else -> trim().replace('_', ' ')
    }

internal fun SollTask.shortHoldReason(maxLength: Int = TASK_HOLD_REASON_MAX_LENGTH): String? {
    if (!shouldExplainHold()) return null

    val rawReason = sequenceOf(executionReason, executionState, routingState)
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        .orEmpty()
    val fallback = when {
        routingState.trim() == "waiting_for_android_adb_node" -> "Ожидает подключение Android-устройства."
        routingState.trim() in setOf("waiting_for_non_local_worker_node", "waiting_for_capable_node") ->
            "Ожидает доступный исполнитель."
        executionPhase.trim() == "needs_user" -> "Нужно решение пользователя."
        executionPhase.trim() == "failed" -> "Последний запуск завершился ошибкой."
        executionPhase.trim() == "retry_wait" -> "Ожидает повторного запуска."
        status.trim() == "deferred" -> "Отложена вручную; причина не указана."
        status.trim() == "stale" -> "Нет обновлений дольше установленного срока."
        else -> "Ожидает устранения блокера."
    }
    val normalized = rawReason
        .takeIf(String::isNotBlank)
        ?.toShortReasonText()
        .orEmpty()
        .ifBlank { fallback }

    if (normalized.length <= maxLength) return normalized
    return normalized.take((maxLength - 1).coerceAtLeast(1)).trimEnd(' ', '.', ',', ':', ';', '-') + "…"
}

private fun SollTask.shouldExplainHold(): Boolean {
    val normalizedStatus = status.trim().lowercase()
    val normalizedPhase = executionPhase.trim().lowercase()
    val normalizedState = executionState.trim().lowercase()
    val normalizedRouting = routingState.trim().lowercase()
    return normalizedStatus in TASK_HOLD_STATUSES ||
        normalizedPhase in TASK_HOLD_EXECUTION_PHASES ||
        normalizedState in TASK_HOLD_EXECUTION_PHASES ||
        normalizedRouting.startsWith("waiting_for_")
}

private fun SollTask.isExecutionBlocked(): Boolean =
    status.trim().lowercase() == "blocked" ||
        executionPhase.trim().lowercase() in setOf("needs_user", "failed")

private fun String.toShortReasonText(): String {
    val compact = lineSequence()
        .joinToString(" ") { it.trim() }
        .replace(Regex("\\s+"), " ")
        .trim()
    val withoutPrefix = TASK_HOLD_REASON_PREFIX.replace(compact, "").trim()
    TASK_SCOPE_BLOCK_REASON.matchEntire(withoutPrefix)?.let { match ->
        return "Проект «${match.groupValues[1]}» не разрешен для автономного выполнения."
    }
    return when {
        withoutPrefix.startsWith("waiting_for_android_adb_node", ignoreCase = true) ->
            "Ожидает подключение Android-устройства."
        withoutPrefix.startsWith("waiting_for_non_local_worker_node", ignoreCase = true) ||
            withoutPrefix.startsWith("waiting_for_capable_node", ignoreCase = true) ->
            "Ожидает доступный исполнитель."
        withoutPrefix.equals("needs_user", ignoreCase = true) ||
            withoutPrefix.equals("waiting_approval", ignoreCase = true) ->
            "Нужно решение пользователя."
        withoutPrefix.startsWith("approval_expired", ignoreCase = true) ->
            "Срок подтверждения пользователя истек."
        withoutPrefix.equals("failed", ignoreCase = true) ->
            "Последний запуск завершился ошибкой."
        withoutPrefix.equals("retry_wait", ignoreCase = true) ->
            "Ожидает повторного запуска."
        withoutPrefix.equals("source_review_deferred", ignoreCase = true) ->
            "Отложено до проверки источника."
        withoutPrefix.equals("source_verification_deferred", ignoreCase = true) ->
            "Отложено до подтверждения данных источника."
        withoutPrefix.equals("source_triage_deferred", ignoreCase = true) ->
            "Отложено до разбора источника."
        withoutPrefix.equals("source_processing_deferred", ignoreCase = true) ->
            "Отложено до обработки источника."
        withoutPrefix.startsWith("deferred_review_only", ignoreCase = true) ->
            "Нужна повторная проверка условий и доступа."
        withoutPrefix.equals("blocked_external_android_token_or_device", ignoreCase = true) ->
            "Ожидает Android-токен или подключенное устройство."
        withoutPrefix.equals("live_telegram_e2e_deferred_server_smoke_passed", ignoreCase = true) ->
            "Проверка Telegram отложена: серверный smoke-тест пройден."
        withoutPrefix.equals("unity_playable_smoke_blocked_by_license", ignoreCase = true) ->
            "Playable smoke-тест Unity ждет активную лицензию."
        withoutPrefix.equals("cancelled", ignoreCase = true) ->
            "Исполнение отменено."
        else -> withoutPrefix.ifBlank { compact }
    }
}

private fun List<String>.requiredCapabilitiesLabel(): String {
    val visible = take(2)
    val suffix = (size - visible.size).takeIf { it > 0 }?.let { " +$it" }.orEmpty()
    return visible.joinToString(", ") + suffix
}

private const val TASK_HOLD_REASON_MAX_LENGTH = 140
private val TASK_HOLD_STATUSES = setOf("blocked", "deferred", "stale")
private val TASK_HOLD_EXECUTION_PHASES = setOf("needs_user", "failed", "retry_wait")
private val TASK_HOLD_REASON_PREFIX = Regex(
    "^(?:(?:external[_ ]blocked|blocked|deferred|stale|needs[_ ]user|failed|retry[_ ]wait)\\s*[:\\-]\\s*)" +
        "(?:Задача заблокирована\\.;\\s*)?",
    RegexOption.IGNORE_CASE,
)
private val TASK_SCOPE_BLOCK_REASON = Regex(
    "^Scope '([^']+)' не входит в autonomous allowlist.*$",
    RegexOption.IGNORE_CASE,
)

internal data class TaskActionVisibility(
    val hasTaskId: Boolean,
    val canMoveToToday: Boolean,
    val canStart: Boolean,
    val canComplete: Boolean,
    val canDefer: Boolean,
    val canReject: Boolean,
)

internal fun taskActionVisibility(status: String, taskId: String): TaskActionVisibility {
    val normalizedStatus = status.trim().lowercase()
    val hasTaskId = taskId.trim().isNotBlank()
    if (!hasTaskId) {
        return TaskActionVisibility(
            hasTaskId = false,
            canMoveToToday = false,
            canStart = false,
            canComplete = false,
            canDefer = false,
            canReject = false,
        )
    }
    return TaskActionVisibility(
        hasTaskId = true,
        canMoveToToday = normalizedStatus !in TASK_STATUS_HIDE_MOVE_TO_TODAY,
        canStart = normalizedStatus !in TASK_STATUS_HIDE_START,
        canComplete = normalizedStatus !in TASK_STATUS_HIDE_DONE_OR_REJECT,
        canDefer = normalizedStatus !in TASK_STATUS_HIDE_DEFER,
        canReject = normalizedStatus !in TASK_STATUS_HIDE_DONE_OR_REJECT,
    )
}

internal fun SollTask.taskListKey(): String =
    id.trim().ifBlank {
        listOf(title, sourceRef, status)
            .joinToString(":")
            .ifBlank { "task-without-id" }
    }

private val TASK_STATUS_HIDE_MOVE_TO_TODAY = setOf("today", "in_progress", "done", "rejected")
private val TASK_STATUS_HIDE_START = setOf("in_progress", "done", "rejected")
private val TASK_STATUS_HIDE_DONE_OR_REJECT = setOf("done", "rejected")
private val TASK_STATUS_HIDE_DEFER = setOf("deferred", "done", "rejected")
