package com.soll.presentation.screens.todo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.soll.SollDailyTask
import com.soll.domain.soll.SollDailyTaskAttachment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTodoScreen(
    onBack: () -> Unit,
    viewModel: DailyTodoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var taskText by rememberSaveable { mutableStateOf("") }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentName by rememberSaveable { mutableStateOf<String?>(null) }
    var existingAttachmentTask by remember { mutableStateOf<SollDailyTask?>(null) }
    var pendingAdd by remember { mutableStateOf<PendingDailyTodoAdd?>(null) }

    fun clearAddForm() {
        taskText = ""
        pendingAttachmentUri = null
        pendingAttachmentName = null
    }

    val addAttachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            pendingAttachmentUri = uri
            pendingAttachmentName = context.dailyTodoDisplayName(uri)
        }
    }
    val existingAttachmentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val task = existingAttachmentTask
        existingAttachmentTask = null
        if (task != null && uri != null) {
            viewModel.attachFile(task, uri)
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = pendingAdd
        pendingAdd = null
        if (request != null) {
            viewModel.addTask(
                text = request.text,
                attachmentUri = request.attachmentUri,
                attachLocation = grants.values.any { it },
            )
            clearAddForm()
        }
    }

    fun submitTask() {
        val cleanText = taskText.trim()
        if (cleanText.isBlank()) {
            viewModel.addTask(cleanText, pendingAttachmentUri, attachLocation = false)
            return
        }
        val attachmentUri = pendingAttachmentUri
        if (context.hasDailyTodoLocationPermission()) {
            viewModel.addTask(cleanText, attachmentUri, attachLocation = true)
            clearAddForm()
        } else {
            pendingAdd = PendingDailyTodoAdd(cleanText, attachmentUri)
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
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
                title = { Text("Список дел") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState.isLoading || uiState.isAdding) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DailyTodoAddCard(
                        taskText = taskText,
                        pendingAttachmentName = pendingAttachmentName,
                        isAdding = uiState.isAdding,
                        onTaskTextChange = { taskText = it },
                        onSelectFile = { addAttachmentPicker.launch("*/*") },
                        onClearFile = {
                            pendingAttachmentUri = null
                            pendingAttachmentName = null
                        },
                        onAddTask = ::submitTask,
                    )
                }

                if (!uiState.isLoading && uiState.tasks.isEmpty()) {
                    item {
                        DailyTodoEmptyState()
                    }
                }

                items(
                    items = uiState.tasks,
                    key = { it.id },
                ) { task ->
                    DailyTodoRow(
                        task = task,
                        isRunning = uiState.actionTaskId == task.id,
                        isAttachmentRunning = uiState.attachmentTaskId == task.id,
                        onToggle = { done -> viewModel.setTaskDone(task, done) },
                        onAttach = {
                            existingAttachmentTask = task
                            existingAttachmentPicker.launch("*/*")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTodoAddCard(
    taskText: String,
    pendingAttachmentName: String?,
    isAdding: Boolean,
    onTaskTextChange: (String) -> Unit,
    onSelectFile: () -> Unit,
    onClearFile: () -> Unit,
    onAddTask: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = taskText,
                onValueChange = onTaskTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Новое дело") },
                shape = RoundedCornerShape(12.dp),
            )
            pendingAttachmentName?.let { name ->
                PendingAttachmentChip(
                    filename = name,
                    onClearFile = onClearFile,
                    enabled = !isAdding,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onSelectFile,
                    enabled = !isAdding,
                    modifier = Modifier.weight(0.42f),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (pendingAttachmentName == null) "Файл" else "Заменить")
                }
                Button(
                    onClick = onAddTask,
                    enabled = !isAdding,
                    modifier = Modifier.weight(0.58f),
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Добавить")
                }
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(
    filename: String,
    enabled: Boolean,
    onClearFile: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = filename,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onClearFile, enabled = enabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Убрать файл", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DailyTodoRow(
    task: SollDailyTask,
    isRunning: Boolean,
    isAttachmentRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    onAttach: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.done) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = onToggle,
                    )
                }
                Text(
                    text = task.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAttach, enabled = !isAttachmentRunning) {
                    if (isAttachmentRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить файл")
                    }
                }
            }
            if (task.attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    task.attachments.take(2).forEach { attachment ->
                        AttachmentChip(text = attachment.dailyTodoAttachmentChipText())
                    }
                    if (task.attachments.size > 2) {
                        AttachmentChip(text = "+${task.attachments.size - 2}")
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DailyTodoEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Нет дел на сегодня",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class PendingDailyTodoAdd(
    val text: String,
    val attachmentUri: Uri?,
)

private fun Context.hasDailyTodoLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun Context.dailyTodoDisplayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                cursor.getString(index)
            } else {
                null
            }
        }
    }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "Файл выбран"

private fun SollDailyTaskAttachment.dailyTodoAttachmentChipText(): String {
    val label = when (analysisStatus) {
        "parsed" -> "Файл"
        "ocr_only" -> "Фото OCR"
        "vision_unavailable" -> "Фото"
        "unsupported" -> "Файл"
        else -> "Вложение"
    }
    return "$label: ${filename.take(22)}"
}
