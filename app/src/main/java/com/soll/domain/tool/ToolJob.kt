package com.soll.domain.tool

import java.util.UUID

data class ToolJob(
    val id: String = UUID.randomUUID().toString(),
    val toolId: String,
    val status: ToolJobStatus,
    val progressPercent: Int?,
    val inputJson: String,
    val outputJson: String?,
    val logText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val finishedAt: Long?,
)

enum class ToolJobStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_CONFIRMATION,
    SUCCESS,
    FAILED,
    CANCELLED,
    BLOCKED,
}

data class ToolJobResult(
    val outputJson: String? = null,
    val logText: String? = null,
)

interface ToolHandler {
    val toolId: String

    suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult
}

interface ToolJobProgressSink {
    suspend fun updateProgress(progressPercent: Int?, logLine: String? = null)
    suspend fun appendLog(logLine: String)
}
