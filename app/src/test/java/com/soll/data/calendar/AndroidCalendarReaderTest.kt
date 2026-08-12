package com.soll.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AndroidCalendarReaderTest {
    @Test
    fun `recurring event occurrences get distinct stable identities`() {
        val first = calendarOccurrenceId(eventId = 42L, startMillis = 1_700_000_000_000L)
        val second = calendarOccurrenceId(eventId = 42L, startMillis = 1_700_086_400_000L)

        assertEquals(first, calendarOccurrenceId(eventId = 42L, startMillis = 1_700_000_000_000L))
        assertNotEquals(first, second)
    }
}
