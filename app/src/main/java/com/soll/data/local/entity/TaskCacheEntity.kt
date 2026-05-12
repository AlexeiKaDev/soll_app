package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.soll.SollTask
import org.json.JSONArray

@Entity(
    tableName = "task_cache",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
    ],
)
data class TaskCacheEntity(
    @PrimaryKey
    val id: String,

    val title: String,
    val description: String,

    @ColumnInfo(name = "source_ref")
    val sourceRef: String,

    @ColumnInfo(name = "project_name")
    val projectName: String?,

    val status: String,
    val priority: String,

    @ColumnInfo(name = "due_date")
    val dueDate: String?,

    @ColumnInfo(name = "tags_json")
    val tagsJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(): SollTask =
        SollTask(
            id = id,
            title = title,
            description = description,
            sourceRef = sourceRef,
            projectName = projectName,
            status = status,
            priority = priority,
            dueDate = dueDate,
            tags = JSONArray(tagsJson).toStringList(),
        )

    companion object {
        fun fromDomain(task: SollTask, updatedAt: Long): TaskCacheEntity =
            TaskCacheEntity(
                id = task.id,
                title = task.title,
                description = task.description,
                sourceRef = task.sourceRef,
                projectName = task.projectName,
                status = task.status,
                priority = task.priority,
                dueDate = task.dueDate,
                tagsJson = JSONArray(task.tags).toString(),
                updatedAt = updatedAt,
            )
    }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
