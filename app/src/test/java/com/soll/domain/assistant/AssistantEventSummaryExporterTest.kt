package com.soll.domain.assistant

import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEventSummaryExporterTest {
    @Test
    fun serverSummaryExcludesPayloadJsonAndKeepsSafeFields() {
        val markdown = AssistantEventSummaryExporter.toServerSummaryMarkdown(
            listOf(
                AssistantEvent(
                    type = "proactive_suggestion_accepted",
                    source = "home",
                    summary = "Принято предложение запустить бота",
                    payloadJson = """{"private":"raw payload should stay local"}""",
                    createdAt = 1_700_000_000_000L,
                )
            )
        )

        assertTrue(markdown.contains("Summary событий Soll App"))
        assertTrue(markdown.contains("Payload JSON, Telegram-сообщения, медиа и сырые логи не включены"))
        assertTrue(markdown.contains("proactive_suggestion_accepted"))
        assertTrue(markdown.contains("Принято предложение запустить бота"))
        assertTrue(!markdown.contains("raw payload should stay local"))
    }

    @Test
    fun serverSummaryHandlesEmptyEvents() {
        val markdown = AssistantEventSummaryExporter.toServerSummaryMarkdown(emptyList())

        assertTrue(markdown.contains("Событий ассистента пока нет"))
    }
}
