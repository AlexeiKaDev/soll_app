package com.soll.presentation.screens.tools.coursecoach

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

private enum class CourseCoachTab(val title: String) {
    TODAY("Сегодня"),
    PLAN("План"),
    STATS("Статистика"),
    LIBRARY("Библиотека"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCoachScreen(
    onBack: () -> Unit,
    viewModel: CourseCoachViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importCoursePackage)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    uiState.activeSession?.let { session ->
        CourseSessionDialog(
            session = session,
            onDismiss = viewModel::dismissSession,
            onToggleExercise = viewModel::toggleExercise,
            onComplete = viewModel::completeActiveSession,
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Дневной прогресс и история сессий будут очищены для текущего курса.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetProgress()
                    }
                ) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.course?.title ?: "Course Coach") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Импортировать package")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val selectedTab = CourseCoachTab.entries[selectedTabIndex]
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                CourseCoachTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(tab.title) },
                    )
                }
            }

            if (uiState.course == null) {
                EmptyCourseState(
                    onInstallSeed = viewModel::reinstallSeedCourse,
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                )
            } else {
                when (selectedTab) {
                    CourseCoachTab.TODAY -> TodayTab(
                        uiState = uiState,
                        onStartSession = viewModel::startSession,
                        onSkipDay = viewModel::skipCurrentDay,
                    )
                    CourseCoachTab.PLAN -> PlanTab(
                        days = uiState.days,
                        onStartSession = viewModel::startSession,
                    )
                    CourseCoachTab.STATS -> StatsTab(stats = uiState.stats)
                    CourseCoachTab.LIBRARY -> LibraryTab(
                        uiState = uiState,
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onReinstallSeed = viewModel::reinstallSeedCourse,
                        onResetProgress = { showResetDialog = true },
                        onToggleReminder = viewModel::toggleReminder,
                        onShiftReminder = viewModel::shiftReminder,
                        onOpenBonus = viewModel::openBonusLesson,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCourseState(
    onInstallSeed: () -> Unit,
    onImport: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F1D8)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FriendlyCoachMascot(
                    modifier = Modifier.size(128.dp),
                    mood = CourseCoachDayStatus.PENDING,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Курс пока не установлен",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Установи встроенный seed-пакет или импортируй утверждённый course-package-v1 JSON.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onInstallSeed, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Установить seed")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Импортировать package")
                }
            }
        }
    }
}

