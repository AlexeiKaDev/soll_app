package com.soll.presentation.screens.tools.bookreader

import android.content.Context
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.BookEntity
import com.soll.data.repository.BookRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.service.TtsService
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubChapter
import com.soll.domain.tts.TextToSpeechManager
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.TtsServiceAction
import com.soll.domain.tts.TtsState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class BookReaderUiState(
    val books: List<BookEntity> = emptyList(),
    val currentBook: EpubBook? = null,
    val currentBookEntity: BookEntity? = null,
    val currentChapter: EpubChapter? = null,
    val currentChapterIndex: Int = 0,
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
    val sileroUseV5: Boolean = true,
    val sileroSpeakerId: Int = 30
)

sealed class BookReaderEvent {
    data class ShowError(val message: String) : BookReaderEvent()
    data class BookImported(val title: String) : BookReaderEvent()
}

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    private val ttsManager: TextToSpeechManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookReaderUiState())
    val uiState: StateFlow<BookReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BookReaderEvent>()
    val events: SharedFlow<BookReaderEvent> = _events.asSharedFlow()

    init {
        loadBooks()
        loadSettings()
        initTts()
        observeTtsState()
        observeChapterFinished()
        observeServiceActions()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
    }

    private fun loadSettings() {
        val engineType = when (settingsRepository.ttsEngineType) {
            "silero" -> TtsEngineType.SILERO
            else -> TtsEngineType.SYSTEM
        }
        _uiState.update {
            it.copy(
                autoAdvanceEnabled = settingsRepository.ttsAutoAdvance,
                selectedEngine = settingsRepository.ttsEngine,
                speechRate = settingsRepository.ttsSpeechRate,
                engineType = engineType,
                sileroModelDownloaded = ttsManager.isModelDownloaded()
            )
        }
        ttsManager.setEngineType(engineType)
        ttsManager.sileroEngine.setUseV5(true) // default to v5 HD
        ttsManager.sileroEngine.setV5SpeakerId(30)
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
                _uiState.update { it.copy(highlightRange = range) }
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
                        currentChapterIndex = chapterIndex
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
                currentChapterIndex = 0
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
                currentChapterIndex = index
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
                currentChapterIndex = index
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

        if (_uiState.value.engineType == TtsEngineType.SILERO && !ttsManager.sileroEngine.isReady.value) {
            viewModelScope.launch {
                val success = ttsManager.initializeSilero()
                if (success) startPlayback(chapter)
                else _events.emit(BookReaderEvent.ShowError("Не удалось загрузить модель Silero. Проверьте интернет."))
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
        _uiState.update { it.copy(engineType = type) }
        settingsRepository.ttsEngineType = when (type) {
            TtsEngineType.SILERO -> "silero"
            TtsEngineType.SYSTEM -> "system"
        }

        if (type == TtsEngineType.SILERO && !ttsManager.sileroEngine.isReady.value) {
            viewModelScope.launch {
                ttsManager.initializeSilero()
                _uiState.update { it.copy(sileroModelDownloaded = ttsManager.isModelDownloaded()) }
            }
        }
    }

    fun setSileroUseV5(enabled: Boolean) {
        _uiState.update { it.copy(sileroUseV5 = enabled) }
        ttsManager.sileroEngine.setUseV5(enabled)
        // Need to reinitialize if changing model
        if (_uiState.value.engineType == TtsEngineType.SILERO) {
            stopTts()
            ttsManager.sileroEngine.shutdown()
            viewModelScope.launch { ttsManager.initializeSilero() }
        }
    }

    fun setSileroSpeakerId(id: Int) {
        _uiState.update { it.copy(sileroSpeakerId = id) }
        ttsManager.sileroEngine.setV5SpeakerId(id)
    }

    fun selectTtsEngine(packageName: String) {
        settingsRepository.ttsEngine = packageName
        _uiState.update { it.copy(selectedEngine = packageName) }
        ttsManager.reinitializeWithEngine(packageName)
        ttsManager.setSpeechRate(_uiState.value.speechRate)
        _uiState.update { it.copy(availableEngines = ttsManager.getAvailableEngines()) }
    }

    fun deleteBook(bookEntity: BookEntity) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookEntity.id)
        }
    }

    private fun saveProgress() {
        val bookEntity = _uiState.value.currentBookEntity ?: return
        val chapterIndex = _uiState.value.currentChapterIndex
        viewModelScope.launch {
            bookRepository.updateReadingProgress(
                bookId = bookEntity.id,
                chapter = chapterIndex,
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

    override fun onCleared() {
        super.onCleared()
        if (!_uiState.value.isTtsPlaying) {
            ttsManager.shutdown()
            TtsService.stop(appContext)
        }
    }
}
