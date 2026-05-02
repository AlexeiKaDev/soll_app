package com.soll.domain.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UtrobinTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private var isPaused = false
    private var currentSentenceIndex = 0
    private var sentences: List<SentenceInfo> = emptyList()
    private var speechRate = 1.0f
    private var speakerId = 0 // 0=Woman, 1=Man
    private var sampleRate = 16000

    companion object {
        private const val ASSETS_DIR = "utrobin_tts"
        private const val FILES_DIR = "utrobin_tts_extracted"
        val SPEAKERS = listOf("Женский" to 0, "Мужской" to 1)
    }

    data class SentenceInfo(val text: String, val startOffset: Int, val endOffset: Int)

    fun isModelDownloaded(): Boolean = true // always available from assets

    fun setSpeaker(id: Int) { speakerId = id }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Copy assets to files dir (sherpa-onnx needs file paths)
            val dir = File(context.filesDir, FILES_DIR)
            dir.mkdirs()

            val modelFile = File(dir, "model.onnx")
            val tokensFile = File(dir, "tokens.txt")

            if (!modelFile.exists() || modelFile.length() < 10_000_000) {
                Timber.d("Extracting UtrobinTTS from assets...")
                _downloadProgress.value = 0.1f
                copyAsset("$ASSETS_DIR/model.onnx", modelFile)
                _downloadProgress.value = 0.9f
                _downloadProgress.value = null
                Timber.d("Extracted: ${modelFile.length() / 1024 / 1024}MB")
            }
            // Always refresh tokens; CRLF breaks ReadTokens first line (space id 0) and drops U+0020 from the map.
            copyTokensAsset("$ASSETS_DIR/tokens.txt", tokensFile)

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        tokens = tokensFile.absolutePath,
                        dataDir = "",
                    ),
                    numThreads = 2,
                    debug = false,
                )
            )
            tts = OfflineTts(config = config)
            sampleRate = tts?.sampleRate() ?: 16000
            _isReady.value = true
            Timber.d("UtrobinTTS ready, sampleRate=$sampleRate, speakers=2")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to init UtrobinTTS")
            _isReady.value = false
            false
        }
    }

    private fun copyAsset(assetPath: String, target: File) {
        context.assets.open(assetPath).use { inp ->
            FileOutputStream(target).use { out -> inp.copyTo(out) }
        }
    }

    /** Sherpa ReadTokens: if a line ends with \\r, the "space id" line is mis-parsed and U+0020 is missing → native crash. */
    private fun copyTokensAsset(assetPath: String, target: File) {
        context.assets.open(assetPath).use { inp ->
            val text = inp.bufferedReader(StandardCharsets.UTF_8).readText()
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
            target.writeText(normalized, StandardCharsets.UTF_8)
        }
    }

    /** Map chars not in utrobin tokens to known ones (see app/src/main/assets/utrobin_tts/tokens.txt). */
    private fun normalizeForUtrobinTts(text: String): String =
        text.lowercase()
            .replace('\u00a0', ' ')
            .replace('\u00ab', ' ')
            .replace('\u00bb', ' ')
            .replace('\u2011', ' ')
            .replace('\u2010', ',')
            .replace('\u2013', ',')
            .replace('\u2014', ',')
            .replace('-', ',')
            .replace(Regex("\\s+"), " ")
            .trim()

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stop()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0; isPaused = false; _isSpeaking.value = true

        playbackJob = launch(Dispatchers.IO) {
            try {
                while (currentSentenceIndex < sentences.size && isActive && !isPaused) {
                    val s = sentences[currentSentenceIndex]
                    _currentWordRange.value = IntRange(s.startOffset, s.endOffset)
                    val audio = generateAudio(s.text)
                    if (audio != null && audio.size > 100 && isActive && !isPaused) {
                        val wj = launch { trackWords(s, audio.size) }
                        playAudio(audio)
                        wj.cancel()
                    }
                    if (!isPaused) currentSentenceIndex++
                }
                if (currentSentenceIndex >= sentences.size && !isPaused) {
                    _isSpeaking.value = false; _currentWordRange.value = null
                    withContext(Dispatchers.Main) { onChapterFinished() }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) Timber.e(e, "Playback error")
                _isSpeaking.value = false; _currentWordRange.value = null
            }
        }
    }

    private fun generateAudio(text: String): FloatArray? {
        val engine = tts ?: return null
        if (text.isBlank()) return null
        val normalized = normalizeForUtrobinTts(text)
        if (normalized.isBlank()) return null
        return try {
            val audio = engine.generate(
                text = normalized,
                sid = speakerId,
                speed = 1.0f / speechRate,
            )
            audio.samples
        } catch (e: Exception) {
            Timber.e(e, "UtrobinTTS failed: ${text.take(40)}")
            null
        }
    }

    private suspend fun trackWords(sentence: SentenceInfo, samples: Int) {
        val durationMs = (samples.toFloat() / sampleRate * 1000).toLong()
        val words = mutableListOf<IntRange>(); var i = 0; val t = sentence.text
        while (i < t.length) {
            while (i < t.length && t[i].isWhitespace()) i++
            if (i >= t.length) break; val s = i
            while (i < t.length && !t[i].isWhitespace()) i++
            words.add(IntRange(s, i))
        }
        if (words.isEmpty()) return
        val total = words.sumOf { it.last - it.first }
        for (w in words) {
            _currentWordRange.value = IntRange(sentence.startOffset + w.first, sentence.startOffset + w.last)
            delay((durationMs * (w.last - w.first) / total).coerceAtLeast(50))
        }
    }

    private fun playAudio(data: FloatArray) {
        val shorts = ShortArray(data.size) { i ->
            (data[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(shorts.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        audioTrack = track; track.write(shorts, 0, shorts.size); track.play()
        Thread.sleep((shorts.size * 1000L / sampleRate) + 100)
        try { track.stop(); track.release() } catch (_: Exception) {}
    }

    fun pause() { isPaused = true; playbackJob?.cancel(); try { audioTrack?.stop() } catch (_: Exception) {}; _isSpeaking.value = false; _currentWordRange.value = null }
    fun stop() { isPaused = false; playbackJob?.cancel(); playbackJob = null; try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}; audioTrack = null; sentences = emptyList(); currentSentenceIndex = 0; _isSpeaking.value = false; _currentWordRange.value = null }
    fun setSpeechRate(rate: Float) { speechRate = rate.coerceIn(0.5f, 2.0f) }
    fun shutdown() { stop(); tts?.release(); tts = null; _isReady.value = false }

    private fun splitIntoSentences(text: String): List<SentenceInfo> {
        val result = mutableListOf<SentenceInfo>(); val pattern = Regex("""[.!?]+[\s\n]+|[.!?]+$|\n\n+"""); var last = 0
        pattern.findAll(text).forEach { m -> val end = m.range.last + 1; val t = text.substring(last, end).trim(); if (t.isNotBlank()) result.add(SentenceInfo(t, last, end)); last = end }
        if (last < text.length) { val t = text.substring(last).trim(); if (t.isNotBlank()) result.add(SentenceInfo(t, last, text.length)) }
        if (result.isEmpty() && text.isNotBlank()) result.add(SentenceInfo(text.trim(), 0, text.length))
        return result
    }
}
