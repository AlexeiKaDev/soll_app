package com.soll.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SollRepositoryTest {
    @Test
    fun `normalize base url adds scheme and trailing slash`() {
        assertEquals("http://192.168.1.10:8000/", normalizeSollBaseUrl("192.168.1.10:8000"))
    }

    @Test
    fun `normalize base url preserves https scheme`() {
        assertEquals("https://soll.local/", normalizeSollBaseUrl("https://soll.local"))
    }

    @Test
    fun `normalize base url keeps blank as blank`() {
        assertEquals("", normalizeSollBaseUrl("   "))
    }

    @Test
    fun `raw note filename keeps readable russian slug`() {
        assertEquals(
            "mobile-19700101-000000-идея-для-soll.md",
            buildRawNoteFilename("Идея для Soll!", timestampMillis = 0L),
        )
    }

    @Test
    fun `raw note content includes metadata and text`() {
        val content = buildRawNoteContent(
            title = "Идея",
            content = "Текст заметки",
            tags = listOf("личное", "задача дня"),
            timestampMillis = 0L,
        )

        org.junit.Assert.assertTrue(content.contains("source: soll_app_android"))
        org.junit.Assert.assertTrue(content.contains("  - личное"))
        org.junit.Assert.assertTrue(content.contains("  - задача-дня"))
        org.junit.Assert.assertTrue(content.contains("# Идея"))
        org.junit.Assert.assertTrue(content.contains("Текст заметки"))
    }

    @Test
    fun `raw upload filename keeps extension and readable slug`() {
        assertEquals(
            "mobile-19700101-000000-фото-1.jpg",
            buildRawUploadFilename("Фото 1.JPG", timestampMillis = 0L),
        )
    }
}
