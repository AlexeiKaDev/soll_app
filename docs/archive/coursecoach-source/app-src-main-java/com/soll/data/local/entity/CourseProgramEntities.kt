package com.soll.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    indices = [
        Index(value = ["slug"], unique = true),
    ],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val slug: String,
    val title: String,
    val description: String,
    val version: String,
    val reviewStatus: String,
    val contentQuality: String?,
    val mascotStyle: String?,
    val sourceFolder: String,
    val packageFileName: String,
    val totalDays: Int,
    val installedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "course_modules",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId"), Index(value = ["courseId", "moduleKey"], unique = true)],
)
data class CourseModuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val moduleKey: String,
    val title: String,
    val kind: String,
    val orderIndex: Int,
    val summary: String,
)

@Entity(
    tableName = "course_lessons",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "lessonKey"], unique = true),
    ],
)
data class CourseLessonEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val moduleKey: String,
    val lessonKey: String,
    val orderIndex: Int,
    val title: String,
    val summary: String,
    val sourceName: String,
    val sourcePath: String?,
    val lessonType: String,
    val sessionTag: String?,
    val required: Boolean,
    val estimatedMinutes: Int?,
)

@Entity(
    tableName = "course_day_plans",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "dayIndex"], unique = true),
    ],
)
data class CourseDayPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val dayIndex: Int,
    val title: String,
    val theme: String,
    val morningPayloadJson: String?,
    val eveningPayloadJson: String?,
    val diaryPromptJson: String?,
)

@Entity(
    tableName = "course_day_progress",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "dayIndex"], unique = true),
    ],
)
data class CourseDayProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val dayIndex: Int,
    val morningCompletedAtMillis: Long? = null,
    val eveningCompletedAtMillis: Long? = null,
    val skippedAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "course_session_logs",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("courseId"), Index("endedAtMillis")],
)
data class CourseSessionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val dayIndex: Int,
    val sessionType: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val completed: Boolean,
    val completedExercises: Int,
    val totalExercises: Int,
    val durationSeconds: Int,
)

@Entity(
    tableName = "course_reminders",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "sessionType"], unique = true),
    ],
)
data class CourseReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val sessionType: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
)
