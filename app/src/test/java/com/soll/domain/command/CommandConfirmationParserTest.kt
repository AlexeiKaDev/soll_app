package com.soll.domain.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandConfirmationParserTest {
    @Test
    fun `parse detects trailing confirm and strips it`() {
        val parsed = CommandConfirmationParser.parse("+1234567890 текст --confirm")

        assertTrue(parsed.confirmed)
        assertEquals("+1234567890 текст", parsed.args)
    }

    @Test
    fun `parse does not treat middle confirm as confirmation`() {
        val parsed = CommandConfirmationParser.parse("--confirm текст")

        assertFalse(parsed.confirmed)
        assertEquals("--confirm текст", parsed.args)
    }

    @Test
    fun `parse returns null args when only confirm is present`() {
        val parsed = CommandConfirmationParser.parse("--confirm")

        assertTrue(parsed.confirmed)
        assertNull(parsed.args)
    }
}
