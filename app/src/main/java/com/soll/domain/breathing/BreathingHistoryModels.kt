package com.soll.domain.breathing

/** Одна строка списка истории сессий на экране. */
data class BreathingHistoryRowUi(
    val id: Long,
    val endedAtMillis: Long,
    val dateTimeLabel: String,
    val durationLabel: String,
    val completedFully: Boolean,
)

/** Один столбец недельного графика (как в статистике-practice apps). */
data class BreathingWeekDayStat(
    val label: String,
    val minutes: Float,
)
