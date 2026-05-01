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
import com.soll.domain.tts.TtsEngineType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BookReaderEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is BookReaderEvent.BookImported -> {
                    Toast.makeText(context, "Imported: ${event.title}", Toast.LENGTH_SHORT).show()
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
            sileroSpeaker = "",
            sileroDownloadProgress = uiState.sileroDownloadProgress,
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
            sileroVoiceId = uiState.sileroVoiceId,
            onSileroVoiceChange = { viewModel.setSileroVoice(it) }
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
                    items(books) { book ->
                        BookListItem(
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
    sileroSpeaker: String,
    sileroDownloadProgress: Float?,
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
    sileroVoiceId: String,
    onSileroVoiceChange: (String) -> Unit
) {
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
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
            Surface(
                tonalElevation = 3.dp
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

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Engine type selector
                    Text(
                        text = "TTS Engine",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Silero option
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
                            Text("Silero (оффлайн)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Встроенная нейросеть, голос Ксения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

                    // Voice picker (shown when Silero selected)
                    if (engineType == TtsEngineType.SILERO) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Голос", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        com.soll.domain.tts.SileroJitEngine.VOICES.forEach { voice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSileroVoiceChange(voice.id) }
                                    .padding(vertical = 2.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = voice.id == sileroVoiceId,
                                    onClick = { onSileroVoiceChange(voice.id) }
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
