package com.soll.domain.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ActivityTrackingPolicyTest {
    @Test
    fun recordsFirstSample() {
        val decision = ActivityTrackingPolicy.decide(
            ActivityTrackingPolicyInput(
                elapsedSinceLastSampleMs = Long.MAX_VALUE,
                pendingStepDelta = 0,
                batteryLevel = 80,
                isCharging = false,
                hasLocationPermission = true,
                hasStepSensor = true,
            )
        )

        assertTrue(decision.shouldRecord)
        assertEquals("первый замер", decision.reason)
    }

    @Test
    fun recordsMovementBeforeNormalInterval() {
        val decision = ActivityTrackingPolicy.decide(
            ActivityTrackingPolicyInput(
                elapsedSinceLastSampleMs = ActivityTrackingPolicy.MOVING_MIN_INTERVAL_MS,
                pendingStepDelta = ActivityTrackingPolicy.NORMAL_STEP_DELTA,
                batteryLevel = 70,
                isCharging = false,
                hasLocationPermission = true,
                hasStepSensor = true,
            )
        )

        assertTrue(decision.shouldRecord)
        assertEquals("движение", decision.reason)
    }

    @Test
    fun lowBatteryRequiresLargerStepDelta() {
        val decision = ActivityTrackingPolicy.decide(
            ActivityTrackingPolicyInput(
                elapsedSinceLastSampleMs = ActivityTrackingPolicy.MOVING_MIN_INTERVAL_MS,
                pendingStepDelta = ActivityTrackingPolicy.NORMAL_STEP_DELTA,
                batteryLevel = ActivityTrackingPolicy.LOW_BATTERY_LEVEL,
                isCharging = false,
                hasLocationPermission = true,
                hasStepSensor = true,
            )
        )

        assertFalse(decision.shouldRecord)
    }

    @Test
    fun recordsPeriodicHeartbeatAfterInterval() {
        val decision = ActivityTrackingPolicy.decide(
            ActivityTrackingPolicyInput(
                elapsedSinceLastSampleMs = ActivityTrackingPolicy.NORMAL_MIN_INTERVAL_MS,
                pendingStepDelta = 0,
                batteryLevel = 70,
                isCharging = false,
                hasLocationPermission = true,
                hasStepSensor = true,
            )
        )

        assertTrue(decision.shouldRecord)
        assertEquals("периодический контроль", decision.reason)
    }

    @Test
    fun summarizeKeepsOnlyTodayCountersButRetainsTotalHistory() {
        val zone = ZoneId.of("UTC")
        val todayStart = LocalDate.of(2026, 6, 18)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val yesterdayStart = LocalDate.of(2026, 6, 17)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val summary = ActivityTrackingSummaries.summarize(
            samples = listOf(
                ActivitySample(
                    id = "yesterday",
                    sessionId = "s-1",
                    capturedAt = yesterdayStart + 60_000L,
                    latitude = 47.0,
                    longitude = 28.0,
                    stepDelta = 999,
                ),
                ActivitySample(
                    id = "today-1",
                    sessionId = "s-2",
                    capturedAt = todayStart + 60_000L,
                    latitude = 0.0,
                    longitude = 0.0,
                    stepDelta = 12,
                ),
                ActivitySample(
                    id = "today-2",
                    sessionId = "s-2",
                    capturedAt = todayStart + 120_000L,
                    latitude = 0.0,
                    longitude = 0.001,
                    stepDelta = 8,
                ),
            ),
            nowMillis = todayStart + 180_000L,
            zoneId = zone,
        )

        assertEquals(20, summary.todaySteps)
        assertEquals(2, summary.samplesToday)
        assertEquals(3, summary.totalSamples)
        assertEquals("today-2", summary.lastSample?.id)
        assertTrue(summary.todayDistanceMeters in 100.0..120.0)
    }
}
