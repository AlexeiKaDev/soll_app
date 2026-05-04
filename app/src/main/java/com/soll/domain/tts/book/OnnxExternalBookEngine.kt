package com.soll.domain.tts.book

import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.kokoro.KokoroOnnxTtsEngine
import com.soll.domain.tts.kokoro.KokoroPlaybackDiagnostics
import com.soll.domain.tts.kokoro.KokoroPlaybackFailure
import com.soll.domain.tts.onnx.InstalledOnnxPack
import com.soll.domain.tts.onnx.OnnxModelPackManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Внешние ONNX-паки с устройства.
 * Реализован **kokoro_v1** ([Kokoro-82M ONNX](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX)).
 * MOSS / Chatterbox / Supertonic — другая топология графов и рантайм; пока не подключены.
 */
@Singleton
class OnnxExternalBookEngine @Inject constructor(
    private val modelPackManager: OnnxModelPackManager,
    private val kokoroEngine: KokoroOnnxTtsEngine,
) : TtsBookEngine {

    override val type: TtsEngineType = TtsEngineType.ONNX_EXTERNAL
    override val displayName: String = "ONNX External"

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    override val isSpeaking: StateFlow<Boolean> = kokoroEngine.isSpeaking
    override val downloadProgress: StateFlow<Float?> = kokoroEngine.downloadProgress
    override val currentWordRange: StateFlow<IntRange?> = kokoroEngine.currentWordRange
    val diagnostics: StateFlow<KokoroPlaybackDiagnostics> = kokoroEngine.diagnostics
    val runtimeFailures: SharedFlow<KokoroPlaybackFailure> = kokoroEngine.playbackFailures

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var selectedPack: InstalledOnnxPack? = null

    fun setSelectedPack(pack: InstalledOnnxPack?) {
        selectedPack = pack
        _isReady.value = false
    }

    override fun voiceOptions(): List<TtsVoiceOption> {
        val root = selectedPack?.rootDir ?: return emptyList()
        val voices = kokoroEngine.listVoiceIdsForPackRoot(File(root))
        return voices.map { id -> TtsVoiceOption(id, id.replace('_', ' ')) }
    }

    override suspend fun prepare(): TtsPrepareResult {
        val pack = selectedPack ?: modelPackManager.pickBestRussianPack()
        if (pack == null) {
            Timber.w("ONNX External: no installed packs")
            _isReady.value = false
            return TtsPrepareResult(
                success = false,
                engineType = type,
                message = "Не найден runnable русский ONNX pack в папке tts",
            )
        }
        selectedPack = pack
        return when (pack.effectiveRuntimeFamily()) {
            "kokoro_v1" -> {
                // Голос из манифеста учитывается только если есть соответствующий *.bin —
                // иначе prepareWithPack берёт первый доступный файл в voices/.
                val ok = kokoroEngine.prepareWithPack(pack)
                _isReady.value = ok
                TtsPrepareResult(
                    success = ok,
                    engineType = type,
                    resolvedPackPath = pack.rootDir,
                    resolvedVoiceId = pack.kokoroVoice,
                    message = if (ok) null else "Kokoro pack найден, но не прошёл инициализацию",
                )
            }
            else -> {
                Timber.w(
                    "ONNX runtime '${pack.effectiveRuntimeFamily()}' (${pack.modelId}) " +
                        "ещё не интегрирован в приложение (нужен отдельный исполнитель графов).",
                )
                _isReady.value = false
                TtsPrepareResult(
                    success = false,
                    engineType = type,
                    resolvedPackPath = pack.rootDir,
                    message = "ONNX runtime '${pack.effectiveRuntimeFamily()}' не поддержан на Android",
                )
            }
        }
    }

    override suspend fun speakChapter(text: String, onChapterFinished: () -> Unit) {
        val pack = selectedPack ?: modelPackManager.pickBestRussianPack()
        if (pack == null) {
            _errors.tryEmit("Не найден runnable русский ONNX pack в папке tts")
            return
        }
        when (pack.effectiveRuntimeFamily()) {
            "kokoro_v1" -> {
                if (!_isReady.value) {
                    Timber.e("Kokoro: speakChapter called before successful prepare")
                    _errors.tryEmit("Kokoro pack найден, но ещё не инициализирован")
                    return
                }
                kokoroEngine.speakChapter(text, onChapterFinished)
            }
            else -> {
                Timber.w("ONNX External: unsupported runtime, skipping speech")
                _errors.tryEmit("ONNX runtime '${pack.effectiveRuntimeFamily()}' не поддержан на Android")
            }
        }
    }

    override fun pause() {
        kokoroEngine.pause()
    }

    override suspend fun resume() {
        kokoroEngine.resume()
    }

    override fun stop() {
        kokoroEngine.stop()
    }

    override fun setSpeechRate(rate: Float) {
        kokoroEngine.setSpeechRate(rate)
    }

    override fun setVoiceId(voiceId: String) {
        kokoroEngine.setVoice(voiceId)
    }

    override fun shutdown() {
        kokoroEngine.shutdown()
        _isReady.value = false
    }

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "kokoro_ort_intra_threads",
            label = "Потоки ONNX Kokoro (ниже — экономичнее)",
            range = 1f..4f,
            defaultValue = kokoroEngine.getOrtIntraThreads().toFloat(),
            materialSliderSteps = 3,
        ),
    )

    override fun applyTunable(key: String, value: Float) {
        if (key == "kokoro_ort_intra_threads") kokoroEngine.applyOrtIntraThreadsTunable(value)
    }

    override fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        kokoroEngine.applyPerformanceProfile(profile)
    }
}
