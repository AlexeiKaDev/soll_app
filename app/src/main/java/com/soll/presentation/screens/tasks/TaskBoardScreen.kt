package com.soll.presentation.screens.tasks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.soll.SollTask
import com.soll.ui.components.PassiveChip

private enum class TaskTab {
    TODAY,
    INBOX,
    STALE,
    DONE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskBoardScreen(
    viewModel: TaskBoardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(TaskTab.TODAY) }
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

            TaskSummary(uiState)

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                TaskTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title(uiState)) },
                    )
                }
            }

            val tasks = when (selectedTab) {
                TaskTab.TODAY -> uiState.today
                TaskTab.INBOX -> uiState.inbox
                TaskTab.STALE -> uiState.stale
                TaskTab.DONE -> uiState.doneRecent
            }

            uiState.message
                ?.takeIf { uiState.isError }
                ?.let { ErrorMessage(it) }

            if (tasks.isEmpty() && !uiState.isLoading) {
                EmptyTasks(tab = selectedTab)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
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
                }
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
            text = "Сегодня: ${uiState.today.size}",
            icon = Icons.Default.PlayArrow,
        )
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (task.projectName?.isNotBlank() == true) {
                        Text(
                            text = task.projectName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                PassiveChip(text = task.priority.ifBlank { "P2" })
            }

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        if (status !in setOf("today", "in_progress", "done", "rejected")) {
            OutlinedButton(onClick = onMoveToToday, enabled = !isActionRunning) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Сегодня")
            }
        }

        if (status !in setOf("in_progress", "done", "rejected")) {
            Button(onClick = onStart, enabled = !isActionRunning) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Начать")
            }
        }

        if (status !in setOf("done", "rejected")) {
            Button(onClick = onDone, enabled = !isActionRunning) {
                Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Готово")
            }
        }

        if (status !in setOf("deferred", "done", "rejected")) {
            OutlinedButton(onClick = onDefer, enabled = !isActionRunning) {
                Text("Отложить")
            }
        }

        if (status !in setOf("done", "rejected")) {
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
        TaskTab.TODAY -> "Сегодня ${uiState.today.size}"
        TaskTab.INBOX -> "Вход ${uiState.inbox.size}"
        TaskTab.STALE -> "Зависли ${uiState.stale.size}"
        TaskTab.DONE -> "Готово ${uiState.doneRecent.size}"
    }

private fun TaskTab.emptyText(): String =
    when (this) {
        TaskTab.TODAY -> "На сегодня задач нет"
        TaskTab.INBOX -> "Входящих задач нет"
        TaskTab.STALE -> "Зависших задач нет"
        TaskTab.DONE -> "Недавних закрытых задач нет"
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
