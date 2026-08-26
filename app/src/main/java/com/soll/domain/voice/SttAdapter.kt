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

data class ResolvedSttTerminal(
    val text: String? = null,
    val suppressError: Boolean = false,
)

fun resolveSttTerminal(
    previousPartial: String,
    finalText: String?,
    errorMessage: String?,
    isListening: Boolean,
): ResolvedSttTerminal {
    val cleanFinal = finalText.normalizedSttText()
    if (cleanFinal.isNotBlank()) {
        return ResolvedSttTerminal(text = cleanFinal, suppressError = true)
    }
    if (isListening || errorMessage !in RECOVERABLE_EMPTY_RESULT_MESSAGES) {
        return ResolvedSttTerminal()
    }
    val cleanPartial = previousPartial.normalizedSttText()
    return if (cleanPartial.isNotBlank()) {
        ResolvedSttTerminal(text = cleanPartial, suppressError = true)
    } else {
        ResolvedSttTerminal()
    }
}

const val MAX_PTT_DURATION_MS = 30_000L

private val RECOVERABLE_EMPTY_RESULT_MESSAGES = setOf(
    "Речь не распознана",
    "Речь не услышана",
)

private fun String?.normalizedSttText(): String =
    this?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
