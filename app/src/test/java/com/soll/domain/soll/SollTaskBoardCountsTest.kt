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

    private fun task(id: String, status: String): SollTask =
        SollTask(
            id = id,
            title = "Task $id",
            description = "",
            sourceRef = "test",
            projectName = "Soll",
            status = status,
            priority = "B",
            dueDate = null,
            tags = emptyList(),
        )
}
