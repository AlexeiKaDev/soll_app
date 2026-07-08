package com.soll.presentation.screens.tools.fieldmap

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.activity.ActivityTrackingSummary
import com.soll.domain.field.FieldLocationSnapshot
import com.soll.domain.field.FieldPoint
import com.soll.domain.field.FieldPointStatus
import com.soll.domain.field.GeoCoordinate
import com.soll.ui.components.PassiveChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldMapScreen(
    onBack: () -> Unit,
    viewModel: FieldMapViewModel = hiltViewModel(),
    initialActivityFocus: Boolean = false,
    locationProcessorMode: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTitle by rememberSaveable { mutableStateOf("") }
    var currentNote by rememberSaveable { mutableStateOf("") }
    var manualTitle by rememberSaveable { mutableStateOf("") }
    var manualNote by rememberSaveable { mutableStateOf("") }
    var manualLatitude by rememberSaveable { mutableStateOf("") }
    var manualLongitude by rememberSaveable { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.refreshLocation()
        } else {
            viewModel.showMessage("Без разрешения геолокации можно добавлять только ручные координаты", isError = true)
        }
    }
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (context.hasActivityTrackingMinimumPermission()) {
            viewModel.startActivityTracking()
        } else {
            viewModel.showMessage("Для трекера активности нужно разрешить шаги или текущую геолокацию", isError = true)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    fun runWithLocationPermission(action: () -> Unit) {
        if (!viewModel.ensureFieldMapCapability()) return
        if (context.hasLocationPermission()) {
            action()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    fun runWithActivityTrackingPermission() {
        if (!viewModel.ensureFieldMapCapability()) return
        val missing = context.activityTrackingPermissionsToRequest()
        if (missing.isEmpty()) {
            viewModel.startActivityTracking()
        } else {
            activityPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            locationProcessorMode -> "Геопозиция"
                            initialActivityFocus -> "Активность"
                            else -> "Карта"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { runWithLocationPermission(viewModel::refreshLocation) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить геолокацию")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (locationProcessorMode) {
                item {
                    LocationProcessorHeader()
                }
                item {
                    CurrentLocationCard(
                        currentLocation = uiState.currentLocation,
                        isLoading = uiState.isLoadingLocation,
                        title = currentTitle,
                        note = currentNote,
                        onTitleChange = { currentTitle = it },
                        onNoteChange = { currentNote = it },
                        onRefreshLocation = { runWithLocationPermission(viewModel::refreshLocation) },
                        onPublishLocation = { runWithLocationPermission(viewModel::publishCurrentLocationToSoll) },
                        onSaveCurrentPoint = {
                            runWithLocationPermission {
                                viewModel.saveCurrentPoint(currentTitle, currentNote)
                                currentTitle = ""
                                currentNote = ""
                            }
                        },
                    )
                }
            } else {
                item {
                    FieldHeader(uiState)
                }

                item {
                    ActivityHistoryCard(
                        summary = uiState.activitySummary,
                        isRunning = uiState.isActivityTrackerRunning,
                        onStart = { runWithActivityTrackingPermission() },
                        onStop = viewModel::stopActivityTracking,
                    )
                }

                item {
                    FieldMapPreview(
                        points = uiState.points,
                        currentLocation = uiState.currentLocation,
                    )
                }

                item {
                    CurrentLocationCard(
                        currentLocation = uiState.currentLocation,
                        isLoading = uiState.isLoadingLocation,
                        title = currentTitle,
                        note = currentNote,
                        onTitleChange = { currentTitle = it },
                        onNoteChange = { currentNote = it },
                        onRefreshLocation = { runWithLocationPermission(viewModel::refreshLocation) },
                        onPublishLocation = { runWithLocationPermission(viewModel::publishCurrentLocationToSoll) },
                        onSaveCurrentPoint = {
                            runWithLocationPermission {
                                viewModel.saveCurrentPoint(currentTitle, currentNote)
                                currentTitle = ""
                                currentNote = ""
                            }
                        },
                    )
                }

                item {
                    ManualPointCard(
                        title = manualTitle,
                        note = manualNote,
                        latitude = manualLatitude,
                        longitude = manualLongitude,
                        onTitleChange = { manualTitle = it },
                        onNoteChange = { manualNote = it },
                        onLatitudeChange = { manualLatitude = it },
                        onLongitudeChange = { manualLongitude = it },
                        onSave = {
                            viewModel.saveManualPoint(
                                title = manualTitle,
                                note = manualNote,
                                latitude = manualLatitude,
                                longitude = manualLongitude,
                            )
                            manualTitle = ""
                            manualNote = ""
                            manualLatitude = ""
                            manualLongitude = ""
                        },
                    )
                }

                item {
                    ImportTasksCard(
                        isImporting = uiState.isImportingTasks,
                        onImport = viewModel::importTaskPoints,
                    )
                }

                item {
                    Text(
                        text = "Точки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (uiState.points.isEmpty()) {
                    item {
                        EmptyFieldPoints()
                    }
                } else {
                    items(uiState.points, key = { it.id }) { point ->
                        FieldPointCard(
                            point = point,
                            isActionRunning = uiState.actionPointId == point.id,
                            onOpenMaps = {
                                if (viewModel.ensureFieldMapCapability() && !openPointInMaps(context, point)) {
                                    viewModel.showMessage("Не удалось открыть карту на устройстве", isError = true)
                                }
                            },
                            onStart = { viewModel.setStatus(point, FieldPointStatus.ACTIVE) },
                            onDone = { viewModel.setStatus(point, FieldPointStatus.DONE) },
                            onSkip = { viewModel.setStatus(point, FieldPointStatus.SKIPPED) },
                            onExportNote = { viewModel.exportToNote(point) },
                            onDelete = { viewModel.deletePoint(point) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationProcessorHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Android -> Soll",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Телефон отправляет текущую точку на primary-компьютер. Soll использует ее для поиска по заданным источникам.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldHeader(uiState: FieldMapUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Полевой режим",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Локальные точки, GPS-фиксация, импорт координат из задач и быстрый переход в установленную карту.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassiveChip(text = "В плане: ${uiState.plannedCount}")
                PassiveChip(text = "В работе: ${uiState.activeCount}")
                PassiveChip(text = "Готово: ${uiState.doneCount}")
            }
        }
    }
}

@Composable
private fun ActivityHistoryCard(
    summary: ActivityTrackingSummary,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Активность", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (isRunning) "Фоновый демон работает" else "Фоновый демон остановлен",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "Шагомер пишет историю экономно: GPS запрашивается редко, чаще только при движении; на низкой батарее интервал увеличивается.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassiveChip(text = "Шаги сегодня: ${summary.todaySteps}")
                PassiveChip(text = "Точек сегодня: ${summary.samplesToday}")
                PassiveChip(text = "Дистанция: ${distanceLabel(summary.todayDistanceMeters)}")
            }
            summary.lastSample?.let { sample ->
                val locationText = if (sample.latitude != null && sample.longitude != null) {
                    GeoCoordinate(sample.latitude, sample.longitude).formatted()
                } else {
                    "без GPS"
                }
                Text(
                    text = "Последний замер: ${formatTime(sample.capturedAt)} • $locationText • +${sample.stepDelta} шагов • ${sample.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } ?: Text(
                text = "История пока пустая. Запусти демон и пройдись несколько минут.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Трекер работает через foreground service: оставьте уведомление включенным; GPS используется только при выданной текущей геолокации.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Остановить")
                    }
                } else {
                    Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Запустить демон")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldMapPreview(
    points: List<FieldPoint>,
    currentLocation: FieldLocationSnapshot?,
) {
    val coordinates = points.map { it.coordinate } + listOfNotNull(currentLocation?.coordinate)
    val surface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val current = MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        if (coordinates.isEmpty()) {
            Text(
                text = "Добавь точку или обнови GPS, чтобы увидеть схему маршрута.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Surface
        }

        val latitudes = coordinates.map { it.latitude }
        val longitudes = coordinates.map { it.longitude }
        val minLat = latitudes.minOrNull() ?: 0.0
        val maxLat = latitudes.maxOrNull() ?: 0.0
        val minLon = longitudes.minOrNull() ?: 0.0
        val maxLon = longitudes.maxOrNull() ?: 0.0
        val latSpan = (maxLat - minLat).takeIf { it > 0.000001 } ?: 0.01
        val lonSpan = (maxLon - minLon).takeIf { it > 0.000001 } ?: 0.01

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(12.dp),
        ) {
            val paddingPx = 14.dp.toPx()
            repeat(4) { index ->
                val fraction = (index + 1) / 5f
                drawLine(
                    color = grid,
                    start = Offset(paddingPx, size.height * fraction),
                    end = Offset(size.width - paddingPx, size.height * fraction),
                )
                drawLine(
                    color = grid,
                    start = Offset(size.width * fraction, paddingPx),
                    end = Offset(size.width * fraction, size.height - paddingPx),
                )
            }

            fun project(coordinate: GeoCoordinate): Offset {
                val x = paddingPx + (((coordinate.longitude - minLon) / lonSpan).toFloat() * (size.width - paddingPx * 2))
                val y = paddingPx + (((maxLat - coordinate.latitude) / latSpan).toFloat() * (size.height - paddingPx * 2))
                return Offset(x, y)
            }

            val routePoints = points.map { project(it.coordinate) }
            routePoints.zipWithNext().forEach { (start, end) ->
                drawLine(color = tertiary.copy(alpha = 0.62f), start = start, end = end, strokeWidth = 2.dp.toPx())
            }
            points.forEach { point ->
                val color = when (point.status) {
                    FieldPointStatus.ACTIVE -> current
                    FieldPointStatus.DONE -> primary
                    FieldPointStatus.PLANNED,
                    FieldPointStatus.SKIPPED -> tertiary
                }
                val center = project(point.coordinate)
                drawCircle(color = color, radius = 7.dp.toPx(), center = center)
                drawCircle(color = color.copy(alpha = 0.28f), radius = 13.dp.toPx(), center = center)
            }
            currentLocation?.let {
                val center = project(it.coordinate)
                drawCircle(color = current, radius = 5.dp.toPx(), center = center)
                drawCircle(color = current, radius = 16.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(
    currentLocation: FieldLocationSnapshot?,
    isLoading: Boolean,
    title: String,
    note: String,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRefreshLocation: () -> Unit,
    onPublishLocation: () -> Unit,
    onSaveCurrentPoint: () -> Unit,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Моя геопозиция", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = currentLocation?.let { location ->
                    buildString {
                        append(location.coordinate.formatted())
                        location.accuracyMeters?.let { append(" • точность ${it.toInt()} м") }
                        append(" • ${formatTime(location.capturedAt)}")
                    }
                } ?: "Геопозиция еще не обновлялась.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название точки") },
                singleLine = true,
            )
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий") },
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefreshLocation,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Обновить")
                }
                Button(
                    onClick = onSaveCurrentPoint,
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Сохранить здесь")
                }
            }
            OutlinedButton(
                onClick = onPublishLocation,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отправить геопозицию в Soll")
            }
        }
    }
}

@Composable
private fun ManualPointCard(
    title: String,
    note: String,
    latitude: String,
    longitude: String,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onSave: () -> Unit,
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
            Text("Ручная точка", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Широта") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Долгота") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий") },
                minLines = 2,
            )
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Добавить точку")
            }
        }
    }
}

