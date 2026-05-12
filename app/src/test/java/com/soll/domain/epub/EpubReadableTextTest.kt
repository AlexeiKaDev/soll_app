package com.soll.domain.epub

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReadableTextTest {
    @Test
    fun `html extraction preserves paragraph breaks`() {
        val html = """
            <html><body>
                <h1>Глава</h1>
                <p>Первый абзац.</p>
                <p>Второй <strong>абзац</strong>.</p>
            </body></html>
        """.trimIndent()

        val text = extractReadableTextFromHtml(html)

        assertTrue(text.contains("Первый абзац.\n\nВторой абзац."))
        assertFalse(text.contains("Первый абзац. Второй абзац."))
    }
}
