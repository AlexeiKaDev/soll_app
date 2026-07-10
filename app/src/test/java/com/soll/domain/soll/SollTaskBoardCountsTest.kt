package com.soll.domain.soll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SollTaskBoardCountsTest {
    @Test
    fun `server counts keep total open count separate from displayed tasks`() {
        val board = SollTaskBoard(
            today = listOf(task(id = "today-1", status = "today")),
            blocked = emptyList(),
            inbox = emptyList(),
            stale = emptyList(),
            deferred = emptyList(),
            doneRecent = listOf(task(id = "done-1", status = "done")),
            counts = SollTaskBoardCounts(
                today = 3,
                blocked = 1,
                inbox = 2,
                stale = 0,
                deferred = 0,
                doneRecent = 10,
            ),
            limitPerSection = 1,
        )

        assertEquals(6, board.openCount)
        assertEquals(1, board.displayedOpenCount)
        assertEquals(10, board.doneCount)
        assertEquals(1, board.displayedDoneCount)
        assertEquals(16, board.totalCount)
        assertEquals(2, board.displayedTotalCount)
        assertTrue(board.hasLimitedOpenSections)
        assertTrue(board.hasLimitedDoneSection)
        assertTrue(board.hasLimitedSections)
    }

    @Test
    fun `daily todo tasks are removed from project task board`() {
        val board = SollTaskBoard(
            today = listOf(task(id = "task:daily:1", status = "today"), task(id = "task-1", status = "today")),
            blocked = listOf(task(id = "task-2", status = "blocked")),
            inbox = listOf(task(id = "task-3", status = "inbox", sourceRef = "android_daily_todo")),
            stale = listOf(task(id = "task-4", status = "stale", tags = listOf("daily_todo"))),
            deferred = listOf(task(id = "task-5", status = "deferred")),
            doneRecent = listOf(task(id = "task-6", status = "done")),
        ).withoutDailyTodoTasks()

        assertEquals(listOf("task-1"), board.today.map { it.id })
        assertEquals(listOf("task-2"), board.blocked.map { it.id })
        assertEquals(emptyList<String>(), board.inbox.map { it.id })
        assertEquals(emptyList<String>(), board.stale.map { it.id })
        assertEquals(listOf("task-5"), board.deferred.map { it.id })
        assertEquals(listOf("task-6"), board.doneRecent.map { it.id })
        assertEquals(4, board.totalCount)
    }

    private fun task(
        id: String,
        status: String,
        sourceRef: String = "test",
        tags: List<String> = emptyList(),
    ): SollTask =
        SollTask(
            id = id,
            title = "Task $id",
            description = "",
            sourceRef = sourceRef,
            projectName = "Soll",
            status = status,
            priority = "B",
            dueDate = null,
            tags = tags,
        )
}
