package com.soll.domain.tool

import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class ToolJobCancelledException(jobId: String) : CancellationException("Задача инструмента отменена: $jobId")

class ToolJobRunner @Inject constructor(
    private val store: ToolJobStore,
    private val notificationCenter: SollNotificationCenter? = null,
) {
    suspend fun enqueue(
        toolId: String,
        inputJson: String = "{}",
        logText: String = "",
    ): ToolJob {
        val now = System.currentTimeMillis()
        return store.insert(
            ToolJob(
                id = UUID.randomUUID().toString(),
                toolId = toolId,
                status = ToolJobStatus.QUEUED,
                progressPercent = null,
                inputJson = inputJson,
                outputJson = null,
                logText = logText,
                createdAt = now,
                updatedAt = now,
                finishedAt = null,
            )
        )
    }

    suspend fun run(
        toolId: String,
        inputJson: String = "{}",
        handler: ToolHandler,
        onQueued: suspend (ToolJob) -> Unit = {},
    ): ToolJob {
        require(handler.toolId == toolId) {
            "Обработчик ${handler.toolId} не может выполнить задачу $toolId"
        }

        val queued = enqueue(toolId = toolId, inputJson = inputJson)
        onQueued(queued)
        val running = queued.copy(
            status = ToolJobStatus.RUNNING,
            updatedAt = System.currentTimeMillis(),
        )
        store.update(running)

        val progress = PersistedToolJobProgressSink(running, store)
        return try {
            val result = handler.execute(running, progress)
            val latest = progress.currentJob()
            store.getJob(latest.id)?.takeIf { it.status == ToolJobStatus.CANCELLED } ?: run {
                val finishedAt = System.currentTimeMillis()
                latest.copy(
                    status = ToolJobStatus.SUCCESS,
                    progressPercent = 100,
                    outputJson = result.outputJson,
                    logText = appendLog(latest.logText, result.logText),
                    updatedAt = finishedAt,
                    finishedAt = finishedAt,
                ).also { finalJob ->
                    store.update(finalJob)
                    publishFinishedJobNotification(finalJob)
                }
            }
        } catch (cancelled: ToolJobCancelledException) {
            store.getJob(running.id)?.takeIf { it.status == ToolJobStatus.CANCELLED }
                ?.also { publishFinishedJobNotification(it) }
                ?: cancelRunningJobSnapshot(running)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val latest = progress.currentJob()
            store.getJob(latest.id)?.takeIf { it.status == ToolJobStatus.CANCELLED } ?: run {
                val finishedAt = System.currentTimeMillis()
                latest.copy(
                    status = ToolJobStatus.FAILED,
                    logText = appendLog(latest.logText, error.message ?: error::class.java.simpleName),
                    updatedAt = finishedAt,
                    finishedAt = finishedAt,
                ).also { finalJob ->
                    store.update(finalJob)
                    publishFinishedJobNotification(finalJob)
                }
            }
        }
    }

    suspend fun markBlocked(
        toolId: String,
        inputJson: String = "{}",
        reason: String,
    ): ToolJob {
        val now = System.currentTimeMillis()
        return store.insert(
            ToolJob(
                id = UUID.randomUUID().toString(),
                toolId = toolId,
                status = ToolJobStatus.BLOCKED,
                progressPercent = null,
                inputJson = inputJson,
                outputJson = null,
                logText = reason,
                createdAt = now,
                updatedAt = now,
                finishedAt = now,
            )
        ).also { publishFinishedJobNotification(it) }
    }

    suspend fun cancel(jobId: String, reason: String? = null): ToolJob? {
        val existing = store.getJob(jobId) ?: return null
        if (existing.finishedAt != null) return existing

        val now = System.currentTimeMillis()
        return existing.copy(
            status = ToolJobStatus.CANCELLED,
            logText = appendLog(existing.logText, reason),
            updatedAt = now,
            finishedAt = now,
        ).also {
            store.update(it)
            publishFinishedJobNotification(it)
        }
    }

    private suspend fun cancelRunningJobSnapshot(running: ToolJob): ToolJob {
        cancel(running.id, "Задача отменена")?.let { return it }
        val finishedAt = System.currentTimeMillis()
        val cancelled = running.copy(
            status = ToolJobStatus.CANCELLED,
            logText = appendLog(running.logText, "Задача отменена"),
            updatedAt = finishedAt,
            finishedAt = finishedAt,
        )
        publishFinishedJobNotification(cancelled)
        return cancelled
    }

    private suspend fun publishFinishedJobNotification(job: ToolJob) {
        if (job.finishedAt == null) return
        val center = notificationCenter ?: return
        val priority = when (job.status) {
            ToolJobStatus.FAILED,
            ToolJobStatus.BLOCKED -> SollNotificationPriority.HIGH
            ToolJobStatus.CANCELLED -> SollNotificationPriority.DEFAULT
            ToolJobStatus.SUCCESS -> SollNotificationPriority.LOW
            ToolJobStatus.QUEUED,
            ToolJobStatus.RUNNING,
            ToolJobStatus.WAITING_FOR_CONFIRMATION -> return
        }
        val title = when (job.status) {
            ToolJobStatus.SUCCESS -> "Задача завершена"
            ToolJobStatus.FAILED -> "Ошибка задачи"
            ToolJobStatus.CANCELLED -> "Задача отменена"
            ToolJobStatus.BLOCKED -> "Задача заблокирована"
            else -> return
        }
        val message = buildString {
            append(job.toolId.toolLabel())
            append(": ")
            append(job.status.label())
            job.logText.lineSequence().lastOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { append(". ").append(it.take(120)) }
        }

        try {
            center.post(
                SollNotificationRequest(
                    channel = SollNotificationChannel.TOOL_JOBS,
                    type = "tool_job_${job.status.name.lowercase()}",
                    source = "tool_job",
                    title = title,
                    message = message,
                    payloadJson = buildJobPayload(job),
                    priority = priority,
                    showSystem = true,
                    onlyAlertOnce = true,
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Job completion must not fail only because the optional notification failed.
        }
    }

    private class PersistedToolJobProgressSink(
        initialJob: ToolJob,
        private val store: ToolJobStore,
    ) : ToolJobProgressSink {
        private var job = initialJob

        fun currentJob(): ToolJob = job

        override suspend fun updateProgress(progressPercent: Int?, logLine: String?) {
            ensureNotCancelled()
            val now = System.currentTimeMillis()
            job = job.copy(
                progressPercent = progressPercent?.coerceIn(0, 100),
                logText = appendLog(job.logText, logLine),
                updatedAt = now,
            )
            store.update(job)
        }

        override suspend fun appendLog(logLine: String) {
            ensureNotCancelled()
            val now = System.currentTimeMillis()
            job = job.copy(
                logText = appendLog(job.logText, logLine),
                updatedAt = now,
            )
            store.update(job)
        }

        private suspend fun ensureNotCancelled() {
            val latest = store.getJob(job.id)
            if (latest?.status == ToolJobStatus.CANCELLED) {
                job = latest
                throw ToolJobCancelledException(job.id)
            }
        }
    }

    private companion object {
        fun appendLog(current: String, line: String?): String {
            if (line.isNullOrBlank()) return current
            return if (current.isBlank()) line else "$current\n$line"
        }

        fun String.toolLabel(): String = when (this) {
            "books" -> "Книги"
            "music_scan" -> "Музыка"
            "scanner_export" -> "Сканер"
            "raw" -> "Raw"
            "photo" -> "Фото"
            "record" -> "Аудио"
            "download" -> "Файл"
            "bot_service_start" -> "Фоновый бот"
            "bot_service_stop" -> "Фоновый бот"
            else -> this
        }

        fun ToolJobStatus.label(): String = when (this) {
            ToolJobStatus.QUEUED -> "в очереди"
            ToolJobStatus.RUNNING -> "выполняется"
            ToolJobStatus.WAITING_FOR_CONFIRMATION -> "ждет подтверждения"
            ToolJobStatus.SUCCESS -> "успешно"
            ToolJobStatus.FAILED -> "ошибка"
            ToolJobStatus.CANCELLED -> "отменено"
            ToolJobStatus.BLOCKED -> "заблокировано"
        }

        fun buildJobPayload(job: ToolJob): String =
            """{"job_id":"${job.id}","tool_id":"${job.toolId}","status":"${job.status.name}"}"""
    }
}
