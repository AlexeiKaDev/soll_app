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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
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
    viewModel: TaskBoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedTaskId by remember { mutableStateOf<String?>(null) }
    var evidenceTask by remember { mutableStateOf<SollTask?>(null) }
    val evidencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val task = evidenceTask
        evidenceTask = null
        if (task != null && uri != null) {
            viewModel.attachEvidence(task, uri)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Задачи Soll") },
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
                                        items(tasks, key = { it.id }, contentType = { "task" }) { task ->
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
        TaskWorkspaceMode.entries.forEach { mode ->
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
    val initialStageId = roadmap.stages.firstOrNull { it.id == roadmap.currentStage }?.id
        ?: roadmap.stages.firstOrNull()?.id
    var editingStageId by remember(roadmap.currentStage, roadmap.stages) { mutableStateOf(initialStageId) }
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
                    expanded = editingStageId == stage.id,
                    onToggle = {
                        editingStageId = if (editingStageId == stage.id) null else stage.id
                    },
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
                    onUpdate = { newLine, text ->
                        viewModel.updateRoadmapLine(stage.id, line.line, newLine, text)
                    },
                    onDelete = { viewModel.deleteRoadmapLine(stage.id, line.line) },
                )
            }
            if (stage.id == editingStageId) {
                item(key = "editor:${stage.id}", contentType = "roadmap-editor") {
                    RoadmapStageEditor(
                        stage = stage,
                        onAdd = { line, text -> viewModel.addRoadmapLine(stage.id, line, text) },
                    )
                }
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
    expanded: Boolean,
    onToggle: () -> Unit,
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
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Скрыть форму" else "Добавить строку",
                )
            }
        }
    }
}

@Composable
private fun RoadmapStageEditor(
    stage: SollRoadmapStage,
    onAdd: (String, String) -> Unit,
) {
    var line by remember(stage.id) { mutableStateOf("") }
    var text by remember(stage.id) { mutableStateOf("") }
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Новая строка: ${stage.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = line,
                onValueChange = { line = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Линия") },
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Текст") },
            )
            Button(
                onClick = {
                    onAdd(line, text)
                    line = ""
                    text = ""
                },
                enabled = line.isNotBlank() && text.isNotBlank(),
            ) {
                Text("Добавить")
            }
        }
    }
}

