package com.soll.domain.voice

enum class SttRecognitionMode {
    SYSTEM,
    ON_DEVICE,
}

data class SttAdapterState(
    val isAvailable: Boolean = true,
    val isListening: Boolean = false,
    val partialText: String = "",
    val finalText: String? = null,
    val errorMessage: String? = null,
    val preferOffline: Boolean = false,
    val holdUntilStop: Boolean = false,
    val recordingLimitReached: Boolean = false,
    val isOnDeviceRecognitionAvailable: Boolean = false,
    val activeMode: SttRecognitionMode = SttRecognitionMode.SYSTEM,
)

interface SttAdapter {
    val state: kotlinx.coroutines.flow.StateFlow<SttAdapterState>
    fun startListening(
        preferOffline: Boolean = false,
        holdUntilStop: Boolean = false,
        maxDurationMillis: Long = MAX_PTT_DURATION_MS,
    )
    fun stopListening()
    fun cancelListening()
    fun clearFinalResult()
    fun destroy()
}

const val MAX_PTT_DURATION_MS = 30_000L
