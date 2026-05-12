package com.soll.domain.tts

import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class PiperProsodyPreset(
    val storageKey: String,
    val displayName: String,
    val description: String,
    val noiseScale: Float,
    val noiseScaleW: Float,
    val lengthScale: Float,
    private val mergeShortMultiplier: Float,
    private val mergeTotalMultiplier: Float,
    private val mergeShortMax: Int,
    private val mergeTotalMax: Int,
    private val sentencePauseMultiplier: Float,
    private val paragraphPauseMultiplier: Float,
) {
    STABLE(
        storageKey = "stable",
        displayName = "Стабильнее",
        description = "Короче фразы, длиннее паузы, минимум неожиданных интонаций.",
        noiseScale = 0.45f,
        noiseScaleW = 0.55f,
        lengthScale = 1.0f,
        mergeShortMultiplier = 0.70f,
        mergeTotalMultiplier = 0.70f,
        mergeShortMax = 150,
        mergeTotalMax = 260,
        sentencePauseMultiplier = 1.25f,
        paragraphPauseMultiplier = 1.35f,
    ),
    NATURAL(
        storageKey = "natural",
        displayName = "Натуральнее",
        description = "Склеивает короткие фразы в смысловые блоки без сильного риска артефактов.",
        noiseScale = 0.72f,
        noiseScaleW = 0.88f,
        lengthScale = 1.0f,
        mergeShortMultiplier = 1.25f,
        mergeTotalMultiplier = 1.28f,
        mergeShortMax = 240,
        mergeTotalMax = 430,
        sentencePauseMultiplier = 1.02f,
        paragraphPauseMultiplier = 1.05f,
    ),
    EXPRESSIVE(
        storageKey = "expressive",
        displayName = "Агрессивнее",
        description = "Длиннее блоки и заметно больше вариативности; возможны странные ударения.",
        noiseScale = 0.90f,
        noiseScaleW = 1.05f,
        lengthScale = 1.0f,
        mergeShortMultiplier = 1.65f,
        mergeTotalMultiplier = 1.75f,
        mergeShortMax = 360,
        mergeTotalMax = 620,
        sentencePauseMultiplier = 0.78f,
        paragraphPauseMultiplier = 0.86f,
    ),
    ;

    fun mergeShortCap(base: Int): Int =
        (base * mergeShortMultiplier).roundToInt().coerceIn(90, mergeShortMax)

    fun mergeTotalCap(base: Int): Int =
        (base * mergeTotalMultiplier).roundToInt().coerceIn(160, mergeTotalMax)

    fun sentencePauseMs(base: Long): Long =
        (base * sentencePauseMultiplier).roundToLong().coerceIn(90L, 320L)

    fun paragraphPauseMs(base: Long): Long =
        (base * paragraphPauseMultiplier).roundToLong().coerceIn(180L, 420L)

    companion object {
        val DEFAULT = NATURAL

        fun fromStorage(value: String?): PiperProsodyPreset =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
