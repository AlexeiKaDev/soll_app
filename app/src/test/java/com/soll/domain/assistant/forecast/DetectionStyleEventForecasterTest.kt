package com.soll.domain.assistant.forecast

import com.soll.domain.assistant.AssistantEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionStyleEventForecasterTest {
    @Test
    fun `detector style prototype forecasts what and when for recurring Soll events`() {
        val history = recurringHistory()

        val forecasts = DetectionStyleEventForecaster.forecast(
            history = history,
            forecastStartMillis = day(28),
            horizonMillis = day(28),
            minimumToleranceMillis = day(1),
        )

        assertEquals(8, forecasts.size)
        assertEquals(
            listOf(day(28), day(35), day(42), day(49)),
            forecasts
                .filter { it.eventClass.type == "sync_success" }
                .map { it.expectedAtMillis },
        )
        assertEquals(
            listOf(day(30), day(37), day(44), day(51)),
            forecasts
                .filter { it.eventClass.type == "source_digest" }
                .map { it.expectedAtMillis },
        )
        assertTrue(forecasts.none { it.eventClass.type == "backup_completed" })
        assertTrue(forecasts.all { it.learnedPeriodMillis == day(7) })
    }

    @Test
    fun `multi class detector improves holdout F1 over frequency baseline`() {
        val history = recurringHistory()
        val actual = listOf(
            event("sync_success", "server_sync", 28),
            event("source_digest", "source_monitor", 30),
            event("sync_success", "server_sync", 35),
            event("source_digest", "source_monitor", 38),
            event("tool_failure", "tool_runner", 40),
            event("sync_success", "server_sync", 42),
            event("source_digest", "source_monitor", 44),
            event("sync_success", "server_sync", 49),
            event("source_digest", "source_monitor", 51),
        )
        val prototype = DetectionStyleEventForecaster.forecast(
            history = history,
            forecastStartMillis = day(28),
            horizonMillis = day(28),
            minimumToleranceMillis = day(1),
        )
        val baseline = DetectionStyleEventForecaster.frequencyBaseline(
            history = history,
            forecastStartMillis = day(28),
            horizonMillis = day(28),
            minimumToleranceMillis = day(1),
        )

        val prototypeMetrics = DetectionStyleEventForecaster.evaluate(
            predictions = prototype,
            actualEvents = actual,
            groundTruthToleranceMillis = day(1),
        )
        val baselineMetrics = DetectionStyleEventForecaster.evaluate(
            predictions = baseline,
            actualEvents = actual,
            groundTruthToleranceMillis = day(1),
        )

        assertEquals(8, prototypeMetrics.truePositives)
        assertEquals(0, prototypeMetrics.falsePositives)
        assertEquals(1, prototypeMetrics.falseNegatives)
        assertEquals(1.0, prototypeMetrics.precision, 0.0001)
        assertEquals(0.8889, prototypeMetrics.recall, 0.0001)
        assertEquals(0.9412, prototypeMetrics.f1, 0.0001)
        assertEquals(day(1) / 8.0, prototypeMetrics.meanAbsoluteTimingErrorMillis!!, 0.1)

        assertEquals(4, baselineMetrics.truePositives)
        assertEquals(0, baselineMetrics.falsePositives)
        assertEquals(5, baselineMetrics.falseNegatives)
        assertEquals(0.6154, baselineMetrics.f1, 0.0001)
        assertTrue(prototypeMetrics.f1 > baselineMetrics.f1)
    }

    @Test
    fun `evaluation matches each actual event at most once`() {
        val eventClass = EventClass("sync_success", "server_sync")
        val predictions = listOf(
            forecast(eventClass, expectedDay = 28, confidence = 0.9),
            forecast(eventClass, expectedDay = 28, confidence = 0.8),
        )

        val metrics = DetectionStyleEventForecaster.evaluate(
            predictions = predictions,
            actualEvents = listOf(event("sync_success", "server_sync", 28)),
            groundTruthToleranceMillis = day(1),
        )

        assertEquals(1, metrics.truePositives)
        assertEquals(1, metrics.falsePositives)
        assertEquals(0, metrics.falseNegatives)
    }

    @Test
    fun `prototype ignores payload and rejects invalid horizons`() {
        val history = recurringHistory().map {
            it.copy(payloadJson = "{\"private\":\"must not affect forecast\"}")
        }

        val forecasts = DetectionStyleEventForecaster.forecast(
            history = history,
            forecastStartMillis = day(28),
            horizonMillis = day(7),
            minimumToleranceMillis = day(1),
        )

        assertEquals(2, forecasts.size)
        val error = runCatching {
            DetectionStyleEventForecaster.forecast(
                history = history,
                forecastStartMillis = day(28),
                horizonMillis = 0,
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    private fun recurringHistory(): List<AssistantEvent> =
        listOf(
            event("sync_success", "server_sync", 0),
            event("source_digest", "source_monitor", 2),
            event("backup_completed", "backup", 5),
            event("sync_success", "server_sync", 7),
            event("source_digest", "source_monitor", 9),
            event("sync_success", "server_sync", 14),
            event("source_digest", "source_monitor", 16),
            event("backup_completed", "backup", 19),
            event("sync_success", "server_sync", 21),
            event("source_digest", "source_monitor", 23),
        )

    private fun event(type: String, source: String, atDay: Int): AssistantEvent =
        AssistantEvent(
            id = "$type-$source-$atDay",
            type = type,
            source = source,
            summary = "synthetic non-sensitive event",
            createdAt = day(atDay),
        )

    private fun forecast(
        eventClass: EventClass,
        expectedDay: Int,
        confidence: Double,
    ): EventForecast =
        EventForecast(
            eventClass = eventClass,
            expectedAtMillis = day(expectedDay),
            windowStartMillis = day(expectedDay - 1),
            windowEndMillis = day(expectedDay + 1),
            confidence = confidence,
            support = 4,
            learnedPeriodMillis = day(7),
        )

    private fun day(value: Int): Long = value * DAY_MILLIS

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
