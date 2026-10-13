package com.soll.presentation.screens.tasks

import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskInput
import com.soll.domain.soll.SollTaskProject
import org.junit.Assert.*
import org.junit.Test

class TaskWorkspaceBehaviorTest {
    private fun task(id: String, title: String, projectName: String? = null) = SollTask(
        id = id, title = title, description = "", sourceRef = "manual",
        projectName = projectName, status = "inbox", priority = "B",
        dueDate = null, tags = emptyList(),
    )

    @Test fun `editor validates a title and real date without rejecting multiline descriptions`() {
        assertNotNull(taskInputError(SollTaskInput(" ")))
        assertNotNull(taskInputError(SollTaskInput("Работа", dueDate = "2026-02-30")))
        assertNull(taskInputError(SollTaskInput("Работа", "Строка 1\nСтрока 2", dueDate = "2026-10-20")))
        assertNull(taskInputError(SollTaskInput("Работа", dueDate = "")))
    }

    @Test fun `remote page determines load more beyond the old board cap`() {
        val state = TaskBoardUiState(pageTasks = listOf(task(id = "late", title = "Последняя задача")),
            pageTotal = 1500, pageHasMore = true, requestedTaskBoardLimitPerSection = 500).deriveTaskList()
        assertTrue(state.canLoadMoreTasks)
        assertEquals(listOf("late"), state.visibleTasks.map { it.id })
        assertFalse(state.copy(pageHasMore = false).canLoadMoreTasks)
    }

    @Test fun `cached tasks keep project filtering by canonical name`() {
        val state = TaskBoardUiState(inbox = listOf(
            task(id = "one", title = "Работа", projectName = "Soll"),
            task(id = "two", title = "Другое", projectName = "Other")),
            projects = listOf(SollTaskProject("soll-project", "Soll")), selectedProjectId = "soll-project").deriveTaskList()
        assertEquals(listOf("one"), state.visibleTasks.map { it.id })
    }

    @Test fun `all existing sections and task actions remain available`() {
        assertEquals(8, TaskTab.entries.map { it.label() }.distinct().size)
        val actions = taskActionVisibility("inbox", "task:android:one")
        assertTrue(actions.canStart && actions.canComplete && actions.canDefer && actions.canReject && actions.canMoveToToday)
        assertFalse(taskActionVisibility("done", "task-one").canComplete)
    }
}
