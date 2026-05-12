package com.soll.domain.tool

import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ToolJobRunnerTest {
    @Test
    fun `successful handler records progress and success status`() = runBlocking {
        val store = FakeToolJobStore()
        val runner = ToolJobRunner(store)

        val result = runner.run(
            toolId = "photo",
            inputJson = """{"camera":"back"}""",
            handler = SuccessHandler("photo"),
        )

        assertEquals(ToolJobStatus.SUCCESS, result.status)
        assertEquals(100, result.progressPercent)
        assertEquals("""{"ok":true}""", result.outputJson)
        assertEquals(result, store.jobs[result.id])
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `failing handler records failed status and error log`() = runBlocking {
        val store = FakeToolJobStore()
        val runner = ToolJobRunner(store)

        val result = runner.run(
            toolId = "download",
            inputJson = """{"path":"/missing"}""",
            handler = FailingHandler("download"),
        )

        assertEquals(ToolJobStatus.FAILED, result.status)
        assertEquals("boom", result.logText.lines().last())
        assertEquals(result, store.jobs[result.id])
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `blocked job is persisted as finished blocked status`() = runBlocking {
        val store = FakeToolJobStore()
        val runner = ToolJobRunner(store)

        val result = runner.markBlocked(
            toolId = "record",
            inputJson = """{"seconds":30}""",
            reason = "Запись аудио отключена",
        )

        assertEquals(ToolJobStatus.BLOCKED, result.status)
        assertEquals("Запись аудио отключена", result.logText)
        assertEquals(result, store.jobs[result.id])
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `cancelled running job is not overwritten by handler completion`() = runBlocking {
        val store = FakeToolJobStore()
        val runner = ToolJobRunner(store)

        val result = runner.run(
            toolId = "music_scan",
            handler = CancellingHandler("music_scan", store),
        )

        assertEquals(ToolJobStatus.CANCELLED, result.status)
        assertEquals(ToolJobStatus.CANCELLED, store.jobs[result.id]?.status)
        assertEquals("Отменено пользователем", result.logText.lines().last())
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `finished job creates app notification when center is available`() = runBlocking {
        val store = FakeToolJobStore()
        val notifications = FakeNotificationCenter()
        val runner = ToolJobRunner(store, notifications)

        runner.run(
            toolId = "download",
            inputJson = """{"path":"file"}""",
            handler = SuccessHandler("download"),
        )

        assertEquals(1, notifications.requests.size)
        assertEquals("tool_job_success", notifications.requests.single().type)
        assertEquals("Задача завершена", notifications.requests.single().title)
    }

    private class SuccessHandler(
        override val toolId: String,
    ) : ToolHandler {
        override suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult {
            progress.updateProgress(25, "Начато")
            progress.appendLog("Готово")
            return ToolJobResult(
                outputJson = """{"ok":true}""",
                logText = "Успешно",
            )
        }
    }

    private class FailingHandler(
        override val toolId: String,
    ) : ToolHandler {
        override suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult {
            progress.updateProgress(10, "Начато")
            error("boom")
        }
    }

    private class CancellingHandler(
        override val toolId: String,
        private val store: FakeToolJobStore,
    ) : ToolHandler {
        override suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult {
            store.update(
                job.copy(
                    status = ToolJobStatus.CANCELLED,
                    logText = "Отменено пользователем",
                    updatedAt = System.currentTimeMillis(),
                    finishedAt = System.currentTimeMillis(),
                )
            )
            progress.updateProgress(50, "Не должно записаться")
            return ToolJobResult(logText = "Не должно стать успехом")
        }
    }

    private class FakeToolJobStore : ToolJobStore {
        val jobs = linkedMapOf<String, ToolJob>()

        override fun getRecentJobs(limit: Int): Flow<List<ToolJob>> =
            flowOf(jobs.values.take(limit))

        override fun getJobsByStatus(status: ToolJobStatus): Flow<List<ToolJob>> =
            flowOf(jobs.values.filter { it.status == status })

        override suspend fun getJob(id: String): ToolJob? =
            jobs[id]

        override suspend fun countActiveJobs(): Int =
            jobs.values.count {
                it.status == ToolJobStatus.QUEUED ||
                    it.status == ToolJobStatus.RUNNING ||
                    it.status == ToolJobStatus.WAITING_FOR_CONFIRMATION
            }

        override suspend fun insert(job: ToolJob): ToolJob {
            jobs[job.id] = job
            return job
        }

        override suspend fun update(job: ToolJob) {
            jobs[job.id] = job
        }

        override suspend fun deleteFinishedJobs() {
            jobs.entries.removeIf { it.value.finishedAt != null }
        }
    }

    private class FakeNotificationCenter : SollNotificationCenter {
        val requests = mutableListOf<SollNotificationRequest>()

        override fun observeRecent(limit: Int): Flow<List<SollNotification>> = flowOf(emptyList())

        override fun observeUnreadCount(): Flow<Int> = flowOf(0)

        override suspend fun post(request: SollNotificationRequest): SollNotification {
            requests += request
            return SollNotification(
                channel = request.channel,
                type = request.type,
                source = request.source,
                title = request.title,
                message = request.message,
                payloadJson = request.payloadJson,
                priority = request.priority,
            )
        }

        override suspend fun markRead(id: String) = Unit

        override suspend fun markAllRead() = Unit

        override suspend fun dismiss(id: String) = Unit

        override suspend fun deleteAll() = Unit

        override fun ensureChannels() = Unit

        override fun canPostSystemNotifications(): Boolean = true
    }
}
