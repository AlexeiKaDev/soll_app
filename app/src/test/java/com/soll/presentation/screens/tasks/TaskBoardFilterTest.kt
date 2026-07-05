package com.soll.presentation.screens.tasks

import com.soll.domain.soll.SollTask
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
}
