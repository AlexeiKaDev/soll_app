package com.soll.presentation.screens.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.local.entity.MessageLogEntity
import com.soll.domain.assistant.memory.AssistantMemory
import com.soll.domain.assistant.memory.AssistantMemoryCategory
import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationStatus
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobStatus
import com.soll.ui.components.PassiveChip
import com.soll.ui.theme.StatusError
import com.soll.ui.theme.StatusRunning
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    initialTab: Int? = null,
    onInitialTabConsumed: () -> Unit = {},
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showClearMemoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) {
        val tab = initialTab ?: return@LaunchedEffect
        viewModel.selectTab(tab)
        onInitialTabConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Логи",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = { showClearDialog = true }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить логи")
            }
        }

        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTab,
            edgePadding = 0.dp,
        ) {
            Tab(
                selected = uiState.selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text("Сообщ. (${uiState.messageLogs.size})") }
            )
            Tab(
                selected = uiState.selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text("Ком. (${uiState.commandLogs.size})") }
            )
            Tab(
                selected = uiState.selectedTab == 2,
                onClick = { viewModel.selectTab(2) },
                text = { Text("Задачи (${uiState.toolJobs.size})") }
            )
            Tab(
                selected = uiState.selectedTab == 3,
                onClick = { viewModel.selectTab(3) },
                text = { Text("Увед. (${uiState.unreadNotifications})") }
            )
            Tab(
                selected = uiState.selectedTab == 4,
                onClick = { viewModel.selectTab(4) },
                text = { Text("Память (${uiState.memories.size})") }
            )
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (uiState.selectedTab) {
                0 -> MessageLogsList(uiState.messageLogs)
                1 -> CommandLogsList(uiState.commandLogs)
                2 -> ToolJobsList(
                    uiState = uiState,
                    onCancelJob = viewModel::cancelJob,
                )
                3 -> NotificationsList(
                    uiState = uiState,
                    onMarkRead = viewModel::markNotificationRead,
                    onMarkAllRead = viewModel::markAllNotificationsRead,
                )
                4 -> MemoriesList(
                    uiState = uiState,
                    onDelete = viewModel::deleteMemory,
                    onClear = { showClearMemoryDialog = true },
                    onExport = viewModel::exportMemory,
                    onSendToSoll = viewModel::sendMemorySummaryToSoll,
                    onSendEventsToSoll = viewModel::sendAssistantEventsSummaryToSoll,
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить логи") },
            text = { Text("Удалить сообщения, команды, уведомления и завершенные задачи инструментов? Активные задачи останутся.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showClearMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoryDialog = false },
            title = { Text("Очистить память") },
            text = { Text("Удалить все сохраненные предпочтения и принятые предложения? Логи и уведомления останутся.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMemory()
                        showClearMemoryDialog = false
                    }
                ) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoryDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    uiState.memoryExportText?.let { exportText ->
        AlertDialog(
            onDismissRequest = viewModel::closeMemoryExport,
            title = { Text("Экспорт памяти") },
            text = {
                Box(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = exportText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeMemoryExport) {
                    Text("Закрыть")
                }
            }
        )
    }
}

@Composable
private fun NotificationsList(
    uiState: LogsUiState,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    when {
        uiState.isLoadingNotifications -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.notificationsError != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.notificationsError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        uiState.notifications.isEmpty() -> {
            EmptyState("Уведомлений пока нет")
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.unreadNotifications > 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onMarkAllRead) {
                                Text("Отметить прочитанными")
                            }
                        }
                    }
                }
                items(
                    items = uiState.notifications,
                    key = { it.id }
                ) { notification ->
                    NotificationLogItem(
                        notification = notification,
                        expanded = expandedId == notification.id,
                        onToggle = {
                            expandedId = if (expandedId == notification.id) null else notification.id
                            if (notification.status == SollNotificationStatus.UNREAD) {
                                onMarkRead(notification.id)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoriesList(
    uiState: LogsUiState,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onSendToSoll: () -> Unit,
    onSendEventsToSoll: () -> Unit,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    when {
        uiState.isLoadingMemories -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.memoriesError != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.memoriesError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = onExport) {
                                Text("Экспорт")
                            }
                            TextButton(onClick = onClear) {
                                Text("Очистить", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onSendToSoll,
                                enabled = !uiState.isSendingMemoryToSoll,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uiState.isSendingMemoryToSoll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Память в Soll")
                            }
                            OutlinedButton(
                                onClick = onSendEventsToSoll,
                                enabled = !uiState.isSendingAssistantEventsToSoll,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (uiState.isSendingAssistantEventsToSoll) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("События")
                            }
                        }
                        uiState.memorySyncMessage?.let { message ->
                            SyncStatusMessage(message)
                        }
                        uiState.assistantEventSyncMessage?.let { message ->
                            SyncStatusMessage(message)
                        }
                        Text(
                            text = "В Soll отправляются только безопасные summary. Сырые логи, Telegram-тексты, payload JSON и медиа не включаются.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (uiState.memories.isEmpty()) {
                    item {
                        Text(
                            text = "Память пока пуста",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(
                        items = uiState.memories,
                        key = { it.id }
                    ) { memory ->
                        MemoryLogItem(
                            memory = memory,
                            expanded = expandedId == memory.id,
                            onToggle = {
                                expandedId = if (expandedId == memory.id) null else memory.id
                            },
                            onDelete = { onDelete(memory.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemoryLogItem(
    memory: AssistantMemory,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${memory.category.label()} · ${formatJobTime(memory.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить запись памяти",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = memory.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Источник: ${memory.source} · ключ: ${memory.key}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Уверенность: ${(memory.confidence.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    memory.payloadJson?.takeIf { it.isNotBlank() }?.let { payload ->
                        Text(
                            text = "Данные: ${payload.take(500)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationLogItem(
    notification: SollNotification,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val isUnread = notification.status == SollNotificationStatus.UNREAD
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = notification.priority.icon(),
                    contentDescription = null,
                    tint = notification.priority.color(),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${notification.channel.label()} · ${formatJobTime(notification.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isUnread) {
                    AssistChip(
                        onClick = onToggle,
                        label = { Text("новое") },
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Источник: ${notification.source} · тип: ${notification.type}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (notification.shownAt != null) {
                            "Системное уведомление: показано ${formatJobTime(notification.shownAt)}"
                        } else {
                            "Системное уведомление не показано: проверь разрешение уведомлений"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    notification.payloadJson?.takeIf { it.isNotBlank() }?.let { payload ->
                        Text(
                            text = "Данные: ${payload.take(500)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolJobsList(
    uiState: LogsUiState,
    onCancelJob: (String) -> Unit,
) {
    var expandedJobId by remember { mutableStateOf<String?>(null) }

    when {
        uiState.isLoadingJobs -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.jobsError != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.jobsError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        uiState.toolJobs.isEmpty() -> {
            EmptyState("Задач инструментов пока нет")
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.toolJobs,
                    key = { it.id }
                ) { job ->
                    ToolJobLogItem(
                        job = job,
                        expanded = expandedJobId == job.id,
                        onToggle = {
                            expandedJobId = if (expandedJobId == job.id) null else job.id
                        },
                        onCancel = { onCancelJob(job.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolJobLogItem(
    job: ToolJob,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCancel: () -> Unit,
) {
    val canCancel = job.status == ToolJobStatus.RUNNING || job.status == ToolJobStatus.QUEUED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = job.status.icon(),
                    contentDescription = null,
                    tint = job.status.color(),
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${job.toolId.toolLabel()} · ${job.status.label()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${job.id.take(8)} · ${formatJobTime(job.updatedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                job.progressPercent?.let {
                    Text(
                        text = "$it%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canCancel) {
                    TextButton(onClick = onCancel) {
                        Text("Отменить")
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (job.status == ToolJobStatus.RUNNING || job.status == ToolJobStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { ((job.progressPercent ?: 0) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (job.logText.isNotBlank()) {
                Text(
                    text = job.logText.lineSequence().lastOrNull().orEmpty().take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ID: ${job.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (job.finishedAt == null) {
                            "Обновлена: ${formatJobTime(job.updatedAt)}"
                        } else {
                            "Завершена: ${formatJobTime(job.finishedAt)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (job.inputJson.isNotBlank()) {
                        Text(
                            text = "Вход: ${job.inputJson.take(300)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    job.outputJson?.takeIf { it.isNotBlank() }?.let { output ->
                        Text(
                            text = "Выход: ${output.take(300)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (job.logText.isNotBlank()) {
                        Text(
                            text = "Лог:\n${job.logText.takeLast(800)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageLogsList(logs: List<MessageLogEntity>) {
    if (logs.isEmpty()) {
        EmptyState("Сообщений пока нет")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                MessageLogItem(log)
            }
        }
    }
}

@Composable
private fun CommandLogsList(logs: List<CommandLogEntity>) {
    if (logs.isEmpty()) {
        EmptyState("Команды пока не выполнялись")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs) { log ->
                CommandLogItem(log)
            }
        }
    }
}

@Composable
private fun MessageLogItem(log: MessageLogEntity) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.username?.let { "@$it" } ?: log.userFullName ?: "Неизвестно",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dateFormat.format(Date(log.receivedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            log.text?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (log.hasDocument) {
                    PassiveChip(
                        text = "Документ",
                        icon = Icons.Default.AttachFile,
                    )
                }
                if (log.hasPhoto) {
                    PassiveChip(
                        text = "Фото",
                        icon = Icons.Default.Image,
                    )
                }
                if (log.hasLocation) {
                    PassiveChip(
                        text = "Гео",
                        icon = Icons.Default.LocationOn,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandLogItem(log: CommandLogEntity) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
    val isSuccess = log.status == CommandLogEntity.STATUS_SUCCESS

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) StatusRunning else StatusError,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "/${log.command}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = dateFormat.format(Date(log.executedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            log.args?.let { args ->
                Text(
                    text = "Аргументы: $args",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSuccess && log.errorMessage != null) {
                Text(
                    text = "Ошибка: ${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
            }

            log.executionTimeMs?.let { ms ->
                Text(
                    text = "Время выполнения: ${ms} мс",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToolJobStatus.color() = when (this) {
    ToolJobStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    ToolJobStatus.FAILED,
    ToolJobStatus.BLOCKED -> MaterialTheme.colorScheme.error
    ToolJobStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    ToolJobStatus.QUEUED,
    ToolJobStatus.RUNNING,
    ToolJobStatus.WAITING_FOR_CONFIRMATION -> MaterialTheme.colorScheme.tertiary
}

private fun ToolJobStatus.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    ToolJobStatus.QUEUED -> Icons.Default.HourglassEmpty
    ToolJobStatus.RUNNING -> Icons.Default.Sync
    ToolJobStatus.WAITING_FOR_CONFIRMATION -> Icons.Default.PendingActions
    ToolJobStatus.SUCCESS -> Icons.Default.CheckCircle
    ToolJobStatus.FAILED -> Icons.Default.Error
    ToolJobStatus.CANCELLED -> Icons.Default.Cancel
    ToolJobStatus.BLOCKED -> Icons.Default.Block
}

private fun ToolJobStatus.label(): String = when (this) {
    ToolJobStatus.QUEUED -> "в очереди"
    ToolJobStatus.RUNNING -> "выполняется"
    ToolJobStatus.WAITING_FOR_CONFIRMATION -> "ждет подтверждения"
    ToolJobStatus.SUCCESS -> "успешно"
    ToolJobStatus.FAILED -> "ошибка"
    ToolJobStatus.CANCELLED -> "отменено"
    ToolJobStatus.BLOCKED -> "заблокировано"
}

@Composable
private fun SollNotificationPriority.color() = when (this) {
    SollNotificationPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    SollNotificationPriority.DEFAULT -> MaterialTheme.colorScheme.primary
    SollNotificationPriority.HIGH -> MaterialTheme.colorScheme.error
}

private fun SollNotificationPriority.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    SollNotificationPriority.LOW -> Icons.Default.NotificationsNone
    SollNotificationPriority.DEFAULT -> Icons.Default.Notifications
    SollNotificationPriority.HIGH -> Icons.Default.NotificationImportant
}

private fun SollNotificationChannel.label(): String = when (this) {
    SollNotificationChannel.EVENTS -> "События"
    SollNotificationChannel.ALERTS -> "Важное"
    SollNotificationChannel.TOOL_JOBS -> "Задачи"
    SollNotificationChannel.BOT_SERVICE -> "Бот"
    SollNotificationChannel.TTS_PLAYBACK -> "Книги"
    SollNotificationChannel.MUSIC_PLAYBACK -> "Музыка"
}

private fun AssistantMemoryCategory.label(): String = when (this) {
    AssistantMemoryCategory.SUGGESTION -> "Предложение"
    AssistantMemoryCategory.PREFERENCE -> "Предпочтение"
    AssistantMemoryCategory.TOOL_USAGE -> "Инструмент"
    AssistantMemoryCategory.COMMAND_PATTERN -> "Команда"
    AssistantMemoryCategory.DEVICE_PROFILE -> "Устройство"
    AssistantMemoryCategory.SYSTEM -> "Система"
}

private fun String.toolLabel(): String = when (this) {
    "books" -> "Книги"
    "music_scan" -> "Музыка"
    "scanner_export" -> "Сканер"
    "raw" -> "Raw"
    "photo" -> "Фото"
    "record" -> "Аудио"
    "download" -> "Файл"
    "bot_service" -> "Бот"
    else -> this
}

private fun formatJobTime(millis: Long): String =
    SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date(millis))

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
