package com.soll.presentation.screens.tools.coursecoach

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
    PROGRAM("Программа"),
    PROGRESS("Прогресс"),
}

private val CourseCoachCardShape = RoundedCornerShape(24.dp)
private val CourseCoachHeroShape = RoundedCornerShape(30.dp)
private val CourseCoachPillShape = RoundedCornerShape(999.dp)

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

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    uiState.activeSession?.let { session ->
        CourseSessionDialog(
            session = session,
            onDismiss = viewModel::dismissSession,
            onTogglePlayback = viewModel::toggleRunnerPlayback,
            onAdvance = viewModel::advanceRunnerStep,
            onRewind = viewModel::rewindRunnerStep,
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
                title = {
                    Text(
                        text = uiState.course?.title ?: "Курс",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                )
            } else {
                when (selectedTab) {
                    CourseCoachTab.TODAY -> TodayTab(
                        uiState = uiState,
                        onStartSession = viewModel::startSession,
                        onOpenSupport = viewModel::openSupportBlock,
                        onSkipDay = viewModel::skipCurrentDay,
                    )
                    CourseCoachTab.PROGRAM -> PlanTab(
                        days = uiState.days,
                        onStartSession = viewModel::startSession,
                        onOpenSupport = viewModel::openSupportBlock,
                    )
                    CourseCoachTab.PROGRESS -> StatsTab(
                        stats = uiState.stats,
                        reminders = uiState.reminders,
                        onResetProgress = { showResetDialog = true },
                        onToggleReminder = viewModel::toggleReminder,
                        onShiftReminder = viewModel::shiftReminder,
                        onOpenBonus = viewModel::openBonusLesson,
                        uiState = uiState,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCourseState(
    onInstallSeed: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = CourseCoachCardShape,
            colors = accentCardColors(),
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
                    text = "Во встроенном курсе уже есть готовый утренний и вечерний ритм. Подключи базовый курс и начни практику.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onInstallSeed, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Подключить базовый курс")
                }
            }
        }
    }
}

