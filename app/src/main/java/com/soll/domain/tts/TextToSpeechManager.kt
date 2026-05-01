package com.soll.domain.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    data class DownloadingModel(val progress: Float) : TtsState()
}

enum class TtsServiceAction {
    NEXT_CHAPTER, PREV_CHAPTER, PLAY, PAUSE, STOP
}

enum class TtsEngineType {
    SYSTEM, SILERO
}

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val sileroEngine: SileroJitEngine
) {
    private var tts: TextToSpeech? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _engineType = MutableStateFlow(TtsEngineType.SYSTEM)
    val engineType: StateFlow<TtsEngineType> = _engineType.asStateFlow()

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private val _chapterFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val chapterFinished: SharedFlow<Unit> = _chapterFinished.asSharedFlow()

    private val _serviceActions = MutableSharedFlow<TtsServiceAction>(extraBufferCapacity = 5)
    val serviceActions: SharedFlow<TtsServiceAction> = _serviceActions.asSharedFlow()

    private var currentText: String? = null
    private var isPaused = false
    private var currentChunks: List<String> = emptyList()
    private var currentChunkIndex = 0
    private var currentChunkOffset = 0
    private var onUtteranceCompleted: (() -> Unit)? = null

    init {
        scope.launch {
            sileroEngine.isSpeaking.collect { speaking ->
                if (_engineType.value == TtsEngineType.SILERO) {
                    _isSpeaking.value = speaking
                    if (speaking) _state.value = TtsState.Speaking("silero")
                    else if (_state.value is TtsState.Speaking) _state.value = TtsState.Ready
                }
            }
        }
        scope.launch {
            sileroEngine.currentWordRange.collect { range ->
                if (_engineType.value == TtsEngineType.SILERO) _currentWordRange.value = range
            }
        }
        scope.launch {
            sileroEngine.downloadProgress.collect { progress ->
                if (progress != null && _engineType.value == TtsEngineType.SILERO)
                    _state.value = TtsState.DownloadingModel(progress)
            }
        }
    }

    fun setEngineType(type: TtsEngineType) {
        if (_isSpeaking.value) stop()
        _engineType.value = type
    }

    fun initialize(enginePackage: String? = null): Boolean {
        if (_state.value is TtsState.Speaking) stop()
        shutdownSystemTts()
        _state.value = TtsState.Initializing

        var initResult = false
        val initListener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val result = engine.setLanguage(Locale("ru", "RU"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setSpeechRate(1.0f)
                    setupProgressListener(engine)
                    _state.value = TtsState.Ready
                    initResult = true
                }
            } else {
                _state.value = TtsState.Error("TTS initialization failed")
            }
        }

        tts = if (enginePackage != null) {
            TextToSpeech(context, initListener, enginePackage)
        } else {
            TextToSpeech(context, initListener)
        }
        return initResult
    }

    suspend fun initializeSilero(): Boolean {
        _state.value = TtsState.Initializing
        val success = sileroEngine.initialize()
        _state.value = if (success) TtsState.Ready else TtsState.Error("Failed to download Silero model")
        return success
    }

    private fun setupProgressListener(engine: TextToSpeech) {
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
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (_engineType.value == TtsEngineType.SYSTEM) {
                    _currentWordRange.value = IntRange(currentChunkOffset + start, currentChunkOffset + end)
                }
            }
        })
    }

    fun getAvailableEngines(): List<TextToSpeech.EngineInfo> = tts?.engines ?: emptyList()

    fun reinitializeWithEngine(enginePackage: String) { initialize(enginePackage) }

    fun speakChapter(text: String) {
        when (_engineType.value) {
            TtsEngineType.SILERO -> speakChapterSilero(text)
            TtsEngineType.SYSTEM -> speakChapterSystem(text)
        }
    }

    private fun speakChapterSilero(text: String) {
        currentText = text
        isPaused = false
        _currentWordRange.value = null
        _state.value = TtsState.Speaking("silero")
        _isSpeaking.value = true
        scope.launch(Dispatchers.IO) {
            sileroEngine.speakChapter(text) { _chapterFinished.tryEmit(Unit) }
        }
    }

    private fun speakChapterSystem(text: String) {
        currentText = text
        isPaused = false
        currentChunks = splitIntoChunks(text)
        currentChunkIndex = 0
        currentChunkOffset = 0
        _currentWordRange.value = null
        speakNextChunk()
    }

    private fun speakNextChunk() {
        if (currentChunkIndex >= currentChunks.size) {
            onUtteranceCompleted = null
            _currentWordRange.value = null
            _chapterFinished.tryEmit(Unit)
            return
        }
        if (isPaused) { onUtteranceCompleted = null; return }

        currentChunkOffset = 0
        for (i in 0 until currentChunkIndex) currentChunkOffset += currentChunks[i].length

        onUtteranceCompleted = { currentChunkIndex++; speakNextChunk() }
        tts?.speak(currentChunks[currentChunkIndex], TextToSpeech.QUEUE_FLUSH, null, "chunk_$currentChunkIndex")
    }

    private fun splitIntoChunks(text: String, maxSize: Int = 3900): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxSize).coerceAtMost(text.length)
            if (end < text.length) {
                val region = text.substring(start, end)
                val lastSentenceEnd = maxOf(
                    region.lastIndexOf(". "), region.lastIndexOf("! "), region.lastIndexOf("? "),
                    region.lastIndexOf(".\n"), region.lastIndexOf("!\n"), region.lastIndexOf("?\n")
                )
                if (lastSentenceEnd > 0) end = start + lastSentenceEnd + 2
                else { val lastSpace = region.lastIndexOf(' '); if (lastSpace > 0) end = start + lastSpace + 1 }
            }
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks
    }

    fun pause() {
        isPaused = true
        when (_engineType.value) {
            TtsEngineType.SILERO -> sileroEngine.pause()
            TtsEngineType.SYSTEM -> tts?.stop()
        }
        _isSpeaking.value = false
        _currentWordRange.value = null
        _state.value = TtsState.Paused
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        when (_engineType.value) {
            TtsEngineType.SILERO -> currentText?.let { speakChapterSilero(it) }
            TtsEngineType.SYSTEM -> {
                if (currentChunks.isNotEmpty()) speakNextChunk()
                else currentText?.let { speakChapterSystem(it) }
            }
        }
    }

    fun stop() {
        isPaused = false
        currentText = null
        currentChunks = emptyList()
        currentChunkIndex = 0
        currentChunkOffset = 0
        onUtteranceCompleted = null
        _currentWordRange.value = null
        when (_engineType.value) {
            TtsEngineType.SILERO -> sileroEngine.stop()
            TtsEngineType.SYSTEM -> tts?.stop()
        }
        _isSpeaking.value = false
        if (_state.value !is TtsState.Idle) _state.value = TtsState.Ready
    }

    fun emitServiceAction(action: TtsServiceAction) { _serviceActions.tryEmit(action) }

    fun setSpeechRate(rate: Float) {
        val coerced = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(coerced)
        sileroEngine.setSpeechRate(coerced)
    }

    fun setPitch(pitch: Float) { tts?.setPitch(pitch.coerceIn(0.5f, 2.0f)) }
    fun setLanguage(locale: Locale): Boolean {
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }
    fun getAvailableLanguages(): List<Locale> = tts?.availableLanguages?.toList() ?: emptyList()
    fun isModelDownloaded(): Boolean = sileroEngine.isModelDownloaded()

    private fun shutdownSystemTts() { tts?.stop(); tts?.shutdown(); tts = null }

    fun shutdown() {
        stop(); shutdownSystemTts(); sileroEngine.shutdown()
        _state.value = TtsState.Idle; _isSpeaking.value = false; _currentWordRange.value = null
    }
}
