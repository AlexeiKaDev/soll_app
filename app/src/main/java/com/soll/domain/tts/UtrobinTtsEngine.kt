package com.soll.domain.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.soll.domain.tts.book.TtsPrepareResult
import com.soll.domain.tts.catalog.TtsPackLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import java.util.Locale

/**
 * Russian Utrobin / HF-style VITS: ONNX often has [input_ids, attention_mask, speaker_id] (HuggingFace export).
 * Sherpa-ONNX [OfflineTts] targets a different graph; using it caused SIGSEGV in libonnxruntime.
 */
@Singleton
class UtrobinTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLibrary: TtsPackLibrary,
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

    private val _diagnostics = MutableStateFlow(UtrobinPlaybackDiagnostics())
    val diagnostics: StateFlow<UtrobinPlaybackDiagnostics> = _diagnostics.asStateFlow()

    private val _playbackFailures = MutableSharedFlow<UtrobinPlaybackFailure>(extraBufferCapacity = 2)
    val playbackFailures: SharedFlow<UtrobinPlaybackFailure> = _playbackFailures.asSharedFlow()

    private var isPaused = false
    private var currentSentenceIndex = 0
    private var sentences: List<SentenceInfo> = emptyList()
    private var chapterFinishedCallback: (() -> Unit)? = null
    @Volatile
    private var playbackSessionId: Long = 0L
    private var speechRate = 1.0f
    private var speakerId = 0
    private var sampleRate = 16000
    private var maxSpeakerIndex = 1
    private var maxTokenId = 42

    private var mergeShortThreshold = 220
    private var mergeTotalCap = 360

    private val sessionLock = Any()
    private var cachedModelPath: String? = null
    private var ortIntraThreads: Int = 2
    private var selectedPackId: String? = null
    @Volatile
    private var sessionStale: Boolean = false

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        val (a, b) = TtsBookPerformanceProfile.chunkMergeLimits(profile)
        mergeShortThreshold = a
        mergeTotalCap = b
        val threads = TtsBookPerformanceProfile.ortIntraThreads(profile)
        applyOrtIntraThreadsTunable(threads.toFloat())
    }

    companion object {
        private const val MIN_MODEL_BYTES = 10_000_000L
        val SPEAKERS = listOf("Женский" to 0, "Мужской" to 1)
        private const val CHUNK_PREVIEW_LIMIT = 72

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

    data class SentenceInfo(
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
        val splitDepth: Int = 0,
        val sourceTag: String = "sentence",
    ) {
        fun range(): IntRange = IntRange(startOffset, endOffset)
    }

    fun isModelDownloaded(): Boolean {
        return packLibrary.findBestPack(TtsEngineType.UTROBIN)?.isRunnable == true
    }

    fun setSelectedPackId(packId: String?) {
        selectedPackId = packId
        _diagnostics.value = diagnostics.value.copy(packId = packId)
    }

    fun setSpeaker(id: Int) {
        speakerId = id
        _diagnostics.value = diagnostics.value.copy(
            speakerId = clampedSpeakerId(),
            speakerLabel = SPEAKERS.firstOrNull { it.second == clampedSpeakerId() }?.first,
        )
    }

    fun getOrtIntraThreads(): Int = ortIntraThreads

    fun applyOrtIntraThreadsTunable(value: Float) {
        val v = value.roundToInt().coerceIn(1, 4)
        if (v == ortIntraThreads && !sessionStale) return
        ortIntraThreads = v
        _diagnostics.value = diagnostics.value.copy(ortThreads = ortIntraThreads)
        if (ortSession != null) sessionStale = true
    }

    suspend fun initialize(): TtsPrepareResult = withContext(Dispatchers.IO) {
        try {
            val pack = selectedPackId?.let(packLibrary::findPackById)
                ?.takeIf { it.engineFamily == com.soll.domain.tts.catalog.TtsPackEngineFamily.UTROBIN }
                ?: packLibrary.findBestPack(TtsEngineType.UTROBIN)
                ?: return@withContext failedPrepare("Не найден pack Utrobin в локальной папке tts")
            val dir = File(pack.rootDir)
            val modelFile = File(dir, "model.onnx")
            val tokensFile = File(dir, "tokens.txt")
            if (!modelFile.exists()) {
                return@withContext failedPrepare(
                    message = "В pack Utrobin нет model.onnx",
                    path = dir.absolutePath,
                )
            }
            if (modelFile.length() < MIN_MODEL_BYTES) {
                return@withContext failedPrepare(
                    message = "Файл Utrobin слишком маленький: ${modelFile.length()} bytes",
                    path = modelFile.absolutePath,
                )
            }
            if (!tokensFile.exists()) {
                return@withContext failedPrepare(
                    message = "В pack Utrobin нет tokens.txt. Нужен экспортированный словарь токенов.",
                    path = dir.absolutePath,
                )
            }
            token2id = UtrobinCharTokenizer.loadTokenMap(tokensFile)
            if (token2id.isEmpty() || !token2id.containsKey(' ')) {
                return@withContext failedPrepare(
                    message = "tokens.txt не распознан: нет корректного id для пробела",
                    path = tokensFile.absolutePath,
                )
            }
            maxTokenId = token2id.values.maxOrNull() ?: 42

            cachedModelPath = modelFile.absolutePath
            buildOrtSession(modelFile.absolutePath)
            logSessionIoSummary()
            sampleRate = readSampleRateFromConfig(dir) ?: 16000
            maxSpeakerIndex = 1
            resetDiagnosticsForPack(pack.packId)
            _isReady.value = true
            Timber.d("UtrobinTTS (HF ONNX Runtime) ready, sampleRate=$sampleRate, speakers=${maxSpeakerIndex + 1}")
            TtsPrepareResult(
                success = true,
                engineType = TtsEngineType.UTROBIN,
                resolvedPackPath = dir.absolutePath,
                resolvedVoiceId = speakerId.toString(),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to init UtrobinTTS")
            _isReady.value = false
            TtsPrepareResult(
                success = false,
                engineType = TtsEngineType.UTROBIN,
                message = e.message ?: "Failed to init UtrobinTTS",
            )
        }
    }

    private fun logSessionIoSummary() {
        val s = ortSession ?: return
        try {
            Timber.d("Utrobin model inputs: ${s.inputInfo.keys.joinToString()}")
            Timber.d("Utrobin model outputs: ${s.outputInfo.keys.joinToString()}")
        } catch (e: Exception) {
            Timber.w(e, "Utrobin: could not log session IO")
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

    private fun normalizeForUtrobinTts(text: String): String {
        val prepared = text
            .replace('\u00A0', ' ')
            .replace("…", "...")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("„", "\"")
            .replace("№", " номер ")
            .replace(Regex("""\bи\s+т\.\s*д\.""", RegexOption.IGNORE_CASE), "и так далее")
            .replace(Regex("""\bи\s+т\.\s*п\.""", RegexOption.IGNORE_CASE), "и тому подобное")
            .replace(Regex("""\bт\.\s*д\.""", RegexOption.IGNORE_CASE), "так далее")
            .replace(Regex("""\bт\.\s*п\.""", RegexOption.IGNORE_CASE), "тому подобное")
            .replace(Regex("""\s*[—–]\s*"""), " - ")
        val lower = prepared.lowercase(Locale("ru", "RU"))
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

    private fun readSampleRateFromConfig(root: File): Int? {
        val config = File(root, "config.json")
        if (!config.exists()) return null
        return runCatching {
            val json = JSONObject(config.readText())
            json.optInt("sampling_rate").takeIf { it > 0 }
                ?: json.optJSONObject("audio")?.optInt("sample_rate")?.takeIf { it > 0 }
                ?: json.optJSONObject("data")?.optInt("sampling_rate")?.takeIf { it > 0 }
        }.getOrNull()
    }

    private fun failedPrepare(message: String, path: String? = null): TtsPrepareResult {
        _isReady.value = false
        return TtsPrepareResult(
            success = false,
            engineType = TtsEngineType.UTROBIN,
            resolvedPackPath = path,
            message = message,
        )
    }

    private fun clampedSpeakerId(): Int = speakerId.coerceIn(0, maxSpeakerIndex)

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stop()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0
        isPaused = false
        chapterFinishedCallback = onChapterFinished
        playbackSessionId++
        resetDiagnosticsForSession(sentences.size)
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
                    val outcome = playSentenceWithRecovery(sentence = s, sessionId = sessionId)
                    when (outcome.status) {
                        ChunkPlayStatus.SUCCESS -> if (!isPaused) currentSentenceIndex++
                        ChunkPlayStatus.INTERRUPTED -> return@launch
                        ChunkPlayStatus.FAILED -> {
                            val failure = outcome.failure ?: buildPlaybackFailure(
                                message = "Utrobin не смог дочитать фрагмент",
                                sentence = s,
                            )
                            recordChunkFailure(failure)
                            _playbackFailures.tryEmit(failure)
                            _isSpeaking.value = false
                            _currentWordRange.value = failure.chunkRange
                            return@launch
                        }
                    }
                }
                if (currentSentenceIndex >= sentences.size && !isPaused && sessionId == playbackSessionId) {
                    _isSpeaking.value = false
                    _currentWordRange.value = null
                    chapterFinishedCallback?.let { cb ->
                        withContext(Dispatchers.Main) { cb() }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) Timber.e(e, "Playback error")
                _isSpeaking.value = false
                _currentWordRange.value = null
            }
        }
    }

    private enum class ChunkPlayStatus {
        SUCCESS,
        INTERRUPTED,
        FAILED,
    }

    private data class ChunkPlayOutcome(
        val status: ChunkPlayStatus,
        val usedRecovery: Boolean = false,
        val failure: UtrobinPlaybackFailure? = null,
    )

    private suspend fun playSentenceWithRecovery(
        sentence: SentenceInfo,
        sessionId: Long,
    ): ChunkPlayOutcome {
        if (!canContinue(sessionId)) {
            return ChunkPlayOutcome(status = ChunkPlayStatus.INTERRUPTED)
        }
        markChunkAttempt(sentence)
        _currentWordRange.value = sentence.range()
        val attempt = generateAudio(sentence.text)
        if (attempt.audio != null && attempt.audio.size > 100) {
            if (!canContinue(sessionId)) {
                return ChunkPlayOutcome(status = ChunkPlayStatus.INTERRUPTED)
            }
            coroutineScope {
                val wj = launch { trackWords(sentence, attempt.audio.size) }
                try {
                    playAudio(attempt.audio)
                } finally {
                    wj.cancel()
                }
            }
            recordChunkSuccess(
                usedRecovery = sentence.splitDepth > 0,
                durationMs = attempt.durationMs,
            )
            return ChunkPlayOutcome(
                status = if (canContinue(sessionId)) ChunkPlayStatus.SUCCESS else ChunkPlayStatus.INTERRUPTED,
                usedRecovery = sentence.splitDepth > 0,
            )
        }

        val splits = splitSentenceForRecovery(sentence)
        if (splits.size <= 1) {
            return ChunkPlayOutcome(
                status = ChunkPlayStatus.FAILED,
                failure = buildPlaybackFailure(
                    message = attempt.errorMessage ?: "Не удалось подобрать безопасное разбиение фрагмента",
                    sentence = sentence,
                ),
            )
        }

        val splitReason = splits.first().sourceTag.substringAfter("recovery:")
        val recoveryNote = "Recovery split depth ${sentence.splitDepth + 1}: $splitReason"
        _diagnostics.value = diagnostics.value.copy(lastRecoveryAction = recoveryNote)
        Timber.w(
            "Utrobin recovery split depth=%d reason=%s preview=%s",
            sentence.splitDepth,
            splitReason,
            previewText(sentence.text),
        )

        for (split in splits) {
            val childOutcome = playSentenceWithRecovery(sentence = split, sessionId = sessionId)
            when (childOutcome.status) {
                ChunkPlayStatus.SUCCESS -> Unit
                ChunkPlayStatus.INTERRUPTED -> return childOutcome
                ChunkPlayStatus.FAILED -> return ChunkPlayOutcome(
                    status = ChunkPlayStatus.FAILED,
                    usedRecovery = true,
                    failure = childOutcome.failure ?: buildPlaybackFailure(
                        message = "Не удалось озвучить дочерний фрагмент после recovery split",
                        sentence = split,
                    ),
                )
            }
        }
        return ChunkPlayOutcome(status = ChunkPlayStatus.SUCCESS, usedRecovery = true)
    }

    private data class GenerationAttempt(
        val audio: FloatArray? = null,
        val durationMs: Long = 0L,
        val errorMessage: String? = null,
    )

    private fun generateAudio(text: String): GenerationAttempt {
        if (text.isBlank()) return GenerationAttempt(errorMessage = "Пустой chunk")
        val normalized = normalizeForUtrobinTts(text)
        if (normalized.isBlank()) return GenerationAttempt(errorMessage = "Пустой текст после normalize")

        val ids = UtrobinCharTokenizer.textToFlatIds(normalized, token2id)
        if (ids.isEmpty()) {
            Timber.w("Utrobin: empty token sequence")
            return GenerationAttempt(errorMessage = "Пустая последовательность токенов")
        }
        for (x in ids) {
            if (x < 0 || x > maxTokenId) {
                Timber.e("Utrobin: token id out of range: $x (max $maxTokenId)")
                return GenerationAttempt(errorMessage = "Token id out of range: $x")
            }
        }

        val env = OrtEnvironment.getEnvironment()
        val seqLen = ids.size
        val sid = clampedSpeakerId().toLong()
        val startedAt = SystemClock.elapsedRealtime()

        return synchronized(sessionLock) utrobinInfer@{
            ensureOrtSessionFresh()
            val session = ortSession ?: return@utrobinInfer GenerationAttempt(
                errorMessage = "Utrobin runtime не инициализирован",
            )
            try {
                OnnxTensor.createTensor(env, arrayOf(ids)).use { inputIds ->
                    OnnxTensor.createTensor(env, Array(1) { LongArray(seqLen) { 1L } }).use { mask ->
                        OnnxTensor.createTensor(env, longArrayOf(sid)).use { spk ->
                            val feeds = buildUtrobinFeeds(session, inputIds, mask, spk)
                            session.run(feeds).use runOut@{ result ->
                                val tensor = waveformOutputTensor(result) ?: run {
                                    Timber.e(
                                        "Utrobin: no tensor output (keys: ${result.joinToString { it.key }})",
                                    )
                                    return@runOut GenerationAttempt(errorMessage = "Модель не вернула waveform")
                                }
                                val raw = waveformToFloatArray(tensor)
                                if (raw.isEmpty()) {
                                    return@runOut GenerationAttempt(errorMessage = "Пустой waveform")
                                }
                                GenerationAttempt(
                                    audio = resampleForSpeechRate(raw),
                                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "UtrobinTTS ONNX failed: ${text.take(40)}")
                GenerationAttempt(
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    errorMessage = e.message ?: "Utrobin ONNX failed",
                )
            }
        }
    }

    private fun buildUtrobinFeeds(
        session: OrtSession,
        inputIds: OnnxTensor,
        mask: OnnxTensor,
        spk: OnnxTensor,
    ): Map<String, OnnxTensor> {
        val inputNames = session.inputInfo.keys.toList()
        val byLower = inputNames.associateBy { it.lowercase() }
        val feeds = linkedMapOf<String, OnnxTensor>()

        fun putIfPresent(vararg aliases: String, tensor: OnnxTensor) {
            val name = aliases.firstNotNullOfOrNull { byLower[it] } ?: return
            feeds[name] = tensor
        }

        putIfPresent("input_ids", "input", "x", "inputs", "tokens", tensor = inputIds)
        putIfPresent("attention_mask", "mask", "attn_mask", tensor = mask)
        putIfPresent("speaker_id", "speaker", "sid", "speakers", "spk", tensor = spk)

        if (feeds.isEmpty() && inputNames.isNotEmpty()) {
            feeds[inputNames[0]] = inputIds
            if (inputNames.size >= 2) feeds[inputNames[1]] = mask
            if (inputNames.size >= 3) feeds[inputNames[2]] = spk
        } else {
            val fallback = listOf(inputIds, mask, spk)
            var idx = 0
            for (name in inputNames) {
                if (!feeds.containsKey(name) && idx < fallback.size) {
                    feeds[name] = fallback[idx++]
                }
            }
        }
        if (feeds.size != inputNames.size) {
            Timber.w(
                "Utrobin: mapped ${feeds.size} feeds for ${inputNames.size} inputs; names=$inputNames",
            )
        }
        return feeds
    }

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

    private fun canContinue(sessionId: Long): Boolean {
        return !isPaused && sessionId == playbackSessionId && playbackJob?.isActive != false
    }

    private fun resetDiagnosticsForPack(packId: String?) {
        _diagnostics.value = UtrobinPlaybackDiagnostics(
            packId = packId ?: selectedPackId,
            speakerId = clampedSpeakerId(),
            speakerLabel = SPEAKERS.firstOrNull { it.second == clampedSpeakerId() }?.first,
            speechRate = speechRate,
            ortThreads = ortIntraThreads,
        )
    }

    private fun resetDiagnosticsForSession(totalChunks: Int) {
        _diagnostics.value = diagnostics.value.copy(
            packId = diagnostics.value.packId ?: selectedPackId,
            speakerId = clampedSpeakerId(),
            speakerLabel = SPEAKERS.firstOrNull { it.second == clampedSpeakerId() }?.first,
            speechRate = speechRate,
            ortThreads = ortIntraThreads,
            totalChunks = totalChunks,
            completedChunks = 0,
            recoveredChunks = 0,
            failedChunks = 0,
            lastChunkPreview = null,
            lastChunkRange = null,
            lastChunkSplitDepth = 0,
            lastChunkDurationMs = null,
            lastRecoveryAction = null,
            lastFailureMessage = null,
            lastFailurePreview = null,
            lastFailureRange = null,
        )
    }

    private fun markChunkAttempt(sentence: SentenceInfo) {
        _diagnostics.value = diagnostics.value.copy(
            lastChunkPreview = previewText(sentence.text),
            lastChunkRange = sentence.range(),
            lastChunkSplitDepth = sentence.splitDepth,
        )
    }

    private fun recordChunkSuccess(usedRecovery: Boolean, durationMs: Long) {
        _diagnostics.value = diagnostics.value.copy(
            completedChunks = (diagnostics.value.completedChunks + 1).coerceAtMost(diagnostics.value.totalChunks),
            recoveredChunks = if (usedRecovery) diagnostics.value.recoveredChunks + 1 else diagnostics.value.recoveredChunks,
            lastChunkDurationMs = durationMs,
        )
    }

    private fun recordChunkFailure(failure: UtrobinPlaybackFailure) {
        _diagnostics.value = diagnostics.value.copy(
            failedChunks = diagnostics.value.failedChunks + 1,
            lastFailureMessage = failure.message,
            lastFailurePreview = failure.chunkPreview,
            lastFailureRange = failure.chunkRange,
        )
        Timber.e(
            "Utrobin final failure: speaker=%s pack=%s range=%s preview=%s message=%s",
            failure.speakerLabel ?: failure.speakerId,
            failure.packId,
            failure.chunkRange,
            failure.chunkPreview,
            failure.message,
        )
    }

    private fun buildPlaybackFailure(message: String, sentence: SentenceInfo): UtrobinPlaybackFailure {
        return UtrobinPlaybackFailure(
            message = message,
            chunkPreview = previewText(sentence.text),
            chunkRange = sentence.range(),
            packId = diagnostics.value.packId,
            speakerId = clampedSpeakerId(),
            speakerLabel = SPEAKERS.firstOrNull { it.second == clampedSpeakerId() }?.first,
        )
    }

    private fun previewText(text: String): String {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        return if (normalized.length <= CHUNK_PREVIEW_LIMIT) {
            normalized
        } else {
            normalized.take(CHUNK_PREVIEW_LIMIT - 1) + "…"
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
        _diagnostics.value = diagnostics.value.copy(speechRate = speechRate)
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
            if (t.isNotBlank()) splitLargeChunk(t, last, end).forEach(result::add)
            last = end
        }
        if (last < text.length) {
            val t = text.substring(last).trim()
            if (t.isNotBlank()) splitLargeChunk(t, last, text.length).forEach(result::add)
        }
        if (result.isEmpty() && text.isNotBlank()) splitLargeChunk(text.trim(), 0, text.length).forEach(result::add)
        return mergeNearbySentences(result, text)
    }

    private fun splitLargeChunk(chunk: String, start: Int, end: Int): List<SentenceInfo> {
        if (chunk.length <= 220) return listOf(SentenceInfo(chunk, start, end))
        val out = mutableListOf<SentenceInfo>()
        var cursor = 0
        while (cursor < chunk.length) {
            val rawEnd = (cursor + 200).coerceAtMost(chunk.length)
            if (rawEnd >= chunk.length) {
                val last = chunk.substring(cursor).trim()
                if (last.isNotBlank()) out += SentenceInfo(last, start + cursor, end)
                break
            }
            val region = chunk.substring(cursor, rawEnd)
            val splitAt = maxOf(region.lastIndexOf(", "), region.lastIndexOf(" - "), region.lastIndexOf(' '))
                .takeIf { it > 28 } ?: region.length
            val pieceEnd = (cursor + splitAt).coerceAtMost(chunk.length)
            val piece = chunk.substring(cursor, pieceEnd).trim()
            if (piece.isNotBlank()) {
                out += SentenceInfo(piece, start + cursor, start + pieceEnd)
            }
            cursor = pieceEnd.coerceAtLeast(cursor + 1)
        }
        return out
    }

    private fun splitSentenceForRecovery(sentence: SentenceInfo): List<SentenceInfo> {
        if (sentence.splitDepth >= 2 || sentence.text.length < 32) return emptyList()
        val strategies = listOf(
            "\n\n" to "paragraph",
            ". " to "sentence",
            "; " to "semicolon",
            ": " to "colon",
            ", " to "comma",
            " - " to "dash",
        )
        for ((separator, label) in strategies) {
            val pieces = splitSentenceBySeparator(sentence, separator, label)
            if (pieces.size > 1) return pieces
        }
        return splitSentenceByLength(sentence)
    }

    private fun splitSentenceBySeparator(
        sentence: SentenceInfo,
        separator: String,
        label: String,
    ): List<SentenceInfo> {
        if (!sentence.text.contains(separator)) return emptyList()
        val parts = mutableListOf<SentenceInfo>()
        var searchStart = 0
        val text = sentence.text
        while (searchStart < text.length) {
            val splitIndex = text.indexOf(separator, startIndex = searchStart)
            val pieceEndExclusive = if (splitIndex >= 0) splitIndex + separator.length else text.length
            val rawPiece = text.substring(searchStart, pieceEndExclusive)
            val info = createSentenceInfo(
                rawText = rawPiece,
                startOffset = sentence.startOffset + searchStart,
                splitDepth = sentence.splitDepth + 1,
                sourceTag = "recovery:$label",
            )
            if (info != null) parts += info
            if (splitIndex < 0) break
            searchStart = pieceEndExclusive
        }
        return parts
    }

    private fun splitSentenceByLength(sentence: SentenceInfo): List<SentenceInfo> {
        val text = sentence.text
        if (text.length < 72) return emptyList()
        val midpoint = text.length / 2
        val searchWindow = 48
        val from = (midpoint - searchWindow).coerceAtLeast(16)
        val to = (midpoint + searchWindow).coerceAtMost(text.lastIndex)
        var splitAt = -1
        for (i in to downTo from) {
            if (text[i].isWhitespace()) {
                splitAt = i
                break
            }
        }
        if (splitAt <= 16 || splitAt >= text.lastIndex - 16) return emptyList()
        val first = createSentenceInfo(
            rawText = text.substring(0, splitAt),
            startOffset = sentence.startOffset,
            splitDepth = sentence.splitDepth + 1,
            sourceTag = "recovery:length",
        )
        val second = createSentenceInfo(
            rawText = text.substring(splitAt),
            startOffset = sentence.startOffset + splitAt,
            splitDepth = sentence.splitDepth + 1,
            sourceTag = "recovery:length",
        )
        return listOfNotNull(first, second).takeIf { it.size > 1 }.orEmpty()
    }

    private fun createSentenceInfo(
        rawText: String,
        startOffset: Int,
        splitDepth: Int,
        sourceTag: String,
    ): SentenceInfo? {
        var trimStart = 0
        var trimEnd = rawText.length
        while (trimStart < trimEnd && rawText[trimStart].isWhitespace()) trimStart++
        while (trimEnd > trimStart && rawText[trimEnd - 1].isWhitespace()) trimEnd--
        if (trimStart >= trimEnd) return null
        val trimmed = rawText.substring(trimStart, trimEnd)
        return SentenceInfo(
            text = trimmed,
            startOffset = startOffset + trimStart,
            endOffset = startOffset + trimEnd,
            splitDepth = splitDepth,
            sourceTag = sourceTag,
        )
    }

    private fun mergeNearbySentences(sentences: List<SentenceInfo>, sourceText: String): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        val maxShort = mergeShortThreshold
        val maxTotal = mergeTotalCap
        for (i in 1 until sentences.size) {
            val next = sentences[i]
            val gap = sourceText.substring(
                current.endOffset.coerceAtLeast(0).coerceAtMost(sourceText.length),
                next.startOffset.coerceAtLeast(0).coerceAtMost(sourceText.length),
            )
            val keepBoundary = gap.contains("\n\n")
            val shouldMerge = current.text.length < maxShort && next.text.length < maxShort &&
                (current.text.length + next.text.length) < maxTotal &&
                !keepBoundary
            current = if (shouldMerge) {
                SentenceInfo(
                    text = "${current.text} ${next.text}".trim(),
                    startOffset = current.startOffset,
                    endOffset = next.endOffset,
                    splitDepth = maxOf(current.splitDepth, next.splitDepth),
                    sourceTag = "merged",
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
