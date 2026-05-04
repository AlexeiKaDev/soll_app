package com.soll.presentation.screens.tools.coursecoach

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.course.CoursePackageDiaryPrompt
import com.soll.data.course.CoursePackageExercise
import com.soll.data.course.CoursePackageSessionPayload
import com.soll.data.local.entity.CourseDayPlanEntity
import com.soll.data.local.entity.CourseDayProgressEntity
import com.soll.data.local.entity.CourseEntity
import com.soll.data.local.entity.CourseLessonEntity
import com.soll.data.local.entity.CourseModuleEntity
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CourseCoachDayStatus {
    COMPLETED,
    IN_PROGRESS,
    PENDING,
    SKIPPED,
}

data class CourseCoachUiState(
    val isLoading: Boolean = true,
    val course: CourseCoachCourseUi? = null,
    val installedCourses: List<CourseCoachInstalledCourseUi> = emptyList(),
    val modules: List<CourseCoachModuleUi> = emptyList(),
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
    val version: String,
    val reviewStatus: String,
    val contentQuality: String?,
    val mascotStyle: String?,
    val totalDays: Int,
    val sourceFolder: String,
)

data class CourseCoachInstalledCourseUi(
    val id: Long,
    val title: String,
    val version: String,
    val isActive: Boolean,
)

data class CourseCoachModuleUi(
    val title: String,
    val kind: String,
    val lessonCount: Int,
    val summary: String,
)

data class CourseCoachDayUi(
    val dayIndex: Int,
    val title: String,
    val theme: String,
    val status: CourseCoachDayStatus,
    val completionFraction: Float,
    val morning: CourseCoachSessionCardUi?,
    val evening: CourseCoachSessionCardUi?,
    val diaryPrompt: CourseCoachDiaryUi?,
)

data class CourseCoachSessionCardUi(
    val sessionType: String,
    val title: String,
    val summary: String,
    val estimatedMinutes: Int?,
    val sourceName: String?,
    val exercises: List<CourseCoachExerciseUi>,
    val completed: Boolean,
    val skipped: Boolean,
)

data class CourseCoachExerciseUi(
    val title: String,
    val instructions: String,
    val durationSec: Int?,
    val repetitions: Int?,
    val restSec: Int?,
    val notes: String?,
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
    val sourceName: String,
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
    val sourceName: String?,
    val exercises: List<CourseCoachExerciseRunnerUi>,
    val startedAtMillis: Long,
    val tracksProgress: Boolean,
)

