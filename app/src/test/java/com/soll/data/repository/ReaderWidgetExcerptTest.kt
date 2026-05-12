package com.soll.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWidgetExcerptTest {
    @Test
    fun `excerpt returns paragraph around saved position`() {
        val content = "Первый абзац.\n\nВторой абзац с нужной позицией.\n\nТретий абзац."

        assertEquals(
            "Второй абзац с нужной позицией.",
            extractReaderWidgetExcerpt(content, content.indexOf("нужной"), maxLength = 120),
        )
    }

    @Test
    fun `excerpt falls back to sentence window for flattened epub text`() {
        val content = "Первая фраза. Последний сохраненный абзац в старом EPUB без переносов. Следующая фраза."

        val excerpt = extractReaderWidgetExcerpt(content, content.indexOf("сохраненный"), maxLength = 120)

        assertTrue(excerpt.contains("Последний сохраненный абзац"))
        assertTrue(excerpt.length <= 120)
    }

    @Test
    fun `excerpt is trimmed for widget line`() {
        val content = "Очень длинный текст ".repeat(30)

        val excerpt = extractReaderWidgetExcerpt(content, 40, maxLength = 48)

        assertTrue(excerpt.length <= 49)
        assertTrue(excerpt.endsWith("…"))
    }
}
