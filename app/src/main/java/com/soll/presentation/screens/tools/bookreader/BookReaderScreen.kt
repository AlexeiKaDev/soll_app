package com.soll.presentation.screens.tools.bookreader

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
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.onnx.InstalledOnnxPack
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    onBack: () -> Unit,
    viewModel: BookReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBook(it) }
    }

    val onnxModelsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.importOnnxPacksFromUserFolder(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BookReaderEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is BookReaderEvent.BookImported -> {
                    Toast.makeText(context, "Imported: ${event.title}", Toast.LENGTH_SHORT).show()
                }
                is BookReaderEvent.OnnxPacksImported -> {
                    Toast.makeText(
                        context,
                        "Импортировано ONNX-пакетов: ${event.count}",
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
            onRefreshOnnxPacks = { viewModel.refreshOnnxPacks() },
            onPickOnnxImportFolder = { onnxModelsFolderLauncher.launch(null) },
            onSelectOnnxPack = { modelId, precision -> viewModel.selectOnnxPack(modelId, precision) },
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
                title = { Text("Book Reader") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onImportBook) {
                        Icon(Icons.Default.Add, contentDescription = "Import book")
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
                        text = "No books yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import an EPUB file to start reading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onImportBook) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import EPUB")
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
                    text = "Chapter ${book.currentChapter + 1} / ${book.totalChapters}",
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
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete book?") },
            text = { Text("Are you sure you want to delete \"${book.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
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
    onRefreshOnnxPacks: () -> Unit,
    onPickOnnxImportFolder: () -> Unit,
    onSelectOnnxPack: (String, String) -> Unit,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showChapterList = true }) {
                        Icon(Icons.Default.List, contentDescription = "Chapters")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chapter")
                    }

                    FilledIconButton(
                        onClick = onToggleTts,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isTtsPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onStopTts) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }

                    IconButton(
                        onClick = onNextChapter,
                        enabled = currentChapterIndex < book.chapters.size - 1
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next chapter")
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
                    text = "No content available",
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
            title = { Text("Chapters") },
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
                    Text("Close")
                }
            }
        )
    }

    // Settings dialog
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Reading Settings") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Speech rate
                    Text(
                        text = "Speech Rate: ${String.format("%.1f", speechRate)}x",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Профиль нагрузки (S200 и др.)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Батарея: меньше потоков и крупнее фразы. Качество: больше CPU, мельче чанки.",
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

                    if (engineTunables.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Параметры движка",
                            style = MaterialTheme.typography.titleSmall,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-advance toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-advance chapters",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoAdvanceEnabled,
                            onCheckedChange = onAutoAdvanceChange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onResetProgress,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сбросить прогресс книги")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Engine type selector
                    Text(
                        text = "TTS Engine",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Silero/Piper first — baseline: скорость и расход батареи
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineTypeChange(TtsEngineType.SILERO) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineType == TtsEngineType.SILERO,
                            onClick = { onEngineTypeChange(TtsEngineType.SILERO) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Piper / Sherpa (оффлайн) — базовый режим", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Низкая нагрузка, стабильно; интонация проще, чем у VITS2. Для S200 рекомендован как дефолт.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineTypeChange(TtsEngineType.NATASHA) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineType == TtsEngineType.NATASHA,
                            onClick = { onEngineTypeChange(TtsEngineType.NATASHA) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Natasha VITS2 (оффлайн)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Лучшая русская интонация среди оффлайн вариантов; выше CPU и расход батареи, модель в assets.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineTypeChange(TtsEngineType.UTROBIN) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineType == TtsEngineType.UTROBIN,
                            onClick = { onEngineTypeChange(TtsEngineType.UTROBIN) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Utrobin VITS (оффлайн)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Третий оффлайн вариант: ONNX из assets, средняя нагрузка; качество зависит от чанка, не дефолт.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineTypeChange(TtsEngineType.ONNX_EXTERNAL) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineType == TtsEngineType.ONNX_EXTERNAL,
                            onClick = { onEngineTypeChange(TtsEngineType.ONNX_EXTERNAL) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("ONNX External (RU модели)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Пакеты моделей ставятся отдельно (вне APK), выбор по quality/size.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (engineType == TtsEngineType.ONNX_EXTERNAL) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPickOnnxImportFolder,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Папка с моделями",
                                    maxLines = 2,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            OutlinedButton(
                                onClick = onRefreshOnnxPacks,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Обновить", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Если файлы лежат в Загрузках (например Downloads/tts), нажми «Папка с моделями» и выбери эту папку — приложение само найдёт model_manifest.json и скопирует пакеты. Либо скопируй вручную в external_models/tts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (installedOnnxPacks.isEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Паки не найдены. Kokoro: python tools/tts/prepare_onnx_pack.py --model kokoro_82m ... --download — в пакете нужны onnx/, voices/*.bin и config.json (hexgrad/Kokoro-82M). Остальные модели пока без рантайма в приложении.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            installedOnnxPacks.forEach { pack ->
                                val key = "${pack.modelId}|${pack.precision}"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectOnnxPack(pack.modelId, pack.precision) }
                                        .padding(vertical = 2.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = key == selectedOnnxPackKey,
                                        onClick = { onSelectOnnxPack(pack.modelId, pack.precision) }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text("${pack.modelId} (${pack.precision})", style = MaterialTheme.typography.bodySmall)
                                        Text("~${pack.estimatedSizeMb} MB", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // Download progress
                    if (sileroDownloadProgress != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Загрузка модели: ${(sileroDownloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LinearProgressIndicator(
                            progress = { sileroDownloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    val selectedVoiceId = when (engineType) {
                        TtsEngineType.SILERO -> sileroVoiceId
                        TtsEngineType.UTROBIN -> utrobinVoiceId
                        TtsEngineType.NATASHA -> ""
                        TtsEngineType.ONNX_EXTERNAL -> ""
                        TtsEngineType.SYSTEM -> ""
                    }
                    if (ttsVoiceOptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Голос",
                            style = MaterialTheme.typography.labelMedium,
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // System TTS option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineTypeChange(TtsEngineType.SYSTEM) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engineType == TtsEngineType.SYSTEM,
                            onClick = { onEngineTypeChange(TtsEngineType.SYSTEM) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("System TTS", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Google TTS или другой установленный",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // System engine selector (shown when System is selected)
                    if (engineType == TtsEngineType.SYSTEM && availableEngines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "System Engine",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        availableEngines.forEach { (label, packageName) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEngineSelect(packageName) }
                                    .padding(vertical = 2.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = packageName == selectedEngine,
                                    onClick = { onEngineSelect(packageName) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Done")
                }
            }
        )
    }
}
