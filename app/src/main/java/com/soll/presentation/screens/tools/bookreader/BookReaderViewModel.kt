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
import com.soll.data.repository.ReaderWidgetBookState
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.extractReaderWidgetExcerpt
import com.soll.data.service.MusicPlaybackService
import com.soll.data.service.TtsService
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubChapter
import com.soll.domain.tts.NatashaPlaybackDiagnostics
import com.soll.domain.tts.PiperPlaybackDiagnostics
import com.soll.domain.tts.PiperProsodyPreset
import com.soll.domain.tts.TextToSpeechManager
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.TtsServiceAction
import com.soll.domain.tts.TtsState
import com.soll.domain.tts.UtrobinPlaybackDiagnostics
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.DownloadableTtsPack
import com.soll.domain.tts.catalog.TtsImportBrowserState
import com.soll.domain.tts.catalog.TtsPackEngineFamily
import com.soll.domain.tts.catalog.TtsPackLibrary
import com.soll.domain.tts.catalog.TtsTreeAccessState
import com.soll.domain.tts.chatterbox.ChatterboxPlaybackDiagnostics
import com.soll.domain.tts.kokoro.KokoroPlaybackDiagnostics
import com.soll.domain.tts.onnx.InstalledOnnxPack
import com.soll.domain.tts.onnx.OnnxModelPackManager
import com.soll.presentation.widgets.ReaderWidgetProvider
import com.soll.presentation.widgets.ReaderWidgetStateStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.ArrayDeque
import kotlin.math.abs
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
    // Системные TTS-движки Android
    val availableEngines: List<TextToSpeech.EngineInfo> = emptyList(),
    val selectedEngine: String? = null,
    // Engine type
    val engineType: TtsEngineType = TtsEngineType.SYSTEM,
    val sileroModelDownloaded: Boolean = false,
    val sileroDownloadProgress: Float? = null,
    val sileroVoiceId: String = "irina",
    val utrobinVoiceId: String = "0",
    val chatterboxVoiceId: String? = null,
    /** Utrobin ONNX intra-op threads (1–4), persisted. */
    val utrobinOrtThreads: Int = 2,
    val natashaOrtThreads: Int = 2,
    val chatterboxOrtThreads: Int = 2,
    val chatterboxExaggeration: Float = 0.5f,
    val sherpaThreads: Int = 2,
    val performanceProfile: TtsBookPerformanceProfile = TtsBookPerformanceProfile.BALANCED,
    val piperProsodyPreset: PiperProsodyPreset = PiperProsodyPreset.DEFAULT,
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
    val lastTtsImportSummary: String? = null,
    val ttsImportBrowser: TtsImportBrowserState = TtsImportBrowserState(),
    val isTtsImporting: Boolean = false,
    val hasDirectFilesystemTtsAccess: Boolean = false,
    val commonFilesystemTtsRoots: List<String> = emptyList(),
    val selectedPiperPackId: String? = null,
    val selectedNatashaPackId: String? = null,
    val selectedUtrobinPackId: String? = null,
    val selectedChatterboxPackId: String? = null,
    val piperDiagnostics: PiperPlaybackDiagnostics = PiperPlaybackDiagnostics(),
    val natashaDiagnostics: NatashaPlaybackDiagnostics = NatashaPlaybackDiagnostics(),
    val utrobinDiagnostics: UtrobinPlaybackDiagnostics = UtrobinPlaybackDiagnostics(),
    val chatterboxDiagnostics: ChatterboxPlaybackDiagnostics = ChatterboxPlaybackDiagnostics(),
    val onnxDiagnostics: KokoroPlaybackDiagnostics = KokoroPlaybackDiagnostics(),
)

sealed class BookReaderEvent {
    data class ShowError(val message: String) : BookReaderEvent()
    data class BookImported(val title: String) : BookReaderEvent()
    data class TtsPacksImported(val importedCount: Int, val failedCount: Int = 0) : BookReaderEvent()
}

