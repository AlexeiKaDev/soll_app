package com.soll.domain.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import kotlin.math.roundToInt
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Russian Utrobin / HF-style VITS: ONNX has [input_ids, attention_mask, speaker_id] (HuggingFace export).
 * Sherpa-ONNX [OfflineTts] targets a different graph; using it caused SIGSEGV in libonnxruntime.
 */
@Singleton
class UtrobinTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var ortSession: OrtSession? = null
    private var token2id: Map<Char, Int> = emptyMap()
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
    private var speakerId = 0
    private var sampleRate = 16000
    private var maxSpeakerIndex = 1
    private var maxTokenId = 42

    private val sessionLock = Any()
    private var cachedModelPath: String? = null
    private var ortIntraThreads: Int = 2
    @Volatile
    private var sessionStale: Boolean = false

    companion object {
        private const val ASSETS_DIR = "utrobin_tts"
        private const val FILES_DIR = "utrobin_tts_extracted"
        val SPEAKERS = listOf("Женский" to 0, "Мужской" to 1)

        private val VOCAB_CHARS: Set<Char> = buildSet {
            add(' ')
            add('_')
            add(':')
            add('+')
            add('.')
            add('!')
            add('?')
            add(',')
            for (c in 'а'..'я') add(c)
            add('ё')
        }
    }

    data class SentenceInfo(val text: String, val startOffset: Int, val endOffset: Int)

    fun isModelDownloaded(): Boolean = true

    fun setSpeaker(id: Int) { speakerId = id }

    fun getOrtIntraThreads(): Int = ortIntraThreads

    fun applyOrtIntraThreadsTunable(value: Float) {
        val v = value.roundToInt().coerceIn(1, 4)
        if (v == ortIntraThreads && !sessionStale) return
        ortIntraThreads = v
        if (ortSession != null) sessionStale = true
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
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
            copyTokensAsset("$ASSETS_DIR/tokens.txt", tokensFile)
            token2id = UtrobinCharTokenizer.loadTokenMap(tokensFile)
            if (token2id.isEmpty() || !token2id.containsKey(' ')) {
                Timber.e("Invalid tokens.txt (no space id)")
                _isReady.value = false
                return@withContext false
            }
            maxTokenId = token2id.values.maxOrNull() ?: 42

            cachedModelPath = modelFile.absolutePath
            buildOrtSession(modelFile.absolutePath)
            sampleRate = 16000
            maxSpeakerIndex = 1
            _isReady.value = true
            Timber.d("UtrobinTTS (HF ONNX Runtime) ready, sampleRate=$sampleRate, speakers=${maxSpeakerIndex + 1}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to init UtrobinTTS")
            _isReady.value = false
            false
        }
    }

    private fun buildOrtSession(modelPath: String) {
        synchronized(sessionLock) {
            ortSession?.close()
            val env = OrtEnvironment.getEnvironment()
            ortSession = OrtSession.SessionOptions().use { opts ->
                opts.setIntraOpNumThreads(ortIntraThreads)
                opts.setInterOpNumThreads(1)
                try {
                    opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                } catch (e: OrtException) {
                    Timber.w(e, "Utrobin: ALL_OPT failed, continuing with defaults")
                }
                env.createSession(modelPath, opts)
            }
            sessionStale = false
        }
    }

    private fun ensureOrtSessionFresh() {
        if (!sessionStale) return
        val path = cachedModelPath ?: return
        buildOrtSession(path)
    }

    private fun copyAsset(assetPath: String, target: File) {
        context.assets.open(assetPath).use { inp ->
            FileOutputStream(target).use { out -> inp.copyTo(out) }
        }
    }

    private fun copyTokensAsset(assetPath: String, target: File) {
        context.assets.open(assetPath).use { inp ->
            val text = inp.bufferedReader(StandardCharsets.UTF_8).readText()
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd()
            target.writeText(normalized, StandardCharsets.UTF_8)
        }
    }

    private fun normalizeForUtrobinTts(text: String): String {
        val lower = text.lowercase(java.util.Locale("ru", "RU"))
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            when {
                ch in VOCAB_CHARS -> sb.append(ch)
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(',')
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun clampedSpeakerId(): Int = speakerId.coerceIn(0, maxSpeakerIndex)

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stop()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0
        isPaused = false
        _isSpeaking.value = true

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
                    _isSpeaking.value = false
                    _currentWordRange.value = null
                    withContext(Dispatchers.Main) { onChapterFinished() }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) Timber.e(e, "Playback error")
                _isSpeaking.value = false
                _currentWordRange.value = null
            }
        }
    }

    private fun generateAudio(text: String): FloatArray? {
        ensureOrtSessionFresh()
        val session = ortSession ?: return null
        if (text.isBlank()) return null
        val normalized = normalizeForUtrobinTts(text)
        if (normalized.isBlank()) return null

        val ids = UtrobinCharTokenizer.textToFlatIds(normalized, token2id)
        if (ids.isEmpty()) {
            Timber.w("Utrobin: empty token sequence")
            return null
        }
        for (x in ids) {
            if (x < 0 || x > maxTokenId) {
                Timber.e("Utrobin: token id out of range: $x (max $maxTokenId)")
                return null
            }
        }

        val env = OrtEnvironment.getEnvironment()
        val seqLen = ids.size
        val sid = clampedSpeakerId().toLong()

        return try {
            OnnxTensor.createTensor(env, arrayOf(ids)).use { inputIds ->
                OnnxTensor.createTensor(env, Array(1) { LongArray(seqLen) { 1L } }).use { mask ->
                    OnnxTensor.createTensor(env, longArrayOf(sid)).use { spk ->
                        val feeds = mapOf(
                            "input_ids" to inputIds,
                            "attention_mask" to mask,
                            "speaker_id" to spk,
                        )
                        session.run(feeds).use { result ->
                            val tensor = waveformOutputTensor(result) ?: run {
                                Timber.e("Utrobin: no tensor output (keys: ${result.joinToString { it.key }})")
                                return@use null
                            }
                            val raw = waveformToFloatArray(tensor)
                            resampleForSpeechRate(raw)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "UtrobinTTS ONNX failed: ${text.take(40)}")
            null
        }
    }

    /** ORT Java API: [OrtSession.Result.get] by name returns Optional&lt;OnnxValue&gt;, not OnnxTensor. */
    private fun waveformOutputTensor(result: OrtSession.Result): OnnxTensor? {
        val byName = listOf("waveform", "audio", "output", "wav", "output_audio")
        for (name in byName) {
            val opt = result.get(name)
            if (!opt.isPresent) continue
            when (val v = opt.get()) {
                is OnnxTensor -> return v
                else -> Timber.w("Utrobin: output %s is %s", name, v.javaClass.simpleName)
            }
        }
        try {
            val v = result.get(0)
            if (v is OnnxTensor) return v
            Timber.w("Utrobin: output[0] is ${v.javaClass.simpleName}")
        } catch (_: IndexOutOfBoundsException) {
            // ignore
        }
        for (e in result) {
            val v = e.value
            if (v is OnnxTensor) return v
        }
        return null
    }

    private fun waveformToFloatArray(tensor: OnnxTensor): FloatArray {
        val o = tensor.value ?: return floatArrayOf()
        @Suppress("UNCHECKED_CAST")
        return when (o) {
            is FloatArray -> o
            is Array<*> -> (o[0] as FloatArray).copyOf()
            else -> (o as Array<FloatArray>)[0].copyOf()
        }
    }

    /** Cheap tempo: ONNX model has no speed input; approximate via decimation/interpolation. */
    private fun resampleForSpeechRate(samples: FloatArray): FloatArray {
        val rate = speechRate
        if (rate == 1.0f) return samples
        val factor = (1.0f / rate).coerceIn(0.5f, 2.0f)
        val newLen = (samples.size * factor).toInt().coerceAtLeast(1)
        if (newLen == samples.size) return samples
        val out = FloatArray(newLen)
        for (i in 0 until newLen) {
            val srcPos = (i / factor).toInt().coerceIn(0, samples.lastIndex)
            out[i] = samples[srcPos]
        }
        return out
    }

    private suspend fun trackWords(sentence: SentenceInfo, samples: Int) {
        val durationMs = (samples.toFloat() / sampleRate * 1000).toLong()
        val words = mutableListOf<IntRange>()
        var i = 0
        val t = sentence.text
        while (i < t.length) {
            while (i < t.length && t[i].isWhitespace()) i++
            if (i >= t.length) break
            val s = i
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
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(shorts.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC).build()
        audioTrack = track
        track.write(shorts, 0, shorts.size)
        track.play()
        Thread.sleep((shorts.size * 1000L / sampleRate) + 100)
        try {
            track.stop()
            track.release()
        } catch (_: Exception) {
        }
    }

    fun pause() {
        isPaused = true
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
        _currentWordRange.value = null
    }

    fun stop() {
        isPaused = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        sentences = emptyList()
        currentSentenceIndex = 0
        _isSpeaking.value = false
        _currentWordRange.value = null
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
    }

    fun shutdown() {
        stop()
        synchronized(sessionLock) {
            try {
                ortSession?.close()
            } catch (_: Exception) {
            }
            ortSession = null
        }
        cachedModelPath = null
        sessionStale = false
        token2id = emptyMap()
        maxSpeakerIndex = 1
        _isReady.value = false
    }

    private fun splitIntoSentences(text: String): List<SentenceInfo> {
        val result = mutableListOf<SentenceInfo>()
        val pattern = Regex("""[.!?]+[\s\n]+|[.!?]+$|\n\n+""")
        var last = 0
        pattern.findAll(text).forEach { m ->
            val end = m.range.last + 1
            val t = text.substring(last, end).trim()
            if (t.isNotBlank()) result.add(SentenceInfo(t, last, end))
            last = end
        }
        if (last < text.length) {
            val t = text.substring(last).trim()
            if (t.isNotBlank()) result.add(SentenceInfo(t, last, text.length))
        }
        if (result.isEmpty() && text.isNotBlank()) result.add(SentenceInfo(text.trim(), 0, text.length))
        return result
    }
}
