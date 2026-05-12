package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobStatus

@Entity(
    tableName = "tool_jobs",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["status"]),
        Index(value = ["tool_id"]),
    ],
)
data class ToolJobEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "tool_id")
    val toolId: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "progress_percent")
    val progressPercent: Int?,

    @ColumnInfo(name = "input_json")
    val inputJson: String,

    @ColumnInfo(name = "output_json")
    val outputJson: String?,

    @ColumnInfo(name = "log_text")
    val logText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
) {
    fun toDomain(): ToolJob = ToolJob(
        id = id,
        toolId = toolId,
        status = runCatching { ToolJobStatus.valueOf(status) }.getOrDefault(ToolJobStatus.FAILED),
        progressPercent = progressPercent,
        inputJson = inputJson,
        outputJson = outputJson,
        logText = logText,
        createdAt = createdAt,
        updatedAt = updatedAt,
        finishedAt = finishedAt,
    )

    companion object {
        fun fromDomain(job: ToolJob): ToolJobEntity = ToolJobEntity(
            id = job.id,
            toolId = job.toolId,
            status = job.status.name,
            progressPercent = job.progressPercent,
            inputJson = job.inputJson,
            outputJson = job.outputJson,
            logText = job.logText,
            createdAt = job.createdAt,
            updatedAt = job.updatedAt,
            finishedAt = job.finishedAt,
        )
    }
}
