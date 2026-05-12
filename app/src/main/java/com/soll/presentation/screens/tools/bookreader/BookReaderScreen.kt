package com.soll.presentation.screens.tools.bookreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.local.entity.BookEntity
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubChapter
import com.soll.domain.tts.NatashaPlaybackDiagnostics
import com.soll.domain.tts.PiperPlaybackDiagnostics
import com.soll.domain.tts.PiperProsodyPreset
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.TtsState
import com.soll.domain.tts.UtrobinPlaybackDiagnostics
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.catalog.DetectedTtsPack
import com.soll.domain.tts.catalog.DownloadableTtsPack
import com.soll.domain.tts.catalog.TtsPackEngineFamily
import com.soll.domain.tts.catalog.TtsImportBrowserState
import com.soll.domain.tts.catalog.TtsPackStatus
import com.soll.domain.tts.catalog.TtsTreeAccessState
import com.soll.domain.tts.chatterbox.ChatterboxPlaybackDiagnostics
import com.soll.domain.tts.kokoro.KokoroPlaybackDiagnostics
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import java.util.Locale

private val TTS_MODEL_FILE_MIME_TYPES = arrayOf(
    "application/octet-stream",
    "application/json",
    "text/plain",
    "audio/wav",
    "audio/x-wav",
    "audio/wave",
    "*/*",
)

private fun buildTtsModelFilePickerIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, TTS_MODEL_FILE_MIME_TYPES)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

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

    val ttsPackFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importTtsFromPickedDocument(uri)
            }
        }
    }

    val manageAllFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStorageAccessState()
        viewModel.refreshTtsImportBrowser()
    }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                        if (event.failedCount > 0) {
                            "Импортировано TTS-паков: ${event.importedCount}, с ошибками: ${event.failedCount}"
                        } else {
                            "Импортировано TTS-паков: ${event.importedCount}"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, uiState.currentBookEntity?.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.persistCurrentProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.persistCurrentProgress()
        }
    }

    val currentBook = uiState.currentBook
    if (currentBook != null) {
        BookReadingScreen(
            book = currentBook,
            currentChapter = uiState.currentChapter,
            currentChapterIndex = uiState.currentChapterIndex,
            isTtsPlaying = uiState.isTtsPlaying,
            ttsState = uiState.ttsState,
            speechRate = uiState.speechRate,
            autoAdvanceEnabled = uiState.autoAdvanceEnabled,
            highlightRange = uiState.highlightRange,
            availableEngines = uiState.availableEngines.map { it.label to it.name },
            selectedEngine = uiState.selectedEngine,
            engineType = uiState.engineType,
            ttsVoiceOptions = uiState.ttsVoiceOptions,
            utrobinOrtThreads = uiState.utrobinOrtThreads,
            natashaOrtThreads = uiState.natashaOrtThreads,
            chatterboxOrtThreads = uiState.chatterboxOrtThreads,
            chatterboxExaggeration = uiState.chatterboxExaggeration,
            sherpaThreads = uiState.sherpaThreads,
            performanceProfile = uiState.performanceProfile,
            piperProsodyPreset = uiState.piperProsodyPreset,
            systemPitch = uiState.systemPitch,
            engineTunables = uiState.engineTunables,
            sileroDownloadProgress = uiState.sileroDownloadProgress,
            detectedTtsPacks = uiState.detectedTtsPacks,
            downloadableTtsPacks = uiState.downloadableTtsPacks,
            packDownloadProgress = uiState.packDownloadProgress,
            packDownloadLabel = uiState.packDownloadLabel,
            lastTtsImportSummary = uiState.lastTtsImportSummary,
            ttsImportBrowser = uiState.ttsImportBrowser,
            isTtsImporting = uiState.isTtsImporting,
            hasDirectFilesystemTtsAccess = uiState.hasDirectFilesystemTtsAccess,
            commonFilesystemTtsRoots = uiState.commonFilesystemTtsRoots,
            selectedPiperPackId = uiState.selectedPiperPackId,
            piperDiagnostics = uiState.piperDiagnostics,
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
            onPiperProsodyPresetChange = { viewModel.setPiperProsodyPreset(it) },
            onOpenTtsImportBrowser = { viewModel.openTtsImportBrowser() },
            onPickTtsModelFile = {
                Timber.d("Launching TTS model file picker")
                ttsPackFileLauncher.launch(buildTtsModelFilePickerIntent())
            },
            onGrantAllFilesAccess = {
                manageAllFilesAccessLauncher.launch(buildManageAllFilesAccessIntent(context.packageName))
            },
            onImportFromCommonFilesystemRoots = { viewModel.importTtsFromCommonFilesystemRoots() },
            onTtsBrowserEnter = { uri, label -> viewModel.enterTtsImportDirectory(uri, label) },
            onTtsBrowserUp = { viewModel.leaveTtsImportDirectory() },
            onTtsBrowserRefresh = { viewModel.refreshTtsImportBrowser() },
            onImportTtsCandidate = { viewModel.importTtsCandidatesFromBrowser(setOf(it)) },
            onImportAllVisibleTtsCandidates = { viewModel.importAllVisibleTtsCandidates() },
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

@Composable
private fun TtsImportBrowserDialog(
    browser: TtsImportBrowserState,
    isImporting: Boolean,
    hasDirectFilesystemTtsAccess: Boolean,
    commonFilesystemTtsRoots: List<String>,
    onDismiss: () -> Unit,
    onPickModelFile: () -> Unit,
    onGrantAllFilesAccess: () -> Unit,
    onImportFromCommonFilesystemRoots: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onImportAll: () -> Unit,
    onEnterDirectory: (String, String) -> Unit,
    onImportCandidate: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт TTS") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (browser.rootLabel != null) {
                        "Сохранённое дерево: ${browser.rootLabel}"
                    } else {
                        "Основной путь: автоимпорт из памяти устройства"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                browser.currentLabel?.let { current ->
                    Text(
                        text = "Текущая папка: $current",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                browser.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = if (hasDirectFilesystemTtsAccess) onImportFromCommonFilesystemRoots else onGrantAllFilesAccess,
                        modifier = Modifier.weight(1f),
                        enabled = !isImporting,
                    ) {
                        Icon(
                            if (hasDirectFilesystemTtsAccess) Icons.Default.Storage else Icons.Default.LockOpen,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (hasDirectFilesystemTtsAccess) "Автоимпорт" else "Доступ")
                    }
                    OutlinedButton(
                        onClick = onPickModelFile,
                        modifier = Modifier.weight(1f),
                        enabled = !isImporting,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Файл модели")
                    }
                }
                if (browser.rootUri != null || browser.currentUri != null) {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isImporting,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Обновить просмотр сохранённого дерева")
                    }
                }
                if (hasDirectFilesystemTtsAccess) {
                    if (commonFilesystemTtsRoots.isNotEmpty()) {
                        Text(
                            text = "Найдены возможные пути: ${commonFilesystemTtsRoots.joinToString()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Стандартные пути `tts`, `Download/tts`, `Documents/tts` и одноимённые папки в общей памяти будут найдены автоматически.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = "На этой прошивке выбор папки может быть сломан. После выдачи доступа приложение само найдёт папку `tts`, а запасной путь позволяет выбрать любой файл внутри нужной модели.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (browser.canGoUp) {
                    OutlinedButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вверх")
                    }
                }

                when (browser.accessState) {
                    TtsTreeAccessState.UNSET,
                    TtsTreeAccessState.NO_PERMISSION,
                    TtsTreeAccessState.INVALID_ROOT,
                    TtsTreeAccessState.PICKER_CANCELLED,
                    -> {
                        Text(
                            text = "Сначала попробуй автоимпорт. Если он не сработал, выбери любой файл внутри нужного пакета: model.onnx, tokenizer.json, manifest или голосовой WAV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    TtsTreeAccessState.READY -> {
                        val folderEntries = browser.entries.filter { it.isDirectory }
                        if (folderEntries.isNotEmpty()) {
                            Text(
                                text = "Папки",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            folderEntries.forEach { entry ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onEnterDirectory(entry.uri, entry.name) },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                            entry.subtitle?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                }
                            }
                        }

                        if (browser.candidates.isNotEmpty()) {
                            Text(
                                text = "Найденные пакеты",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            OutlinedButton(
                                onClick = onImportAll,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isImporting,
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Импортировать всё найденное")
                            }
                            browser.candidates.forEach { candidate ->
                                val statusColor = when (candidate.status) {
                                    TtsPackStatus.READY -> MaterialTheme.colorScheme.primary
                                    TtsPackStatus.READY_NON_RUSSIAN -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(candidate.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = buildString {
                                                append(candidate.engineFamily.label())
                                                candidate.runtimeFamily?.let {
                                                    append(" · ")
                                                    append(it)
                                                }
                                                append(" · ")
                                                append(candidate.status.label())
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                        )
                                        candidate.voiceSummary?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        candidate.reason?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onImportCandidate(candidate.sourceUri) },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isImporting,
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Импортировать этот пакет")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BookReadingScreen(
    book: EpubBook,
    currentChapter: EpubChapter?,
    currentChapterIndex: Int,
    isTtsPlaying: Boolean,
    ttsState: TtsState,
    speechRate: Float,
    autoAdvanceEnabled: Boolean,
    highlightRange: IntRange?,
    availableEngines: List<Pair<String, String>>,
    selectedEngine: String?,
    engineType: TtsEngineType,
    ttsVoiceOptions: List<TtsVoiceOption>,
    utrobinOrtThreads: Int,
    natashaOrtThreads: Int,
    chatterboxOrtThreads: Int,
    chatterboxExaggeration: Float,
    sherpaThreads: Int,
    performanceProfile: TtsBookPerformanceProfile,
    piperProsodyPreset: PiperProsodyPreset,
    systemPitch: Float,
    engineTunables: List<TtsEngineTunable>,
    sileroDownloadProgress: Float?,
    detectedTtsPacks: List<DetectedTtsPack>,
    downloadableTtsPacks: List<DownloadableTtsPack>,
    packDownloadProgress: Float?,
    packDownloadLabel: String?,
    lastTtsImportSummary: String?,
    ttsImportBrowser: TtsImportBrowserState,
    isTtsImporting: Boolean,
    hasDirectFilesystemTtsAccess: Boolean,
    commonFilesystemTtsRoots: List<String>,
    selectedPiperPackId: String?,
    piperDiagnostics: PiperPlaybackDiagnostics,
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
    onPiperProsodyPresetChange: (PiperProsodyPreset) -> Unit,
    onOpenTtsImportBrowser: () -> Unit,
    onPickTtsModelFile: () -> Unit,
    onGrantAllFilesAccess: () -> Unit,
    onImportFromCommonFilesystemRoots: () -> Unit,
    onTtsBrowserEnter: (String, String) -> Unit,
    onTtsBrowserUp: () -> Unit,
    onTtsBrowserRefresh: () -> Unit,
    onImportTtsCandidate: (String) -> Unit,
    onImportAllVisibleTtsCandidates: () -> Unit,
    onDeleteTtsPack: (String) -> Unit,
    onDeleteSuggestedTtsPacks: () -> Unit,
    onDownloadTtsPack: (String) -> Unit,
    onSelectEnginePack: (String) -> Unit,
    onResetProgress: () -> Unit,
) {
    var showChapterList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTtsImportBrowser by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val isTtsInitializing = ttsState is TtsState.Initializing

    // Track text layout for auto-scroll
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Auto-scroll to highlighted word
    LaunchedEffect(highlightRange) {
        if (highlightRange != null) {
            val layout = textLayoutResult ?: return@LaunchedEffect
            val content = currentChapter?.content?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
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
                        enabled = !isTtsInitializing,
                        modifier = Modifier.size(56.dp)
                    ) {
                        if (isTtsInitializing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = if (isTtsPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isTtsPlaying) "Пауза" else "Воспроизвести",
                                modifier = Modifier.size(32.dp)
                            )
                        }
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
                val removableSuggestedPacks = detectedTtsPacks.filter {
                    (it.suggestedDeletion || it.engineFamily.name != "PIPER") && it.canDelete
                }
                val piperPacks = detectedTtsPacks.filter {
                    it.engineFamily.name == "PIPER" && it.isRunnable && it.isRussianCapable
                }
                val nonPiperPacks = detectedTtsPacks.filter { it.engineFamily.name != "PIPER" }
                val incompletePiperPacks = detectedTtsPacks.filter {
                    it.engineFamily.name == "PIPER" && !(it.isRunnable && it.isRussianCapable)
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
                            text = "Скорость речи: ${formatOneDecimal(speechRate)}x",
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
                        Text(
                            text = "Интонация Piper",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = piperProsodyPreset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PiperProsodyPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = piperProsodyPreset == preset,
                                    onClick = { onPiperProsodyPresetChange(preset) },
                                    label = { Text(preset.displayName) },
                                )
                            }
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
                        title = "Piper: голоса",
                        subtitle = "В читалке оставлены только русские Piper-голоса и системный движок Android. Тяжелые экспериментальные модели отключены.",
                    ) {
                        OutlinedButton(
                            onClick = {
                                showTtsImportBrowser = true
                                onOpenTtsImportBrowser()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Импорт из папки TTS")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Официальные русские Piper-голоса: Irina, Ruslan, Denis, Dmitri. Уровень у всех medium; отдельного русского high в официальном списке нет.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        lastTtsImportSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                ),
                            ) {
                                Text(
                                    text = summary,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (removableSuggestedPacks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onDeleteSuggestedTtsPacks,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Удалить не-Piper модели (${removableSuggestedPacks.size})")
                            }
                        }
                        if (downloadableTtsPacks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Скачать русские Piper-голоса",
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
                        if (piperPacks.isEmpty()) {
                            Text(
                                text = "Установленных Piper-голосов пока нет. Скачай один из голосов выше.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "Установленные Piper-голоса",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            piperPacks.forEach { pack ->
                                ReaderPackCard(
                                    pack = pack,
                                    onDelete = if (pack.canDelete) {
                                        { onDeleteTtsPack(pack.packId) }
                                    } else {
                                        null
                                    },
                                )
                            }
                            if (incompletePiperPacks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Неполные Piper-пакеты",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                incompletePiperPacks.forEach { pack ->
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
                            if (nonPiperPacks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Отключены из читалки",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                nonPiperPacks.forEach { pack ->
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
                    }

                    ReaderSettingsSection(
                        title = "Движок чтения",
                        subtitle = "Сейчас доступны только стабильный Piper и системный Android TTS.",
                    ) {
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.SILERO,
                            title = "Piper / Sherpa (оффлайн)",
                            description = "Основной режим читалки: стабильный русский офлайн-TTS с короткими фрагментами и диагностикой.",
                            enabled = piperPacks.isNotEmpty(),
                            onClick = { onEngineTypeChange(TtsEngineType.SILERO) },
                        )
                        ReaderEngineOption(
                            selected = engineType == TtsEngineType.SYSTEM,
                            title = "Системный TTS Android",
                            description = "Google TTS или другой установленный движок Android.",
                            onClick = { onEngineTypeChange(TtsEngineType.SYSTEM) },
                        )
                    }

                    ReaderSettingsSection(
                        title = when (engineType) {
                            TtsEngineType.SILERO -> "Текущий движок: Piper"
                            TtsEngineType.SYSTEM -> "Текущий движок: системный TTS Android"
                            else -> "Текущий движок отключён"
                        },
                        subtitle = when (engineType) {
                            TtsEngineType.SILERO -> "Пакет равен голосу. Здесь выбирается конкретный Piper-голос и видна диагностика чтения."
                            TtsEngineType.SYSTEM -> "Выбор установленного системного TTS и его пользовательских параметров."
                            else -> "Выбери Piper или системный TTS Android."
                        },
                    ) {
                        when (engineType) {
                            TtsEngineType.SILERO -> {
                                if (piperPacks.isEmpty()) {
                                    Text(
                                        text = "Локальные Piper-пакеты не найдены.",
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
                            else -> {
                                Text(
                                    text = "Этот движок отключён в читалке. Выбери Piper или системный TTS Android.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (ttsVoiceOptions.isNotEmpty() && engineType == TtsEngineType.SYSTEM) {
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
                                        selected = false,
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
                                            "chatterbox_ort_intra_threads" -> chatterboxOrtThreads.toFloat()
                                            "chatterbox_exaggeration" -> chatterboxExaggeration
                                            "sherpa_num_threads" -> sherpaThreads.toFloat()
                                            else -> tunable.defaultValue
                                        }.coerceIn(tunable.range.start, tunable.range.endInclusive)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val valueLabel = when (tunable.key) {
                                            "ort_intra_threads",
                                            "natasha_ort_intra_threads",
                                            "chatterbox_ort_intra_threads",
                                            "sherpa_num_threads",
                                            -> sliderValue.toInt().toString()
                                            else -> formatTwoDecimal(sliderValue)
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

    if (showTtsImportBrowser) {
        TtsImportBrowserDialog(
            browser = ttsImportBrowser,
            isImporting = isTtsImporting,
            hasDirectFilesystemTtsAccess = hasDirectFilesystemTtsAccess,
            commonFilesystemTtsRoots = commonFilesystemTtsRoots,
            onDismiss = { showTtsImportBrowser = false },
            onPickModelFile = onPickTtsModelFile,
            onGrantAllFilesAccess = onGrantAllFilesAccess,
            onImportFromCommonFilesystemRoots = onImportFromCommonFilesystemRoots,
            onNavigateUp = onTtsBrowserUp,
            onRefresh = onTtsBrowserRefresh,
            onImportAll = onImportAllVisibleTtsCandidates,
            onEnterDirectory = onTtsBrowserEnter,
            onImportCandidate = onImportTtsCandidate,
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text(
                    text = "Нет рабочей русской модели для этого движка.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
                    text = "${pack.engineFamily.label()} · ${pack.status.label()}" +
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
                        contentDescription = "Удалить пакет",
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
                text = "Пакет: ${piperDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${piperDiagnostics.completedChunks}/${piperDiagnostics.totalChunks} · восстановлено ${piperDiagnostics.recoveredChunks} · ошибки ${piperDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Скорость ${formatOneDecimal(piperDiagnostics.speechRate)}x · потоки ${piperDiagnostics.sherpaThreads}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            piperDiagnostics.lastChunkDurationMs?.let { durationMs ->
                val audioMs = piperDiagnostics.lastChunkAudioMs?.let { " · аудио ${it}мс" }.orEmpty()
                val prefetch = if (piperDiagnostics.lastChunkPrefetched) {
                    " · ожидание предзагрузки ${piperDiagnostics.lastPrefetchWaitMs ?: 0}мс"
                } else {
                    ""
                }
                val queued = piperDiagnostics.prefetchQueuedIndex?.let { " · готовится #$it" }.orEmpty()
                Text(
                    text = "Генерация ${durationMs}мс$audioMs$prefetch · попадания ${piperDiagnostics.prefetchHits}$queued",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Интонация: ${piperDiagnostics.prosodyPresetLabel} · шум ${formatTwoDecimal(piperDiagnostics.noiseScale)} / ${formatTwoDecimal(piperDiagnostics.noiseScaleW)}",
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
                    text = "Последний фрагмент: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                piperDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · глубина ${piperDiagnostics.lastChunkSplitDepth}",
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
                text = "Токенизатор: ${natashaDiagnostics.tokenizerLabel ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Пакет: ${natashaDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${natashaDiagnostics.completedChunks}/${natashaDiagnostics.totalChunks} · восстановлено ${natashaDiagnostics.recoveredChunks} · ошибки ${natashaDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Скорость ${formatOneDecimal(natashaDiagnostics.speechRate)}x · потоки ${natashaDiagnostics.ortThreads}",
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
                    text = "Последний фрагмент: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                natashaDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · глубина ${natashaDiagnostics.lastChunkSplitDepth}",
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
                text = "Спикер: ${utrobinDiagnostics.speakerLabel ?: utrobinDiagnostics.speakerId}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Пакет: ${utrobinDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${utrobinDiagnostics.completedChunks}/${utrobinDiagnostics.totalChunks} · восстановлено ${utrobinDiagnostics.recoveredChunks} · ошибки ${utrobinDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Скорость ${formatOneDecimal(utrobinDiagnostics.speechRate)}x · потоки ${utrobinDiagnostics.ortThreads}",
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
                    text = "Последний фрагмент: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                utrobinDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · глубина ${utrobinDiagnostics.lastChunkSplitDepth}",
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
private fun ReaderChatterboxDiagnosticsCard(
    chatterboxDiagnostics: ChatterboxPlaybackDiagnostics,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Диагностика Chatterbox",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Язык: ${chatterboxDiagnostics.languageId}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Пакет: ${chatterboxDiagnostics.packId ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            chatterboxDiagnostics.voiceId?.let { voiceId ->
                Text(
                    text = "Голос: $voiceId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chatterboxDiagnostics.referenceVoicePath?.let { voice ->
                Text(
                    text = "Эталонный WAV: $voice",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Чанки: ${chatterboxDiagnostics.completedChunks}/${chatterboxDiagnostics.totalChunks} · восстановлено ${chatterboxDiagnostics.recoveredChunks} · ошибки ${chatterboxDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Скорость ${formatOneDecimal(chatterboxDiagnostics.speechRate)}x · эмоция ${formatTwoDecimal(chatterboxDiagnostics.exaggeration)} · потоки ${chatterboxDiagnostics.ortThreads}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            chatterboxDiagnostics.lastChunkDurationMs?.let { durationMs ->
                Text(
                    text = "Последняя генерация: ${durationMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chatterboxDiagnostics.lastGeneratedTokens?.let { tokenCount ->
                Text(
                    text = "Речевые токены: $tokenCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            chatterboxDiagnostics.lastRecoveryAction?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            chatterboxDiagnostics.lastChunkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последний фрагмент: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                chatterboxDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · глубина ${chatterboxDiagnostics.lastChunkSplitDepth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            chatterboxDiagnostics.lastFailureMessage?.let { message ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Последняя ошибка: $message",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                chatterboxDiagnostics.lastFailurePreview?.let { preview ->
                    Text(
                        text = "Проблемный фрагмент: $preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                chatterboxDiagnostics.lastFailureRange?.let { range ->
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
                text = "Голос: ${onnxDiagnostics.voiceId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Пакет: ${onnxDiagnostics.packRoot ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Чанки: ${onnxDiagnostics.completedChunks}/${onnxDiagnostics.totalChunks} · восстановлено ${onnxDiagnostics.recoveredChunks} · ошибки ${onnxDiagnostics.failedChunks}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Скорость ${formatOneDecimal(onnxDiagnostics.speechRate)}x · потоки ${onnxDiagnostics.ortThreads}",
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
                    text = "Последний фрагмент: $preview",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                onnxDiagnostics.lastChunkRange?.let { range ->
                    Text(
                        text = "Диапазон: ${range.asDisplayRange()} · глубина ${onnxDiagnostics.lastChunkSplitDepth}",
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

private fun TtsPackEngineFamily.label(): String = when (this) {
    TtsPackEngineFamily.PIPER -> "Piper"
    TtsPackEngineFamily.NATASHA -> "Natasha"
    TtsPackEngineFamily.UTROBIN -> "Utrobin"
    TtsPackEngineFamily.CHATTERBOX -> "Chatterbox"
    TtsPackEngineFamily.ONNX_EXTERNAL -> "Внешний ONNX"
}

private fun TtsPackStatus.label(): String = when (this) {
    TtsPackStatus.READY -> "готов"
    TtsPackStatus.READY_NON_RUSSIAN -> "не для русского"
    TtsPackStatus.INCOMPLETE -> "неполный"
    TtsPackStatus.UNSUPPORTED_RUNTIME -> "неподдерживаемый рантайм"
    TtsPackStatus.DISABLED_RUNTIME -> "рантайм отключен"
    TtsPackStatus.BROKEN_POINTER -> "битая ссылка"
    TtsPackStatus.INVALID_FILESET -> "неверный набор файлов"
}

private fun IntRange.asDisplayRange(): String = "${first}..${last}"

@SuppressLint("InlinedApi")
private fun buildManageAllFilesAccessIntent(packageName: String): Intent {
    val packageUri = Uri.parse("package:$packageName")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }
}

private val RussianNumberLocale: Locale = Locale.forLanguageTag("ru")

private fun formatOneDecimal(value: Float): String =
    String.format(RussianNumberLocale, "%.1f", value)

private fun formatTwoDecimal(value: Float): String =
    String.format(RussianNumberLocale, "%.2f", value)
