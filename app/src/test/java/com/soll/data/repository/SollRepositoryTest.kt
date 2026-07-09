package com.soll.data.repository

import com.soll.data.api.ChatTaskIntakeItemResponse
import com.soll.data.api.ChatTaskIntakeResponse
import com.soll.data.api.ChatTurnResponse
import com.soll.data.api.ChatMessageResponse
import com.soll.data.api.SollTaskMutationResponse
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class SollRepositoryTest {
    @Test
    fun `normalize base url adds scheme and trailing slash`() {
        assertEquals("http://192.168.1.10:8000/", normalizeSollBaseUrl("192.168.1.10:8000"))
    }

    @Test
    fun `normalize base url preserves https scheme`() {
        assertEquals("https://soll.local/", normalizeSollBaseUrl("https://soll.local"))
    }

    @Test
    fun `normalize base url strips api v1 path from server url`() {
        assertEquals("http://192.168.1.10:49237/", normalizeSollBaseUrl("http://192.168.1.10:49237/api/v1"))
    }

    @Test
    fun `normalize base url strips monolith soll api path from server url`() {
        assertEquals("https://sales.monolith-ost.com/", normalizeSollBaseUrl("https://sales.monolith-ost.com/api/v1/soll"))
    }

    @Test
    fun `normalize base url preserves unknown non api path`() {
        assertEquals("https://example.com/custom/", normalizeSollBaseUrl("https://example.com/custom"))
    }

    @Test
    fun `normalize base url keeps blank as blank`() {
        assertEquals("", normalizeSollBaseUrl("   "))
    }

    @Test
    fun `normalize api prefix keeps blank blank`() {
        assertEquals("", normalizeSollApiPathPrefix("   "))
        assertEquals("api/v1/soll", normalizeSollApiPathPrefix("/api/v1/soll/"))
    }

    @Test
    fun `api prefix rewrites legacy api v1 paths to soll namespace`() {
        val rewritten = rewriteSollApiUrl(
            "https://sales.monolith-ost.com/api/v1/chat/turn?x=1".toHttpUrl(),
            "api/v1/soll",
        )

        assertEquals("https://sales.monolith-ost.com/api/v1/soll/chat/turn?x=1", rewritten.toString())
    }

    @Test
    fun `api prefix does not duplicate already rewritten soll path`() {
        val rewritten = rewriteSollApiUrl(
            "https://sales.monolith-ost.com/api/v1/soll/chat/turn".toHttpUrl(),
            "api/v1/soll",
        )

        assertEquals("https://sales.monolith-ost.com/api/v1/soll/chat/turn", rewritten.toString())
    }

    @Test
    fun `raw note filename keeps readable russian slug`() {
        assertEquals(
            "mobile-19700101-000000-идея-для-soll.md",
            buildRawNoteFilename("Идея для Soll!", timestampMillis = 0L),
        )
    }

    @Test
    fun `raw note content includes metadata and text`() {
        val content = buildRawNoteContent(
            title = "Идея",
            content = "Текст заметки",
            tags = listOf("личное", "задача дня"),
            timestampMillis = 0L,
        )

        org.junit.Assert.assertTrue(content.contains("source: soll_app_android"))
        org.junit.Assert.assertTrue(content.contains("  - личное"))
        org.junit.Assert.assertTrue(content.contains("  - задача-дня"))
        org.junit.Assert.assertTrue(content.contains("# Идея"))
        org.junit.Assert.assertTrue(content.contains("Текст заметки"))
    }

    @Test
    fun `raw upload filename keeps extension and readable slug`() {
        assertEquals(
            "mobile-19700101-000000-фото-1.jpg",
            buildRawUploadFilename("Фото 1.JPG", timestampMillis = 0L),
        )
    }

    @Test
    fun `task mutation response accepts python root task and monolith task wrapper`() {
        val adapter = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(SollTaskMutationResponse::class.java)

        val root = adapter.fromJson(
            """
            {
              "id": "task-1",
              "title": "Start task",
              "status": "in_progress",
              "priority": "A"
            }
            """.trimIndent(),
        )!!.taskResponse()
        val wrapped = adapter.fromJson(
            """
            {
              "success": true,
              "action": {"action_id": "task-action-1"},
              "task": {
                "id": "task-1",
                "title": "Start task",
                "status": "in_progress",
                "priority": "A"
              }
            }
            """.trimIndent(),
        )!!.taskResponse()

        assertEquals("task-1", root.id)
        assertEquals("task-1", wrapped.id)
        assertEquals("in_progress", wrapped.status)
    }

    @Test
    fun `task board fallback keeps only daily open tasks`() {
        val list = taskBoardToDailyTaskList(
            board = SollTaskBoard(
                today = listOf(
                    task("task:daily:today-1", "today", "Call client"),
                    task("project-daily-1", "today", "Project Daily task", projectName = "Daily"),
                ),
                inbox = listOf(task("task:daily:inbox-1", "inbox", "task: Buy milk")),
                stale = listOf(task("stale-1", "stale", "General task", projectName = "AI Core")),
                deferred = emptyList(),
                doneRecent = listOf(task("done-1", "done", "Sent report")),
            ),
            createdTaskId = "task:daily:inbox-1",
            today = "2026-07-09",
        )

        assertEquals("2026-07-09", list.date)
        assertEquals("Android daily fallback", list.sourcePath)
        assertEquals("task:daily:inbox-1", list.createdTaskId)
        assertEquals(listOf("task:daily:today-1", "task:daily:inbox-1"), list.tasks.map { it.id })
        assertEquals("Buy milk", list.tasks[1].text)
        assertEquals(listOf(false, false), list.tasks.map { it.done })
    }

    @Test
    fun `task board fallback keeps just created non daily task`() {
        val list = taskBoardToDailyTaskList(
            board = SollTaskBoard(
                today = listOf(task("created-1", "today", "task: Fresh fallback", projectName = "Inbox")),
                inbox = emptyList(),
                stale = listOf(task("other-1", "stale", "Other task", projectName = "AI Core")),
                deferred = emptyList(),
                doneRecent = emptyList(),
            ),
            createdTaskId = "created-1",
            today = "2026-07-09",
        )

        assertEquals(listOf("created-1"), list.tasks.map { it.id })
        assertEquals("Fresh fallback", list.tasks.single().text)
    }

    @Test
    fun `chat turn task intake id is extracted from top level response`() {
        val taskId = taskIntakeTaskId(
            ChatTurnResponse(
                taskIntake = ChatTaskIntakeResponse(
                    acted = true,
                    items = listOf(ChatTaskIntakeItemResponse(taskId = "task:chat:1")),
                ),
            ),
        )

        assertEquals("task:chat:1", taskId)
    }

    @Test
    fun `chat turn task intake id falls back to assistant metadata`() {
        val taskId = taskIntakeTaskId(
            ChatTurnResponse(
                assistant = ChatMessageResponse(
                    metadata = mapOf(
                        "task_intake" to mapOf(
                            "items" to listOf(mapOf("task_id" to "task:chat:2")),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("task:chat:2", taskId)
    }

    @Test
    fun `daily delete fallback is limited to task board ids`() {
        assertEquals(true, canFallbackDeleteDailyTaskId("task:chat:1"))
        assertEquals(false, canFallbackDeleteDailyTaskId("daily-1"))
        assertEquals(false, canFallbackDeleteDailyTaskId(""))
    }

    private fun task(id: String, status: String, title: String, projectName: String = "Daily"): SollTask =
        SollTask(
            id = id,
            title = title,
            description = "",
            sourceRef = "test",
            projectName = projectName,
            status = status,
            priority = "B",
            dueDate = null,
            tags = emptyList(),
        )
}
