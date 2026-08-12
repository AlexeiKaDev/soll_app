package com.soll.data.repository

import com.soll.data.api.SollTaskMutationResponse
import com.soll.domain.soll.SollLearningItem
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollSourceScope
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import com.soll.domain.soll.withoutDailyTodoTasks
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
    fun `api prefix preserves encoded task id segments`() {
        val rewritten = rewriteSollApiUrl(
            "https://sales.monolith-ost.com/api/v1/daily/tasks/today/task%3Adaily%3A20260709%3Aabc".toHttpUrl(),
            "api/v1/soll",
        )

        assertEquals(
            "https://sales.monolith-ost.com/api/v1/soll/daily/tasks/today/task%3Adaily%3A20260709%3Aabc",
            rewritten.toString(),
        )
    }

    @Test
    fun `api prefix keeps canonical public roadmap path without trailing slash`() {
        val rewritten = rewriteSollApiUrl(
            "https://sales.monolith-ost.com/api/v1/roadmap".toHttpUrl(),
            "api/v1/soll",
        )

        assertEquals(
            "https://sales.monolith-ost.com/api/v1/soll/roadmap",
            rewritten.toString(),
        )
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
              "priority": "A",
              "completion_kind": "verification",
              "completion_result": "verified",
              "completion_evidence": ["test:focused", "artifact:report.md"]
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
        assertEquals("verification", root.completionKind)
        assertEquals("verified", root.completionResult)
        assertEquals(listOf("test:focused", "artifact:report.md"), root.completionEvidence)
        assertEquals("task-1", wrapped.id)
        assertEquals("in_progress", wrapped.status)
    }

    @Test
    fun `task board drops daily todo origin from every section`() {
        val board = SollTaskBoard(
            today = listOf(
                task("task-1", "today", "Project task"),
                task("task:daily:today-1", "today", "Daily marker"),
            ),
            blocked = listOf(task("task-2", "blocked", "Blocked")),
            inbox = listOf(task("task-3", "inbox", "Inbox", sourceRef = "android_daily_todo")),
            stale = listOf(task("task-4", "stale", "Stale", tags = listOf("daily_todo"))),
            deferred = listOf(task("task-5", "deferred", "Deferred")),
            doneRecent = listOf(task("task-6", "done", "Done", sourceRef = "project")),
        ).withoutDailyTodoTasks()

        assertEquals(listOf("task-1"), board.today.map { it.id })
        assertEquals(listOf("task-2"), board.blocked.map { it.id })
        assertEquals(emptyList<String>(), board.inbox.map { it.id })
        assertEquals(emptyList<String>(), board.stale.map { it.id })
        assertEquals(listOf("task-5"), board.deferred.map { it.id })
        assertEquals(listOf("task-6"), board.doneRecent.map { it.id })
        assertEquals(4, board.totalCount)
    }

    @Test
    fun `source scope filter separates project and daily sources`() {
        val sources = listOf(
            source("project-1", SollSourceScope.PROJECT_SOLL, "project"),
            source("daily-1", SollSourceScope.DAILY_TODO, "daily"),
            source("legacy-daily", SollSourceScope.PROJECT_SOLL, "legacy", tags = listOf("daily_todo")),
        )

        assertEquals(
            listOf("project-1"),
            sources.filterForSourceScope(SollSourceScope.PROJECT_SOLL).map { it.id },
        )
        assertEquals(
            listOf("daily-1", "legacy-daily"),
            sources.filterForSourceScope(SollSourceScope.DAILY_TODO).map { it.id },
        )
    }

    @Test
    fun `learning items detect daily todo origin`() {
        assertEquals(false, learning("project-1", sourceRef = "project/source").isDailyTodoOrigin())
        assertEquals(true, learning("daily-1", sourceRef = "daily_todo/source").isDailyTodoOrigin())
        assertEquals(true, learning("project-2", tags = listOf("android_daily_todo")).isDailyTodoOrigin())
    }

    private fun task(
        id: String,
        status: String,
        title: String,
        projectName: String = "Soll",
        sourceRef: String = "test",
        tags: List<String> = emptyList(),
    ): SollTask =
        SollTask(
            id = id,
            title = title,
            description = "",
            sourceRef = sourceRef,
            projectName = projectName,
            status = status,
            priority = "B",
            dueDate = null,
            tags = tags,
        )

    private fun source(
        id: String,
        scope: SollSourceScope,
        target: String,
        tags: List<String> = emptyList(),
    ): SollMonitoredSource =
        SollMonitoredSource(
            id = id,
            name = id,
            sourceType = "web",
            scope = scope,
            target = target,
            description = "",
            tags = tags,
            enabled = true,
            lastResult = "",
            itemsSeen = 0,
            newItemsLastCheck = 0,
        )

    private fun learning(
        id: String,
        sourceRef: String = "",
        tags: List<String> = emptyList(),
    ): SollLearningItem =
        SollLearningItem(
            id = id,
            title = id,
            status = "pending",
            nextAction = "",
            sourceRef = sourceRef,
            seenCount = 0,
            tags = tags,
        )
}
