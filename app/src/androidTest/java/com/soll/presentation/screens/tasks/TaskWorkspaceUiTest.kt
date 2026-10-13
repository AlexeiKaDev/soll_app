package com.soll.presentation.screens.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskInput
import com.soll.domain.soll.SollRoadmapLine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskWorkspaceUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun archivedRoadmapLineKeepsItsDescriptionWithoutTaskCreation() {
        compose.setContent { MaterialTheme {
            RoadmapLineRow(SollRoadmapLine("internal-line-id", "Сохранённый пункт", "declined", "Сохранён в истории"),
                false, { fail("An archived line must not create tasks") })
        } }
        compose.onNodeWithText("В архиве").assertIsDisplayed()
        compose.onNodeWithText("Сохранённый пункт").assertIsDisplayed()
        compose.onNodeWithText("Сохранён в истории").assertIsDisplayed()
        compose.onNodeWithText("В задачу").assertDoesNotExist()
        compose.onNodeWithText("internal-line-id").assertDoesNotExist()
    }

    @Test fun compactRowOpensDetailsWithoutShowingTheEntireDescription() {
        var opened = false
        compose.setContent { MaterialTheme {
            CompactTaskRow(SollTask(id = "test-only", title = "Проверить заказ", description = "Описание скрыто до открытия",
                status = "inbox", priority = "B", projectName = "Soll", sourceRef = "manual/test", dueDate = null, tags = emptyList()), pending = false) { opened = true }
        } }
        compose.onNodeWithText("Описание скрыто до открытия").assertDoesNotExist()
        compose.onNodeWithText("Проверить заказ").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun editorKeepsInputOnErrorAndHasAWorkingSaveAction() {
        var saved = ""
        compose.setContent { MaterialTheme {
            var input by remember { mutableStateOf(SollTaskInput("")) }
            TaskEditorSheet(input, emptyList(), false, false, "Нет связи. Повтори попытку.",
                onChange = { input = it }, onDismiss = {}, onSave = { saved = input.title })
        } }
        compose.onNodeWithText("Название *").performTextInput("Позвонить клиенту")
        compose.onNodeWithText("Нет связи. Повтори попытку.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Создать задачу").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Позвонить клиенту", saved) }
    }

    @Test fun detailsRetainEveryExistingActionIncludingAttachments() {
        val calls = mutableSetOf<String>()
        compose.setContent { MaterialTheme {
            TaskDetailSheet(SollTask(id = "test-only", title = "Проверить заказ", status = "inbox", priority = "B",
                description = "", sourceRef = "manual/test", projectName = "Soll", dueDate = null, tags = emptyList()),
                false, false, false, false, {}, { calls += "edit" }, { calls += "attach" },
                { calls += "today" }, { calls += "start" }, { calls += "done" }, { calls += "defer" }, { calls += "reject" })
        } }
        listOf("Сегодня", "Начать", "Готово", "Отложить", "Изменить", "Вложить").forEach {
            compose.onNodeWithText(it).performScrollTo().performClick()
        }
        compose.onNodeWithText("Отклонить").performScrollTo().performClick()
        compose.onNodeWithText("Отклонить задачу?").assertIsDisplayed()
        compose.onAllNodesWithText("Отклонить").onLast().performClick()
        compose.runOnIdle { assertEquals(setOf("today", "start", "done", "defer", "edit", "attach", "reject"), calls) }
    }
}
