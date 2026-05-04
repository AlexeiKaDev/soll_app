package com.soll.presentation.screens.tools.bookreader

import android.content.Context
import android.net.Uri
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.BookEntity
import com.soll.data.repository.BookRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.service.TtsService
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubChapter
import com.soll.domain.tts.PiperPlaybackDiagnostics
import com.soll.domain.tts.TextToSpeechManager
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.TtsServiceAction
import com.soll.domain.tts.TtsState
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.DownloadableTtsPack
import com.soll.domain.tts.catalog.TtsPackEngineFamily
import com.soll.domain.tts.catalog.TtsPackLibrary
import com.soll.domain.tts.onnx.InstalledOnnxPack
import com.soll.domain.tts.onnx.OnnxModelPackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt
import javax.inject.Inject

data class BookReaderUiState(
    val books: List<BookEntity> = emptyList(),
    val currentBook: EpubBook? = null,
    val currentBookEntity: BookEntity? = null,
    val currentChapter: EpubChapter? = null,
    val currentChapterIndex: Int = 0,
    val currentChapterPosition: Int = 0,
    val isLoading: Boolean = false,
    val isTtsPlaying: Boolean = false,
    val ttsState: TtsState = TtsState.Idle,
    val error: String? = null,
    val speechRate: Float = 1.0f,
    val autoAdvanceEnabled: Boolean = true,
    val highlightRange: IntRange? = null,
    // System TTS engines
    val availableEngines: List<TextToSpeech.EngineInfo> = emptyList(),
    val selectedEngine: String? = null,
    // Engine type
    val engineType: TtsEngineType = TtsEngineType.SYSTEM,
    val sileroModelDownloaded: Boolean = false,
    val sileroDownloadProgress: Float? = null,
    val sileroVoiceId: String = "irina",
    val utrobinVoiceId: String = "0",
    /** Utrobin ONNX intra-op threads (1–4), persisted. */
    val utrobinOrtThreads: Int = 2,
    val natashaOrtThreads: Int = 2,
    val sherpaThreads: Int = 2,
    val performanceProfile: TtsBookPerformanceProfile = TtsBookPerformanceProfile.BALANCED,
    val ttsVoiceOptions: List<TtsVoiceOption> = emptyList(),
    val installedOnnxPacks: List<InstalledOnnxPack> = emptyList(),
    val selectedOnnxPackKey: String? = null,
    /** Слайдеры и др. из [TtsBookEngine.tunableSettings] активного движка. */
    val engineTunables: List<TtsEngineTunable> = emptyList(),
    val systemPitch: Float = 1.0f,
    val detectedTtsPacks: List<DetectedTtsPack> = emptyList(),
    val downloadableTtsPacks: List<DownloadableTtsPack> = emptyList(),
    val packDownloadProgress: Float? = null,
    val packDownloadLabel: String? = null,
    val lastTtsModelRootUri: String? = null,
    val selectedPiperPackId: String? = null,
    val selectedNatashaPackId: String? = null,
    val selectedUtrobinPackId: String? = null,
    val piperDiagnostics: PiperPlaybackDiagnostics = PiperPlaybackDiagnostics(),
)

