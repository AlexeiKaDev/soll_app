package com.soll.domain.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.soll.domain.tts.book.TtsPrepareResult
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.TtsPackLibrary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

@Singleton
class SileroJitEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLibrary: TtsPackLibrary,
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

    private val _diagnostics = MutableStateFlow(PiperPlaybackDiagnostics())
    val diagnostics: StateFlow<PiperPlaybackDiagnostics> = _diagnostics.asStateFlow()

    private val _playbackFailures = MutableSharedFlow<PiperPlaybackFailure>(extraBufferCapacity = 2)
    val playbackFailures: SharedFlow<PiperPlaybackFailure> = _playbackFailures.asSharedFlow()

    private var isPaused = false
    private var currentSentenceIndex = 0
    private var sentences: List<SentenceInfo> = emptyList()
    private var chapterFinishedCallback: (() -> Unit)? = null

    @Volatile
    private var playbackSessionId: Long = 0L

    private var speechRate = 1.0f
    private var sampleRate = 22050
    private var sherpaNumThreads: Int = 2
    private var mergeShortThreshold = 220
    private var mergeTotalCap = 360
    private var selectedPackId: String? = null
    private var activeSignature: String? = null
    private var prosodyPreset: PiperProsodyPreset = PiperProsodyPreset.DEFAULT

    companion object {
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val MAX_RECOVERY_SPLIT_DEPTH = 3
        private const val MIN_RECOVERY_CHUNK_CHARS = 36
        private const val MIN_RECOVERY_SIDE_CHARS = 18
        private const val CHUNK_PREVIEW_LIMIT = 88

        private val VOICE_LABELS = mapOf(
            "irina" to "Ирина (ж)",
            "denis" to "Денис (м)",
            "dmitri" to "Дмитрий (м)",
            "ruslan" to "Руслан (м)",
            "burunov" to "Бурунов (м)",
        )

        private val PARAGRAPH_SEPARATOR_REGEX = Regex("""\n{2,}""")
        private val SENTENCE_BOUNDARY_REGEX = Regex("""[.!?]+(?:["»”']+)?(?=\s|$)""")
    }

    data class SentenceInfo(
        val text: String,
        val rawText: String,
        val startOffset: Int,
        val endOffset: Int,
        val splitDepth: Int = 0,
        val sourceTag: String = "sentence",
        val mergedCount: Int = 1,
        val pauseAfterMs: Long = 120L,
    ) {
        fun range(): IntRange = IntRange(startOffset, (endOffset - 1).coerceAtLeast(startOffset))
    }

    private data class PiperVoiceTuning(
        val mergeShortCap: Int,
        val mergeTotalCap: Int,
        val sentencePauseMs: Long,
        val paragraphPauseMs: Long,
    )

    private enum class ChunkPlayStatus {
        SUCCESS,
        FAILED,
        INTERRUPTED,
    }

    private data class ChunkPlayOutcome(
        val status: ChunkPlayStatus,
        val usedRecovery: Boolean = false,
        val failure: PiperPlaybackFailure? = null,
    )

    private data class GenerationAttempt(
        val audio: FloatArray? = null,
        val durationMs: Long,
        val errorMessage: String? = null,
        val fromPrefetch: Boolean = false,
        val prefetchWaitMs: Long? = null,
    )

    private data class PrefetchedChunk(
        val index: Int,
        val sessionId: Long,
        val chunk: SentenceInfo,
        val generation: Deferred<GenerationAttempt>,
    )

    private data class RecoverySplit(
        val index: Int,
        val reason: String,
    )

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        val (a, b) = TtsBookPerformanceProfile.chunkMergeLimits(profile)
        mergeShortThreshold = a
        mergeTotalCap = b
        val threads = TtsBookPerformanceProfile.sherpaNumThreads(
            profile,
            Runtime.getRuntime().availableProcessors(),
        )
        applySherpaNumThreadsInternal(threads)
    }

    fun getSherpaNumThreads(): Int = sherpaNumThreads

    fun getProsodyPreset(): PiperProsodyPreset = prosodyPreset

    fun applyProsodyPreset(preset: PiperProsodyPreset) {
        if (preset == prosodyPreset) return
        prosodyPreset = preset
        _diagnostics.update {
            it.copy(
                prosodyPresetKey = preset.storageKey,
                prosodyPresetLabel = preset.displayName,
                noiseScale = preset.noiseScale,
                noiseScaleW = preset.noiseScaleW,
            )
        }
        invalidateRuntime()
    }

    fun applySherpaNumThreads(value: Float) {
        applySherpaNumThreadsInternal(value.roundToInt())
    }

    private fun applySherpaNumThreadsInternal(value: Int) {
        val newValue = value.coerceIn(1, 4)
        if (newValue == sherpaNumThreads) return
        sherpaNumThreads = newValue
        _diagnostics.update { it.copy(sherpaThreads = sherpaNumThreads) }
        invalidateRuntime()
    }

    fun isModelDownloaded(): Boolean = resolveUsablePack() != null

    fun setVoice(voiceId: String) {
        val pack = packLibrary.listPacksFor(TtsEngineType.SILERO)
            .filter { it.isRunnable }
            .firstOrNull { candidate ->
                candidate.voices.any { it.id.equals(voiceId, ignoreCase = true) }
            } ?: return
        setSelectedPackId(pack.packId)
    }

    @Suppress("UNUSED_PARAMETER")
    fun setUseV5(enabled: Boolean) {}

    @Suppress("UNUSED_PARAMETER")
    fun setV5SpeakerId(id: Int) {}

    fun setSelectedPackId(packId: String?) {
        if (selectedPackId == packId) return
        selectedPackId = packId
        _diagnostics.update { it.copy(packId = packId) }
        invalidateRuntime()
    }

    suspend fun initialize(): TtsPrepareResult = withContext(Dispatchers.IO) {
        val pack = resolveUsablePack()
            ?: return@withContext failedPrepare("Не найден полный Piper/Sherpa-пакет в локальной папке tts")

        val root = File(pack.rootDir)
        val tokensFile = File(root, "tokens.txt")
        val dataDir = File(root, "espeak-ng-data")
        if (!tokensFile.exists()) {
            return@withContext failedPrepare("В Piper-пакете нет tokens.txt", root.absolutePath)
        }
        if (!dataDir.isDirectory) {
            return@withContext failedPrepare("В Piper-пакете нет espeak-ng-data", root.absolutePath)
        }
        val modelFile = resolveVoiceModelFile(root)
            ?: return@withContext failedPrepare("Не найден ONNX-файл Piper в выбранном пакете", root.absolutePath)
        if (modelFile.length() < MIN_MODEL_BYTES) {
            return@withContext failedPrepare(
                "ONNX-файл Piper слишком маленький: ${modelFile.name}",
                modelFile.absolutePath,
            )
        }

        val resolvedVoiceId = pack.voices.firstOrNull()?.id ?: detectVoiceId(modelFile)
        val resolvedVoiceLabel = pack.voices.firstOrNull()?.label ?: resolvedVoiceId?.let(::voiceLabel) ?: modelFile.name

        val preset = prosodyPreset
        val signature = "${root.absolutePath}|${modelFile.absolutePath}|$sherpaNumThreads|${preset.storageKey}"
        try {
            if (tts == null || activeSignature != signature) {
                stop()
                tts?.release()
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = modelFile.absolutePath,
                            tokens = tokensFile.absolutePath,
                            dataDir = dataDir.absolutePath,
                            noiseScale = preset.noiseScale,
                            noiseScaleW = preset.noiseScaleW,
                            lengthScale = preset.lengthScale,
                        ),
                        numThreads = sherpaNumThreads,
                        debug = false,
                    ),
                )
                tts = OfflineTts(config = config)
                activeSignature = signature
            }
            sampleRate = tts?.sampleRate() ?: 22050
            _isReady.value = true
            _diagnostics.update {
                it.copy(
                    packId = pack.packId,
                    voiceId = resolvedVoiceId,
                    voiceLabel = resolvedVoiceLabel,
                    prosodyPresetKey = preset.storageKey,
                    prosodyPresetLabel = preset.displayName,
                    noiseScale = preset.noiseScale,
                    noiseScaleW = preset.noiseScaleW,
                    speechRate = speechRate,
                    sherpaThreads = sherpaNumThreads,
                )
            }
            Timber.d(
                "Piper/Sherpa ready: model=%s voice=%s sampleRate=%d threads=%d preset=%s noise=%.3f noiseW=%.3f selectedPack=%s",
                modelFile.absolutePath,
                resolvedVoiceLabel,
                sampleRate,
                sherpaNumThreads,
                preset.storageKey,
                preset.noiseScale,
                preset.noiseScaleW,
                pack.packId,
            )
            TtsPrepareResult(
                success = true,
                engineType = TtsEngineType.SILERO,
                resolvedPackPath = root.absolutePath,
                resolvedVoiceId = resolvedVoiceId,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Piper/Sherpa")
            failedPrepare(e.message ?: "Не удалось инициализировать Piper/Sherpa", root.absolutePath)
        }
    }

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) = coroutineScope {
        stop()
        sentences = splitIntoSentences(text)
        currentSentenceIndex = 0
        isPaused = false
        chapterFinishedCallback = onChapterFinished
        playbackSessionId++
        resetDiagnosticsForSession(sentences.size)
        val tuning = currentVoiceTuning()
        Timber.d(
            "Piper speakChapter: chunks=%d pack=%s voice=%s rate=%.2f threads=%d preset=%s noise=%.3f noiseW=%.3f merge=%d/%d pause=%d/%d",
            sentences.size,
            diagnostics.value.packId,
            diagnostics.value.voiceLabel,
            speechRate,
            sherpaNumThreads,
            prosodyPreset.storageKey,
            prosodyPreset.noiseScale,
            prosodyPreset.noiseScaleW,
            tuning.mergeShortCap,
            tuning.mergeTotalCap,
            tuning.sentencePauseMs,
            tuning.paragraphPauseMs,
        )
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
            var pendingPrefetch: PrefetchedChunk? = null

            fun startPrefetch(index: Int): PrefetchedChunk? {
                if (index >= sentences.size || !canContinue(sessionId)) return null
                val chunk = sentences[index]
                Timber.d(
                    "Piper prefetch start: idx=%d chars=%d preview=%s",
                    index,
                    chunk.text.length,
                    previewText(chunk.text),
                )
                _diagnostics.update { it.copy(prefetchQueuedIndex = index + 1) }
                return PrefetchedChunk(
                    index = index,
                    sessionId = sessionId,
                    chunk = chunk,
                    generation = async(Dispatchers.IO) { generateAudio(chunk, index) },
                )
            }

            try {
                while (currentSentenceIndex < sentences.size && isActive && !isPaused && sessionId == playbackSessionId) {
                    val chunkIndex = currentSentenceIndex
                    val sentence = sentences[currentSentenceIndex]
                    val prefetched = pendingPrefetch?.takeIf {
                        it.index == chunkIndex && it.sessionId == sessionId && it.chunk == sentence
                    }
                    if (prefetched == null) {
                        pendingPrefetch?.generation?.cancel()
                    }
                    pendingPrefetch = null

                    val attempt = if (prefetched != null) {
                        val waitStartedAt = SystemClock.elapsedRealtime()
                        val result = prefetched.generation.await()
                        val waitMs = SystemClock.elapsedRealtime() - waitStartedAt
                        Timber.d(
                            "Piper prefetch hit: idx=%d waitMs=%d genMs=%d audio=%s preview=%s",
                            chunkIndex,
                            waitMs,
                            result.durationMs,
                            result.audio?.size ?: 0,
                            previewText(sentence.text),
                        )
                        _diagnostics.update {
                            it.copy(
                                prefetchHits = it.prefetchHits + 1,
                                prefetchQueuedIndex = null,
                            )
                        }
                        result.copy(fromPrefetch = true, prefetchWaitMs = waitMs)
                    } else {
                        generateAudio(sentence, chunkIndex)
                    }

                    var nextPrefetch: PrefetchedChunk? = null
                    fun startNextPrefetchOnce() {
                        if (nextPrefetch == null) {
                            nextPrefetch = startPrefetch(chunkIndex + 1)
                        }
                    }

                    val outcome = playChunkWithRecovery(
                        chunk = sentence,
                        chunkIndex = chunkIndex,
                        sessionId = sessionId,
                        precomputedAttempt = attempt,
                        onAudioReady = ::startNextPrefetchOnce,
                    )
                    when (outcome.status) {
                        ChunkPlayStatus.SUCCESS -> {
                            recordChunkSuccess(outcome.usedRecovery)
                            if (!isPaused && sessionId == playbackSessionId) {
                                currentSentenceIndex++
                                pendingPrefetch = nextPrefetch
                            } else {
                                nextPrefetch?.generation?.cancel()
                            }
                        }

                        ChunkPlayStatus.INTERRUPTED -> {
                            nextPrefetch?.generation?.cancel()
                            break
                        }

                        ChunkPlayStatus.FAILED -> {
                            nextPrefetch?.generation?.cancel()
                            val failure = outcome.failure ?: buildPlaybackFailure(
                                message = "Не удалось озвучить фрагмент",
                                chunk = sentence,
                            )
                            recordChunkFailure(failure)
                            _playbackFailures.tryEmit(failure)
                            _isSpeaking.value = false
                            _currentWordRange.value = failure.chunkRange
                            break
                        }
                    }
                }
                if (currentSentenceIndex >= sentences.size && !isPaused && sessionId == playbackSessionId) {
                    _isSpeaking.value = false
                    _currentWordRange.value = null
                    Timber.d(
                        "Piper session completed: done=%d/%d recovered=%d failed=%d voice=%s pack=%s",
                        diagnostics.value.completedChunks,
                        diagnostics.value.totalChunks,
                        diagnostics.value.recoveredChunks,
                        diagnostics.value.failedChunks,
                        diagnostics.value.voiceLabel,
                        diagnostics.value.packId,
                    )
                    chapterFinishedCallback?.let { cb ->
                        withContext(Dispatchers.Main) { cb() }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Piper/Sherpa playback error")
                }
                _isSpeaking.value = false
                _currentWordRange.value = null
            } finally {
                pendingPrefetch?.generation?.cancel()
                _diagnostics.update { it.copy(prefetchQueuedIndex = null) }
            }
        }
    }

    private suspend fun playChunkWithRecovery(
        chunk: SentenceInfo,
        chunkIndex: Int,
        sessionId: Long,
        precomputedAttempt: GenerationAttempt? = null,
        onAudioReady: (() -> Unit)? = null,
    ): ChunkPlayOutcome {
        if (!canContinue(sessionId)) return ChunkPlayOutcome(ChunkPlayStatus.INTERRUPTED)

        markChunkAttempt(chunk)
        _currentWordRange.value = chunk.range()

        val attempt = precomputedAttempt ?: generateAudio(chunk, chunkIndex)
        recordChunkGeneration(chunk, attempt)
        val audio = attempt.audio
        if (audio != null && audio.isNotEmpty()) {
            if (!canContinue(sessionId)) return ChunkPlayOutcome(ChunkPlayStatus.INTERRUPTED)
            onAudioReady?.invoke()
            val fullyPlayed = coroutineScope {
                val wordJob = launch(Dispatchers.IO) { trackWordsInSentence(chunk, audio.size) }
                try {
                    playAudio(audio, sessionId)
                } finally {
                    wordJob.cancel()
                }
            }
            return if (fullyPlayed) {
                applyChunkPause(chunk, sessionId)
                ChunkPlayOutcome(ChunkPlayStatus.SUCCESS)
            } else {
                ChunkPlayOutcome(ChunkPlayStatus.INTERRUPTED)
            }
        }

        if (chunk.splitDepth >= MAX_RECOVERY_SPLIT_DEPTH || chunk.text.length < MIN_RECOVERY_CHUNK_CHARS) {
            return ChunkPlayOutcome(
                status = ChunkPlayStatus.FAILED,
                failure = buildPlaybackFailure(
                    message = attempt.errorMessage ?: "Фрагмент слишком нестабилен для Piper",
                    chunk = chunk,
                ),
            )
        }

        val splits = splitChunkForRecovery(chunk)
        if (splits.size <= 1) {
            return ChunkPlayOutcome(
                status = ChunkPlayStatus.FAILED,
                failure = buildPlaybackFailure(
                    message = attempt.errorMessage ?: "Не удалось подобрать безопасное разбиение фрагмента",
                    chunk = chunk,
                ),
            )
        }

        val splitReason = splits.first().sourceTag.substringAfter("recovery:")
        val recoveryNote = "Recovery split depth ${chunk.splitDepth + 1}: $splitReason"
        _diagnostics.update { it.copy(lastRecoveryAction = recoveryNote) }
        Timber.w(
            "Piper recovery split: chunk=%d depth=%d reason=%s preview=%s",
            chunkIndex,
            chunk.splitDepth,
            splitReason,
            previewText(chunk.text),
        )

        var usedRecovery = true
        for (splitChunk in splits) {
            val childOutcome = playChunkWithRecovery(
                chunk = splitChunk,
                chunkIndex = chunkIndex,
                sessionId = sessionId,
            )
            when (childOutcome.status) {
                ChunkPlayStatus.SUCCESS -> usedRecovery = true
                ChunkPlayStatus.INTERRUPTED -> return childOutcome
                ChunkPlayStatus.FAILED -> {
                    val failure = childOutcome.failure ?: buildPlaybackFailure(
                        message = "Не удалось озвучить дочерний фрагмент после recovery split",
                        chunk = splitChunk,
                    )
                    return ChunkPlayOutcome(
                        status = ChunkPlayStatus.FAILED,
                        usedRecovery = true,
                        failure = failure,
                    )
                }
            }
        }
        return ChunkPlayOutcome(
            status = ChunkPlayStatus.SUCCESS,
            usedRecovery = usedRecovery,
        )
    }

    private fun generateAudio(chunk: SentenceInfo, chunkIndex: Int): GenerationAttempt {
        val engine = tts
        if (engine == null) {
            return GenerationAttempt(durationMs = 0, errorMessage = "Piper runtime не инициализирован")
        }
        if (chunk.text.isBlank()) {
            return GenerationAttempt(durationMs = 0, errorMessage = "Пустой chunk")
        }

        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val speed = speechRate.coerceIn(0.5f, 2.0f)
            val synthesisText = prepareTextForPiper(chunk.text)
            val audio = engine.generate(text = synthesisText, sid = 0, speed = speed).samples
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            if (audio.isEmpty()) {
                Timber.w(
                    "Piper chunk empty audio: idx=%d depth=%d len=%d preview=%s",
                    chunkIndex,
                    chunk.splitDepth,
                    chunk.text.length,
                    previewText(chunk.text),
                )
                GenerationAttempt(durationMs = durationMs, errorMessage = "Piper вернул пустой аудиобуфер")
            } else {
                Timber.d(
                    "Piper chunk ok: idx=%d depth=%d chars=%d samples=%d genMs=%d merged=%d source=%s preview=%s",
                    chunkIndex,
                    chunk.splitDepth,
                    synthesisText.length,
                    audio.size,
                    durationMs,
                    chunk.mergedCount,
                    chunk.sourceTag,
                    previewText(synthesisText),
                )
                GenerationAttempt(audio = audio, durationMs = durationMs)
            }
        } catch (e: Exception) {
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            Timber.e(
                e,
                "Piper chunk failed: idx=%d depth=%d chars=%d genMs=%d source=%s preview=%s",
                chunkIndex,
                chunk.splitDepth,
                chunk.text.length,
                durationMs,
                chunk.sourceTag,
                previewText(chunk.text),
            )
            GenerationAttempt(
                durationMs = durationMs,
                errorMessage = e.message ?: "Sherpa/Piper generation failed",
            )
        }
    }

    private suspend fun trackWordsInSentence(sentence: SentenceInfo, audioSamples: Int) {
        val durationMs = (audioSamples.toFloat() / sampleRate * 1000).toLong()
        val words = mutableListOf<IntRange>()
        var i = 0
        val text = sentence.text
        while (i < text.length) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length) break
            val start = i
            while (i < text.length && !text[i].isWhitespace()) i++
            words.add(IntRange(start, i))
        }
        if (words.isEmpty()) return

        val totalChars = words.sumOf { it.last - it.first }.coerceAtLeast(1)
        for (word in words) {
            _currentWordRange.value = IntRange(
                sentence.startOffset + word.first,
                sentence.startOffset + word.last,
            )
            val wordDurationMs = (durationMs * (word.last - word.first) / totalChars).coerceAtLeast(50)
            delay(wordDurationMs)
        }
    }

    private suspend fun playAudio(audioData: FloatArray, sessionId: Long): Boolean {
        val shorts = ShortArray(audioData.size) { i ->
            (audioData[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(shorts.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        var elapsed = 0L
        val totalMs = (shorts.size * 1000L / sampleRate).coerceAtLeast(1L)
        try {
            track.write(shorts, 0, shorts.size)
            track.play()
            while (elapsed < totalMs && canContinue(sessionId)) {
                val step = minOf(20L, totalMs - elapsed)
                delay(step)
                elapsed += step
            }
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
            if (audioTrack == track) audioTrack = null
        }
        return elapsed >= totalMs && canContinue(sessionId)
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
        _diagnostics.update { it.copy(speechRate = speechRate) }
    }

    fun shutdown() {
        stop()
        tts?.release()
        tts = null
        activeSignature = null
        _isReady.value = false
    }

    private fun resolveUsablePack(): DetectedTtsPack? {
        val packs = packLibrary.listPacksFor(TtsEngineType.SILERO).filter { it.isRunnable }
        if (packs.isEmpty()) return null
        selectedPackId?.let { id ->
            packs.firstOrNull { it.packId == id }?.let { return it }
        }
        return packs.first()
    }

    private fun resolveVoiceModelFile(root: File): File? {
        val candidates = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("onnx", true) && it.length() >= MIN_MODEL_BYTES }
            ?.sortedBy { it.name }
            .orEmpty()
        if (candidates.isEmpty()) return null
        return candidates.first()
    }

    private fun detectVoiceId(file: File): String? {
        val name = file.nameWithoutExtension.lowercase()
        return VOICE_LABELS.keys.firstOrNull { voiceId ->
            name.contains("-$voiceId-") ||
                name.endsWith("-$voiceId-medium") ||
                name.endsWith("-$voiceId-high") ||
                name.endsWith(voiceId)
        }
    }

    private fun voiceLabel(voiceId: String): String = VOICE_LABELS[voiceId.lowercase()] ?: voiceId

    private fun invalidateRuntime() {
        stop()
        tts?.release()
        tts = null
        activeSignature = null
        _isReady.value = false
    }

    private fun failedPrepare(message: String, path: String? = null): TtsPrepareResult {
        _isReady.value = false
        return TtsPrepareResult(
            success = false,
            engineType = TtsEngineType.SILERO,
            resolvedPackPath = path,
            message = message,
        )
    }

    private fun splitIntoSentences(text: String): List<SentenceInfo> {
        val result = mutableListOf<SentenceInfo>()
        splitIntoParagraphRanges(text).forEach { (start, endExclusive) ->
            val paragraphChunks = splitParagraphIntoSentences(text, start, endExclusive)
            result += mergeNearbySentences(paragraphChunks)
        }
        if (result.isEmpty() && text.isNotBlank()) {
            buildChunkFromBounds(text, 0, text.length)?.let(result::add)
        }
        Timber.d("Piper splitIntoSentences: total=%d", result.size)
        return result
    }

    private fun splitIntoParagraphRanges(text: String): List<Pair<Int, Int>> {
        if (text.isBlank()) return emptyList()
        val ranges = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        PARAGRAPH_SEPARATOR_REGEX.findAll(text).forEach { match ->
            val endExclusive = match.range.first
            if (cursor < endExclusive) {
                ranges += cursor to endExclusive
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            ranges += cursor to text.length
        }
        return if (ranges.isEmpty()) listOf(0 to text.length) else ranges
    }

    private fun splitParagraphIntoSentences(
        fullText: String,
        paragraphStart: Int,
        paragraphEndExclusive: Int,
    ): List<SentenceInfo> {
        val paragraph = fullText.substring(paragraphStart, paragraphEndExclusive)
        val chunks = mutableListOf<SentenceInfo>()
        var localStart = 0
        SENTENCE_BOUNDARY_REGEX.findAll(paragraph).forEach { match ->
            val localEnd = match.range.last + 1
            buildChunkFromBounds(
                fullText = fullText,
                rawStart = paragraphStart + localStart,
                rawEndExclusive = paragraphStart + localEnd,
                pauseAfterMs = pauseForChunk(
                    rawText = fullText.substring(paragraphStart + localStart, paragraphStart + localEnd),
                    isParagraphEnd = false,
                ),
            )?.let { chunks += splitLargeChunk(it) }
            localStart = localEnd
        }
        if (localStart < paragraph.length) {
            buildChunkFromBounds(
                fullText = fullText,
                rawStart = paragraphStart + localStart,
                rawEndExclusive = paragraphEndExclusive,
                pauseAfterMs = pauseForChunk(
                    rawText = fullText.substring(paragraphStart + localStart, paragraphEndExclusive),
                    isParagraphEnd = true,
                ),
            )?.let { chunks += splitLargeChunk(it) }
        }
        return chunks
    }

    private fun splitLargeChunk(chunk: SentenceInfo): List<SentenceInfo> {
        val maxChars = currentVoiceTuning().mergeTotalCap.coerceIn(120, 260)
        if (chunk.text.length <= maxChars) return listOf(chunk)

        val raw = chunk.rawText
        val result = mutableListOf<SentenceInfo>()
        var cursor = 0
        while (cursor < raw.length) {
            val remaining = raw.length - cursor
            if (remaining <= maxChars) {
                buildChunkFromRawText(
                    rawText = raw.substring(cursor),
                    globalStartOffset = chunk.startOffset + cursor,
                    splitDepth = chunk.splitDepth,
                    sourceTag = "${chunk.sourceTag}:split",
                    mergedCount = 1,
                    pauseAfterMs = chunk.pauseAfterMs,
                )?.let(result::add)
                break
            }

            val hardEnd = (cursor + maxChars).coerceAtMost(raw.length)
            val window = raw.substring(cursor, hardEnd)
            val minSide = (maxChars / 3).coerceAtLeast(MIN_RECOVERY_SIDE_CHARS)
            val localSplit = chooseLargeChunkSplit(window, minSide) ?: maxChars
            val pieceEnd = (cursor + localSplit).coerceIn(cursor + 1, raw.length)
            buildChunkFromRawText(
                rawText = raw.substring(cursor, pieceEnd),
                globalStartOffset = chunk.startOffset + cursor,
                splitDepth = chunk.splitDepth,
                sourceTag = "${chunk.sourceTag}:split",
                mergedCount = 1,
                pauseAfterMs = currentVoiceTuning().sentencePauseMs,
            )?.let(result::add)
            cursor = pieceEnd
        }

        if (result.size > 1) {
            Timber.d(
                "Piper split large chunk: chars=%d parts=%d max=%d preview=%s",
                chunk.text.length,
                result.size,
                maxChars,
                previewText(chunk.text),
            )
        }
        return result.ifEmpty { listOf(chunk) }
    }

    private fun chooseLargeChunkSplit(window: String, minSide: Int): Int? {
        val patterns = listOf(
            Regex("""[.!?]+(?:["»”']+)?\s+"""),
            Regex("""[,;:—–]\s+"""),
            Regex("""\s+"""),
        )
        for (pattern in patterns) {
            pattern.findAll(window)
                .map { it.range.last + 1 }
                .filter { it >= minSide && it < window.length }
                .lastOrNull()
                ?.let { return it }
        }
        return null
    }

    private fun mergeNearbySentences(sentences: List<SentenceInfo>): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        val tuning = currentVoiceTuning()
        val preset = prosodyPreset
        val maxShort = minOf(preset.mergeShortCap(mergeShortThreshold), tuning.mergeShortCap)
        val maxTotal = minOf(preset.mergeTotalCap(mergeTotalCap), tuning.mergeTotalCap)
        for (i in 1 until sentences.size) {
            val next = sentences[i]
            val shouldMerge = current.text.length < maxShort &&
                next.text.length < maxShort &&
                (current.text.length + next.text.length) < maxTotal &&
                '\n' !in current.rawText &&
                '\n' !in next.rawText
            current = if (shouldMerge) {
                SentenceInfo(
                    text = "${current.text} ${next.text}".trim(),
                    rawText = "${current.rawText.trimEnd()} ${next.rawText.trimStart()}".trim(),
                    startOffset = current.startOffset,
                    endOffset = next.endOffset,
                    splitDepth = maxOf(current.splitDepth, next.splitDepth),
                    sourceTag = "merged",
                    mergedCount = current.mergedCount + next.mergedCount,
                    pauseAfterMs = next.pauseAfterMs,
                )
            } else {
                merged += current
                next
            }
        }
        merged += current
        return merged
    }

    private fun splitChunkForRecovery(chunk: SentenceInfo): List<SentenceInfo> {
        if (chunk.text.length < MIN_RECOVERY_CHUNK_CHARS) return emptyList()
        val split = chooseRecoverySplitPoint(chunk.rawText) ?: return emptyList()
        val left = buildChunkFromRawText(
            rawText = chunk.rawText.substring(0, split.index),
            globalStartOffset = chunk.startOffset,
            splitDepth = chunk.splitDepth + 1,
            sourceTag = "recovery:${split.reason}",
        )
        val right = buildChunkFromRawText(
            rawText = chunk.rawText.substring(split.index),
            globalStartOffset = chunk.startOffset + split.index,
            splitDepth = chunk.splitDepth + 1,
            sourceTag = "recovery:${split.reason}",
        )
        return listOfNotNull(left, right).takeIf { it.size > 1 } ?: emptyList()
    }

    private fun chooseRecoverySplitPoint(rawText: String): RecoverySplit? {
        val midpoint = rawText.length / 2
        val candidateGroups = listOf(
            "paragraph" to PARAGRAPH_SEPARATOR_REGEX.findAll(rawText).map { it.range.last + 1 }.toList(),
            "sentence" to Regex("""[.!?]+(?:["»”']+)?\s+""").findAll(rawText).map { it.range.last + 1 }.toList(),
            "clause" to Regex("""[;:—–]\s+""").findAll(rawText).map { it.range.last + 1 }.toList(),
            "comma" to Regex(""",\s+""").findAll(rawText).map { it.range.last + 1 }.toList(),
            "space" to Regex("""\s+""").findAll(rawText).map { it.range.last + 1 }.toList(),
        )
        candidateGroups.forEach { (reason, candidates) ->
            pickBalancedSplitIndex(candidates, rawText.length, midpoint)?.let { index ->
                return RecoverySplit(index = index, reason = reason)
            }
        }
        return null
    }

    private fun pickBalancedSplitIndex(
        candidates: List<Int>,
        length: Int,
        midpoint: Int,
    ): Int? {
        return candidates
            .filter { it >= MIN_RECOVERY_SIDE_CHARS && (length - it) >= MIN_RECOVERY_SIDE_CHARS }
            .minByOrNull { abs(it - midpoint) }
    }

    private fun buildChunkFromBounds(
        fullText: String,
        rawStart: Int,
        rawEndExclusive: Int,
        splitDepth: Int = 0,
        sourceTag: String = "sentence",
        mergedCount: Int = 1,
        pauseAfterMs: Long? = null,
    ): SentenceInfo? {
        if (rawStart >= rawEndExclusive) return null
        return buildChunkFromRawText(
            rawText = fullText.substring(rawStart, rawEndExclusive),
            globalStartOffset = rawStart,
            splitDepth = splitDepth,
            sourceTag = sourceTag,
            mergedCount = mergedCount,
            pauseAfterMs = pauseAfterMs,
        )
    }

    private fun buildChunkFromRawText(
        rawText: String,
        globalStartOffset: Int,
        splitDepth: Int = 0,
        sourceTag: String = "sentence",
        mergedCount: Int = 1,
        pauseAfterMs: Long? = null,
    ): SentenceInfo? {
        var start = 0
        var end = rawText.length
        while (start < end && rawText[start].isWhitespace()) start++
        while (end > start && rawText[end - 1].isWhitespace()) end--
        if (start >= end) return null
        val trimmed = rawText.substring(start, end)
        return SentenceInfo(
            text = trimmed,
            rawText = trimmed,
            startOffset = globalStartOffset + start,
            endOffset = globalStartOffset + end,
            splitDepth = splitDepth,
            sourceTag = sourceTag,
            mergedCount = mergedCount,
            pauseAfterMs = pauseAfterMs ?: pauseForChunk(trimmed, isParagraphEnd = false),
        )
    }

    private fun canContinue(sessionId: Long): Boolean {
        return !isPaused && sessionId == playbackSessionId && playbackJob?.isActive != false
    }

    private fun resetDiagnosticsForSession(totalChunks: Int) {
        val pack = resolveUsablePack()
        val voice = pack?.voices?.firstOrNull()
        val preset = prosodyPreset
        _diagnostics.value = PiperPlaybackDiagnostics(
            packId = pack?.packId ?: selectedPackId,
            voiceId = voice?.id ?: diagnostics.value.voiceId,
            voiceLabel = voice?.label ?: diagnostics.value.voiceLabel,
            prosodyPresetKey = preset.storageKey,
            prosodyPresetLabel = preset.displayName,
            noiseScale = preset.noiseScale,
            noiseScaleW = preset.noiseScaleW,
            speechRate = speechRate,
            sherpaThreads = sherpaNumThreads,
            totalChunks = totalChunks,
        )
    }

    private fun markChunkAttempt(chunk: SentenceInfo) {
        _diagnostics.update {
            it.copy(
                lastChunkPreview = previewText(chunk.text),
                lastChunkRange = chunk.range(),
                lastChunkSplitDepth = chunk.splitDepth,
            )
        }
    }

    private fun recordChunkGeneration(chunk: SentenceInfo, attempt: GenerationAttempt) {
        val audioMs = attempt.audio?.let { samples ->
            (samples.size * 1000L / sampleRate).coerceAtLeast(1L)
        }
        _diagnostics.update {
            it.copy(
                lastChunkPreview = previewText(chunk.text),
                lastChunkRange = chunk.range(),
                lastChunkSplitDepth = chunk.splitDepth,
                lastChunkDurationMs = attempt.durationMs,
                lastChunkAudioMs = audioMs,
                lastChunkPrefetched = attempt.fromPrefetch,
                lastPrefetchWaitMs = attempt.prefetchWaitMs,
            )
        }
    }

    private fun recordChunkSuccess(usedRecovery: Boolean) {
        _diagnostics.update {
            it.copy(
                completedChunks = (it.completedChunks + 1).coerceAtMost(it.totalChunks),
                recoveredChunks = if (usedRecovery) it.recoveredChunks + 1 else it.recoveredChunks,
            )
        }
    }

    private fun recordChunkFailure(failure: PiperPlaybackFailure) {
        _diagnostics.update {
            it.copy(
                failedChunks = it.failedChunks + 1,
                lastFailureMessage = failure.message,
                lastFailurePreview = failure.chunkPreview,
                lastFailureRange = failure.chunkRange,
            )
        }
        Timber.e(
            "Piper final failure: voice=%s pack=%s range=%s preview=%s message=%s",
            failure.voiceLabel ?: failure.voiceId,
            failure.packId,
            failure.chunkRange,
            failure.chunkPreview,
            failure.message,
        )
    }

    private fun buildPlaybackFailure(message: String, chunk: SentenceInfo): PiperPlaybackFailure {
        return PiperPlaybackFailure(
            message = message,
            chunkPreview = previewText(chunk.text),
            chunkRange = chunk.range(),
            packId = diagnostics.value.packId,
            voiceId = diagnostics.value.voiceId,
            voiceLabel = diagnostics.value.voiceLabel,
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

    private fun prepareTextForPiper(text: String): String {
        return text
            .replace('\u00A0', ' ')
            .replace(Regex("""(?m)^\s*[IVXLCDM]+\s+(?=[А-ЯЁ])""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[\s*\d{1,4}\s*]"""), " ")
            .replace(Regex("""[{]\s*\d{1,4}\s*[}]"""), " ")
            .replace("…", "...")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace("„", "\"")
            .replace("‚", "'")
            .replace(Regex("""\bи\s+т\.\s*д\.""", RegexOption.IGNORE_CASE), "и так далее")
            .replace(Regex("""\bи\s+т\.\s*п\.""", RegexOption.IGNORE_CASE), "и тому подобное")
            .replace(Regex("""\bт\.\s*е\.""", RegexOption.IGNORE_CASE), "то есть")
            .replace(Regex("""\bт\.\s*к\.""", RegexOption.IGNORE_CASE), "так как")
            .replace(Regex("""\bт\.\s*д\.""", RegexOption.IGNORE_CASE), "так далее")
            .replace(Regex("""\bт\.\s*п\.""", RegexOption.IGNORE_CASE), "тому подобное")
            .replace(Regex("""(?<=\d)\s*г\.""", RegexOption.IGNORE_CASE), " года")
            .replace(Regex("""\bг\.\s*(?=[А-ЯЁ])"""), "город ")
            .replace(Regex("""\bул\.\s*""", RegexOption.IGNORE_CASE), "улица ")
            .replace(Regex("""\bстр\.\s*""", RegexOption.IGNORE_CASE), "страница ")
            .replace(Regex("""\bрис\.\s*""", RegexOption.IGNORE_CASE), "рисунок ")
            .replace(Regex("""\bруб\.\s*""", RegexOption.IGNORE_CASE), "рублей ")
            .replace(Regex("""\bтыс\.\s*""", RegexOption.IGNORE_CASE), "тысяч ")
            .replace(Regex("""\bмлн\.?\s*""", RegexOption.IGNORE_CASE), "миллионов ")
            .replace(Regex("""\bмлрд\.?\s*""", RegexOption.IGNORE_CASE), "миллиардов ")
            .replace(Regex("""\bд-р\s+""", RegexOption.IGNORE_CASE), "доктор ")
            .replace(Regex("""\bдр\.\s*""", RegexOption.IGNORE_CASE), "другие ")
            .replace("№", "номер ")
            .replace("%", " процентов ")
            .replace("&", " и ")
            .replace(Regex("""\s*[—–]\s*"""), " — ")
            .replace(Regex("""\s*/\s*"""), " или ")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }

    private fun currentVoiceTuning(): PiperVoiceTuning {
        val base = when (diagnostics.value.voiceId?.lowercase()) {
            "irina" -> PiperVoiceTuning(
                mergeShortCap = 165,
                mergeTotalCap = 250,
                sentencePauseMs = 125L,
                paragraphPauseMs = 240L,
            )
            "denis" -> PiperVoiceTuning(
                mergeShortCap = 150,
                mergeTotalCap = 235,
                sentencePauseMs = 110L,
                paragraphPauseMs = 220L,
            )
            "dmitri" -> PiperVoiceTuning(
                mergeShortCap = 155,
                mergeTotalCap = 245,
                sentencePauseMs = 115L,
                paragraphPauseMs = 225L,
            )
            "ruslan" -> PiperVoiceTuning(
                mergeShortCap = 175,
                mergeTotalCap = 275,
                sentencePauseMs = 105L,
                paragraphPauseMs = 205L,
            )
            "burunov" -> PiperVoiceTuning(
                mergeShortCap = 120,
                mergeTotalCap = 190,
                sentencePauseMs = 85L,
                paragraphPauseMs = 170L,
            )
            else -> PiperVoiceTuning(
                mergeShortCap = 170,
                mergeTotalCap = 260,
                sentencePauseMs = 115L,
                paragraphPauseMs = 220L,
            )
        }
        val preset = prosodyPreset
        return PiperVoiceTuning(
            mergeShortCap = preset.mergeShortCap(base.mergeShortCap),
            mergeTotalCap = preset.mergeTotalCap(base.mergeTotalCap),
            sentencePauseMs = preset.sentencePauseMs(base.sentencePauseMs),
            paragraphPauseMs = preset.paragraphPauseMs(base.paragraphPauseMs),
        )
    }

    private fun pauseForChunk(rawText: String, isParagraphEnd: Boolean): Long {
        val tuning = currentVoiceTuning()
        if (isParagraphEnd) return tuning.paragraphPauseMs
        val trimmed = rawText.trimEnd()
        return when {
            trimmed.endsWith("?") || trimmed.endsWith("!") -> tuning.sentencePauseMs + 25L
            trimmed.endsWith(":") || trimmed.endsWith(";") -> tuning.sentencePauseMs + 10L
            else -> tuning.sentencePauseMs
        }
    }

    private suspend fun applyChunkPause(chunk: SentenceInfo, sessionId: Long) {
        val pauseMs = chunk.pauseAfterMs.coerceAtLeast(0L)
        if (pauseMs <= 0L || !canContinue(sessionId)) return
        delay(pauseMs)
    }
}
