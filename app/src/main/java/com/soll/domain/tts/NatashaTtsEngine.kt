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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Offline Natasha VITS2 ONNX engine.
 * Model must be bundled inside APK assets and extracted on first run.
 */
@Singleton
class NatashaTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ortSession: OrtSession? = null
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
    private var chapterFinishedCallback: (() -> Unit)? = null
    @Volatile
    private var playbackSessionId: Long = 0L
    private var speechRate = 1.0f
    private var sampleRate = 22050

    private var mergeShortThreshold = 220
    private var mergeTotalCap = 360

    private val sessionLock = Any()
    private var cachedModelPath: String? = null
    private var ortIntraThreads: Int = 2
    @Volatile
    private var sessionStale: Boolean = false

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        val (a, b) = TtsBookPerformanceProfile.chunkMergeLimits(profile)
        mergeShortThreshold = a
        mergeTotalCap = b
        applyOrtIntraThreadsTunable(TtsBookPerformanceProfile.ortIntraThreads(profile).toFloat())
    }

    fun getOrtIntraThreads(): Int = ortIntraThreads

    fun applyOrtIntraThreadsTunable(value: Float) {
        val v = value.roundToInt().coerceIn(1, 4)
        if (v == ortIntraThreads && !sessionStale) return
        ortIntraThreads = v
        if (ortSession != null) sessionStale = true
    }

    companion object {
        private const val ASSETS_DIR = "natasha_vits2"
        private const val FILES_DIR = "natasha_vits2_extracted"
        private const val MODEL_NAME = "model.onnx"
        private val ASSET_MODEL_CANDIDATES = listOf(
            "model.onnx",
            "natasha.onnx",
        )
        private const val MIN_MODEL_BYTES = 10_000_000L

        /** (noise_scale, length_scale, noise_scale_w) to speaker id — try in order on failure. */
        private val SCALES_SID_FALLBACKS: List<Pair<FloatArray, Long>> = listOf(
            floatArrayOf(0.667f, 1f, 0.8f) to 0L,
            floatArrayOf(1f, 1f, 1f) to 0L,
            floatArrayOf(0.8f, 1f, 0.8f) to 0L,
            floatArrayOf(0.667f, 1f, 0.8f) to 1L,
        )
    }

    data class SentenceInfo(val text: String, val startOffset: Int, val endOffset: Int)

    fun isModelDownloaded(): Boolean {
        val f = File(File(context.filesDir, FILES_DIR), MODEL_NAME)
        return f.exists() && f.length() >= MIN_MODEL_BYTES
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, FILES_DIR)
            dir.mkdirs()
            val modelFile = File(dir, MODEL_NAME)
            if (!modelFile.exists() || modelFile.length() < MIN_MODEL_BYTES) {
                Timber.d("Extracting Natasha VITS2 from assets...")
                _downloadProgress.value = 0.1f
                val sourceAsset = ASSET_MODEL_CANDIDATES
                    .map { "$ASSETS_DIR/$it" }
                    .firstOrNull { assetExists(it) }
                    ?: throw IllegalStateException(
                        "No Natasha model in assets/$ASSETS_DIR. Expected one of: $ASSET_MODEL_CANDIDATES. " +
                            "Found: ${listAssetsSafe(ASSETS_DIR)}",
                    )
                copyAsset(sourceAsset, modelFile)
                _downloadProgress.value = null
                Timber.d("Extracted Natasha model from $sourceAsset: ${modelFile.length() / 1024 / 1024}MB")
            }
            cachedModelPath = modelFile.absolutePath
            buildOrtSession(modelFile.absolutePath)
            sampleRate = 22050
            logModelProfile()
            _isReady.value = true
            Timber.d("Natasha VITS2 ready, sampleRate=$sampleRate, ortThreads=$ortIntraThreads")
            true
        } catch (e: Exception) {
            Timber.e(
                e,
                "Natasha init failed. Place model at app/src/main/assets/$ASSETS_DIR/$MODEL_NAME",
            )
            _isReady.value = false
            false
        }
    }

    private fun logModelProfile() {
        val s = ortSession ?: return
        try {
            Timber.d("Natasha model inputs: ${s.inputInfo.keys.joinToString()}")
            Timber.d("Natasha model outputs: ${s.outputInfo.keys.joinToString()}")
        } catch (e: Exception) {
            Timber.w(e, "Natasha: could not log model profile")
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
                    Timber.w(e, "Natasha: ALL_OPT failed")
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

    private fun assetExists(assetPath: String): Boolean = try {
        context.assets.open(assetPath).close()
        true
    } catch (_: Exception) {
        false
    }

    private fun listAssetsSafe(path: String): List<String> = try {
        context.assets.list(path)?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stop()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0
        isPaused = false
        chapterFinishedCallback = onChapterFinished
        playbackSessionId++
        resume()
    }

    suspend fun resume() = coroutineScope {
        if (sentences.isEmpty()) return@coroutineScope
        if (currentSentenceIndex >= sentences.size) return@coroutineScope
        if (!isPaused && playbackJob?.isActive == true) return@coroutineScope
        isPaused = false
        val sessionId = playbackSessionId
        _isSpeaking.value = true

        playbackJob = launch(Dispatchers.IO) {
            try {
                while (currentSentenceIndex < sentences.size && isActive && !isPaused && sessionId == playbackSessionId) {
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
                if (currentSentenceIndex >= sentences.size && !isPaused && sessionId == playbackSessionId) {
                    _isSpeaking.value = false
                    _currentWordRange.value = null
                    chapterFinishedCallback?.let { cb ->
                        withContext(Dispatchers.Main) { cb() }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) Timber.e(e, "Natasha playback error")
                _isSpeaking.value = false
                _currentWordRange.value = null
            }
        }
    }

    private fun generateAudio(text: String): FloatArray? {
        if (text.isBlank()) return null
        val normalized = NatashaSymbolTokenizer.normalizeLight(text)
        if (normalized.isBlank()) return null
        val ids = NatashaSymbolTokenizer.textToIds(normalized)
        if (ids.isEmpty()) {
            Timber.w("Natasha: empty token sequence")
            return null
        }
        val seqLen = ids.size
        val env = OrtEnvironment.getEnvironment()

        return synchronized(sessionLock) natashaInfer@{
            ensureOrtSessionFresh()
            val session = ortSession ?: return@natashaInfer null

            var lastError: Exception? = null
            for ((scales, sid) in SCALES_SID_FALLBACKS) {
                try {
                    OnnxTensor.createTensor(env, arrayOf(ids)).use { inputT ->
                        OnnxTensor.createTensor(env, longArrayOf(seqLen.toLong())).use { lenT ->
                            OnnxTensor.createTensor(env, scales).use { scalesT ->
                                OnnxTensor.createTensor(env, longArrayOf(sid)).use { sidT ->
                                    val feeds = buildFeedsForCurrentModel(
                                        session = session,
                                        inputTensor = inputT,
                                        lengthTensor = lenT,
                                        scalesTensor = scalesT,
                                        sidTensor = sidT,
                                    )
                                    session.run(feeds).use runOut@{ result ->
                                        val tensor = waveformOutputTensor(result) ?: run {
                                            Timber.e(
                                                "Natasha: no output (${result.joinToString { it.key }})",
                                            )
                                            return@runOut null
                                        }
                                        val raw = waveformToFloatArray(tensor)
                                        if (raw.isEmpty()) return@runOut null
                                        return@natashaInfer resampleForSpeechRate(raw)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    lastError = e
                    Timber.w(
                        e,
                        "Natasha: inference retry (scales=${scales.contentToString()}, sid=$sid) " +
                            text.take(24),
                    )
                }
            }
            Timber.e(lastError, "Natasha ONNX failed after fallbacks: ${text.take(40)}")
            null
        }
    }

    private fun buildFeedsForCurrentModel(
        session: OrtSession,
        inputTensor: OnnxTensor,
        lengthTensor: OnnxTensor,
        scalesTensor: OnnxTensor,
        sidTensor: OnnxTensor,
    ): Map<String, OnnxTensor> {
        val inputNames = session.inputInfo.keys.toList()
        val byLower = inputNames.associateBy { it.lowercase() }
        val feeds = linkedMapOf<String, OnnxTensor>()

        fun putIfPresent(vararg aliases: String, tensor: OnnxTensor) {
            val name = aliases.firstNotNullOfOrNull { byLower[it] } ?: return
            feeds[name] = tensor
        }

        putIfPresent("input", "input_ids", "x", "text", tensor = inputTensor)
        putIfPresent("input_lengths", "lengths", "x_lengths", "text_lengths", tensor = lengthTensor)
        putIfPresent("scales", "scale", tensor = scalesTensor)
        putIfPresent("sid", "speaker_id", "speaker", tensor = sidTensor)

        if (feeds.isEmpty() && inputNames.isNotEmpty()) {
            feeds[inputNames[0]] = inputTensor
            if (inputNames.size >= 2) feeds[inputNames[1]] = lengthTensor
            if (inputNames.size >= 3) feeds[inputNames[2]] = scalesTensor
            if (inputNames.size >= 4) feeds[inputNames[3]] = sidTensor
        } else {
            val fallback = listOf(inputTensor, lengthTensor, scalesTensor, sidTensor)
            var idx = 0
            for (name in inputNames) {
                if (!feeds.containsKey(name) && idx < fallback.size) {
                    feeds[name] = fallback[idx++]
                }
            }
        }
        if (feeds.size != inputNames.size) {
            Timber.w(
                "Natasha: mapped ${feeds.size} feeds for ${inputNames.size} inputs; names=$inputNames",
            )
        }
        return feeds
    }

    private fun waveformOutputTensor(result: OrtSession.Result): OnnxTensor? {
        val byName = listOf("output", "waveform", "audio", "wav")
        for (name in byName) {
            val opt = result.get(name)
            if (!opt.isPresent) continue
            when (val v = opt.get()) {
                is OnnxTensor -> return v
                else -> Timber.w("Natasha: %s is %s", name, v.javaClass.simpleName)
            }
        }
        try {
            val v = result.get(0)
            if (v is OnnxTensor) return v
        } catch (_: IndexOutOfBoundsException) {
        }
        for (e in result) {
            val v = e.value
            if (v is OnnxTensor) return v
        }
        return null
    }

    private fun waveformToFloatArray(tensor: OnnxTensor): FloatArray {
        val o = tensor.value ?: return floatArrayOf()
        return when (o) {
            is FloatArray -> o
            is Array<*> -> {
                val a = o[0]
                when (a) {
                    is FloatArray -> a
                    is Array<*> -> (a[0] as? FloatArray)?.copyOf() ?: floatArrayOf()
                    else -> floatArrayOf()
                }
            }
            else -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    (o as Array<FloatArray>)[0].copyOf()
                } catch (_: Exception) {
                    floatArrayOf()
                }
            }
        }
    }

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
        val total = words.sumOf { it.last - it.first }.coerceAtLeast(1)
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
        val totalMs = (shorts.size * 1000L / sampleRate).coerceAtLeast(1L)
        var elapsed = 0L
        while (elapsed < totalMs && !isPaused && (playbackJob?.isActive != false)) {
            val step = minOf(20L, totalMs - elapsed)
            Thread.sleep(step)
            elapsed += step
        }
        try {
            track.stop()
            track.release()
        } catch (_: Exception) {
        }
    }

    fun pause() {
        playbackSessionId++
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
        playbackSessionId++
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
        chapterFinishedCallback = null
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
        return mergeNearbySentences(result)
    }

    /** Merge short neighbour sentences to reduce synthesis gaps between chunks. */
    private fun mergeNearbySentences(sentences: List<SentenceInfo>): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        val maxShort = mergeShortThreshold
        val maxTotal = mergeTotalCap
        for (i in 1 until sentences.size) {
            val next = sentences[i]
            val shouldMerge = current.text.length < maxShort && next.text.length < maxShort &&
                (current.text.length + next.text.length) < maxTotal
            current = if (shouldMerge) {
                SentenceInfo(
                    text = "${current.text} ${next.text}".trim(),
                    startOffset = current.startOffset,
                    endOffset = next.endOffset,
                )
            } else {
                merged.add(current)
                next
            }
        }
        merged.add(current)
        return merged
    }
}
