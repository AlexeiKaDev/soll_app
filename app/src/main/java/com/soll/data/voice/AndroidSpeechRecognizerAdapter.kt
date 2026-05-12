package com.soll.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.soll.domain.voice.SttAdapter
import com.soll.domain.voice.SttAdapterState
import com.soll.domain.voice.SttRecognitionMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSpeechRecognizerAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : SttAdapter {

    private val _state = MutableStateFlow(currentAvailability())
    override val state: StateFlow<SttAdapterState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var activeMode: SttRecognitionMode = SttRecognitionMode.SYSTEM

    override fun startListening(preferOffline: Boolean) {
        val availability = currentAvailability(preferOffline = preferOffline)
        if (!availability.isAvailable) {
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
            partialText = "",
            finalText = null,
            errorMessage = null,
            activeMode = desiredMode,
        )

        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
        )
    }

    override fun stopListening() {
        recognizer?.stopListening()
        _state.value = _state.value.copy(isListening = false)
    }

    override fun clearFinalResult() {
        _state.value = _state.value.copy(finalText = null)
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognizerFor(mode: SttRecognitionMode): SpeechRecognizer {
        val current = recognizer
        if (current != null && activeMode == mode) return current

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
            _state.value = _state.value.copy(isListening = false)
        }

        override fun onError(error: Int) {
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = error.toUserMessage(),
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results.bestText()
            _state.value = _state.value.copy(
                isListening = false,
                partialText = "",
                finalText = text,
                errorMessage = if (text.isNullOrBlank()) "Речь не распознана" else null,
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            _state.value = _state.value.copy(partialText = partialResults.bestText().orEmpty())
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.bestText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.maxByOrNull { it.length }
            ?.trim()
            ?.takeIf { it.isNotBlank() }

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
