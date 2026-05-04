package com.soll.presentation.screens.tools.bookreader

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.local.entity.BookEntity
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubChapter
import com.soll.domain.tts.NatashaPlaybackDiagnostics
import com.soll.domain.tts.PiperPlaybackDiagnostics
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.UtrobinPlaybackDiagnostics
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.DownloadableTtsPack
import com.soll.domain.tts.catalog.TtsPackStatus
import com.soll.domain.tts.kokoro.KokoroPlaybackDiagnostics
import com.soll.domain.tts.onnx.InstalledOnnxPack
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    onBack: () -> Unit,
    viewModel: BookReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lastTtsRootUri = remember(uiState.lastTtsModelRootUri) {
        uiState.lastTtsModelRootUri?.let { raw ->
            runCatching { Uri.parse(raw) }
                .onFailure { Timber.w(it, "Failed to parse saved TTS root uri=%s", raw) }
                .getOrNull()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBook(it) }
    }

    val ttsModelsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        Timber.d("OpenDocumentTree result for TTS root: %s", uri)
        uri?.let { viewModel.importTtsPacksFromUserFolder(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BookReaderEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is BookReaderEvent.BookImported -> {
                    Toast.makeText(context, "Импортирована книга: ${event.title}", Toast.LENGTH_SHORT).show()
                }
                is BookReaderEvent.TtsPacksImported -> {
                    Toast.makeText(
                        context,
                        "Импортировано TTS-паков: ${event.count}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    if (uiState.currentBook != null) {
        BookReadingScreen(
            book = uiState.currentBook!!,
            currentChapter = uiState.currentChapter,
            currentChapterIndex = uiState.currentChapterIndex,
            isTtsPlaying = uiState.isTtsPlaying,
            speechRate = uiState.speechRate,
            autoAdvanceEnabled = uiState.autoAdvanceEnabled,
            highlightRange = uiState.highlightRange,
            availableEngines = uiState.availableEngines.map { it.label to it.name },
            selectedEngine = uiState.selectedEngine,
            engineType = uiState.engineType,
            ttsVoiceOptions = uiState.ttsVoiceOptions,
            sileroVoiceId = uiState.sileroVoiceId,
            utrobinVoiceId = uiState.utrobinVoiceId,
            utrobinOrtThreads = uiState.utrobinOrtThreads,
            natashaOrtThreads = uiState.natashaOrtThreads,
            sherpaThreads = uiState.sherpaThreads,
            performanceProfile = uiState.performanceProfile,
            systemPitch = uiState.systemPitch,
            engineTunables = uiState.engineTunables,
            sileroDownloadProgress = uiState.sileroDownloadProgress,
            detectedTtsPacks = uiState.detectedTtsPacks,
            downloadableTtsPacks = uiState.downloadableTtsPacks,
            packDownloadProgress = uiState.packDownloadProgress,
            packDownloadLabel = uiState.packDownloadLabel,
            lastTtsModelRootUri = uiState.lastTtsModelRootUri,
            selectedPiperPackId = uiState.selectedPiperPackId,
            selectedNatashaPackId = uiState.selectedNatashaPackId,
            selectedUtrobinPackId = uiState.selectedUtrobinPackId,
            piperDiagnostics = uiState.piperDiagnostics,
            natashaDiagnostics = uiState.natashaDiagnostics,
            utrobinDiagnostics = uiState.utrobinDiagnostics,
            onnxDiagnostics = uiState.onnxDiagnostics,
            installedOnnxPacks = uiState.installedOnnxPacks,
            selectedOnnxPackKey = uiState.selectedOnnxPackKey,
            onBack = { viewModel.closeBook() },
            onChapterSelect = { viewModel.goToChapter(it) },
            onPreviousChapter = { viewModel.previousChapter() },
            onNextChapter = { viewModel.nextChapter() },
            onToggleTts = { viewModel.toggleTts() },
            onStopTts = { viewModel.stopTts() },
            onSpeechRateChange = { viewModel.setSpeechRate(it) },
            onAutoAdvanceChange = { viewModel.setAutoAdvance(it) },
            onEngineSelect = { viewModel.selectTtsEngine(it) },
            onEngineTypeChange = { viewModel.setEngineType(it) },
            onEngineVoiceChange = { viewModel.setEngineVoice(it) },
            onEngineTunableChange = { key, value -> viewModel.applyEngineTunable(key, value) },
            onPerformanceProfileChange = { viewModel.setPerformanceProfile(it) },
            onPickTtsImportFolder = {
                Timber.d("Launching TTS folder picker with initialUri=%s", lastTtsRootUri)
                ttsModelsFolderLauncher.launch(lastTtsRootUri)
            },
            onSelectOnnxPack = { modelId, precision -> viewModel.selectOnnxPack(modelId, precision) },
            onDeleteTtsPack = { viewModel.deleteTtsPack(it) },
            onDeleteSuggestedTtsPacks = { viewModel.deleteSuggestedTtsPacks() },
            onDownloadTtsPack = { viewModel.downloadTtsPack(it) },
            onSelectEnginePack = { viewModel.selectEnginePack(it) },
            onResetProgress = { viewModel.resetCurrentBookProgress() },
        )
    } else {
        BookLibraryScreen(
            books = uiState.books,
            isLoading = uiState.isLoading,
            onBack = onBack,
            onImportBook = { filePickerLauncher.launch(arrayOf("application/epub+zip")) },
            onOpenBook = { viewModel.openBook(it) },
            onDeleteBook = { viewModel.deleteBook(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookLibraryScreen(
    books: List<BookEntity>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onImportBook: () -> Unit,
    onOpenBook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Читалка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onImportBook) {
                        Icon(Icons.Default.Add, contentDescription = "Импорт книги")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (books.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Книг пока нет",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Импортируй EPUB-файл, чтобы начать чтение",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onImportBook) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Импортировать EPUB")
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(books.size) { index ->
                        val book = books[index]
                        BookListItem(
                            index = index + 1,
                            book = book,
                            onClick = { onOpenBook(book) },
                            onDelete = { onDeleteBook(book) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookListItem(
    index: Int,
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Глава ${book.currentChapter + 1} / ${book.totalChapters}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val progressPercent = remember(book.currentChapter, book.currentPosition, book.totalChapters) {
                val total = book.totalChapters.coerceAtLeast(1)
                (((book.currentChapter.toFloat() + (book.currentPosition / 10000f)) / total) * 100f)
                    .toInt()
                    .coerceIn(0, 100)
            }
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить книгу?") },
            text = { Text("Точно удалить «${book.title}»?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookReadingScreen(
    book: EpubBook,
    currentChapter: EpubChapter?,
    currentChapterIndex: Int,
    isTtsPlaying: Boolean,
    speechRate: Float,
    autoAdvanceEnabled: Boolean,
    highlightRange: IntRange?,
    availableEngines: List<Pair<String, String>>,
    selectedEngine: String?,
    engineType: TtsEngineType,
    ttsVoiceOptions: List<TtsVoiceOption>,
    sileroVoiceId: String,
    utrobinVoiceId: String,
    utrobinOrtThreads: Int,
    natashaOrtThreads: Int,
    sherpaThreads: Int,
    performanceProfile: TtsBookPerformanceProfile,
    systemPitch: Float,
    engineTunables: List<TtsEngineTunable>,
    sileroDownloadProgress: Float?,
    detectedTtsPacks: List<DetectedTtsPack>,
    downloadableTtsPacks: List<DownloadableTtsPack>,
    packDownloadProgress: Float?,
    packDownloadLabel: String?,
    lastTtsModelRootUri: String?,
    selectedPiperPackId: String?,
    selectedNatashaPackId: String?,
    selectedUtrobinPackId: String?,
    piperDiagnostics: PiperPlaybackDiagnostics,
    natashaDiagnostics: NatashaPlaybackDiagnostics,
    utrobinDiagnostics: UtrobinPlaybackDiagnostics,
    onnxDiagnostics: KokoroPlaybackDiagnostics,
    installedOnnxPacks: List<InstalledOnnxPack>,
    selectedOnnxPackKey: String?,
    onBack: () -> Unit,
    onChapterSelect: (Int) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToggleTts: () -> Unit,
    onStopTts: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onAutoAdvanceChange: (Boolean) -> Unit,
    onEngineSelect: (String) -> Unit,
    onEngineTypeChange: (TtsEngineType) -> Unit,
    onEngineVoiceChange: (String) -> Unit,
    onEngineTunableChange: (String, Float) -> Unit,
    onPerformanceProfileChange: (TtsBookPerformanceProfile) -> Unit,
    onPickTtsImportFolder: () -> Unit,
    onSelectOnnxPack: (String, String) -> Unit,
    onDeleteTtsPack: (String) -> Unit,
    onDeleteSuggestedTtsPacks: () -> Unit,
    onDownloadTtsPack: (String) -> Unit,
    onSelectEnginePack: (String) -> Unit,
    onResetProgress: () -> Unit,
) {
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Track text layout for auto-scroll
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Auto-scroll to highlighted word
    LaunchedEffect(highlightRange) {
        if (highlightRange != null && textLayoutResult != null) {
            val layout = textLayoutResult!!
            val content = currentChapter?.content ?: return@LaunchedEffect
            val offset = highlightRange.first.coerceIn(0, content.length - 1)
            try {
                val line = layout.getLineForOffset(offset)
                val lineTop = layout.getLineTop(line).toInt()
                // Add padding offset (16dp for chapter title area + some margin)
                val targetScroll = (lineTop - with(density) { 200.dp.toPx() }.toInt())
                    .coerceAtLeast(0)

                // Only scroll if the word is outside the visible area
                val currentScroll = scrollState.value
                val viewportHeight = scrollState.viewportSize
                if (lineTop < currentScroll || lineTop > currentScroll + viewportHeight - 100) {
                    scrollState.animateScrollTo(targetScroll)
                }
            } catch (_: Exception) {
                // Ignore layout errors
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        currentChapter?.let {
                            Text(
                                text = it.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showChapterList = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Главы")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousChapter,
                        enabled = currentChapterIndex > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущая глава")
                    }

                    FilledIconButton(
                        onClick = onToggleTts,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTtsPlaying) "Пауза" else "Воспроизвести",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onStopTts) {
                        Icon(Icons.Default.Stop, contentDescription = "Стоп")
                    }

                    IconButton(
                        onClick = onNextChapter,
                        enabled = currentChapterIndex < book.chapters.size - 1
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Следующая глава")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            currentChapter?.let { chapter ->
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Build annotated string with word highlighting
                val highlightColor = MaterialTheme.colorScheme.primaryContainer
                val highlightTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                val annotatedText = remember(chapter.content, highlightRange) {
                    buildAnnotatedString {
                        append(chapter.content)
                        if (highlightRange != null) {
                            val start = highlightRange.first.coerceIn(0, chapter.content.length)
                            val end = highlightRange.last.coerceIn(0, chapter.content.length)
                            if (start < end) {
                                addStyle(
                                    SpanStyle(
                                        background = highlightColor,
                                        color = highlightTextColor
                                    ),
                                    start = start,
                                    end = end
                                )
                            }
                        }
                    }
                }

                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5,
                    onTextLayout = { result ->
                        textLayoutResult = result
                    }
                )
            } ?: run {
                Text(
                    text = "Контент главы недоступен",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Chapter list dialog
    if (showChapterList) {
        AlertDialog(
            onDismissRequest = { showChapterList = false },
            title = { Text("Главы") },
            text = {
                LazyColumn {
                    items(book.chapters.size) { index ->
                        val chapter = book.chapters[index]
                        ListItem(
                            headlineContent = { Text(chapter.title) },
                            leadingContent = {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.clickable {
                                onChapterSelect(index)
                                showChapterList = false
                            },
                            colors = if (index == currentChapterIndex) {
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            } else {
                                ListItemDefaults.colors()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChapterList = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Settings dialog
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Настройки чтения") },
            text = {
                val removableSuggestedPacks = detectedTtsPacks.filter { it.suggestedDeletion && it.canDelete }
                val piperPacks = detectedTtsPacks.filter { it.engineFamily.name == "PIPER" }
                val natashaPacks = detectedTtsPacks.filter { it.engineFamily.name == "NATASHA" }
                val utrobinPacks = detectedTtsPacks.filter { it.engineFamily.name == "UTROBIN" }
                val selectedVoiceId = when (engineType) {
                    TtsEngineType.SILERO -> sileroVoiceId
                    TtsEngineType.UTROBIN -> utrobinVoiceId
                    TtsEngineType.NATASHA -> ""
                    TtsEngineType.ONNX_EXTERNAL -> ""
                    TtsEngineType.SYSTEM -> ""
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ReaderSettingsSection(
                        title = "Воспроизведение",
                        subtitle = "Скорость, профиль нагрузки и поведение при переходе по главам.",
                    ) {
                        Text(
                            text = "Скорость речи: ${String.format("%.1f", speechRate)}x",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = speechRate,
                            onValueChange = onSpeechRateChange,
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Профиль нагрузки",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "Батарея: меньше потоков и крупнее фразы. Качество: больше CPU и безопаснее чанки.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = performanceProfile == TtsBookPerformanceProfile.BATTERY,
                                onClick = { onPerformanceProfileChange(TtsBookPerformanceProfile.BATTERY) },
                                label = { Text("Батарея") },
                            )
                            FilterChip(
                                selected = performanceProfile == TtsBookPerformanceProfile.BALANCED,
                                onClick = { onPerformanceProfileChange(TtsBookPerformanceProfile.BALANCED) },
                                label = { Text("Баланс") },
                            )
                            FilterChip(
                                selected = performanceProfile == TtsBookPerformanceProfile.QUALITY,
                                onClick = { onPerformanceProfileChange(TtsBookPerformanceProfile.QUALITY) },
                                label = { Text("Качество") },
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Автопереход по главам",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(
                                checked = autoAdvanceEnabled,
                                onCheckedChange = onAutoAdvanceChange,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onResetProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сбросить прогресс книги")
                        }
                    }

                    ReaderSettingsSection(
                        title = "Модели и голоса",
                        subtitle = "Ручной импорт пользовательских pack-ов и управление локальной библиотекой.",
                    ) {
                        OutlinedButton(
                            onClick = onPickTtsImportFolder,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Импорт из папки tts")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Кнопка нужна для ручного импорта `Utrobin`, `ONNX External` и любых пользовательских pack-ов. Отдельный ручной refresh больше не нужен: список обновляется автоматически после импорта, загрузки и удаления.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        lastTtsModelRootUri?.let { savedRoot ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Последняя папка tts: $savedRoot",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (removableSuggestedPacks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onDeleteSuggestedTtsPacks,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Удалить всё на удаление (${removableSuggestedPacks.size})")
                            }
                        }
                        if (downloadableTtsPacks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Быстрая загрузка русских pack-ов",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            downloadableTtsPacks.forEach { pack ->
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { onDownloadTtsPack(pack.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Скачать ${pack.displayName}")
                                }
                                Text(
                                    text = pack.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (packDownloadLabel != null || packDownloadProgress != null || sileroDownloadProgress != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = packDownloadLabel ?: "Загрузка модели",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            (packDownloadProgress ?: sileroDownloadProgress)?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        if (detectedTtsPacks.isEmpty()) {
                            Text(
                                text = "Паки пока не найдены.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "Установленные pack-и",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            detectedTtsPacks.forEach { pack ->
                                ReaderPackCard(
                                    pack = pack,
                                    onDelete = if (pack.canDelete) {
                                        { onDeleteTtsPack(pack.packId) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }

                    ReaderSettingsSection(
                        title = "Движок чтения",
                        subtitle = "Выбор активного runtime. Детальные настройки показываются только для выбранного движка.",
                    ) {
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.SILERO,
                            title = "Piper / Sherpa (оффлайн) — базовый режим",
                            description = "Низкая нагрузка, стабильно; нужен локальный Piper pack с ONNX-голосом, tokens.txt и espeak-ng-data.",
                            onClick = { onEngineTypeChange(TtsEngineType.SILERO) },
                        )
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.NATASHA,
                            title = "Natasha VITS2 (оффлайн)",
                            description = "Лучшая русская интонация среди оффлайн вариантов; выше CPU и расход батареи.",
                            onClick = { onEngineTypeChange(TtsEngineType.NATASHA) },
                        )
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.UTROBIN,
                            title = "Utrobin VITS (оффлайн)",
                            description = "Русский ONNX/VITS из локального pack-а; средняя нагрузка, качество зависит от токенизации и чанков.",
                            onClick = { onEngineTypeChange(TtsEngineType.UTROBIN) },
                        )
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.ONNX_EXTERNAL,
                            title = "ONNX External (RU модели)",
                            description = "Пакеты моделей ставятся отдельно, выбор по quality/size.",
                            onClick = { onEngineTypeChange(TtsEngineType.ONNX_EXTERNAL) },
                        )
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.SYSTEM,
                            title = "System TTS",
                            description = "Google TTS или другой установленный движок Android.",
                            onClick = { onEngineTypeChange(TtsEngineType.SYSTEM) },
                        )
                    }

                    ReaderSettingsSection(
                        title = when (engineType) {
                            TtsEngineType.SILERO -> "Текущий движок: Piper"
                            TtsEngineType.NATASHA -> "Текущий движок: Natasha"
                            TtsEngineType.UTROBIN -> "Текущий движок: Utrobin"
                            TtsEngineType.ONNX_EXTERNAL -> "Текущий движок: ONNX External"
                            TtsEngineType.SYSTEM -> "Текущий движок: System TTS"
                        },
                        subtitle = when (engineType) {
                            TtsEngineType.SILERO -> "Пакет = голос. Здесь выбирается конкретный установленный pack и видна диагностика чтения."
                            TtsEngineType.NATASHA -> "Первый следующий offline-движок после Piper. Добавлена диагностика и stop на реальном сбое вместо тихого skip."
                            TtsEngineType.UTROBIN -> "Русский ONNX/VITS со speaker 0/1. Добавлена диагностика и recovery split вместо молчаливого skip."
                            TtsEngineType.ONNX_EXTERNAL -> "Показываются только runnable пакеты. Неподдержанные runtime остаются в общей библиотеке выше."
                            TtsEngineType.SYSTEM -> "Выбор установленного системного TTS и его пользовательских параметров."
                        },
                    ) {
                        when (engineType) {
                            TtsEngineType.SILERO -> {
                                if (piperPacks.isEmpty()) {
                                    Text(
                                        text = "Локальные Piper pack-и не найдены.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    piperPacks.forEach { pack ->
                                        ReaderPackSelectorRow(
                                            pack = pack,
                                            selected = pack.packId == selectedPiperPackId,
                                            onClick = { onSelectEnginePack(pack.packId) },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                ReaderPiperDiagnosticsCard(piperDiagnostics = piperDiagnostics)
                            }
                            TtsEngineType.NATASHA -> {
                                if (natashaPacks.isEmpty()) {
                                    Text(
                                        text = "Локальные Natasha pack-и не найдены.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    natashaPacks.forEach { pack ->
                                        ReaderPackSelectorRow(
                                            pack = pack,
                                            selected = pack.packId == selectedNatashaPackId,
                                            onClick = { onSelectEnginePack(pack.packId) },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                ReaderNatashaDiagnosticsCard(natashaDiagnostics = natashaDiagnostics)
                            }
                            TtsEngineType.UTROBIN -> {
                                if (utrobinPacks.isEmpty()) {
                                    Text(
                                        text = "Локальные Utrobin pack-и не найдены.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    utrobinPacks.forEach { pack ->
                                        ReaderPackSelectorRow(
                                            pack = pack,
                                            selected = pack.packId == selectedUtrobinPackId,
                                            onClick = { onSelectEnginePack(pack.packId) },
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                ReaderUtrobinDiagnosticsCard(utrobinDiagnostics = utrobinDiagnostics)
                            }
                            TtsEngineType.ONNX_EXTERNAL -> {
                                if (installedOnnxPacks.isEmpty()) {
                                    Text(
                                        text = "Готовые к запуску ONNX-паки не найдены. Неподдержанные runtime и нерусские паки смотри выше в общей библиотеке моделей.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    installedOnnxPacks.forEach { pack ->
                                        val key = "${pack.modelId}|${pack.precision}"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectOnnxPack(pack.modelId, pack.precision) }
                                                .padding(vertical = 2.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = key == selectedOnnxPackKey,
                                                onClick = { onSelectOnnxPack(pack.modelId, pack.precision) },
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Column {
                                                Text("${pack.modelId} (${pack.precision})", style = MaterialTheme.typography.bodySmall)
                                                Text("~${pack.estimatedSizeMb} MB", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                ReaderOnnxDiagnosticsCard(onnxDiagnostics = onnxDiagnostics)
                            }
                            TtsEngineType.SYSTEM -> {
                                if (availableEngines.isEmpty()) {
                                    Text(
                                        text = "Системные движки не найдены.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    availableEngines.forEach { (label, packageName) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onEngineSelect(packageName) }
                                                .padding(vertical = 2.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioButton(
                                                selected = packageName == selectedEngine,
                                                onClick = { onEngineSelect(packageName) },
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(label, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        if (ttsVoiceOptions.isNotEmpty() && engineType != TtsEngineType.SILERO) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Голос",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            ttsVoiceOptions.forEach { voice ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEngineVoiceChange(voice.id) }
                                        .padding(vertical = 2.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = voice.id == selectedVoiceId,
                                        onClick = { onEngineVoiceChange(voice.id) },
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(voice.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        if (engineTunables.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Параметры движка",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            engineTunables.forEach { tunable ->
                                when (tunable) {
                                    is TtsEngineTunable.Slider -> {
                                        val sliderValue = when (tunable.key) {
                                            "pitch" -> systemPitch
                                            "ort_intra_threads" -> utrobinOrtThreads.toFloat()
                                            "natasha_ort_intra_threads" -> natashaOrtThreads.toFloat()
                                            "sherpa_num_threads" -> sherpaThreads.toFloat()
                                            else -> tunable.defaultValue
                                        }.coerceIn(tunable.range.start, tunable.range.endInclusive)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val valueLabel = when (tunable.key) {
                                            "ort_intra_threads",
                                            "natasha_ort_intra_threads",
                                            "sherpa_num_threads",
                                            -> sliderValue.toInt().toString()
                                            else -> String.format("%.2f", sliderValue)
                                        }
                                        Text(
                                            text = "${tunable.label}: $valueLabel",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        val stepsCount = tunable.materialSliderSteps
                                            ?: (((tunable.range.endInclusive - tunable.range.start) * 20f).toInt() - 1)
                                                .coerceAtLeast(0)
                                        Slider(
                                            value = sliderValue,
                                            onValueChange = { onEngineTunableChange(tunable.key, it) },
                                            valueRange = tunable.range,
                                            steps = stepsCount,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Готово")
                }
            }
        )
    }
}

@Composable
private fun ReaderSettingsSection(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            subtitle?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ReaderEngineOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderPackCard(
    pack: DetectedTtsPack,
    onDelete: (() -> Unit)?,
) {
    val statusColor = when (pack.status) {
        TtsPackStatus.READY -> MaterialTheme.colorScheme.primary
        TtsPackStatus.READY_NON_RUSSIAN -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = when (pack.status) {
                    TtsPackStatus.READY -> Icons.Default.CheckCircle
                    TtsPackStatus.READY_NON_RUSSIAN -> Icons.Default.Info
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                tint = statusColor,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${pack.engineFamily.name} · ${pack.status.name}" +
                        if (pack.suggestedDeletion) " · на удаление" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                )
                pack.voiceSummary()?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pack.reason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = pack.rootDir,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить пак",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPackSelectorRow(
    pack: DetectedTtsPack,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(pack.displayName, style = MaterialTheme.typography.bodySmall)
            pack.voiceSummary()?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            Text(pack.rootDir, style = MaterialTheme.typography.labelSmall)
            pack.reason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ReaderPiperDiagnosticsCard(
    piperDiagnostics: PiperPlaybackDiagnostics,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Диагностика Piper",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Голос: ${piperDiagnostics.voiceLabel ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Pack: ${piperDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${piperDiagnostics.completedChunks}/${piperDiagnostics.totalChunks} · recovery ${piperDiagnostics.recoveredChunks} · ошибки ${piperDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Rate ${String.format("%.1f", piperDiagnostics.speechRate)}x · threads ${piperDiagnostics.sherpaThreads}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            piperDiagnostics.lastRecoveryAction?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            piperDiagnostics.lastChunkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последний chunk: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                piperDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · depth ${piperDiagnostics.lastChunkSplitDepth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            piperDiagnostics.lastFailureMessage?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последняя ошибка: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                piperDiagnostics.lastFailurePreview?.let { preview ->
                    Text(
                        text = "Проблемный фрагмент: $preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                piperDiagnostics.lastFailureRange?.let { range ->
                    Text(
                        text = "Сбой в диапазоне: ${range.asDisplayRange()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderNatashaDiagnosticsCard(
    natashaDiagnostics: NatashaPlaybackDiagnostics,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Диагностика Natasha",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tokenizer: ${natashaDiagnostics.tokenizerLabel ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Pack: ${natashaDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${natashaDiagnostics.completedChunks}/${natashaDiagnostics.totalChunks} · recovery ${natashaDiagnostics.recoveredChunks} · ошибки ${natashaDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Rate ${String.format("%.1f", natashaDiagnostics.speechRate)}x · threads ${natashaDiagnostics.ortThreads}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            natashaDiagnostics.lastChunkDurationMs?.let { durationMs ->
                Text(
                    text = "Последняя генерация: ${durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            natashaDiagnostics.lastRecoveryAction?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            natashaDiagnostics.lastChunkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последний chunk: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                natashaDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · depth ${natashaDiagnostics.lastChunkSplitDepth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            natashaDiagnostics.lastFailureMessage?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последняя ошибка: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                natashaDiagnostics.lastFailurePreview?.let { preview ->
                    Text(
                        text = "Проблемный фрагмент: $preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                natashaDiagnostics.lastFailureRange?.let { range ->
                    Text(
                        text = "Сбой в диапазоне: ${range.asDisplayRange()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderUtrobinDiagnosticsCard(
    utrobinDiagnostics: UtrobinPlaybackDiagnostics,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Диагностика Utrobin",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Speaker: ${utrobinDiagnostics.speakerLabel ?: utrobinDiagnostics.speakerId}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Pack: ${utrobinDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${utrobinDiagnostics.completedChunks}/${utrobinDiagnostics.totalChunks} · recovery ${utrobinDiagnostics.recoveredChunks} · ошибки ${utrobinDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Rate ${String.format("%.1f", utrobinDiagnostics.speechRate)}x · threads ${utrobinDiagnostics.ortThreads}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            utrobinDiagnostics.lastChunkDurationMs?.let { durationMs ->
                Text(
                    text = "Последняя генерация: ${durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            utrobinDiagnostics.lastRecoveryAction?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            utrobinDiagnostics.lastChunkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последний chunk: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                utrobinDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · depth ${utrobinDiagnostics.lastChunkSplitDepth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            utrobinDiagnostics.lastFailureMessage?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последняя ошибка: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                utrobinDiagnostics.lastFailurePreview?.let { preview ->
                    Text(
                        text = "Проблемный фрагмент: $preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                utrobinDiagnostics.lastFailureRange?.let { range ->
                    Text(
                        text = "Сбой в диапазоне: ${range.asDisplayRange()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderOnnxDiagnosticsCard(
    onnxDiagnostics: KokoroPlaybackDiagnostics,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Диагностика ONNX / Kokoro",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Voice: ${onnxDiagnostics.voiceId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Pack: ${onnxDiagnostics.packRoot ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${onnxDiagnostics.completedChunks}/${onnxDiagnostics.totalChunks} · recovery ${onnxDiagnostics.recoveredChunks} · ошибки ${onnxDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Rate ${String.format("%.1f", onnxDiagnostics.speechRate)}x · threads ${onnxDiagnostics.ortThreads}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onnxDiagnostics.lastChunkDurationMs?.let { durationMs ->
                Text(
                    text = "Последняя генерация: ${durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            onnxDiagnostics.lastRecoveryAction?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            onnxDiagnostics.lastChunkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последний chunk: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                onnxDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · depth ${onnxDiagnostics.lastChunkSplitDepth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            onnxDiagnostics.lastFailureMessage?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последняя ошибка: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                onnxDiagnostics.lastFailurePreview?.let { preview ->
                    Text(
                        text = "Проблемный фрагмент: $preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                onnxDiagnostics.lastFailureRange?.let { range ->
                    Text(
                        text = "Сбой в диапазоне: ${range.asDisplayRange()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun DetectedTtsPack.voiceSummary(): String? {
    if (voices.isEmpty()) return null
    val preview = voices.take(4).joinToString { it.label }
    val suffix = if (voices.size > 4) " +${voices.size - 4}" else ""
    return "Голоса: $preview$suffix"
}

private fun IntRange.asDisplayRange(): String = "${first}..${last}"