@Composable
private fun TodayTab(
    uiState: CourseCoachUiState,
    onStartSession: (Int, String) -> Unit,
    onSkipDay: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val day = uiState.currentDay
            HeroCard(
                course = uiState.course!!,
                day = day,
                stats = uiState.stats,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Сделано",
                    value = "${uiState.stats.completedDays}/${uiState.course!!.totalDays}",
                    tint = Color(0xFF2FA74E),
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Streak",
                    value = uiState.stats.currentStreak.toString(),
                    tint = Color(0xFFF08C00),
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Рейт",
                    value = "${uiState.stats.completionRatePercent}%",
                    tint = Color(0xFF0C7C59),
                )
            }
        }

        val day = uiState.currentDay
        if (day == null) {
            item {
                InfoCard(
                    title = "План завершён",
                    body = "Все доступные дни уже закрыты. Можно открыть бонусные материалы во вкладке «Библиотека».",
                )
            }
        } else {
            item {
                DaySummaryCard(day = day)
            }
            day.morning?.let { session ->
                item {
                    SessionCard(
                        heading = "Утренний блок",
                        session = session,
                        onStart = { onStartSession(day.dayIndex, "morning") },
                    )
                }
            }
            day.evening?.let { session ->
                item {
                    SessionCard(
                        heading = "Вечерний блок",
                        session = session,
                        onStart = { onStartSession(day.dayIndex, "evening") },
                    )
                }
            }
            day.diaryPrompt?.let { prompt ->
                item {
                    DiaryCard(prompt = prompt)
                }
            }
            if (day.status != CourseCoachDayStatus.COMPLETED && day.status != CourseCoachDayStatus.SKIPPED) {
                item {
                    OutlinedButton(onClick = onSkipDay, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Flag, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Пометить день как пропущенный")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanTab(
    days: List<CourseCoachDayUi>,
    onStartSession: (Int, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(days, key = { it.dayIndex }) { day ->
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = statusContainerColor(day.status)),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = day.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusBadge(day.status)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = day.theme,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { day.completionFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2FA74E),
                        trackColor = Color.White.copy(alpha = 0.55f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    day.morning?.let { session ->
                        CompactSessionRow(
                            title = "Утро",
                            session = session,
                            onOpen = { onStartSession(day.dayIndex, "morning") },
                        )
                    }
                    day.evening?.let { session ->
                        Spacer(modifier = Modifier.height(8.dp))
                        CompactSessionRow(
                            title = "Вечер",
                            session = session,
                            onOpen = { onStartSession(day.dayIndex, "evening") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTab(stats: CourseCoachStatsUi) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Сессии",
                    value = "${stats.completedSessions}/${stats.totalRequiredSessions}",
                    subtitle = "обязательных блоков",
                    tint = Color(0xFF2FA74E),
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Время",
                    value = "${stats.totalMinutes}",
                    subtitle = "минут практики",
                    tint = Color(0xFF0C7C59),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Streak",
                    value = stats.currentStreak.toString(),
                    subtitle = "дней подряд",
                    tint = Color(0xFFF08C00),
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Пропуски",
                    value = stats.skippedDays.toString(),
                    subtitle = "отмеченных дней",
                    tint = Color(0xFF9E4F00),
                )
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Последние 7 дней",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    WeekBars(stats.weekBars)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "История",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (stats.history.isEmpty()) {
                        Text(
                            text = "Сохранённых сессий пока нет.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        stats.history.forEachIndexed { index, row ->
                            if (index > 0) Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        row.sessionTypeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    row.durationLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(0xFF0C7C59),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    uiState: CourseCoachUiState,
    onImport: () -> Unit,
    onReinstallSeed: () -> Unit,
    onResetProgress: () -> Unit,
    onToggleReminder: (String, Boolean) -> Unit,
    onShiftReminder: (String, Int) -> Unit,
    onOpenBonus: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Управление курсом",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.course!!.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailLine("Версия", uiState.course.version)
                    DetailLine("Review", uiState.course.reviewStatus)
                    DetailLine("Качество", uiState.course.contentQuality ?: "n/a")
                    DetailLine("Источник", uiState.course.sourceFolder)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onImport, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Импорт")
                        }
                        OutlinedButton(onClick = onReinstallSeed, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seed заново")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onResetProgress, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DoneAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сбросить прогресс")
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Напоминания",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    uiState.reminders.forEachIndexed { index, reminder ->
                        if (index > 0) {
                            Divider(modifier = Modifier.padding(vertical = 12.dp))
                        }
                        ReminderRow(
                            reminder = reminder,
                            onToggle = { onToggleReminder(reminder.sessionType, it) },
                            onEarlier = { onShiftReminder(reminder.sessionType, -30) },
                            onLater = { onShiftReminder(reminder.sessionType, 30) },
                        )
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Модули курса",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.modules.forEachIndexed { index, module ->
                        if (index > 0) Divider(modifier = Modifier.padding(vertical = 10.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(module.title, style = MaterialTheme.typography.titleMedium)
                                StatusPill(
                                    text = "${module.lessonCount} уроков",
                                    container = Color(0xFFE3F4EA),
                                    content = Color(0xFF0C7C59),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = module.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Бонусные материалы",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.bonusLessons.isEmpty()) {
                        Text(
                            text = "Бонусные уроки не обнаружены.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        uiState.bonusLessons.forEachIndexed { index, lesson ->
                            if (index > 0) Divider(modifier = Modifier.padding(vertical = 10.dp))
                            BonusLessonRow(
                                lesson = lesson,
                                onOpen = { onOpenBonus(lesson.lessonKey) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Установленные пакеты",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.installedCourses.forEachIndexed { index, course ->
                        if (index > 0) Divider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(course.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Версия ${course.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (course.isActive) {
                                StatusPill(
                                    text = "active",
                                    container = Color(0xFFEAF7E8),
                                    content = Color(0xFF2F6B1F),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    course: CourseCoachCourseUi,
    day: CourseCoachDayUi?,
    stats: CourseCoachStatsUi,
) {
    Card(
        shape = RoundedCornerShape(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFF7D96B), Color(0xFFBDE773))
                    )
                )
                .padding(20.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.20f),
                    radius = size.minDimension * 0.18f,
                    center = Offset(size.width * 0.88f, size.height * 0.18f),
                )
                drawCircle(
                    color = Color(0xFFFFF5CE).copy(alpha = 0.65f),
                    radius = size.minDimension * 0.24f,
                    center = Offset(size.width * 0.12f, size.height * 0.86f),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day?.title ?: "Курс готов",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF163300),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = day?.theme ?: course.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF254C12),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusPill(
                        text = if (day == null) "Все дни закрыты" else "Пройдено ${stats.completedDays} из ${course.totalDays}",
                        container = Color.White.copy(alpha = 0.72f),
                        content = Color(0xFF184600),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FriendlyCoachMascot(
                    modifier = Modifier.size(124.dp),
                    mood = day?.status ?: CourseCoachDayStatus.COMPLETED,
                )
            }
        }
    }
}

@Composable
private fun DaySummaryCard(day: CourseCoachDayUi) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(day.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                StatusBadge(day.status)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = day.theme,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { day.completionFraction },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2FA74E),
                trackColor = Color(0xFFDDE6D8),
            )
        }
    }
}

@Composable
private fun SessionCard(
    heading: String,
    session: CourseCoachSessionCardUi,
    onStart: () -> Unit,
) {
    val actionEnabled = !session.completed && !session.skipped
    val actionLabel = when {
        session.skipped -> "Пропущен"
        session.completed -> "Готово"
        session.sessionType == "morning" -> "Начать утро"
        else -> "Начать вечер"
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBF1)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (session.completed) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (session.completed) Color(0xFF2FA74E) else Color(0xFFF08C00),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                if (session.completed) {
                    StatusPill(
                        text = "done",
                        container = Color(0xFFE7F7EA),
                        content = Color(0xFF166534),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(session.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = session.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = "${session.exercises.size} упражн.",
                    container = Color(0xFFF4E7C8),
                    content = Color(0xFF7A4E00),
                )
                session.estimatedMinutes?.let {
                    StatusPill(
                        text = "$it мин",
                        container = Color(0xFFE3F4EA),
                        content = Color(0xFF0C7C59),
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onStart,
                enabled = actionEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (session.completed) Icons.Default.DoneAll else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun DiaryCard(prompt: CourseCoachDiaryUi) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F7FF)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(prompt.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "После практики быстро зафиксируй состояние и следующий шаг.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            prompt.fields.forEach { field ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("• ", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = field.replace("\n", " ").trim(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: CourseCoachReminderUi,
    onToggle: (Boolean) -> Unit,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFF08C00))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (reminder.sessionType == "morning") "Утреннее напоминание" else "Вечернее напоминание",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = reminder.timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeChip(label = "-30 мин", onClick = onEarlier)
            TimeChip(label = "+30 мин", onClick = onLater)
        }
    }
}

@Composable
private fun BonusLessonRow(
    lesson: CourseCoachBonusLessonUi,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(lesson.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lesson.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = lesson.sourceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(onClick = onOpen) {
            Text("Открыть")
        }
    }
}

@Composable
private fun CompactSessionRow(
    title: String,
    session: CourseCoachSessionCardUi,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !session.completed && !session.skipped, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(session.title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = session.estimatedMinutes?.let { "$it мин" } ?: "без оценки",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        StatusBadge(
            when {
                session.skipped -> CourseCoachDayStatus.SKIPPED
                session.completed -> CourseCoachDayStatus.COMPLETED
                else -> CourseCoachDayStatus.PENDING
            }
        )
    }
}

@Composable
private fun WeekBars(items: List<CourseCoachWeekBarUi>) {
    val maxMinutes = items.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEach { item ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (item.minutes == 0) "0" else item.minutes.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((32 + (128f * item.minutes / maxMinutes)).dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF9CE863), Color(0xFF2FA74E))
                            ),
                            shape = RoundedCornerShape(18.dp),
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FriendlyCoachMascot(
    modifier: Modifier = Modifier,
    mood: CourseCoachDayStatus,
) {
    val infinite = rememberInfiniteTransition(label = "mascot")
    val bounce = infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )
    val blink = infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blink",
    )

    val bodyColor = when (mood) {
        CourseCoachDayStatus.COMPLETED -> Color(0xFF58CC52)
        CourseCoachDayStatus.IN_PROGRESS -> Color(0xFFF0B429)
        CourseCoachDayStatus.SKIPPED -> Color(0xFFA58F61)
        CourseCoachDayStatus.PENDING -> Color(0xFF7ED957)
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.24f + bounce.value
        val bodyHeight = h * 0.60f
        val earRadius = w * 0.11f

        drawCircle(
            color = Color.Black.copy(alpha = 0.12f),
            radius = w * 0.22f,
            center = Offset(w * 0.5f, h * 0.92f),
        )
        drawCircle(color = bodyColor, radius = earRadius, center = Offset(w * 0.32f, bodyTop))
        drawCircle(color = bodyColor, radius = earRadius, center = Offset(w * 0.68f, bodyTop))
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(w * 0.16f, bodyTop),
            size = Size(w * 0.68f, bodyHeight),
            cornerRadius = CornerRadius(w * 0.24f, w * 0.24f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.26f, bodyTop + h * 0.09f),
            size = Size(w * 0.18f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        )

        val eyeWidth = w * 0.08f
        val eyeHeight = h * 0.08f * blink.value
        drawRoundRect(
            color = Color(0xFF173600),
            topLeft = Offset(w * 0.34f, bodyTop + h * 0.22f),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(eyeWidth, eyeWidth),
        )
        drawRoundRect(
            color = Color(0xFF173600),
            topLeft = Offset(w * 0.58f, bodyTop + h * 0.22f),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(eyeWidth, eyeWidth),
        )

        val mouthPath = Path().apply {
            val mouthY = bodyTop + h * 0.41f
            moveTo(w * 0.36f, mouthY)
            when (mood) {
                CourseCoachDayStatus.COMPLETED -> quadraticBezierTo(w * 0.50f, mouthY + h * 0.12f, w * 0.64f, mouthY)
                CourseCoachDayStatus.SKIPPED -> quadraticBezierTo(w * 0.50f, mouthY - h * 0.06f, w * 0.64f, mouthY)
                else -> quadraticBezierTo(w * 0.50f, mouthY + h * 0.05f, w * 0.64f, mouthY)
            }
        }
        drawPath(
            path = mouthPath,
            color = Color(0xFF173600),
            style = Stroke(width = w * 0.035f, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseSessionDialog(
    session: CourseCoachSessionRunnerUi,
    onDismiss: () -> Unit,
    onToggleExercise: (Int) -> Unit,
    onComplete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(session.title) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Button(
                                onClick = onComplete,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (session.tracksProgress) "Завершить блок" else "Закрыть бонус")
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Card(
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F1C9)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (session.sessionType == "morning") "Утренний проход" else if (session.sessionType == "evening") "Вечерний проход" else "Бонусный проход",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF7A4E00),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = session.summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF5D4B00),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    session.estimatedMinutes?.let {
                                        StatusPill(
                                            text = "$it мин",
                                            container = Color.White.copy(alpha = 0.72f),
                                            content = Color(0xFF5D4B00),
                                        )
                                    }
                                    StatusPill(
                                        text = "${session.exercises.size} шагов",
                                        container = Color.White.copy(alpha = 0.72f),
                                        content = Color(0xFF5D4B00),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            FriendlyCoachMascot(
                                modifier = Modifier.size(110.dp),
                                mood = CourseCoachDayStatus.IN_PROGRESS,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    session.exercises.forEachIndexed { index, exercise ->
                        SessionExerciseCard(
                            exercise = exercise,
                            index = index,
                            onToggle = { onToggleExercise(index) },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionExerciseCard(
    exercise: CourseCoachExerciseRunnerUi,
    index: Int,
    onToggle: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (exercise.completed) Color(0xFFE9F7EA) else Color(0xFFFFFBF2),
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = "${index + 1}",
                    container = if (exercise.completed) Color(0xFF2FA74E) else Color(0xFFF0B429),
                    content = Color.White,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = exercise.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (exercise.completed) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2FA74E))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = exercise.instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.durationSec?.let {
                    StatusPill(
                        text = "${it / 60} мин",
                        container = Color(0xFFEAF1FF),
                        content = Color(0xFF2855A3),
                    )
                }
                exercise.repetitions?.let {
                    StatusPill(
                        text = "$it повторов",
                        container = Color(0xFFF4E7C8),
                        content = Color(0xFF7A4E00),
                    )
                }
                exercise.restSec?.let {
                    StatusPill(
                        text = "пауза $it c",
                        container = Color(0xFFE3F4EA),
                        content = Color(0xFF0C7C59),
                    )
                }
            }
            exercise.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    if (exercise.completed) Icons.Default.DoneAll else Icons.Default.CheckCircle,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (exercise.completed) "Снять отметку" else "Отметить как сделано")
            }
        }
    }
}

@Composable
private fun StatusBadge(status: CourseCoachDayStatus) {
    when (status) {
        CourseCoachDayStatus.COMPLETED -> StatusPill("done", Color(0xFFE7F7EA), Color(0xFF166534))
        CourseCoachDayStatus.IN_PROGRESS -> StatusPill("in progress", Color(0xFFFFF2CF), Color(0xFF9A6700))
        CourseCoachDayStatus.PENDING -> StatusPill("next", Color(0xFFE9F0FF), Color(0xFF2855A3))
        CourseCoachDayStatus.SKIPPED -> StatusPill("skipped", Color(0xFFF1E5D8), Color(0xFF8A5C2E))
    }
}

@Composable
private fun StatusPill(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun MetricPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    tint: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    tint: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = tint)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimeChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFF4F0E1),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF7A4E00),
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun statusContainerColor(status: CourseCoachDayStatus): Color = when (status) {
    CourseCoachDayStatus.COMPLETED -> Color(0xFFEAF7E8)
    CourseCoachDayStatus.IN_PROGRESS -> Color(0xFFFFF4D7)
    CourseCoachDayStatus.PENDING -> Color(0xFFF4F7FB)
    CourseCoachDayStatus.SKIPPED -> Color(0xFFF3EAE0)
}
