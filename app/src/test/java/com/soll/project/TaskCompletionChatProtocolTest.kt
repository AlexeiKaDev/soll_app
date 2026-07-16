package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionChatProtocolTest {
    @Test
    fun `completion chat report requires concrete delivery facts`() {
        val guidance = projectFile("CLAUDE.md").readText()
        val protocol = projectFile("docs/task-completion-chat-protocol.md").readText()

        assertTrue(guidance.contains("docs/task-completion-chat-protocol.md"))
        val requiredFields = listOf(
            "Статус:",
            "Сделано:",
            "Изменённые файлы:",
            "Проверки:",
            "Git:",
            "коммит:",
            "push:",
            "Перезапуск:",
            "сервер:",
            "приложение:",
            "Важно:",
        )
        var previousIndex = -1
        requiredFields.forEach { requiredField ->
            val currentIndex = protocol.indexOf(requiredField)
            assertTrue("Missing completion field: $requiredField", currentIndex >= 0)
            assertTrue("Completion field is out of order: $requiredField", currentIndex > previousIndex)
            previousIndex = currentIndex
        }
        assertTrue(protocol.contains("Сообщение `Сделал задачу` без фактов не считается отчётом"))
        assertTrue(protocol.contains("не утверждать, что изменения опубликованы"))
    }

    @Test
    fun `server reload procedure preserves verification order`() {
        val protocol = projectFile("docs/task-completion-chat-protocol.md").readText()
        val orderedSteps = listOf(
            "1. **Зафиксировать состояние до перезагрузки.**",
            "2. **Проверить готовность изменения.**",
            "3. **Выполнить утверждённый reload.**",
            "4. **Дождаться readiness.**",
            "5. **Выполнить smoke-проверку.**",
            "6. **Проверить ошибки и завершить отчёт.**",
        )

        var previousIndex = -1
        orderedSteps.forEach { step ->
            val currentIndex = protocol.indexOf(step)
            assertTrue("Missing reload step: $step", currentIndex >= 0)
            assertTrue("Reload step is out of order: $step", currentIndex > previousIndex)
            previousIndex = currentIndex
        }
        assertTrue(protocol.contains("нельзя"))
        assertTrue(protocol.contains("угадывать имя процесса"))
        assertTrue(protocol.contains("контрактный тест не заменяет live health/readiness"))
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: break
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }
}