@Composable
private fun ImportTasksCard(
    isImporting: Boolean,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Импорт из задач", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Если в задаче есть координаты, geo-ссылка или Google Maps `q=lat,lon`, она появится как полевая точка.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onImport,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Найти координаты в задачах")
            }
        }
    }
}

@Composable
private fun FieldPointCard(
    point: FieldPoint,
    isActionRunning: Boolean,
    onOpenMaps: () -> Unit,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onExportNote: () -> Unit,
    onDelete: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = point.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = point.coordinate.formatted(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, enabled = !isActionRunning) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }
            if (point.note.isNotBlank()) {
                Text(
                    text = point.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassiveChip(text = point.status.label)
                PassiveChip(text = point.source.label)
                point.distanceMeters?.let {
                    PassiveChip(text = distanceLabel(it))
                }
                point.accuracyMeters?.let {
                    PassiveChip(text = "±${it.toInt()} м")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenMaps,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Маршрут")
                }
                if (point.status != FieldPointStatus.ACTIVE) {
                    OutlinedButton(
                        onClick = onStart,
                        enabled = !isActionRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("В работу")
                    }
                }
                if (point.status != FieldPointStatus.DONE) {
                    Button(
                        onClick = onDone,
                        enabled = !isActionRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Готово")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExportNote, enabled = !isActionRunning) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("В заметку")
                }
                TextButton(onClick = onSkip, enabled = !isActionRunning && point.status != FieldPointStatus.SKIPPED) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Пропустить")
                }
            }
        }
    }
}

