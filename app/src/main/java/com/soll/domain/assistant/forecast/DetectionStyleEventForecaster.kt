package com.soll.domain.assistant.forecast

import com.soll.domain.assistant.AssistantEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Research-only adaptation of object detection's "what + where" contract to
 * Soll assistant events: event type/source is "what" and a bounded time window
 * is "when". The prototype is deterministic, local and does not inspect event
 * payloads.
 */
internal object DetectionStyleEventForecaster {
    fun forecast(
        history: List<AssistantEvent>,
        forecastStartMillis: Long,
        horizonMillis: Long,
        minimumOccurrences: Int = 3,
        minimumToleranceMillis: Long = 60_000L,
        nmsTemporalIouThreshold: Double = 0.5,
        maxPredictions: Int = 512,
    ): List<EventForecast> {
        require(horizonMillis > 0) { "horizonMillis must be positive" }
        require(minimumOccurrences >= 2) { "minimumOccurrences must be at least 2" }
        require(minimumToleranceMillis >= 0) { "minimumToleranceMillis must not be negative" }
        require(nmsTemporalIouThreshold in 0.0..1.0) {
            "nmsTemporalIouThreshold must be between 0 and 1"
        }
        require(maxPredictions > 0) { "maxPredictions must be positive" }

        val forecastEndMillis = forecastStartMillis.safePlus(horizonMillis)
        val candidates = history
            .asSequence()
            .filter { event ->
                event.createdAt < forecastStartMillis && event.type.isNotBlank()
            }
            .groupBy { event ->
                EventClass(
                    type = event.type.trim(),
                    source = event.source.trim().ifBlank { UNKNOWN_SOURCE },
                )
            }
            .flatMap { (eventClass, events) ->
                val timestamps = events.map { it.createdAt }.distinct().sorted()
                if (timestamps.size < minimumOccurrences) return@flatMap emptyList()

                val gaps = timestamps.zipWithNext { first, second -> second - first }
                    .filter { it > 0 }
                if (gaps.isEmpty()) return@flatMap emptyList()

                val periodMillis = gaps.median()
                if (periodMillis <= 0L) return@flatMap emptyList()

                val medianAbsoluteDeviation = gaps
                    .map { gap -> abs(gap - periodMillis) }
                    .median()
                val toleranceMillis = max(
                    minimumToleranceMillis,
                    min(periodMillis / 2, medianAbsoluteDeviation.safeTimes(2)),
                )
                val confidence = recurrenceConfidence(
                    occurrenceCount = timestamps.size,
                    periodMillis = periodMillis,
                    medianAbsoluteDeviation = medianAbsoluteDeviation,
                )

                buildList {
                    var expectedAt = timestamps.last().safePlus(periodMillis)
                    while (expectedAt < forecastStartMillis) {
                        val advanced = expectedAt.safePlus(periodMillis)
                        if (advanced <= expectedAt) break
                        expectedAt = advanced
                    }
                    while (expectedAt < forecastEndMillis && size < maxPredictions) {
                        add(
                            EventForecast(
                                eventClass = eventClass,
                                expectedAtMillis = expectedAt,
                                windowStartMillis = expectedAt.safeMinus(toleranceMillis),
                                windowEndMillis = expectedAt.safePlus(toleranceMillis),
                                confidence = confidence,
                                support = timestamps.size,
                                learnedPeriodMillis = periodMillis,
                            ),
                        )
                        val advanced = expectedAt.safePlus(periodMillis)
                        if (advanced <= expectedAt) break
                        expectedAt = advanced
                    }
                }
            }
            .sortedWith(
                compareByDescending<EventForecast> { it.confidence }
                    .thenBy { it.expectedAtMillis }
                    .thenBy { it.eventClass.type }
                    .thenBy { it.eventClass.source },
            )
            .take(maxPredictions)

        return suppressOverlaps(candidates, nmsTemporalIouThreshold)
            .sortedWith(
                compareBy<EventForecast> { it.expectedAtMillis }
                    .thenBy { it.eventClass.type }
                    .thenBy { it.eventClass.source },
            )
    }

    /** A deliberately narrow baseline: forecast only the most frequent class. */
    fun frequencyBaseline(
        history: List<AssistantEvent>,
        forecastStartMillis: Long,
        horizonMillis: Long,
        minimumOccurrences: Int = 3,
        minimumToleranceMillis: Long = 60_000L,
    ): List<EventForecast> {
        val dominantClass = history
            .asSequence()
            .filter { it.createdAt < forecastStartMillis && it.type.isNotBlank() }
            .groupingBy {
                EventClass(
                    type = it.type.trim(),
                    source = it.source.trim().ifBlank { UNKNOWN_SOURCE },
                )
            }
            .eachCount()
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<EventClass, Int>> { it.value }
                    .thenBy { it.key.type }
                    .thenBy { it.key.source },
            )
            .firstOrNull()
            ?.key
            ?: return emptyList()