data class CourseCoachExerciseRunnerUi(
    val title: String,
    val instructions: String,
    val durationSec: Int?,
    val repetitions: Int?,
    val restSec: Int?,
    val notes: String?,
    val completed: Boolean = false,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CourseCoachViewModel @Inject constructor(
    private val repository: CourseProgramRepository,
) : ViewModel() {

    private data class CourseCoachPrimaryData(
        val course: CourseEntity?,
        val courses: List<CourseEntity>,
        val modules: List<CourseModuleEntity>,
        val lessons: List<CourseLessonEntity>,
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

    private val activeCourse = repository.observeActiveCourse()
    private val installedCourses = repository.observeCourses()
    private val modules = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeModules(it.id) } ?: flowOf(emptyList())
    }
    private val lessons = activeCourse.flatMapLatest { course ->
        course?.let { repository.observeLessons(it.id) } ?: flowOf(emptyList())
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
        installedCourses,
        modules,
        lessons,
    ) { course, courses, modules, lessons ->
        CourseCoachPrimaryData(
            course = course,
            courses = courses,
            modules = modules,
            lessons = lessons,
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
            courses = primary.courses,
            modules = primary.modules,
            lessons = primary.lessons,
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

        activeSession.value = CourseCoachSessionRunnerUi(
            courseId = course.id,
            dayIndex = dayIndex,
            sessionType = sessionType,
            title = session.title,
            summary = session.summary,
            estimatedMinutes = session.estimatedMinutes,
            sourceName = session.sourceName,
            exercises = session.exercises.map { it.toRunnerExercise() },
            startedAtMillis = System.currentTimeMillis(),
            tracksProgress = true,
        )
    }

    fun openBonusLesson(lessonKey: String) {
        val bonus = uiState.value.bonusLessons.firstOrNull { it.lessonKey == lessonKey } ?: return
        val courseId = uiState.value.course?.id ?: return
        activeSession.value = CourseCoachSessionRunnerUi(
            courseId = courseId,
            dayIndex = -1,
            sessionType = "bonus",
            title = bonus.title,
            summary = bonus.summary,
            estimatedMinutes = bonus.estimatedMinutes,
            sourceName = bonus.sourceName,
            exercises = listOf(
                CourseCoachExerciseRunnerUi(
                    title = bonus.title,
                    instructions = "Используй бонусный материал как дополнительную практику без форсирования.",
                    durationSec = bonus.estimatedMinutes?.times(60),
                    repetitions = null,
                    restSec = 30,
                    notes = "Источник: ${bonus.sourceName}",
                )
            ),
            startedAtMillis = System.currentTimeMillis(),
            tracksProgress = false,
        )
    }

    fun dismissSession() {
        activeSession.value = null
    }

    fun toggleExercise(index: Int) {
        activeSession.update { session ->
            session ?: return@update null
            if (index !in session.exercises.indices) return@update session
            session.copy(
                exercises = session.exercises.mapIndexed { currentIndex, exercise ->
                    if (currentIndex == index) {
                        exercise.copy(completed = !exercise.completed)
                    } else {
                        exercise
                    }
                }
            )
        }
    }

    fun completeActiveSession() {
        val session = activeSession.value ?: return
        val completedCount = session.exercises.count { it.completed }
        val totalExercises = session.exercises.size.coerceAtLeast(1)
        if (completedCount < totalExercises) {
            viewModelScope.launch {
                eventsInternal.emit("Отметь все упражнения в блоке перед завершением.")
            }
            return
        }

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
                eventsInternal.emit("Бонусный материал просмотрен.")
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

    fun importCoursePackage(uri: Uri) {
        viewModelScope.launch {
            repository.importCoursePackage(uri)
                .onSuccess { course ->
                    eventsInternal.emit("Импортирован курс: ${course.title}")
                }
                .onFailure {
                    eventsInternal.emit("Импорт не удался: ${it.message}")
                }
        }
    }

    fun reinstallSeedCourse() {
        viewModelScope.launch {
            runCatching { repository.installBundledSeedPackage() }
                .onSuccess { eventsInternal.emit("Встроенный seed-пакет установлен заново.") }
                .onFailure { eventsInternal.emit("Не удалось переустановить seed-пакет: ${it.message}") }
        }
    }

    fun resetProgress() {
        val courseId = uiState.value.course?.id ?: return
        viewModelScope.launch {
            repository.resetProgress(courseId)
            activeSession.value = null
            eventsInternal.emit("Прогресс курса сброшен.")
        }
    }

    private fun buildUiState(
        course: CourseEntity?,
        courses: List<CourseEntity>,
        modules: List<CourseModuleEntity>,
        lessons: List<CourseLessonEntity>,
        dayPlans: List<CourseDayPlanEntity>,
        dayProgress: List<CourseDayProgressEntity>,
        reminders: List<CourseReminderEntity>,
        sessionLogs: List<CourseSessionLogEntity>,
        activeSession: CourseCoachSessionRunnerUi?,
    ): CourseCoachUiState {
        val installed = courses.map { item ->
            CourseCoachInstalledCourseUi(
                id = item.id,
                title = compactCourseTitle(item.title),
                version = item.version,
                isActive = item.id == course?.id,
            )
        }
        if (course == null) {
            return CourseCoachUiState(
                isLoading = false,
                installedCourses = installed,
                activeSession = activeSession,
            )
        }

        val progressByDay = dayProgress.associateBy { it.dayIndex }
        val lessonCountByModule = lessons.groupingBy { it.moduleKey }.eachCount()
        val days = dayPlans.map { plan ->
            val progress = progressByDay[plan.dayIndex]
            val morning = repository.decodeSessionPayload(plan.morningPayloadJson)
                ?.toSessionCard("morning", progress)
            val evening = repository.decodeSessionPayload(plan.eveningPayloadJson)
                ?.toSessionCard("evening", progress)
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
                version = course.version,
                reviewStatus = course.reviewStatus,
                contentQuality = course.contentQuality,
                mascotStyle = course.mascotStyle,
                totalDays = course.totalDays,
                sourceFolder = course.sourceFolder,
            ),
            installedCourses = installed,
            modules = modules.map { module ->
                CourseCoachModuleUi(
                    title = module.title,
                    kind = module.kind,
                    lessonCount = lessonCountByModule[module.moduleKey] ?: 0,
                    summary = module.summary,
                )
            },
            days = days,
            currentDay = currentDay,
            reminders = reminders
                .sortedBy { if (it.sessionType == "morning") 0 else 1 }
                .map { it.toReminderUi() },
            bonusLessons = lessons
                .filter { !it.required }
                .sortedBy { it.orderIndex }
                .map { it.toBonusLessonUi() },
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
                            else -> "Бонус"
                        },
                    )
                },
        )
    }

    private fun CoursePackageSessionPayload.toSessionCard(
        sessionType: String,
        progress: CourseDayProgressEntity?,
    ) = CourseCoachSessionCardUi(
        sessionType = sessionType,
        title = title,
        summary = summary,
        estimatedMinutes = estimated_minutes,
        sourceName = source_name,
        exercises = exercises.map { it.toUiExercise() },
        completed = when (sessionType) {
            "morning" -> progress?.morningCompletedAtMillis != null
            else -> progress?.eveningCompletedAtMillis != null
        },
        skipped = progress?.skippedAtMillis != null,
    )

    private fun CoursePackageExercise.toUiExercise() = CourseCoachExerciseUi(
        title = title,
        instructions = instructions,
        durationSec = duration_sec,
        repetitions = repetitions,
        restSec = rest_sec,
        notes = notes,
    )

    private fun CourseCoachExerciseUi.toRunnerExercise() = CourseCoachExerciseRunnerUi(
        title = title,
        instructions = instructions,
        durationSec = durationSec,
        repetitions = repetitions,
        restSec = restSec,
        notes = notes,
    )

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

    private fun CourseLessonEntity.toBonusLessonUi() = CourseCoachBonusLessonUi(
        lessonKey = lessonKey,
        title = title,
        summary = summary,
        estimatedMinutes = estimatedMinutes,
        sourceName = sourceName,
    )

    private fun Int.floorToDay(): Int {
        val minutesInDay = 24 * 60
        var result = this % minutesInDay
        if (result < 0) result += minutesInDay
        return result
    }
}
