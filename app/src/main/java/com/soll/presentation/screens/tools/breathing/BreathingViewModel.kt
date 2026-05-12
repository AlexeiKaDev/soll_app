package com.soll.presentation.screens.tools.breathing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.BreathingSessionEntity
import com.soll.data.repository.BreathingRepository
import com.soll.domain.breathing.BreathingHistoryRowUi
import com.soll.domain.breathing.BreathingPhase
import com.soll.domain.breathing.BreathingSessionConfig
import com.soll.domain.breathing.BreathingSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class BreathingViewModel @Inject constructor(
    private val audioFeedback: BreathingAudioFeedback,
    private val breathingRepository: BreathingRepository,
) : ViewModel() {

    private val config = BreathingSessionConfig()
    private val _uiState = MutableStateFlow(
        BreathingSessionState(
            totalRounds = config.rounds,
            breathsPerRound = config.breathsPerRound,
            breathingStepMs = config.breathingStepMs,
        )
    )
    val uiState: StateFlow<BreathingSessionState> = _uiState.asStateFlow()

    private var phaseJob: Job? = null
    private var elapsedJob: Job? = null
    private var sessionStartWallClockMs: Long? = null

    init {
        viewModelScope.launch {
            breathingRepository.observeRecentSessions(60).collect {
                val week = breathingRepository.weekStatsEndingToday()
                _uiState.update { state ->
                    state.copy(
                        sessionHistory = it.map(::mapEntityToHistoryRow),
                        weekDayStats = week,
                    )
                }
            }
        }
    }

    fun startSession() {
        stopSession(resetToIdle = false)
        audioFeedback.playSessionStart()
        sessionStartWallClockMs = System.currentTimeMillis()
        startElapsedTicker()
        _uiState.value = BreathingSessionState(
            phase = BreathingPhase.BREATHING,
            round = 1,
            totalRounds = config.rounds,
            breathsPerRound = config.breathsPerRound,
            breathingStepMs = config.breathingStepMs,
            breathingHalfStep = 1,
            breathCountInRound = 0,
            instruction = "Вдох",
            recoverySecondsLeft = config.recoverySeconds,
            isInhalePhase = true,
            isRunning = true,
            sessionElapsedMillis = 0L,
            sessionHistory = _uiState.value.sessionHistory,
            weekDayStats = _uiState.value.weekDayStats,
        )
        startBreathingLoop()
    }

    fun finishHoldNow() {
        if (_uiState.value.phase != BreathingPhase.HOLD) return
        transitionHoldToRecovery()
    }

    fun stopSession(resetToIdle: Boolean = true) {
        phaseJob?.cancel()
        phaseJob = null
        if (!resetToIdle) {
            _uiState.update { it.copy(isRunning = false) }
            return
        }
        val start = sessionStartWallClockMs
        if (start != null) {
            val end = System.currentTimeMillis()
            val snap = _uiState.value
            val completedFully = snap.phase == BreathingPhase.COMPLETE
            persistSnapshot(start, end, snap, completedFully)
        }
        sessionStartWallClockMs = null
        elapsedJob?.cancel()
        elapsedJob = null
        audioFeedback.release()
        val hist = _uiState.value.sessionHistory
        val week = _uiState.value.weekDayStats
        _uiState.value = BreathingSessionState(
            totalRounds = config.rounds,
            breathsPerRound = config.breathsPerRound,
            breathingStepMs = config.breathingStepMs,
            sessionHistory = hist,
            weekDayStats = week,
        )
    }

    private fun startElapsedTicker() {
        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            while (isActive && sessionStartWallClockMs != null) {
                val phase = _uiState.value.phase
                if (phase == BreathingPhase.COMPLETE || phase == BreathingPhase.IDLE) break
                val start = sessionStartWallClockMs ?: break
                val elapsed = System.currentTimeMillis() - start
                _uiState.update { it.copy(sessionElapsedMillis = elapsed.coerceAtLeast(0)) }
                delay(500)
            }
        }
    }

    private fun persistSnapshot(
        startedAt: Long,
        endedAt: Long,
        snapshot: BreathingSessionState,
        completedFully: Boolean,
    ) {
        val durationSec = ((endedAt - startedAt) / 1000L).toInt().coerceAtLeast(0)
        if (durationSec < 3) return
        val roundsCompleted = if (completedFully) {
            snapshot.totalRounds
        } else {
            (snapshot.round - 1).coerceIn(0, snapshot.totalRounds)
        }
        val csv = snapshot.holdRecordsByRound.joinToString(",")
        viewModelScope.launch {
            breathingRepository.insertSession(
                BreathingSessionEntity(
                    startedAtMillis = startedAt,
                    endedAtMillis = endedAt,
                    durationSeconds = durationSec,
                    completedFully = completedFully,
                    roundsCompleted = roundsCompleted,
                    holdRecordsCsv = csv,
                )
            )
        }
    }

    /**
     * Один полушаг за итерацию: после паузы полушаг растёт (2 = первый выдох, 3 = второй вдох…).
     */
    private fun startBreathingLoop() {
        phaseJob?.cancel()
        phaseJob = viewModelScope.launch {
            val pairsTotal = config.breathsPerRound
            val maxHalfStep = 2 * pairsTotal
            while (isActive) {
                val state = _uiState.value
                if (state.phase != BreathingPhase.BREATHING) break
                delay(config.breathingStepMs)
                val st = _uiState.value
                if (st.phase != BreathingPhase.BREATHING) break
                val prevInstruction = st.instruction
                val nextHalf = st.breathingHalfStep + 1
                if (nextHalf > maxHalfStep) {
                    audioFeedback.playHoldPhaseStart()
                    _uiState.update {
                        it.copy(
                            phase = BreathingPhase.HOLD,
                            breathCountInRound = pairsTotal,
                            breathingHalfStep = maxHalfStep,
                            holdSeconds = 0,
                            holdCountdownRemaining = config.holdGuidedSeconds,
                            holdGuidedSeconds = config.holdGuidedSeconds,
                            instruction = "Полный выдох и задержка",
                            isInhalePhase = false,
                        )
                    }
                    startHoldLoop()
                    break
                }
                val isInhale = nextHalf % 2 == 1
                val instruction = if (isInhale) "Вдох" else "Выдох"
                _uiState.update {
                    it.copy(
                        breathingHalfStep = nextHalf,
                        instruction = instruction,
                        isInhalePhase = isInhale,
                        breathCountInRound = nextHalf / 2,
                    )
                }
                if (prevInstruction != instruction &&
                    (instruction == "Вдох" || instruction == "Выдох")
                ) {
                    audioFeedback.playBreathPhase(instruction == "Вдох")
                }
            }
        }
    }

    private fun startHoldLoop() {
        phaseJob?.cancel()
        phaseJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (state.phase != BreathingPhase.HOLD) break
                delay(1000)
                val st = _uiState.value
                if (st.phase != BreathingPhase.HOLD) break
                val elapsed = st.holdSeconds + 1
                val remaining = (st.holdCountdownRemaining - 1).coerceAtLeast(0)
                _uiState.update {
                    it.copy(
                        holdSeconds = elapsed,
                        holdCountdownRemaining = remaining,
                    )
                }
                if (remaining <= 0) {
                    transitionHoldToRecovery()
                    break
                }
            }
        }
    }

    private fun transitionHoldToRecovery() {
        val state = _uiState.value
        if (state.phase != BreathingPhase.HOLD) return
        audioFeedback.playRecoveryPhaseStart()
        val records = state.holdRecordsByRound.toMutableList()
        val index = (state.round - 1).coerceAtLeast(0)
        while (records.size <= index) records.add(0)
        records[index] = state.holdSeconds
        _uiState.update {
            it.copy(
                holdRecordsByRound = records,
                phase = BreathingPhase.RECOVERY,
                recoverySecondsLeft = config.recoverySeconds,
                instruction = "Восстановительный вдох",
                isInhalePhase = true,
                holdCountdownRemaining = 0,
            )
        }
        startRecoveryLoop()
    }

    private fun startRecoveryLoop() {
        phaseJob?.cancel()
        phaseJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (state.phase != BreathingPhase.RECOVERY) break
                delay(1000)
                val st = _uiState.value
                if (st.phase != BreathingPhase.RECOVERY) break
                val left = (st.recoverySecondsLeft - 1).coerceAtLeast(0)
                if (left == 0) {
                    if (st.round >= config.rounds) {
                        val snap = _uiState.value
                        val start = sessionStartWallClockMs
                        val end = System.currentTimeMillis()
                        val elapsed = start?.let { end - it } ?: snap.sessionElapsedMillis
                        audioFeedback.playSessionComplete()
                        _uiState.update {
                            it.copy(
                                phase = BreathingPhase.COMPLETE,
                                instruction = "Сессия завершена",
                                isRunning = false,
                                isInhalePhase = true,
                                sessionElapsedMillis = elapsed.coerceAtLeast(0),
                            )
                        }
                        if (start != null) {
                            persistSnapshot(start, end, snap, completedFully = true)
                        }
                        sessionStartWallClockMs = null
                        elapsedJob?.cancel()
                        elapsedJob = null
                        break
                    } else {
                        audioFeedback.playRoundCompleteChime()
                        _uiState.update {
                            it.copy(
                                phase = BreathingPhase.BREATHING,
                                round = it.round + 1,
                                breathCountInRound = 0,
                                breathingHalfStep = 1,
                                holdSeconds = 0,
                                holdCountdownRemaining = 0,
                                recoverySecondsLeft = config.recoverySeconds,
                                instruction = "Вдох",
                                isInhalePhase = true,
                            )
                        }
                        startBreathingLoop()
                        break
                    }
                } else {
                    _uiState.update { it.copy(recoverySecondsLeft = left) }
                }
            }
        }
    }

    private fun mapEntityToHistoryRow(e: BreathingSessionEntity): BreathingHistoryRowUi {
        val zdt = Instant.ofEpochMilli(e.endedAtMillis).atZone(ZoneId.systemDefault())
        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", Locale.forLanguageTag("ru"))
        val durMin = e.durationSeconds / 60
        val durSec = e.durationSeconds % 60
        val durationLabel = "$durMin:${durSec.toString().padStart(2, '0')}"
        return BreathingHistoryRowUi(
            id = e.id,
            endedAtMillis = e.endedAtMillis,
            dateTimeLabel = formatter.format(zdt),
            durationLabel = durationLabel,
            completedFully = e.completedFully,
        )
    }

    override fun onCleared() {
        super.onCleared()
        phaseJob?.cancel()
        elapsedJob?.cancel()
        audioFeedback.release()
    }
}