@Composable
private fun RoadmapLineCard(
    item: SollRoadmapLine,
    isCreatingTask: Boolean,
    onCreateTask: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(item.line, item.text) { mutableStateOf(false) }
    var line by remember(item.line) { mutableStateOf(item.line) }
    var text by remember(item.text) { mutableStateOf(item.text) }
    Card(shape = RoundedCornerShape(8.dp)) {
        if (editing) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Линия") },
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Текст") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onUpdate(line, text)
                            editing = false
                        },
                        enabled = line.isNotBlank() && text.isNotBlank(),
                    ) {
                        Text("Сохранить")
                    }
                    TextButton(
                        onClick = {
                            line = item.line
                            text = item.text
                            editing = false
                        },
                    ) {
                        Text("Отмена")
                    }
                }
            }
        } else {
            RoadmapLineRow(
                item = item,
                isCreatingTask = isCreatingTask,
                onCreateTask = onCreateTask,
                onEdit = { editing = true },
                onDelete = onDelete,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun RoadmapLineRow(
    item: SollRoadmapLine,
    isCreatingTask: Boolean,
    onCreateTask: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
            TextButton(onClick = onEdit) { Text("Править") }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
private fun SourcesMode(
    uiState: TaskBoardUiState,
    viewModel: TaskBoardViewModel,
) {
    var sourceName by remember { mutableStateOf("") }
    var sourceTarget by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(SourceTypeOption.WEB) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "source-add") {
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Новый источник", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourceTypeOption.entries.forEach { option ->
                            FilterChip(
                                selected = sourceType == option,
                                onClick = { sourceType = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = sourceName,
                        onValueChange = { sourceName = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Название") },
                    )
                    OutlinedTextField(
                        value = sourceTarget,
                        onValueChange = { sourceTarget = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("URL") },
                    )
                    Button(
                        onClick = {
                            viewModel.createSource(sourceName, sourceTarget, sourceType.apiValue)
                            sourceName = ""
                            sourceTarget = ""
                            sourceType = SourceTypeOption.WEB
                        },
                        enabled = sourceTarget.isNotBlank(),
                    ) {
                        Text("Добавить")
                    }
                }
            }
        }
        items(uiState.sources, key = { it.id }) { source ->
            SourceCard(
                source = source,
                selected = source.id == uiState.selectedSourceId,
                onSelect = { viewModel.selectSource(source) },
                onCheck = { viewModel.checkSource(source) },
                onUpdate = { name, description, tags, enabled ->
                    viewModel.updateSource(source, name, description, tags, enabled)
                },
                onDelete = { viewModel.deleteSource(source) },
            )
        }
        if (uiState.sourceItems.isNotEmpty()) {
            item(key = "source-items-title") {
                Text("Последние материалы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

private enum class SourceTypeOption(val label: String, val apiValue: String) {
    WEB("Web", "web"),
    RSS("RSS", "rss"),
    TELEGRAM("Telegram", "telegram_chat"),
}

@Composable
private fun SourceCard(
    source: SollMonitoredSource,
    selected: Boolean,
    onSelect: () -> Unit,
    onCheck: () -> Unit,
    onUpdate: (String, String, String, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(source.id) { mutableStateOf(false) }
    var name by remember(source.id, source.name) { mutableStateOf(source.name) }
    var description by remember(source.id, source.description) { mutableStateOf(source.description) }
    var tagsText by remember(source.id, source.tags) { mutableStateOf(source.tags.joinToString(", ")) }
    var enabled by remember(source.id, source.enabled) { mutableStateOf(source.enabled) }
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                Text(
                    text = source.target,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Название") },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text("Описание") },
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Теги через запятую") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Активен", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onUpdate(name, description, tagsText, enabled)
                            editing = false
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("Сохранить")
                    }
                    TextButton(
                        onClick = {
                            name = source.name
                            description = source.description
                            tagsText = source.tags.joinToString(", ")
                            enabled = source.enabled
                            editing = false
                        },
                    ) {
                        Text("Отмена")
                    }
                }
            } else {
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
                    OutlinedButton(onClick = { editing = true }) { Text("Править") }
                    TextButton(onClick = onDelete) { Text("Удалить") }
                }
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
                PassiveChip(
                    text = priorityBadge.label,
                    containerColor = priorityBadge.containerColor,
                    contentColor = priorityBadge.contentColor,
                )
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
    isActionRunning: Boolean,
    onMoveToToday: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onDefer: () -> Unit,
    onReject: () -> Unit,
) {
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

        if (status !in TASK_STATUS_HIDE_MOVE_TO_TODAY) {
            OutlinedButton(onClick = onMoveToToday, enabled = !isActionRunning) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Сегодня")
            }
        }

        if (status !in TASK_STATUS_HIDE_START) {
            Button(onClick = onStart, enabled = !isActionRunning) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Начать")
            }
        }

        if (status !in TASK_STATUS_HIDE_DONE_OR_REJECT) {
            Button(onClick = onDone, enabled = !isActionRunning) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Готово")
            }
        }

        if (status !in TASK_STATUS_HIDE_DEFER) {
            OutlinedButton(onClick = onDefer, enabled = !isActionRunning) {
                Text("Отложить")
            }
        }

        if (status !in TASK_STATUS_HIDE_DONE_OR_REJECT) {
            TextButton(onClick = onReject, enabled = !isActionRunning) {
                Text("Отклонить")
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

private const val TASK_DESCRIPTION_COLLAPSED_LINES = 4
private val TASK_STATUS_HIDE_MOVE_TO_TODAY = setOf("today", "in_progress", "done", "rejected")
private val TASK_STATUS_HIDE_START = setOf("in_progress", "done", "rejected")
private val TASK_STATUS_HIDE_DONE_OR_REJECT = setOf("done", "rejected")
private val TASK_STATUS_HIDE_DEFER = setOf("deferred", "done", "rejected")
