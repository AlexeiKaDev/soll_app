package com.soll.domain.field

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class FieldPointStatus(
    val storageKey: String,
    val label: String,
) {
    PLANNED("planned", "В плане"),
    ACTIVE("active", "В работе"),
    DONE("done", "Готово"),
    SKIPPED("skipped", "Пропущено");

    val sortRank: Int
        get() = when (this) {
            ACTIVE -> 0
            PLANNED -> 1
            DONE -> 2
            SKIPPED -> 3
        }

    companion object {
        fun fromStorage(value: String?): FieldPointStatus =
            entries.firstOrNull { it.storageKey == value } ?: PLANNED
    }
}

enum class FieldPointSource(
    val storageKey: String,
    val label: String,
) {
    MANUAL("manual", "Вручную"),
    CURRENT_LOCATION("current_location", "Моя точка"),
    TASK("task", "Задача");

    companion object {
        fun fromStorage(value: String?): FieldPointSource =
            entries.firstOrNull { it.storageKey == value } ?: MANUAL
    }
}

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    fun formatted(): String =
        String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
}

data class FieldLocationSnapshot(
    val coordinate: GeoCoordinate,
    val accuracyMeters: Float?,
    val capturedAt: Long,
    val provider: String,
    val fallbackUsed: Boolean,
)

data class FieldPoint(
    val id: String,
    val title: String,
    val note: String,
    val coordinate: GeoCoordinate,
    val accuracyMeters: Float?,
    val source: FieldPointSource,
    val status: FieldPointStatus,
    val taskId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val visitedAt: Long?,
    val distanceMeters: Double? = null,
)

object FieldDistance {
    fun metersBetween(left: GeoCoordinate, right: GeoCoordinate): Double {
        val radius = 6_371_000.0
        val leftLat = Math.toRadians(left.latitude)
        val rightLat = Math.toRadians(right.latitude)
        val deltaLat = Math.toRadians(right.latitude - left.latitude)
        val deltaLon = Math.toRadians(right.longitude - left.longitude)
        val a = sin(deltaLat / 2).pow(2.0) +
            cos(leftLat) * cos(rightLat) * sin(deltaLon / 2).pow(2.0)
        return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

object FieldCoordinateParser {
    private val coordinatePairRegex = Regex(
        pattern = """(?<!\d)([+-]?\d{1,2}(?:[.,]\d+)?)\s*[,;\s]\s*([+-]?\d{1,3}(?:[.,]\d+)?)(?!\d)""",
        options = setOf(RegexOption.IGNORE_CASE),
    )

    fun parseFirst(text: String): GeoCoordinate? =
        coordinatePairRegex.findAll(text)
            .mapNotNull { match ->
                val lat = match.groupValues[1].normalizeNumber()
                val lon = match.groupValues[2].normalizeNumber()
                validate(lat, lon)
            }
            .firstOrNull()

    fun parseManual(latitude: String, longitude: String): GeoCoordinate {
        val lat = latitude.normalizeNumber()
            ?: throw IllegalArgumentException("Широта должна быть числом")
        val lon = longitude.normalizeNumber()
            ?: throw IllegalArgumentException("Долгота должна быть числом")
        return validate(lat, lon)
            ?: throw IllegalArgumentException("Координаты вне диапазона: широта -90..90, долгота -180..180")
    }

    fun validate(latitude: Double?, longitude: Double?): GeoCoordinate? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) {
            GeoCoordinate(latitude = lat, longitude = lon)
        } else {
            null
        }
    }

    private fun String.normalizeNumber(): Double? =
        trim()
            .replace(',', '.')
            .toDoubleOrNull()
}
