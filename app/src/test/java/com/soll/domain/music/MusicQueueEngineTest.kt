package com.soll.domain.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicQueueEngineTest {
    @Test
    fun `next index respects repeat modes`() {
        assertEquals(1, MusicQueueEngine.nextIndex(currentIndex = 0, size = 3, repeatMode = MusicRepeatMode.OFF))
        assertNull(MusicQueueEngine.nextIndex(currentIndex = 2, size = 3, repeatMode = MusicRepeatMode.OFF))
        assertEquals(0, MusicQueueEngine.nextIndex(currentIndex = 2, size = 3, repeatMode = MusicRepeatMode.ALL))
        assertEquals(2, MusicQueueEngine.nextIndex(currentIndex = 2, size = 3, repeatMode = MusicRepeatMode.ONE))
    }

    @Test
    fun `previous index stops at beginning`() {
        assertNull(MusicQueueEngine.previousIndex(currentIndex = 0, size = 3))
        assertEquals(1, MusicQueueEngine.previousIndex(currentIndex = 2, size = 3))
    }
}
