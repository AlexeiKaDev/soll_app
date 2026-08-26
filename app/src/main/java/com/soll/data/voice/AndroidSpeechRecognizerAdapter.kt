package com.soll.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.soll.domain.voice.SttAdapter
import com.soll.domain.voice.SttAdapterState
import com.soll.domain.voice.SttRecognitionMode
import com.soll.domain.voice.MAX_PTT_DURATION_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSpeechRecognizerAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : SttAdapter {

    private var recognizer: SpeechRecognizer? = null
    private var activeMode: SttRecognitionMode = SttRecognitionMode.SYSTEM
    private val handler = Handler(Looper.getMainLooper())
    private var holdUntilStop: Boolean = false
    private var stopRequested: Boolean = false
    private var cancelRequested: Boolean = false
    private var lastPreferOffline: Boolean = false
    private var manualFinalEmitted: Boolean = false
    private val manualSegments = mutableListOf<String>()
    private val speechAudioRouter = BluetoothSpeechAudioRouter(context)

    private val _state = MutableStateFlow(currentAvailability())
    override val state: StateFlow<SttAdapterState> = _state.asStateFlow()

    private val durationLimit = Runnable {
        if (_state.value.isListening) {
            _state.value = _state.value.copy(recordingLimitReached = true)
            stopListening()
        }
    }

    override fun startListening(
        preferOffline: Boolean,
        holdUntilStop: Boolean,
        maxDurationMillis: Long,
    ) {
        handler.removeCallbacks(durationLimit)
        this.holdUntilStop = holdUntilStop
        this.stopRequested = false
        this.cancelRequested = false
        this.lastPreferOffline = preferOffline
        this.manualFinalEmitted = false
        manualSegments.clear()
        speechAudioRouter.prepareBluetoothInput()
        startRecognizer(preferOffline = preferOffline, resetText = true)
        handler.postDelayed(
            durationLimit,
            maxDurationMillis.coerceIn(MIN_PTT_DURATION_MS, MAX_PTT_DURATION_MS),
        )
    }

    private fun startRecognizer(preferOffline: Boolean, resetText: Boolean) {
        val availability = currentAvailability(preferOffline = preferOffline)
        if (!availability.isAvailable) {
            speechAudioRouter.release()
            _state.value = availability.copy(
                errorMessage = "Распознавание речи недоступно на этом устройстве",
            )
            return
        }

        val systemAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val desiredMode = if (
            availability.isOnDeviceRecognitionAvailable &&
            (preferOffline || !systemAvailable)
        ) {
            SttRecognitionMode.ON_DEVICE
        } else {
            SttRecognitionMode.SYSTEM
        }
        val speechRecognizer = recognizerFor(desiredMode)

        _state.value = availability.copy(
            isAvailable = true,
            isListening = true,
            partialText = if (resetText) "" else _state.value.partialText,
            finalText = null,
            errorMessage = null,
            recordingLimitReached = false,
            holdUntilStop = holdUntilStop,
            activeMode = desiredMode,
        )

        runCatching {
            speechRecognizer.startListening(buildIntent(preferOffline))
        }.onFailure { error ->
            speechAudioRouter.release()
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = error.message ?: "Не удалось запустить распознавание речи",
            )
        }
    }

    override fun stopListening() {
        if (!_state.value.isListening) return
        handler.removeCallbacks(durationLimit)
        stopRequested = true
        recognizer?.stopListening()
        if (holdUntilStop) {
            handler.postDelayed({ emitManualFinalIfNeeded(fallbackError = null) }, STOP_RESULT_GRACE_MS)
        } else {
            _state.value = _state.value.copy(isListening = false)
        }
    }

    override fun cancelListening() {
        handler.removeCallbacks(durationLimit)
        cancelRequested = true
        stopRequested = false
        holdUntilStop = false
        manualFinalEmitted = true
        manualSegments.clear()
        recognizer?.cancel()
        speechAudioRouter.release()
        _state.value = _state.value.copy(
            isListening = false,
            partialText = "",
            finalText = null,
            errorMessage = null,
            holdUntilStop = false,
            recordingLimitReached = false,
        )
    }

    override fun clearFinalResult() {
        _state.value = _state.value.copy(finalText = null)
    }

    override fun destroy() {
        handler.removeCallbacksAndMessages(null)
        cancelRequested = true
        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        recognizer = null
        speechAudioRouter.release()
    }

    private fun buildIntent(preferOffline: Boolean): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000)
        }

    private fun recognizerFor(mode: SttRecognitionMode): SpeechRecognizer {
        val current = recognizer
        if (current != null && activeMode == mode) return current

        recognizer?.setRecognitionListener(null)
        recognizer?.destroy()
        activeMode = mode
        return createRecognizer(mode).also {
            recognizer = it
            it.setRecognitionListener(listener)
        }
    }

    private fun createRecognizer(mode: SttRecognitionMode): SpeechRecognizer =
        if (mode == SttRecognitionMode.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun currentAvailability(preferOffline: Boolean = false): SttAdapterState {
        val onDeviceAvailable = isOnDeviceRecognitionAvailable()
        val systemAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        return SttAdapterState(
            isAvailable = systemAvailable || onDeviceAvailable,
            preferOffline = preferOffline,
            holdUntilStop = holdUntilStop,
            isOnDeviceRecognitionAvailable = onDeviceAvailable,
            activeMode = activeMode,
        )
    }

    private fun isOnDeviceRecognitionAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(isListening = true, errorMessage = null)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (!holdUntilStop) {
                speechAudioRouter.release()
                _state.value = _state.value.copy(isListening = false)
            } else if (stopRequested) {
                speechAudioRouter.release()
            }
        }

        override fun onError(error: Int) {
            if (cancelRequested) return
            if (holdUntilStop && !stopRequested && error.isManualRetryable()) {
                restartManualRecognition()
                return
            }
            if (holdUntilStop && stopRequested) {
                speechAudioRouter.release()
                emitManualFinalIfNeeded(fallbackError = error.toUserMessage())
                return
            }
            speechAudioRouter.release()
            _state.value = _state.value.copy(isListening = false, errorMessage = error.toUserMessage())
        }

        override fun onResults(results: Bundle?) {
            if (cancelRequested) return
            val text = results.bestText()
            if (holdUntilStop) {
                rememberManualSegment(text)
                if (stopRequested) {
                    speechAudioRouter.release()
                    emitManualFinalIfNeeded(fallbackError = if (text.isNullOrBlank()) "Речь не распознана" else null)
                } else {
                    restartManualRecognition()
                }
                return
            }
            speechAudioRouter.release()
            _state.value = _state.value.copy(
                isListening = false,
                partialText = "",
                finalText = text,
                errorMessage = if (text.isNullOrBlank()) "Речь не распознана" else null,
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (cancelRequested) return
            _state.value = _state.value.copy(partialText = partialResults.bestText().orEmpty())
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.bestText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.maxByOrNull { it.length }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun restartManualRecognition() {
        if (!holdUntilStop || stopRequested) return
        _state.value = _state.value.copy(isListening = true, partialText = "", errorMessage = null)
        handler.postDelayed(
            {
                if (holdUntilStop && !stopRequested) {
                    startRecognizer(preferOffline = lastPreferOffline, resetText = false)
                }
            },
            MANUAL_RESTART_DELAY_MS,
        )
    }

    private fun rememberManualSegment(text: String?) {
        val clean = text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (clean.isBlank()) return
        if (manualSegments.lastOrNull() != clean) {
            manualSegments += clean
        }
    }

    private fun emitManualFinalIfNeeded(fallbackError: String?) {
        if (!holdUntilStop || manualFinalEmitted) return
        manualFinalEmitted = true
        speechAudioRouter.release()
        val finalText = manualSegments.joinToString(" ").trim().takeIf { it.isNotBlank() }
        holdUntilStop = false
        stopRequested = false
        manualSegments.clear()
        _state.value = _state.value.copy(
            isListening = false,
            partialText = "",
            finalText = finalText,
            errorMessage = if (finalText == null) fallbackError ?: "Речь не распознана" else null,
            holdUntilStop = false,
        )
    }

    private fun Int.isManualRetryable(): Boolean =
        this == SpeechRecognizer.ERROR_NO_MATCH ||
            this == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
            this == SpeechRecognizer.ERROR_CLIENT ||
            this == SpeechRecognizer.ERROR_RECOGNIZER_BUSY

    private fun Int.toUserMessage(): String =
        when (this) {
            SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи микрофона"
            SpeechRecognizer.ERROR_CLIENT -> "Распознавание остановлено"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Сетевая ошибка распознавания"
            SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание уже запущено"
            SpeechRecognizer.ERROR_SERVER -> "Сервис распознавания недоступен"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не услышана"
            else -> "Ошибка распознавания речи: $this"
        }
}

private const val MANUAL_RESTART_DELAY_MS = 300L
private const val STOP_RESULT_GRACE_MS = 800L
private const val MIN_PTT_DURATION_MS = 1_000L
