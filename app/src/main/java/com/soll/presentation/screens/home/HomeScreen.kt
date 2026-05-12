package com.soll.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.proactive.ProactiveSuggestion
import com.soll.domain.assistant.proactive.ProactiveSuggestionAction
import com.soll.domain.assistant.proactive.ProactiveSuggestionPriority
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobStatus
import com.soll.ui.theme.StatusError
import com.soll.ui.theme.StatusRunning
import com.soll.ui.theme.StatusStopped
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshToken()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Soll",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (uiState.isRunning) StatusRunning else StatusStopped
                    )
                    Text(
                        text = if (uiState.isRunning) "Бот запущен" else "Бот остановлен",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                uiState.botUsername?.let { botUsername ->
                    Text(
                        text = botUsername,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (!uiState.hasToken) {
                    Text(
                        text = "Токен бота не настроен. Добавьте его в настройках.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Control Button
        Button(
            onClick = {
                if (uiState.isRunning) viewModel.stopBot() else viewModel.startBot()
            },
            enabled = !uiState.isLoading && (uiState.hasToken || uiState.isRunning),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isRunning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (uiState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isRunning) "Остановить бота" else "Запустить бота")
        }

        ProactiveSuggestionsCard(
            suggestions = uiState.proactiveSuggestions,
            onAccept = viewModel::acceptProactiveSuggestion,
            onDismiss = viewModel::dismissProactiveSuggestion,
            onSnooze = viewModel::snoozeProactiveSuggestion,
        )

        AssistantHealthCard(
            items = uiState.healthItems,
            onAction = viewModel::runHealthAction,
        )

        RecentOperationsCard(
            events = uiState.recentAssistantEvents,
            jobs = uiState.recentToolJobs,
        )

        // Statistics
        if (uiState.isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Статистика",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    StatRow(
                        icon = Icons.Default.Timer,
                        label = "Время работы",
                        value = uiState.uptime
                    )

                    StatRow(
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "Обработано сообщений",
                        value = uiState.messagesProcessed.toString()
                    )
                }
            }
        }

        // Error Card
        uiState.lastError?.let { lastError ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = StatusError
                    )
                    Text(
                        text = lastError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Info Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Как это работает",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Soll работает как фоновый сервис и слушает команды, отправленные вашему Telegram-боту. " +
                            "После запуска можно удаленно управлять устройством через команды /status, /info, /ping и другие.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ProactiveSuggestionsCard(
    suggestions: List<ProactiveSuggestion>,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onSnooze: (String) -> Unit,
) {
    if (suggestions.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Предложения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            suggestions.forEach { suggestion ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = suggestion.priority.icon(),
                                contentDescription = null,
                                tint = suggestion.priority.color(),
                                modifier = Modifier.size(22.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = suggestion.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = suggestion.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onDismiss(suggestion.id) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Скрыть предложение",
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onSnooze(suggestion.id) }) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Позже")
                            }
                            Button(onClick = { onAccept(suggestion.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(suggestion.action.acceptLabel())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantHealthCard(
    items: List<HealthItemUiState>,
    onAction: (HealthAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Состояние ассистента",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items.forEach { item ->
                HealthRow(item = item, onAction = onAction)
            }
        }
    }
}

@Composable
private fun HealthRow(
    item: HealthItemUiState,
    onAction: (HealthAction) -> Unit,
) {
    val icon = when (item.level) {
        HealthLevel.OK -> Icons.Default.CheckCircle
        HealthLevel.WARNING -> Icons.Default.Warning
        HealthLevel.ERROR -> Icons.Default.Error
    }
    val tint = when (item.level) {
        HealthLevel.OK -> StatusRunning
        HealthLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        HealthLevel.ERROR -> StatusError
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item.action?.let { action ->
            TextButton(onClick = { onAction(action) }) {
                Text(action.label())
            }
        }
    }
}

@Composable
private fun RecentOperationsCard(
    events: List<AssistantEvent>,
    jobs: List<ToolJob>,
) {
    if (events.isEmpty() && jobs.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Последние операции",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            jobs.forEach { job ->
                ToolJobRow(job)
            }

            events.forEach { event ->
                AssistantEventRow(event)
            }
        }
    }
}

@Composable
private fun ToolJobRow(job: ToolJob) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = job.status.statusColor()
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${job.toolId} - ${job.status.label()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${formatTime(job.updatedAt)}${job.progressPercent?.let { " - $it%" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssistantEventRow(event: AssistantEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${event.source} - ${formatTime(event.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun HealthAction.label(): String =
    when (this) {
        HealthAction.APP_SETTINGS -> "Настройки"
        HealthAction.BATTERY_SETTINGS -> "Батарея"
        HealthAction.NOTIFICATION_SETTINGS -> "Увед."
        HealthAction.WRITE_SETTINGS -> "Разрешить"
    }

@Composable
private fun ProactiveSuggestionPriority.color() =
    when (this) {
        ProactiveSuggestionPriority.HIGH -> MaterialTheme.colorScheme.error
        ProactiveSuggestionPriority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        ProactiveSuggestionPriority.LOW -> MaterialTheme.colorScheme.primary
    }

private fun ProactiveSuggestionPriority.icon() =
    when (this) {
        ProactiveSuggestionPriority.HIGH -> Icons.Default.Warning
        ProactiveSuggestionPriority.MEDIUM -> Icons.Default.Info
        ProactiveSuggestionPriority.LOW -> Icons.Default.CheckCircle
    }

private fun ProactiveSuggestionAction.acceptLabel(): String =
    when (this) {
        ProactiveSuggestionAction.NONE -> "Принять"
        ProactiveSuggestionAction.START_BOT -> "Запустить"
        ProactiveSuggestionAction.OPEN_APP_SETTINGS -> "Открыть"
        ProactiveSuggestionAction.OPEN_BATTERY_SETTINGS -> "Батарея"
    }

@Composable
private fun ToolJobStatus.statusColor() =
    when (this) {
        ToolJobStatus.SUCCESS -> StatusRunning
        ToolJobStatus.FAILED, ToolJobStatus.BLOCKED -> StatusError
        ToolJobStatus.CANCELLED -> StatusStopped
        ToolJobStatus.QUEUED,
        ToolJobStatus.RUNNING,
        ToolJobStatus.WAITING_FOR_CONFIRMATION -> MaterialTheme.colorScheme.tertiary
    }

private fun ToolJobStatus.label(): String =
    when (this) {
        ToolJobStatus.QUEUED -> "в очереди"
        ToolJobStatus.RUNNING -> "выполняется"
        ToolJobStatus.WAITING_FOR_CONFIRMATION -> "ждет подтверждения"
        ToolJobStatus.SUCCESS -> "успешно"
        ToolJobStatus.FAILED -> "ошибка"
        ToolJobStatus.CANCELLED -> "отменено"
        ToolJobStatus.BLOCKED -> "заблокировано"
    }

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