private const val READER_PROGRESS_AUTOSAVE_INTERVAL_MS = 15_000L
private const val READER_PROGRESS_AUTOSAVE_CHAR_DELTA = 480

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
    private var lastTtsErrorMessage: String? = null
    private var hadActivePackDownload: Boolean = false
    private var showNextTtsErrorToast: Boolean = false
    private val ttsBrowserStack = ArrayDeque<TtsBrowserLocation>()
    private var lastProgressBookId: Long? = null
    private var lastProgressChapterIndex: Int = -1
    private var lastProgressPosition: Int = -1
    private var lastProgressSavedAtMs: Long = 0L

    init {
        loadBooks()
        loadSettings()
        observeTtsState()
        observeChapterFinished()
        observeServiceActions()
        observePackDownloads()
        observeEngineDiagnostics()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ensureS200BookReaderBootstrap()
            }
            val profile = TtsBookPerformanceProfile.fromStorage(settingsRepository.ttsBookPerformanceProfile)
            ttsManager.applyBookPerformanceProfile(profile)
            val catalog = loadTtsCatalogSnapshot(parseStoredTtsRootUri())
            val detectedPacks = catalog.detectedPacks
            val downloadablePacks = catalog.downloadablePacks

            val sileroVoice = settingsRepository.ttssileroSpeaker
            val utrobinVoice = settingsRepository.ttsUtrobinSpeaker
            val piperPackId = resolveSavedPiperPackId(
                detected = detectedPacks,
                savedPackId = settingsRepository.ttsPiperPackId,
                legacyVoiceId = sileroVoice,
            )
            val rawEngineType = readStoredReaderEngineType()
            val engineType = resolveReaderEngineType(rawEngineType, piperPackId)
            if (engineType != rawEngineType) {
                persistReaderEngineType(engineType)
            }
            val natashaPackId = resolveSavedPackId(
                detected = detectedPacks,
                family = TtsPackEngineFamily.NATASHA,
                savedPackId = settingsRepository.ttsNatashaPackId,
            )
            val utrobinPackId = resolveSavedPackId(
                detected = detectedPacks,
                family = TtsPackEngineFamily.UTROBIN,
                savedPackId = settingsRepository.ttsUtrobinPackId,
            )
            val chatterboxPackId = resolveSavedPackId(
                detected = detectedPacks,
                family = TtsPackEngineFamily.CHATTERBOX,
                savedPackId = settingsRepository.ttsChatterboxPackId,
            )
            val chatterboxVoice = resolveSavedChatterboxVoiceId(
                detected = detectedPacks,
                packId = chatterboxPackId,
                savedVoiceId = settingsRepository.ttsChatterboxVoice,
            )
            val utrobinOrt = settingsRepository.ttsUtrobinOrtIntraThreads
            val natashaOrt = settingsRepository.ttsNatashaOrtIntraThreads
            val chatterboxOrt = settingsRepository.ttsChatterboxOrtIntraThreads
            val chatterboxExaggeration = settingsRepository.ttsChatterboxExaggeration
            val sherpaTh = settingsRepository.ttsSherpaNumThreads
            val piperProsodyPreset = PiperProsodyPreset.fromStorage(settingsRepository.ttsPiperProsodyPreset)
            val pitch = settingsRepository.ttsSystemPitch
            ttsManager.setEngineType(engineType)
            ttsManager.setVoiceIdForEngine(TtsEngineType.UTROBIN, utrobinVoice)
            ttsManager.setPackIdForEngine(TtsEngineType.SILERO, piperPackId)
            ttsManager.applyTunableForEngine(TtsEngineType.SILERO, "sherpa_num_threads", sherpaTh.toFloat())
            ttsManager.applyPiperProsodyPreset(piperProsodyPreset)
            ttsManager.setPitch(pitch)
            val installedOnnxPacks = catalog.installedOnnxPacks
            val savedPackKey = buildOnnxPackKey(settingsRepository.ttsOnnxModelId, settingsRepository.ttsOnnxPrecision)
            val selectedOnnxPack = installedOnnxPacks.firstOrNull {
                buildOnnxPackKey(it.modelId, it.precision) == savedPackKey
            } ?: catalog.bestOnnxPack
            Timber.d(
                "BookReader loadSettings engine=%s detectedPacks=%d downloadable=%d savedTtsRoot=%s",
                engineType,
                detectedPacks.size,
                downloadablePacks.size,
                settingsRepository.ttsModelRootUri,
            )
            ttsManager.setSelectedOnnxPack(null)
            _uiState.update {
                it.copy(
                    autoAdvanceEnabled = settingsRepository.ttsAutoAdvance,
                    selectedEngine = settingsRepository.ttsEngine,
                    speechRate = settingsRepository.ttsSpeechRate,
                    engineType = engineType,
                    sileroModelDownloaded = ttsManager.isModelDownloaded(),
                    sileroVoiceId = resolvePiperVoiceId(detectedPacks, piperPackId) ?: sileroVoice,
                    utrobinVoiceId = utrobinVoice,
                    chatterboxVoiceId = chatterboxVoice,
                    utrobinOrtThreads = utrobinOrt,
                    natashaOrtThreads = natashaOrt,
                    chatterboxOrtThreads = chatterboxOrt,
                    chatterboxExaggeration = chatterboxExaggeration,
                    sherpaThreads = sherpaTh,
                    performanceProfile = profile,
                    piperProsodyPreset = piperProsodyPreset,
                    systemPitch = pitch,
                    ttsVoiceOptions = if (engineType == TtsEngineType.SILERO) emptyList() else ttsManager.voiceOptions(engineType),
                    engineTunables = ttsManager.tunableSettingsFor(engineType),
                    installedOnnxPacks = installedOnnxPacks,
                    selectedOnnxPackKey = selectedOnnxPack?.let { p -> buildOnnxPackKey(p.modelId, p.precision) },
                    detectedTtsPacks = detectedPacks,
                    downloadableTtsPacks = downloadablePacks,
                    lastTtsModelRootUri = settingsRepository.ttsModelRootUri,
                    ttsImportBrowser = catalog.importBrowser,
                    hasDirectFilesystemTtsAccess = catalog.hasDirectFilesystemTtsAccess,
                    commonFilesystemTtsRoots = catalog.commonFilesystemTtsRoots,
                    selectedPiperPackId = piperPackId,
                    selectedNatashaPackId = natashaPackId,
                    selectedUtrobinPackId = utrobinPackId,
                    selectedChatterboxPackId = chatterboxPackId,
                    piperDiagnostics = ttsManager.piperDiagnostics.value,
                    natashaDiagnostics = ttsManager.natashaDiagnostics.value,
                    utrobinDiagnostics = ttsManager.utrobinDiagnostics.value,
                    chatterboxDiagnostics = ttsManager.chatterboxDiagnostics.value,
                    onnxDiagnostics = ttsManager.onnxDiagnostics.value,
                )
            }
            initTts()
        }
    }

    private fun ensureS200BookReaderBootstrap() {
        if (settingsRepository.bookReaderS200BootstrapDone) return
        if (isLikelyDoogeeS200()) {
            settingsRepository.ttsEngineType = when {
                ttsPackLibrary.findBestPack(TtsEngineType.SILERO) != null -> "silero"
                else -> "system"
            }
            settingsRepository.ttsBookPerformanceProfile = TtsBookPerformanceProfile.BALANCED.storageKey
            settingsRepository.syncThreadPrefsFromProfile(TtsBookPerformanceProfile.BALANCED)
        }
        settingsRepository.bookReaderS200BootstrapDone = true
    }

    private fun readStoredReaderEngineType(): TtsEngineType =
        when (settingsRepository.ttsEngineType) {
            "silero" -> TtsEngineType.SILERO
            "utrobin" -> TtsEngineType.UTROBIN
            "natasha" -> TtsEngineType.NATASHA
            "chatterbox" -> TtsEngineType.CHATTERBOX
            "onnx_external" -> TtsEngineType.ONNX_EXTERNAL
            "system" -> TtsEngineType.SYSTEM
            else -> TtsEngineType.SYSTEM
        }

    private fun resolveReaderEngineType(
        stored: TtsEngineType,
        piperPackId: String?,
    ): TtsEngineType =
        when {
            stored == TtsEngineType.SILERO && piperPackId != null -> TtsEngineType.SILERO
            stored == TtsEngineType.SILERO -> TtsEngineType.SYSTEM
            stored == TtsEngineType.SYSTEM -> TtsEngineType.SYSTEM
            piperPackId != null -> TtsEngineType.SILERO
            else -> TtsEngineType.SYSTEM
        }

    private fun persistReaderEngineType(type: TtsEngineType) {
        settingsRepository.ttsEngineType = when (type) {
            TtsEngineType.SILERO -> "silero"
            else -> "system"
        }
    }

    private fun isReaderEngineEnabled(type: TtsEngineType): Boolean =
        type == TtsEngineType.SYSTEM || type == TtsEngineType.SILERO

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
    }

    fun setPiperProsodyPreset(preset: PiperProsodyPreset) {
        if (_uiState.value.piperProsodyPreset == preset) return
        if (_uiState.value.engineType == TtsEngineType.SILERO && _uiState.value.isTtsPlaying) {
            stopTts()
        }
        settingsRepository.ttsPiperProsodyPreset = preset.storageKey
        ttsManager.applyPiperProsodyPreset(preset)
        _uiState.update {
            it.copy(
                piperProsodyPreset = preset,
                piperDiagnostics = ttsManager.piperDiagnostics.value,
            )
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
                    val shouldShow = showNextTtsErrorToast || _uiState.value.isTtsPlaying
                    if (shouldShow && state.message != lastTtsErrorMessage) {
                        lastTtsErrorMessage = state.message
                        showNextTtsErrorToast = false
                        _events.emit(BookReaderEvent.ShowError(state.message))
                    }
                } else {
                    lastTtsErrorMessage = null
                    showNextTtsErrorToast = false
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
                range?.first?.let { saveProgressIfNeeded(it) }
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

    private suspend fun loadTtsCatalogSnapshot(
        rootUri: Uri?,
        currentUri: Uri? = null,
    ): TtsCatalogSnapshot = withContext(Dispatchers.IO) {
        TtsCatalogSnapshot(
            detectedPacks = ttsPackLibrary.listDetectedPacks(),
            downloadablePacks = ttsPackLibrary.listDownloadableRussianPacks(),
            installedOnnxPacks = onnxModelPackManager.listInstalledPacks(),
            bestOnnxPack = onnxModelPackManager.pickBestRussianPack(),
            importBrowser = ttsPackLibrary.browseImportTree(rootUri, currentUri),
            hasDirectFilesystemTtsAccess = ttsPackLibrary.hasDirectFilesystemAccess(),
            commonFilesystemTtsRoots = ttsPackLibrary.listCommonFilesystemRootPaths(),
        )
    }

    private suspend fun browseTtsImportTree(
        rootUri: Uri?,
        currentUri: Uri? = null,
    ): TtsImportBrowserState = withContext(Dispatchers.IO) {
        ttsPackLibrary.browseImportTree(rootUri, currentUri)
    }

    private suspend fun loadTtsStorageAccessSnapshot(): TtsStorageAccessSnapshot = withContext(Dispatchers.IO) {
        TtsStorageAccessSnapshot(
            hasDirectFilesystemTtsAccess = ttsPackLibrary.hasDirectFilesystemAccess(),
            commonFilesystemTtsRoots = ttsPackLibrary.listCommonFilesystemRootPaths(),
        )
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
                    _events.emit(BookReaderEvent.ShowError(error.message ?: "Не удалось импортировать книгу"))
                }
            )
        }
    }

    fun openBook(bookEntity: BookEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val epubBook = bookRepository.parseBook(bookEntity)
            if (epubBook != null && epubBook.chapters.isNotEmpty()) {
                val chapterIndex = bookEntity.currentChapter.coerceIn(0, epubBook.chapters.size - 1)
                val chapter = epubBook.chapters.getOrNull(chapterIndex)
                val savedPosition = bookEntity.currentPosition
                    .coerceIn(0, chapter?.content?.length ?: 0)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentBook = epubBook,
                        currentBookEntity = bookEntity,
                        currentChapter = chapter,
                        currentChapterIndex = chapterIndex,
                        currentChapterPosition = savedPosition
                    )
                }
                saveProgress()
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Не удалось разобрать книгу") }
                _events.emit(BookReaderEvent.ShowError("Не удалось разобрать книгу"))
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
        _uiState.update {
            it.copy(
                currentChapter = book.chapters[index],
                currentChapterIndex = index,
                currentChapterPosition = 0
            )
        }
        saveProgress()
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
        saveProgress()
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
        Timber.i(
            "BookReader playTts requested engine=%s chapter=%s chars=%d state=%s isPlaying=%s",
            _uiState.value.engineType,
            chapter.title,
            chapter.content.length,
            _uiState.value.ttsState,
            _uiState.value.isTtsPlaying,
        )
        saveProgress()

        if (!isReaderEngineEnabled(_uiState.value.engineType)) {
            val fallback = if (_uiState.value.selectedPiperPackId != null) {
                TtsEngineType.SILERO
            } else {
                TtsEngineType.SYSTEM
            }
            persistReaderEngineType(fallback)
            ttsManager.setEngineType(fallback)
            _uiState.update {
                it.copy(
                    engineType = fallback,
                    ttsVoiceOptions = if (fallback == TtsEngineType.SILERO) emptyList() else ttsManager.voiceOptions(fallback),
                    engineTunables = ttsManager.tunableSettingsFor(fallback),
                )
            }
        }

        if (_uiState.value.engineType == TtsEngineType.SILERO && !ttsManager.isEngineReady(TtsEngineType.SILERO)) {
            viewModelScope.launch {
                showNextTtsErrorToast = true
                val success = ttsManager.initializeSilero()
                if (success) startPlayback(chapter)
                else _events.emit(
                    BookReaderEvent.ShowError(
                        "Piper/Sherpa не готов: проверь локальный Piper-пакет в папке tts. Нужны ONNX-голос, tokens.txt и espeak-ng-data.",
                    ),
                )
            }
            return
        }

        startPlayback(chapter)
    }

    private fun startPlayback(chapter: EpubChapter) {
        if (settingsRepository.musicPauseForTts) {
            MusicPlaybackService.pause(appContext)
        }
        val state = _uiState.value.ttsState
        if (state is TtsState.Paused) {
            ttsManager.resume()
        } else {
            ttsManager.speakChapter(chapter.content)
        }
        TtsService.start(
            appContext,
            getNotificationTitle(),
            getNotificationSubtitle(),
            getNotificationCoverPath(),
        )
    }

    fun pauseTts() {
        saveProgress()
        ttsManager.pause()
        TtsService.updatePlaybackState(appContext, false)
    }

    fun stopTts() {
        saveProgress()
        ttsManager.stop()
        TtsService.stop(appContext)
    }

    fun toggleTts() {
        Timber.i(
            "BookReader toggleTts isPlaying=%s state=%s engine=%s",
            _uiState.value.isTtsPlaying,
            _uiState.value.ttsState,
            _uiState.value.engineType,
        )
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
        if (!isReaderEngineEnabled(type)) {
            viewModelScope.launch {
                _events.emit(BookReaderEvent.ShowError("В читалке сейчас включены только Piper и системный TTS Android. Остальные офлайн-движки отключены."))
            }
            return
        }
        if (!hasRunnablePackForEngine(type)) {
            viewModelScope.launch {
                _events.emit(BookReaderEvent.ShowError("Для Piper нет рабочей русской модели. Скачай один из Piper-голосов."))
            }
            return
        }
        stopTts()
        ttsManager.setEngineType(type)
        persistReaderEngineType(type)
        _uiState.update {
            it.copy(
                engineType = type,
                ttsVoiceOptions = if (type == TtsEngineType.SILERO) emptyList() else ttsManager.voiceOptions(type),
                engineTunables = ttsManager.tunableSettingsFor(type),
                utrobinOrtThreads = settingsRepository.ttsUtrobinOrtIntraThreads,
                natashaOrtThreads = settingsRepository.ttsNatashaOrtIntraThreads,
                chatterboxOrtThreads = settingsRepository.ttsChatterboxOrtIntraThreads,
                chatterboxExaggeration = settingsRepository.ttsChatterboxExaggeration,
                sherpaThreads = settingsRepository.ttsSherpaNumThreads,
            )
        }
    }

    private fun hasRunnablePackForEngine(type: TtsEngineType): Boolean {
        val state = _uiState.value
        return when (type) {
            TtsEngineType.SYSTEM -> true
            TtsEngineType.SILERO -> state.selectedPiperPackId != null
            else -> false
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
            TtsEngineType.CHATTERBOX -> {
                settingsRepository.ttsChatterboxVoice = voiceId
                _uiState.update { it.copy(chatterboxVoiceId = voiceId) }
                ttsManager.setVoiceIdForEngine(TtsEngineType.CHATTERBOX, voiceId)
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
        if (type == TtsEngineType.CHATTERBOX && key == "chatterbox_ort_intra_threads") {
            val v = value.roundToInt().coerceIn(1, 4)
            settingsRepository.ttsChatterboxOrtIntraThreads = v
            _uiState.update { it.copy(chatterboxOrtThreads = v) }
        }
        if (type == TtsEngineType.CHATTERBOX && key == "chatterbox_exaggeration") {
            val v = value.coerceIn(0.3f, 0.9f)
            settingsRepository.ttsChatterboxExaggeration = v
            _uiState.update { it.copy(chatterboxExaggeration = v) }
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

    fun openTtsImportBrowser() {
        val rootUri = parseStoredTtsRootUri()
        viewModelScope.launch {
            val browserState = browseTtsImportTree(rootUri)
            ttsBrowserStack.clear()
            browserState.currentUri?.let { currentUri ->
                ttsBrowserStack.addLast(TtsBrowserLocation(currentUri, browserState.currentLabel ?: "tts"))
            }
            _uiState.update {
                it.copy(
                    lastTtsModelRootUri = settingsRepository.ttsModelRootUri,
                    ttsImportBrowser = browserState,
                )
            }
        }
    }

    fun onTtsTreeSelected(treeUri: Uri) {
        Timber.i("Selected TTS root tree=%s", treeUri)
        settingsRepository.ttsModelRootUri = treeUri.toString()
        viewModelScope.launch {
            val browserState = browseTtsImportTree(treeUri)
            ttsBrowserStack.clear()
            browserState.currentUri?.let { currentUri ->
                ttsBrowserStack.addLast(TtsBrowserLocation(currentUri, browserState.currentLabel ?: "tts"))
            }
            _uiState.update {
                it.copy(
                    lastTtsModelRootUri = treeUri.toString(),
                    ttsImportBrowser = browserState,
                )
            }
        }
    }

    fun onTtsTreePickerCancelled() {
        _uiState.update {
            it.copy(
                lastTtsModelRootUri = settingsRepository.ttsModelRootUri,
                ttsImportBrowser = ttsPackLibrary.pickerCancelledState(settingsRepository.ttsModelRootUri),
            )
        }
    }

    fun enterTtsImportDirectory(uriString: String, label: String) {
        val rootUri = parseStoredTtsRootUri() ?: return
        val targetUri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        viewModelScope.launch {
            val browserState = browseTtsImportTree(rootUri, targetUri)
            if (browserState.accessState == TtsTreeAccessState.READY) {
                val lastUri = ttsBrowserStack.lastOrNull()?.uri
                if (lastUri != uriString) {
                    ttsBrowserStack.addLast(TtsBrowserLocation(uriString, label))
                }
            }
            _uiState.update { it.copy(ttsImportBrowser = browserState) }
        }
    }

    fun leaveTtsImportDirectory() {
        val rootUri = parseStoredTtsRootUri() ?: return
        if (ttsBrowserStack.size > 1) {
            ttsBrowserStack.removeLast()
        }
        val current = ttsBrowserStack.lastOrNull()
        viewModelScope.launch {
            val browserState = browseTtsImportTree(rootUri, current?.uri?.let(Uri::parse))
            _uiState.update { it.copy(ttsImportBrowser = browserState) }
        }
    }

    fun refreshTtsImportBrowser() {
        viewModelScope.launch {
            refreshTtsImportBrowserNow()
        }
    }

    fun refreshStorageAccessState() {
        viewModelScope.launch {
            val storage = loadTtsStorageAccessSnapshot()
            _uiState.update {
                it.copy(
                    hasDirectFilesystemTtsAccess = storage.hasDirectFilesystemTtsAccess,
                    commonFilesystemTtsRoots = storage.commonFilesystemTtsRoots,
                )
            }
        }
    }

    private suspend fun refreshTtsImportBrowserNow() {
        val rootUri = parseStoredTtsRootUri()
        val currentUri = _uiState.value.ttsImportBrowser.currentUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val browserState = browseTtsImportTree(rootUri, currentUri)
        val storage = loadTtsStorageAccessSnapshot()
        _uiState.update {
            it.copy(
                lastTtsModelRootUri = settingsRepository.ttsModelRootUri,
                ttsImportBrowser = browserState,
                hasDirectFilesystemTtsAccess = storage.hasDirectFilesystemTtsAccess,
                commonFilesystemTtsRoots = storage.commonFilesystemTtsRoots,
            )
        }
    }

    private fun startTtsImport(message: String): Boolean {
        if (_uiState.value.isTtsImporting) {
            _uiState.update {
                it.copy(
                    lastTtsImportSummary = "Импорт TTS уже идёт. Дождись завершения текущей операции.",
                    ttsImportBrowser = it.ttsImportBrowser.copy(message = "Импорт TTS уже идёт."),
                )
            }
            return false
        }
        _uiState.update {
            it.copy(
                isTtsImporting = true,
                lastTtsImportSummary = message,
                ttsImportBrowser = it.ttsImportBrowser.copy(message = message),
            )
        }
        return true
    }

    private fun finishTtsImport() {
        _uiState.update { it.copy(isTtsImporting = false) }
    }

    fun importTtsFromPickedDocument(documentUri: Uri) {
        Timber.i("Importing TTS from picked document uri=%s", documentUri)
        if (!startTtsImport("Импорт TTS по выбранному файлу...")) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ttsPackLibrary.importFromPickedDocument(documentUri) }
                }
                result.fold(
                    onFailure = { error ->
                        Timber.e(error, "Failed to import TTS from picked document=%s", documentUri)
                        val summary = "Импорт по выбранному файлу не удался: ${error.message ?: "неизвестная ошибка"}"
                        _uiState.update {
                            it.copy(
                                lastTtsImportSummary = summary,
                                ttsImportBrowser = it.ttsImportBrowser.copy(message = summary),
                            )
                        }
                    },
                    onSuccess = { importResult ->
                        val summary = buildString {
                            append("Импорт по файлу: найдено ${importResult.detectedCount}, подключено ${importResult.importedCount}, проблемных ${importResult.failedCount}.")
                            if (importResult.issues.isNotEmpty()) {
                                append('\n')
                                append(importResult.issues.take(3).joinToString(separator = "\n"))
                            }
                        }
                        refreshTtsPacksNow()
                        val storage = loadTtsStorageAccessSnapshot()
                        _uiState.update {
                            it.copy(
                                lastTtsImportSummary = summary,
                                ttsImportBrowser = it.ttsImportBrowser.copy(message = summary),
                                hasDirectFilesystemTtsAccess = storage.hasDirectFilesystemTtsAccess,
                                commonFilesystemTtsRoots = storage.commonFilesystemTtsRoots,
                            )
                        }
                        if (importResult.importedCount > 0) {
                            reinitializeCurrentOfflineEngineIfPossible()
                            _events.emit(
                                BookReaderEvent.TtsPacksImported(
                                    importedCount = importResult.importedCount,
                                    failedCount = importResult.failedCount,
                                ),
                            )
                        }
                    },
                )
            } finally {
                finishTtsImport()
            }
        }
    }

    fun importTtsCandidatesFromBrowser(sourceUris: Set<String>) {
        val rootUri = parseStoredTtsRootUri() ?: run {
            _uiState.update {
                it.copy(
                    ttsImportBrowser = ttsPackLibrary.pickerCancelledState(null).copy(
                        message = "Для ручного импорта выбери любой файл внутри модели. Для обычного сценария используй автоимпорт.",
                    ),
                )
            }
            return
        }
        Timber.i("Importing selected TTS packs from root=%s count=%d", rootUri, sourceUris.size)
        if (!startTtsImport("Импорт TTS из выбранного списка...")) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (sourceUris.isEmpty()) {
                            ttsPackLibrary.importFromTreeUri(rootUri)
                        } else {
                            ttsPackLibrary.importSelectedFromTreeUri(rootUri, sourceUris)
                        }
                    }
                }
                result.fold(
                    onFailure = { e ->
                        Timber.e(e, "TTS pack import failed for root=%s", rootUri)
                        _uiState.update {
                            it.copy(
                                lastTtsImportSummary = "Импорт не удался: ${e.message ?: "неизвестная ошибка"}",
                                ttsImportBrowser = it.ttsImportBrowser.copy(
                                    message = "Импорт не удался: ${e.message ?: "неизвестная ошибка"}",
                                ),
                            )
                        }
                    },
                    onSuccess = { importResult ->
                        Timber.i(
                            "TTS pack import finished root=%s detected=%d imported=%d failed=%d",
                            rootUri,
                            importResult.detectedCount,
                            importResult.importedCount,
                            importResult.failedCount,
                        )
                        val importSummary = buildString {
                            append("Проверено кандидатов: ${importResult.detectedCount}. ")
                            append("Подключено: ${importResult.importedCount}. ")
                            append("Проблемных: ${importResult.failedCount}.")
                            if (importResult.issues.isNotEmpty()) {
                                append('\n')
                                append(importResult.issues.take(3).joinToString(separator = "\n"))
                            }
                        }
                        refreshTtsPacksNow()
                        refreshTtsImportBrowserNow()
                        _uiState.update {
                            it.copy(
                                lastTtsImportSummary = importSummary,
                                ttsImportBrowser = it.ttsImportBrowser.copy(message = importSummary),
                            )
                        }
                        reinitializeCurrentOfflineEngineIfPossible()
                        if (importResult.importedCount > 0) {
                            _events.emit(
                                BookReaderEvent.TtsPacksImported(
                                    importedCount = importResult.importedCount,
                                    failedCount = importResult.failedCount,
                                ),
                            )
                        }
                    },
                )
            } finally {
                finishTtsImport()
            }
        }
    }

    fun importAllVisibleTtsCandidates() {
        importTtsCandidatesFromBrowser(
            _uiState.value.ttsImportBrowser.candidates
                .map { it.sourceUri }
                .toSet(),
        )
    }

    fun importTtsFromCommonFilesystemRoots() {
        if (!startTtsImport("Автопоиск TTS в /Download/tts...")) return
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ttsPackLibrary.importFromCommonFilesystemRoots()
                }
                val summary = buildString {
                    append("Автопоиск tts: найдено ${result.detectedCount}, подключено ${result.importedCount}, проблемных ${result.failedCount}.")
                    if (result.issues.isNotEmpty()) {
                        append('\n')
                        append(result.issues.take(3).joinToString(separator = "\n"))
                    }
                }
                refreshTtsPacksNow()
                val storage = loadTtsStorageAccessSnapshot()
                _uiState.update {
                    it.copy(
                        lastTtsImportSummary = summary,
                        ttsImportBrowser = it.ttsImportBrowser.copy(message = summary),
                        hasDirectFilesystemTtsAccess = storage.hasDirectFilesystemTtsAccess,
                        commonFilesystemTtsRoots = storage.commonFilesystemTtsRoots,
                    )
                }
                if (result.importedCount > 0) {
                    reinitializeCurrentOfflineEngineIfPossible()
                    _events.emit(
                        BookReaderEvent.TtsPacksImported(
                            importedCount = result.importedCount,
                            failedCount = result.failedCount,
                        ),
                    )
                }
            } finally {
                finishTtsImport()
            }
        }
    }

    fun refreshTtsPacks() {
        viewModelScope.launch {
            refreshTtsPacksNow()
        }
    }

    private suspend fun refreshTtsPacksNow() {
        val catalog = loadTtsCatalogSnapshot(parseStoredTtsRootUri())
        val detected = catalog.detectedPacks
        val downloadable = catalog.downloadablePacks
        val piperPackId = resolveSavedPiperPackId(
            detected = detected,
            savedPackId = settingsRepository.ttsPiperPackId,
            legacyVoiceId = settingsRepository.ttssileroSpeaker,
        )
        val natashaPackId = resolveSavedPackId(
            detected = detected,
            family = TtsPackEngineFamily.NATASHA,
            savedPackId = settingsRepository.ttsNatashaPackId,
        )
        val utrobinPackId = resolveSavedPackId(
            detected = detected,
            family = TtsPackEngineFamily.UTROBIN,
            savedPackId = settingsRepository.ttsUtrobinPackId,
        )
        val chatterboxPackId = resolveSavedPackId(
            detected = detected,
            family = TtsPackEngineFamily.CHATTERBOX,
            savedPackId = settingsRepository.ttsChatterboxPackId,
        )
        val chatterboxVoice = resolveSavedChatterboxVoiceId(
            detected = detected,
            packId = chatterboxPackId,
            savedVoiceId = settingsRepository.ttsChatterboxVoice,
        )
        val packs = catalog.installedOnnxPacks
        val selected = packs.firstOrNull {
            buildOnnxPackKey(it.modelId, it.precision) == _uiState.value.selectedOnnxPackKey
        } ?: catalog.bestOnnxPack
        ttsManager.setSelectedOnnxPack(null)
        ttsManager.setPackIdForEngine(TtsEngineType.SILERO, piperPackId)
        val resolvedEngine = resolveReaderEngineType(_uiState.value.engineType, piperPackId)
        if (resolvedEngine != _uiState.value.engineType) {
            persistReaderEngineType(resolvedEngine)
            ttsManager.setEngineType(resolvedEngine)
        }
        Timber.d(
            "refreshTtsPacks detected=%d downloadable=%d selectedPiper=%s selectedNatasha=%s selectedUtrobin=%s selectedChatterbox=%s savedRoot=%s",
            detected.size,
            downloadable.size,
            piperPackId,
            natashaPackId,
            utrobinPackId,
            chatterboxPackId,
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
                ttsImportBrowser = it.ttsImportBrowser.takeIf { browser -> browser.rootUri != null }
                    ?: catalog.importBrowser,
                hasDirectFilesystemTtsAccess = catalog.hasDirectFilesystemTtsAccess,
                commonFilesystemTtsRoots = catalog.commonFilesystemTtsRoots,
                engineType = resolvedEngine,
                ttsVoiceOptions = if (resolvedEngine == TtsEngineType.SILERO) emptyList() else ttsManager.voiceOptions(resolvedEngine),
                engineTunables = ttsManager.tunableSettingsFor(resolvedEngine),
                sileroVoiceId = resolvePiperVoiceId(detected, piperPackId) ?: settingsRepository.ttssileroSpeaker,
                selectedPiperPackId = piperPackId,
                selectedNatashaPackId = natashaPackId,
                selectedUtrobinPackId = utrobinPackId,
                selectedChatterboxPackId = chatterboxPackId,
                chatterboxVoiceId = chatterboxVoice,
            )
        }
    }

    fun deleteBook(bookEntity: BookEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.deleteBook(bookEntity.id)
            ReaderWidgetStateStore.clear(appContext)
            ReaderWidgetProvider.updateAll(appContext)
        }
    }

    fun deleteTtsPack(packId: String) {
        if (shouldRestartForPackMutation(setOf(packId))) {
            stopTts()
        }
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                ttsPackLibrary.deletePack(packId)
            }
            if (deleted) {
                when (packId) {
                    _uiState.value.selectedPiperPackId -> {
                        settingsRepository.ttsPiperPackId = null
                        settingsRepository.ttssileroSpeaker = "irina"
                    }
                    _uiState.value.selectedNatashaPackId -> settingsRepository.ttsNatashaPackId = null
                    _uiState.value.selectedUtrobinPackId -> settingsRepository.ttsUtrobinPackId = null
                    _uiState.value.selectedChatterboxPackId -> {
                        settingsRepository.ttsChatterboxPackId = null
                        settingsRepository.ttsChatterboxVoice = null
                    }
                }
                refreshTtsPacksNow()
                reinitializeCurrentOfflineEngineIfPossible()
            } else {
                _events.emit(BookReaderEvent.ShowError("Не удалось удалить пакет: $packId"))
            }
        }
    }

    fun deleteSuggestedTtsPacks() {
        val deletedPackIds = _uiState.value.detectedTtsPacks
            .filter { (it.suggestedDeletion || it.engineFamily != TtsPackEngineFamily.PIPER) && it.canDelete }
            .map { it.packId }
            .toSet()
        if (shouldRestartForPackMutation(deletedPackIds)) {
            stopTts()
        }
        viewModelScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                ttsPackLibrary.deleteSuggestedPacks()
            }
            if (deleted > 0) {
                refreshTtsPacksNow()
                reinitializeCurrentOfflineEngineIfPossible()
            } else {
                _events.emit(BookReaderEvent.ShowError("Нет удаляемых неподдерживаемых пакетов"))
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
                            settingsRepository.ttsEngineType = "silero"
                            ttsManager.setPackIdForEngine(TtsEngineType.SILERO, suggestedPackId)
                            ttsManager.setEngineType(TtsEngineType.SILERO)
                            settingsRepository.ttssileroSpeaker =
                                resolvePiperVoiceId(ttsPackLibrary.listDetectedPacks(), suggestedPackId) ?: settingsRepository.ttssileroSpeaker
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun selectEnginePack(packId: String) {
        val selected = _uiState.value.detectedTtsPacks.firstOrNull { it.packId == packId } ?: return
        if (selected.engineFamily != TtsPackEngineFamily.PIPER) {
            viewModelScope.launch {
                _events.emit(BookReaderEvent.ShowError("Сейчас в читалке поддерживаются только Piper-пакеты."))
            }
            return
        }
        settingsRepository.ttsPiperPackId = packId
        settingsRepository.ttsEngineType = "silero"
        resolvePiperVoiceId(_uiState.value.detectedTtsPacks, packId)?.let { settingsRepository.ttssileroSpeaker = it }
        ttsManager.setPackIdForEngine(TtsEngineType.SILERO, packId)
        ttsManager.setEngineType(TtsEngineType.SILERO)
        _uiState.update {
            it.copy(
                engineType = TtsEngineType.SILERO,
                selectedPiperPackId = packId,
                sileroVoiceId = resolvePiperVoiceId(_uiState.value.detectedTtsPacks, packId) ?: it.sileroVoiceId,
                ttsVoiceOptions = emptyList(),
                engineTunables = ttsManager.tunableSettingsFor(TtsEngineType.SILERO),
            )
        }
        if (selected.isRunnable) {
            viewModelScope.launch {
                showNextTtsErrorToast = true
                ttsManager.initializeSilero()
            }
        } else {
            val reason = selected.reason ?: "пак неполный или не поддержан"
            viewModelScope.launch {
                _events.emit(
                    BookReaderEvent.ShowError("Пак ${selected.displayName} пока не готов: $reason"),
                )
            }
        }
    }

    private fun observePackDownloads() {
        viewModelScope.launch {
            ttsPackLibrary.downloadState.collect { state ->
                if (state != null) {
                    hadActivePackDownload = true
                }
                _uiState.update {
                    it.copy(
                        packDownloadProgress = state?.progress,
                        packDownloadLabel = state?.label ?: state?.message,
                    )
                }
                if (state == null) {
                    lastPackDownloadErrorMessage = null
                    refreshTtsPacksNow()
                    if (hadActivePackDownload) {
                        hadActivePackDownload = false
                        reinitializeCurrentOfflineEngineIfPossible()
                    }
                } else if (state.isError && state.message != null && state.message != lastPackDownloadErrorMessage) {
                    lastPackDownloadErrorMessage = state.message
                    _events.emit(BookReaderEvent.ShowError(state.message))
                }
            }
        }
    }

    private fun observeEngineDiagnostics() {
        viewModelScope.launch {
            ttsManager.piperDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(piperDiagnostics = diagnostics) }
            }
        }
        viewModelScope.launch {
            ttsManager.natashaDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(natashaDiagnostics = diagnostics) }
            }
        }
        viewModelScope.launch {
            ttsManager.utrobinDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(utrobinDiagnostics = diagnostics) }
            }
        }
        viewModelScope.launch {
            ttsManager.chatterboxDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(chatterboxDiagnostics = diagnostics) }
            }
        }
        viewModelScope.launch {
            ttsManager.onnxDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(onnxDiagnostics = diagnostics) }
            }
        }
    }

    fun persistCurrentProgress() {
        saveProgress()
    }

    private fun saveProgress() {
        persistReaderProgress(force = true)
    }

    private fun saveProgressIfNeeded(position: Int) {
        persistReaderProgress(force = false, requestedPosition = position)
    }

    private fun persistReaderProgress(
        force: Boolean,
        requestedPosition: Int? = null,
    ) {
        val snapshot = readerProgressSnapshot(requestedPosition) ?: return
        if (!force && !shouldPersistReaderProgress(snapshot)) return
        markReaderProgressScheduled(snapshot)

        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.updateReadingProgress(
                bookId = snapshot.bookId,
                chapter = snapshot.chapterIndex,
                position = snapshot.position,
            )
            ReaderWidgetStateStore.write(appContext, snapshot.toWidgetState())
            ReaderWidgetProvider.updateAll(appContext)
        }
    }

    private fun readerProgressSnapshot(requestedPosition: Int? = null): ReaderProgressSnapshot? {
        val state = _uiState.value
        val bookEntity = state.currentBookEntity ?: return null
        val chapter = state.currentChapter ?: return null
        val position = (requestedPosition ?: state.currentChapterPosition)
            .coerceIn(0, chapter.content.length)

        return ReaderProgressSnapshot(
            bookId = bookEntity.id,
            title = bookEntity.title,
            coverPath = bookEntity.coverPath,
            chapterIndex = state.currentChapterIndex,
            chapterTitle = chapter.title,
            chapterContent = chapter.content,
            position = position,
        )
    }

    private fun shouldPersistReaderProgress(snapshot: ReaderProgressSnapshot): Boolean {
        val now = System.currentTimeMillis()
        val chapterChanged = lastProgressBookId != snapshot.bookId ||
            lastProgressChapterIndex != snapshot.chapterIndex
        if (chapterChanged) return true
        if (abs(snapshot.position - lastProgressPosition) >= READER_PROGRESS_AUTOSAVE_CHAR_DELTA) return true
        return now - lastProgressSavedAtMs >= READER_PROGRESS_AUTOSAVE_INTERVAL_MS
    }

    private fun markReaderProgressScheduled(snapshot: ReaderProgressSnapshot) {
        lastProgressBookId = snapshot.bookId
        lastProgressChapterIndex = snapshot.chapterIndex
        lastProgressPosition = snapshot.position
        lastProgressSavedAtMs = System.currentTimeMillis()
    }

    private fun ReaderProgressSnapshot.toWidgetState(): ReaderWidgetBookState {
        val excerpt = extractReaderWidgetExcerpt(chapterContent, position)
        return ReaderWidgetBookState(
            title = title,
            subtitle = excerpt.ifBlank {
                chapterTitle.takeIf { it.isNotBlank() }
                    ?: "Глава ${chapterIndex + 1}"
            },
            coverPath = coverPath,
        )
    }

    fun resetCurrentBookProgress() {
        val book = _uiState.value.currentBook ?: return
        stopTts()
        _uiState.update {
            it.copy(
                currentChapterIndex = 0,
                currentChapter = book.chapters.firstOrNull(),
                currentChapterPosition = 0,
                highlightRange = null
            )
        }
        saveProgress()
    }

    private fun getNotificationTitle(): String {
        return _uiState.value.currentBook?.title ?: "Читалка Soll"
    }

    private fun getNotificationSubtitle(): String {
        return _uiState.value.currentChapter?.title ?: ""
    }

    private fun getNotificationCoverPath(): String? {
        return _uiState.value.currentBookEntity?.coverPath
    }

    private fun updateServiceNotification() {
        if (TtsService.isRunning.value) {
            TtsService.updateNotification(
                appContext,
                getNotificationTitle(),
                getNotificationSubtitle(),
                getNotificationCoverPath(),
            )
        }
    }

    private fun resolveSavedPiperPackId(
        detected: List<DetectedTtsPack>,
        savedPackId: String?,
        legacyVoiceId: String?,
    ): String? {
        val piperPacks = detected.filter {
            it.engineFamily == TtsPackEngineFamily.PIPER && it.isRunnable && it.isRussianCapable
        }
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

    private fun resolveSavedPackId(
        detected: List<DetectedTtsPack>,
        family: TtsPackEngineFamily,
        savedPackId: String?,
    ): String? {
        val familyPacks = detected
            .filter { it.engineFamily == family && it.isRunnable && it.isRussianCapable }
            .sortedWith(packPreferenceComparator(family))
        val bestPack = familyPacks.firstOrNull()
        val savedPack = savedPackId?.let { id -> familyPacks.firstOrNull { it.packId == id } }
        val validSaved = savedPack
            ?.takeUnless { pack ->
                family == TtsPackEngineFamily.CHATTERBOX &&
                    bestPack != null &&
                    packPreferenceRank(family, bestPack) < packPreferenceRank(family, pack)
            }
            ?.packId
        val fallback = bestPack?.packId
        val resolved = validSaved ?: fallback
        when (family) {
            TtsPackEngineFamily.NATASHA -> settingsRepository.ttsNatashaPackId = resolved
            TtsPackEngineFamily.UTROBIN -> settingsRepository.ttsUtrobinPackId = resolved
            TtsPackEngineFamily.CHATTERBOX -> settingsRepository.ttsChatterboxPackId = resolved
            else -> {}
        }
        return resolved
    }

    private fun packPreferenceComparator(family: TtsPackEngineFamily): Comparator<DetectedTtsPack> =
        compareBy<DetectedTtsPack> { packPreferenceRank(family, it) }
            .thenBy { it.displayName.lowercase() }
            .thenBy { it.packId }

    private fun packPreferenceRank(
        family: TtsPackEngineFamily,
        pack: DetectedTtsPack,
    ): Int {
        return when (family) {
            TtsPackEngineFamily.CHATTERBOX -> when (pack.precision?.lowercase()) {
                "int4" -> 0
                "fp16" -> 20
                "fp32" -> 40
                else -> 60
            }
            else -> 0
        }
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

    private fun resolveSavedChatterboxVoiceId(
        detected: List<DetectedTtsPack>,
        packId: String?,
        savedVoiceId: String?,
    ): String? {
        val pack = packId?.let { id ->
            detected.firstOrNull { it.packId == id && it.engineFamily == TtsPackEngineFamily.CHATTERBOX }
        } ?: return null
        val resolved = savedVoiceId?.takeIf { candidate ->
            pack.voices.any { it.id.equals(candidate, ignoreCase = true) }
        } ?: pack.voices.firstOrNull()?.id
        settingsRepository.ttsChatterboxVoice = resolved
        return resolved
    }

    private fun shouldRestartForPackMutation(packIds: Set<String>): Boolean {
        if (packIds.isEmpty()) return false
        return when (_uiState.value.engineType) {
            TtsEngineType.SILERO -> _uiState.value.selectedPiperPackId in packIds
            else -> false
        }
    }

    private fun reinitializeCurrentOfflineEngineIfPossible() {
        when (_uiState.value.engineType) {
            TtsEngineType.SILERO -> {
                if (_uiState.value.selectedPiperPackId == null) return
                viewModelScope.launch {
                    ttsManager.initializeSilero()
                    _uiState.update { it.copy(sileroModelDownloaded = ttsManager.isModelDownloaded()) }
                }
            }
            else -> Unit
        }
    }

    private fun parseStoredTtsRootUri(): Uri? =
        settingsRepository.ttsModelRootUri
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { Uri.parse(raw) }
                    .onFailure { Timber.w(it, "Failed to parse saved TTS root uri=%s", raw) }
                    .getOrNull()
            }

    private data class ReaderProgressSnapshot(
        val bookId: Long,
        val title: String,
        val coverPath: String?,
        val chapterIndex: Int,
        val chapterTitle: String,
        val chapterContent: String,
        val position: Int,
    )

    private data class TtsCatalogSnapshot(
        val detectedPacks: List<DetectedTtsPack>,
        val downloadablePacks: List<DownloadableTtsPack>,
        val installedOnnxPacks: List<InstalledOnnxPack>,
        val bestOnnxPack: InstalledOnnxPack?,
        val importBrowser: TtsImportBrowserState,
        val hasDirectFilesystemTtsAccess: Boolean,
        val commonFilesystemTtsRoots: List<String>,
    )

    private data class TtsStorageAccessSnapshot(
        val hasDirectFilesystemTtsAccess: Boolean,
        val commonFilesystemTtsRoots: List<String>,
    )

    private data class TtsBrowserLocation(
        val uri: String,
        val label: String,
    )

    override fun onCleared() {
        saveProgress()
        if (!_uiState.value.isTtsPlaying) {
            ttsManager.shutdown()
            TtsService.stop(appContext)
        }
        super.onCleared()
    }
}
