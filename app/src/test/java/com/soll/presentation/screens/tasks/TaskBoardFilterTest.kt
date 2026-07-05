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

    @Test
    fun `task action visibility blocks every button when task id is missing`() {
        val visibility = taskActionVisibility(status = "blocked", taskId = " ")

        assertFalse(visibility.hasTaskId)
        assertFalse(visibility.canMoveToToday)
        assertFalse(visibility.canStart)
        assertFalse(visibility.canComplete)
        assertFalse(visibility.canDefer)
        assertFalse(visibility.canReject)
        assertEquals("Task without id:test:blocked", task(id = " ", status = "blocked").taskListKey())
    }

    @Test
    fun `task action visibility covers all task buttons by status`() {
        val inbox = taskActionVisibility(status = "inbox", taskId = "task-1")
        assertTrue(inbox.canMoveToToday)
        assertTrue(inbox.canStart)
        assertTrue(inbox.canComplete)
        assertTrue(inbox.canDefer)
        assertTrue(inbox.canReject)

        val started = taskActionVisibility(status = "in_progress", taskId = "task-1")
        assertFalse(started.canMoveToToday)
        assertFalse(started.canStart)
        assertTrue(started.canComplete)
        assertTrue(started.canDefer)
        assertTrue(started.canReject)

        val deferred = taskActionVisibility(status = "deferred", taskId = "task-1")
        assertTrue(deferred.canMoveToToday)
        assertTrue(deferred.canStart)
        assertTrue(deferred.canComplete)
        assertFalse(deferred.canDefer)
        assertTrue(deferred.canReject)

        val done = taskActionVisibility(status = "done", taskId = "task-1")
        assertFalse(done.canMoveToToday)
        assertFalse(done.canStart)
        assertFalse(done.canComplete)
        assertFalse(done.canDefer)
        assertFalse(done.canReject)

        val rejected = taskActionVisibility(status = "rejected", taskId = "task-1")
        assertFalse(rejected.canMoveToToday)
        assertFalse(rejected.canStart)
        assertFalse(rejected.canComplete)
        assertFalse(rejected.canDefer)
        assertFalse(rejected.canReject)
    }

    private fun task(id: String, status: String): SollTask =
        SollTask(
            id = id,
            title = "Task ${id.ifBlank { "without id" }.trim()}",
            description = "",
            sourceRef = "test",
            projectName = "Soll",
            status = status,
            priority = "B",
            dueDate = null,
            tags = emptyList(),
        )
}
