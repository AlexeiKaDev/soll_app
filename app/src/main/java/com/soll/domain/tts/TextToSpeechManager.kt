package com.soll.domain.tts

import android.speech.tts.TextToSpeech
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.book.PiperSherpaBookEngine
import com.soll.domain.tts.book.SystemAndroidBookEngine
import com.soll.domain.tts.book.TtsBookEngine
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.book.NatashaVitsBookEngine
import com.soll.domain.tts.book.UtrobinVitsBookEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

enum class TtsEngineType {
    SYSTEM,
    SILERO,
    UTROBIN,
    NATASHA,
}

enum class TtsServiceAction {
    NEXT_CHAPTER,
    PREV_CHAPTER,
    PLAY,
    PAUSE,
    STOP,
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TextToSpeechManager @Inject constructor(
    private val systemEngine: SystemAndroidBookEngine,
    private val piperEngine: PiperSherpaBookEngine,
    private val utrobinEngine: UtrobinVitsBookEngine,
    private val natashaEngine: NatashaVitsBookEngine,
) {
    private val engines: Map<TtsEngineType, TtsBookEngine> = mapOf(
        TtsEngineType.SYSTEM to systemEngine,
        TtsEngineType.SILERO to piperEngine,
        TtsEngineType.UTROBIN to utrobinEngine,
        TtsEngineType.NATASHA to natashaEngine,
    )

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

    init {
        scope.launch {
            _engineType.flatMapLatest { engines.getValue(it).isSpeaking }.collect { speaking ->
                _isSpeaking.value = speaking
                if (speaking) {
                    _state.value = TtsState.Speaking(_engineType.value.name.lowercase())
                } else if (_state.value is TtsState.Speaking) {
                    _state.value = TtsState.Ready
                }
            }
        }
        scope.launch {
            _engineType.flatMapLatest { engines.getValue(it).currentWordRange }.collect { range ->
                _currentWordRange.value = range
            }
        }
        scope.launch {
            _engineType.flatMapLatest { engines.getValue(it).downloadProgress }.collect { progress ->
                if (progress != null && _engineType.value != TtsEngineType.SYSTEM) {
                    _state.value = TtsState.DownloadingModel(progress)
                }
            }
        }
    }

    fun engine(type: TtsEngineType): TtsBookEngine = engines.getValue(type)

    fun voiceOptions(type: TtsEngineType): List<TtsVoiceOption> = engines.getValue(type).voiceOptions()

    fun isEngineReady(type: TtsEngineType): Boolean = engines.getValue(type).isReady.value

    fun setEngineType(type: TtsEngineType) {
        if (_isSpeaking.value) stop()
        _engineType.value = type
    }

    fun initialize(enginePackage: String? = null): Boolean {
        if (_state.value is TtsState.Speaking) stop()
        shutdownSystemTtsForRecreate()
        _state.value = TtsState.Initializing
        systemEngine.setup(enginePackage) { ok ->
            _state.value = if (ok) TtsState.Ready else TtsState.Error("TTS initialization failed")
        }
        return true
    }

    suspend fun initializeSilero(): Boolean = prepareEngine(TtsEngineType.SILERO)

    suspend fun initializeUtrobin(): Boolean = prepareEngine(TtsEngineType.UTROBIN)

    suspend fun initializeNatasha(): Boolean = prepareEngine(TtsEngineType.NATASHA)

    suspend fun prepareEngine(type: TtsEngineType): Boolean {
        _state.value = TtsState.Initializing
        val engine = engines.getValue(type)
        val success = engine.prepare()
        _state.value = if (success) {
            TtsState.Ready
        } else {
            TtsState.Error("Failed to initialize ${engine.displayName}")
        }
        return success
    }

    fun getAvailableEngines(): List<TextToSpeech.EngineInfo> = systemEngine.availableEngines()

    fun reinitializeWithEngine(enginePackage: String) {
        initialize(enginePackage)
    }

    fun speakChapter(text: String) {
        currentText = text
        isPaused = false
        when (_engineType.value) {
            TtsEngineType.SYSTEM -> {
                scope.launch(Dispatchers.Main) {
                    systemEngine.speakChapter(text) { _chapterFinished.tryEmit(Unit) }
                }
            }
            TtsEngineType.SILERO -> {
                scope.launch(Dispatchers.IO) {
                    piperEngine.speakChapter(text) { _chapterFinished.tryEmit(Unit) }
                }
            }
            TtsEngineType.UTROBIN -> {
                scope.launch(Dispatchers.IO) {
                    utrobinEngine.speakChapter(text) { _chapterFinished.tryEmit(Unit) }
                }
            }
            TtsEngineType.NATASHA -> {
                scope.launch(Dispatchers.IO) {
                    natashaEngine.speakChapter(text) { _chapterFinished.tryEmit(Unit) }
                }
            }
        }
    }

    fun pause() {
        isPaused = true
        when (_engineType.value) {
            TtsEngineType.SYSTEM -> systemEngine.pause()
            TtsEngineType.SILERO -> piperEngine.pause()
            TtsEngineType.UTROBIN -> utrobinEngine.pause()
            TtsEngineType.NATASHA -> natashaEngine.pause()
        }
        _isSpeaking.value = false
        _currentWordRange.value = null
        _state.value = TtsState.Paused
    }

    fun resume() {
        if (!isPaused) return
        isPaused = false
        when (_engineType.value) {
            TtsEngineType.SYSTEM -> {
                if (systemEngine.hasPausedProgress()) {
                    systemEngine.resumeIfPaused()
                } else {
                    currentText?.let { txt ->
                        scope.launch(Dispatchers.Main) {
                            systemEngine.speakChapter(txt) { _chapterFinished.tryEmit(Unit) }
                        }
                    }
                }
            }
            TtsEngineType.SILERO -> {
                currentText?.let { txt ->
                    scope.launch(Dispatchers.IO) {
                        piperEngine.speakChapter(txt) { _chapterFinished.tryEmit(Unit) }
                    }
                }
            }
            TtsEngineType.UTROBIN -> {
                currentText?.let { txt ->
                    scope.launch(Dispatchers.IO) {
                        utrobinEngine.speakChapter(txt) { _chapterFinished.tryEmit(Unit) }
                    }
                }
            }
            TtsEngineType.NATASHA -> {
                currentText?.let { txt ->
                    scope.launch(Dispatchers.IO) {
                        natashaEngine.speakChapter(txt) { _chapterFinished.tryEmit(Unit) }
                    }
                }
            }
        }
    }

    fun stop() {
        isPaused = false
        currentText = null
        _currentWordRange.value = null
        when (_engineType.value) {
            TtsEngineType.SYSTEM -> systemEngine.stop()
            TtsEngineType.SILERO -> piperEngine.stop()
            TtsEngineType.UTROBIN -> utrobinEngine.stop()
            TtsEngineType.NATASHA -> natashaEngine.stop()
        }
        _isSpeaking.value = false
        if (_state.value !is TtsState.Idle) _state.value = TtsState.Ready
    }

    fun emitServiceAction(action: TtsServiceAction) {
        _serviceActions.tryEmit(action)
    }

    fun setSpeechRate(rate: Float) {
        val coerced = rate.coerceIn(0.5f, 2.0f)
        systemEngine.setSpeechRate(coerced)
        piperEngine.setSpeechRate(coerced)
        utrobinEngine.setSpeechRate(coerced)
        natashaEngine.setSpeechRate(coerced)
    }

    fun setPitch(pitch: Float) {
        systemEngine.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun setLanguage(locale: Locale): Boolean = systemEngine.setLanguage(locale)

    fun getAvailableLanguages(): List<Locale> = systemEngine.availableLanguages()

    fun isModelDownloaded(): Boolean = piperEngine.isModelDownloaded()

    fun isModelDownloadedFor(type: TtsEngineType): Boolean = engines.getValue(type).isModelDownloaded()

    fun setVoiceIdForEngine(type: TtsEngineType, voiceId: String) {
        engines.getValue(type).setVoiceId(voiceId)
    }

    fun tunableSettingsFor(type: TtsEngineType) = engines.getValue(type).tunableSettings()

    fun applyTunableForEngine(type: TtsEngineType, key: String, value: Float) {
        engines.getValue(type).applyTunable(key, value)
    }

    /** Chunk merge + default thread budget for Utrobin/Natasha/Piper. */
    fun applyBookPerformanceProfile(profile: TtsBookPerformanceProfile) {
        engines.values.forEach { it.applyPerformanceProfile(profile) }
    }

    private fun shutdownSystemTtsForRecreate() {
        systemEngine.shutdown()
    }

    fun shutdown() {
        stop()
        systemEngine.shutdown()
        piperEngine.shutdown()
        utrobinEngine.shutdown()
        natashaEngine.shutdown()
        _state.value = TtsState.Idle
        _isSpeaking.value = false
        _currentWordRange.value = null
    }
}
