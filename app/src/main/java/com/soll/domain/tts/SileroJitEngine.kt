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
import kotlinx.coroutines.Job
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

    companion object {
        private const val MIN_MODEL_BYTES = 1_000_000L
        private const val MAX_RECOVERY_SPLIT_DEPTH = 3
        private const val MIN_RECOVERY_CHUNK_CHARS = 36
        private const val MIN_RECOVERY_SIDE_CHARS = 18
        private const val PIPER_MAX_MERGE_SHORT = 180
        private const val PIPER_MAX_MERGE_TOTAL = 300
        private const val CHUNK_PREVIEW_LIMIT = 88

        private val VOICE_LABELS = mapOf(
            "irina" to "Ирина (ж)",
            "denis" to "Денис (м)",
            "dmitri" to "Дмитрий (м)",
            "ruslan" to "Руслан (м)",
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
    ) {
        fun range(): IntRange = IntRange(startOffset, (endOffset - 1).coerceAtLeast(startOffset))
    }

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

    fun setUseV5(enabled: Boolean) {}
    fun setV5SpeakerId(id: Int) {}

    fun setSelectedPackId(packId: String?) {
        if (selectedPackId == packId) return
        selectedPackId = packId
        _diagnostics.update { it.copy(packId = packId) }
        invalidateRuntime()
    }

    suspend fun initialize(): TtsPrepareResult = withContext(Dispatchers.IO) {
        val pack = resolveUsablePack()
            ?: return@withContext failedPrepare("Не найден полный Piper/Sherpa pack в локальной папке tts")

        val root = File(pack.rootDir)
        val tokensFile = File(root, "tokens.txt")
        val dataDir = File(root, "espeak-ng-data")
        if (!tokensFile.exists()) {
            return@withContext failedPrepare("В Piper pack нет tokens.txt", root.absolutePath)
        }
        if (!dataDir.isDirectory) {
            return@withContext failedPrepare("В Piper pack нет espeak-ng-data", root.absolutePath)
        }
        val modelFile = resolveVoiceModelFile(root)
            ?: return@withContext failedPrepare("Не найден ONNX-файл Piper в выбранном pack", root.absolutePath)
        if (modelFile.length() < MIN_MODEL_BYTES) {
            return@withContext failedPrepare(
                "ONNX-файл Piper слишком маленький: ${modelFile.name}",
                modelFile.absolutePath,
            )
        }

        val resolvedVoiceId = pack.voices.firstOrNull()?.id ?: detectVoiceId(modelFile)
        val resolvedVoiceLabel = pack.voices.firstOrNull()?.label ?: resolvedVoiceId?.let(::voiceLabel) ?: modelFile.name

        val signature = "${root.absolutePath}|${modelFile.absolutePath}|$sherpaNumThreads"
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
                    speechRate = speechRate,
                    sherpaThreads = sherpaNumThreads,
                )
            }
            Timber.d(
                "Piper/Sherpa ready: model=%s voice=%s sampleRate=%d threads=%d selectedPack=%s",
                modelFile.absolutePath,
                resolvedVoiceLabel,
                sampleRate,
                sherpaNumThreads,
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
        Timber.d(
            "Piper speakChapter: chunks=%d pack=%s voice=%s rate=%.2f threads=%d",
            sentences.size,
            diagnostics.value.packId,
            diagnostics.value.voiceLabel,
            speechRate,
            sherpaNumThreads,
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
            try {
                while (currentSentenceIndex < sentences.size && isActive && !isPaused && sessionId == playbackSessionId) {
                    val sentence = sentences[currentSentenceIndex]
                    val outcome = playChunkWithRecovery(
                        chunk = sentence,
                        chunkIndex = currentSentenceIndex,
                        sessionId = sessionId,
                    )
                    when (outcome.status) {
                        ChunkPlayStatus.SUCCESS -> {
                            recordChunkSuccess(outcome.usedRecovery)
                            if (!isPaused && sessionId == playbackSessionId) {
                                currentSentenceIndex++
                            }
                        }

                        ChunkPlayStatus.INTERRUPTED -> break

                        ChunkPlayStatus.FAILED -> {
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
            }
        }
    }

    private suspend fun playChunkWithRecovery(
        chunk: SentenceInfo,
        chunkIndex: Int,
        sessionId: Long,
    ): ChunkPlayOutcome {
        if (!canContinue(sessionId)) return ChunkPlayOutcome(ChunkPlayStatus.INTERRUPTED)

        markChunkAttempt(chunk)
        _currentWordRange.value = chunk.range()

        val attempt = generateAudio(chunk, chunkIndex)
        val audio = attempt.audio
        if (audio != null && audio.isNotEmpty()) {
            if (!canContinue(sessionId)) return ChunkPlayOutcome(ChunkPlayStatus.INTERRUPTED)
            val fullyPlayed = coroutineScope {
                val wordJob = launch(Dispatchers.IO) { trackWordsInSentence(chunk, audio.size) }
                try {
                    playAudioBlocking(audio, sessionId)
                } finally {
                    wordJob.cancel()
                }
            }
            return if (fullyPlayed) {
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
            val audio = engine.generate(text = chunk.text, sid = 0, speed = speed).samples
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
                    chunk.text.length,
                    audio.size,
                    durationMs,
                    chunk.mergedCount,
                    chunk.sourceTag,
                    previewText(chunk.text),
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

    private fun playAudioBlocking(audioData: FloatArray, sessionId: Long): Boolean {
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
        track.write(shorts, 0, shorts.size)
        track.play()
        val totalMs = (shorts.size * 1000L / sampleRate).coerceAtLeast(1L)
        var elapsed = 0L
        while (elapsed < totalMs && canContinue(sessionId)) {
            val step = minOf(20L, totalMs - elapsed)
            Thread.sleep(step)
            elapsed += step
        }
        try {
            track.stop()
            track.release()
        } catch (_: Exception) {
        }
        audioTrack = null
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
            )?.let(chunks::add)
            localStart = localEnd
        }
        if (localStart < paragraph.length) {
            buildChunkFromBounds(
                fullText = fullText,
                rawStart = paragraphStart + localStart,
                rawEndExclusive = paragraphEndExclusive,
            )?.let(chunks::add)
        }
        return chunks
    }

    private fun mergeNearbySentences(sentences: List<SentenceInfo>): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        val maxShort = mergeShortThreshold.coerceAtMost(PIPER_MAX_MERGE_SHORT)
        val maxTotal = mergeTotalCap.coerceAtMost(PIPER_MAX_MERGE_TOTAL)
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
    ): SentenceInfo? {
        if (rawStart >= rawEndExclusive) return null
        return buildChunkFromRawText(
            rawText = fullText.substring(rawStart, rawEndExclusive),
            globalStartOffset = rawStart,
            splitDepth = splitDepth,
            sourceTag = sourceTag,
            mergedCount = mergedCount,
        )
    }

    private fun buildChunkFromRawText(
        rawText: String,
        globalStartOffset: Int,
        splitDepth: Int = 0,
        sourceTag: String = "sentence",
        mergedCount: Int = 1,
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
        )
    }

    private fun canContinue(sessionId: Long): Boolean {
        return !isPaused && sessionId == playbackSessionId && playbackJob?.isActive != false
    }

    private fun resetDiagnosticsForSession(totalChunks: Int) {
        val pack = resolveUsablePack()
        val voice = pack?.voices?.firstOrNull()
        _diagnostics.value = PiperPlaybackDiagnostics(
            packId = pack?.packId ?: selectedPackId,
            voiceId = voice?.id ?: diagnostics.value.voiceId,
            voiceLabel = voice?.label ?: diagnostics.value.voiceLabel,
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
}