        return forecast(
            history = history.filter {
                it.type.trim() == dominantClass.type &&
                    it.source.trim().ifBlank { UNKNOWN_SOURCE } == dominantClass.source
            },
            forecastStartMillis = forecastStartMillis,
            horizonMillis = horizonMillis,
            minimumOccurrences = minimumOccurrences,
            minimumToleranceMillis = minimumToleranceMillis,
        )
    }

    fun evaluate(
        predictions: List<EventForecast>,
        actualEvents: List<AssistantEvent>,
        groundTruthToleranceMillis: Long,
        minimumTemporalIou: Double = 0.3,
    ): EventForecastMetrics {
        require(groundTruthToleranceMillis >= 0) {
            "groundTruthToleranceMillis must not be negative"
        }
        require(minimumTemporalIou in 0.0..1.0) {
            "minimumTemporalIou must be between 0 and 1"
        }

        val remainingActual = actualEvents
            .filter { it.type.isNotBlank() }
            .map { event ->
                EventTarget(
                    eventClass = EventClass(
                        type = event.type.trim(),
                        source = event.source.trim().ifBlank { UNKNOWN_SOURCE },
                    ),
                    occurredAtMillis = event.createdAt,
                    windowStartMillis = event.createdAt.safeMinus(groundTruthToleranceMillis),
                    windowEndMillis = event.createdAt.safePlus(groundTruthToleranceMillis),
                )
            }
            .toMutableList()

        var truePositives = 0
        var totalAbsoluteTimingErrorMillis = 0.0
        predictions
            .sortedWith(
                compareByDescending<EventForecast> { it.confidence }
                    .thenBy { it.expectedAtMillis },
            )
            .forEach { prediction ->
                val bestMatch = remainingActual
                    .withIndex()
                    .asSequence()
                    .filter { (_, target) -> target.eventClass == prediction.eventClass }
                    .map { indexedTarget ->
                        indexedTarget to temporalIou(
                            prediction.windowStartMillis,
                            prediction.windowEndMillis,
                            indexedTarget.value.windowStartMillis,
                            indexedTarget.value.windowEndMillis,
                        )
                    }
                    .filter { (_, iou) -> iou >= minimumTemporalIou }
                    .sortedWith(
                        compareByDescending<Pair<IndexedValue<EventTarget>, Double>> { it.second }
                            .thenBy {
                                abs(prediction.expectedAtMillis - it.first.value.occurredAtMillis)
                            },
                    )
                    .firstOrNull()

                if (bestMatch != null) {
                    val matched = remainingActual.removeAt(bestMatch.first.index)
                    truePositives += 1
                    totalAbsoluteTimingErrorMillis +=
                        abs(prediction.expectedAtMillis - matched.occurredAtMillis).toDouble()
                }
            }

        val falsePositives = predictions.size - truePositives
        val falseNegatives = remainingActual.size
        val precision = ratio(truePositives, truePositives + falsePositives)
        val recall = ratio(truePositives, truePositives + falseNegatives)
        val f1 = if (precision + recall == 0.0) {
            0.0
        } else {
            2.0 * precision * recall / (precision + recall)
        }
        return EventForecastMetrics(
            truePositives = truePositives,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            precision = precision,
            recall = recall,
            f1 = f1,
            meanAbsoluteTimingErrorMillis = if (truePositives == 0) {
                null
            } else {
                totalAbsoluteTimingErrorMillis / truePositives
            },
        )
    }

    private fun suppressOverlaps(
        candidates: List<EventForecast>,
        temporalIouThreshold: Double,
    ): List<EventForecast> {
        val kept = mutableListOf<EventForecast>()
        candidates.forEach { candidate ->
            val overlapsHigherConfidencePrediction = kept.any { accepted ->
                accepted.eventClass == candidate.eventClass &&
                    temporalIou(
                        accepted.windowStartMillis,
                        accepted.windowEndMillis,
                        candidate.windowStartMillis,
                        candidate.windowEndMillis,
                    ) > temporalIouThreshold
            }
            if (!overlapsHigherConfidencePrediction) kept += candidate
        }
        return kept
    }

    private fun recurrenceConfidence(
        occurrenceCount: Int,
        periodMillis: Long,
        medianAbsoluteDeviation: Long,
    ): Double {
        val supportScore = min(1.0, (occurrenceCount - 1) / 4.0)
        val regularityScore = 1.0 / (1.0 + medianAbsoluteDeviation.toDouble() / periodMillis)
        return (supportScore * regularityScore).coerceIn(0.0, 1.0)
    }

    private fun temporalIou(
        firstStart: Long,
        firstEnd: Long,
        secondStart: Long,
        secondEnd: Long,
    ): Double {
        val intersection = max(0L, min(firstEnd, secondEnd) - max(firstStart, secondStart))
        val union = max(firstEnd, secondEnd) - min(firstStart, secondStart)
        return if (union <= 0L) {
            if (firstStart == secondStart) 1.0 else 0.0
        } else {
            intersection.toDouble() / union
        }
    }

    private fun List<Long>.median(): Long {
        if (isEmpty()) return 0L
        val ordered = sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) {
            ordered[middle]
        } else {
            ordered[middle - 1] + (ordered[middle] - ordered[middle - 1]) / 2
        }
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private fun Long.safePlus(other: Long): Long =
        if (other > 0 && this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

    private fun Long.safeMinus(other: Long): Long =
        if (other > 0 && this < Long.MIN_VALUE + other) Long.MIN_VALUE else this - other

    private fun Long.safeTimes(multiplier: Long): Long =
        if (this > 0 && multiplier > 0 && this > Long.MAX_VALUE / multiplier) {
            Long.MAX_VALUE
        } else {
            this * multiplier
        }

    private const val UNKNOWN_SOURCE = "unknown"
}

internal data class EventClass(
    val type: String,
    val source: String,
)

internal data class EventForecast(
    val eventClass: EventClass,
    val expectedAtMillis: Long,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val confidence: Double,
    val support: Int,
    val learnedPeriodMillis: Long,
)

internal data class EventForecastMetrics(
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val meanAbsoluteTimingErrorMillis: Double?,
)

private data class EventTarget(
    val eventClass: EventClass,
    val occurredAtMillis: Long,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
)
