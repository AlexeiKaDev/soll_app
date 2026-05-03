package com.soll.domain.breathing

enum class BreathingPhase {
    IDLE,
    BREATHING,
    HOLD,
    RECOVERY,
    COMPLETE,
}

data class BreathingSessionConfig(
    val rounds: Int = 3,
    val breathsPerRound: Int = 35,
    val breathingStepMs: Long = 1500L,
    /** После серии дыханий — задержка на выдохе с отсчётом (как в guided-сессиях). */
    val holdGuidedSeconds: Int = 30,
    val recoverySeconds: Int = 15,
)

data class BreathingSessionState(
    val phase: BreathingPhase = BreathingPhase.IDLE,
    val round: Int = 1,
    val totalRounds: Int = 3,
    val breathCountInRound: Int = 0,
    /** Полушаг в раунде: 1 вдох, 2 выдох, 3 вдох… до 2×breathsPerRound. */
    val breathingHalfStep: Int = 0,
    val breathsPerRound: Int = 35,
    /** Длительность одного шага «вдох/выдох» для синхронной анимации орба */
    val breathingStepMs: Long = 1500L,
    val instruction: String = "Готов к сессии",
    /** Для анимации растущий круг = вдох, сжимающийся = выдох */
    val isInhalePhase: Boolean = true,
    /** Фактически удержано секунд в задержке (для рекордов). */
    val holdSeconds: Int = 0,
    /** Оставшиеся секунды ориентира задержки (30→0). */
    val holdCountdownRemaining: Int = 0,
    /** Длина ориентира задержки для этого захода (из конфига). */
    val holdGuidedSeconds: Int = 30,
    val recoverySecondsLeft: Int = 0,
    val isRunning: Boolean = false,
    val holdRecordsByRound: List<Int> = emptyList(),
    /** Длительность текущей (или только что завершённой) сессии, мс. */
    val sessionElapsedMillis: Long = 0L,
    val sessionHistory: List<BreathingHistoryRowUi> = emptyList(),
    val weekDayStats: List<BreathingWeekDayStat> = emptyList(),
)

