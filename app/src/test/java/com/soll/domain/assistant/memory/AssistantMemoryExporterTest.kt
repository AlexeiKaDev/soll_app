package com.soll.domain.assistant.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMemoryExporterTest {
    @Test
    fun exportUsesRussianSectionsAndStableDetails() {
        val markdown = AssistantMemoryExporter.toMarkdown(
            listOf(
                AssistantMemory(
                    category = AssistantMemoryCategory.SUGGESTION,
                    key = "suggestion:bot_stopped",
                    title = "Запустить фонового бота",
                    summary = "Пользователь принял предложение запустить сервис.",
                    source = "home.proactive",
                    confidence = 0.94f,
                    createdAt = 10L,
                    updatedAt = 20L,
                )
            )
        )

        assertTrue(markdown.contains("# Память Soll"))
        assertTrue(markdown.contains("## Принятые предложения"))
        assertTrue(markdown.contains("Запустить фонового бота"))
        assertTrue(markdown.contains("Источник: home.proactive"))
    }

    @Test
    fun exportHandlesEmptyMemory() {
        val markdown = AssistantMemoryExporter.toMarkdown(emptyList())

        assertTrue(markdown.contains("Память пока пуста"))
    }

    @Test
    fun serverSummaryExcludesRawPayload() {
        val markdown = AssistantMemoryExporter.toServerSummaryMarkdown(
            listOf(
                AssistantMemory(
                    category = AssistantMemoryCategory.SUGGESTION,
                    key = "suggestion:notifications_disabled",
                    title = "Разрешить уведомления",
                    summary = "Пользователь принял предложение открыть настройки уведомлений.",
                    source = "home.proactive",
                    confidence = 0.86f,
                    payloadJson = """{"private":"raw payload should stay local"}""",
                    createdAt = 10L,
                    updatedAt = 20L,
                )
            )
        )

        assertTrue(markdown.contains("Summary памяти Soll App"))
        assertTrue(markdown.contains("Сырые логи, payload JSON и медиа не включены"))
        assertTrue(markdown.contains("Разрешить уведомления"))
        assertTrue(!markdown.contains("raw payload should stay local"))
    }
}
