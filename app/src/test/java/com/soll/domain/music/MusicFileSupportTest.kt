package com.soll.domain.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFileSupportTest {
    @Test
    fun `accepts common audio mime types and extensions`() {
        assertTrue(MusicFileSupport.isSupported("track.mp3", null))
        assertTrue(MusicFileSupport.isSupported("lossless.flac", "application/octet-stream"))
        assertTrue(MusicFileSupport.isSupported("stream.bin", "audio/ogg"))
        assertTrue(MusicFileSupport.isSupported("podcast.ogg", "application/ogg"))
    }

    @Test
    fun `strict mode rejects unknown audio mime without known extension`() {
        assertFalse(MusicFileSupport.isSupported("stream.bin", "audio/unknown-codec"))
        assertTrue(MusicFileSupport.isSupported("stream.bin", "audio/unknown-codec", strict = false))
    }

    @Test
    fun `rejects non audio files`() {
        assertFalse(MusicFileSupport.isSupported("cover.jpg", "image/jpeg"))
        assertFalse(MusicFileSupport.isSupported("notes.txt", "text/plain"))
    }

    @Test
    fun `cleans file extension for fallback title`() {
        assertEquals("Песня", MusicFileSupport.cleanTitle("Песня.mp3"))
        assertEquals("Песня", MusicFileSupport.cleanTitle("Песня"))
    }
}