@Composable
private fun TodayTab(
    uiState: CourseCoachUiState,
    onStartSession: (Int, String) -> Unit,
    onOpenSupport: (Int, String) -> Unit,
    onSkipDay: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = courseTabContentPadding(),
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
                    tint = MaterialTheme.colorScheme.primary,
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Серия",
                    value = uiState.stats.currentStreak.toString(),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Прогресс",
                    value = "${uiState.stats.completionRatePercent}%",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        val day = uiState.currentDay
        if (day == null) {
            item {
                InfoCard(
                    title = "План завершён",
                    body = "Все основные дни уже закрыты. Дальше можно поддерживать ритм через дополнительные практики.",
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
            if (day.supportBlocks.isNotEmpty()) {
                item {
                    DaySupportCard(
                        sessions = day.supportBlocks,
                        onOpenSupport = { sessionKey -> onOpenSupport(day.dayIndex, sessionKey) },
                    )
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
    onOpenSupport: (Int, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = courseTabContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(days, key = { it.dayIndex }) { day ->
            Card(
                shape = CourseCoachCardShape,
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
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
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
                    if (day.supportBlocks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Поддержка по состоянию",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        day.supportBlocks.forEach { support ->
                            Spacer(modifier = Modifier.height(6.dp))
                            SupportSessionRow(
                                session = support,
                                onOpen = {
                                    val sessionKey = support.sessionKey ?: return@SupportSessionRow
                                    onOpenSupport(day.dayIndex, sessionKey)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsTab(
    stats: CourseCoachStatsUi,
    reminders: List<CourseCoachReminderUi>,
    onResetProgress: () -> Unit,
    onToggleReminder: (String, Boolean) -> Unit,
    onShiftReminder: (String, Int) -> Unit,
    onOpenBonus: (String) -> Unit,
    uiState: CourseCoachUiState,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = courseTabContentPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Сессии",
                    value = "${stats.completedSessions}/${stats.totalRequiredSessions}",
                    subtitle = "обязательных блоков",
                    tint = MaterialTheme.colorScheme.primary,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Время",
                    value = "${stats.totalMinutes}",
                    subtitle = "минут практики",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Серия",
                    value = stats.currentStreak.toString(),
                    subtitle = "дней подряд",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Пропуски",
                    value = stats.skippedDays.toString(),
                    subtitle = "отмеченных дней",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
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
            Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Напоминания",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    reminders.forEachIndexed { index, reminder ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
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
            Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "О курсе",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = uiState.course?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    DetailLine("Версия", uiState.course?.version ?: "-")
                    DetailLine("Дней в плане", uiState.course?.totalDays?.toString() ?: "-")
                    uiState.course?.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ориентиры",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        notes.forEach { note ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("• ", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onResetProgress, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DoneAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сбросить прогресс")
                    }
                }
            }
        }
        item {
            Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
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
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (uiState.bonusLessons.isNotEmpty()) {
            item {
                Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Поддерживающие практики",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Это не обязательные блоки дня. Используй их точечно, когда нужно снять напряжение или мягко поддержать самочувствие.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        uiState.bonusLessons.forEachIndexed { index, lesson ->
                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                            BonusLessonRow(
                                lesson = lesson,
                                onOpen = { onOpenBonus(lesson.lessonKey) },
                            )
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
    val heroOrbPrimary = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f)
    val heroOrbSecondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    val heroPillContainer = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)

    Card(
        shape = CourseCoachHeroShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
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
                    color = heroOrbPrimary,
                    radius = size.minDimension * 0.18f,
                    center = Offset(size.width * 0.88f, size.height * 0.18f),
                )
                drawCircle(
                    color = heroOrbSecondary,
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = day?.theme ?: course.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusPill(
                        text = if (day == null) "Все дни закрыты" else "Пройдено ${stats.completedDays} из ${course.totalDays}",
                        container = heroPillContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (day != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            day.morning?.let {
                                StatusPill(
                                    text = if (it.completed) "утро готово" else "утро впереди",
                                    container = heroPillContainer,
                                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            day.evening?.let {
                                StatusPill(
                                    text = if (it.completed) "вечер готов" else "вечер впереди",
                                    container = heroPillContainer,
                                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        if (day.supportBlocks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            StatusPill(
                                text = "Поддержка: ${formatSupportCount(day.supportBlocks.size)}",
                                container = heroPillContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
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
    Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                day.morning?.let {
                    StatusPill(
                        text = if (it.completed) "Утро закрыто" else "Утро в плане",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                day.evening?.let {
                    StatusPill(
                        text = if (it.completed) "Вечер закрыт" else "Вечер в плане",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (day.supportBlocks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Есть ${formatSupportCount(day.supportBlocks.size)} по состоянию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { day.completionFraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.18f),
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
        shape = CourseCoachCardShape,
        colors = warmCardColors(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (session.completed) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = when {
                        session.completed -> MaterialTheme.colorScheme.primary
                        session.skipped -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                if (session.completed || session.skipped) {
                    StatusPill(
                        text = if (session.completed) "готово" else "пропущено",
                        container = badgeContainerColor(
                            if (session.completed) CourseCoachDayStatus.COMPLETED else CourseCoachDayStatus.SKIPPED,
                        ),
                        content = badgeContentColor(
                            if (session.completed) CourseCoachDayStatus.COMPLETED else CourseCoachDayStatus.SKIPPED,
                        ),
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
            session.exercises.takeIf { it.isNotEmpty() }?.let { steps ->
                Spacer(modifier = Modifier.height(12.dp))
                SessionPreviewList(steps = steps)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                session.focusLabel?.takeIf { it.isNotBlank() }?.let {
                    StatusPill(
                        text = it,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                StatusPill(
                    text = formatPracticeCount(session.exercises.size),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                session.estimatedMinutes?.let {
                    StatusPill(
                        text = "$it мин",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
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
        shape = CourseCoachCardShape,
        colors = coolCardColors(),
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
private fun DaySupportCard(
    sessions: List<CourseCoachSessionCardUi>,
    onOpenSupport: (String) -> Unit,
) {
    Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Поддержка по состоянию",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Эти короткие практики не обязательны для дня, но помогают поддержать курс, если нужна мягкая разгрузка.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                SupportSessionRow(
                    session = session,
                    onOpen = {
                        val sessionKey = session.sessionKey ?: return@SupportSessionRow
                        onOpenSupport(sessionKey)
                    },
                )
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
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
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
private fun SupportSessionRow(
    session: CourseCoachSessionCardUi,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = session.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            session.focusLabel?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(6.dp))
                StatusPill(
                    text = it,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            session.estimatedMinutes?.let {
                StatusPill(
                    text = "$it мин",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onOpen) {
                Text("Начать")
            }
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
            lesson.focusLabel?.takeIf { it.isNotBlank() }?.let {
                StatusPill(
                    text = it,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedButton(onClick = onOpen) {
            Text("Начать")
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
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                                    MaterialTheme.colorScheme.primary,
                                )
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

    val bodyColor = mascotBodyColor(mood)
    val faceColor = MaterialTheme.colorScheme.onPrimaryContainer
    val shadowColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f)
    val highlightColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.24f + bounce.value
        val bodyHeight = h * 0.60f
        val earRadius = w * 0.11f

        drawCircle(
            color = shadowColor,
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
            color = highlightColor,
            topLeft = Offset(w * 0.26f, bodyTop + h * 0.09f),
            size = Size(w * 0.18f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        )

        val eyeWidth = w * 0.08f
        val eyeHeight = h * 0.08f * blink.value
        drawRoundRect(
            color = faceColor,
            topLeft = Offset(w * 0.34f, bodyTop + h * 0.22f),
            size = Size(eyeWidth, eyeHeight),
            cornerRadius = CornerRadius(eyeWidth, eyeWidth),
        )
        drawRoundRect(
            color = faceColor,
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
            color = faceColor,
            style = Stroke(width = w * 0.035f, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseSessionDialog(
    session: CourseCoachSessionRunnerUi,
    onDismiss: () -> Unit,
    onTogglePlayback: () -> Unit,
    onAdvance: () -> Unit,
    onRewind: () -> Unit,
    onComplete: () -> Unit,
) {
    val currentStep = session.steps.getOrNull(session.currentStepIndex) ?: return
    val stepNumber = session.currentStepIndex + 1
    val totalSteps = session.steps.size.coerceAtLeast(1)
    val progress = stepNumber.toFloat() / totalSteps.toFloat()
    val actionLabel = when {
        currentStep.kind == CourseCoachRunnerStepKind.REST -> "Пропустить паузу"
        currentStep.durationSec == null && stepNumber >= totalSteps -> "Завершить блок"
        currentStep.durationSec == null -> "Готово и дальше"
        stepNumber >= totalSteps -> "Завершить раньше"
        else -> "Дальше"
    }

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
                                .navigationBarsPadding()
                                .padding(16.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onRewind,
                                    enabled = session.currentStepIndex > 0,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Назад")
                                }
                                if (currentStep.durationSec != null) {
                                    OutlinedButton(
                                        onClick = onTogglePlayback,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (session.isPlaying) "Пауза" else "Продолжить")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(actionLabel)
                            }
                            if (!session.tracksProgress) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                                    Text("Закрыть дополнительную практику")
                                }
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
                        shape = CourseCoachHeroShape,
                        colors = accentCardColors(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (session.sessionType) {
                                        "morning" -> "Утренний блок"
                                        "evening" -> "Вечерний блок"
                                        else -> "Дополнительная практика"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = session.summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    session.estimatedMinutes?.let {
                                        StatusPill(
                                            text = "$it мин",
                                            container = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    StatusPill(
                                        text = "$stepNumber из $totalSteps",
                                        container = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    session.focusLabel?.takeIf { it.isNotBlank() }?.let {
                                        StatusPill(
                                            text = it,
                                            container = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            CoursePracticeOrb(
                                modifier = Modifier.size(116.dp),
                                remainingSec = session.remainingStepSec,
                                isPlaying = session.isPlaying,
                                isRestStep = currentStep.kind == CourseCoachRunnerStepKind.REST,
                                fallbackLabel = if (currentStep.durationSec == null) stepNumber.toString() else null,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SessionStepCard(
                        step = currentStep,
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        remainingSec = session.remainingStepSec,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    StepTimeline(session = session)
                }
            }
        }
    }
}

@Composable
private fun SessionStepCard(
    step: CourseCoachExerciseRunnerUi,
    stepNumber: Int,
    totalSteps: Int,
    remainingSec: Int?,
) {
    Card(
        shape = CourseCoachCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = "$stepNumber / $totalSteps",
                    container = if (step.kind == CourseCoachRunnerStepKind.REST) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    content = if (step.kind == CourseCoachRunnerStepKind.REST) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    },
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            step.purpose?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = step.instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                step.durationSec?.let {
                    StatusPill(
                        text = formatRunnerDuration(remainingSec ?: it),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                step.repetitions?.let {
                    StatusPill(
                        text = "$it повторов",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            step.hint?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(10.dp))
                StepInfoCard(
                    title = "Подсказка",
                    body = it,
                    container = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            step.safety?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                StepInfoCard(
                    title = "Важно",
                    body = it,
                    container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            step.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                StepInfoCard(
                    title = "Заметка",
                    body = it,
                    container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepTimeline(session: CourseCoachSessionRunnerUi) {
    Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Структура блока",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            session.steps.forEachIndexed { index, step ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                val isDone = index < session.currentStepIndex
                val isCurrent = index == session.currentStepIndex
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        isDone -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
                        else -> Color.Transparent
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        StatusPill(
                            text = "${index + 1}",
                            container = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isDone -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            content = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isDone -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = when {
                                    step.kind == CourseCoachRunnerStepKind.REST -> "Пауза и восстановление"
                                    step.durationSec != null -> "Таймер ${formatRunnerDuration(step.durationSec)}"
                                    step.repetitions != null -> "${step.repetitions} повторов"
                                    else -> "Шаг без таймера"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursePracticeOrb(
    modifier: Modifier = Modifier,
    remainingSec: Int?,
    isPlaying: Boolean,
    isRestStep: Boolean,
    fallbackLabel: String?,
) {
    val infinite = rememberInfiniteTransition(label = "course-practice-orb")
    val pulse = infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "course-practice-pulse",
    )
    val orbColor = if (isRestStep) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.34f
            drawCircle(
                color = orbColor.copy(alpha = 0.18f),
                radius = base * (if (isPlaying) pulse.value else 1f),
                center = center,
            )
            drawCircle(
                color = orbColor.copy(alpha = 0.35f),
                radius = base * 0.72f,
                center = center,
            )
            drawCircle(
                color = orbColor,
                radius = base * 0.46f,
                center = center,
            )
        }
        Text(
            text = remainingSec?.toString() ?: (fallbackLabel ?: "•"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SessionPreviewList(
    steps: List<CourseCoachExerciseUi>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Что внутри блока",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        steps.take(2).forEachIndexed { index, exercise ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    exercise.purpose?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (steps.size > 2) {
            Text(
                text = "Ещё ${steps.size - 2} ${formatRemainingStepsLabel(steps.size - 2)} внутри блока.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepInfoCard(
    title: String,
    body: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = container,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = content,
            )
        }
    }
}

@Composable
private fun StatusBadge(status: CourseCoachDayStatus) {
    when (status) {
        CourseCoachDayStatus.COMPLETED -> StatusPill("готово", badgeContainerColor(status), badgeContentColor(status))
        CourseCoachDayStatus.IN_PROGRESS -> StatusPill("в процессе", badgeContainerColor(status), badgeContentColor(status))
        CourseCoachDayStatus.PENDING -> StatusPill("дальше", badgeContainerColor(status), badgeContentColor(status))
        CourseCoachDayStatus.SKIPPED -> StatusPill("пропущено", badgeContainerColor(status), badgeContentColor(status))
    }
}

@Composable
private fun StatusPill(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = CourseCoachPillShape,
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
        shape = CourseCoachCardShape,
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
        shape = CourseCoachCardShape,
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
        shape = CourseCoachPillShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun formatRunnerDuration(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val minutes = safe / 60
    val seconds = safe % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "${seconds} c"
    }
}

private fun formatPracticeCount(count: Int): String {
    val tail = when {
        count % 10 == 1 && count % 100 != 11 -> "шаг"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "шага"
        else -> "шагов"
    }
    return "$count $tail"
}

private fun formatSupportCount(count: Int): String {
    val tail = when {
        count % 10 == 1 && count % 100 != 11 -> "блок"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "блока"
        else -> "блоков"
    }
    return "$count $tail"
}

private fun formatRemainingStepsLabel(count: Int): String {
    return when {
        count % 10 == 1 && count % 100 != 11 -> "шаг"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "шага"
        else -> "шагов"
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
    Card(shape = CourseCoachCardShape, colors = sectionCardColors()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun courseTabContentPadding(): PaddingValues {
    val navigationBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    return PaddingValues(
        start = 16.dp,
        top = 16.dp,
        end = 16.dp,
        bottom = 24.dp + navigationBottom,
    )
}

@Composable
private fun sectionCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))

@Composable
private fun warmCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f))

@Composable
private fun coolCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f))

@Composable
private fun accentCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))

@Composable
private fun badgeContainerColor(status: CourseCoachDayStatus): Color = when (status) {
    CourseCoachDayStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
    CourseCoachDayStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer
    CourseCoachDayStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    CourseCoachDayStatus.SKIPPED -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun badgeContentColor(status: CourseCoachDayStatus): Color = when (status) {
    CourseCoachDayStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
    CourseCoachDayStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onTertiaryContainer
    CourseCoachDayStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    CourseCoachDayStatus.SKIPPED -> MaterialTheme.colorScheme.onSecondaryContainer
}

@Composable
private fun statusContainerColor(status: CourseCoachDayStatus): Color = when (status) {
    CourseCoachDayStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
    CourseCoachDayStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.32f)
    CourseCoachDayStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    CourseCoachDayStatus.SKIPPED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
}

@Composable
private fun mascotBodyColor(status: CourseCoachDayStatus): Color = when (status) {
    CourseCoachDayStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    CourseCoachDayStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
    CourseCoachDayStatus.PENDING -> MaterialTheme.colorScheme.secondary
    CourseCoachDayStatus.SKIPPED -> MaterialTheme.colorScheme.outline
}
