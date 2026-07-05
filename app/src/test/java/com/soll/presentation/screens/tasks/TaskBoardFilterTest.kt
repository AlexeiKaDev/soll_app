package com.soll.presentation.screens.tasks

import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoardCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBoardFilterTest {
    @Test
    fun `task query matches fields without building a combined search string`() {
        val task = SollTask(
            id = "task-1",
            title = "Android push follow-up",
            description = "Check closed app notification delivery",
            sourceRef = "manual/android-fcm",
            projectName = "Soll app",
            status = "in_progress",
            priority = "A",
            dueDate = null,
            tags = listOf("push", "notifications"),
        )

        assertTrue(task.matchesTaskQuery("CLOSED APP"))
        assertTrue(task.matchesTaskQuery("soll app"))
        assertTrue(task.matchesTaskQuery("manual/android"))
        assertTrue(task.matchesTaskQuery("notifications"))
        assertTrue(task.matchesTaskQuery("in_progress"))
        assertFalse(task.matchesTaskQuery("roadmap"))
    }

    @Test
    fun `all tab lists only open tasks while done stays in done tab`() {
        val base = TaskBoardUiState(
            blocked = listOf(task(id = "blocked-1", status = "blocked")),
            doneRecent = listOf(task(id = "done-1", status = "done")),
            taskCounts = SollTaskBoardCounts(blocked = 1, doneRecent = 10),
            selectedTab = TaskTab.ALL,
        ).rebuildTaskIndex().deriveTaskList()

        assertEquals(listOf("blocked-1"), base.visibleTasks.map { it.id })
        assertEquals(1, base.selectedDisplayedTaskCount)
        assertEquals(1, base.selectedTaskCount)
        assertFalse(base.canLoadMoreTasks)

        val done = base.copy(selectedTab = TaskTab.DONE)
            .deriveTaskList()

        assertEquals(listOf("done-1"), done.visibleTasks.map { it.id })
        assertEquals(1, done.selectedDisplayedTaskCount)
        assertEquals(10, done.selectedTaskCount)
        assertTrue(done.canLoadMoreTasks)
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
