package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.CourseDayPlanEntity
import com.soll.data.local.entity.CourseDayProgressEntity
import com.soll.data.local.entity.CourseEntity
import com.soll.data.local.entity.CourseLessonEntity
import com.soll.data.local.entity.CourseModuleEntity
import com.soll.data.local.entity.CourseReminderEntity
import com.soll.data.local.entity.CourseSessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseProgramDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModules(items: List<CourseModuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(items: List<CourseLessonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayPlans(items: List<CourseDayPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDayProgress(items: List<CourseDayProgressEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(items: List<CourseReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayProgress(item: CourseDayProgressEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionLog(item: CourseSessionLogEntity): Long

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun getCourseCount(): Int

    @Query("SELECT * FROM courses WHERE slug = :slug LIMIT 1")
    suspend fun getCourseBySlug(slug: String): CourseEntity?

    @Query("SELECT * FROM courses ORDER BY lastUpdatedAt DESC LIMIT 1")
    suspend fun getActiveCourseSnapshot(): CourseEntity?

    @Query("SELECT * FROM courses ORDER BY lastUpdatedAt DESC LIMIT 1")
    fun observeActiveCourse(): Flow<CourseEntity?>

    @Query("SELECT * FROM courses ORDER BY installedAt DESC")
    fun observeCourses(): Flow<List<CourseEntity>>

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

    @Query("SELECT * FROM course_modules WHERE courseId = :courseId ORDER BY orderIndex ASC")
    fun observeModules(courseId: Long): Flow<List<CourseModuleEntity>>

    @Query("SELECT * FROM course_lessons WHERE courseId = :courseId ORDER BY orderIndex ASC")
    fun observeLessons(courseId: Long): Flow<List<CourseLessonEntity>>

    @Query("SELECT * FROM course_day_plans WHERE courseId = :courseId ORDER BY dayIndex ASC")
    fun observeDayPlans(courseId: Long): Flow<List<CourseDayPlanEntity>>

    @Query("SELECT * FROM course_day_plans WHERE courseId = :courseId ORDER BY dayIndex ASC")
    suspend fun getDayPlansSnapshot(courseId: Long): List<CourseDayPlanEntity>

    @Query("SELECT * FROM course_day_progress WHERE courseId = :courseId ORDER BY dayIndex ASC")
    fun observeDayProgress(courseId: Long): Flow<List<CourseDayProgressEntity>>

    @Query("SELECT * FROM course_day_progress WHERE courseId = :courseId ORDER BY dayIndex ASC")
    suspend fun getDayProgressSnapshot(courseId: Long): List<CourseDayProgressEntity>

    @Query("SELECT * FROM course_day_progress WHERE courseId = :courseId AND dayIndex = :dayIndex LIMIT 1")
    suspend fun getDayProgress(courseId: Long, dayIndex: Int): CourseDayProgressEntity?

    @Query("DELETE FROM course_day_progress WHERE courseId = :courseId")
    suspend fun deleteDayProgressByCourseId(courseId: Long)

    @Query("SELECT * FROM course_reminders WHERE courseId = :courseId ORDER BY sessionType ASC")
    fun observeReminders(courseId: Long): Flow<List<CourseReminderEntity>>

    @Query("SELECT * FROM course_reminders WHERE courseId = :courseId ORDER BY sessionType ASC")
    suspend fun getReminderSnapshot(courseId: Long): List<CourseReminderEntity>

    @Query(
        """
        UPDATE course_reminders
        SET enabled = :enabled,
            hour = :hour,
            minute = :minute
        WHERE courseId = :courseId AND sessionType = :sessionType
        """
    )
    suspend fun updateReminder(courseId: Long, sessionType: String, enabled: Boolean, hour: Int, minute: Int)

    @Query("SELECT * FROM course_session_logs WHERE courseId = :courseId ORDER BY endedAtMillis DESC")
    fun observeSessionLogs(courseId: Long): Flow<List<CourseSessionLogEntity>>

    @Query("SELECT * FROM course_session_logs WHERE courseId = :courseId ORDER BY endedAtMillis DESC")
    suspend fun getSessionLogsSnapshot(courseId: Long): List<CourseSessionLogEntity>

    @Query("DELETE FROM course_session_logs WHERE courseId = :courseId")
    suspend fun deleteSessionLogsByCourseId(courseId: Long)
}
