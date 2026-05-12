package com.soll.presentation.screens.tools.coursecoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.course.CoursePackageModel
import com.soll.data.course.CoursePackageDiaryPrompt
import com.soll.data.course.CoursePackageExercise
import com.soll.data.course.CoursePackageSessionPayload
import com.soll.data.local.entity.CourseDayPlanEntity
import com.soll.data.local.entity.CourseDayProgressEntity
import com.soll.data.local.entity.CourseEntity
import com.soll.data.local.entity.CourseReminderEntity
import com.soll.data.local.entity.CourseSessionLogEntity
import com.soll.data.repository.CourseProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class CourseCoachDayStatus {
    COMPLETED,
    IN_PROGRESS,
    PENDING,
    SKIPPED,
}

enum class CourseCoachTimerMode {
    COUNTDOWN,
    MANUAL,
}

enum class CourseCoachRunnerStepKind {
    PRACTICE,
    REST,
}

data class CourseCoachUiState(
    val isLoading: Boolean = true,
    val course: CourseCoachCourseUi? = null,
    val days: List<CourseCoachDayUi> = emptyList(),
    val currentDay: CourseCoachDayUi? = null,
    val reminders: List<CourseCoachReminderUi> = emptyList(),
    val bonusLessons: List<CourseCoachBonusLessonUi> = emptyList(),
    val stats: CourseCoachStatsUi = CourseCoachStatsUi(),
    val activeSession: CourseCoachSessionRunnerUi? = null,
)

data class CourseCoachCourseUi(
    val id: Long,
    val title: String,
    val description: String,
    val notes: List<String>,
    val version: String,
    val mascotStyle: String?,
    val totalDays: Int,
)

data class CourseCoachDayUi(
    val dayIndex: Int,
    val title: String,
    val theme: String,
    val status: CourseCoachDayStatus,
    val completionFraction: Float,
    val morning: CourseCoachSessionCardUi?,
    val evening: CourseCoachSessionCardUi?,
    val supportBlocks: List<CourseCoachSessionCardUi>,
    val diaryPrompt: CourseCoachDiaryUi?,
)

data class CourseCoachSessionCardUi(
    val sessionKey: String?,
    val sessionType: String,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int?,
    val focusLabel: String?,
    val exercises: List<CourseCoachExerciseUi>,
    val completed: Boolean,
    val skipped: Boolean,
)

data class CourseCoachExerciseUi(
    val title: String,
    val purpose: String?,
    val instructions: String,
    val hint: String?,
    val safety: String?,
    val durationSec: Int?,
    val repetitions: Int?,
    val restSec: Int?,
    val notes: String?,
    val timerMode: CourseCoachTimerMode,
    val showOrb: Boolean,
)

data class CourseCoachDiaryUi(
    val title: String,
    val fields: List<String>,
)

data class CourseCoachReminderUi(
    val sessionType: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val timeLabel: String,
)

data class CourseCoachBonusLessonUi(
    val lessonKey: String,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int?,
    val focusLabel: String?,
    val exercises: List<CourseCoachExerciseUi>,
)

data class CourseCoachStatsUi(
    val completedDays: Int = 0,
    val skippedDays: Int = 0,
    val totalRequiredSessions: Int = 0,
    val completedSessions: Int = 0,
    val completionRatePercent: Int = 0,
    val currentStreak: Int = 0,
    val totalMinutes: Int = 0,
    val weekBars: List<CourseCoachWeekBarUi> = emptyList(),
    val history: List<CourseCoachHistoryUi> = emptyList(),
)

data class CourseCoachWeekBarUi(
    val label: String,
    val minutes: Int,
)

data class CourseCoachHistoryUi(
    val label: String,
    val durationLabel: String,
    val sessionTypeLabel: String,
)

data class CourseCoachSessionRunnerUi(
    val courseId: Long,
    val dayIndex: Int,
    val sessionType: String,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int?,
    val focusLabel: String?,
    val steps: List<CourseCoachExerciseRunnerUi>,
    val currentStepIndex: Int,
    val remainingStepSec: Int?,
    val isPlaying: Boolean,
    val startedAtMillis: Long,
    val tracksProgress: Boolean,
)

