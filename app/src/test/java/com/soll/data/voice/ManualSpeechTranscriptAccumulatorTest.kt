package com.soll.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class ManualSpeechTranscriptAccumulatorTest {
    @Test
    fun `keeps visible partial when recognizer returns an empty final result`() {
        val transcript = ManualSpeechTranscriptAccumulator()

        transcript.updatePartial("  финальный   голосовой тест Soll работает  ")
        transcript.commitResult(null)

        assertEquals("финальный голосовой тест Soll работает", transcript.text())
    }

    @Test
    fun `prefers a non-empty final result over its partial hypothesis`() {
        val transcript = ManualSpeechTranscriptAccumulator()

        transcript.updatePartial("финальный голосовой")
        transcript.commitResult("финальный голосовой тест")

        assertEquals("финальный голосовой тест", transcript.text())
    }

    @Test
    fun `preserves pending speech before a retry without duplicating the same segment`() {
        val transcript = ManualSpeechTranscriptAccumulator()

        transcript.updatePartial("Soll работает")
        transcript.commitPendingPartial()
        transcript.updatePartial("Soll работает")
        transcript.commitResult("")

        assertEquals("Soll работает", transcript.text())
    }
}
