package com.soll.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActivationPolicyTest {
    private val policy = VoiceActivationPolicy()

    @Test
    fun `passes text unchanged when wake phrase is not required`() {
        val decision = policy.prepare("включи музыку", requireWakePhrase = false)

        assertTrue(decision.accepted)
        assertEquals("включи музыку", decision.commandText)
    }

    @Test
    fun `strips russian wake phrase from command`() {
        val decision = policy.prepare("Солл, включи музыку", requireWakePhrase = true)

        assertTrue(decision.accepted)
        assertEquals("включи музыку", decision.commandText)
        assertEquals("солл", decision.matchedPhrase)
    }

    @Test
    fun `keeps command casing after wake phrase`() {
        val decision = policy.prepare("Солл, создай заметку Купить Молоко", requireWakePhrase = true)

        assertTrue(decision.accepted)
        assertEquals("создай заметку Купить Молоко", decision.commandText)
    }

    @Test
    fun `supports ok soll wake phrase`() {
        val decision = policy.prepare("Ок Солл какие задачи сегодня", requireWakePhrase = true)

        assertTrue(decision.accepted)
        assertEquals("какие задачи сегодня", decision.commandText)
        assertEquals("ок солл", decision.matchedPhrase)
    }

    @Test
    fun `rejects command without wake phrase when required`() {
        val decision = policy.prepare("включи музыку", requireWakePhrase = true)

        assertFalse(decision.accepted)
        assertEquals("включи музыку", decision.commandText)
    }

    @Test
    fun `rejects wake phrase without command`() {
        val decision = policy.prepare("Солл", requireWakePhrase = true)

        assertFalse(decision.accepted)
        assertEquals("солл", decision.matchedPhrase)
    }
}
