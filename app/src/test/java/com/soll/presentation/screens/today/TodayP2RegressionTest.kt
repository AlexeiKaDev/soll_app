package com.soll.presentation.screens.today

import com.soll.domain.soll.SollFeedItem
import com.soll.domain.soll.SollTodayCard
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TodayP2RegressionTest {
    @Test
    fun `calendar read rethrows coroutine cancellation`() {
        try {
            runBlocking {
                runTodayCalendarRead<Unit> { throw CancellationException("cancelled") }
            }
            fail("CancellationException must be rethrown")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    @Test
    fun `calendar read wraps ordinary failures`() = runBlocking {
        val result = runTodayCalendarRead<Unit> { error("calendar unavailable") }

        assertTrue(result.isFailure)
        assertEquals("calendar unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `today card keys remain unique for blank and duplicate ids`() {
        val cards = listOf(
            SollTodayCard(id = "", title = "Weather"),
            SollTodayCard(id = "same", title = "News A"),
            SollTodayCard(id = "same", title = "News A"),
        )

        val first = stableTodayCardKeys(cards)

        assertEquals(first, stableTodayCardKeys(cards))
        assertEquals(cards.size, first.toSet().size)
    }

    @Test
    fun `feed keys remain unique for blank and duplicate ids`() {
        val feed = listOf(
            SollFeedItem(id = "", title = "Article"),
            SollFeedItem(id = "same", title = "Article"),
            SollFeedItem(id = "same", title = "Article"),
        )

        val first = stableFeedItemKeys(feed)

        assertEquals(first, stableFeedItemKeys(feed))
        assertEquals(feed.size, first.toSet().size)
    }
}
