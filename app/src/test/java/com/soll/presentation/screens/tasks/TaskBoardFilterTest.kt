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
            assignedNodeId = "soll-home",
            requiredCapabilities = listOf("android_adb"),
            routingState = "waiting_for_android_adb_node",
        )

        assertTrue(task.matchesTaskQuery("CLOSED APP"))
        assertTrue(task.matchesTaskQuery("soll app"))
        assertTrue(task.matchesTaskQuery("manual/android"))
        assertTrue(task.matchesTaskQuery("notifications"))
        assertTrue(task.matchesTaskQuery("in_progress"))
        assertTrue(task.matchesTaskQuery("soll-home"))
        assertTrue(task.matchesTaskQuery("android_adb"))
        assertTrue(task.matchesTaskQuery("waiting_for_android"))
        assertTrue(task.hasRoutingContext())
        assertFalse(task.matchesTaskQuery("roadmap"))
    }

    @Test
    fun `task summary counts open tasks with routing context`() {
        val state = TaskBoardUiState(
            today = listOf(task(id = "routed-today", status = "today", routingState = "delegated_active")),
            blocked = listOf(task(id = "routed-blocked", status = "blocked", assignedNodeId = "soll-home")),
            doneRecent = listOf(task(id = "routed-done", status = "done", assignedNodeId = "soll-home")),
        )

        assertEquals(2, state.routedOpenTaskCount)
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

    @Test
    fun `held task reason is concise and strips technical prefix`() {
        val held = task(id = "blocked-1", status = "blocked").copy(
            executionPhase = "needs_user",
            executionReason = "blocked: Android device is not connected and the hardware smoke cannot run",
        )

        assertEquals(
            "Android device is not connected and the hardware smoke cannot run",
            held.shortHoldReason(),
        )
        assertTrue(held.copy(executionReason = "x".repeat(200)).shortHoldReason()!!.length <= 140)
    }

    @Test
    fun `held task reason uses clear routing and status fallbacks`() {
        assertEquals(
            "Ожидает подключение Android-устройства.",
            task(
                id = "routed-1",
                status = "in_progress",
                routingState = "waiting_for_android_adb_node",
            ).shortHoldReason(),
        )
        assertEquals(
            "Отложена вручную; причина не указана.",
            task(id = "deferred-1", status = "deferred").shortHoldReason(),
        )
        assertEquals(
            "Нет обновлений дольше установленного срока.",
            task(id = "stale-1", status = "stale").shortHoldReason(),
        )
        assertEquals(null, task(id = "today-1", status = "today").shortHoldReason())
    }

    @Test
    fun `held task reason explains autonomous scope and source deferral`() {
        assertEquals(
            "Проект «MonoSales» не разрешен для автономного выполнения.",
            task(id = "scope-1", status = "blocked").copy(
                executionState = "external_blocked: Задача заблокирована.; " +
                    "Scope 'MonoSales' не входит в autonomous allowlist (soll, soll_app).",
            ).shortHoldReason(),
        )
        assertEquals(
            "Отложено до проверки источника.",
            task(id = "source-1", status = "deferred").copy(
                executionState = "source_review_deferred",
            ).shortHoldReason(),
        )
    }

    private fun task(
        id: String,
        status: String,
        assignedNodeId: String? = null,
        routingState: String = "",
    ): SollTask =
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
            assignedNodeId = assignedNodeId,
            routingState = routingState,
        )
}
