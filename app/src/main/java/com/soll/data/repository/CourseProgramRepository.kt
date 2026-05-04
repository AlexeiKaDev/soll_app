package com.soll.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.soll.data.course.CoursePackageDayPlan
import com.soll.data.course.CoursePackageDiaryPrompt
import com.soll.data.course.CoursePackageLesson
import com.soll.data.course.CoursePackageModel
import com.soll.data.course.CoursePackageModule
import com.soll.data.course.CoursePackageResource
import com.soll.data.course.CoursePackageSessionPayload
import com.soll.data.local.SollDatabase
import com.soll.data.local.dao.CourseProgramDao
import com.soll.data.local.entity.CourseDayPlanEntity
import com.soll.data.local.entity.CourseDayProgressEntity
import com.soll.data.local.entity.CourseEntity
import com.soll.data.local.entity.CourseLessonEntity
import com.soll.data.local.entity.CourseModuleEntity
import com.soll.data.local.entity.CourseReminderEntity
import com.soll.data.local.entity.CourseSessionLogEntity
import com.soll.data.service.CourseReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class CourseProgramRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SollDatabase,
    private val dao: CourseProgramDao,
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val packageAdapter = moshi.adapter(CoursePackageModel::class.java)
    private val sessionAdapter = moshi.adapter(CoursePackageSessionPayload::class.java)
    private val diaryAdapter = moshi.adapter(CoursePackageDiaryPrompt::class.java)
    private val packagesDir = File(context.filesDir, "course_packages").apply { mkdirs() }

    fun observeActiveCourse(): Flow<CourseEntity?> = dao.observeActiveCourse()
    fun observeCourses(): Flow<List<CourseEntity>> = dao.observeCourses()
    fun observeModules(courseId: Long): Flow<List<CourseModuleEntity>> = dao.observeModules(courseId)
    fun observeLessons(courseId: Long): Flow<List<CourseLessonEntity>> = dao.observeLessons(courseId)
    fun observeDayPlans(courseId: Long): Flow<List<CourseDayPlanEntity>> = dao.observeDayPlans(courseId)
    fun observeDayProgress(courseId: Long): Flow<List<CourseDayProgressEntity>> = dao.observeDayProgress(courseId)
    fun observeReminders(courseId: Long): Flow<List<CourseReminderEntity>> = dao.observeReminders(courseId)
    fun observeSessionLogs(courseId: Long): Flow<List<CourseSessionLogEntity>> = dao.observeSessionLogs(courseId)

    suspend fun ensureSeedCourseInstalled() = withContext(Dispatchers.IO) {
        if (dao.getCourseCount() > 0) return@withContext
        val assetName = "course_packages/male_focus_seed.json"
        context.assets.open(assetName).use { input ->
            installPackage(input, "male_focus_seed.json")
        }
    }

    suspend fun installBundledSeedPackage() = withContext(Dispatchers.IO) {
        val assetName = "course_packages/male_focus_seed.json"
        context.assets.open(assetName).use { input ->
            installPackage(input, "male_focus_seed.json")
        }
    }

    suspend fun importCoursePackage(uri: Uri): Result<CourseEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = DocumentFile.fromSingleUri(context, uri)?.name ?: "imported_course.json"
            context.contentResolver.openInputStream(uri)?.use { input ->
                installPackage(input, displayName)
            } ?: error("Не удалось открыть JSON package")
        }
    }

    suspend fun getActiveCourseSnapshot(): CourseEntity? = dao.getActiveCourseSnapshot()

    fun decodeSessionPayload(raw: String?): CoursePackageSessionPayload? {
        if (raw.isNullOrBlank()) return null
        return runCatching { sessionAdapter.fromJson(raw) }
            .onFailure { Timber.w(it, "Failed to parse session payload") }
            .getOrNull()
    }

    fun decodeDiaryPrompt(raw: String?): CoursePackageDiaryPrompt? {
        if (raw.isNullOrBlank()) return null
        return runCatching { diaryAdapter.fromJson(raw) }
            .onFailure { Timber.w(it, "Failed to parse diary payload") }
            .getOrNull()
    }

    suspend fun markSessionCompleted(
        courseId: Long,
        dayIndex: Int,
        sessionType: String,
        completedExercises: Int,
        totalExercises: Int,
        startedAtMillis: Long,
        endedAtMillis: Long,
    ) = withContext(Dispatchers.IO) {
        val current = dao.getDayProgress(courseId, dayIndex) ?: CourseDayProgressEntity(
            courseId = courseId,
            dayIndex = dayIndex,
        )
        val updated = when (sessionType) {
            "morning" -> current.copy(
                morningCompletedAtMillis = endedAtMillis,
                skippedAtMillis = null,
                updatedAtMillis = endedAtMillis,
            )
            else -> current.copy(
                eveningCompletedAtMillis = endedAtMillis,
                skippedAtMillis = null,
                updatedAtMillis = endedAtMillis,
            )
        }
        dao.upsertDayProgress(updated)
        dao.insertSessionLog(
            CourseSessionLogEntity(
                courseId = courseId,
                dayIndex = dayIndex,
                sessionType = sessionType,
                startedAtMillis = startedAtMillis,
                endedAtMillis = endedAtMillis,
                completed = true,
                completedExercises = completedExercises,
                totalExercises = totalExercises,
                durationSeconds = ((endedAtMillis - startedAtMillis) / 1000L).toInt().coerceAtLeast(0),
            )
        )
    }

    suspend fun skipDay(courseId: Long, dayIndex: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val current = dao.getDayProgress(courseId, dayIndex) ?: CourseDayProgressEntity(
            courseId = courseId,
            dayIndex = dayIndex,
        )
        dao.upsertDayProgress(
            current.copy(
                skippedAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun updateReminder(
        courseId: Long,
        sessionType: String,
        enabled: Boolean,
        hour: Int,
        minute: Int,
    ) = withContext(Dispatchers.IO) {
        dao.updateReminder(courseId, sessionType, enabled, hour, minute)
        rescheduleReminders(courseId)
    }

    suspend fun resetProgress(courseId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val plans = dao.getDayPlansSnapshot(courseId)
            dao.deleteSessionLogsByCourseId(courseId)
            dao.deleteDayProgressByCourseId(courseId)
            dao.insertDayProgress(
                plans.map { plan ->
                    CourseDayProgressEntity(courseId = courseId, dayIndex = plan.dayIndex)
                }
            )
        }
    }

    suspend fun rescheduleAllReminders() = withContext(Dispatchers.IO) {
        val active = dao.getActiveCourseSnapshot() ?: return@withContext
        rescheduleReminders(active.id)
    }

    suspend fun rescheduleReminders(courseId: Long) = withContext(Dispatchers.IO) {
        val course = dao.getActiveCourseSnapshot()?.takeIf { it.id == courseId } ?: return@withContext
        val reminders = dao.getReminderSnapshot(courseId)
        CourseReminderScheduler.scheduleCourse(context, course, reminders)
    }

    suspend fun getCurrentDayPlan(courseId: Long): Pair<CourseDayPlanEntity, CourseDayProgressEntity?>? =
        withContext(Dispatchers.IO) {
            val plans = dao.getDayPlansSnapshot(courseId)
            val progress = dao.getDayProgressSnapshot(courseId).associateBy { it.dayIndex }
            val current = plans.firstOrNull { plan ->
                val p = progress[plan.dayIndex]
                !isDayFinished(plan, p)
            } ?: plans.lastOrNull()
            current?.let { it to progress[it.dayIndex] }
        }

    private fun isDayFinished(plan: CourseDayPlanEntity, progress: CourseDayProgressEntity?): Boolean {
        if (progress?.skippedAtMillis != null) return true
        val needsMorning = !plan.morningPayloadJson.isNullOrBlank()
        val needsEvening = !plan.eveningPayloadJson.isNullOrBlank()
        val morningDone = !needsMorning || progress?.morningCompletedAtMillis != null
        val eveningDone = !needsEvening || progress?.eveningCompletedAtMillis != null
        return morningDone && eveningDone
    }

    private suspend fun installPackage(input: InputStream, fileName: String): CourseEntity {
        val rawJson = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = packageAdapter.fromJson(rawJson) ?: error("Package JSON пустой или невалидный")
        require(parsed.manifest.schema_version == 1) { "Поддерживается только schema_version=1" }
        require(parsed.manifest.review_status.equals("approved", ignoreCase = true)) {
            "Package не approved: ${parsed.manifest.review_status}"
        }

        val storedFile = File(packagesDir, fileName.sanitizeFileName())
        storedFile.writeText(rawJson, Charsets.UTF_8)

        val course = database.withTransaction {
            dao.getCourseBySlug(parsed.manifest.course_slug)?.let { existing ->
                dao.deleteCourseById(existing.id)
            }

            val courseId = dao.insertCourse(
                CourseEntity(
                    slug = parsed.manifest.course_slug,
                    title = parsed.course.title,
                    description = parsed.course.description,
                    version = parsed.manifest.course_version,
                    reviewStatus = parsed.manifest.review_status,
                    contentQuality = parsed.manifest.content_quality,
                    mascotStyle = parsed.course.mascot_style,
                    sourceFolder = parsed.manifest.source_folder,
                    packageFileName = storedFile.name,
                    totalDays = parsed.day_plans.size,
                )
            )

            dao.insertModules(parsed.modules.map { it.toEntity(courseId) })
            dao.insertLessons(parsed.lessons.map { it.toEntity(courseId) })
            dao.insertDayPlans(parsed.day_plans.map { it.toEntity(courseId, sessionAdapter, diaryAdapter) })
            dao.insertDayProgress(parsed.day_plans.map { CourseDayProgressEntity(courseId = courseId, dayIndex = it.day_index) })
            dao.insertReminders(
                listOf(
                    CourseReminderEntity(courseId = courseId, sessionType = "morning", enabled = true, hour = 7, minute = 30),
                    CourseReminderEntity(courseId = courseId, sessionType = "evening", enabled = true, hour = 20, minute = 0),
                )
            )
            dao.getActiveCourseSnapshot() ?: error("Курс не сохранился")
        }

        rescheduleReminders(course.id)
        return course
    }

    private fun CoursePackageModule.toEntity(courseId: Long) = CourseModuleEntity(
        courseId = courseId,
        moduleKey = module_key,
        title = title,
        kind = kind,
        orderIndex = order_index,
        summary = summary,
    )

    private fun CoursePackageLesson.toEntity(courseId: Long) = CourseLessonEntity(
        courseId = courseId,
        moduleKey = module_key,
        lessonKey = lesson_key,
        orderIndex = order_index,
        title = title,
        summary = summary,
        sourceName = source_name,
        sourcePath = source_path,
        lessonType = lesson_type,
        sessionTag = session_tag,
        required = required,
        estimatedMinutes = estimated_minutes,
    )

    private fun CoursePackageDayPlan.toEntity(
        courseId: Long,
        sessionAdapter: com.squareup.moshi.JsonAdapter<CoursePackageSessionPayload>,
        diaryAdapter: com.squareup.moshi.JsonAdapter<CoursePackageDiaryPrompt>,
    ) = CourseDayPlanEntity(
        courseId = courseId,
        dayIndex = day_index,
        title = title,
        theme = theme,
        morningPayloadJson = morning?.let(sessionAdapter::toJson),
        eveningPayloadJson = evening?.let(sessionAdapter::toJson),
        diaryPromptJson = diary_prompt?.let(diaryAdapter::toJson),
    )

    private fun String.sanitizeFileName(): String =
        replace(Regex("""[^\w.\-]+"""), "_")
}
