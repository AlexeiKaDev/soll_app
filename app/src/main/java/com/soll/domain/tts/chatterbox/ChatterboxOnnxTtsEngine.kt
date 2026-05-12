package com.soll.domain.tts.chatterbox

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Half
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.book.TtsPrepareResult
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.TtsPackEngineFamily
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ChatterboxOnnxTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLibrary: TtsPackLibrary,
) {
    private data class SentenceInfo(
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
        val splitDepth: Int = 0,
        val sourceTag: String = "sentence",
    ) {
        fun range(): IntRange = IntRange(startOffset, endOffset)
    }

    private data class FloatBlob(
        val data: FloatArray,
        val shape: LongArray,
    )

    private data class LongBlob(
        val data: LongArray,
        val shape: LongArray,
    )

    private data class LanguageModelLayout(
        val pastInputNames: List<String>,
        val pastOutputNames: List<String>,
        val logitsOutputName: String?,
        val numLayers: Int,
        val numKeyValueHeads: Int,
        val headDim: Int,
        val pastTensorType: OnnxJavaType,
        val inputsEmbedsType: OnnxJavaType,
    )

    private data class RuntimePack(
        val packId: String,
        val rootDir: File,
        val runtimeFamily: String,
        val precision: String,
        val speechEncoderPath: File,
        val embedTokensPath: File,
        val conditionalDecoderPath: File,
        val languageModelPath: File,
        val tokenizerPath: File,
        val defaultVoicePath: File,
    )

    private data class Sessions(
        val speechEncoder: OrtSession,
        val embedTokens: OrtSession,
        val languageModel: OrtSession,
        val conditionalDecoder: OrtSession,
        val layout: LanguageModelLayout,
        val speakerEmbeddingsType: OnnxJavaType,
        val speakerFeaturesType: OnnxJavaType,
    )

    private enum class ChunkPlayStatus {
        SUCCESS,
        INTERRUPTED,
        FAILED,
    }

    private data class ChunkPlayOutcome(
        val status: ChunkPlayStatus,
        val failure: ChatterboxPlaybackFailure? = null,
    )

    private data class GeneratedSpeech(
        val speechTokens: LongArray,
        val tokenCount: Int,
        val durationMs: Long,
    )

    private data class GenerationAttempt(
        val audio: FloatArray? = null,
        val durationMs: Long = 0L,
        val generatedTokens: Int = 0,
        val errorMessage: String? = null,
    )

    private val env get() = OrtEnvironment.getEnvironment()
    private val sessionLock = Any()
    private val initializeMutex = Mutex()

    private var sessions: Sessions? = null
    private var runtimePack: RuntimePack? = null
    private var sessionStale: Boolean = false

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isPaused = false
    private var currentSentenceIndex = 0
    private var sentences: List<SentenceInfo> = emptyList()
    private var chapterFinishedCallback: (() -> Unit)? = null
    @Volatile
    private var playbackSessionId: Long = 0L

    private var speechRate = 1.0f
    private var exaggeration = 0.5f
    private var ortIntraThreads = 2
    private var mergeShortThreshold = 170
    private var mergeTotalCap = 280
    private val sampleRate = 24_000

    private var selectedPackId: String? = null
    private var selectedVoiceId: String? = null
    private var tokenizerProfile: ChatterboxTokenizer.Profile? = null
    private var languageId: String = "ru"
    private var referenceVoiceSamples: FloatArray = floatArrayOf()
    private var referenceVoicePath: String? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private val _diagnostics = MutableStateFlow(ChatterboxPlaybackDiagnostics())
    val diagnostics: StateFlow<ChatterboxPlaybackDiagnostics> = _diagnostics.asStateFlow()

    private val _playbackFailures = MutableSharedFlow<ChatterboxPlaybackFailure>(extraBufferCapacity = 2)
    val playbackFailures: SharedFlow<ChatterboxPlaybackFailure> = _playbackFailures.asSharedFlow()

    fun isModelDownloaded(): Boolean {
        return packLibrary.findBestPack(TtsEngineType.CHATTERBOX)?.isRunnable == true
    }

    fun setSelectedPackId(packId: String?) {
        selectedPackId = packId
    }

    fun voiceOptions(): List<TtsVoiceOption> {
        val pack = resolveCandidatePacks().firstOrNull()
            ?: return emptyList()
        return pack.voices.map { voice -> TtsVoiceOption(voice.id, voice.label) }
    }

    fun setSelectedVoiceId(voiceId: String?) {
        selectedVoiceId = voiceId?.takeIf { it.isNotBlank() }
        _diagnostics.value = diagnostics.value.copy(voiceId = selectedVoiceId)
        val runtime = runtimePack ?: return
        runCatching {
            val voice = resolveReferenceVoiceFile(runtime.rootDir, selectedVoiceId)
            if (voice != null) {
                referenceVoiceSamples = ChatterboxWaveReader.readMonoFloat(voice, targetSampleRate = sampleRate)
                referenceVoicePath = voice.absolutePath
                _diagnostics.value = diagnostics.value.copy(
                    voiceId = selectedVoiceId ?: voice.nameWithoutExtension,
                    referenceVoicePath = voice.absolutePath,
                )
                Timber.i("Chatterbox switched reference voice to %s", voice.absolutePath)
            }
        }.onFailure { error ->
            Timber.w(error, "Chatterbox: failed to switch reference voice '%s'", selectedVoiceId)
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        _diagnostics.value = diagnostics.value.copy(speechRate = speechRate)
    }

    fun getOrtIntraThreads(): Int = ortIntraThreads

    fun applyOrtIntraThreadsTunable(value: Float) {
        val v = value.roundToInt().coerceIn(1, 4)
        if (v == ortIntraThreads && !sessionStale) return
        ortIntraThreads = v
        if (sessions != null) sessionStale = true
        _diagnostics.value = diagnostics.value.copy(ortThreads = ortIntraThreads)
    }

    fun getExaggeration(): Float = exaggeration

    fun applyExaggeration(value: Float) {
        exaggeration = value.coerceIn(0.3f, 0.9f)
        _diagnostics.value = diagnostics.value.copy(exaggeration = exaggeration)
    }

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        val (a, b) = TtsBookPerformanceProfile.chunkMergeLimits(profile)
        mergeShortThreshold = maxOf(120, a - 30)
        mergeTotalCap = maxOf(180, b - 80)
        applyOrtIntraThreadsTunable(TtsBookPerformanceProfile.ortIntraThreads(profile).toFloat())
    }

    suspend fun initialize(): TtsPrepareResult = withContext(Dispatchers.IO) { initializeMutex.withLock {
        synchronized(sessionLock) {
            val current = runtimePack
            if (_isReady.value && !sessionStale && current != null && sessions != null &&
                (selectedPackId == null || selectedPackId == current.packId)
            ) {
                return@withLock TtsPrepareResult(
                    success = true,
                    engineType = TtsEngineType.CHATTERBOX,
                    resolvedPackPath = current.rootDir.absolutePath,
                )
            }
        }
        val candidates = resolveCandidatePacks()
        if (candidates.isEmpty()) {
            return@withLock failedPrepare("Не найден pack Chatterbox Multilingual в локальной папке tts")
        }
        var lastFailure: TtsPrepareResult? = null
        for (pack in candidates) {
            val result = initializePack(pack)
            if (result.success) return@withLock result
            lastFailure = result
            Timber.w(
                "Chatterbox candidate failed pack=%s precision=%s path=%s message=%s",
                pack.packId,
                pack.precision,
                pack.rootDir,
                result.message,
            )
        }
        lastFailure ?: failedPrepare("Chatterbox не смог инициализировать ни один подходящий pack")
    } }

    private fun initializePack(pack: DetectedTtsPack): TtsPrepareResult {
        return try {
            Timber.i(
                "Chatterbox init candidate pack=%s precision=%s source=%s path=%s",
                pack.packId,
                pack.precision,
                pack.sourceType,
                pack.rootDir,
            )
            val runtime = resolveRuntimePack(pack.packId, File(pack.rootDir), pack.runtimeFamily, pack.precision)
                ?: return failedPrepare(
                    message = pack.reason ?: "Pack Chatterbox не содержит нужные ONNX-графы",
                    path = pack.rootDir,
                )
            if (runtime.runtimeFamily != "chatterbox_v1") {
                return failedPrepare(
                    message = "Поддержан только Chatterbox Multilingual (runtime=${runtime.runtimeFamily})",
                    path = runtime.rootDir.absolutePath,
                )
            }
            val tokenizer = ChatterboxTokenizer.loadFromPack(runtime.rootDir)
                ?: return failedPrepare(
                    message = "В pack Chatterbox нет tokenizer.json",
                    path = runtime.rootDir.absolutePath,
                )
            val voiceFile = resolveReferenceVoiceFile(runtime.rootDir, selectedVoiceId) ?: runtime.defaultVoicePath
            val voiceSamples = ChatterboxWaveReader.readMonoFloat(voiceFile, targetSampleRate = sampleRate)
            val freshSessions = buildSessions(runtime)
            synchronized(sessionLock) {
                closeSessionsLocked()
                runtimePack = runtime
                tokenizerProfile = tokenizer
                referenceVoiceSamples = voiceSamples
                referenceVoicePath = voiceFile.absolutePath
                languageId = "ru"
                sessions = freshSessions
                sessionStale = false
            }
            if (selectedVoiceId == null) {
                selectedVoiceId = voiceFile.nameWithoutExtension
            }
            resetDiagnosticsForPack(runtime, voiceFile)
            logRuntimeProfile()
            _isReady.value = true
            TtsPrepareResult(
                success = true,
                engineType = TtsEngineType.CHATTERBOX,
                resolvedPackPath = runtime.rootDir.absolutePath,
            )
        } catch (e: Exception) {
            Timber.e(e, "Chatterbox init failed for pack=%s precision=%s", pack.packId, pack.precision)
            _isReady.value = false
            TtsPrepareResult(
                success = false,
                engineType = TtsEngineType.CHATTERBOX,
                resolvedPackPath = pack.rootDir,
                message = e.message ?: "Chatterbox init failed",
            )
        }
    }

    private fun resolveCandidatePacks(): List<DetectedTtsPack> {
        val runnable = packLibrary.listPacksFor(TtsEngineType.CHATTERBOX)
            .filter {
                it.engineFamily == TtsPackEngineFamily.CHATTERBOX &&
                    it.isRunnable &&
                    it.isRussianCapable &&
                    it.runtimeFamily == "chatterbox_v1"
            }
            .sortedWith(chatterboxPackComparator())
        val selected = selectedPackId
            ?.let(packLibrary::findPackById)
            ?.takeIf {
                it.engineFamily == TtsPackEngineFamily.CHATTERBOX &&
                    it.isRunnable &&
                    it.isRussianCapable &&
                    it.runtimeFamily == "chatterbox_v1"
            }
        val best = runnable.firstOrNull()
        if (selected != null && best != null && chatterboxPackRank(best) < chatterboxPackRank(selected)) {
            Timber.i(
                "Chatterbox selected pack=%s precision=%s moved after preferred pack=%s precision=%s",
                selected.packId,
                selected.precision,
                best.packId,
                best.precision,
            )
        }
        val ordered = if (selected != null && (best == null || chatterboxPackRank(selected) <= chatterboxPackRank(best))) {
            listOf(selected) + runnable
        } else {
            runnable + listOfNotNull(selected)
        }
        return ordered.distinctBy { it.packId }
    }

    private fun chatterboxPackComparator(): Comparator<DetectedTtsPack> =
        compareBy<DetectedTtsPack> { chatterboxPackRank(it) }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.packId }

    private fun chatterboxPackRank(pack: DetectedTtsPack): Int {
        return when (pack.precision?.lowercase()) {
            "int4" -> 0
            "fp16" -> 20
            "fp32" -> 40
            else -> 60
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
        Timber.i(
            "Chatterbox speakChapter chunks=%d chars=%d pack=%s precision=%s",
            sentences.size,
            text.length,
            runtimePack?.packId,
            runtimePack?.precision,
        )
        resume()
    }

    suspend fun synthesize(text: String): ChatterboxSynthesisResult = withContext(Dispatchers.IO) {
        val attempt = generateAudio(text)
        val audio = attempt.audio ?: error(attempt.errorMessage ?: "Chatterbox не смог синтезировать текст")
        ChatterboxSynthesisResult(
            audio = audio,
            sampleRate = sampleRate,
            generatedTokens = attempt.generatedTokens,
            durationMs = attempt.durationMs,
            voiceId = selectedVoiceId,
            referenceVoicePath = referenceVoicePath,
        )
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
                    val outcome = playSentenceWithRecovery(sentence, sessionId)
                    when (outcome.status) {
                        ChunkPlayStatus.SUCCESS -> if (!isPaused) currentSentenceIndex++
                        ChunkPlayStatus.INTERRUPTED -> return@launch
                        ChunkPlayStatus.FAILED -> {
                            val failure = outcome.failure ?: buildPlaybackFailure(
                                message = "Chatterbox не смог дочитать фрагмент",
                                sentence = sentence,
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
                    chapterFinishedCallback?.let { callback ->
                        withContext(Dispatchers.Main) { callback() }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Chatterbox playback error")
                }
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

    fun shutdown() {
        stop()
        synchronized(sessionLock) {
            closeSessionsLocked()
            runtimePack = null
            tokenizerProfile = null
            referenceVoiceSamples = floatArrayOf()
            referenceVoicePath = null
            sessionStale = false
        }
        _isReady.value = false
    }

    private suspend fun playSentenceWithRecovery(
        sentence: SentenceInfo,
        sessionId: Long,
    ): ChunkPlayOutcome {
        if (!canContinue(sessionId)) return ChunkPlayOutcome(status = ChunkPlayStatus.INTERRUPTED)
        markChunkAttempt(sentence)
        _currentWordRange.value = sentence.range()
        val attempt = generateAudio(sentence.text)
        if (attempt.audio != null && attempt.audio.size > 100) {
            if (!canContinue(sessionId)) {
                return ChunkPlayOutcome(status = ChunkPlayStatus.INTERRUPTED)
            }
            coroutineScope {
                val wordTracker = launch { trackWords(sentence, attempt.audio.size) }
                try {
                    playAudio(attempt.audio)
                } finally {
                    wordTracker.cancel()
                }
            }
            recordChunkSuccess(
                usedRecovery = sentence.splitDepth > 0,
                durationMs = attempt.durationMs,
                generatedTokens = attempt.generatedTokens,
            )
            return ChunkPlayOutcome(
                status = if (canContinue(sessionId)) ChunkPlayStatus.SUCCESS else ChunkPlayStatus.INTERRUPTED,
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
            "Chatterbox recovery split depth=%d reason=%s preview=%s",
            sentence.splitDepth,
            splitReason,
            previewText(sentence.text),
        )
        for (split in splits) {
            val childOutcome = playSentenceWithRecovery(split, sessionId)
            when (childOutcome.status) {
                ChunkPlayStatus.SUCCESS -> Unit
                ChunkPlayStatus.INTERRUPTED -> return childOutcome
                ChunkPlayStatus.FAILED -> return ChunkPlayOutcome(
                    status = ChunkPlayStatus.FAILED,
                    failure = childOutcome.failure ?: buildPlaybackFailure(
                        message = "Не удалось озвучить дочерний фрагмент после recovery split",
                        sentence = split,
                    ),
                )
            }
        }
        return ChunkPlayOutcome(status = ChunkPlayStatus.SUCCESS)
    }

    private fun generateAudio(text: String): GenerationAttempt {
        if (text.isBlank()) return GenerationAttempt(errorMessage = "Пустой chunk")
        val profile = tokenizerProfile ?: return GenerationAttempt(errorMessage = "Tokenizer Chatterbox не инициализирован")
        val prepared = prepareTextForChatterbox(text)
        val inputIds = ChatterboxTokenizer.encodeText(prepared, profile, languageId = languageId)
        if (inputIds.isEmpty()) return GenerationAttempt(errorMessage = "Пустая последовательность токенов")
        val runtime = synchronized(sessionLock) {
            ensureSessionsFreshLocked()
            sessions
        } ?: return GenerationAttempt(errorMessage = "Chatterbox runtime не инициализирован")

        val startedAt = SystemClock.elapsedRealtime()
        val activePack = runtimePack
        Timber.i(
            "Chatterbox synth start chars=%d tokens=%d pack=%s precision=%s preview=%s",
            prepared.length,
            inputIds.size,
            activePack?.packId,
            activePack?.precision,
            previewText(text),
        )
        return runCatching {
            val promptSpeechTokens = runSpeechPrompt(runtime, inputIds, profile)
            val decoderAudio = runConditionalDecoder(runtime, promptSpeechTokens.speechTokens)
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            Timber.i(
                "Chatterbox synth done durationMs=%d audioSamples=%d generatedTokens=%d pack=%s precision=%s",
                durationMs,
                decoderAudio.size,
                promptSpeechTokens.tokenCount,
                activePack?.packId,
                activePack?.precision,
            )
            GenerationAttempt(
                audio = resampleForSpeechRate(decoderAudio),
                durationMs = durationMs,
                generatedTokens = promptSpeechTokens.tokenCount,
            )
        }.getOrElse { error ->
            Timber.e(
                error,
                "Chatterbox ONNX inference failed pack=%s precision=%s chars=%d tokens=%d preview=%s",
                activePack?.packId,
                activePack?.precision,
                prepared.length,
                inputIds.size,
                previewText(text),
            )
            GenerationAttempt(
                durationMs = SystemClock.elapsedRealtime() - startedAt,
                errorMessage = error.message ?: "Chatterbox ONNX inference failed",
            )
        }
    }

    private fun runSpeechPrompt(
        runtime: Sessions,
        inputIds: LongArray,
        profile: ChatterboxTokenizer.Profile,
    ): GeneratedSpeech {
        val positionIds = buildPositionIds(inputIds, profile.startSpeechTokenId.toLong())
        val audioInput = createFloatTensor(referenceVoiceSamples, longArrayOf(1, referenceVoiceSamples.size.toLong()))
        audioInput.use { audioTensor ->
            val speechResult = runtime.speechEncoder.run(mapFeedByAliases(runtime.speechEncoder, mapOf("audio_values" to audioTensor)))
            speechResult.use { encoderOutput ->
                val condEmb = tensorToFloatBlob(encoderOutput.get(0) as OnnxTensor)
                val promptTokens = tensorToLongBlob(encoderOutput.get(1) as OnnxTensor)
                val speakerEmb = tensorToFloatBlob(encoderOutput.get(2) as OnnxTensor)
                val speakerFeat = tensorToFloatBlob(encoderOutput.get(3) as OnnxTensor)

                val initialEmbeddingBlob = createInitialEmbeddingBlob(runtime, inputIds, positionIds)
                val combinedEmbeddings = concatEmbeddings(condEmb, initialEmbeddingBlob)
                val generatedTokens = sampleSpeechTokens(runtime, combinedEmbeddings, profile.startSpeechTokenId.toLong(), profile.stopSpeechTokenId.toLong())

                val speechTokens = LongArray(promptTokens.data.size + generatedTokens.size)
                System.arraycopy(promptTokens.data, 0, speechTokens, 0, promptTokens.data.size)
                System.arraycopy(generatedTokens, 0, speechTokens, promptTokens.data.size, generatedTokens.size)
                latestSpeakerEmbeddings = speakerEmb
                latestSpeakerFeatures = speakerFeat
                return GeneratedSpeech(
                    speechTokens = speechTokens,
                    tokenCount = generatedTokens.size,
                    durationMs = 0L,
                )
            }
        }
    }

    @Volatile
    private var latestSpeakerEmbeddings: FloatBlob? = null

    @Volatile
    private var latestSpeakerFeatures: FloatBlob? = null

    private fun runConditionalDecoder(
        runtime: Sessions,
        speechTokens: LongArray,
    ): FloatArray {
        val speakerEmb = latestSpeakerEmbeddings ?: error("Chatterbox speaker embeddings отсутствуют")
        val speakerFeat = latestSpeakerFeatures ?: error("Chatterbox speaker features отсутствуют")
        createLongTensor(speechTokens, longArrayOf(1, speechTokens.size.toLong())).use { speechTokensTensor ->
            createTypedFloatTensor(speakerEmb, runtime.speakerEmbeddingsType).use { speakerEmbTensor ->
                createTypedFloatTensor(speakerFeat, runtime.speakerFeaturesType).use { speakerFeatTensor ->
                    val feeds = mapFeedByAliases(
                        runtime.conditionalDecoder,
                        mapOf(
                            "speech_tokens" to speechTokensTensor,
                            "speaker_embeddings" to speakerEmbTensor,
                            "speaker_features" to speakerFeatTensor,
                        ),
                    )
                    runtime.conditionalDecoder.run(feeds).use { decoderResult ->
                        val waveformTensor = firstTensor(decoderResult) ?: error("Chatterbox decoder не вернул waveform")
                        val waveform = tensorToFloatArray(waveformTensor)
                        if (waveform.isEmpty()) error("Chatterbox decoder вернул пустой waveform")
                        return waveform
                    }
                }
            }
        }
    }

    private fun createInitialEmbeddingBlob(
        runtime: Sessions,
        inputIds: LongArray,
        positionIds: LongArray,
    ): FloatBlob {
        createLongTensor(inputIds, longArrayOf(1, inputIds.size.toLong())).use { inputIdsTensor ->
            createLongTensor(positionIds, longArrayOf(1, positionIds.size.toLong())).use { positionIdsTensor ->
                OnnxTensor.createTensor(env, floatArrayOf(exaggeration)).use { exaggerationTensor ->
                    val feeds = mapFeedByAliases(
                        runtime.embedTokens,
                        mapOf(
                            "input_ids" to inputIdsTensor,
                            "position_ids" to positionIdsTensor,
                            "exaggeration" to exaggerationTensor,
                        ),
                    )
                    runtime.embedTokens.run(feeds).use { embedResult ->
                        val tensor = firstTensor(embedResult) ?: error("Chatterbox embed_tokens не вернул output")
                        return tensorToFloatBlob(tensor)
                    }
                }
            }
        }
    }

    private fun sampleSpeechTokens(
        runtime: Sessions,
        initialEmbeddings: FloatBlob,
        startSpeechTokenId: Long,
        stopSpeechTokenId: Long,
    ): LongArray {
        val layout = runtime.layout
        val generated = mutableListOf(startSpeechTokenId)
        var attentionLength = initialEmbeddings.shape[1].toInt()
        var tokenPosition = 1L

        var currentInputsEmbeds = createTypedFloatTensor(initialEmbeddings, layout.inputsEmbedsType)
        var currentPastOwner: OrtSession.Result? = null
        var currentPastInputs = createInitialPastKeyValues(layout)

        try {
            for (step in 0 until computeMaxNewTokens()) {
                val attentionMask = createLongTensor(LongArray(attentionLength) { 1L }, longArrayOf(1, attentionLength.toLong()))
                try {
                    val feeds = linkedMapOf<String, OnnxTensor>()
                    feeds.putAll(
                        mapFeedByAliases(
                            runtime.languageModel,
                            mapOf(
                                "inputs_embeds" to currentInputsEmbeds,
                                "attention_mask" to attentionMask,
                            ),
                        ),
                    )
                    layout.pastInputNames.forEach { name ->
                        feeds[name] = currentPastInputs.getValue(name)
                    }
                    val result = runtime.languageModel.run(feeds)
                    val logitsTensor = findLogitsTensor(result, layout)
                    if (logitsTensor == null) {
                        result.close()
                        error("Chatterbox language_model не вернул logits")
                    }
                    val nextToken = selectNextToken(logitsTensor, generated)
                    val newPastInputs = extractPastInputs(result, layout)

                    currentInputsEmbeds.close()
                    if (currentPastOwner == null) {
                        currentPastInputs.values.forEach { it.close() }
                    } else {
                        currentPastOwner.close()
                    }
                    currentPastOwner = result
                    currentPastInputs = newPastInputs

                    generated += nextToken
                    if (nextToken == stopSpeechTokenId) {
                        break
                    }

                    attentionLength += 1
                    currentInputsEmbeds = createNextTokenEmbedding(runtime, nextToken, tokenPosition)
                    tokenPosition += 1
                } finally {
                    attentionMask.close()
                }
            }
        } finally {
            currentInputsEmbeds.close()
            if (currentPastOwner == null) {
                currentPastInputs.values.forEach { it.close() }
            } else {
                currentPastOwner.close()
            }
        }

        return generated
            .drop(1)
            .takeWhile { it != stopSpeechTokenId }
            .toLongArray()
    }

    private fun createNextTokenEmbedding(
        runtime: Sessions,
        tokenId: Long,
        positionId: Long,
    ): OnnxTensor {
        createLongTensor(longArrayOf(tokenId), longArrayOf(1, 1)).use { inputIdsTensor ->
            createLongTensor(longArrayOf(positionId), longArrayOf(1, 1)).use { positionIdsTensor ->
                OnnxTensor.createTensor(env, floatArrayOf(exaggeration)).use { exaggerationTensor ->
                    val feeds = mapFeedByAliases(
                        runtime.embedTokens,
                        mapOf(
                            "input_ids" to inputIdsTensor,
                            "position_ids" to positionIdsTensor,
                            "exaggeration" to exaggerationTensor,
                        ),
                    )
                    runtime.embedTokens.run(feeds).use { result ->
                        val tensor = firstTensor(result) ?: error("Chatterbox embed_tokens не вернул next-token embedding")
                        val blob = tensorToFloatBlob(tensor)
                        return createTypedFloatTensor(blob, runtime.layout.inputsEmbedsType)
                    }
                }
            }
        }
    }

    private fun selectNextToken(
        logitsTensor: OnnxTensor,
        generatedTokens: List<Long>,
    ): Long {
        val logits = tensorToFloatArray(logitsTensor)
        val shape = logitsTensor.info.shape
        val vocabSize = shape.lastOrNull()?.takeIf { it > 0 }?.toInt() ?: logits.size
        val lastSliceStart = (logits.size - vocabSize).coerceAtLeast(0)
        val nextTokenLogits = logits.copyOfRange(lastSliceStart, logits.size)
        val seen = generatedTokens.distinct().map { it.toInt() }
        for (token in seen) {
            if (token !in nextTokenLogits.indices) continue
            val score = nextTokenLogits[token]
            nextTokenLogits[token] = if (score < 0f) score * REPETITION_PENALTY else score / REPETITION_PENALTY
        }
        var bestIndex = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (index in nextTokenLogits.indices) {
            if (nextTokenLogits[index] > bestValue) {
                bestValue = nextTokenLogits[index]
                bestIndex = index
            }
        }
        return bestIndex.toLong()
    }

    private fun buildPositionIds(
        inputIds: LongArray,
        startSpeechTokenId: Long,
    ): LongArray {
        return LongArray(inputIds.size) { index ->
            if (inputIds[index] >= startSpeechTokenId) {
                0L
            } else {
                index.toLong() - 1L
            }
        }
    }

    private fun createInitialPastKeyValues(layout: LanguageModelLayout): Map<String, OnnxTensor> {
        val shape = longArrayOf(1, layout.numKeyValueHeads.toLong(), 0L, layout.headDim.toLong())
        return layout.pastInputNames.associateWith {
            when (layout.pastTensorType) {
                OnnxJavaType.FLOAT16 -> OnnxTensor.createTensor(
                    env,
                    ShortBuffer.wrap(ShortArray(0)),
                    shape,
                    OnnxJavaType.FLOAT16,
                )
                else -> OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(0)), shape)
            }
        }
    }

    private fun extractPastInputs(
        result: OrtSession.Result,
        layout: LanguageModelLayout,
    ): Map<String, OnnxTensor> {
        return layout.pastInputNames.mapIndexed { index, inputName ->
            val outputName = layout.pastOutputNames.getOrNull(index)
            inputName to (
                outputName?.let { namedTensor(result, it) }
                    ?: tensorAt(result, index + 1)
                    ?: error("Chatterbox language_model не вернул present cache #$index")
            )
        }.toMap(linkedMapOf())
    }

    private fun ensureSessionsFreshLocked() {
        if (!sessionStale) return
        val runtime = runtimePack ?: return
        closeSessionsLocked()
        sessions = buildSessions(runtime)
        sessionStale = false
    }

    private fun buildSessions(runtime: RuntimePack): Sessions {
        var speechEncoder: OrtSession? = null
        var embedTokens: OrtSession? = null
        var languageModel: OrtSession? = null
        var conditionalDecoder: OrtSession? = null
        try {
            speechEncoder = createSession(runtime.speechEncoderPath)
            embedTokens = createSession(runtime.embedTokensPath)
            languageModel = createSession(runtime.languageModelPath)
            conditionalDecoder = createSession(
                runtime.conditionalDecoderPath,
                OrtSession.SessionOptions.OptLevel.NO_OPT,
            )
            val layout = inspectLanguageModelLayout(languageModel)
            val speakerEmbeddingsType = findInputTensorType(conditionalDecoder, "speaker_embeddings")
            val speakerFeaturesType = findInputTensorType(conditionalDecoder, "speaker_features")
            return Sessions(
                speechEncoder = speechEncoder,
                embedTokens = embedTokens,
                languageModel = languageModel,
                conditionalDecoder = conditionalDecoder,
                layout = layout,
                speakerEmbeddingsType = speakerEmbeddingsType,
                speakerFeaturesType = speakerFeaturesType,
            )
        } catch (e: Exception) {
            runCatching { speechEncoder?.close() }
            runCatching { embedTokens?.close() }
            runCatching { languageModel?.close() }
            runCatching { conditionalDecoder?.close() }
            throw e
        }
    }

    private fun createSession(
        modelFile: File,
        optimizationLevel: OrtSession.SessionOptions.OptLevel = OrtSession.SessionOptions.OptLevel.BASIC_OPT,
    ): OrtSession {
        val startMs = SystemClock.elapsedRealtime()
        Timber.i(
            "Chatterbox create ONNX session file=%s sizeMb=%d",
            modelFile.name,
            estimateOnnxFootprintMb(modelFile),
        )
        return OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(ortIntraThreads)
            options.setInterOpNumThreads(1)
            runCatching { options.addConfigEntry("session.intra_op.allow_spinning", "0") }
                .onFailure { Timber.w(it, "Chatterbox: failed to disable intra-op spinning for %s", modelFile.name) }
            runCatching { options.addConfigEntry("session.inter_op.allow_spinning", "0") }
                .onFailure { Timber.w(it, "Chatterbox: failed to disable inter-op spinning for %s", modelFile.name) }
            try {
                options.setOptimizationLevel(optimizationLevel)
            } catch (e: OrtException) {
                Timber.w(e, "Chatterbox: %s failed for %s", optimizationLevel, modelFile.name)
            }
            env.createSession(modelFile.absolutePath, options).also {
                Timber.i(
                    "Chatterbox ONNX session ready file=%s durationMs=%d",
                    modelFile.name,
                    SystemClock.elapsedRealtime() - startMs,
                )
            }
        }
    }

    private fun estimateOnnxFootprintMb(modelFile: File): Long {
        val sidecars = listOf(
            File(modelFile.parentFile, "${modelFile.name}_data"),
            File(modelFile.parentFile, "${modelFile.name}.data"),
            File(modelFile.parentFile, "${modelFile.nameWithoutExtension}.onnx_data"),
            File(modelFile.parentFile, "${modelFile.nameWithoutExtension}.onnx.data"),
        )
        val bytes = modelFile.length() + sidecars
            .filter { it.isFile }
            .distinctBy { it.absolutePath }
            .sumOf { it.length() }
        return bytes / (1024L * 1024L)
    }

    private fun inspectLanguageModelLayout(session: OrtSession): LanguageModelLayout {
        val pastInputs = session.inputInfo.entries
            .filter { it.key.startsWith("past_key_values.") }
            .sortedWith(
                compareBy<Map.Entry<String, NodeInfo>>(
                    { it.key.substringAfter("past_key_values.").substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE },
                    { if (it.key.endsWith(".key")) 0 else 1 },
                ),
            )
        val pastOutputs = session.outputInfo.entries
            .filter {
                it.key.startsWith("present_key_values.") || it.key.startsWith("past_key_values.")
            }
            .sortedWith(
                compareBy<Map.Entry<String, NodeInfo>>(
                    { it.key.substringAfter('.').substringBefore('.').toIntOrNull() ?: Int.MAX_VALUE },
                    { if (it.key.endsWith(".key")) 0 else 1 },
                ),
            )
        require(pastInputs.isNotEmpty()) { "Chatterbox language_model не содержит past_key_values.* inputs" }
        val sampleTensorInfo = pastInputs.first().value.info as? ai.onnxruntime.TensorInfo
            ?: error("Chatterbox past input не является TensorInfo")
        val shape = sampleTensorInfo.shape
        val numHeads = shape.getOrNull(1)?.takeIf { it > 0 }?.toInt() ?: 16
        val headDim = shape.lastOrNull()?.takeIf { it > 0 }?.toInt() ?: 64
        val numLayers = pastInputs.map { it.key.substringAfter("past_key_values.").substringBefore('.').toIntOrNull() ?: 0 }
            .distinct()
            .size
        val inputsEmbedsType = findInputTensorType(session, "inputs_embeds")
        val logitsOutputName = session.outputInfo.keys.firstOrNull { it.equals("logits", ignoreCase = true) }
            ?: session.outputInfo.keys.firstOrNull { it.contains("logits", ignoreCase = true) }
        return LanguageModelLayout(
            pastInputNames = pastInputs.map { it.key },
            pastOutputNames = pastOutputs.map { it.key },
            logitsOutputName = logitsOutputName,
            numLayers = numLayers,
            numKeyValueHeads = numHeads,
            headDim = headDim,
            pastTensorType = sampleTensorInfo.type,
            inputsEmbedsType = inputsEmbedsType,
        )
    }

    private fun findInputTensorType(session: OrtSession, preferredName: String): OnnxJavaType {
        val direct = session.inputInfo[preferredName]?.info as? ai.onnxruntime.TensorInfo
        if (direct != null) return direct.type
        val match = session.inputInfo.entries.firstOrNull { it.key.equals(preferredName, ignoreCase = true) }
        val info = match?.value?.info as? ai.onnxruntime.TensorInfo
        return info?.type ?: OnnxJavaType.FLOAT
    }

    private fun mapFeedByAliases(
        session: OrtSession,
        preferredFeeds: Map<String, OnnxTensor>,
    ): LinkedHashMap<String, OnnxTensor> {
        val inputNames = session.inputInfo.keys.toList()
        val byLower = inputNames.associateBy { it.lowercase() }
        val result = LinkedHashMap<String, OnnxTensor>()
        preferredFeeds.forEach { (alias, tensor) ->
            val mappedName = byLower[alias.lowercase()] ?: alias.takeIf { it in inputNames }
            if (mappedName != null) {
                result[mappedName] = tensor
            }
        }
        return result
    }

    private fun resolveRuntimePack(
        packId: String,
        root: File,
        runtimeFamilyHint: String?,
        precisionHint: String?,
    ): RuntimePack? {
        val tokenizer = File(root, "tokenizer.json").takeIf { it.isFile } ?: return null
        val defaultVoice = resolveReferenceVoiceFile(root, selectedVoiceId)
            ?: collectReferenceVoiceFiles(root).firstOrNull()
            ?: return null
        val onnxDir = File(root, "onnx").takeIf { it.isDirectory } ?: root
        val speechEncoder = pickRuntimeGraph(onnxDir, "speech_encoder", precisionHint) ?: return null
        val embedTokens = pickRuntimeGraph(onnxDir, "embed_tokens", precisionHint) ?: return null
        val conditionalDecoder = pickRuntimeGraph(onnxDir, "conditional_decoder", precisionHint) ?: return null
        val languageModel = pickRuntimeGraph(onnxDir, "language_model", precisionHint) ?: return null
        val precision = inferRuntimePrecision(languageModel, precisionHint)
        return RuntimePack(
            packId = packId,
            rootDir = root,
            runtimeFamily = runtimeFamilyHint ?: "chatterbox_v1",
            precision = precision,
            speechEncoderPath = speechEncoder,
            embedTokensPath = embedTokens,
            conditionalDecoderPath = conditionalDecoder,
            languageModelPath = languageModel,
            tokenizerPath = tokenizer,
            defaultVoicePath = defaultVoice,
        )
    }

    private fun pickRuntimeGraph(
        onnxDir: File,
        baseName: String,
        precisionHint: String?,
    ): File? {
        val orderedSuffixes = when {
            precisionHint.equals("int4", ignoreCase = true) -> listOf(
                "_q4.onnx",
                "_q4f16.onnx",
                "_int4.onnx",
                "_quantized.onnx",
                "_uint8f16.onnx",
                "_fp16.onnx",
                "_q8f16.onnx",
                ".onnx",
            )
            precisionHint.equals("fp16", ignoreCase = true) -> listOf(
                "_fp16.onnx",
                "_q8f16.onnx",
                ".onnx",
                "_q4.onnx",
                "_q4f16.onnx",
                "_int4.onnx",
                "_quantized.onnx",
                "_uint8f16.onnx",
            )
            else -> listOf(
                ".onnx",
                "_fp16.onnx",
                "_q8f16.onnx",
                "_q4.onnx",
                "_q4f16.onnx",
                "_int4.onnx",
                "_quantized.onnx",
                "_uint8f16.onnx",
            )
        }
        val orderedNames = chatterboxGraphBaseNames(baseName).flatMap { graphBase ->
            orderedSuffixes.map { suffix -> graphBase + suffix }
        }
        return orderedNames
            .asSequence()
            .map { File(onnxDir, it) }
            .firstOrNull { it.isFile && it.length() > MIN_ONNX_BYTES }
    }

    private fun chatterboxGraphBaseNames(baseName: String): List<String> {
        return when (baseName) {
            "speech_encoder" -> listOf("speech_encoder", "multi_lang_speech_encoder")
            "embed_tokens" -> listOf("embed_tokens", "multi_lang_embed_tokens")
            "conditional_decoder" -> listOf("conditional_decoder", "multi_lang_conditional_decoder")
            else -> listOf(baseName)
        }
    }

    private fun inferRuntimePrecision(modelFile: File, precisionHint: String?): String {
        val hinted = precisionHint?.lowercase()?.takeIf { it in setOf("int4", "fp16", "fp32") }
        val name = modelFile.name.lowercase()
        return when {
            name.contains("q4") || name.contains("int4") || name.contains("quantized") || name.contains("uint8") -> "int4"
            name.contains("fp16") || name.contains("q8f16") -> "fp16"
            hinted != null -> hinted
            else -> "fp32"
        }
    }

    private fun closeSessionsLocked() {
        runCatching { sessions?.speechEncoder?.close() }
        runCatching { sessions?.embedTokens?.close() }
        runCatching { sessions?.languageModel?.close() }
        runCatching { sessions?.conditionalDecoder?.close() }
        sessions = null
    }

    private fun logRuntimeProfile() {
        val runtime = runtimePack ?: return
        val liveSessions = sessions ?: return
        Timber.i(
            "Chatterbox ready pack=%s precision=%s runtime=%s voice=%s voiceId=%s threads=%d lmType=%s kvType=%s layers=%d",
            runtime.packId,
            runtime.precision,
            runtime.runtimeFamily,
            referenceVoicePath,
            selectedVoiceId,
            ortIntraThreads,
            liveSessions.layout.inputsEmbedsType,
            liveSessions.layout.pastTensorType,
            liveSessions.layout.numLayers,
        )
    }

    @SuppressLint("HalfFloat")
    private fun createTypedFloatTensor(blob: FloatBlob, targetType: OnnxJavaType): OnnxTensor {
        return when (targetType) {
            OnnxJavaType.FLOAT16 -> {
                val half = ShortArray(blob.data.size)
                for (index in blob.data.indices) {
                    half[index] = Half.toHalf(blob.data[index])
                }
                OnnxTensor.createTensor(env, ShortBuffer.wrap(half), blob.shape, OnnxJavaType.FLOAT16)
            }
            else -> OnnxTensor.createTensor(env, FloatBuffer.wrap(blob.data), blob.shape)
        }
    }

    private fun createFloatTensor(data: FloatArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    private fun createLongTensor(data: LongArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
    }

    private fun tensorToFloatBlob(tensor: OnnxTensor): FloatBlob {
        return FloatBlob(
            data = tensorToFloatArray(tensor),
            shape = tensor.info.shape.copyOf(),
        )
    }

    private fun tensorToLongBlob(tensor: OnnxTensor): LongBlob {
        return LongBlob(
            data = tensorToLongArray(tensor),
            shape = tensor.info.shape.copyOf(),
        )
    }

    @SuppressLint("HalfFloat")
    private fun tensorToFloatArray(tensor: OnnxTensor): FloatArray {
        return when (tensor.info.type) {
            OnnxJavaType.FLOAT16 -> {
                val buffer = tensor.shortBuffer
                val out = FloatArray(buffer.remaining())
                for (index in out.indices) {
                    out[index] = Half.toFloat(buffer.get(index))
                }
                out
            }
            OnnxJavaType.DOUBLE -> {
                val buffer = tensor.doubleBuffer
                val out = FloatArray(buffer.remaining())
                for (index in out.indices) {
                    out[index] = buffer.get(index).toFloat()
                }
                out
            }
            else -> {
                val buffer = tensor.floatBuffer
                val out = FloatArray(buffer.remaining())
                for (index in out.indices) {
                    out[index] = buffer.get(index)
                }
                out
            }
        }
    }

    private fun tensorToLongArray(tensor: OnnxTensor): LongArray {
        return when (tensor.info.type) {
            OnnxJavaType.INT32 -> {
                val buffer = tensor.intBuffer
                LongArray(buffer.remaining()) { index -> buffer.get(index).toLong() }
            }
            else -> {
                val buffer = tensor.longBuffer
                LongArray(buffer.remaining()) { index -> buffer.get(index) }
            }
        }
    }

    private fun concatEmbeddings(first: FloatBlob, second: FloatBlob): FloatBlob {
        require(first.shape.size == 3 && second.shape.size == 3) { "Chatterbox embeddings должны быть rank-3" }
        val batch = first.shape[0].coerceAtLeast(1L)
        require(batch == second.shape[0]) { "Chatterbox embeddings batch mismatch" }
        val hidden = first.shape[2].coerceAtLeast(1L)
        require(hidden == second.shape[2]) { "Chatterbox embeddings hidden mismatch" }
        val firstSeq = first.shape[1].toInt()
        val secondSeq = second.shape[1].toInt()
        val hiddenInt = hidden.toInt()
        val out = FloatArray((firstSeq + secondSeq) * hiddenInt)
        System.arraycopy(first.data, 0, out, 0, first.data.size)
        System.arraycopy(second.data, 0, out, first.data.size, second.data.size)
        return FloatBlob(
            data = out,
            shape = longArrayOf(batch, (firstSeq + secondSeq).toLong(), hidden),
        )
    }

    private fun firstTensor(result: OrtSession.Result): OnnxTensor? {
        val preferred = listOf("audio", "waveform", "output", "wav")
        for (name in preferred) {
            namedTensor(result, name)?.let { return it }
        }
        return tensorAt(result, 0)
    }

    private fun findLogitsTensor(
        result: OrtSession.Result,
        layout: LanguageModelLayout,
    ): OnnxTensor? {
        layout.logitsOutputName?.let { outputName ->
            namedTensor(result, outputName)?.let { return it }
        }
        return namedTensor(result, "logits") ?: tensorAt(result, 0)
    }

    private fun namedTensor(
        result: OrtSession.Result,
        name: String,
    ): OnnxTensor? {
        val named = runCatching { result.get(name) }.getOrNull() ?: return null
        val resolved = (named as? java.util.Optional<*>)?.orElse(null) ?: named
        return resolved as? OnnxTensor
    }

    private fun tensorAt(
        result: OrtSession.Result,
        index: Int,
    ): OnnxTensor? = runCatching { result.get(index) as? OnnxTensor }.getOrNull()

    private fun resolveReferenceVoiceFile(
        root: File,
        voiceId: String?,
    ): File? {
        val wavFiles = collectReferenceVoiceFiles(root)
        if (wavFiles.isEmpty()) return null
        val preferredId = voiceId?.trim()?.takeIf { it.isNotEmpty() }
        if (preferredId != null) {
            wavFiles.firstOrNull { it.nameWithoutExtension.equals(preferredId, ignoreCase = true) }?.let { return it }
            wavFiles.firstOrNull { it.name.equals(preferredId, ignoreCase = true) }?.let { return it }
        }
        return wavFiles.firstOrNull { it.name.equals("default_voice.wav", ignoreCase = true) } ?: wavFiles.first()
    }

    private fun collectReferenceVoiceFiles(root: File): List<File> {
        val files = linkedMapOf<String, File>()
        fun addFrom(dir: File?) {
            dir?.listFiles()
                ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { wav ->
                    files.putIfAbsent(wav.name.lowercase(), wav)
                }
        }
        addFrom(root)
        root.parentFile
            ?.listFiles()
            ?.filter { it.isDirectory && it != root }
            ?.sortedBy { it.name }
            ?.forEach(::addFrom)
        return files.values.toList()
    }

    private fun prepareTextForChatterbox(text: String): String {
        return text
            .replace('\u00A0', ' ')
            .replace("…", "...")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace("№", " номер ")
            .replace(Regex("""\bи\s+т\.\s*д\.""", RegexOption.IGNORE_CASE), "и так далее")
            .replace(Regex("""\bи\s+т\.\s*п\.""", RegexOption.IGNORE_CASE), "и тому подобное")
            .replace(Regex("""\s*[—–]\s*"""), " — ")
            .replace(Regex("""[ \t]+"""), " ")
            .trim()
    }

    private fun resampleForSpeechRate(samples: FloatArray): FloatArray {
        val rate = speechRate
        if (rate == 1.0f) return samples
        val factor = (1.0f / rate).coerceIn(0.5f, 2.0f)
        val newLength = (samples.size * factor).roundToInt().coerceAtLeast(1)
        if (newLength == samples.size) return samples
        val out = FloatArray(newLength)
        for (i in 0 until newLength) {
            val src = i / factor
            val left = src.toInt().coerceIn(0, samples.lastIndex)
            val right = (left + 1).coerceIn(0, samples.lastIndex)
            val frac = src - left
            out[i] = ((1.0 - frac) * samples[left] + frac * samples[right]).toFloat()
        }
        return out
    }

    private suspend fun trackWords(sentence: SentenceInfo, samples: Int) {
        val durationMs = (samples.toFloat() / sampleRate * 1000).toLong()
        val words = mutableListOf<IntRange>()
        var index = 0
        val text = sentence.text
        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length) break
            val start = index
            while (index < text.length && !text[index].isWhitespace()) index++
            words += IntRange(start, index)
        }
        if (words.isEmpty()) return
        val total = words.sumOf { it.last - it.first }.coerceAtLeast(1)
        for (word in words) {
            _currentWordRange.value = IntRange(sentence.startOffset + word.first, sentence.startOffset + word.last)
            delay((durationMs * (word.last - word.first) / total).coerceAtLeast(50))
        }
    }

    private suspend fun playAudio(data: FloatArray) {
        val pcm = ShortArray(data.size) { index ->
            (data[index] * Short.MAX_VALUE)
                .toInt()
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
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack = track
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val totalMs = (pcm.size * 1000L / sampleRate).coerceAtLeast(1L)
            var elapsed = 0L
            while (elapsed < totalMs && !isPaused && (playbackJob?.isActive != false)) {
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
    }

    private fun splitIntoSentences(text: String): List<SentenceInfo> {
        val result = mutableListOf<SentenceInfo>()
        val pattern = Regex("""\n{2,}|[.!?…]+[\s\n]+|[:;]+[\s\n]+|[.!?…]+$""")
        var last = 0
        pattern.findAll(text).forEach { match ->
            val end = match.range.last + 1
            val chunk = text.substring(last, end).trim()
            if (chunk.isNotBlank()) splitLargeChunk(chunk, last, end).forEach(result::add)
            last = end
        }
        if (last < text.length) {
            val chunk = text.substring(last).trim()
            if (chunk.isNotBlank()) splitLargeChunk(chunk, last, text.length).forEach(result::add)
        }
        if (result.isEmpty() && text.isNotBlank()) {
            splitLargeChunk(text.trim(), 0, text.length).forEach(result::add)
        }
        return mergeNearbySentences(result, text)
    }

    private fun splitLargeChunk(chunk: String, start: Int, end: Int): List<SentenceInfo> {
        if (chunk.length <= 180) return listOf(SentenceInfo(chunk, start, end))
        val out = mutableListOf<SentenceInfo>()
        var cursor = 0
        while (cursor < chunk.length) {
            val rawEnd = (cursor + 160).coerceAtMost(chunk.length)
            if (rawEnd >= chunk.length) {
                val last = chunk.substring(cursor).trim()
                if (last.isNotBlank()) out += SentenceInfo(last, start + cursor, end)
                break
            }
            val region = chunk.substring(cursor, rawEnd)
            val splitAt = maxOf(
                region.lastIndexOf(", "),
                region.lastIndexOf(" — "),
                region.lastIndexOf(": "),
                region.lastIndexOf(' '),
            ).takeIf { it > 24 } ?: region.length
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
            " — " to "dash",
        )
        for ((separator, label) in strategies) {
            val parts = splitSentenceBySeparator(sentence, separator, label)
            if (parts.size > 1) return parts
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
        for (index in to downTo from) {
            if (text[index].isWhitespace()) {
                splitAt = index
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

    private fun mergeNearbySentences(
        sentences: List<SentenceInfo>,
        sourceText: String,
    ): List<SentenceInfo> {
        if (sentences.size <= 1) return sentences
        val merged = mutableListOf<SentenceInfo>()
        var current = sentences.first()
        for (index in 1 until sentences.size) {
            val next = sentences[index]
            val gap = sourceText.substring(
                current.endOffset.coerceIn(0, sourceText.length),
                next.startOffset.coerceIn(0, sourceText.length),
            )
            val keepBoundary = gap.contains("\n\n")
            val shouldMerge = current.text.length < mergeShortThreshold &&
                next.text.length < mergeShortThreshold &&
                current.text.length + next.text.length < mergeTotalCap &&
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
                merged += current
                next
            }
        }
        merged += current
        return merged
    }

    private fun canContinue(sessionId: Long): Boolean {
        return !isPaused && sessionId == playbackSessionId && playbackJob?.isActive != false
    }

    private fun computeMaxNewTokens(): Int {
        return 320
    }

    private fun resetDiagnosticsForPack(
        runtime: RuntimePack,
        referenceVoiceFile: File,
    ) {
        _diagnostics.value = ChatterboxPlaybackDiagnostics(
            packId = runtime.packId,
            runtimeFamily = runtime.runtimeFamily,
            languageId = languageId,
            voiceId = selectedVoiceId ?: referenceVoiceFile.nameWithoutExtension,
            referenceVoicePath = referenceVoiceFile.absolutePath,
            speechRate = speechRate,
            exaggeration = exaggeration,
            ortThreads = ortIntraThreads,
        )
    }

    private fun resetDiagnosticsForSession(totalChunks: Int) {
        _diagnostics.value = diagnostics.value.copy(
            totalChunks = totalChunks,
            completedChunks = 0,
            recoveredChunks = 0,
            failedChunks = 0,
            lastChunkPreview = null,
            lastChunkRange = null,
            lastChunkSplitDepth = 0,
            lastChunkDurationMs = null,
            lastGeneratedTokens = null,
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

    private fun recordChunkSuccess(
        usedRecovery: Boolean,
        durationMs: Long,
        generatedTokens: Int,
    ) {
        _diagnostics.value = diagnostics.value.copy(
            completedChunks = (diagnostics.value.completedChunks + 1).coerceAtMost(diagnostics.value.totalChunks),
            recoveredChunks = if (usedRecovery) diagnostics.value.recoveredChunks + 1 else diagnostics.value.recoveredChunks,
            lastChunkDurationMs = durationMs,
            lastGeneratedTokens = generatedTokens,
        )
    }

    private fun recordChunkFailure(failure: ChatterboxPlaybackFailure) {
        _diagnostics.value = diagnostics.value.copy(
            failedChunks = diagnostics.value.failedChunks + 1,
            lastFailureMessage = failure.message,
            lastFailurePreview = failure.chunkPreview,
            lastFailureRange = failure.chunkRange,
        )
        Timber.e(
            "Chatterbox final failure: pack=%s range=%s preview=%s message=%s",
            failure.packId,
            failure.chunkRange,
            failure.chunkPreview,
            failure.message,
        )
    }

    private fun buildPlaybackFailure(
        message: String,
        sentence: SentenceInfo,
    ): ChatterboxPlaybackFailure {
        return ChatterboxPlaybackFailure(
            message = message,
            chunkPreview = previewText(sentence.text),
            chunkRange = sentence.range(),
            packId = diagnostics.value.packId,
            languageId = diagnostics.value.languageId,
        )
    }

    private fun previewText(text: String): String {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        return if (normalized.length <= CHUNK_PREVIEW_LIMIT) normalized else normalized.take(CHUNK_PREVIEW_LIMIT - 1) + "…"
    }

    private fun failedPrepare(message: String, path: String? = null): TtsPrepareResult {
        _isReady.value = false
        return TtsPrepareResult(
            success = false,
            engineType = TtsEngineType.CHATTERBOX,
            resolvedPackPath = path,
            message = message,
        )
    }

    companion object {
        private const val REPETITION_PENALTY = 1.2f
        private const val CHUNK_PREVIEW_LIMIT = 72
        private const val MIN_ONNX_BYTES = 1_024L
    }
}
