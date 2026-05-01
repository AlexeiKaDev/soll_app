package com.soll.presentation.screens.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.soll.ui.theme.StatusError
import com.soll.ui.theme.StatusRunning
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: LogsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with clear button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Logs",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = { showClearDialog = true }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs")
            }
        }

        // Tabs
        TabRow(selectedTabIndex = uiState.selectedTab) {
            Tab(
                selected = uiState.selectedTab == 0,
                onClick = { viewModel.selectTab(0) },
                text = { Text("Messages (${uiState.messageLogs.size})") }
            )
            Tab(
                selected = uiState.selectedTab == 1,
                onClick = { viewModel.selectTab(1) },
                text = { Text("Commands (${uiState.commandLogs.size})") }
            )
        }

        // Content
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
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Logs") },
            text = { Text("Are you sure you want to clear all logs? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MessageLogsList(logs: List<MessageLogEntity>) {
    if (logs.isEmpty()) {
        EmptyState("No messages received yet")
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
        EmptyState("No commands executed yet")
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
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }

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
                    text = log.username?.let { "@$it" } ?: log.userFullName ?: "Unknown",
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
                    AssistChip(
                        onClick = { },
                        label = { Text("Document") },
                        leadingIcon = {
                            Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp))
                        }
                    )
                }
                if (log.hasPhoto) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Photo") },
                        leadingIcon = {
                            Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                        }
                    )
                }
                if (log.hasLocation) {
                    AssistChip(
                        onClick = { },
                        label = { Text("Location") },
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandLogItem(log: CommandLogEntity) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
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
                    text = "Args: $args",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isSuccess && log.errorMessage != null) {
                Text(
                    text = "Error: ${log.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
            }

            log.executionTimeMs?.let { ms ->
                Text(
                    text = "Execution time: ${ms}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
