package com.soll.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAssistantTurnTest {
    @Test
    fun `voice turn is read only and cannot create tasks`() {
        val turn = VoiceAssistantTurn.create(
            transcript = "  Что   важного сегодня? ",
            requestId = "voice-1",
        )

        assertEquals("Что важного сегодня?", turn.content)
        assertTrue(turn.runAssistant)
        assertFalse(turn.taskIntake)
        assertFalse(turn.allowActions)
        assertEquals("android_voice", turn.metadata["source"])
        assertEquals("push_to_talk", turn.metadata["input_mode"])
        assertEquals("read_only", turn.metadata["safety_mode"])
        assertEquals("voice-1", turn.metadata["request_id"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank transcript is rejected`() {
        VoiceAssistantTurn.create(transcript = "  ", requestId = "voice-2")
    }

    @Test
    fun `transcript is bounded before transport`() {
        val turn = VoiceAssistantTurn.create(
            transcript = "я".repeat(5_000),
            requestId = "voice-3",
        )

        assertEquals(MAX_VOICE_TURN_CHARS, turn.content.length)
    }
}