sealed class BookReaderEvent {
    data class ShowError(val message: String) : BookReaderEvent()
    data class BookImported(val title: String) : BookReaderEvent()
    data class TtsPacksImported(val count: Int) : BookReaderEvent()
}

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    private val ttsManager: TextToSpeechManager,
    private val settingsRepository: SettingsRepository,
    private val onnxModelPackManager: OnnxModelPackManager,
    private val ttsPackLibrary: TtsPackLibrary,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookReaderUiState())
    val uiState: StateFlow<BookReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BookReaderEvent>()
    val events: SharedFlow<BookReaderEvent> = _events.asSharedFlow()
    private var lastPackDownloadErrorMessage: String? = null

    init {
        loadBooks()
        loadSettings()
        initTts()
        observeTtsState()
        observeChapterFinished()
        observeServiceActions()
        observePackDownloads()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
    }

    private fun loadSettings() {
        ensureS200BookReaderBootstrap()
        val profile = TtsBookPerformanceProfile.fromStorage(settingsRepository.ttsBookPerformanceProfile)
        ttsManager.applyBookPerformanceProfile(profile)
        val detectedPacks = ttsPackLibrary.listDetectedPacks()
        val downloadablePacks = ttsPackLibrary.listDownloadableRussianPacks()

        val engineType = when (settingsRepository.ttsEngineType) {
            "silero" -> TtsEngineType.SILERO
            "utrobin" -> TtsEngineType.UTROBIN
            "natasha" -> TtsEngineType.NATASHA
            "onnx_external" -> TtsEngineType.ONNX_EXTERNAL
            else -> TtsEngineType.SYSTEM
        }
        val sileroVoice = settingsRepository.ttssileroSpeaker
        val utrobinVoice = settingsRepository.ttsUtrobinSpeaker
        val piperPackId = resolveSavedPiperPackId(
            detected = detectedPacks,
            savedPackId = settingsRepository.ttsPiperPackId,
            legacyVoiceId = sileroVoice,
        )
        val natashaPackId = settingsRepository.ttsNatashaPackId
        val utrobinPackId = settingsRepository.ttsUtrobinPackId
        val utrobinOrt = settingsRepository.ttsUtrobinOrtIntraThreads
        val natashaOrt = settingsRepository.ttsNatashaOrtIntraThreads
        val sherpaTh = settingsRepository.ttsSherpaNumThreads
        val pitch = settingsRepository.ttsSystemPitch
        ttsManager.setEngineType(engineType)
        ttsManager.setVoiceIdForEngine(TtsEngineType.UTROBIN, utrobinVoice)
        ttsManager.setPackIdForEngine(TtsEngineType.SILERO, piperPackId)
        ttsManager.setPackIdForEngine(TtsEngineType.NATASHA, natashaPackId)
        ttsManager.setPackIdForEngine(TtsEngineType.UTROBIN, utrobinPackId)
        ttsManager.applyTunableForEngine(TtsEngineType.UTROBIN, "ort_intra_threads", utrobinOrt.toFloat())
        ttsManager.applyTunableForEngine(TtsEngineType.NATASHA, "natasha_ort_intra_threads", natashaOrt.toFloat())
        ttsManager.applyTunableForEngine(TtsEngineType.SILERO, "sherpa_num_threads", sherpaTh.toFloat())
        ttsManager.setPitch(pitch)
        val installedOnnxPacks = onnxModelPackManager.listInstalledPacks()
        val savedPackKey = buildOnnxPackKey(settingsRepository.ttsOnnxModelId, settingsRepository.ttsOnnxPrecision)
        val selectedOnnxPack = installedOnnxPacks.firstOrNull {
            buildOnnxPackKey(it.modelId, it.precision) == savedPackKey
        } ?: onnxModelPackManager.pickBestRussianPack()
        Timber.d(
            "BookReader loadSettings engine=%s detectedPacks=%d downloadable=%d savedTtsRoot=%s",
            engineType,
            detectedPacks.size,
            downloadablePacks.size,
            settingsRepository.ttsModelRootUri,
        )
        ttsManager.setSelectedOnnxPack(selectedOnnxPack)
        _uiState.update {
            it.copy(
                autoAdvanceEnabled = settingsRepository.ttsAutoAdvance,
                selectedEngine = settingsRepository.ttsEngine,
                speechRate = settingsRepository.ttsSpeechRate,
                engineType = engineType,
                sileroModelDownloaded = ttsManager.isModelDownloaded(),
                sileroVoiceId = resolvePiperVoiceId(detectedPacks, piperPackId) ?: sileroVoice,
                utrobinVoiceId = utrobinVoice,
                utrobinOrtThreads = utrobinOrt,
                natashaOrtThreads = natashaOrt,
                sherpaThreads = sherpaTh,
                performanceProfile = profile,
                systemPitch = pitch,
                ttsVoiceOptions = if (engineType == TtsEngineType.SILERO) emptyList() else ttsManager.voiceOptions(engineType),
                engineTunables = ttsManager.tunableSettingsFor(engineType),
                installedOnnxPacks = installedOnnxPacks,
                selectedOnnxPackKey = selectedOnnxPack?.let { p -> buildOnnxPackKey(p.modelId, p.precision) },
                detectedTtsPacks = detectedPacks,
                downloadableTtsPacks = downloadablePacks,
                lastTtsModelRootUri = settingsRepository.ttsModelRootUri,
                selectedPiperPackId = piperPackId,
                selectedNatashaPackId = natashaPackId,
                selectedUtrobinPackId = utrobinPackId,
                piperDiagnostics = ttsManager.piperDiagnostics.value,
            )
        }
    }

    private fun ensureS200BookReaderBootstrap() {
        if (settingsRepository.bookReaderS200BootstrapDone) return
        if (isLikelyDoogeeS200()) {
            settingsRepository.ttsEngineType = when {
                ttsPackLibrary.findBestPack(TtsEngineType.NATASHA) != null -> "natasha"
                ttsPackLibrary.findBestPack(TtsEngineType.UTROBIN) != null -> "utrobin"
                else -> "system"
            }
            settingsRepository.ttsBookPerformanceProfile = TtsBookPerformanceProfile.BALANCED.storageKey
            settingsRepository.syncThreadPrefsFromProfile(TtsBookPerformanceProfile.BALANCED)
        }
        settingsRepository.bookReaderS200BootstrapDone = true
    }

    private fun isLikelyDoogeeS200(): Boolean {
        val m = Build.MANUFACTURER.lowercase(Locale.US)
        val model = Build.MODEL.uppercase(Locale.US)
        return m.contains("doogee") && model.contains("S200")
    }

    fun setPerformanceProfile(profile: TtsBookPerformanceProfile) {
        stopTts()
        settingsRepository.ttsBookPerformanceProfile = profile.storageKey
        settingsRepository.syncThreadPrefsFromProfile(profile)
        ttsManager.applyBookPerformanceProfile(profile)
        ttsManager.applyTunableForEngine(
            TtsEngineType.UTROBIN,
            "ort_intra_threads",
            settingsRepository.ttsUtrobinOrtIntraThreads.toFloat(),
        )
        ttsManager.applyTunableForEngine(
            TtsEngineType.NATASHA,
            "natasha_ort_intra_threads",
            settingsRepository.ttsNatashaOrtIntraThreads.toFloat(),
        )
        ttsManager.applyTunableForEngine(
            TtsEngineType.SILERO,
            "sherpa_num_threads",
            settingsRepository.ttsSherpaNumThreads.toFloat(),
        )
        val type = _uiState.value.engineType
        _uiState.update {
            it.copy(
                performanceProfile = profile,
                utrobinOrtThreads = settingsRepository.ttsUtrobinOrtIntraThreads,
                natashaOrtThreads = settingsRepository.ttsNatashaOrtIntraThreads,
                sherpaThreads = settingsRepository.ttsSherpaNumThreads,
                engineTunables = ttsManager.tunableSettingsFor(type),
            )
        }
        if (type == TtsEngineType.SILERO && !ttsManager.isEngineReady(TtsEngineType.SILERO)) {
            viewModelScope.launch {
                ttsManager.initializeSilero()
                _uiState.update { it.copy(sileroModelDownloaded = ttsManager.isModelDownloaded()) }
            }
        }
        if (type == TtsEngineType.NATASHA && !ttsManager.isEngineReady(TtsEngineType.NATASHA)) {
            viewModelScope.launch { ttsManager.initializeNatasha() }
        }
        if (type == TtsEngineType.UTROBIN && !ttsManager.isEngineReady(TtsEngineType.UTROBIN)) {
            viewModelScope.launch { ttsManager.initializeUtrobin() }
        }
        if (type == TtsEngineType.ONNX_EXTERNAL && !ttsManager.isEngineReady(TtsEngineType.ONNX_EXTERNAL)) {
            viewModelScope.launch { ttsManager.initializeOnnxExternal() }
        }
    }

    private fun initTts() {
        viewModelScope.launch {
            // Always init system TTS for engine list
            val enginePackage = settingsRepository.ttsEngine
            ttsManager.initialize(enginePackage)
            _uiState.update {
                it.copy(availableEngines = ttsManager.getAvailableEngines())
            }
            ttsManager.setSpeechRate(_uiState.value.speechRate)
            ttsManager.setPitch(settingsRepository.ttsSystemPitch)

            if (_uiState.value.engineType == TtsEngineType.SILERO && ttsManager.isModelDownloaded()) {
                ttsManager.initializeSilero()
            }
            if (_uiState.value.engineType == TtsEngineType.NATASHA && ttsManager.isModelDownloadedFor(TtsEngineType.NATASHA)) {
                ttsManager.initializeNatasha()
            }
            if (_uiState.value.engineType == TtsEngineType.UTROBIN && ttsManager.isModelDownloadedFor(TtsEngineType.UTROBIN)) {
                ttsManager.initializeUtrobin()
            }
            if (_uiState.value.engineType == TtsEngineType.ONNX_EXTERNAL) {
                ttsManager.initializeOnnxExternal()
            }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsManager.state.collect { state ->
                _uiState.update {
                    it.copy(
                        ttsState = state,
                        sileroDownloadProgress = if (state is TtsState.DownloadingModel) state.progress else null
                    )
                }
                if (state is TtsState.Error) {
                    _events.emit(BookReaderEvent.ShowError(state.message))
                }
            }
        }
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { isSpeaking ->
                _uiState.update { it.copy(isTtsPlaying = isSpeaking) }
            }
        }
        viewModelScope.launch {
            ttsManager.currentWordRange.collect { range ->
                _uiState.update { state ->
                    state.copy(
                        highlightRange = range,
                        currentChapterPosition = range?.first ?: state.currentChapterPosition
                    )
                }
            }
        }
    }

    private fun observeChapterFinished() {
        viewModelScope.launch {
            ttsManager.chapterFinished.collect {
                if (_uiState.value.autoAdvanceEnabled) {
                    val nextIndex = _uiState.value.currentChapterIndex + 1
                    val book = _uiState.value.currentBook
                    if (book != null && nextIndex < book.chapters.size) {
                        goToChapterAndPlay(nextIndex)
                    } else {
                        stopTts()
                    }
                }
            }
        }
    }

    private fun observeServiceActions() {
        viewModelScope.launch {
            ttsManager.serviceActions.collect { action ->
                when (action) {
                    TtsServiceAction.NEXT_CHAPTER -> nextChapter()
                    TtsServiceAction.PREV_CHAPTER -> previousChapter()
                    TtsServiceAction.PLAY -> playTts()
                    TtsServiceAction.PAUSE -> pauseTts()
                    TtsServiceAction.STOP -> stopTts()
                }
            }
        }
    }

    private fun buildOnnxPackKey(modelId: String?, precision: String?): String? {
        if (modelId.isNullOrBlank() || precision.isNullOrBlank()) return null
        return "$modelId|$precision"
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            bookRepository.importBook(uri).fold(
                onSuccess = { book ->
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(BookReaderEvent.BookImported(book.title))
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                    _events.emit(BookReaderEvent.ShowError(error.message ?: "Failed to import book"))
                }
            )
        }
    }

    fun openBook(bookEntity: BookEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val epubBook = bookRepository.parseBook(bookEntity)
            if (epubBook != null) {
                val chapterIndex = bookEntity.currentChapter.coerceIn(0, epubBook.chapters.size - 1)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentBook = epubBook,
                        currentBookEntity = bookEntity,
                        currentChapter = epubBook.chapters.getOrNull(chapterIndex),
                        currentChapterIndex = chapterIndex,
                        currentChapterPosition = bookEntity.currentPosition
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Failed to parse book") }
                _events.emit(BookReaderEvent.ShowError("Failed to parse book"))
            }
        }
    }

    fun closeBook() {
        stopTts()
        saveProgress()
        _uiState.update {
            it.copy(
                currentBook = null,
                currentBookEntity = null,
                currentChapter = null,
                currentChapterIndex = 0,
                currentChapterPosition = 0
            )
        }
    }

    fun goToChapter(index: Int) {
        val book = _uiState.value.currentBook ?: return
        if (index < 0 || index >= book.chapters.size) return
        stopTts()
        saveProgress()
        _uiState.update {
            it.copy(
                currentChapter = book.chapters[index],
                currentChapterIndex = index,
                currentChapterPosition = 0
            )
        }
    }

    private fun goToChapterAndPlay(index: Int) {
        val book = _uiState.value.currentBook ?: return
        if (index < 0 || index >= book.chapters.size) return
        saveProgress()
        _uiState.update {
            it.copy(
                currentChapter = book.chapters[index],
                currentChapterIndex = index,
                currentChapterPosition = 0
            )
        }
        val chapter = book.chapters[index]
        ttsManager.speakChapter(chapter.content)
        updateServiceNotification()
    }

    fun nextChapter() {
        goToChapter(_uiState.value.currentChapterIndex + 1)
    }

    fun previousChapter() {
        goToChapter(_uiState.value.currentChapterIndex - 1)
    }

    fun playTts() {
        val chapter = _uiState.value.currentChapter ?: return
        saveProgress()

        if (_uiState.value.engineType == TtsEngineType.SILERO && !ttsManager.isEngineReady(TtsEngineType.SILERO)) {
            viewModelScope.launch {
                val success = ttsManager.initializeSilero()
                if (success) startPlayback(chapter)
                else _events.emit(
                    BookReaderEvent.ShowError(
                        "Piper/Sherpa не готов: проверь локальный Piper pack в папке tts. Нужны ONNX-голос, tokens.txt и espeak-ng-data.",
                    ),
                )
            }
            return
        }
        if (_uiState.value.engineType == TtsEngineType.UTROBIN && !ttsManager.isEngineReady(TtsEngineType.UTROBIN)) {
            viewModelScope.launch {
                val success = ttsManager.initializeUtrobin()
                if (success) startPlayback(chapter)
                else _events.emit(
                    BookReaderEvent.ShowError(
                        "Не удалось инициализировать UtrobinTTS. Проверь локальный pack Utrobin в папке tts: model.onnx и tokens.txt.",
                    ),
                )
            }
            return
        }
        if (_uiState.value.engineType == TtsEngineType.NATASHA && !ttsManager.isEngineReady(TtsEngineType.NATASHA)) {
            viewModelScope.launch {
                val success = ttsManager.initializeNatasha()
                if (success) startPlayback(chapter)
                else _events.emit(
                    BookReaderEvent.ShowError(
                        "Не удалось инициализировать Natasha VITS2. Проверь локальный pack Natasha в папке tts.",
                    ),
                )
            }
            return
        }
        if (_uiState.value.engineType == TtsEngineType.ONNX_EXTERNAL && !ttsManager.isEngineReady(TtsEngineType.ONNX_EXTERNAL)) {
            viewModelScope.launch {
                val success = ttsManager.initializeOnnxExternal()
                if (success) startPlayback(chapter)
                else _events.emit(
                    BookReaderEvent.ShowError(
                        "Не найден runnable русский ONNX pack. Неподдержанные и нерусские паки смотри в списке моделей.",
                    ),
                )
            }
            return
        }

        startPlayback(chapter)
    }

    private fun startPlayback(chapter: EpubChapter) {
        val state = _uiState.value.ttsState
        if (state is TtsState.Paused) {
            ttsManager.resume()
        } else {
            ttsManager.speakChapter(chapter.content)
        }
        TtsService.start(appContext, getNotificationTitle(), getNotificationSubtitle())
    }

    fun pauseTts() {
        ttsManager.pause()
        TtsService.updatePlaybackState(appContext, false)
    }

    fun stopTts() {
        saveProgress()
        ttsManager.stop()
        TtsService.stop(appContext)
    }

    fun toggleTts() {
        if (_uiState.value.isTtsPlaying) {
            pauseTts()
        } else {
            playTts()
        }
    }

    fun setSpeechRate(rate: Float) {
        _uiState.update { it.copy(speechRate = rate) }
        ttsManager.setSpeechRate(rate)
        settingsRepository.ttsSpeechRate = rate
    }

    fun setAutoAdvance(enabled: Boolean) {
        _uiState.update { it.copy(autoAdvanceEnabled = enabled) }
        settingsRepository.ttsAutoAdvance = enabled
    }

    fun setEngineType(type: TtsEngineType) {
        stopTts()
        ttsManager.setEngineType(type)
        settingsRepository.ttsEngineType = when (type) {
            TtsEngineType.SILERO -> "silero"
            TtsEngineType.UTROBIN -> "utrobin"
            TtsEngineType.NATASHA -> "natasha"
            TtsEngineType.ONNX_EXTERNAL -> "onnx_external"
            TtsEngineType.SYSTEM -> "system"
        }
        if (type == TtsEngineType.UTROBIN) {
            val t = settingsRepository.ttsUtrobinOrtIntraThreads
            ttsManager.applyTunableForEngine(TtsEngineType.UTROBIN, "ort_intra_threads", t.toFloat())
        }
        _uiState.update {
            it.copy(
                engineType = type,
                ttsVoiceOptions = if (type == TtsEngineType.SILERO) emptyList() else ttsManager.voiceOptions(type),
                engineTunables = ttsManager.tunableSettingsFor(type),
                utrobinOrtThreads = settingsRepository.ttsUtrobinOrtIntraThreads,
                natashaOrtThreads = settingsRepository.ttsNatashaOrtIntraThreads,
                sherpaThreads = settingsRepository.ttsSherpaNumThreads,
            )
        }

        if (type == TtsEngineType.UTROBIN && !ttsManager.isEngineReady(TtsEngineType.UTROBIN)) {
            viewModelScope.launch { ttsManager.initializeUtrobin() }
        }
        if (type == TtsEngineType.NATASHA && !ttsManager.isEngineReady(TtsEngineType.NATASHA)) {
            viewModelScope.launch { ttsManager.initializeNatasha() }
        }
        if (type == TtsEngineType.SILERO && !ttsManager.isEngineReady(TtsEngineType.SILERO)) {
            viewModelScope.launch {
                ttsManager.initializeSilero()
                _uiState.update { it.copy(sileroModelDownloaded = ttsManager.isModelDownloaded()) }
            }
        }
        if (type == TtsEngineType.ONNX_EXTERNAL && !ttsManager.isEngineReady(TtsEngineType.ONNX_EXTERNAL)) {
            viewModelScope.launch { ttsManager.initializeOnnxExternal() }
        }
    }

    fun setEngineVoice(voiceId: String) {
        when (_uiState.value.engineType) {
            TtsEngineType.SILERO -> {
                val targetPackId = resolvePiperPackIdByVoiceId(_uiState.value.detectedTtsPacks, voiceId) ?: return
                selectEnginePack(targetPackId)
            }
            TtsEngineType.UTROBIN -> {
                settingsRepository.ttsUtrobinSpeaker = voiceId
                _uiState.update { it.copy(utrobinVoiceId = voiceId) }
                ttsManager.setVoiceIdForEngine(TtsEngineType.UTROBIN, voiceId)
            }
            else -> {}
        }
    }

    fun applyEngineTunable(key: String, value: Float) {
        val type = _uiState.value.engineType
        ttsManager.applyTunableForEngine(type, key, value)
        if (type == TtsEngineType.SYSTEM && key == "pitch") {
            val v = value.coerceIn(0.5f, 2.0f)
            settingsRepository.ttsSystemPitch = v
            _uiState.update { it.copy(systemPitch = v) }
        }
        if (type == TtsEngineType.UTROBIN && key == "ort_intra_threads") {
            val v = value.roundToInt().coerceIn(1, 4)
            settingsRepository.ttsUtrobinOrtIntraThreads = v
            _uiState.update { it.copy(utrobinOrtThreads = v) }
        }
        if (type == TtsEngineType.NATASHA && key == "natasha_ort_intra_threads") {
            val v = value.roundToInt().coerceIn(1, 4)
            settingsRepository.ttsNatashaOrtIntraThreads = v
            _uiState.update { it.copy(natashaOrtThreads = v) }
        }
        if (type == TtsEngineType.SILERO && key == "sherpa_num_threads") {
            val v = value.roundToInt().coerceIn(1, 4)
            settingsRepository.ttsSherpaNumThreads = v
            _uiState.update { it.copy(sherpaThreads = v) }
        }
    }

    fun selectTtsEngine(packageName: String) {
        settingsRepository.ttsEngine = packageName
        _uiState.update { it.copy(selectedEngine = packageName) }
        ttsManager.reinitializeWithEngine(packageName)
        ttsManager.setSpeechRate(_uiState.value.speechRate)
        ttsManager.setPitch(_uiState.value.systemPitch)
        _uiState.update { it.copy(availableEngines = ttsManager.getAvailableEngines()) }
    }

    /** Импорт из папки (SAF): копирует пакеты в приложение и обновляет список. */
    fun importTtsPacksFromUserFolder(treeUri: Uri) {
        Timber.i("Importing TTS packs from picker uri=%s", treeUri)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ttsPackLibrary.importFromTreeUri(treeUri) }
            }
            result.fold(
                onFailure = { e ->
                    Timber.e(e, "TTS pack import failed for uri=%s", treeUri)
                    _events.emit(BookReaderEvent.ShowError(e.message ?: "Импорт TTS-паков не удался"))
                },
                onSuccess = { count ->
                    Timber.i("TTS pack import finished uri=%s imported=%d", treeUri, count)
                    settingsRepository.ttsModelRootUri = treeUri.toString()
                    refreshOnnxPacks()
                    if (count > 0) {
                        _events.emit(BookReaderEvent.TtsPacksImported(count))
                    } else {
                        _events.emit(
                            BookReaderEvent.ShowError(
                                "В выбранной папке tts не найдены поддерживаемые паки.",
                            ),
                        )
                    }
                },
            )
        }
    }

    fun importOnnxPacksFromUserFolder(treeUri: Uri) = importTtsPacksFromUserFolder(treeUri)

    fun refreshOnnxPacks() {
        val detected = ttsPackLibrary.listDetectedPacks()
        val downloadable = ttsPackLibrary.listDownloadableRussianPacks()
        val piperPackId = resolveSavedPiperPackId(
            detected = detected,
            savedPackId = settingsRepository.ttsPiperPackId,
            legacyVoiceId = settingsRepository.ttssileroSpeaker,
        )
        val natashaPackId = settingsRepository.ttsNatashaPackId?.takeIf { id -> detected.any { it.packId == id } }
        val utrobinPackId = settingsRepository.ttsUtrobinPackId?.takeIf { id -> detected.any { it.packId == id } }
        if (piperPackId == null) settingsRepository.ttsPiperPackId = null
        if (natashaPackId == null) settingsRepository.ttsNatashaPackId = null
        if (utrobinPackId == null) settingsRepository.ttsUtrobinPackId = null
        val packs = onnxModelPackManager.listInstalledPacks()
        val selected = packs.firstOrNull {
            buildOnnxPackKey(it.modelId, it.precision) == _uiState.value.selectedOnnxPackKey
        } ?: onnxModelPackManager.pickBestRussianPack()
        ttsManager.setSelectedOnnxPack(selected)
        ttsManager.setPackIdForEngine(TtsEngineType.SILERO, piperPackId)
        ttsManager.setPackIdForEngine(TtsEngineType.NATASHA, natashaPackId)
        ttsManager.setPackIdForEngine(TtsEngineType.UTROBIN, utrobinPackId)
        Timber.d(
            "refreshOnnxPacks detected=%d downloadable=%d selectedPiper=%s selectedNatasha=%s selectedUtrobin=%s savedRoot=%s",
            detected.size,
            downloadable.size,
            piperPackId,
            natashaPackId,
            utrobinPackId,
            settingsRepository.ttsModelRootUri,
        )
        _uiState.update {
            it.copy(
                sileroModelDownloaded = ttsManager.isModelDownloaded(),
                installedOnnxPacks = packs,
                selectedOnnxPackKey = selected?.let { p -> buildOnnxPackKey(p.modelId, p.precision) },
                detectedTtsPacks = detected,
                downloadableTtsPacks = downloadable,
                lastTtsModelRootUri = settingsRepository.ttsModelRootUri,
                sileroVoiceId = resolvePiperVoiceId(detected, piperPackId) ?: settingsRepository.ttssileroSpeaker,
                selectedPiperPackId = piperPackId,
                selectedNatashaPackId = natashaPackId,
                selectedUtrobinPackId = utrobinPackId,
            )
        }
    }

    fun selectOnnxPack(modelId: String, precision: String) {
        val selected = _uiState.value.installedOnnxPacks.firstOrNull {
            it.modelId == modelId && it.precision == precision
        } ?: return
        ttsManager.setSelectedOnnxPack(selected)
        settingsRepository.ttsOnnxModelId = modelId
        settingsRepository.ttsOnnxPrecision = precision
        _uiState.update { it.copy(selectedOnnxPackKey = buildOnnxPackKey(modelId, precision)) }
        if (_uiState.value.engineType == TtsEngineType.ONNX_EXTERNAL) {
            viewModelScope.launch { ttsManager.initializeOnnxExternal() }
        }
    }

    fun deleteBook(bookEntity: BookEntity) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookEntity.id)
        }
    }

    fun deleteTtsPack(packId: String) {
        val deleted = ttsPackLibrary.deletePack(packId)
        if (deleted) {
            when (packId) {
                _uiState.value.selectedPiperPackId -> {
                    settingsRepository.ttsPiperPackId = null
                    settingsRepository.ttssileroSpeaker = "irina"
                }
                _uiState.value.selectedNatashaPackId -> settingsRepository.ttsNatashaPackId = null
                _uiState.value.selectedUtrobinPackId -> settingsRepository.ttsUtrobinPackId = null
            }
            refreshOnnxPacks()
        } else {
            viewModelScope.launch {
                _events.emit(BookReaderEvent.ShowError("Не удалось удалить pack: $packId"))
            }
        }
    }

    fun deleteSuggestedTtsPacks() {
        val deleted = ttsPackLibrary.deleteSuggestedPacks()
        if (deleted > 0) {
            refreshOnnxPacks()
        } else {
            viewModelScope.launch {
                _events.emit(BookReaderEvent.ShowError("Нет удаляемых неподдерживаемых pack-ов"))
            }
        }
    }

    fun downloadTtsPack(packId: String) {
        val descriptor = _uiState.value.downloadableTtsPacks.firstOrNull { it.id == packId }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ttsPackLibrary.downloadPack(packId)
            if (ok) {
                when (descriptor?.engineFamily) {
                    TtsPackEngineFamily.PIPER -> {
                        descriptor.suggestedEnginePackId?.let { suggestedPackId ->
                            settingsRepository.ttsPiperPackId = suggestedPackId
                            ttsManager.setPackIdForEngine(TtsEngineType.SILERO, suggestedPackId)
                            settingsRepository.ttssileroSpeaker =
                                resolvePiperVoiceId(ttsPackLibrary.listDetectedPacks(), suggestedPackId) ?: settingsRepository.ttssileroSpeaker
                        }
                    }
                    TtsPackEngineFamily.NATASHA -> {
                        descriptor.suggestedEnginePackId?.let { suggestedPackId ->
                            settingsRepository.ttsNatashaPackId = suggestedPackId
                            ttsManager.setPackIdForEngine(TtsEngineType.NATASHA, suggestedPackId)
                        }
                    }
                    TtsPackEngineFamily.UTROBIN -> {
                        descriptor.suggestedEnginePackId?.let { suggestedPackId ->
                            settingsRepository.ttsUtrobinPackId = suggestedPackId
                            ttsManager.setPackIdForEngine(TtsEngineType.UTROBIN, suggestedPackId)
                        }
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            ttsManager.piperDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(piperDiagnostics = diagnostics) }
            }
        }
    }

    fun selectEnginePack(packId: String) {
        val selected = _uiState.value.detectedTtsPacks.firstOrNull { it.packId == packId } ?: return
        when (_uiState.value.engineType) {
            TtsEngineType.SILERO -> {
                settingsRepository.ttsPiperPackId = packId
                resolvePiperVoiceId(_uiState.value.detectedTtsPacks, packId)?.let { settingsRepository.ttssileroSpeaker = it }
                ttsManager.setPackIdForEngine(TtsEngineType.SILERO, packId)
                _uiState.update {
                    it.copy(
                        selectedPiperPackId = packId,
                        sileroVoiceId = resolvePiperVoiceId(_uiState.value.detectedTtsPacks, packId) ?: it.sileroVoiceId,
                    )
                }
            }
            TtsEngineType.NATASHA -> {
                settingsRepository.ttsNatashaPackId = packId
                ttsManager.setPackIdForEngine(TtsEngineType.NATASHA, packId)
                _uiState.update { it.copy(selectedNatashaPackId = packId) }
            }
            TtsEngineType.UTROBIN -> {
                settingsRepository.ttsUtrobinPackId = packId
                ttsManager.setPackIdForEngine(TtsEngineType.UTROBIN, packId)
                _uiState.update { it.copy(selectedUtrobinPackId = packId) }
            }
            else -> return
        }
        if (selected.isRunnable) {
            when (_uiState.value.engineType) {
                TtsEngineType.SILERO -> viewModelScope.launch { ttsManager.initializeSilero() }
                TtsEngineType.NATASHA -> viewModelScope.launch { ttsManager.initializeNatasha() }
                TtsEngineType.UTROBIN -> viewModelScope.launch { ttsManager.initializeUtrobin() }
                else -> {}
            }
        }
    }

    private fun observePackDownloads() {
        viewModelScope.launch {
            ttsPackLibrary.downloadState.collect { state ->
                _uiState.update {
                    it.copy(
                        packDownloadProgress = state?.progress,
                        packDownloadLabel = state?.label ?: state?.message,
                    )
                }
                if (state == null) {
                    lastPackDownloadErrorMessage = null
                    refreshOnnxPacks()
                } else if (state.isError && state.message != null && state.message != lastPackDownloadErrorMessage) {
                    lastPackDownloadErrorMessage = state.message
                    _events.emit(BookReaderEvent.ShowError(state.message))
                }
            }
        }
    }

    private fun saveProgress() {
        val bookEntity = _uiState.value.currentBookEntity ?: return
        val chapterIndex = _uiState.value.currentChapterIndex
        val chapterLength = _uiState.value.currentChapter?.content?.length ?: 0
        val position = _uiState.value.currentChapterPosition.coerceIn(0, chapterLength)
        viewModelScope.launch {
            bookRepository.updateReadingProgress(
                bookId = bookEntity.id,
                chapter = chapterIndex,
                position = position
            )
        }
    }

    fun resetCurrentBookProgress() {
        val book = _uiState.value.currentBook ?: return
        val bookEntity = _uiState.value.currentBookEntity ?: return
        stopTts()
        _uiState.update {
            it.copy(
                currentChapterIndex = 0,
                currentChapter = book.chapters.firstOrNull(),
                currentChapterPosition = 0,
                highlightRange = null
            )
        }
        viewModelScope.launch {
            bookRepository.updateReadingProgress(
                bookId = bookEntity.id,
                chapter = 0,
                position = 0
            )
        }
    }

    private fun getNotificationTitle(): String {
        return _uiState.value.currentBook?.title ?: "Book Reader"
    }

    private fun getNotificationSubtitle(): String {
        return _uiState.value.currentChapter?.title ?: ""
    }

    private fun updateServiceNotification() {
        if (TtsService.isRunning.value) {
            TtsService.updateNotification(appContext, getNotificationTitle(), getNotificationSubtitle())
        }
    }

    private fun resolveSavedPiperPackId(
        detected: List<DetectedTtsPack>,
        savedPackId: String?,
        legacyVoiceId: String?,
    ): String? {
        val piperPacks = detected.filter { it.engineFamily == TtsPackEngineFamily.PIPER }
        savedPackId?.takeIf { id -> piperPacks.any { it.packId == id } }?.let { validSaved ->
            resolvePiperVoiceId(piperPacks, validSaved)?.let { settingsRepository.ttssileroSpeaker = it }
            return validSaved
        }
        legacyVoiceId?.let { voiceId ->
            resolvePiperPackIdByVoiceId(piperPacks, voiceId)?.let { matchedPackId ->
                settingsRepository.ttsPiperPackId = matchedPackId
                settingsRepository.ttssileroSpeaker = voiceId
                return matchedPackId
            }
        }
        val fallback = piperPacks.firstOrNull { it.isRunnable }?.packId
        settingsRepository.ttsPiperPackId = fallback
        fallback?.let { packId ->
            resolvePiperVoiceId(piperPacks, packId)?.let { settingsRepository.ttssileroSpeaker = it }
        }
        return fallback
    }

    private fun resolvePiperPackIdByVoiceId(
        detected: List<DetectedTtsPack>,
        voiceId: String,
    ): String? {
        return detected
            .asSequence()
            .filter { it.engineFamily == TtsPackEngineFamily.PIPER }
            .firstOrNull { pack ->
                pack.voices.any { it.id.equals(voiceId, ignoreCase = true) }
            }
            ?.packId
    }

    private fun resolvePiperVoiceId(
        detected: List<DetectedTtsPack>,
        packId: String?,
    ): String? {
        val pack = packId?.let { id -> detected.firstOrNull { it.packId == id } } ?: return null
        return pack.voices.firstOrNull()?.id
    }

    override fun onCleared() {
        super.onCleared()
        if (!_uiState.value.isTtsPlaying) {
            ttsManager.shutdown()
            TtsService.stop(appContext)
        }
    }
}
