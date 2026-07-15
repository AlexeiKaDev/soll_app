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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
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
    title: String = "Задачи Soll",
    initialMode: TaskWorkspaceMode = TaskWorkspaceMode.TASKS,
    visibleModes: List<TaskWorkspaceMode> = listOf(
        TaskWorkspaceMode.TASKS,
        TaskWorkspaceMode.INSIGHTS,
        TaskWorkspaceMode.ROADMAP,
        TaskWorkspaceMode.SOURCES,
    ),
    onBack: (() -> Unit)? = null,
    viewModel: TaskBoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }
    var evidenceTask by remember { mutableStateOf<SollTask?>(null) }
    var editingTask by remember { mutableStateOf<SollTask?>(null) }
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
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    editingTask?.let { task ->
        TaskEditDialog(
            task = task,
            isSaving = uiState.actionTaskId == task.id,
            onDismiss = { editingTask = null },
            onSave = { title, description ->
                viewModel.updateTask(task, title, description)
                editingTask = null
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

            TaskSummary(uiState)

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
                            TaskBoardFilters(
                                searchQuery = uiState.searchQuery,
                                selectedPriority = uiState.selectedPriority,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onPriorityChange = viewModel::selectPriority,
                            )

                            ScrollableTabRow(selectedTabIndex = uiState.selectedTab.ordinal, edgePadding = 12.dp) {
                                TaskTab.entries.forEach { tab ->
                                    Tab(
                                        selected = uiState.selectedTab == tab,
                                        onClick = { viewModel.selectTab(tab) },
                                        text = { Text(tab.title(uiState)) },
                                    )
                                }
                            }

                            val tasks = uiState.visibleTasks
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                if (tasks.isEmpty() && !uiState.isLoading) {
                                    EmptyTasks(tab = uiState.selectedTab)
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(tasks, key = { it.taskListKey() }, contentType = { "task" }) { task ->
                                            TaskCard(
                                                task = task,
                                                expanded = expandedTaskId == task.id,
                                                isActionRunning = uiState.actionTaskId == task.id,
                                                isEvidenceRunning = uiState.evidenceTaskId == task.id,
                                                hasPendingEvidence = task.id in uiState.pendingEvidenceTaskIds,
                                                hasPendingTaskAction = task.id in uiState.pendingTaskActionIds,
                                                onToggleDetails = {
                                                    expandedTaskId = if (expandedTaskId == task.id) null else task.id
                                                },
                                                onAttachEvidence = {
                                                    evidenceTask = task
                                                    evidencePicker.launch("*/*")
                                                },
                                                onEdit = { editingTask = task },
                                                onMoveToToday = { viewModel.moveToToday(task) },
                                                onStart = { viewModel.startTask(task) },
                                                onDone = { viewModel.completeTask(task) },
                                                onDefer = { viewModel.deferTask(task) },
                                                onReject = { viewModel.rejectTask(task) },
                                            )
                                        }
                                        if (uiState.canLoadMoreTasks) {
                                            item(key = "load-more-tasks", contentType = "load-more") {
                                                LoadMoreTasksRow(
                                                    displayedTotal = uiState.selectedDisplayedTaskCount,
                                                    total = uiState.selectedTaskCount,
                                                    currentLimit = uiState.taskBoardLimitPerSection
                                                        ?: uiState.requestedTaskBoardLimitPerSection,
                                                    onLoadMore = viewModel::loadMoreTasks,
                                                )
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
private fun LoadMoreTasksRow(
    displayedTotal: Int,
    total: Int,
    currentLimit: Int,
    onLoadMore: () -> Unit,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Показано $displayedTotal из $total",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Лимит секции: $currentLimit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onLoadMore) {
                Text("Показать больше")
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
private fun TaskBoardFilters(
    searchQuery: String,
    selectedPriority: TaskPriorityFilter,
    onSearchQueryChange: (String) -> Unit,
    onPriorityChange: (TaskPriorityFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Поиск задач") },
            shape = RoundedCornerShape(12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskPriorityFilter.entries.forEach { filter ->
                val badgeStyle = priorityBadgeStyle(filter.label)
                FilterChip(
                    selected = selectedPriority == filter,
                    onClick = { onPriorityChange(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = badgeStyle.containerColor,
                        labelColor = badgeStyle.contentColor,
                        selectedContainerColor = badgeStyle.contentColor,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TaskSummary(uiState: TaskBoardUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PassiveChip(
            text = "Открытых: ${uiState.openCount}",
            icon = Icons.Default.Schedule,
        )
        PassiveChip(
            text = "Сегодня: ${uiState.taskCounts?.today ?: uiState.today.size}",
            icon = Icons.Default.PlayArrow,
        )
        val blockedCount = uiState.taskCounts?.blocked ?: uiState.blocked.size
        if (blockedCount > 0) {
            PassiveChip(text = "Блок: $blockedCount", icon = Icons.Default.Warning)
        }
        if (uiState.hasLimitedSections) {
            PassiveChip(
                text = "Показано: ${uiState.displayedTotalCount}/${uiState.totalCount}",
                icon = Icons.Default.FilterList,
            )
        }
        if (uiState.isShowingCache) {
            PassiveChip(text = "Кэш", icon = Icons.Default.Warning)
        }
        if (uiState.pendingEvidenceCount > 0) {
            PassiveChip(text = "В очереди: ${uiState.pendingEvidenceCount}", icon = Icons.Default.AttachFile)
        }
        if (uiState.pendingTaskActionCount > 0) {
            PassiveChip(text = "Действия: ${uiState.pendingTaskActionCount}", icon = Icons.Default.Schedule)
        }
        if (uiState.routedOpenTaskCount > 0) {
            PassiveChip(text = "Маршрут: ${uiState.routedOpenTaskCount}", icon = Icons.Default.FilterList)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PassiveChip(text = "Текущий: ${roadmap.currentStage}")
                roadmap.updated?.let { PassiveChip(text = it) }
            }
        }
        if (roadmap.readiness.isNotEmpty()) {
            item(key = "roadmap-readiness-title", contentType = "roadmap-readiness-title") {
                Text("Готовность", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(
                items = roadmap.readiness,
                key = { item -> "readiness:${item.area}" },
                contentType = { "roadmap-readiness" },
            ) { item ->
                RoadmapReadinessCard(item = item)
            }
        }
        roadmap.stages.forEach { stage ->
            item(key = "stage:${stage.id}", contentType = "roadmap-stage") {
                RoadmapStageHeader(
                    stage = stage,
                )
            }
            items(
                items = stage.lines,
                key = { line -> "line:${stage.id}:${line.line}" },
                contentType = { "roadmap-line" },
            ) { line ->
                RoadmapLineCard(
                    item = line,
                    isCreatingTask = uiState.roadmapLineTaskKey == roadmapLineTaskKey(stage.id, line.line),
                    onCreateTask = { viewModel.createTaskFromRoadmapLine(stage.id, line) },
                )
            }
        }
    }
}

@Composable
private fun RoadmapReadinessCard(item: SollRoadmapReadiness) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.area, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                PassiveChip(text = "${item.percent.coerceIn(0, 100)}%")
            }
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
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stage.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                PassiveChip(text = stage.status)
            }
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
private fun RoadmapLineRow(
    item: SollRoadmapLine,
    isCreatingTask: Boolean,
    onCreateTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PassiveChip(text = item.line)
        Text(
            text = item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(horizontalAlignment = Alignment.End) {
            if (isCreatingTask) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onCreateTask) { Text("В задачу") }
            }
        }
    }
}

@Composable
private fun SourcesMode(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
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
            )
        }
        if (uiState.sourceItems.isNotEmpty()) {
            item(key = "source-items-title") {
                Text("Последние материалы Soll", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(uiState.sourceItems, key = { it.itemId }) { item ->
                SourceItemCard(
                    item = item,
                    isCreatingTask = uiState.sourceItemTaskId == item.itemId,
                    onCreateTask = {
                        uiState.selectedSourceId?.let { sourceId ->
                            viewModel.createTaskFromSourceItem(sourceId, item)
                        }
                    },
                )
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
) {
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
                        selected -> "открыт"
                        !source.enabled -> "выкл"
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
                PassiveChip(text = "seen: ${source.itemsSeen}")
                if (source.newItemsLastCheck > 0) {
                    PassiveChip(text = "+${source.newItemsLastCheck}")
                }
                source.tags.forEach { tag -> PassiveChip(text = tag) }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onSelect) { Text("Открыть") }
                Button(onClick = onCheck, enabled = source.enabled) { Text("Проверить") }
            }
        }
    }
}

@Composable
private fun SourceItemCard(
    item: SollSourceItem,
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
                    PassiveChip(text = item.usefulness)
                    item.linkPreview["site_name"]?.toString()?.takeIf { it.isNotBlank() }?.let { PassiveChip(text = it) }
                }
                Button(
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
private fun TaskCard(
    task: SollTask,
    expanded: Boolean,
    isActionRunning: Boolean,
    isEvidenceRunning: Boolean,
    hasPendingEvidence: Boolean,
    hasPendingTaskAction: Boolean,
    onEdit: () -> Unit,
    onToggleDetails: () -> Unit,
    onAttachEvidence: () -> Unit,
    onMoveToToday: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onDefer: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                val priorityBadge = priorityBadgeStyle(task.priority)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (task.projectName?.isNotBlank() == true) {
                        Text(
                            text = task.projectName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, enabled = !isActionRunning) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать задачу")
                    }
                    PassiveChip(
                        text = priorityBadge.label,
                        containerColor = priorityBadge.containerColor,
                        contentColor = priorityBadge.contentColor,
                    )
                }
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else TASK_DESCRIPTION_COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                PassiveChip(text = task.status.statusLabel())
                task.dueDate?.takeIf { it.isNotBlank() }?.let { dueDate ->
                    PassiveChip(text = "Дата: $dueDate")
                }
                if (hasPendingEvidence) {
                    PassiveChip(text = "Вложение в очереди")
                }
                if (hasPendingTaskAction) {
                    PassiveChip(text = "Действие в очереди")
                }
                task.executionPhase.takeIf { it.isNotBlank() }?.let { phase ->
                    PassiveChip(text = "Исполнение: ${phase.executionPhaseLabel()}")
                }
                if (task.executionAttempts > 0) {
                    PassiveChip(text = "Попытка: ${task.executionAttempts}")
                }
                task.commitSha.takeIf { it.isNotBlank() }?.let { sha ->
                    PassiveChip(text = "Commit: ${sha.take(8)}")
                }
            }

            task.shortHoldReason()?.let { reason ->
                Text(
                    text = "Почему: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isExecutionBlocked()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (task.hasRoutingContext()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    task.routingState.takeIf { it.isNotBlank() }?.let { routingState ->
                        PassiveChip(text = "Маршрут: ${routingState.routingStateLabel()}")
                    }
                    task.assignedNodeId?.takeIf { it.isNotBlank() }?.let { nodeId ->
                        PassiveChip(text = "Нода: $nodeId")
                    }
                    if (task.requiredCapabilities.isNotEmpty()) {
                        PassiveChip(text = "Нужно: ${task.requiredCapabilities.requiredCapabilitiesLabel()}")
                    }
                }
            }

            if (task.sourceRef.isNotBlank()) {
                Text(
                    text = "Источник: ${task.sourceRef}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TaskActions(
                status = task.status,
                taskId = task.id,
                isActionRunning = isActionRunning || hasPendingTaskAction,
                onMoveToToday = onMoveToToday,
                onStart = onStart,
                onDone = onDone,
                onDefer = onDefer,
                onReject = onReject,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleDetails) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (expanded) "Скрыть детали" else "Детали")
                }

                OutlinedButton(
                    onClick = onAttachEvidence,
                    enabled = !isEvidenceRunning,
                ) {
                    if (isEvidenceRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Вложить")
                }
            }

            if (expanded) {
                TaskDetailSection(task = task)
            }
        }
    }
}

@Composable
private fun TaskEditDialog(
    task: SollTask,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember(task.id, task.title) { mutableStateOf(task.title) }
    var description by remember(task.id, task.description) { mutableStateOf(task.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать задачу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Описание") },
                    minLines = 3,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, description) },
                enabled = title.isNotBlank() && !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Сохранить")
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
private fun TaskDetailSection(task: SollTask) {
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

@Composable
private fun TaskActions(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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

private fun TaskTab.title(uiState: TaskBoardUiState): String =
    when (this) {
        TaskTab.ALL -> "Все ${uiState.openCount}"
        TaskTab.TODAY -> "Сегодня ${uiState.taskCounts?.today ?: uiState.today.size}"
        TaskTab.BLOCKED -> "Блок ${uiState.taskCounts?.blocked ?: uiState.blocked.size}"
        TaskTab.INBOX -> "Вход ${uiState.taskCounts?.inbox ?: uiState.inbox.size}"
        TaskTab.STALE -> "Зависли ${uiState.taskCounts?.stale ?: uiState.stale.size}"
        TaskTab.DEFERRED -> "Отложено ${uiState.taskCounts?.deferred ?: uiState.deferred.size}"
        TaskTab.IDEAS -> "Идеи ${uiState.ideaCount}"
        TaskTab.DONE -> "Готово ${uiState.doneCount}"
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

private data class PriorityBadgeStyle(
    val label: String,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun priorityBadgeStyle(priority: String): PriorityBadgeStyle {
    val label = priorityLabel(priority)
    val contentColor = when (label) {
        "A" -> Color(0xFF247A52)
        "B" -> MaterialTheme.colorScheme.primary
        "C" -> MaterialTheme.colorScheme.tertiary
        "D" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    return PriorityBadgeStyle(
        label = label,
        containerColor = contentColor.copy(alpha = 0.16f),
        contentColor = contentColor,
    )
}

private fun priorityLabel(priority: String): String =
    when (priority.trim().uppercase()) {
        "", "B", "P2" -> "B"
        "A", "P1" -> "A"
        "C", "P3" -> "C"
        "D", "P4" -> "D"
        else -> priority.trim().uppercase()
    }

private fun String.statusLabel(): String =
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

    val rawReason = sequenceOf(executionState, executionReason, routingState)
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

private const val TASK_DESCRIPTION_COLLAPSED_LINES = 4
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
