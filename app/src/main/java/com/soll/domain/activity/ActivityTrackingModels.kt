package com.soll.domain.activity

import com.soll.domain.field.FieldDistance
import com.soll.domain.field.GeoCoordinate
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ActivitySample(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val capturedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val stepDelta: Int = 0,
    val batteryLevel: Int? = null,
    val isCharging: Boolean = false,
    val reason: String = "sample",
) {
    fun coordinateOrNull(): GeoCoordinate? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return GeoCoordinate(lat, lon)
    }
}

data class ActivityTrackingSummary(
    val enabled: Boolean = false,
    val todaySteps: Int = 0,
    val todayDistanceMeters: Double = 0.0,
    val samplesToday: Int = 0,
    val totalSamples: Int = 0,
    val lastSample: ActivitySample? = null,
)

data class ActivityTrackingPolicyInput(
    val elapsedSinceLastSampleMs: Long,
    val pendingStepDelta: Int,
    val batteryLevel: Int?,
    val isCharging: Boolean,
    val hasLocationPermission: Boolean,
    val hasStepSensor: Boolean,
)

data class ActivityTrackingPolicyDecision(
    val shouldRecord: Boolean,
    val reason: String,
    val nextCheckInMs: Long,
)

object ActivityTrackingPolicy {
    const val LOW_BATTERY_LEVEL = 20
    const val NORMAL_MIN_INTERVAL_MS = 5 * 60_000L
    const val LOW_BATTERY_MIN_INTERVAL_MS = 15 * 60_000L
    const val MOVING_MIN_INTERVAL_MS = 60_000L
    const val NORMAL_STEP_DELTA = 60
    const val LOW_BATTERY_STEP_DELTA = 140

    fun decide(input: ActivityTrackingPolicyInput): ActivityTrackingPolicyDecision {
        if (!input.hasStepSensor && !input.hasLocationPermission) {
            return ActivityTrackingPolicyDecision(
                shouldRecord = false,
                reason = "нет датчика шагов и геолокации",
                nextCheckInMs = LOW_BATTERY_MIN_INTERVAL_MS,
            )
        }

        if (input.elapsedSinceLastSampleMs == Long.MAX_VALUE) {
            return ActivityTrackingPolicyDecision(
                shouldRecord = true,
                reason = "первый замер",
                nextCheckInMs = NORMAL_MIN_INTERVAL_MS,
            )
        }

        val lowBattery = input.batteryLevel != null &&
            input.batteryLevel <= LOW_BATTERY_LEVEL &&
            !input.isCharging
        val minInterval = if (lowBattery) LOW_BATTERY_MIN_INTERVAL_MS else NORMAL_MIN_INTERVAL_MS
        val stepThreshold = if (lowBattery) LOW_BATTERY_STEP_DELTA else NORMAL_STEP_DELTA

        if (
            input.pendingStepDelta >= stepThreshold &&
            input.elapsedSinceLastSampleMs >= MOVING_MIN_INTERVAL_MS
        ) {
            return ActivityTrackingPolicyDecision(
                shouldRecord = true,
                reason = if (lowBattery) "движение, экономный режим" else "движение",
                nextCheckInMs = MOVING_MIN_INTERVAL_MS,
            )
        }

        if (input.elapsedSinceLastSampleMs >= minInterval) {
            return ActivityTrackingPolicyDecision(
                shouldRecord = true,
                reason = if (lowBattery) "редкий контроль батареи" else "периодический контроль",
                nextCheckInMs = minInterval,
            )
        }

        return ActivityTrackingPolicyDecision(
            shouldRecord = false,
            reason = if (lowBattery) "ожидание движения, экономия батареи" else "ожидание движения",
            nextCheckInMs = minInterval - input.elapsedSinceLastSampleMs,
        )
    }
}

object ActivityTrackingSummaries {
    fun summarize(
        samples: List<ActivitySample>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ActivityTrackingSummary {
        val dayStart = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val today = samples
            .filter { it.capturedAt >= dayStart }
            .sortedBy { it.capturedAt }
        val coordinates = today.mapNotNull { it.coordinateOrNull() }
        val distance = coordinates
            .zipWithNext()
            .sumOf { (left, right) -> FieldDistance.metersBetween(left, right) }
        return ActivityTrackingSummary(
            todaySteps = today.sumOf { it.stepDelta.coerceAtLeast(0) },
            todayDistanceMeters = distance,
            samplesToday = today.size,
            totalSamples = samples.size,
            lastSample = samples.maxByOrNull { it.capturedAt },
        )
    }
}