@Composable
private fun EmptyFieldPoints() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "Пока нет точек. Сохрани текущую геопозицию, добавь координаты вручную или импортируй задачи с координатами.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Context.hasLocationPermission(): Boolean {
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

private fun Context.hasActivityRecognitionPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.hasActivityTrackingMinimumPermission(): Boolean =
    hasActivityRecognitionPermission() || hasLocationPermission()

private fun Context.activityTrackingPermissionsToRequest(): List<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityRecognitionPermission()) {
        permissions += Manifest.permission.ACTIVITY_RECOGNITION
    }
    val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    return permissions.distinct()
}

private fun openPointInMaps(context: Context, point: FieldPoint): Boolean {
    val latitude = point.coordinate.latitude
    val longitude = point.coordinate.longitude
    val label = Uri.encode(point.title)
    val geoIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("geo:0,0?q=$latitude,$longitude($label)"),
    )
    return runCatching {
        context.startActivity(geoIntent)
        true
    }.getOrElse {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://maps.google.com/?q=$latitude,$longitude"),
        )
        runCatching {
            context.startActivity(webIntent)
            true
        }.getOrDefault(false)
    }
}

private fun distanceLabel(distanceMeters: Double): String =
    if (distanceMeters >= 1000) {
        String.format(Locale.forLanguageTag("ru"), "%.1f км", distanceMeters / 1000.0)
    } else {
        "${distanceMeters.toInt()} м"
    }

private fun formatTime(timeMillis: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru")).format(Date(timeMillis))
