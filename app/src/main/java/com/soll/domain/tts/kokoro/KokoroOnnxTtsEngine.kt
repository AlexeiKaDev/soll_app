package com.soll.domain.tts.kokoro

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.onnx.InstalledOnnxPack
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
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * ONNX Kokoro v1 ([onnx-community/Kokoro-82M-v1.0-ONNX](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX)):
 * входы input_ids, style, speed; 24 kHz.
 *
 * Нужны файлы в корне пака: **config.json** из hexgrad/Kokoro-82M (vocab), **onnx/model*.onnx**, подкаталог **voices** с .bin по одному на голос.
 */
@Singleton
class KokoroOnnxTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val env get() = OrtEnvironment.getEnvironment()

    private var ortSession: OrtSession? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    private val sessionLock = Any()
    private var cachedOnnxPath: String? = null
    private var packRootDir: File? = null
    private var vocabCharToId: Map<String, Int> = emptyMap()
    private var voiceRows: Array<FloatArray> = emptyArray()
    private var voiceId: String = "af_bella"
    private var ortIntraThreads: Int = 2

    private val lexicon by lazy { KokoroCmuG2p.loadMiniCmudict(context) }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private var speechRate = 1f
    private var isPaused = false
    private var currentSentenceIndex = 0
    private var sentences: List<SentenceInfo> = emptyList()
    private var chapterFinishedCallback: (() -> Unit)? = null
    private var playbackSessionId: Long = 0L

    private var mergeShortThreshold = 220
    private var mergeTotalCap = 360
    private var sampleRate = 24_000

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        val (a, b) = TtsBookPerformanceProfile.chunkMergeLimits(profile)
        mergeShortThreshold = a
        mergeTotalCap = b
        ortIntraThreads = TtsBookPerformanceProfile.ortIntraThreads(profile)
            .coerceIn(1, 4)
        synchronized(sessionLock) {
            if (ortSession != null) {
                rebuildSession()
            }
        }
    }

    fun getOrtIntraThreads(): Int = ortIntraThreads

    fun applyOrtIntraThreadsTunable(value: Float) {
        val v = value.roundToInt().coerceIn(1, 4)
        if (v == ortIntraThreads) return
        ortIntraThreads = v
        synchronized(sessionLock) {
            if (ortSession != null) rebuildSession()
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
    }

    fun setVoice(voice: String) {
        if (voice.isBlank()) return
        voiceId = voice.trim()
        val root = packRootDir ?: return
        val voicesDir = voicesDirForRoot(root)
        val voices = loadVoiceTensor(voicesDir, voiceId)
        if (voices.isEmpty()) {
            Timber.w("Kokoro: voice ${voiceId}.bin missing under ${voicesDir.absolutePath}")
            return
        }
        voiceRows = voices
    }

    /** Имена *.bin в каталоге voices пака (без расширения). */
    fun listVoiceIdsForPackRoot(root: File): List<String> =
        orderedVoiceBinIds(root).second

    suspend fun prepareWithPack(
        pack: InstalledOnnxPack,
        preferredVoice: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        shutdownOnnxOnly()
        val root = File(pack.rootDir)
        packRootDir = root
        if (!root.isDirectory) {
            Timber.e("Kokoro: invalid pack root $root")
            _isReady.value = false
            return@withContext false
        }
        val onnxFile = resolveOnnxModel(root, pack.precision) ?: run {
            Timber.e("Kokoro: ONNX model not found under ${root.absolutePath} precision=${pack.precision}")
            _isReady.value = false
            return@withContext false
        }
        val cfgFile = File(root, "config.json")
        if (!cfgFile.isFile) {
            Timber.e("Kokoro: missing config.json (download hexgrad/Kokoro-82M/config.json into pack)")
            _isReady.value = false
            return@withContext false
        }
        val vocab = loadVocab(cfgFile)
        if (vocab.isEmpty()) {
            Timber.e("Kokoro: empty vocab in config.json")
            _isReady.value = false
            return@withContext false
        }
        vocabCharToId = vocab
        val (voicesDir, orderedVoiceIds) = orderedVoiceBinIds(root)
        val manifestVoice = pack.kokoroVoice?.takeIf { it.isNotBlank() }
        voiceId = when {
            !preferredVoice.isNullOrBlank() &&
                preferredVoice.trim() in orderedVoiceIds ->
                preferredVoice.trim()
            !manifestVoice.isNullOrBlank() && manifestVoice.trim() in orderedVoiceIds ->
                manifestVoice.trim()
            orderedVoiceIds.isNotEmpty() -> orderedVoiceIds.first()
            else -> preferredVoice?.trim()?.takeIf { it.isNotEmpty() }
                ?: manifestVoice?.trim()?.takeIf { it.isNotEmpty() }
                ?: voiceId
        }
        if (orderedVoiceIds.isEmpty()) {
            val suspiciousSmall = voicesDir.takeIf { it.isDirectory }?.listFiles()?.filter { f ->
                f.isFile && f.extension.equals("bin", true) && f.length() < MIN_KOKORO_VOICE_FILE_BYTES
            }.orEmpty()
            Timber.e(
                "Kokoro: нет настоящих voices/*.bin (≥${MIN_KOKORO_VOICE_FILE_BYTES} B) в ${root.absolutePath}. " +
                    "На HF голоса в Git LFS: при «git clone» без LFS или кривом копировании остаются крошечные " +
                    "файлы-указатели, не звук. Перекачай: python tools/tts/prepare_onnx_pack.py --model kokoro_82m " +
                    "--precision fp32 --download или huggingface-cli с LFS. Мелких .bin: ${suspiciousSmall.size}; " +
                    "корень пака: " + root.listFiles()?.joinToString { it.name },
            )
        }
        val voices = loadVoiceTensor(voicesDir, voiceId)
        if (voices.isEmpty()) {
            Timber.e(
                "Kokoro: нет файла голоса ${voicesDir.absolutePath}${File.separator}$voiceId.bin; " +
                    "доступные: $orderedVoiceIds"
            )
            _isReady.value = false
            return@withContext false
        }
        voiceRows = voices
        synchronized(sessionLock) {
            cachedOnnxPath = onnxFile.absolutePath
            rebuildSession()
        }
        Timber.i(
            "Kokoro ONNX ready model=${onnxFile.name} voices=${voiceRows.size} rows vocab=${vocabCharToId.size}",
        )
        _isReady.value = true
        true
    }

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stopPlaybackJobsOnly()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0
        isPaused = false
        chapterFinishedCallback = onChapterFinished
        playbackSessionId++
        resumeInternal(this)
    }

    suspend fun resume() = coroutineScope {
        resumeInternal(this)
    }

    private suspend fun resumeInternal(scope: kotlinx.coroutines.CoroutineScope) {
        if (sentences.isEmpty()) return
        if (currentSentenceIndex >= sentences.size) return
        if (!isPaused && playbackJob?.isActive == true) return
        if (!_isReady.value || ortSession == null) {
            Timber.w("Kokoro: resume called while not ready")
            return
        }
        isPaused = false
        val sessionId = playbackSessionId
        _isSpeaking.value = true

        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                while (
                    currentSentenceIndex < sentences.size &&
                    isActive &&
                    !isPaused &&
                    sessionId == playbackSessionId
                ) {
                    val s = sentences[currentSentenceIndex]
                    _currentWordRange.value = IntRange(s.startOffset, s.endOffset)
                    val phonemeLine = KokoroCmuG2p.englishTextToPhonemeLine(s.text, lexicon)
                    if (phonemeLine == null) {
                        Timber.w("Kokoro: skip sentence (non-English/Cyrillic or empty): ${s.text.take(80)}")
                        if (!isPaused) currentSentenceIndex++
                        continue
                    }
                    val innerIds = phonemeStringToIds(phonemeLine)
                    if (innerIds.isEmpty()) {
                        if (!isPaused) currentSentenceIndex++
                        continue
                    }
                    if (innerIds.size > MAX_PHONEME_INNER) {
                        Timber.w("Kokoro: phoneme chunk too long (${innerIds.size}), truncating")
                    }
                    val clippedInner = innerIds.take(MAX_PHONEME_INNER)
                    val audio = generateAudio(clippedInner)
                    if (audio != null && audio.size > 64 && isActive && !isPaused) {
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
                if (e !is kotlinx.coroutines.CancellationException) Timber.e(e, "Kokoro playback error")
                _isSpeaking.value = false
                _currentWordRange.value = null
            }
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

    fun shutdownOnnxOnly() {
        stopPlaybackJobsOnly()
        synchronized(sessionLock) {
            try {
                ortSession?.close()
            } catch (_: Exception) {
            }
            ortSession = null
            cachedOnnxPath = null
        }
        voiceRows = emptyArray()
        vocabCharToId = emptyMap()
        packRootDir = null
        _isReady.value = false
    }

    fun shutdown() {
        stop()
        shutdownOnnxOnly()
    }

    private fun stopPlaybackJobsOnly() {
        playbackSessionId++
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        _isSpeaking.value = false
        _currentWordRange.value = null
    }

    private fun rebuildSession() {
        val path = cachedOnnxPath ?: return
        ortSession?.close()
        ortSession = OrtSession.SessionOptions().use { opts ->
            opts.setIntraOpNumThreads(ortIntraThreads)
            opts.setInterOpNumThreads(1)
            try {
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            } catch (e: OrtException) {
                Timber.w(e, "Kokoro: ALL_OPT failed")
            }
            env.createSession(path, opts)
        }
    }

    private fun phonemeStringToIds(phonemes: String): List<Long> {
        val out = ArrayList<Long>(phonemes.length)
        var i = 0
        while (i < phonemes.length) {
            val cp = phonemes.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val id = vocabCharToId[ch]
            if (id != null) {
                out.add(id.toLong())
            } else {
                Timber.w("Kokoro: phoneme char not in vocab: U+%04X (%s)", cp, ch)
            }
            i += Character.charCount(cp)
        }
        return out
    }

    private fun generateAudio(innerTokenIds: List<Long>): FloatArray? {
        if (innerTokenIds.isEmpty()) return null
        val seqLen = innerTokenIds.size + 2
        val padded = LongArray(seqLen)
        padded[0] = 0L
        for (k in innerTokenIds.indices) {
            padded[k + 1] = innerTokenIds[k]
        }
        padded[padded.lastIndex] = 0L

        val rowIdx = innerTokenIds.size.coerceIn(0, voiceRows.lastIndex.coerceAtLeast(0))
        val style = voiceRows.getOrNull(rowIdx) ?: return null

        val session = ortSession ?: return null

        return try {
            OnnxTensor.createTensor(env, arrayOf(padded)).use { idsTensor ->
                OnnxTensor.createTensor(env, arrayOf(style)).use { styleTensor ->
                    OnnxTensor.createTensor(env, floatArrayOf(speechRate.coerceIn(0.5f, 2f))).use { spd ->
                        val feeds = linkedMapOf<String, OnnxTensor>()
                        mapFeeds(session, feeds, idsTensor, styleTensor, spd)
                        session.run(feeds).use { result ->
                            waveformToFloatArray(firstTensor(result))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Kokoro ONNX run failed")
            null
        }
    }

    private fun mapFeeds(
        session: OrtSession,
        feeds: LinkedHashMap<String, OnnxTensor>,
        inputIds: OnnxTensor,
        style: OnnxTensor,
        speed: OnnxTensor,
    ) {
        val names = session.inputInfo.keys.toList()
        val byLower = names.associateBy { it.lowercase(LocaleRoot) }
        fun putAlias(vararg aliases: String, t: OnnxTensor) {
            val key = aliases.firstNotNullOfOrNull { byLower[it.lowercase(LocaleRoot)] } ?: return
            feeds[key] = t
        }
        putAlias("input_ids", "ids", t = inputIds)
        putAlias("style", "ref_s", "spk", t = style)
        putAlias("speed", "speech_speed", t = speed)
        if (feeds.size < names.size) {
            Timber.w("Kokoro feeds incomplete; inputs=$names feeds=${feeds.keys}")
            var idx = 0
            val tensors = listOf(inputIds, style, speed)
            for (n in names) {
                if (!feeds.containsKey(n) && idx < tensors.size) {
                    feeds[n] = tensors[idx++]
                }
            }
        }
    }

    private fun firstTensor(result: OrtSession.Result): OnnxTensor? {
        val names = listOf("audio", "waveform", "output", "wav")
        for (n in names) {
            val opt = result.get(n)
            if (opt.isPresent) {
                val v = opt.get()
                if (v is OnnxTensor) return v
            }
        }
        return try {
            val v = result.get(0)
            if (v is OnnxTensor) v else null
        } catch (_: Exception) {
            null
        } ?: run {
            for (e in result) {
                val v = e.value
                if (v is OnnxTensor) return v
            }
            null
        }
    }

    private fun waveformToFloatArray(tensor: OnnxTensor?): FloatArray {
        if (tensor == null) return floatArrayOf()
        val o = tensor.value ?: return floatArrayOf()
        return when (o) {
            is FloatArray -> o
            is Array<*> -> {
                val row = o[0]
                when (row) {
                    is FloatArray -> row
                    is Array<*> -> (row[0] as? FloatArray)?.copyOf() ?: floatArrayOf()
                    else -> floatArrayOf()
                }
            }
            else -> floatArrayOf()
        }
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
            _currentWordRange.value =
                IntRange(sentence.startOffset + w.first, sentence.startOffset + w.last)
            delay((durationMs * (w.last - w.first) / total).coerceAtLeast(50))
        }
    }

    private fun playAudio(data: FloatArray) {
        val shorts = ShortArray(data.size) { i ->
            (data[i] * Short.MAX_VALUE).toInt().coerceIn(
                Short.MIN_VALUE.toInt(),
                Short.MAX_VALUE.toInt(),
            ).toShort()
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

    private fun mergeNearbySentences(sentences: List<SentenceInfo>): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        for (i in 1 until sentences.size) {
            val next = sentences[i]
            val shouldMerge = current.text.length < mergeShortThreshold &&
                next.text.length < mergeShortThreshold &&
                (current.text.length + next.text.length) < mergeTotalCap
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

    data class SentenceInfo(val text: String, val startOffset: Int, val endOffset: Int)

    companion object {
        private val LocaleRoot = java.util.Locale.ROOT
        private const val VOICES_SUBDIR = "voices"
        private const val MAX_PHONEME_INNER = 508
        private const val STYLE_DIM = 256
        /** Реальные style bins Kokoro на HF ~500 KiB; меньше — обычно Git LFS pointer. */
        private const val MIN_KOKORO_VOICE_FILE_BYTES = 8192L

        /** onnx-community Kokoro держит голос в `voices/`; допускаем `Voices/` и вложенный поиск. */
        private fun voicesDirForRoot(root: File): File {
            val lower = File(root, VOICES_SUBDIR)
            if (lower.isDirectory) return lower
            val cap = File(root, "Voices")
            if (cap.isDirectory) return cap
            val nested = root.walkTopDown().maxDepth(6).firstOrNull { f ->
                f.isDirectory && f.name.equals(VOICES_SUBDIR, ignoreCase = true)
            }
            if (nested != null) return nested
            return lower
        }

        internal fun orderedVoiceBinIds(root: File): Pair<File, List<String>> {
            val candidates = buildList<File> {
                add(voicesDirForRoot(root))
                root.parentFile?.let {
                    add(File(it, VOICES_SUBDIR))
                    add(File(it, "Voices"))
                }
            }.distinct()

            fun listBinIds(dir: File): List<String> {
                if (!dir.isDirectory) return emptyList()
                return dir.listFiles()
                    ?.filter { f ->
                        f.isFile &&
                            f.extension.lowercase(LocaleRoot) == "bin" &&
                            f.length() >= MIN_KOKORO_VOICE_FILE_BYTES
                    }
                    ?.map { it.nameWithoutExtension }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
            }
            for (dir in candidates) {
                val ids = listBinIds(dir)
                if (ids.isNotEmpty()) return dir to ids
            }
            val fallbackDir = voicesDirForRoot(root)
            return fallbackDir to emptyList()
        }

        private fun loadVocab(configFile: File): Map<String, Int> {
            val json = JSONObject(configFile.readText(Charsets.UTF_8))
            val vocabObj = json.optJSONObject("vocab") ?: return emptyMap()
            val map = LinkedHashMap<String, Int>()
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = vocabObj.getInt(k)
            }
            return map
        }

        internal fun loadVoiceTensor(voicesDir: File, voiceId: String): Array<FloatArray> {
            val f = File(voicesDir, "$voiceId.bin")
            if (!f.isFile) return emptyArray()
            val bytes = f.readBytes()
            val byteBuf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val floatCount = bytes.size / 4
            val floats = FloatArray(floatCount)
            for (i in 0 until floatCount) {
                floats[i] = byteBuf.float
            }
            if (floats.size % STYLE_DIM != 0) {
                Timber.e("Kokoro: corrupt voice bin ${floats.size} floats not divisible by $STYLE_DIM")
                return emptyArray()
            }
            val rows = floats.size / STYLE_DIM
            return Array(rows) { r ->
                FloatArray(STYLE_DIM) { c -> floats[r * STYLE_DIM + c] }
            }
        }

        private fun resolveOnnxModel(root: File, precision: String): File? {
            val preferred = when (precision.lowercase(LocaleRoot)) {
                "fp16" -> listOf(
                    "onnx/model_fp16.onnx",
                    "model_fp16.onnx",
                )
                "int4", "q4", "quantized" -> listOf(
                    "onnx/model_quantized.onnx",
                    "onnx/model_q4f16.onnx",
                    "onnx/model_q4.onnx",
                    "model_quantized.onnx",
                )
                else -> listOf(
                    "onnx/model.onnx",
                    "model.onnx",
                )
            }
            for (rel in preferred) {
                val f = File(root, rel)
                if (f.isFile && f.length() > 1024L) return f
            }
            return root.walkTopDown().maxDepth(4).firstOrNull {
                it.isFile && it.extension.equals("onnx", ignoreCase = true) &&
                    it.name.startsWith("model", ignoreCase = true)
            }
        }
    }
}