data class CourseCoachExerciseRunnerUi(
    val id: String,
    val kind: CourseCoachRunnerStepKind,
    val title: String,
    val purpose: String?,
    val instructions: String,
    val hint: String?,
    val safety: String?,
    val durationSec: Int?,
    val repetitions: Int?,
    val notes: String?,
    val showOrb: Boolean,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CourseCoachViewModel @Inject constructor(
    private val repository: CourseProgramRepository,
) : ViewModel() {

    private data class CourseCoachPrimaryData(
        val course: CourseEntity?,
        val packageModel: CoursePackageModel?,
    )

    private data class CourseCoachSecondaryData(
        val dayPlans: List<CourseDayPlanEntity>,
        val dayProgress: List<CourseDayProgressEntity>,
        val reminders: List<CourseReminderEntity>,
        val sessionLogs: List<CourseSessionLogEntity>,
    )

    private val eventsInternal = MutableSharedFlow<String>()
    val events = eventsInternal.asSharedFlow()

    private val activeSession = MutableStateFlow<CourseCoachSessionRunnerUi?>(null)
    private var runnerJob: Job? = null

    private val activeCourse = repository.observeActiveCourse()
    private val activePackage = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeStoredCoursePackage(it.packageFileName) } ?: flowOf(null)
    }
    private val dayPlans = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeDayPlans(it.id) } ?: flowOf(emptyList())
    }
    private val dayProgress = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeDayProgress(it.id) } ?: flowOf(emptyList())
    }
    private val reminders = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeReminders(it.id) } ?: flowOf(emptyList())
    }
    private val sessionLogs = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeSessionLogs(it.id) } ?: flowOf(emptyList())
    }

    private val primaryData = combine(
        activeCourse,
        activePackage,
    ) { course, packageModel ->
        CourseCoachPrimaryData(
            course = course,
            packageModel = packageModel,
        )
    }

    private val secondaryData = combine(
        dayPlans,
        dayProgress,
        reminders,
        sessionLogs,
    ) { dayPlans, dayProgress, reminders, sessionLogs ->
        CourseCoachSecondaryData(
            dayPlans = dayPlans,
            dayProgress = dayProgress,
            reminders = reminders,
            sessionLogs = sessionLogs,
        )
    }

    val uiState = combine(
        primaryData,
        secondaryData,
        activeSession,
    ) { primary, secondary, activeSession ->
        buildUiState(
            course = primary.course,
            packageModel = primary.packageModel,
            dayPlans = secondary.dayPlans,
            dayProgress = secondary.dayProgress,
            reminders = secondary.reminders,
            sessionLogs = secondary.sessionLogs,
            activeSession = activeSession,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CourseCoachUiState(),
    )

    init {
        viewModelScope.launch {
            runCatching { repository.ensureSeedCourseInstalled() }
                .onFailure { eventsInternal.emit("Не удалось установить встроенный курс: ${it.message}") }
        }
    }

    fun startSession(dayIndex: Int, sessionType: String) {
        val state = uiState.value
        val course = state.course ?: return
        val day = state.days.firstOrNull { it.dayIndex == dayIndex } ?: return
        val session = when (sessionType) {
            "morning" -> day.morning
            else -> day.evening
        } ?: return
        activateSession(
            courseId = course.id,
            dayIndex = dayIndex,
            sessionType = sessionType,
            session = session,
            tracksProgress = true,
        )
    }

    fun openSupportBlock(dayIndex: Int, sessionKey: String) {
        val state = uiState.value
        val course = state.course ?: return
        val day = state.days.firstOrNull { it.dayIndex == dayIndex } ?: return
        val session = day.supportBlocks.firstOrNull { it.sessionKey == sessionKey } ?: return
        activateSession(
            courseId = course.id,
            dayIndex = dayIndex,
            sessionType = "support",
            session = session,
            tracksProgress = false,
        )
    }

    fun openBonusLesson(lessonKey: String) {
        val bonus = uiState.value.bonusLessons.firstOrNull { it.lessonKey == lessonKey } ?: return
        val courseId = uiState.value.course?.id ?: return
        val supportCard = CourseCoachSessionCardUi(
            sessionKey = bonus.lessonKey,
            sessionType = "support",
            title = bonus.title,
            summary = bonus.summary,
            estimatedMinutes = bonus.estimatedMinutes,
            focusLabel = bonus.focusLabel ?: "Поддержка по состоянию",
            exercises = bonus.exercises,
            completed = false,
            skipped = false,
        )
        activateSession(
            courseId = courseId,
            dayIndex = -1,
            sessionType = "bonus",
            session = supportCard,
            tracksProgress = false,
        )
    }

    fun dismissSession() {
        runnerJob?.cancel()
        runnerJob = null
        activeSession.value = null
    }

    fun toggleRunnerPlayback() {
        activeSession.update { session ->
            session?.copy(isPlaying = !session.isPlaying)
        }
        syncRunnerTicker()
    }

    fun advanceRunnerStep() {
        val session = activeSession.value ?: return
        advanceRunnerInternal(session)
    }

    fun rewindRunnerStep() {
        val session = activeSession.value ?: return
        if (session.currentStepIndex <= 0) return
        val previousIndex = session.currentStepIndex - 1
        val previousStep = session.steps.getOrNull(previousIndex) ?: return
        activeSession.value = session.copy(
            currentStepIndex = previousIndex,
            remainingStepSec = previousStep.durationSec,
            isPlaying = previousStep.durationSec != null,
        )
        syncRunnerTicker()
    }

    fun completeActiveSession() {
        val session = activeSession.value ?: return
        runnerJob?.cancel()
        runnerJob = null
        val completedCount = session.steps.count { it.kind == CourseCoachRunnerStepKind.PRACTICE }
        val totalExercises = completedCount.coerceAtLeast(1)
        viewModelScope.launch {
            val endedAt = System.currentTimeMillis()
            if (session.tracksProgress) {
                repository.markSessionCompleted(
                    courseId = session.courseId,
                    dayIndex = session.dayIndex,
                    sessionType = session.sessionType,
                    completedExercises = completedCount,
                    totalExercises = totalExercises,
                    startedAtMillis = session.startedAtMillis,
                    endedAtMillis = endedAt,
                )
                val label = if (session.sessionType == "morning") "Утренний" else "Вечерний"
                eventsInternal.emit("$label блок завершён.")
            } else {
                eventsInternal.emit("Дополнительная практика завершена.")
            }
            activeSession.value = null
        }
    }

    fun skipCurrentDay() {
        val state = uiState.value
        val courseId = state.course?.id ?: return
        val dayIndex = state.currentDay?.dayIndex ?: return

        viewModelScope.launch {
            repository.skipDay(courseId, dayIndex)
            eventsInternal.emit("День $dayIndex помечен как пропущенный.")
        }
    }

    fun toggleReminder(sessionType: String, enabled: Boolean) {
        val state = uiState.value
        val courseId = state.course?.id ?: return
        val reminder = state.reminders.firstOrNull { it.sessionType == sessionType } ?: return

        viewModelScope.launch {
            repository.updateReminder(courseId, sessionType, enabled, reminder.hour, reminder.minute)
        }
    }

    fun shiftReminder(sessionType: String, deltaMinutes: Int) {
        val state = uiState.value
        val courseId = state.course?.id ?: return
        val reminder = state.reminders.firstOrNull { it.sessionType == sessionType } ?: return
        val totalMinutes = ((reminder.hour * 60) + reminder.minute + deltaMinutes).floorToDay()

        viewModelScope.launch {
            repository.updateReminder(
                courseId = courseId,
                sessionType = sessionType,
                enabled = reminder.enabled,
                hour = totalMinutes / 60,
                minute = totalMinutes % 60,
            )
        }
    }

    fun reinstallSeedCourse() {
        viewModelScope.launch {
            runCatching { repository.installBundledSeedPackage() }
                .onSuccess { eventsInternal.emit("Базовый курс обновлён.") }
                .onFailure { eventsInternal.emit("Не удалось обновить базовый курс: ${it.message}") }
        }
    }

    fun resetProgress() {
        val courseId = uiState.value.course?.id ?: return
        viewModelScope.launch {
            repository.resetProgress(courseId)
            runnerJob?.cancel()
            runnerJob = null
            activeSession.value = null
            eventsInternal.emit("Прогресс курса сброшен.")
        }
    }

    private fun syncRunnerTicker() {
        runnerJob?.cancel()
        val session = activeSession.value ?: return
        val step = session.steps.getOrNull(session.currentStepIndex) ?: return
        val remaining = session.remainingStepSec
        if (!session.isPlaying || step.durationSec == null || remaining == null) return
        runnerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = activeSession.value ?: break
                if (!current.isPlaying) break
                if (current.currentStepIndex >= current.steps.size) break
                val currentStep = current.steps[current.currentStepIndex]
                val currentRemaining = current.remainingStepSec
                if (currentStep.durationSec == null || currentRemaining == null) break
                if (currentRemaining <= 1) {
                    advanceRunnerInternal(current)
                    break
                }
                activeSession.value = current.copy(remainingStepSec = currentRemaining - 1)
            }
        }
    }

    private fun advanceRunnerInternal(session: CourseCoachSessionRunnerUi) {
        val nextIndex = session.currentStepIndex + 1
        if (nextIndex >= session.steps.size) {
            completeActiveSession()
            return
        }
        val nextStep = session.steps[nextIndex]
        activeSession.value = session.copy(
            currentStepIndex = nextIndex,
            remainingStepSec = nextStep.durationSec,
            isPlaying = nextStep.durationSec != null,
        )
        syncRunnerTicker()
    }

    private fun activateSession(
        courseId: Long,
        dayIndex: Int,
        sessionType: String,
        session: CourseCoachSessionCardUi,
        tracksProgress: Boolean,
    ) {
        val steps = session.toRunnerSteps()
        if (steps.isEmpty()) {
            viewModelScope.launch {
                eventsInternal.emit("В этом блоке пока нет подготовленных шагов.")
            }
            return
        }
        val firstTimedStep = steps.firstOrNull()?.durationSec
        activeSession.value = CourseCoachSessionRunnerUi(
            courseId = courseId,
            dayIndex = dayIndex,
            sessionType = sessionType,
            title = session.title,
            summary = session.summary,
            estimatedMinutes = session.estimatedMinutes,
            focusLabel = session.focusLabel,
            steps = steps,
            currentStepIndex = 0,
            remainingStepSec = firstTimedStep,
            isPlaying = firstTimedStep != null,
            startedAtMillis = System.currentTimeMillis(),
            tracksProgress = tracksProgress,
        )
        syncRunnerTicker()
    }

    private fun buildUiState(
        course: CourseEntity?,
        packageModel: CoursePackageModel?,
        dayPlans: List<CourseDayPlanEntity>,
        dayProgress: List<CourseDayProgressEntity>,
        reminders: List<CourseReminderEntity>,
        sessionLogs: List<CourseSessionLogEntity>,
        activeSession: CourseCoachSessionRunnerUi?,
    ): CourseCoachUiState {
        if (course == null) {
            return CourseCoachUiState(
                isLoading = false,
                activeSession = activeSession,
            )
        }

        val progressByDay = dayProgress.associateBy { it.dayIndex }
        val days = dayPlans.map { plan ->
            val progress = progressByDay[plan.dayIndex]
            val morning = repository.decodeSessionPayload(plan.morningPayloadJson)
                ?.toSessionCard("morning", progress)
            val evening = repository.decodeSessionPayload(plan.eveningPayloadJson)
                ?.toSessionCard("evening", progress)
            val supportBlocks = packageModel?.day_plans
                ?.firstOrNull { it.day_index == plan.dayIndex }
                ?.optional_blocks
                ?.map { it.toSessionCard("support", progress = null) }
                .orEmpty()
            val diary = repository.decodeDiaryPrompt(plan.diaryPromptJson)?.toDiaryUi()
            val status = resolveDayStatus(morning, evening, progress)
            val completedParts = listOfNotNull(morning, evening).count { it.completed }
            val totalParts = listOfNotNull(morning, evening).size.coerceAtLeast(1)
            CourseCoachDayUi(
                dayIndex = plan.dayIndex,
                title = plan.title,
                theme = plan.theme,
                status = status,
                completionFraction = completedParts.toFloat() / totalParts.toFloat(),
                morning = morning,
                evening = evening,
                supportBlocks = supportBlocks,
                diaryPrompt = diary,
            )
        }
        val currentDay = days.firstOrNull {
            it.status != CourseCoachDayStatus.COMPLETED && it.status != CourseCoachDayStatus.SKIPPED
        } ?: days.lastOrNull()

        return CourseCoachUiState(
            isLoading = false,
            course = CourseCoachCourseUi(
                id = course.id,
                title = compactCourseTitle(course.title),
                description = course.description,
                notes = packageModel?.course?.notes.orEmpty().filter { it.isNotBlank() },
                version = course.version,
                mascotStyle = course.mascotStyle,
                totalDays = course.totalDays,
            ),
            days = days,
            currentDay = currentDay,
            reminders = reminders
                .sortedBy { if (it.sessionType == "morning") 0 else 1 }
                .map { it.toReminderUi() },
            bonusLessons = packageModel?.bonus_sessions
                ?.map { it.toBonusLessonUi() }
                .orEmpty(),
            stats = buildStats(days, sessionLogs),
            activeSession = activeSession,
        )
    }

    private fun compactCourseTitle(title: String): String {
        val normalized = title.trim()
        return if (normalized.contains("Мужской фокус", ignoreCase = true)) {
            "Мужской фокус"
        } else {
            normalized
        }
    }

    private fun resolveDayStatus(
        morning: CourseCoachSessionCardUi?,
        evening: CourseCoachSessionCardUi?,
        progress: CourseDayProgressEntity?,
    ): CourseCoachDayStatus {
        if (progress?.skippedAtMillis != null) return CourseCoachDayStatus.SKIPPED
        val sessions = listOfNotNull(morning, evening)
        if (sessions.isNotEmpty() && sessions.all { it.completed }) return CourseCoachDayStatus.COMPLETED
        if (sessions.any { it.completed }) return CourseCoachDayStatus.IN_PROGRESS
        return CourseCoachDayStatus.PENDING
    }

    private fun buildStats(
        days: List<CourseCoachDayUi>,
        sessionLogs: List<CourseSessionLogEntity>,
    ): CourseCoachStatsUi {
        val totalRequiredSessions = days.sumOf { day ->
            listOfNotNull(day.morning, day.evening).size
        }
        val completedSessions = days.sumOf { day ->
            listOfNotNull(day.morning, day.evening).count { it.completed }
        }
        val completionRatePercent = if (totalRequiredSessions == 0) {
            0
        } else {
            ((completedSessions * 100f) / totalRequiredSessions.toFloat()).toInt()
        }

        val streak = days
            .asReversed()
            .takeWhile { it.status == CourseCoachDayStatus.COMPLETED }
            .count()

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val dayFormatter = DateTimeFormatter.ofPattern("EE", Locale("ru"))
        val historyFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale("ru"))
        val minutesByDay = mutableMapOf<LocalDate, Int>()
        sessionLogs.filter { it.completed }.forEach { log ->
            val localDate = Instant.ofEpochMilli(log.endedAtMillis).atZone(zoneId).toLocalDate()
            minutesByDay[localDate] = (minutesByDay[localDate] ?: 0) + (log.durationSeconds / 60)
        }

        return CourseCoachStatsUi(
            completedDays = days.count { it.status == CourseCoachDayStatus.COMPLETED },
            skippedDays = days.count { it.status == CourseCoachDayStatus.SKIPPED },
            totalRequiredSessions = totalRequiredSessions,
            completedSessions = completedSessions,
            completionRatePercent = completionRatePercent,
            currentStreak = streak,
            totalMinutes = sessionLogs.sumOf { it.durationSeconds } / 60,
            weekBars = (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                CourseCoachWeekBarUi(
                    label = dayFormatter.format(date).replaceFirstChar { it.titlecase(Locale("ru")) },
                    minutes = minutesByDay[date] ?: 0,
                )
            },
            history = sessionLogs
                .take(12)
                .map { log ->
                    val dateTime = Instant.ofEpochMilli(log.endedAtMillis).atZone(zoneId)
                    CourseCoachHistoryUi(
                        label = historyFormatter.format(dateTime),
                        durationLabel = "${log.durationSeconds / 60} мин",
                        sessionTypeLabel = when (log.sessionType) {
                            "morning" -> "Утро"
                            "evening" -> "Вечер"
                            else -> "Поддержка"
                        },
                    )
                },
        )
    }

    private fun CoursePackageSessionPayload.toSessionCard(
        sessionType: String,
        progress: CourseDayProgressEntity?,
    ) = CourseCoachSessionCardUi(
        sessionKey = lesson_key,
        sessionType = sessionType,
        title = title,
        summary = summary,
        estimatedMinutes = estimated_minutes,
        focusLabel = focus_label,
        exercises = exercises.map { it.toUiExercise() },
        completed = when (sessionType) {
            "morning" -> progress?.morningCompletedAtMillis != null
            "evening" -> progress?.eveningCompletedAtMillis != null
            else -> false
        },
        skipped = sessionType != "support" && progress?.skippedAtMillis != null,
    )

    private fun CoursePackageExercise.toUiExercise() = CourseCoachExerciseUi(
        title = title,
        purpose = purpose,
        instructions = instructions,
        hint = hint,
        safety = safety,
        durationSec = duration_sec,
        repetitions = repetitions,
        restSec = rest_sec,
        notes = notes,
        timerMode = timer_mode.toCourseTimerMode(duration_sec),
        showOrb = show_orb ?: (duration_sec != null),
    )

    private fun CourseCoachSessionCardUi.toRunnerSteps(): List<CourseCoachExerciseRunnerUi> {
        return exercises.flatMapIndexed { index, exercise ->
            buildList {
                add(
                    CourseCoachExerciseRunnerUi(
                        id = "$sessionType-practice-$index",
                        kind = CourseCoachRunnerStepKind.PRACTICE,
                        title = exercise.title,
                        purpose = exercise.purpose ?: summary,
                        instructions = exercise.instructions,
                        hint = exercise.hint,
                        safety = exercise.safety,
                        durationSec = when (exercise.timerMode) {
                            CourseCoachTimerMode.COUNTDOWN -> exercise.durationSec
                            CourseCoachTimerMode.MANUAL -> null
                        },
                        repetitions = exercise.repetitions,
                        notes = exercise.notes,
                        showOrb = exercise.showOrb,
                    )
                )
                exercise.restSec?.takeIf { it > 0 }?.let { rest ->
                    add(
                        CourseCoachExerciseRunnerUi(
                            id = "$sessionType-rest-$index",
                            kind = CourseCoachRunnerStepKind.REST,
                            title = "Пауза",
                            purpose = "Короткое восстановление перед следующим шагом.",
                            instructions = "Сохрани ровное дыхание и не спеши переходить дальше.",
                            hint = "Отпусти плечи и челюсть, верни спокойный ритм.",
                            safety = null,
                            durationSec = rest,
                            repetitions = null,
                            notes = null,
                            showOrb = true,
                        )
                    )
                }
            }
        }
    }

    private fun CoursePackageDiaryPrompt.toDiaryUi() = CourseCoachDiaryUi(
        title = title,
        fields = fields.filter { it.isNotBlank() },
    )

    private fun CourseReminderEntity.toReminderUi() = CourseCoachReminderUi(
        sessionType = sessionType,
        enabled = enabled,
        hour = hour,
        minute = minute,
        timeLabel = "%02d:%02d".format(hour, minute),
    )

    private fun CoursePackageSessionPayload.toBonusLessonUi() = CourseCoachBonusLessonUi(
        lessonKey = lesson_key ?: title.lowercase(Locale.ROOT).replace(' ', '-'),
        title = title,
        summary = summary,
        estimatedMinutes = estimated_minutes,
        focusLabel = focus_label,
        exercises = exercises.map { it.toUiExercise() },
    )

    private fun Int.floorToDay(): Int {
        val minutesInDay = 24 * 60
        var result = this % minutesInDay
        if (result < 0) result += minutesInDay
        return result
    }

    private fun String?.toCourseTimerMode(durationSec: Int?): CourseCoachTimerMode {
        return when (this?.trim()?.lowercase(Locale.ROOT)) {
            "manual" -> CourseCoachTimerMode.MANUAL
            "countdown" -> CourseCoachTimerMode.COUNTDOWN
            else -> if (durationSec != null) CourseCoachTimerMode.COUNTDOWN else CourseCoachTimerMode.MANUAL
        }
    }
}
