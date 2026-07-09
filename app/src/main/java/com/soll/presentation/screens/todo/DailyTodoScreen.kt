package com.soll.presentation.screens.todo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.soll.SollDailyTask
import com.soll.domain.soll.SollDailyTaskAttachment
import com.soll.domain.soll.SollDailyTaskDetail
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollSourceItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTodoScreen(
    onBack: () -> Unit,
    viewModel: DailyTodoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var taskText by rememberSaveable { mutableStateOf("") }
    var pendingAttachmentUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAttachmentName by rememberSaveable { mutableStateOf<String?>(null) }
    var existingAttachmentTask by remember { mutableStateOf<SollDailyTask?>(null) }
    var pendingAdd by remember { mutableStateOf<PendingDailyTodoAdd?>(null) }
    var pendingResearchLocationRequest by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraFilename by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    fun clearAddForm() {
        taskText = ""
        pendingAttachmentUri = null
        pendingAttachmentName = null
    }

    fun pendingCameraCapture(): PendingCameraCapture? {
        val uri = pendingCameraUriString?.let(Uri::parse) ?: return null
        val task = pendingCameraTaskId?.let { taskId ->
            uiState.selectedTaskDetail?.task?.takeIf { it.id == taskId }
                ?: uiState.tasks.firstOrNull { it.id == taskId }
        }
        return PendingCameraCapture(
            uri = uri,
            filename = pendingCameraFilename ?: uri.lastPathSegment?.substringAfterLast('/') ?: "daily-photo.jpg",
            task = task,
        )
    }

    fun setPendingCameraCapture(photo: DailyTodoPhoto, task: SollDailyTask?) {
        pendingCameraUriString = photo.uri.toString()
        pendingCameraFilename = photo.filename
        pendingCameraTaskId = task?.id
    }

    fun clearPendingCameraCapture() {
        pendingCameraUriString = null
        pendingCameraFilename = null
        pendingCameraTaskId = null
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
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val request = pendingCameraCapture()
        clearPendingCameraCapture()
        if (saved && request != null) {
            if (request.task == null) {
                pendingAttachmentUri = request.uri
                pendingAttachmentName = request.filename
            } else {
                viewModel.attachFile(request.task, request.uri)
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingCameraCapture()
        if (granted && request != null) {
            cameraLauncher.launch(request.uri)
        } else {
            clearPendingCameraCapture()
            coroutineScope.launch { snackbarHostState.showSnackbar("Камера недоступна") }
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val request = pendingAdd
        val shouldResearch = pendingResearchLocationRequest
        pendingAdd = null
        pendingResearchLocationRequest = false
        if (request != null) {
            viewModel.addTask(
                text = request.text,
                attachmentUri = request.attachmentUri,
                attachLocation = grants.values.any { it },
            )
        } else if (shouldResearch) {
            viewModel.researchSelectedTask(publishLocation = grants.values.any { it })
        }
    }

    fun startCameraCapture(task: SollDailyTask?) {
        val photo = context.createDailyTodoPhotoUri()
        if (photo == null) {
            coroutineScope.launch { snackbarHostState.showSnackbar("Не удалось открыть камеру") }
            return
        }
        setPendingCameraCapture(photo, task)
        if (context.hasDailyTodoCameraPermission()) {
            cameraLauncher.launch(photo.uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

    fun submitResearch() {
        if (context.hasDailyTodoLocationPermission()) {
            viewModel.researchSelectedTask(publishLocation = true)
        } else {
            pendingResearchLocationRequest = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    fun requestDeleteTask(task: SollDailyTask) {
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Удалить дело?",
                actionLabel = "Отмена",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result != SnackbarResult.ActionPerformed) {
                viewModel.deleteTask(task)
            }
        }
    }

    LaunchedEffect(uiState.addSuccessVersion) {
        if (uiState.addSuccessVersion > 0L) {
            clearAddForm()
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
                    IconButton(
                        onClick = { viewModel.refreshCurrent() },
                        enabled = !uiState.isLoading && !uiState.sourceLoading,
                    ) {
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
            if (uiState.isLoading || uiState.isAdding || uiState.sourceLoading || uiState.detailLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            DailyTodoTabs(
                selectedTab = uiState.selectedTab,
                onSelect = viewModel::selectTab,
            )
            when (uiState.selectedTab) {
                DailyTodoTab.TASKS -> {
                    val detail = uiState.selectedTaskDetail
                    if (detail != null || uiState.detailLoading) {
                        DailyTodoDetailPane(
                            detail = detail,
                            isLoading = uiState.detailLoading,
                            isAttachmentRunning = detail?.task?.id == uiState.attachmentTaskId,
                            isResearchRunning = detail?.task?.id == uiState.researchTaskId,
                            onBack = viewModel::closeTaskDetail,
                            onToggle = { task, done -> viewModel.setTaskDone(task, done) },
                            onSelectFile = { task ->
                                existingAttachmentTask = task
                                existingAttachmentPicker.launch("*/*")
                            },
                            onTakePhoto = { task -> startCameraCapture(task) },
                            onResearch = ::submitResearch,
                        )
                    } else {
                        DailyTodoTasksMode(
                            uiState = uiState,
                            taskText = taskText,
                            pendingAttachmentName = pendingAttachmentName,
                            onTaskTextChange = { taskText = it },
                            onSelectFile = { addAttachmentPicker.launch("*/*") },
                            onTakePhoto = { startCameraCapture(null) },
                            onClearFile = {
                                pendingAttachmentUri = null
                                pendingAttachmentName = null
                            },
                            onAddTask = ::submitTask,
                            onOpenTask = viewModel::openTask,
                            onToggleTask = viewModel::setTaskDone,
                            onDeleteTask = ::requestDeleteTask,
                            onAttachTask = { task ->
                                existingAttachmentTask = task
                                existingAttachmentPicker.launch("*/*")
                            },
                            onPhotoTask = { task -> startCameraCapture(task) },
                        )
                    }
                }
                DailyTodoTab.SOURCES -> DailyTodoSourcesMode(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun DailyTodoTabs(
    selectedTab: DailyTodoTab,
    onSelect: (DailyTodoTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedTab == DailyTodoTab.TASKS,
            onClick = { onSelect(DailyTodoTab.TASKS) },
            label = { Text("Дела") },
        )
        FilterChip(
            selected = selectedTab == DailyTodoTab.SOURCES,
            onClick = { onSelect(DailyTodoTab.SOURCES) },
            label = { Text("Источники дел") },
        )
    }
}

@Composable
private fun DailyTodoTasksMode(
    uiState: DailyTodoUiState,
    taskText: String,
    pendingAttachmentName: String?,
    onTaskTextChange: (String) -> Unit,
    onSelectFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onClearFile: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (SollDailyTask) -> Unit,
    onToggleTask: (SollDailyTask, Boolean) -> Unit,
    onDeleteTask: (SollDailyTask) -> Unit,
    onAttachTask: (SollDailyTask) -> Unit,
    onPhotoTask: (SollDailyTask) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            DailyTodoAddCard(
                taskText = taskText,
                pendingAttachmentName = pendingAttachmentName,
                isAdding = uiState.isAdding,
                onTaskTextChange = onTaskTextChange,
                onSelectFile = onSelectFile,
                onTakePhoto = onTakePhoto,
                onClearFile = onClearFile,
                onAddTask = onAddTask,
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
                isRunning = uiState.actionTaskId == task.id || uiState.deletingTaskId == task.id,
                isAttachmentRunning = uiState.attachmentTaskId == task.id,
                onOpen = { onOpenTask(task) },
                onToggle = { done -> onToggleTask(task, done) },
                onDelete = { onDeleteTask(task) },
                onAttach = { onAttachTask(task) },
                onPhoto = { onPhotoTask(task) },
            )
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
    onTakePhoto: () -> Unit,
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
                IconButton(
                    onClick = onSelectFile,
                    enabled = !isAdding,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить файл")
                }
                IconButton(
                    onClick = onTakePhoto,
                    enabled = !isAdding,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Сделать фото")
                }
                Button(
                    onClick = onAddTask,
                    enabled = !isAdding,
                    modifier = Modifier.weight(1f),
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
@OptIn(ExperimentalMaterial3Api::class)
private fun DailyTodoRow(
    task: SollDailyTask,
    isRunning: Boolean,
    isAttachmentRunning: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAttach: () -> Unit,
    onPhoto: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
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
                    IconButton(onClick = onPhoto, enabled = !isAttachmentRunning) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Сделать фото")
                    }
                }
                if (task.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        task.attachments.take(3).forEach { attachment ->
                            AttachmentChip(text = attachment.dailyTodoAttachmentChipText())
                        }
                        if (task.attachments.size > 3) {
                            AttachmentChip(text = "+${task.attachments.size - 3}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTodoDetailPane(
    detail: SollDailyTaskDetail?,
    isLoading: Boolean,
    isAttachmentRunning: Boolean,
    isResearchRunning: Boolean,
    onBack: () -> Unit,
    onToggle: (SollDailyTask, Boolean) -> Unit,
    onSelectFile: (SollDailyTask) -> Unit,
    onTakePhoto: (SollDailyTask) -> Unit,
    onResearch: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (detail == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
        item {
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "К списку")
                        }
                        Text(
                            text = detail.task.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Checkbox(
                            checked = detail.task.done,
                            onCheckedChange = { done -> onToggle(detail.task, done) },
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = { onSelectFile(detail.task) },
                            enabled = !isAttachmentRunning,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить файл")
                        }
                        IconButton(
                            onClick = { onTakePhoto(detail.task) },
                            enabled = !isAttachmentRunning,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Сделать фото")
                        }
                        Button(onClick = onResearch, enabled = !isResearchRunning) {
                            if (isResearchRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Найти")
                        }
                    }
                }
            }
        }

        if (detail.geo.hasGeo()) {
            item {
                DetailSection(title = "Гео") {
                    val coords = listOfNotNull(
                        detail.geo.latitude?.let { "%.5f".format(Locale.US, it) },
                        detail.geo.longitude?.let { "%.5f".format(Locale.US, it) },
                    ).joinToString(", ")
                    if (detail.geo.locationLabel.isNotBlank()) {
                        Text(detail.geo.locationLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (coords.isNotBlank()) {
                        Text(coords, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    detail.geo.accuracyMeters?.let {
                        Text("Точность: ${it.toInt()} м", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            DetailSection(title = "Вложения") {
                if (detail.task.attachments.isEmpty()) {
                    Text("Файлов пока нет", style = MaterialTheme.typography.bodyMedium)
                } else {
                    detail.task.attachments.forEach { attachment ->
                        AttachmentDetailCard(attachment)
                    }
                }
            }
        }

        if (detail.sourceMatches.isNotEmpty()) {
            item {
                DetailSection(title = "Совпадения в источниках") {
                    detail.sourceMatches.forEach { result ->
                        ResearchMapCard(result)
                    }
                }
            }
        }

        detail.research?.let { research ->
            item {
                DetailSection(title = "Поиск") {
                    Text(research.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    research.query.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ResearchGroup("Локально", research.localResults)
                    ResearchGroup("Источники", research.sourceResults)
                    ResearchGroup("В сети", research.webResults)
                }
            }
        }
        }
    }
}

@Composable
private fun DailyTodoSourcesMode(
    uiState: DailyTodoUiState,
    viewModel: DailyTodoViewModel,
) {
    var sourceName by remember { mutableStateOf("") }
    var sourceTarget by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf(SourceTypeOption.WEB) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "source-add") {
            Card(shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Новый источник дел", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                        label = { Text("URL или RSS") },
                    )
                    Button(
                        onClick = {
                            viewModel.createSource(sourceName, sourceTarget, sourceType.apiValue)
                            sourceName = ""
                            sourceTarget = ""
                            sourceType = SourceTypeOption.WEB
                        },
                        enabled = sourceTarget.isNotBlank() && !uiState.sourceLoading,
                    ) {
                        Text("Добавить")
                    }
                }
            }
        }
        if (!uiState.sourceLoading && uiState.sources.isEmpty()) {
            item {
                Text(
                    text = "Источники дел не добавлены",
                    modifier = Modifier.padding(vertical = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Text("Материалы источника дел", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(uiState.sourceItems, key = { it.itemId }) { item ->
                SourceItemCard(item = item)
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
private fun SourceItemCard(item: SollSourceItem) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
                item.sourceUrl.takeIf { it.isNotBlank() }?.let { PassiveChip(text = it.take(42)) }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AttachmentDetailCard(attachment: SollDailyTaskAttachment) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(attachment.filename, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                attachment.analysisStatus.dailyAttachmentMessage(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            attachment.analysisSummary.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            if (attachment.searchTerms.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachment.searchTerms.take(8).forEach { term -> PassiveChip(text = term) }
                }
            }
        }
    }
}

@Composable
private fun ResearchGroup(title: String, results: List<Map<String, Any?>>) {
    if (results.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge)
    results.forEach { result ->
        ResearchMapCard(result)
    }
}

@Composable
private fun ResearchMapCard(result: Map<String, Any?>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = result.text("title", "path", "url", fallback = "Материал"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            result.text("source_name", "source", fallback = "").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            result.text("summary", "snippet", "content_preview", fallback = "").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            result.text("url", "path", fallback = "").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun PassiveChip(text: String) {
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

private data class PendingCameraCapture(
    val uri: Uri,
    val filename: String,
    val task: SollDailyTask?,
)

private data class DailyTodoPhoto(
    val uri: Uri,
    val filename: String,
)

private fun Context.hasDailyTodoLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun Context.hasDailyTodoCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.createDailyTodoPhotoUri(): DailyTodoPhoto? =
    runCatching {
        val directory = File(cacheDir, "daily_todo_photos").apply { mkdirs() }
        val filename = "daily-todo-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jpg"
        val file = File(directory, filename)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        DailyTodoPhoto(uri = uri, filename = filename)
    }.getOrNull()

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

private fun String.dailyAttachmentMessage(): String =
    when (this) {
        "parsed" -> "Файл прикреплен и разобран"
        "ocr_only" -> "Фото прикреплено, текст распознан"
        "vision_unavailable" -> "Фото прикреплено, для объекта нужна локальная vision-модель"
        "unsupported" -> "Файл прикреплен, анализ недоступен"
        else -> "Вложение прикреплено"
    }

private fun com.soll.domain.soll.SollDailyTaskGeo.hasGeo(): Boolean =
    locationLabel.isNotBlank() || latitude != null || longitude != null || accuracyMeters != null

private fun Map<String, Any?>.text(vararg keys: String, fallback: String): String =
    keys.firstNotNullOfOrNull { key -> this[key]?.toString()?.takeIf { it.isNotBlank() } } ?: fallback
