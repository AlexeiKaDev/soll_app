package com.soll.domain.tts.book

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.soll.domain.tts.TtsEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemAndroidBookEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsBookEngine {

    override val type = TtsEngineType.SYSTEM
    override val displayName = "Системный TTS"

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    override val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private var tts: TextToSpeech? = null

    private var currentChunks: List<String> = emptyList()
    private var currentChunkIndex = 0
    private var currentChunkOffset = 0
    private var onUtteranceCompleted: (() -> Unit)? = null
    private var chapterFinishedCallback: (() -> Unit)? = null
    private var paused = false
    private var pitchSetting = 1.0f

    override fun voiceOptions(): List<TtsVoiceOption> = emptyList()

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "pitch",
            label = "Тон (системный TTS)",
            range = 0.5f..2.0f,
            defaultValue = pitchSetting,
        ),
    )

    override fun applyTunable(key: String, value: Float) {
        if (key == "pitch") setPitch(value)
    }

    override suspend fun prepare(): TtsPrepareResult = TtsPrepareResult(
        success = true,
        engineType = type,
    )

    fun setup(enginePackage: String?, onInitialized: (Boolean) -> Unit = {}) {
        shutdownTtsOnly()
        _isReady.value = false
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    val lang = engine.setLanguage(Locale.forLanguageTag("ru-RU"))
                    if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        engine.setLanguage(Locale.getDefault())
                    }
                    engine.setSpeechRate(1.0f)
                    engine.setPitch(pitchSetting)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            onUtteranceCompleted?.invoke()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                        }

                        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                            _currentWordRange.value = IntRange(currentChunkOffset + start, currentChunkOffset + end)
                        }
                    })
                    _isReady.value = true
                    onInitialized(true)
                } ?: onInitialized(false)
            } else {
                _isReady.value = false
                Timber.e("Системный TTS не запустился: $status")
                onInitialized(false)
            }
        }
        tts = if (enginePackage != null) {
            TextToSpeech(context, listener, enginePackage)
        } else {
            TextToSpeech(context, listener)
        }
    }

    fun availableEngines(): List<TextToSpeech.EngineInfo> = tts?.engines ?: emptyList()

    override suspend fun speakChapter(text: String, onChapterFinished: () -> Unit) {
        paused = false
        currentChunks = splitIntoChunks(text)
        currentChunkIndex = 0
        currentChunkOffset = 0
        _currentWordRange.value = null
        chapterFinishedCallback = onChapterFinished
        speakNextChunkInternal()
    }

    private fun speakNextChunkInternal() {
        val onChapterFinished = chapterFinishedCallback ?: return
        if (currentChunkIndex >= currentChunks.size) {
            onUtteranceCompleted = null
            _currentWordRange.value = null
            chapterFinishedCallback = null
            onChapterFinished()
            return
        }

        currentChunkOffset = 0
        for (i in 0 until currentChunkIndex) {
            currentChunkOffset += currentChunks[i].length
        }

        onUtteranceCompleted = {
            currentChunkIndex++
            speakNextChunkInternal()
        }
        tts?.speak(currentChunks[currentChunkIndex], TextToSpeech.QUEUE_FLUSH, null, "chunk_$currentChunkIndex")
            ?: run {
                Timber.e("Системный TTS: попытка озвучивания без готового движка")
                onChapterFinished()
            }
    }

    override fun pause() {
        paused = true
        tts?.stop()
        onUtteranceCompleted = null
        _isSpeaking.value = false
        _currentWordRange.value = null
    }

    override suspend fun resume() {
        resumeIfPaused()
    }

    fun resumeIfPaused() {
        if (!paused) return
        if (chapterFinishedCallback == null) return
        paused = false
        speakNextChunkInternal()
    }

    fun hasPausedProgress(): Boolean = paused && currentChunks.isNotEmpty()

    override fun stop() {
        paused = false
        chapterFinishedCallback = null
        currentChunks = emptyList()
        currentChunkIndex = 0
        currentChunkOffset = 0
        onUtteranceCompleted = null
        _currentWordRange.value = null
        tts?.stop()
        _isSpeaking.value = false
    }

    override fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        pitchSetting = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitchSetting)
    }

    fun setLanguage(locale: Locale): Boolean {
        val r = tts?.setLanguage(locale)
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun availableLanguages(): List<Locale> = tts?.availableLanguages?.toList() ?: emptyList()

    override fun shutdown() {
        stop()
        shutdownTtsOnly()
    }

    private fun shutdownTtsOnly() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
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
                    region.lastIndexOf(".\n"), region.lastIndexOf("!\n"), region.lastIndexOf("?\n"),
                )
                if (lastSentenceEnd > 0) end = start + lastSentenceEnd + 2
                else {
                    val lastSpace = region.lastIndexOf(' ')
                    if (lastSpace > 0) end = start + lastSpace + 1
                }
            }
            chunks.add(text.substring(start, end))
            start = end
        }
        return chunks
    }
}
