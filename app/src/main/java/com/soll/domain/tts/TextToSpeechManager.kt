package com.soll.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class TtsState {
    data object Idle : TtsState()
    data object Initializing : TtsState()
    data object Ready : TtsState()
    data class Speaking(val utteranceId: String) : TtsState()
    data object Paused : TtsState()
    data class Error(val message: String) : TtsState()
}

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var currentText: String? = null
    private var currentPosition: Int = 0
    private var isPaused = false

    private var onUtteranceCompleted: (() -> Unit)? = null

    fun initialize(): Flow<Boolean> = callbackFlow {
        _state.value = TtsState.Initializing

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    // Set language to Russian by default, fallback to default
                    val result = engine.setLanguage(Locale("ru", "RU"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }

                    // Set speech rate
                    engine.setSpeechRate(1.0f)

                    // Set up progress listener
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                            _state.value = TtsState.Speaking(utteranceId ?: "")
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _state.value = TtsState.Ready
                            onUtteranceCompleted?.invoke()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            _state.value = TtsState.Error("TTS Error")
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            _state.value = TtsState.Error("TTS Error: $errorCode")
                        }
                    })

                    _state.value = TtsState.Ready
                    trySend(true)
                }
            } else {
                _state.value = TtsState.Error("TTS initialization failed")
                trySend(false)
            }
        }

        awaitClose { }
    }

    fun speak(text: String, utteranceId: String = System.currentTimeMillis().toString()) {
        if (_state.value !is TtsState.Ready && _state.value !is TtsState.Speaking) {
            Timber.w("TTS not ready, current state: ${_state.value}")
            return
        }

        currentText = text
        currentPosition = 0
        isPaused = false

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun speakChunked(
        text: String,
        chunkSize: Int = 4000,
        onChunkComplete: ((Int, Int) -> Unit)? = null
    ) {
        val chunks = text.chunked(chunkSize)
        var currentChunk = 0

        fun speakNextChunk() {
            if (currentChunk < chunks.size && !isPaused) {
                onUtteranceCompleted = {
                    onChunkComplete?.invoke(currentChunk, chunks.size)
                    currentChunk++
                    speakNextChunk()
                }
                speak(chunks[currentChunk], "chunk_$currentChunk")
            } else {
                onUtteranceCompleted = null
            }
        }

        speakNextChunk()
    }

    fun pause() {
        isPaused = true
        tts?.stop()
        _isSpeaking.value = false
        _state.value = TtsState.Paused
    }

    fun resume() {
        if (isPaused && currentText != null) {
            isPaused = false
            // Resume from current position (simplified - starts from beginning)
            speak(currentText!!)
        }
    }

    fun stop() {
        isPaused = false
        currentText = null
        currentPosition = 0
        onUtteranceCompleted = null
        tts?.stop()
        _isSpeaking.value = false
        _state.value = TtsState.Ready
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun setLanguage(locale: Locale): Boolean {
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun getAvailableLanguages(): List<Locale> {
        return tts?.availableLanguages?.toList() ?: emptyList()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _state.value = TtsState.Idle
        _isSpeaking.value = false
    }
}
