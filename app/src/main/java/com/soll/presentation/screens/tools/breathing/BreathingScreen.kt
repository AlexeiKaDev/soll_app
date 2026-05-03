package com.soll.presentation.screens.tools.breathing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.breathing.BreathingHistoryRowUi
import com.soll.domain.breathing.BreathingPhase
import com.soll.domain.breathing.BreathingSessionState
import com.soll.domain.breathing.BreathingWeekDayStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingScreen(
    onBack: () -> Unit,
    viewModel: BreathingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showWarning by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    /** Как столбики графика — тот же primary, но легче для заливки круга на фоне. */
    val innerOrbFill = primary.copy(alpha = 0.48f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Дыхание") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showStats = true }) {
                        Icon(Icons.Default.BarChart, contentDescription = "Статистика и история")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Раунд сессии: ${uiState.round} из ${uiState.totalRounds}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = cyclesLine(uiState),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { overallSessionProgress(uiState) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                strokeCap = StrokeCap.Round,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val maxHalf = uiState.breathsPerRound * 2
                    val breathProgress =
                        if (uiState.phase == BreathingPhase.BREATHING && maxHalf > 0) {
                            (uiState.breathingHalfStep - 1).coerceAtLeast(0).toFloat() / maxHalf.toFloat()
                        } else {
                            0f
                        }

                    DuolingoStyleBreathingOrb(
                        phase = uiState.phase,
                        isInhalePhase = uiState.isInhalePhase,
                        breathingStepMs = uiState.breathingStepMs,
                        breathProgress = breathProgress.coerceIn(0f, 1f),
                        primaryColor = primary,
                        innerFillColor = innerOrbFill,
                        centerCycleNumber = if (uiState.phase == BreathingPhase.BREATHING) {
                            breathingCycleIndex(uiState.breathingHalfStep)
                        } else {
                            null
                        },
                        holdCountdownRemaining = if (uiState.phase == BreathingPhase.HOLD) {
                            uiState.holdCountdownRemaining
                        } else {
                            null
                        },
                        guidedHoldSeconds = uiState.holdGuidedSeconds.coerceAtLeast(1),
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = phaseHeadline(uiState.phase, uiState.isInhalePhase),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = phaseSubtitle(uiState),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )

                    if (uiState.phase == BreathingPhase.HOLD) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Удержано сейчас: ${uiState.holdSeconds} с · можно закончить раньше",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.finishHoldNow() }) {
                            Text("Завершить задержку")
                        }
                    }

                    if (uiState.phase == BreathingPhase.RECOVERY) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${uiState.recoverySecondsLeft} с",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (uiState.holdRecordsByRound.isNotEmpty()) {
                Text(
                    text = "Задержки (сек по раундам): ${uiState.holdRecordsByRound.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (uiState.phase != BreathingPhase.IDLE || uiState.sessionElapsedMillis > 0) {
                Text(
                    text = when {
                        uiState.phase == BreathingPhase.IDLE && uiState.sessionElapsedMillis == 0L -> ""
                        else -> "Время сессии: ${formatBreathingElapsed(uiState.sessionElapsedMillis)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showWarning = true },
                    enabled = !uiState.isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Старт")
                }
                OutlinedButton(
                    onClick = { viewModel.stopSession() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Стоп")
                }
            }
        }
    }

    if (showStats) {
        BreathingStatsDialog(
            weekDays = uiState.weekDayStats,
            history = uiState.sessionHistory,
            onDismiss = { showStats = false },
        )
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Важное предупреждение") },
            text = {
                Text(
                    "Не выполнять за рулём и в воде. Возможны головокружение и дискомфорт. " +
                        "При проблемах с сердцем/давлением — только после консультации врача."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWarning = false
                    viewModel.startSession()
                }) { Text("Понятно, начать") }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) { Text("Отмена") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreathingStatsDialog(
    weekDays: List<BreathingWeekDayStat>,
    history: List<BreathingHistoryRowUi>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Статистика") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                ) {
                    BreathingWeekChart(
                        days = weekDays,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    Text(
                        text = "История сессий",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (history.isEmpty()) {
                        Text(
                            text = "Пока нет сохранённых сессий.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        history.take(40).forEach { row ->
                            Text(
                                text = "${row.dateTimeLabel} · ${row.durationLabel} · " +
                                    if (row.completedFully) "полностью" else "остановлено",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Один цикл = вдох + выдох; полушаги 1–2 → 1, 3–4 → 2, … */
private fun breathingCycleIndex(breathingHalfStep: Int): Int? {
    if (breathingHalfStep <= 0) return null
    return (breathingHalfStep + 1) / 2
}

private fun cyclesLine(state: BreathingSessionState): String {
    val total = state.breathsPerRound.coerceAtLeast(1)
    return when (state.phase) {
        BreathingPhase.IDLE ->
            "Один раунд — это серия дыханий, задержка и восстановление. Всего раундов в сессии: ${state.totalRounds}."
        BreathingPhase.BREATHING -> {
            val c = breathingCycleIndex(state.breathingHalfStep)?.coerceIn(1, total) ?: 1
            "Цикл $c из $total в этом раунде."
        }
        BreathingPhase.HOLD ->
            "Циклы этого раунда завершены. Задержка после полного выдоха — отсчёт в круге (~${state.holdGuidedSeconds} с ориентира, можно закончить раньше)."
        BreathingPhase.RECOVERY ->
            "Раунд ${state.round}: восстановление перед следующим раундом или завершением."
        BreathingPhase.COMPLETE ->
            "Сессия из ${state.totalRounds} раундов завершена."
    }
}

private fun overallSessionProgress(state: BreathingSessionState): Float {
    val totalRounds = state.totalRounds.coerceAtLeast(1)
    val breathFrac = when (state.phase) {
        BreathingPhase.BREATHING ->
            if (state.breathsPerRound > 0) {
                val maxHalf = 2 * state.breathsPerRound
                (state.breathingHalfStep - 1).coerceAtLeast(0).toFloat() / maxHalf.toFloat()
            } else {
                0f
            }
        BreathingPhase.HOLD, BreathingPhase.RECOVERY -> 1f
        BreathingPhase.COMPLETE -> 1f
        BreathingPhase.IDLE -> 0f
    }.coerceIn(0f, 1f)
    val roundPart = (state.round - 1).coerceAtLeast(0) + breathFrac
    return (roundPart / totalRounds).coerceIn(0f, 1f)
}

@Composable
private fun phaseHeadline(phase: BreathingPhase, isInhale: Boolean): String {
    return when (phase) {
        BreathingPhase.IDLE -> "Готов?"
        BreathingPhase.BREATHING -> if (isInhale) "Вдох" else "Выдох"
        BreathingPhase.HOLD -> "Задержка"
        BreathingPhase.RECOVERY -> "Спокойно"
        BreathingPhase.COMPLETE -> "Готово"
    }
}

private fun formatBreathingElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).toInt().coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun phaseSubtitle(state: BreathingSessionState): String {
    return when (state.phase) {
        BreathingPhase.IDLE -> "Нажми «Старт» и следуй кругу."
        BreathingPhase.BREATHING -> state.instruction
        BreathingPhase.HOLD ->
            "Как в гипервентиляции перед задержкой: полностью выдохни и держи. Отсчёт ${state.holdGuidedSeconds} с — ориентир по технике, можно завершить досрочно."
        BreathingPhase.RECOVERY -> state.instruction
        BreathingPhase.COMPLETE -> "Сессия завершена. Отличная работа."
    }
}

private fun orbDigitStyle(base: TextStyle): TextStyle = base.copy(
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.28f),
        offset = Offset(1f, 1f),
        blurRadius = 5f,
    ),
)

/**
 * Орб: кольцо прогресса; по центру — номер цикла (вдох+выдох = 1) или отсчёт задержки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuolingoStyleBreathingOrb(
    phase: BreathingPhase,
    isInhalePhase: Boolean,
    breathingStepMs: Long,
    breathProgress: Float,
    primaryColor: Color,
    innerFillColor: Color,
    centerCycleNumber: Int? = null,
    holdCountdownRemaining: Int? = null,
    guidedHoldSeconds: Int = 30,
) {
    val targetScale = when (phase) {
        BreathingPhase.BREATHING -> if (isInhalePhase) 1f else 0.78f
        BreathingPhase.HOLD -> 0.66f
        BreathingPhase.RECOVERY -> 1.04f
        BreathingPhase.COMPLETE -> 1f
        BreathingPhase.IDLE -> 0.94f
    }
    val tweenMs = when (phase) {
        BreathingPhase.BREATHING -> breathingStepMs.toInt().coerceIn(400, 6000)
        BreathingPhase.RECOVERY -> 1000
        BreathingPhase.HOLD -> 850
        BreathingPhase.COMPLETE -> 500
        BreathingPhase.IDLE -> 2200
    }
    val orbScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(tweenMs, easing = FastOutSlowInEasing),
        label = "breathOrb"
    )

    val infinite = rememberInfiniteTransition(label = "idleBreath")
    val idlePulse by infinite.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.98f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idlePulse"
    )

    val drawScale = if (phase == BreathingPhase.IDLE) idlePulse else orbScale

    Box(
        modifier = Modifier.size(320.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minSide = size.minDimension
            val center = Offset(size.width / 2, size.height / 2)
            val r = minSide / 2 * 0.48f * drawScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f),
                        primaryColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = r * 2.4f
                ),
                radius = r * 2.05f,
                center = center
            )

            drawCircle(
                color = primaryColor.copy(alpha = 0.45f),
                radius = r,
                center = center,
                style = Stroke(width = minSide * 0.02f, cap = StrokeCap.Round)
            )

            drawCircle(
                color = innerFillColor,
                radius = r * 0.88f,
                center = center
            )
        }

        when (phase) {
            BreathingPhase.BREATHING -> {
                CircularProgressIndicator(
                    progress = { breathProgress },
                    modifier = Modifier
                        .size(296.dp)
                        .padding(4.dp),
                    strokeWidth = 12.dp,
                    color = primaryColor.copy(alpha = 0.9f),
                    trackColor = primaryColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )
            }
            BreathingPhase.HOLD -> {
                val remain = holdCountdownRemaining ?: 0
                val elapsedFrac =
                    if (guidedHoldSeconds > 0) {
                        ((guidedHoldSeconds - remain).coerceIn(0, guidedHoldSeconds).toFloat() /
                            guidedHoldSeconds.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                CircularProgressIndicator(
                    progress = { elapsedFrac },
                    modifier = Modifier
                        .size(296.dp)
                        .padding(4.dp),
                    strokeWidth = 12.dp,
                    color = primaryColor.copy(alpha = 0.9f),
                    trackColor = primaryColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )
            }
            else -> {}
        }

        val orbDigitColor = Color.White.copy(alpha = 0.82f)
        holdCountdownRemaining?.let { remain ->
            Text(
                text = remain.coerceAtLeast(0).toString(),
                style = orbDigitStyle(MaterialTheme.typography.displayLarge),
                fontWeight = FontWeight.Bold,
                color = orbDigitColor,
                textAlign = TextAlign.Center,
            )
        } ?: centerCycleNumber?.let { cycle ->
            Text(
                text = cycle.toString(),
                style = orbDigitStyle(MaterialTheme.typography.displayLarge),
                fontWeight = FontWeight.Bold,
                color = orbDigitColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
