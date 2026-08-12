package com.soll.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyIntelligenceApiModelsTest {
    @Test
    fun feedFeedbackRequestCarriesStableIdempotencyValue() {
        val request = FeedFeedbackRequest(
            decision = "useful",
            topic = "soll_project",
            source = "source-1",
            clientId = "feedback-event-1",
            idempotencyKey = "feedback-event-1",
        )

        assertEquals("feedback-event-1", request.clientId)
        assertEquals(request.clientId, request.idempotencyKey)
    }

    @Test
    fun todayResponsePreservesBriefingFeedAndApplicability() {
        val feed = FeedItemResponse(
            id = "feed:source:item",
            sourceId = "source",
            sourceName = "Primary source",
            category = "technology",
            title = "New edge runtime",
            rankScore = 91,
            whyForYou = "Matches portable Soll",
            assessment = TechnologyAssessmentResponse(
                method = "deterministic_v1",
                readiness = "prototype",
                targets = listOf("esp32", "portable"),
                risk = "low",
                experiment = "Run a bounded bench test",
            ),
        )
        val response = TodaySnapshotResponse(
            date = "2026-08-10",
            status = "ready",
            summary = "Today is ready",
            briefingCards = listOf(
                TodayCardResponse(id = "today:tasks", kind = "tasks", title = "Tasks")
            ),
            feedPreview = listOf(feed),
            calendar = CalendarSnapshotResponse(available = true, count = 2),
        )

        val domain = response.toDomain()

        assertEquals("ready", domain.status)
        assertEquals("tasks", domain.briefingCards.single().kind)
        assertEquals(91, domain.feedPreview.single().rankScore)
        assertEquals("prototype", domain.feedPreview.single().assessment?.readiness)
        assertTrue("esp32" in domain.feedPreview.single().assessment?.targets.orEmpty())
        assertEquals(2, domain.calendar.count)
    }

    @Test
    fun feedPageRetainsOpaqueCursorAndFeedbackContract() {
        val page = FeedPageResponse(
            items = listOf(
                FeedItemResponse(
                    id = "feed:a:b",
                    feedback = FeedFeedbackContractResponse(
                        entityId = "feed:a:b",
                        topic = "important_news",
                        source = "a",
                    ),
                )
            ),
            nextCursor = "djE6MzA",
            hasMore = true,
            total = 42,
        ).toDomain()

        assertTrue(page.hasMore)
        assertEquals("djE6MzA", page.nextCursor)
        assertEquals("important_news", page.items.single().feedback.topic)
        assertEquals(42, page.total)
    }
}
