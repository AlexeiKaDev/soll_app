package com.soll.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedLinkParserTest {
    @Test
    fun `extracts first http link and keeps explicit title`() {
        val parsed = SharedLinkParser.parse(
            sharedText = "Read https://example.com/first then https://example.org/second",
            explicitTitle = "  Edge article  ",
            clientId = "share-1",
        )

        assertEquals("https://example.com/first", parsed.url)
        assertEquals("Edge article", parsed.title)
        assertEquals("share-1", parsed.clientId)
        assertTrue(parsed.canSubmit)
        assertNull(parsed.validationError)
    }

    @Test
    fun `derives title and removes prose punctuation after url`() {
        val parsed = SharedLinkParser.parse(
            sharedText = "Portable inference update — https://example.com/article?q=1).",
            explicitTitle = null,
            clientId = "share-2",
        )

        assertEquals("https://example.com/article?q=1", parsed.url)
        assertEquals("Portable inference update", parsed.title)
        assertTrue(parsed.canSubmit)
    }

    @Test
    fun `keeps balanced closing parenthesis in url`() {
        val parsed = SharedLinkParser.parse(
            sharedText = "https://example.com/wiki/Test_(device)",
            explicitTitle = null,
            clientId = "share-3",
        )

        assertEquals("https://example.com/wiki/Test_(device)", parsed.url)
    }

    @Test
    fun `removes typographic quote around shared url`() {
        val parsed = SharedLinkParser.parse(
            sharedText = "Ссылка «https://example.com/news»",
            explicitTitle = null,
            clientId = "share-quote",
        )

        assertEquals("https://example.com/news", parsed.url)
        assertEquals("Ссылка", parsed.title)
    }

    @Test
    fun `rejects missing unsafe and credential urls`() {
        val missing = SharedLinkParser.parse("javascript:alert(1)", null, "share-4")
        val credentials = SharedLinkParser.parse("https://user:pass@example.com/private", null, "share-5")

        assertFalse(missing.canSubmit)
        assertEquals("Не найдена ссылка HTTP(S)", missing.validationError)
        assertFalse(credentials.canSubmit)
        assertTrue(credentials.validationError.orEmpty().contains("небезопасный"))
    }

    @Test
    fun `bounds shared text title and client id`() {
        val parsed = SharedLinkParser.parse(
            sharedText = "https://example.com " + "x".repeat(SharedLinkParser.MAX_SHARED_TEXT_LENGTH * 2),
            explicitTitle = "T".repeat(SharedLinkParser.MAX_TITLE_LENGTH * 2),
            clientId = "c".repeat(200),
        )

        assertEquals(SharedLinkParser.MAX_SHARED_TEXT_LENGTH, parsed.sharedText.length)
        assertEquals(SharedLinkParser.MAX_TITLE_LENGTH, parsed.title.length)
        assertEquals(SharedLinkParser.MAX_CLIENT_ID_LENGTH, parsed.clientId.length)
        assertEquals(80, parsed.clientId.length)
    }
}
