package com.soll.domain.tts.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.soll.domain.tts.TtsEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class TtsPackLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val importedRoot = File(context.filesDir, "tts_packs")
    private val legacyPiperExtractedRoot = File(context.filesDir, "piper_ru")
    private val legacyNatashaExtractedRoot = File(context.filesDir, "natasha_vits2_extracted")
    private val legacyUtrobinExtractedRoot = File(context.filesDir, "utrobin_tts_extracted")
    private val legacyOnnxInternalRoot = File(context.filesDir, "external_models/tts")
    private val legacyOnnxExternalRoot: File? = context.getExternalFilesDir(null)?.let { File(it, "tts_models") }
    private val okHttpClient = OkHttpClient()

    private val _downloadState = MutableStateFlow<TtsPackDownloadState?>(null)
    val downloadState: StateFlow<TtsPackDownloadState?> = _downloadState.asStateFlow()
    private var detectedPacksCache: List<DetectedTtsPack>? = null
    private var detectedPacksCacheAtMs: Long = 0L

    fun importFromTreeUri(treeUri: Uri): TtsPackImportResult {
        invalidateDetectedPacksCache()
        val root = prepareReadableTreeRoot(treeUri)
            ?: return TtsPackImportResult(detectedCount = 0, importedCount = 0, failedCount = 0)
        return importCandidates(root, collectImportCandidates(root))
    }

    fun importSelectedFromTreeUri(
        treeUri: Uri,
        sourceUris: Set<String>,
    ): TtsPackImportResult {
        invalidateDetectedPacksCache()
        val root = prepareReadableTreeRoot(treeUri)
            ?: return TtsPackImportResult(detectedCount = 0, importedCount = 0, failedCount = 0)
        val selected = collectImportCandidates(root)
            .filter { it.sourceDir.uri.toString() in sourceUris }
        return importCandidates(root, selected)
    }

    fun browseImportTree(
        treeUri: Uri?,
        currentUri: Uri? = null,
    ): TtsImportBrowserState {
        if (treeUri == null) {
            return TtsImportBrowserState(
                accessState = TtsTreeAccessState.UNSET,
                message = "Основной путь: выдай доступ ко всем файлам и запусти автоимпорт. Запасной путь: выбери любой файл внутри нужного TTS-pack.",
            )
        }
        val root = resolveTreeRoot(treeUri)
            ?: return TtsImportBrowserState(
                accessState = TtsTreeAccessState.INVALID_ROOT,
                rootUri = treeUri.toString(),
                message = "Не удалось открыть выбранное дерево папок.",
            )
        if (!root.isDirectory || !root.canRead()) {
            return TtsImportBrowserState(
                accessState = TtsTreeAccessState.NO_PERMISSION,
                rootUri = treeUri.toString(),
                rootLabel = root.name ?: "tts",
                message = "Нет доступа к выбранной папке tts. Выбери её заново.",
            )
        }
        val current = currentUri?.let(::resolveAnyDocument)?.takeIf { it.canRead() } ?: root
        val entries = current.listFiles()
            .orEmpty()
            .filter { it.canRead() }
            .sortedWith(
                compareByDescending<DocumentFile> { it.isDirectory }
                    .thenBy { it.name?.lowercase(Locale.getDefault()).orEmpty() },
            )
            .map { child ->
                TtsTreeBrowserEntry(
                    uri = child.uri.toString(),
                    name = child.name ?: "без имени",
                    isDirectory = child.isDirectory,
                    subtitle = when {
                        child.isDirectory -> "Папка"
                        else -> "${child.type ?: "файл"} · ${child.length().coerceAtLeast(0L)} B"
                    },
                )
            }
        val candidates = collectImportCandidates(current)
            .map(::previewImportCandidate)
            .sortedWith(
                compareByDescending<TtsImportCandidatePreview> { it.status == TtsPackStatus.READY }
                    .thenBy { it.engineFamily.ordinal }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) },
            )
        return TtsImportBrowserState(
            accessState = TtsTreeAccessState.READY,
            rootUri = treeUri.toString(),
            rootLabel = root.name ?: "tts",
            currentUri = current.uri.toString(),
            currentLabel = current.name ?: root.name ?: "tts",
            canGoUp = current.uri != root.uri,
            entries = entries,
            candidates = candidates,
            message = when {
                candidates.isNotEmpty() -> "Найдено паков: ${candidates.size}"
                entries.isEmpty() -> "Папка пуста или в ней нет читаемых файлов."
                else -> "В этой папке пока не найдено поддерживаемых TTS-паков."
            },
        )
    }

    fun pickerCancelledState(savedRootUri: String?): TtsImportBrowserState {
        return if (savedRootUri.isNullOrBlank()) {
            TtsImportBrowserState(
                accessState = TtsTreeAccessState.PICKER_CANCELLED,
                message = "Выбор файла отменён. Можно использовать автоимпорт или выбрать другой файл внутри модели.",
            )
        } else {
            TtsImportBrowserState(
                accessState = TtsTreeAccessState.PICKER_CANCELLED,
                rootUri = savedRootUri,
                message = "Выбор нового файла отменён. Можно продолжить просмотр уже сохранённого дерева или использовать автоимпорт.",
            )
        }
    }

    fun listDetectedPacks(): List<DetectedTtsPack> {
        val now = System.currentTimeMillis()
        detectedPacksCache?.takeIf { now - detectedPacksCacheAtMs < DETECTED_PACKS_CACHE_MS }?.let { cached ->
            return cached
        }
        val packs = mutableListOf<DetectedTtsPack>()
        scanImportedPacks(packs)
        scanLegacyRuntimePacks(packs)
        scanLegacyOnnxPacks(packs)
        scanExternalLinkedPacks(packs)
        val detected = packs
            .dedupeDetectedPacks()
            .sortedWith(
                compareByDescending<DetectedTtsPack> { it.isRussianCapable }
                    .thenBy { it.engineFamily.ordinal }
                    .thenBy { engineSelectionRank(it) }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) },
            )
            .also { detected ->
                detectedPacksCache = detected
                detectedPacksCacheAtMs = now
                Timber.d("Detected %d TTS packs", detected.size)
                detected.forEach { pack ->
                    Timber.d(
                        "Pack %s status=%s voices=%s root=%s reason=%s",
                        pack.packId,
                        pack.status,
                        pack.voices.joinToString { it.label },
                        pack.rootDir,
                        pack.reason,
                    )
                }
            }
        return detected
    }

    fun listDownloadableRussianPacks(): List<DownloadableTtsPack> =
        PIPER_REMOTE_PACKS.map { remote ->
            DownloadableTtsPack(
                id = remote.downloadId,
                engineFamily = TtsPackEngineFamily.PIPER,
                displayName = "Piper ${remote.voiceLabel}",
                description = "Официальный Sherpa-совместимый русский голос Piper ${remote.voiceLabel} (~${remote.estimatedSizeMb} MB).",
                estimatedSizeMb = remote.estimatedSizeMb,
                isRussianCapable = true,
                suggestedEnginePackId = remote.packId,
            )
        }

    fun findPackById(packId: String): DetectedTtsPack? =
        listDetectedPacks().firstOrNull { it.packId == packId }

    fun deletePack(packId: String): Boolean {
        val pack = findPackById(packId) ?: return false
        if (!pack.canDelete) return false
        return runCatching {
            File(pack.rootDir).deleteRecursively()
        }.getOrDefault(false).also { if (it) invalidateDetectedPacksCache() }
    }

    fun deleteSuggestedPacks(): Int {
        val targets = listDetectedPacks()
            .filter { (it.suggestedDeletion || it.engineFamily != TtsPackEngineFamily.PIPER) && it.canDelete }
            .map { it.packId }
            .distinct()
        var deleted = 0
        for (packId in targets) {
            if (deletePack(packId)) deleted++
        }
        return deleted
    }

    suspend fun downloadPack(packId: String): Boolean {
        invalidateDetectedPacksCache()
        PIPER_REMOTE_PACKS.firstOrNull { it.downloadId == packId }?.let { remote ->
            return downloadPiperPack(
                downloadId = remote.downloadId,
                label = "Piper ${remote.voiceLabel}",
                url = remote.archiveUrl,
                destSlug = remote.packSlug,
            ).also { if (it) invalidateDetectedPacksCache() }
        }
        return false
    }

    fun findBestPack(type: TtsEngineType): DetectedTtsPack? {
        val family = when (type) {
            TtsEngineType.SILERO -> TtsPackEngineFamily.PIPER
            TtsEngineType.NATASHA -> TtsPackEngineFamily.NATASHA
            TtsEngineType.UTROBIN -> TtsPackEngineFamily.UTROBIN
            TtsEngineType.CHATTERBOX -> TtsPackEngineFamily.CHATTERBOX
            TtsEngineType.ONNX_EXTERNAL -> TtsPackEngineFamily.ONNX_EXTERNAL
            TtsEngineType.SYSTEM -> return null
        }
        return listDetectedPacks()
            .filter { it.engineFamily == family }
            .filter { it.isRunnable }
            .filter { it.isRussianCapable }
            .sortedWith(engineSelectionComparator())
            .firstOrNull()
    }

    fun listPacksFor(type: TtsEngineType): List<DetectedTtsPack> {
        val family = when (type) {
            TtsEngineType.SILERO -> TtsPackEngineFamily.PIPER
            TtsEngineType.NATASHA -> TtsPackEngineFamily.NATASHA
            TtsEngineType.UTROBIN -> TtsPackEngineFamily.UTROBIN
            TtsEngineType.CHATTERBOX -> TtsPackEngineFamily.CHATTERBOX
            TtsEngineType.ONNX_EXTERNAL -> TtsPackEngineFamily.ONNX_EXTERNAL
            TtsEngineType.SYSTEM -> return emptyList()
        }
        return listDetectedPacks().filter { it.engineFamily == family }
    }

    fun hasDirectFilesystemAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun listCommonFilesystemRootPaths(): List<String> =
        discoverFilesystemTtsRoots().map { it.absolutePath }

    fun importFromCommonFilesystemRoots(): TtsPackImportResult {
        invalidateDetectedPacksCache()
        if (!hasDirectFilesystemAccess()) {
            Timber.w("TTS auto-import skipped: no direct filesystem access")
            return TtsPackImportResult(
                detectedCount = 0,
                importedCount = 0,
                failedCount = 0,
                issues = listOf("Нет доступа ко всем файлам для прямого сканирования папки tts."),
            )
        }
        val roots = discoverFilesystemTtsRoots().filter { it.isDirectory && it.canRead() }
        Timber.i(
            "TTS auto-import roots=%s",
            roots.joinToString { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) },
        )
        if (roots.isEmpty()) {
            return TtsPackImportResult(
                detectedCount = 0,
                importedCount = 0,
                failedCount = 0,
                issues = listOf("Не найдена ни одна папка tts в общей памяти устройства."),
            )
        }
        val candidates = roots
            .flatMap(::collectFileImportCandidates)
            .distinctBy { "${it.engineDirName}:${it.destSlug}:${it.sourceDir.absolutePath}" }
        Timber.i(
            "TTS auto-import candidates=%d roots=%d",
            candidates.size,
            roots.size,
        )
        return importFileCandidates(candidates)
    }

    fun importFromPickedDocument(documentUri: Uri): TtsPackImportResult {
        invalidateDetectedPacksCache()
        Timber.i("TTS picked document uri=%s", documentUri)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                documentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Timber.w(error, "Failed to persist read permission for picked TTS document=%s", documentUri)
        }
        val fileTarget = resolveFilesystemFileFromDocumentUri(documentUri)
        Timber.i("TTS picked document resolved file target=%s", fileTarget?.absolutePath)
        if (fileTarget != null && hasDirectFilesystemAccess()) {
            return importFromFilesystemTarget(fileTarget)
        }

        val picked = resolveAnyDocument(documentUri)
            ?: return TtsPackImportResult(
                detectedCount = 0,
                importedCount = 0,
                failedCount = 0,
                issues = listOf("Не удалось открыть выбранный файл TTS."),
            )
        var current: DocumentFile? = picked.takeIf { it.isDirectory } ?: picked.parentFile
        repeat(8) {
            val dir = current ?: return@repeat
            val exact = detectImportCandidate(dir)
            if (exact != null) {
                return importCandidates(dir, listOf(exact))
            }
            if (dir.name?.equals("tts", ignoreCase = true) == true) {
                val candidates = collectImportCandidates(dir)
                if (candidates.isNotEmpty()) return importCandidates(dir, candidates)
            }
            current = dir.parentFile
        }
        return TtsPackImportResult(
            detectedCount = 0,
            importedCount = 0,
            failedCount = 0,
            issues = listOf("Выбранный файл не относится к поддержанному TTS-pack. Выбери model.onnx, tokenizer.json, manifest или любой файл внутри нужного pack-а."),
        )
    }

    private fun prepareReadableTreeRoot(treeUri: Uri): DocumentFile? {
        persistReadPermission(treeUri)
        return resolveTreeRoot(treeUri)?.takeIf { it.isDirectory && it.canRead() }
    }

    private fun persistReadPermission(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Timber.w(error, "Failed to persist read permission for TTS tree=%s", treeUri)
        }
    }

    private fun resolveTreeRoot(treeUri: Uri): DocumentFile? {
        return runCatching { DocumentFile.fromTreeUri(context, treeUri) }
            .onFailure { error -> Timber.w(error, "Failed to resolve tree uri=%s", treeUri) }
            .getOrNull()
    }

    private fun resolveAnyDocument(uri: Uri): DocumentFile? {
        return runCatching { DocumentFile.fromTreeUri(context, uri) }
            .getOrNull()
            ?.takeIf { it.exists() }
            ?: runCatching { DocumentFile.fromSingleUri(context, uri) }
                .getOrNull()
                ?.takeIf { it.exists() }
    }

    private fun importCandidates(
        treeRoot: DocumentFile,
        candidates: List<ImportCandidate>,
    ): TtsPackImportResult {
        if (candidates.isEmpty()) {
            return TtsPackImportResult(
                detectedCount = 0,
                importedCount = 0,
                failedCount = 0,
            )
        }
        val issues = mutableListOf<String>()
        var importedCount = 0
        var failedCount = 0
        importedRoot.mkdirs()
        candidates.forEach { candidate ->
            val destDir = File(importedRoot, "${candidate.engineDirName}/${candidate.destSlug}")
            val tempDir = freshTempImportDir(candidate.engineDirName, candidate.destSlug)
            runCatching {
                copyDocumentDirToFiles(candidate.sourceDir, tempDir)
                if (candidate.isKokoroManifestRoot) {
                    mergeKokoroVoicesFromNearbyTree(tempDir, candidate.sourceDir)
                    mergeKokoroVoicesFromSelectedTree(tempDir, treeRoot)
                }
                val validatedPack = validateImportedPack(candidate, tempDir)
                if (!validatedPack.isRunnable) {
                    error(validatedPack.reason ?: "Импортированный pack не готов к запуску")
                }
                replaceImportedDir(tempDir, destDir)
            }.onSuccess {
                importedCount++
            }.onFailure { error ->
                failedCount++
                tempDir.deleteRecursively()
                issues += "${candidate.sourceDir.name ?: candidate.destSlug}: ${error.message ?: "ошибка импорта"}"
                Timber.e(
                    error,
                    "Failed to import TTS pack dir=%s engine=%s slug=%s",
                    candidate.sourceDir.uri,
                    candidate.engineDirName,
                    candidate.destSlug,
                )
            }
        }
        return TtsPackImportResult(
            detectedCount = candidates.size,
            importedCount = importedCount,
            failedCount = failedCount,
            issues = issues,
        )
    }

    private fun importFileCandidates(candidates: List<FileImportCandidate>): TtsPackImportResult {
        if (candidates.isEmpty()) {
            return TtsPackImportResult(
                detectedCount = 0,
                importedCount = 0,
                failedCount = 0,
            )
        }
        val issues = mutableListOf<String>()
        var importedCount = 0
        var failedCount = 0
        importedRoot.mkdirs()
        candidates.forEach { candidate ->
            if (candidate.engineDirName == "onnx") {
                val detected = inspectFileCandidate(candidate, TtsPackSourceType.EXTERNAL_LINKED)
                if (detected == null) {
                    failedCount++
                    issues += "${candidate.sourceDir.name}: ONNX pack не распознан"
                    Timber.w(
                        "Skipped unresolved external ONNX pack dir=%s slug=%s",
                        candidate.sourceDir.absolutePath,
                        candidate.destSlug,
                    )
                } else {
                    importedCount++
                    if (!detected.isRunnable || !detected.isRussianCapable) {
                        issues += buildString {
                            append(detected.displayName)
                            append(": ")
                            append(detected.reason ?: detected.status.name)
                        }
                    }
                    Timber.i(
                        "Linked external ONNX TTS pack id=%s status=%s root=%s reason=%s",
                        detected.packId,
                        detected.status,
                        detected.rootDir,
                        detected.reason,
                    )
                }
                return@forEach
            }
            val destDir = File(importedRoot, "${candidate.engineDirName}/${candidate.destSlug}")
            val tempDir = freshTempImportDir(candidate.engineDirName, candidate.destSlug)
            runCatching {
                copyFileDirToFiles(candidate.sourceDir, tempDir)
                val validatedPack = validateImportedPack(
                    candidate = ImportCandidate(
                        sourceDir = DocumentFile.fromFile(candidate.sourceDir),
                        engineDirName = candidate.engineDirName,
                        destSlug = candidate.destSlug,
                        isKokoroManifestRoot = candidate.isKokoroManifestRoot,
                    ),
                    dest = tempDir,
                )
                if (!validatedPack.isRunnable) {
                    error(validatedPack.reason ?: "Импортированный pack не готов к запуску")
                }
                replaceImportedDir(tempDir, destDir)
            }.onSuccess {
                importedCount++
            }.onFailure { error ->
                failedCount++
                tempDir.deleteRecursively()
                issues += "${candidate.sourceDir.name}: ${error.message ?: "ошибка импорта"}"
                Timber.e(
                    error,
                    "Failed to import file-system TTS pack dir=%s engine=%s slug=%s",
                    candidate.sourceDir.absolutePath,
                    candidate.engineDirName,
                    candidate.destSlug,
                )
            }
        }
        return TtsPackImportResult(
            detectedCount = candidates.size,
            importedCount = importedCount,
            failedCount = failedCount,
            issues = issues,
        )
    }

    private fun importFromFilesystemTarget(target: File): TtsPackImportResult {
        Timber.i("Importing TTS from filesystem target=%s", target.absolutePath)
        var current: File? = target.takeIf { it.isDirectory } ?: target.parentFile
        repeat(8) {
            val dir = current ?: return@repeat
            Timber.d("Inspecting filesystem dir for TTS import=%s", dir.absolutePath)
            detectFileImportCandidate(dir)?.let { candidate ->
                Timber.i(
                    "Resolved direct TTS pack from picked file dir=%s engine=%s slug=%s",
                    dir.absolutePath,
                    candidate.engineDirName,
                    candidate.destSlug,
                )
                return importFileCandidates(listOf(candidate))
            }
            if (dir.name.equals("tts", ignoreCase = true)) {
                val candidates = collectFileImportCandidates(dir)
                Timber.i(
                    "Resolved TTS root from picked file dir=%s candidates=%d",
                    dir.absolutePath,
                    candidates.size,
                )
                if (candidates.isNotEmpty()) return importFileCandidates(candidates)
            }
            current = dir.parentFile
        }
        return TtsPackImportResult(
            detectedCount = 0,
            importedCount = 0,
            failedCount = 0,
            issues = listOf("Не удалось определить pack по выбранному файлу. Выбери файл внутри конкретной модели или папки tts."),
        )
    }

    private fun resolveFilesystemFileFromDocumentUri(uri: Uri): File? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path?.let(::File)
        }
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        Timber.d("Resolving filesystem path from document uri=%s docId=%s", uri, docId)
        val parts = docId.split(':', limit = 2)
        if (parts.size != 2) return null
        val volume = parts[0]
        val relativePath = parts[1]
        val base = when {
            volume.equals("primary", ignoreCase = true) ->
                Environment.getExternalStorageDirectory()
            volume.matches(Regex("\\d+")) ->
                File("/storage/$volume")
            else ->
                File("/storage/$volume")
        }
        return File(base, relativePath).takeIf { it.exists() }
    }

    private fun previewImportCandidate(candidate: ImportCandidate): TtsImportCandidatePreview {
        val children = candidate.sourceDir.listFiles().orEmpty().toList()
        val childNamesLower = children.mapNotNull { it.name?.lowercase(Locale.getDefault()) }.toSet()
        val manifest = children.firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
        return when (candidate.engineDirName) {
            "piper" -> previewPiperCandidate(candidate, children)
            "natasha" -> previewNatashaCandidate(candidate, children)
            "utrobin" -> previewUtrobinCandidate(candidate, children)
            "onnx" -> when {
                manifest != null -> previewManifestOnnxCandidate(candidate, manifest, childNamesLower)
                isChatterboxImportCandidate(childNamesLower) -> previewChatterboxCandidate(candidate, children, runtimeFamily = null)
                else -> TtsImportCandidatePreview(
                    sourceUri = candidate.sourceDir.uri.toString(),
                    displayName = candidate.sourceDir.name ?: candidate.destSlug,
                    engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
                    status = TtsPackStatus.INCOMPLETE,
                    reason = "Папка похожа на ONNX pack, но runtime не распознан",
                )
            }
            else -> TtsImportCandidatePreview(
                sourceUri = candidate.sourceDir.uri.toString(),
                displayName = candidate.sourceDir.name ?: candidate.destSlug,
                engineFamily = TtsPackEngineFamily.PIPER,
                status = TtsPackStatus.INVALID_FILESET,
                reason = "Неизвестный тип TTS-пака",
            )
        }
    }

    private fun detectImportCandidate(dir: DocumentFile): ImportCandidate? {
        if (!dir.isDirectory || !dir.canRead()) return null
        val name = dir.name.orEmpty()
        val children = dir.listFiles().orEmpty()
        val childNamesLower = children.mapNotNull { it.name?.lowercase(Locale.getDefault()) }.toSet()
        val manifest = children.firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
        if (manifest != null) {
            val meta = parseManifestDocument(manifest)
            if (meta != null) {
                return ImportCandidate(
                    sourceDir = dir,
                    engineDirName = "onnx",
                    destSlug = "${meta.modelId}_${meta.precision}",
                    isKokoroManifestRoot = meta.runtimeFamily == "kokoro_v1",
                )
            }
        }
        val lowerName = name.lowercase(Locale.getDefault())
        return when {
            isNatashaImportCandidate(lowerName, childNamesLower) ->
                ImportCandidate(dir, "natasha", sanitizeSlug(name.ifBlank { "natasha_vits2" }))
            isUtrobinImportCandidate(childNamesLower) ->
                ImportCandidate(dir, "utrobin", sanitizeSlug(name.ifBlank { "utrobin_vits" }))
            children.any { child -> child.isFile && PIPER_VOICE_REGEX.containsMatchIn(child.name.orEmpty()) } ->
                ImportCandidate(dir, "piper", sanitizeSlug(name.ifBlank { "piper_pack" }))
            isChatterboxImportCandidate(childNamesLower) ->
                ImportCandidate(dir, "onnx", sanitizeSlug(name.ifBlank { "chatterbox_pack" }))
            else -> null
        }
    }

    private fun previewPiperCandidate(
        candidate: ImportCandidate,
        children: List<DocumentFile>,
    ): TtsImportCandidatePreview {
        val onnxVoiceIds = children.mapNotNull { child ->
            child.name?.let { name -> PIPER_VOICE_REGEX.find(name)?.groupValues?.getOrNull(1) }
        }
        val hasTokens = children.any { child -> child.isFile && child.name.equals("tokens.txt", ignoreCase = true) }
        val hasEspeak = children.any { child -> child.isDirectory && child.name.equals("espeak-ng-data", ignoreCase = true) }
        val pointerLike = children.any { child ->
            child.isFile && child.name?.endsWith(".onnx", ignoreCase = true) == true && isLikelyPointerDocument(child)
        }
        val status = when {
            onnxVoiceIds.isEmpty() -> TtsPackStatus.INCOMPLETE
            pointerLike -> TtsPackStatus.BROKEN_POINTER
            !hasTokens || !hasEspeak -> TtsPackStatus.INCOMPLETE
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> when {
                onnxVoiceIds.isEmpty() -> "Нет ONNX-голоса Piper"
                !hasTokens -> "Нет tokens.txt для Sherpa/Piper"
                !hasEspeak -> "Нет espeak-ng-data для Sherpa/Piper"
                else -> "Пак Piper неполный"
            }
            TtsPackStatus.BROKEN_POINTER -> "Файл голоса похож на Git LFS/Xet pointer"
            else -> null
        }
        val voiceSummary = onnxVoiceIds.distinct()
            .ifEmpty { listOf(candidate.destSlug) }
            .joinToString { piperVoiceLabel(it) }
        return TtsImportCandidatePreview(
            sourceUri = candidate.sourceDir.uri.toString(),
            displayName = "Piper / Sherpa",
            engineFamily = TtsPackEngineFamily.PIPER,
            status = status,
            reason = reason,
            voiceSummary = voiceSummary,
        )
    }

    private fun previewNatashaCandidate(
        candidate: ImportCandidate,
        children: List<DocumentFile>,
    ): TtsImportCandidatePreview {
        val model = children.firstOrNull { child ->
            child.isFile && NATASHA_MODEL_CANDIDATES.any { it.equals(child.name, ignoreCase = true) }
        }
        val status = when {
            model == null -> TtsPackStatus.INCOMPLETE
            isLikelyPointerDocument(model) -> TtsPackStatus.BROKEN_POINTER
            model.length() < MIN_NATASHA_MODEL_BYTES -> TtsPackStatus.INVALID_FILESET
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> "Нет model.onnx/natasha.onnx"
            TtsPackStatus.BROKEN_POINTER -> "Файл модели похож на Git LFS/Xet pointer"
            TtsPackStatus.INVALID_FILESET -> "Модель Natasha слишком маленькая"
            else -> null
        }
        return TtsImportCandidatePreview(
            sourceUri = candidate.sourceDir.uri.toString(),
            displayName = "Natasha VITS2",
            engineFamily = TtsPackEngineFamily.NATASHA,
            status = status,
            reason = reason,
            voiceSummary = "Natasha RU",
        )
    }

    private fun previewUtrobinCandidate(
        candidate: ImportCandidate,
        children: List<DocumentFile>,
    ): TtsImportCandidatePreview {
        val model = children.firstOrNull { it.isFile && it.name.equals("model.onnx", ignoreCase = true) }
        val vocab = children.firstOrNull { child ->
            child.isFile && listOf("tokens.txt", "vocab.json", "tokenizer.json").any {
                it.equals(child.name, ignoreCase = true)
            }
        }
        val status = when {
            model == null -> TtsPackStatus.INCOMPLETE
            isLikelyPointerDocument(model) -> TtsPackStatus.BROKEN_POINTER
            model.length() < MIN_UTROBIN_MODEL_BYTES -> TtsPackStatus.INVALID_FILESET
            vocab == null -> TtsPackStatus.INCOMPLETE
            isLikelyPointerDocument(vocab) -> TtsPackStatus.BROKEN_POINTER
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> when {
                model == null -> "Нет model.onnx"
                vocab == null -> "Нет tokens.txt/vocab.json/tokenizer.json"
                else -> "Пак Utrobin неполный"
            }
            TtsPackStatus.BROKEN_POINTER -> "Файлы пакета похожи на Git LFS/Xet pointer"
            TtsPackStatus.INVALID_FILESET -> "Модель Utrobin слишком маленькая"
            else -> null
        }
        return TtsImportCandidatePreview(
            sourceUri = candidate.sourceDir.uri.toString(),
            displayName = "Utrobin VITS",
            engineFamily = TtsPackEngineFamily.UTROBIN,
            status = status,
            reason = reason,
            voiceSummary = "Женский, Мужской",
        )
    }

    private fun previewManifestOnnxCandidate(
        candidate: ImportCandidate,
        manifest: DocumentFile,
        childNamesLower: Set<String>,
    ): TtsImportCandidatePreview {
        val meta = parseManifestDocument(manifest)
        if (meta == null) {
            return TtsImportCandidatePreview(
                sourceUri = candidate.sourceDir.uri.toString(),
                displayName = candidate.sourceDir.name ?: candidate.destSlug,
                engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
                status = TtsPackStatus.INVALID_FILESET,
                reason = "Не удалось разобрать model_manifest.json",
            )
        }
        return when (meta.runtimeFamily) {
            "kokoro_v1" -> previewKokoroCandidate(candidate, meta)
            "chatterbox_v1", "chatterbox_turbo_v1" -> previewChatterboxCandidate(
                candidate = candidate,
                children = candidate.sourceDir.listFiles().orEmpty().toList(),
                runtimeFamily = meta.runtimeFamily,
                modelId = meta.modelId,
                precision = meta.precision,
            )
            else -> TtsImportCandidatePreview(
                sourceUri = candidate.sourceDir.uri.toString(),
                displayName = meta.modelId,
                engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
                runtimeFamily = meta.runtimeFamily,
                status = TtsPackStatus.UNSUPPORTED_RUNTIME,
                reason = "Runtime family '${meta.runtimeFamily ?: "unknown"}' не поддержан на Android",
                voiceSummary = if ("voices" in childNamesLower) "Есть voices/" else null,
            )
        }
    }

    private fun previewKokoroCandidate(
        candidate: ImportCandidate,
        meta: ManifestMeta,
    ): TtsImportCandidatePreview {
        val root = candidate.sourceDir
        val children = root.listFiles().orEmpty()
        val hasConfig = children.any { it.isFile && it.name.equals("config.json", ignoreCase = true) }
        val voicesDir = children.firstOrNull { it.isDirectory && it.name.equals("voices", ignoreCase = true) }
        val hasVoices = voicesDir?.hasUsableBinChildren() == true
        val onnxDir = children.firstOrNull { it.isDirectory && it.name.equals("onnx", ignoreCase = true) }
        val graphOwner = onnxDir ?: root
        val graphNames = graphOwner.listFiles().mapNotNull { it.name?.lowercase(Locale.getDefault()) }.toSet()
        val hasGraph = setOf(
            "model.onnx",
            "model_fp16.onnx",
            "model_quantized.onnx",
            "model_q8f16.onnx",
            "model_uint8f16.onnx",
            "model_q4f16.onnx",
        )
            .any { it in graphNames }
        val status = when {
            !hasConfig || !hasVoices || !hasGraph -> TtsPackStatus.INCOMPLETE
            else -> TtsPackStatus.READY_NON_RUSSIAN
        }
        val reason = when {
            !hasConfig -> "Нет config.json"
            !hasVoices -> "Нет голосов Kokoro в voices/"
            !hasGraph -> "Нет ONNX-графа Kokoro"
            else -> null
        }
        return TtsImportCandidatePreview(
            sourceUri = candidate.sourceDir.uri.toString(),
            displayName = meta.modelId,
            engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
            runtimeFamily = meta.runtimeFamily,
            status = status,
            reason = reason,
            voiceSummary = if (hasVoices) "Есть локальные голоса Kokoro" else null,
        )
    }

    private fun previewChatterboxCandidate(
        candidate: ImportCandidate,
        children: List<DocumentFile>,
        runtimeFamily: String?,
        modelId: String? = null,
        precision: String? = null,
    ): TtsImportCandidatePreview {
        val onnxDir = children.firstOrNull { it.isDirectory && it.name.equals("onnx", ignoreCase = true) }
        val onnxNames = onnxDir?.listFiles()?.mapNotNull { it.name?.lowercase(Locale.getDefault()) }?.toSet().orEmpty()
        val hasTokenizer = children.any { it.isFile && it.name.equals("tokenizer.json", ignoreCase = true) }
        val voiceFiles = children.filter { it.isFile && it.name?.endsWith(".wav", ignoreCase = true) == true }
        val hasBaseGraphs = listOf("speech_encoder", "embed_tokens", "conditional_decoder")
            .all { base -> hasChatterboxGraphName(onnxNames, base) }
        val hasLmGraph = hasChatterboxGraphName(onnxNames, "language_model")
        val effectiveRuntime = runtimeFamily ?: "chatterbox_v1"
        val effectivePrecision = precision ?: when {
            onnxNames.any { it.contains("language_model") && (it.contains("q4") || it.contains("int4") || it.contains("uint8")) } -> "int4"
            onnxNames.any { it.contains("language_model") && (it.contains("fp16") || it.contains("q8f16")) } -> "fp16"
            else -> "fp32"
        }
        val pointerLike = voiceFiles.any(::isLikelyPointerDocument) ||
            onnxDir?.listFiles().orEmpty().any { child -> child.isFile && isLikelyPointerDocument(child) }
        val status = when {
            effectiveRuntime == "chatterbox_turbo_v1" -> TtsPackStatus.UNSUPPORTED_RUNTIME
            !hasTokenizer || voiceFiles.isEmpty() || onnxDir == null || !hasBaseGraphs || !hasLmGraph -> TtsPackStatus.INCOMPLETE
            pointerLike -> TtsPackStatus.BROKEN_POINTER
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.UNSUPPORTED_RUNTIME -> "Chatterbox Turbo пока не поддержан на Android"
            TtsPackStatus.INCOMPLETE -> when {
                !hasTokenizer -> "Нет tokenizer.json"
                voiceFiles.isEmpty() -> "Нет reference voice WAV"
                onnxDir == null -> "Нет папки onnx/"
                !hasBaseGraphs -> "Не хватает базовых ONNX-графов Chatterbox"
                !hasLmGraph -> "Нет language_model*.onnx"
                else -> "Пак Chatterbox неполный"
            }
            TtsPackStatus.BROKEN_POINTER -> "Файлы Chatterbox похожи на Git LFS/Xet pointer"
            else -> null
        }
        return TtsImportCandidatePreview(
            sourceUri = candidate.sourceDir.uri.toString(),
            displayName = modelId ?: "Chatterbox Multilingual",
            engineFamily = TtsPackEngineFamily.CHATTERBOX,
            runtimeFamily = effectiveRuntime,
            status = status,
            reason = reason,
            voiceSummary = buildString {
                append("voice: ")
                append(voiceFiles.firstOrNull()?.name ?: "нет")
                append(" · ")
                append(effectivePrecision)
            },
        )
    }

    private fun hasChatterboxGraphName(names: Set<String>, baseName: String): Boolean {
        return chatterboxGraphBaseNames(baseName).any { graphBase ->
            names.any { name ->
                name == "$graphBase.onnx" ||
                    name == "${graphBase}_fp16.onnx" ||
                    name == "${graphBase}_q8f16.onnx" ||
                    name == "${graphBase}_q4.onnx" ||
                    name == "${graphBase}_q4f16.onnx" ||
                    name == "${graphBase}_int4.onnx" ||
                    name == "${graphBase}_quantized.onnx" ||
                    name == "${graphBase}_uint8f16.onnx"
            }
        }
    }

    private fun isLikelyPointerDocument(file: DocumentFile): Boolean {
        if (!file.isFile || file.length() > POINTER_MAX_BYTES) return false
        val text = runCatching {
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return false
        return text.contains("git-lfs", ignoreCase = true) ||
            text.contains("version https://git-lfs.github.com/spec", ignoreCase = true) ||
            text.contains("xet", ignoreCase = true)
    }

    private fun List<DetectedTtsPack>.dedupeDetectedPacks(): List<DetectedTtsPack> =
        groupBy { it.packId }
            .values
            .map { group ->
                group.maxWith(
                    compareBy<DetectedTtsPack> { packStatusRank(it.status) }
                        .thenBy { sourceTypeRank(it.sourceType) },
                )
            }

    private fun packStatusRank(status: TtsPackStatus): Int = when (status) {
        TtsPackStatus.READY -> 70
        TtsPackStatus.READY_NON_RUSSIAN -> 60
        TtsPackStatus.UNSUPPORTED_RUNTIME -> 50
        TtsPackStatus.DISABLED_RUNTIME -> 40
        TtsPackStatus.INCOMPLETE -> 30
        TtsPackStatus.BROKEN_POINTER -> 20
        TtsPackStatus.INVALID_FILESET -> 10
    }

    private fun sourceTypeRank(sourceType: TtsPackSourceType): Int = when (sourceType) {
        TtsPackSourceType.DOWNLOADED -> 50
        TtsPackSourceType.IMPORTED -> 40
        TtsPackSourceType.EXTERNAL_LINKED -> 30
        TtsPackSourceType.LEGACY_INTERNAL -> 20
        TtsPackSourceType.LEGACY_EXTERNAL -> 10
    }

    private fun engineSelectionComparator(): Comparator<DetectedTtsPack> =
        compareBy<DetectedTtsPack> { engineSelectionRank(it) }
            .thenByDescending { sourceTypeRank(it.sourceType) }
            .thenBy { it.displayName.lowercase(Locale.getDefault()) }
            .thenBy { it.packId }

    private fun engineSelectionRank(pack: DetectedTtsPack): Int {
        if (!pack.isRunnable) return 1_000
        if (!pack.isRussianCapable) return 900
        return when (pack.engineFamily) {
            TtsPackEngineFamily.CHATTERBOX -> when (pack.precision?.lowercase(Locale.getDefault())) {
                "int4" -> 0
                "fp16" -> 20
                "fp32" -> 40
                else -> 60
            }
            else -> 0
        }
    }

    private fun invalidateDetectedPacksCache() {
        detectedPacksCache = null
        detectedPacksCacheAtMs = 0L
    }

    private fun scanImportedPacks(out: MutableList<DetectedTtsPack>) {
        if (!importedRoot.exists()) return
        val families = importedRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (familyDir in families) {
            when (familyDir.name) {
                "piper" -> familyDir.listFiles()?.filter { it.isStableImportDir() }?.forEach { out += inspectPiperPack(it, TtsPackSourceType.IMPORTED) }
                "natasha" -> familyDir.listFiles()?.filter { it.isStableImportDir() }?.forEach { out += inspectNatashaPack(it, TtsPackSourceType.IMPORTED) }
                "utrobin" -> familyDir.listFiles()?.filter { it.isStableImportDir() }?.forEach { out += inspectUtrobinPack(it, TtsPackSourceType.IMPORTED) }
                "onnx" -> {
                    familyDir.walkTopDown()
                        .filter { it.isFile && it.name == MODEL_MANIFEST && !it.path.contains(".tmp-") }
                        .forEach { manifest -> inspectOnnxPack(manifest, TtsPackSourceType.IMPORTED)?.let(out::add) }
                    familyDir.listFiles()
                        ?.filter { it.isStableImportDir() }
                        ?.forEach { root ->
                            if (root.walkTopDown().none { it.isFile && it.name == MODEL_MANIFEST }) {
                                inspectOnnxRuntimeRoot(root, TtsPackSourceType.IMPORTED)?.let(out::add)
                            }
                        }
                }
            }
        }
    }

    private fun File.isStableImportDir(): Boolean = isDirectory && !name.contains(".tmp-")

    private fun scanExternalLinkedPacks(out: MutableList<DetectedTtsPack>) {
        if (!hasDirectFilesystemAccess()) return
        val roots = discoverFilesystemTtsRoots().filter { it.isDirectory && it.canRead() }
        if (roots.isEmpty()) return
        val candidates = roots
            .flatMap(::collectFileImportCandidates)
            .distinctBy { runCatching { it.sourceDir.canonicalPath }.getOrDefault(it.sourceDir.absolutePath) }
        candidates.forEach { candidate ->
            val pack = inspectFileCandidate(candidate, TtsPackSourceType.EXTERNAL_LINKED) ?: return@forEach
            out += pack.copy(canDelete = canDeleteExternalPackRoot(File(pack.rootDir), roots))
        }
        Timber.i("TTS external-linked scan roots=%d candidates=%d", roots.size, candidates.size)
    }

    private suspend fun downloadNatashaPack(): Boolean {
        val destDir = File(importedRoot, "natasha/natasha_vits2_remote")
        destDir.mkdirs()
        val outFile = File(destDir, "model.onnx")
        _downloadState.value = TtsPackDownloadState(
            packId = DOWNLOAD_ID_NATASHA,
            label = "Natasha VITS2",
            progress = 0f,
            message = "Начинаю загрузку",
        )
        return try {
            downloadFileWithProgress(
                url = NATASHA_MODEL_URL,
                outFile = outFile,
                packId = DOWNLOAD_ID_NATASHA,
                label = "Natasha VITS2",
            )
            _downloadState.value = TtsPackDownloadState(
                packId = DOWNLOAD_ID_NATASHA,
                label = "Natasha VITS2",
                progress = null,
                message = "Скачиваю metadata",
            )
            downloadSmallFile(NATASHA_CONFIG_URL, File(destDir, "config.json"))
            downloadSmallFile(NATASHA_SYMBOLS_URL, File(destDir, "symbols.py"))
            if (outFile.length() < MIN_NATASHA_MODEL_BYTES) {
                error("Загруженная Natasha слишком маленькая: ${outFile.length()} bytes")
            }
            _downloadState.value = null
            true
        } catch (error: CancellationException) {
            outFile.delete()
            _downloadState.value = null
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "Failed to download Natasha pack")
            _downloadState.value = TtsPackDownloadState(
                packId = DOWNLOAD_ID_NATASHA,
                label = "Natasha VITS2",
                progress = null,
                message = error.message ?: "Ошибка загрузки Natasha",
                isError = true,
            )
            false
        }
    }

    private suspend fun downloadPiperPack(
        downloadId: String,
        label: String,
        url: String,
        destSlug: String,
    ): Boolean {
        val familyRoot = File(importedRoot, "piper")
        familyRoot.mkdirs()
        val archiveFile = File(familyRoot, "$destSlug.tar.bz2")
        val destDir = File(familyRoot, destSlug)
        _downloadState.value = TtsPackDownloadState(
            packId = downloadId,
            label = label,
            progress = 0f,
            message = "Начинаю загрузку",
        )
        Timber.i("Starting Piper download id=%s slug=%s url=%s", downloadId, destSlug, url)
        return try {
            if (destDir.exists()) destDir.deleteRecursively()
            archiveFile.delete()
            downloadFileWithProgress(
                url = url,
                outFile = archiveFile,
                packId = downloadId,
                label = label,
            )
            _downloadState.value = TtsPackDownloadState(
                packId = downloadId,
                label = label,
                progress = null,
                message = "Распаковка Piper-пакета",
            )
            extractTarBz2(archiveFile, familyRoot)
            Timber.d(
                "Finished Piper extraction slug=%s directChildren=%s",
                destSlug,
                familyRoot.listFiles()?.joinToString { it.name } ?: "<empty>",
            )
            val extracted = familyRoot.listFiles()
                ?.firstOrNull { it.isDirectory && it.name == destSlug }
                ?: error("Не удалось найти распакованный Piper-пакет: $destSlug")
            val pack = inspectPiperPack(extracted, TtsPackSourceType.IMPORTED)
            if (!pack.isRunnable) {
                error(pack.reason ?: "Piper-пакет распакован, но не распознан как рабочий")
            }
            Timber.i(
                "Piper-пакет готов packId=%s voices=%s root=%s",
                pack.packId,
                pack.voices.joinToString { it.label },
                pack.rootDir,
            )
            archiveFile.delete()
            _downloadState.value = null
            true
        } catch (error: CancellationException) {
            archiveFile.delete()
            if (destDir.exists()) destDir.deleteRecursively()
            _downloadState.value = null
            throw error
        } catch (error: Throwable) {
            Timber.e(error, "Не удалось скачать Piper-пакет $destSlug")
            archiveFile.delete()
            if (destDir.exists()) destDir.deleteRecursively()
            _downloadState.value = TtsPackDownloadState(
                packId = downloadId,
                label = label,
                progress = null,
                message = error.message ?: "Ошибка загрузки Piper-пакета",
                isError = true,
            )
            false
        }
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        outFile: File,
        packId: String,
        label: String,
    ) {
        val request = Request.Builder().url(url).build()
        val call = okHttpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Пустой ответ сервера")
                val total = body.contentLength().takeIf { it > 0L }
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        var read = input.read(buffer)
                        while (read > 0) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                            downloaded += read
                            val progress = total?.let {
                                (downloaded.toFloat() / it.toFloat()).coerceIn(0f, 1f)
                            }
                            _downloadState.value = TtsPackDownloadState(
                                packId = packId,
                                label = label,
                                progress = progress,
                            )
                            read = input.read(buffer)
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            call.cancel()
            outFile.delete()
            throw error
        }
    }

    private suspend fun downloadSmallFile(url: String, outFile: File) {
        val request = Request.Builder().url(url).build()
        val call = okHttpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Пустой ответ сервера")
                outFile.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read > 0) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            call.cancel()
            outFile.delete()
            throw error
        }
    }

    private suspend fun extractTarBz2(archiveFile: File, destDir: File) {
        try {
            extractTarBz2WithSystemTar(archiveFile, destDir)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Timber.w(error, "System tar extraction failed for %s, falling back to Java extractor", archiveFile.absolutePath)
            archiveFile.inputStream().use { fileInput ->
                BZip2CompressorInputStream(fileInput).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        extractTarEntries(tarIn, destDir)
                    }
                }
            }
        }
    }

    private suspend fun extractTarBz2WithSystemTar(archiveFile: File, destDir: File) {
        val process = ProcessBuilder(
            "/system/bin/tar",
            "-xjf",
            archiveFile.absolutePath,
            "-C",
            destDir.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
        val exitCode = waitForProcess(process)
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        Timber.d("System tar exit=%d output=%s", exitCode, output.ifBlank { "<empty>" })
        if (exitCode != 0) {
            error("System tar extraction failed with code $exitCode: ${output.ifBlank { "no output" }}")
        }
    }

    private suspend fun waitForProcess(process: Process): Int {
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
            }
            return process.exitValue()
        } catch (error: CancellationException) {
            process.destroyForcibly()
            throw error
        }
    }

    private suspend fun extractTarEntries(input: TarArchiveInputStream, destDir: File) {
        val canonicalRoot = destDir.canonicalFile
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entry = input.nextEntry
        while (entry != null) {
            currentCoroutineContext().ensureActive()
            if (shouldLogPiperEntry(entry.name)) {
                Timber.d("Piper tar entry name=%s size=%d dir=%s", entry.name, entry.size, entry.isDirectory)
            }
            val output = File(destDir, entry.name).canonicalFile
            if (!output.path.startsWith(canonicalRoot.path + File.separator) && output != canonicalRoot) {
                error("Небезопасный путь в архиве: ${entry.name}")
            }
            if (entry.isDirectory) {
                output.mkdirs()
                continue
            }
            if (entry.isSymbolicLink || entry.isLink) {
                error("Piper archive содержит неподдерживаемую ссылку: ${entry.name}")
            }
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { out ->
                var remaining = entry.size
                while (remaining > 0L) {
                    currentCoroutineContext().ensureActive()
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read < 0) {
                        error("Архив Piper оборвался на ${entry.name}")
                    }
                    out.write(buffer, 0, read)
                    remaining -= read.toLong()
                }
            }
            if (shouldLogPiperEntry(entry.name)) {
                Timber.d("Piper tar extracted name=%s bytes=%d", entry.name, output.length())
            }
            entry = input.nextEntry
        }
    }

    private fun shouldLogPiperEntry(entryName: String): Boolean {
        return entryName.endsWith(".onnx") ||
            entryName.endsWith(".onnx.json") ||
            entryName.endsWith("tokens.txt") ||
            entryName.endsWith("MODEL_CARD") ||
            entryName.endsWith("espeak-ng-data/")
    }

    private fun piperVoiceLabel(voiceId: String): String {
        if (voiceId.equals("burunov", ignoreCase = true)) return "Бурунов (м)"
        return PIPER_REMOTE_PACKS.firstOrNull { it.voiceId.equals(voiceId, ignoreCase = true) }
            ?.voiceLabel
            ?: voiceId.replaceFirstChar { it.uppercase() }
    }

    private fun scanLegacyOnnxPacks(out: MutableList<DetectedTtsPack>) {
        listOfNotNull(legacyOnnxInternalRoot.takeIf { it.exists() }, legacyOnnxExternalRoot?.takeIf { it.exists() })
            .forEach { root ->
                val source = if (root == legacyOnnxInternalRoot) TtsPackSourceType.LEGACY_INTERNAL else TtsPackSourceType.LEGACY_EXTERNAL
                root.walkTopDown()
                    .filter { it.isFile && it.name == MODEL_MANIFEST }
                    .forEach { manifest -> inspectOnnxPack(manifest, source)?.let(out::add) }
            }
    }

    private fun scanLegacyRuntimePacks(out: MutableList<DetectedTtsPack>) {
        legacyNatashaExtractedRoot
            .takeIf { it.isDirectory }
            ?.let { out += inspectNatashaPack(it, TtsPackSourceType.LEGACY_INTERNAL) }

        legacyUtrobinExtractedRoot
            .takeIf { it.isDirectory }
            ?.let { out += inspectUtrobinPack(it, TtsPackSourceType.LEGACY_INTERNAL) }

        legacyPiperExtractedRoot
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { out += inspectPiperPack(it, TtsPackSourceType.LEGACY_INTERNAL) }
    }

    private fun inspectNatashaPack(root: File, sourceType: TtsPackSourceType): DetectedTtsPack {
        val model = NATASHA_MODEL_CANDIDATES.map { File(root, it) }.firstOrNull { it.exists() }
        val modelSize = model?.length() ?: 0L
        val status = when {
            model == null -> TtsPackStatus.INCOMPLETE
            isLikelyPointerFile(model) -> TtsPackStatus.BROKEN_POINTER
            modelSize < MIN_NATASHA_MODEL_BYTES -> TtsPackStatus.INVALID_FILESET
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> "Нет model.onnx/natasha.onnx"
            TtsPackStatus.BROKEN_POINTER -> "Файл модели похож на Git LFS/Xet pointer"
            TtsPackStatus.INVALID_FILESET -> "Модель Natasha слишком маленькая"
            else -> null
        }
        return DetectedTtsPack(
            packId = "natasha:${root.name}",
            engineFamily = TtsPackEngineFamily.NATASHA,
            displayName = "Natasha VITS2",
            rootDir = root.absolutePath,
            sourceType = sourceType,
            status = status,
            reason = reason,
            isRussianCapable = true,
            voices = listOf(DetectedTtsVoice("natasha_default", "Natasha RU")),
            canDelete = canDeletePackRoot(root, sourceType),
        )
    }

    private fun inspectUtrobinPack(root: File, sourceType: TtsPackSourceType): DetectedTtsPack {
        val model = File(root, "model.onnx")
        val vocabFile = findUtrobinVocabularyFile(root)
        val status = when {
            !model.exists() -> TtsPackStatus.INCOMPLETE
            isLikelyPointerFile(model) -> TtsPackStatus.BROKEN_POINTER
            model.length() < MIN_UTROBIN_MODEL_BYTES -> TtsPackStatus.INVALID_FILESET
            vocabFile == null -> TtsPackStatus.INCOMPLETE
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> when {
                !model.exists() -> "Нет model.onnx"
                vocabFile == null -> "Нет tokens.txt/vocab.json/tokenizer.json"
                else -> "Пак Utrobin неполный"
            }
            TtsPackStatus.BROKEN_POINTER -> "Файл модели похож на Git LFS/Xet pointer"
            TtsPackStatus.INVALID_FILESET -> "Модель Utrobin слишком маленькая"
            else -> null
        }
        return DetectedTtsPack(
            packId = "utrobin:${root.name}",
            engineFamily = TtsPackEngineFamily.UTROBIN,
            displayName = "Utrobin VITS",
            rootDir = root.absolutePath,
            sourceType = sourceType,
            status = status,
            reason = reason,
            isRussianCapable = true,
            voices = listOf(
                DetectedTtsVoice("0", "Женский"),
                DetectedTtsVoice("1", "Мужской"),
            ),
            canDelete = canDeletePackRoot(root, sourceType),
        )
    }

    private fun inspectPiperPack(root: File, sourceType: TtsPackSourceType): DetectedTtsPack {
        val onnxFiles = root.listFiles()?.filter { it.isFile && it.extension.equals("onnx", true) }.orEmpty()
        val voiceIds = onnxFiles.mapNotNull { onnx ->
            PIPER_VOICE_REGEX.find(onnx.name)?.groupValues?.getOrNull(1)
        }
        val hasTokens = File(root, "tokens.txt").exists()
        val hasEspeak = File(root, "espeak-ng-data").isDirectory
        val baseStatus = when {
            onnxFiles.isEmpty() -> TtsPackStatus.INCOMPLETE
            onnxFiles.any(::isLikelyPointerFile) -> TtsPackStatus.BROKEN_POINTER
            !hasTokens || !hasEspeak -> TtsPackStatus.INCOMPLETE
            else -> TtsPackStatus.READY
        }
        val reason = when (baseStatus) {
            TtsPackStatus.INCOMPLETE -> when {
                onnxFiles.isEmpty() -> "Нет ONNX голоса Piper"
                !hasTokens -> "Нет tokens.txt для Sherpa/Piper"
                !hasEspeak -> "Нет espeak-ng-data для Sherpa/Piper"
                else -> "Пак Piper неполный"
            }
            TtsPackStatus.BROKEN_POINTER -> "Файл голоса похож на Git LFS/Xet pointer"
            else -> null
        }
        val voices = voiceIds.distinct().ifEmpty { listOf(root.name) }.map { id ->
            DetectedTtsVoice(id = id, label = piperVoiceLabel(id))
        }
        val displayName = voices.singleOrNull()?.let { "Piper ${it.label}" } ?: "Piper / Sherpa"
        return DetectedTtsPack(
            packId = "piper:${root.name}",
            engineFamily = TtsPackEngineFamily.PIPER,
            displayName = displayName,
            rootDir = root.absolutePath,
            sourceType = sourceType,
            status = baseStatus,
            reason = reason,
            isRussianCapable = true,
            voices = voices,
            suggestedDeletion = false,
            canDelete = canDeletePackRoot(root, sourceType),
        )
    }

    private fun inspectOnnxPack(manifestFile: File, sourceType: TtsPackSourceType): DetectedTtsPack? {
        return runCatching {
            val json = JSONObject(manifestFile.readText())
            val modelId = json.optString("modelId").ifBlank { manifestFile.parentFile?.name.orEmpty() }
            val precision = json.optString("precision", "fp32")
            val runtimeFamily = json.optString("runtimeFamily").ifBlank { null }
            val root = manifestFile.parentFile ?: return@runCatching null
            val hasRussian = json.optJSONArray("languages")?.let { arr ->
                (0 until arr.length()).mapNotNull(arr::optString).any {
                    it.equals("ru", true) || it.contains("russian", true)
                }
            } ?: modelId.contains("moss", true) || modelId.contains("ru", true)
            when (runtimeFamily) {
                "kokoro_v1" -> inspectKokoroPack(
                    root = root,
                    sourceType = sourceType,
                    modelId = modelId,
                    precision = precision,
                    hasRussian = hasRussian,
                    estimatedSizeMb = json.optInt("estimatedSizeMb", -1).takeIf { it > 0 },
                )
                "chatterbox_v1", "chatterbox_turbo_v1" -> inspectChatterboxPack(
                    root = root,
                    sourceType = sourceType,
                    modelId = modelId,
                    precision = precision,
                    runtimeFamily = runtimeFamily,
                    estimatedSizeMb = json.optInt("estimatedSizeMb", -1).takeIf { it > 0 },
                    hasRussian = hasRussian,
                )
                else -> DetectedTtsPack(
                    packId = "onnx:$modelId:$precision",
                    engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
                    displayName = modelId,
                    rootDir = root.absolutePath,
                    sourceType = sourceType,
                    status = TtsPackStatus.UNSUPPORTED_RUNTIME,
                    reason = "Runtime family '${runtimeFamily ?: "unknown"}' не поддержан на Android",
                    isRussianCapable = hasRussian,
                    runtimeFamily = runtimeFamily,
                    suggestedDeletion = true,
                    canDelete = canDeletePackRoot(root, sourceType),
                    modelId = modelId,
                    precision = precision,
                    estimatedSizeMb = json.optInt("estimatedSizeMb", -1).takeIf { it > 0 },
                )
            }
        }.onFailure {
            Timber.w(it, "Failed to inspect ONNX manifest ${manifestFile.absolutePath}")
        }.getOrNull()
    }

    private fun inspectOnnxRuntimeRoot(root: File, sourceType: TtsPackSourceType): DetectedTtsPack? {
        detectChatterboxRuntime(root)?.let { runtime ->
            return inspectChatterboxPack(
                root = root,
                sourceType = sourceType,
                modelId = runtime.modelId,
                precision = runtime.precision,
                runtimeFamily = runtime.runtimeFamily,
                estimatedSizeMb = null,
                hasRussian = runtime.runtimeFamily == "chatterbox_v1",
            )
        }
        return if (hasUsableKokoroRuntimeFiles(root)) {
            inspectKokoroPack(
                root = root,
                sourceType = sourceType,
                modelId = "kokoro_82m",
                precision = inferKokoroPrecision(root),
                hasRussian = false,
                estimatedSizeMb = null,
            )
        } else {
            null
        }
    }

    private fun inspectKokoroPack(
        root: File,
        sourceType: TtsPackSourceType,
        modelId: String,
        precision: String,
        hasRussian: Boolean,
        estimatedSizeMb: Int?,
    ): DetectedTtsPack {
        val status = when {
            hasUsableKokoroRuntimeFiles(root) ->
                if (hasRussian) TtsPackStatus.READY else TtsPackStatus.READY_NON_RUSSIAN
            else -> TtsPackStatus.INCOMPLETE
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> "Для Kokoro не хватает model/config/voices"
            TtsPackStatus.READY_NON_RUSSIAN -> "Пак рабочий, но не рассчитан на русский TTS"
            else -> null
        }
        return DetectedTtsPack(
            packId = "onnx:$modelId:$precision",
            engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
            displayName = modelId,
            rootDir = root.absolutePath,
            sourceType = sourceType,
            status = status,
            reason = reason,
            isRussianCapable = hasRussian,
            runtimeFamily = "kokoro_v1",
            voices = listKokoroVoices(root),
            suggestedDeletion = status == TtsPackStatus.READY_NON_RUSSIAN,
            canDelete = canDeletePackRoot(root, sourceType),
            modelId = modelId,
            precision = precision,
            estimatedSizeMb = estimatedSizeMb,
        )
    }

    private fun inspectChatterboxPack(
        root: File,
        sourceType: TtsPackSourceType,
        modelId: String,
        precision: String,
        runtimeFamily: String,
        estimatedSizeMb: Int?,
        hasRussian: Boolean,
    ): DetectedTtsPack {
        val runtime = detectChatterboxRuntime(root)
        val tokenizerFile = File(root, "tokenizer.json").takeIf { it.isFile && !isLikelyPointerFile(it) }
        val defaultVoiceFile = resolveChatterboxVoiceFile(root)
        val brokenPointer = runtime?.pointerLike == true ||
            listOfNotNull(tokenizerFile, defaultVoiceFile).any(::isLikelyPointerFile)
        val status = when {
            brokenPointer -> TtsPackStatus.BROKEN_POINTER
            runtimeFamily == "chatterbox_turbo_v1" -> TtsPackStatus.UNSUPPORTED_RUNTIME
            runtime == null || tokenizerFile == null || defaultVoiceFile == null -> TtsPackStatus.INCOMPLETE
            else -> if (hasRussian) TtsPackStatus.READY else TtsPackStatus.READY_NON_RUSSIAN
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> when {
                tokenizerFile == null -> "Нет tokenizer.json для Chatterbox"
                defaultVoiceFile == null -> "Нет default_voice.wav или другого reference .wav"
                runtime == null -> "Нет нужных ONNX-графов Chatterbox (speech_encoder/embed_tokens/language_model/conditional_decoder)"
                else -> "Пак Chatterbox неполный"
            }
            TtsPackStatus.BROKEN_POINTER -> "Один из файлов Chatterbox похож на Git LFS/Xet pointer"
            TtsPackStatus.UNSUPPORTED_RUNTIME -> "Chatterbox Turbo пока не поддержан; встроен только multilingual runtime"
            TtsPackStatus.READY_NON_RUSSIAN -> "Пак рабочий, но не рассчитан на русский TTS"
            else -> null
        }
        val voices = listChatterboxVoices(root)
        return DetectedTtsPack(
            packId = "chatterbox:$modelId:$precision",
            engineFamily = TtsPackEngineFamily.CHATTERBOX,
            displayName = if (runtimeFamily == "chatterbox_v1") "Chatterbox Multilingual" else "Chatterbox Turbo",
            rootDir = root.absolutePath,
            sourceType = sourceType,
            status = status,
            reason = reason,
            isRussianCapable = hasRussian,
            runtimeFamily = runtimeFamily,
            voices = voices,
            suggestedDeletion = status == TtsPackStatus.UNSUPPORTED_RUNTIME || status == TtsPackStatus.READY_NON_RUSSIAN,
            canDelete = canDeletePackRoot(root, sourceType),
            modelId = modelId,
            precision = precision,
            estimatedSizeMb = estimatedSizeMb,
        )
    }

    private fun detectChatterboxRuntime(root: File): ChatterboxRuntimeProbe? {
        val onnxDir = File(root, "onnx").takeIf { it.isDirectory } ?: root
        val speechEncoder = pickChatterboxGraph(onnxDir, "speech_encoder")
        val embedTokens = pickChatterboxGraph(onnxDir, "embed_tokens")
        val conditionalDecoder = pickChatterboxGraph(onnxDir, "conditional_decoder")
        val languageModel = pickChatterboxGraph(onnxDir, "language_model")
        if (speechEncoder == null || embedTokens == null || conditionalDecoder == null || languageModel == null) {
            return null
        }
        val precision = inferChatterboxPrecision(languageModel)
        val nameForModel = "${root.parentFile?.name.orEmpty()}_${root.name}"
        val modelId = nameForModel
            .lowercase(Locale.getDefault())
            .let { name ->
                when {
                    name.contains("turbo") -> "chatterbox_turbo"
                    else -> "chatterbox_multilingual"
                }
            }
        val runtimeFamily = if (modelId == "chatterbox_turbo") "chatterbox_turbo_v1" else "chatterbox_v1"
        val pointerLike = listOfNotNull(speechEncoder, embedTokens, conditionalDecoder, languageModel)
            .any(::isLikelyPointerFile)
        return ChatterboxRuntimeProbe(
            modelId = modelId,
            precision = precision,
            runtimeFamily = runtimeFamily,
            pointerLike = pointerLike,
        )
    }

    private fun pickChatterboxGraph(onnxDir: File, baseName: String): File? {
        val candidates = chatterboxGraphBaseNames(baseName).flatMap { graphBase ->
            listOf(
                "${graphBase}_q4.onnx",
                "${graphBase}_q4f16.onnx",
                "${graphBase}_int4.onnx",
                "${graphBase}_quantized.onnx",
                "${graphBase}_uint8f16.onnx",
                "${graphBase}_fp16.onnx",
                "${graphBase}_q8f16.onnx",
                "$graphBase.onnx",
            )
        }
        return candidates
            .map { File(onnxDir, it) }
            .firstOrNull { it.isFile }
    }

    private fun chatterboxGraphBaseNames(baseName: String): List<String> {
        return when (baseName) {
            "speech_encoder" -> listOf("speech_encoder", "multi_lang_speech_encoder")
            "embed_tokens" -> listOf("embed_tokens", "multi_lang_embed_tokens")
            "conditional_decoder" -> listOf("conditional_decoder", "multi_lang_conditional_decoder")
            else -> listOf(baseName)
        }
    }

    private fun inferChatterboxPrecision(modelFile: File?): String {
        val name = modelFile?.name?.lowercase(Locale.getDefault()).orEmpty()
        return when {
            name.contains("q4") || name.contains("int4") || name.contains("quantized") || name.contains("uint8") -> "int4"
            name.contains("fp16") || name.contains("q8f16") -> "fp16"
            else -> "fp32"
        }
    }

    private fun hasUsableKokoroRuntimeFiles(root: File): Boolean {
        val hasConfig = File(root, "config.json").exists()
        val voicesDir = File(root, "voices")
        val hasVoices = voicesDir.hasUsableKokoroVoiceBins()
        val onnxCandidates = listOf(
            File(root, "onnx/model.onnx"),
            File(root, "onnx/model_fp16.onnx"),
            File(root, "onnx/model_quantized.onnx"),
            File(root, "onnx/model_q8f16.onnx"),
            File(root, "onnx/model_uint8f16.onnx"),
            File(root, "onnx/model_q4f16.onnx"),
            File(root, "model.onnx"),
            File(root, "model_fp16.onnx"),
            File(root, "model_quantized.onnx"),
            File(root, "model_q8f16.onnx"),
            File(root, "model_uint8f16.onnx"),
            File(root, "model_q4f16.onnx"),
        )
        return hasConfig && hasVoices && onnxCandidates.any { it.exists() && !isLikelyPointerFile(it) }
    }

    private fun inferKokoroPrecision(root: File): String {
        val onnxDir = File(root, "onnx").takeIf { it.isDirectory } ?: root
        return when {
            File(onnxDir, "model_quantized.onnx").isFile ||
                File(onnxDir, "model_q4f16.onnx").isFile ||
                File(onnxDir, "model_uint8f16.onnx").isFile -> "int4"
            File(onnxDir, "model_fp16.onnx").isFile ||
                File(onnxDir, "model_q8f16.onnx").isFile -> "fp16"
            else -> "fp32"
        }
    }

    private fun listKokoroVoices(root: File): List<DetectedTtsVoice> {
        val voicesDir = File(root, "voices")
        return voicesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("bin", true) && it.length() >= MIN_KOKORO_VOICE_BIN_BYTES }
            ?.sortedBy { it.name }
            ?.map { DetectedTtsVoice(it.nameWithoutExtension, it.nameWithoutExtension.replace('_', ' '), language = "en", isRussian = false, sourcePath = it.absolutePath) }
            .orEmpty()
    }

    private fun resolveChatterboxVoiceFile(root: File): File? {
        return collectChatterboxVoiceFiles(root)
            .firstOrNull { it.name.equals("default_voice.wav", ignoreCase = true) }
            ?: collectChatterboxVoiceFiles(root).firstOrNull()
    }

    private fun listChatterboxVoices(root: File): List<DetectedTtsVoice> {
        return collectChatterboxVoiceFiles(root).map { wav ->
            val id = wav.nameWithoutExtension
            DetectedTtsVoice(
                id = id,
                label = id.replace('_', ' '),
                language = "ru",
                isRussian = true,
                sourcePath = wav.absolutePath,
            )
        }
    }

    private fun collectChatterboxVoiceFiles(root: File): List<File> {
        val files = linkedMapOf<String, File>()
        fun addFrom(dir: File?) {
            dir?.listFiles()
                ?.filter { it.isFile && it.extension.equals("wav", true) && !isLikelyPointerFile(it) }
                ?.sortedBy { it.name }
                ?.forEach { wav ->
                    val key = wav.name.lowercase(Locale.getDefault())
                    files.putIfAbsent(key, wav)
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

    private fun collectImportCandidates(root: DocumentFile): List<ImportCandidate> {
        val queue = ArrayDeque<DocumentFile>()
        val out = mutableListOf<ImportCandidate>()
        val seen = mutableSetOf<String>()
        queue.add(root)
        var steps = 0
        while (queue.isNotEmpty()) {
            if (steps++ > 15_000) break
            val dir = queue.removeFirst()
            if (!dir.isDirectory || !dir.canRead()) continue
            val key = dir.uri.toString()
            if (!seen.add(key)) continue
            val name = dir.name.orEmpty()
            val children = dir.listFiles().orEmpty()
            val childNamesLower = children.mapNotNull { it.name?.lowercase(Locale.getDefault()) }.toSet()

            val manifest = children.firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
            if (manifest != null) {
                val meta = parseManifestDocument(manifest)
                if (meta != null) {
                    out += ImportCandidate(
                        sourceDir = dir,
                        engineDirName = "onnx",
                        destSlug = "${meta.modelId}_${meta.precision}",
                        isKokoroManifestRoot = meta.runtimeFamily == "kokoro_v1",
                    )
                    continue
                }
            }

            val lowerName = name.lowercase(Locale.getDefault())
            if (isNatashaImportCandidate(lowerName, childNamesLower)) {
                out += ImportCandidate(dir, "natasha", sanitizeSlug(name.ifBlank { "natasha_vits2" }))
                continue
            }
            if (isUtrobinImportCandidate(childNamesLower)) {
                out += ImportCandidate(dir, "utrobin", sanitizeSlug(name.ifBlank { "utrobin_vits" }))
                continue
            }
            if (children.any { child -> child.isFile && PIPER_VOICE_REGEX.containsMatchIn(child.name.orEmpty()) }) {
                out += ImportCandidate(dir, "piper", sanitizeSlug(name.ifBlank { "piper_pack" }))
                continue
            }
            if (isChatterboxImportCandidate(childNamesLower)) {
                out += ImportCandidate(dir, "onnx", sanitizeSlug(name.ifBlank { "chatterbox_pack" }))
                continue
            }

            if ("model.onnx" in childNamesLower || "natasha.onnx" in childNamesLower || "model_manifest.json" in childNamesLower) {
                Timber.d(
                    "Skipped potential TTS dir name=%s uri=%s children=%s",
                    name,
                    dir.uri,
                    childNamesLower.sorted().joinToString(),
                )
            }

            children.filter { it.isDirectory }.forEach(queue::add)
        }
        if (out.isEmpty()) {
            Timber.w(
                "TTS import scan found no supported candidates under %s. Root children=%s",
                root.uri,
                root.listFiles().mapNotNull { it.name }.sorted().joinToString(),
            )
        }
        return out.distinctBy { "${it.engineDirName}:${it.destSlug}" }
    }

    private fun collectFileImportCandidates(root: File): List<FileImportCandidate> {
        val queue = ArrayDeque<File>()
        val out = mutableListOf<FileImportCandidate>()
        val seen = mutableSetOf<String>()
        queue.add(root)
        var steps = 0
        while (queue.isNotEmpty()) {
            if (steps++ > 15_000) break
            val dir = queue.removeFirst()
            if (!dir.isDirectory || !dir.canRead()) continue
            val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!seen.add(key)) continue
            val candidate = detectFileImportCandidate(dir)
            if (candidate != null) {
                out += candidate
                continue
            }
            dir.listFiles().orEmpty().filter { it.isDirectory }.forEach(queue::add)
        }
        return out
    }

    private fun detectFileImportCandidate(dir: File): FileImportCandidate? {
        if (!dir.isDirectory || !dir.canRead()) return null
        val children = dir.listFiles().orEmpty()
        val childNamesLower = children.map { it.name.lowercase(Locale.getDefault()) }.toSet()
        val manifest = children.firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
        if (manifest != null) {
            val meta = parseManifestFile(manifest)
            if (meta != null) {
                return FileImportCandidate(
                    sourceDir = dir,
                    engineDirName = "onnx",
                    destSlug = "${meta.modelId}_${meta.precision}",
                    isKokoroManifestRoot = meta.runtimeFamily == "kokoro_v1",
                )
            }
        }
        val lowerName = dir.name.lowercase(Locale.getDefault())
        return when {
            isNatashaImportCandidate(lowerName, childNamesLower) ->
                FileImportCandidate(dir, "natasha", sanitizeSlug(dir.name.ifBlank { "natasha_vits2" }))
            isUtrobinImportCandidate(childNamesLower) ->
                FileImportCandidate(dir, "utrobin", sanitizeSlug(dir.name.ifBlank { "utrobin_vits" }))
            children.any { it.isFile && PIPER_VOICE_REGEX.containsMatchIn(it.name) } ->
                FileImportCandidate(dir, "piper", sanitizeSlug(dir.name.ifBlank { "piper_pack" }))
            isChatterboxImportCandidate(childNamesLower) ->
                FileImportCandidate(dir, "onnx", sanitizeSlug(dir.name.ifBlank { "chatterbox_pack" }))
            else -> null
        }
    }

    private fun parseManifestDocument(manifestFile: DocumentFile): ManifestMeta? {
        return runCatching {
            val text = context.contentResolver.openInputStream(manifestFile.uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@runCatching null
            val json = JSONObject(text)
            val modelId = json.optString("modelId")
            val precision = json.optString("precision", "fp32")
            val runtimeFamily = json.optString("runtimeFamily").ifBlank { null }
            if (modelId.isBlank()) return@runCatching null
            ManifestMeta(modelId = modelId, precision = precision, runtimeFamily = runtimeFamily)
        }.getOrNull()
    }

    private fun parseManifestFile(manifestFile: File): ManifestMeta? {
        return runCatching {
            val json = JSONObject(manifestFile.readText())
            val modelId = json.optString("modelId")
            val precision = json.optString("precision", "fp32")
            val runtimeFamily = json.optString("runtimeFamily").ifBlank { null }
            if (modelId.isBlank()) return@runCatching null
            ManifestMeta(modelId = modelId, precision = precision, runtimeFamily = runtimeFamily)
        }.getOrNull()
    }

    private fun isNatashaImportCandidate(lowerName: String, childNamesLower: Set<String>): Boolean {
        val hasModel = "model.onnx" in childNamesLower || "natasha.onnx" in childNamesLower
        if ("natasha.onnx" in childNamesLower) return true
        if (!hasModel) return false
        return lowerName.contains("natasha") ||
            lowerName.contains("vits2") ||
            "symbols.py" in childNamesLower ||
            (
                "config.json" in childNamesLower &&
                    "tokens.txt" !in childNamesLower &&
                    "vocab.json" !in childNamesLower &&
                    "tokenizer.json" !in childNamesLower &&
                    "voices" !in childNamesLower
                )
    }

    private fun isUtrobinImportCandidate(childNamesLower: Set<String>): Boolean {
        val hasModel = "model.onnx" in childNamesLower
        val hasVocabulary = "tokens.txt" in childNamesLower ||
            "vocab.json" in childNamesLower ||
            "tokenizer.json" in childNamesLower
        return hasModel && hasVocabulary
    }

    private fun isChatterboxImportCandidate(childNamesLower: Set<String>): Boolean {
        val hasTokenizer = "tokenizer.json" in childNamesLower
        val hasVoice = "default_voice.wav" in childNamesLower || childNamesLower.any { it.endsWith(".wav") }
        val hasOnnxDir = "onnx" in childNamesLower
        return hasTokenizer && hasVoice && hasOnnxDir
    }

    private fun findUtrobinVocabularyFile(root: File): File? {
        val candidates = listOf(
            File(root, "tokens.txt"),
            File(root, "vocab.json"),
            File(root, "tokenizer.json"),
        )
        return candidates.firstOrNull { it.exists() && !isLikelyPointerFile(it) }
    }

    private fun inspectFileCandidate(
        candidate: FileImportCandidate,
        sourceType: TtsPackSourceType,
    ): DetectedTtsPack? {
        val root = candidate.sourceDir
        return when (candidate.engineDirName) {
            "piper" -> inspectPiperPack(root, sourceType)
            "natasha" -> inspectNatashaPack(root, sourceType)
            "utrobin" -> inspectUtrobinPack(root, sourceType)
            "onnx" -> root.walkTopDown()
                .firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
                ?.let { inspectOnnxPack(it, sourceType) }
                ?: inspectOnnxRuntimeRoot(root, sourceType)
            else -> null
        }
    }

    private fun validateImportedPack(candidate: ImportCandidate, dest: File): DetectedTtsPack {
        return when (candidate.engineDirName) {
            "piper" -> inspectPiperPack(dest, TtsPackSourceType.IMPORTED)
            "natasha" -> inspectNatashaPack(dest, TtsPackSourceType.IMPORTED)
            "utrobin" -> inspectUtrobinPack(dest, TtsPackSourceType.IMPORTED)
            "onnx" -> dest.walkTopDown()
                .firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
                ?.let { inspectOnnxPack(it, TtsPackSourceType.IMPORTED) }
                ?: inspectOnnxRuntimeRoot(dest, TtsPackSourceType.IMPORTED)
                ?: DetectedTtsPack(
                    packId = "onnx:${dest.name}:unknown",
                    engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
                    displayName = dest.name,
                    rootDir = dest.absolutePath,
                    sourceType = TtsPackSourceType.IMPORTED,
                    status = TtsPackStatus.INCOMPLETE,
                    reason = "После импорта не найден model_manifest.json и не распознан runtime pack",
                )
            else -> DetectedTtsPack(
                packId = "${candidate.engineDirName}:${dest.name}",
                engineFamily = TtsPackEngineFamily.PIPER,
                displayName = dest.name,
                rootDir = dest.absolutePath,
                sourceType = TtsPackSourceType.IMPORTED,
                status = TtsPackStatus.INVALID_FILESET,
                reason = "Неизвестный тип TTS-пака",
            )
        }
    }

    private fun sanitizeSlug(name: String): String =
        name.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "pack" }

    private fun isLikelyPointerFile(file: File): Boolean {
        if (!file.isFile || file.length() > POINTER_MAX_BYTES) return false
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        return text.contains("git-lfs", ignoreCase = true) ||
            text.contains("version https://git-lfs.github.com/spec", ignoreCase = true) ||
            text.contains("xet", ignoreCase = true)
    }

    private fun canDeletePackRoot(root: File, sourceType: TtsPackSourceType): Boolean {
        if (isManagedPackRoot(root)) return true
        return sourceType == TtsPackSourceType.EXTERNAL_LINKED && false
    }

    private fun canDeleteExternalPackRoot(root: File, roots: List<File>): Boolean {
        val path = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
        return roots.any { ttsRoot ->
            val rootPath = runCatching { ttsRoot.canonicalPath }.getOrDefault(ttsRoot.absolutePath)
            path != rootPath && path.startsWith("$rootPath${File.separator}")
        }
    }

    private fun isManagedPackRoot(root: File): Boolean {
        val path = runCatching { root.canonicalPath }.getOrElse { return false }
        fun matches(base: File?): Boolean {
            val basePath = runCatching { base?.canonicalPath }.getOrNull() ?: return false
            return path == basePath || path.startsWith("$basePath${File.separator}")
        }
        return matches(importedRoot) ||
            matches(legacyPiperExtractedRoot) ||
            matches(legacyNatashaExtractedRoot) ||
            matches(legacyUtrobinExtractedRoot) ||
            matches(legacyOnnxInternalRoot) ||
            matches(legacyOnnxExternalRoot)
    }

    private fun discoverFilesystemTtsRoots(): List<File> {
        val seeds = buildList {
            Environment.getExternalStorageDirectory()?.let(::add)
            add(File("/storage/emulated/0"))
            add(File("/storage/self/primary"))
            context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile?.let(::add)
        }
            .filter { it.exists() && it.isDirectory }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
        Timber.d(
            "TTS filesystem discovery seeds=%s",
            seeds.joinToString { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) },
        )
        val results = LinkedHashMap<String, File>()

        fun remember(dir: File) {
            val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                results.putIfAbsent(key, dir)
            }
        }

        seeds.forEach { root ->
            COMMON_TTS_ROOTS.forEach { relative ->
                remember(File(root, relative))
            }
        }

        val queue = ArrayDeque<Pair<File, Int>>()
        val seen = mutableSetOf<String>()
        seeds.forEach { queue.add(it to 0) }
        var steps = 0
        while (queue.isNotEmpty() && steps++ < 6_000) {
            val (dir, depth) = queue.removeFirst()
            val key = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            if (!seen.add(key)) continue
            if (!dir.isDirectory || !dir.canRead()) continue
            if (dir.name.equals("tts", ignoreCase = true)) {
                remember(dir)
                continue
            }
            if (depth >= 4) continue
            dir.listFiles()
                ?.filter { child ->
                    child.isDirectory &&
                        child.canRead() &&
                        child.name !in SKIPPED_STORAGE_DIRS
                }
                ?.forEach { child -> queue.add(child to (depth + 1)) }
        }

        return results.values.toList().also { found ->
            Timber.i(
                "TTS filesystem roots discovered=%s",
                found.joinToString { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) },
            )
        }
    }

    private fun freshTempImportDir(engineDirName: String, destSlug: String): File {
        val familyRoot = File(importedRoot, engineDirName)
        familyRoot.mkdirs()
        return File(familyRoot, "$destSlug.tmp-${System.currentTimeMillis()}-${System.nanoTime()}")
            .also { if (it.exists()) it.deleteRecursively() }
    }

    private fun replaceImportedDir(tempDir: File, destDir: File) {
        destDir.parentFile?.mkdirs()
        if (destDir.exists() && !destDir.deleteRecursively()) {
            error("Не удалось заменить старый TTS pack: ${destDir.name}")
        }
        if (!tempDir.renameTo(destDir)) {
            copyFileDirToFiles(tempDir, destDir)
            tempDir.deleteRecursively()
        }
    }

    private fun copyDocumentDirToFiles(srcDir: DocumentFile, destDir: File) {
        destDir.mkdirs()
        val children = srcDir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                copyDocumentDirToFiles(child, File(destDir, name))
            } else {
                copyDocumentFileToFile(child, File(destDir, name))
            }
        }
    }

    private fun copyFileDirToFiles(srcDir: File, destDir: File) {
        destDir.mkdirs()
        srcDir.listFiles().orEmpty().forEach { child ->
            val out = File(destDir, child.name)
            if (child.isDirectory) {
                copyFileDirToFiles(child, out)
            } else if (child.isFile) {
                out.parentFile?.mkdirs()
                child.copyTo(out, overwrite = true)
            }
        }
    }

    private fun copyDocumentFileToFile(child: DocumentFile, outFile: File) {
        outFile.parentFile?.mkdirs()
        context.contentResolver.openInputStream(child.uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: error("Не удалось открыть файл ${child.name ?: child.uri}")
    }

    private fun mergeKokoroVoicesFromNearbyTree(destPack: File, packRootDoc: DocumentFile) {
        val voicesDest = File(destPack, "voices")
        if (voicesDest.hasUsableKokoroVoiceBins()) return
        val src = findVoicesDocumentNearPack(packRootDoc) ?: return
        voicesDest.mkdirs()
        copyDocumentDirToFiles(src, voicesDest)
    }

    private fun mergeKokoroVoicesFromSelectedTree(destPack: File, treeRoot: DocumentFile) {
        val voicesDest = File(destPack, "voices")
        if (voicesDest.hasUsableKokoroVoiceBins()) return
        val src = findVoicesDocumentWithUsableBinsInSubtree(treeRoot) ?: return
        voicesDest.mkdirs()
        copyDocumentDirToFiles(src, voicesDest)
    }

    private fun findVoicesDocumentWithUsableBinsInSubtree(searchRoot: DocumentFile): DocumentFile? {
        val queue = ArrayDeque<DocumentFile>()
        queue.add(searchRoot)
        var steps = 0
        while (queue.isNotEmpty()) {
            if (steps++ > 12_000) break
            val dir = queue.removeFirst()
            if (!dir.isDirectory || !dir.canRead()) continue
            if (dir.name?.equals("voices", ignoreCase = true) == true && dir.hasUsableBinChildren()) {
                return dir
            }
            dir.listFiles().filter { it.isDirectory }.forEach(queue::add)
        }
        return null
    }

    private fun findVoicesDocumentNearPack(packRootDoc: DocumentFile): DocumentFile? {
        var cursor: DocumentFile? = packRootDoc.parentFile
        repeat(6) {
            val dir = cursor ?: return null
            val voices = dir.listFiles().filter { df ->
                df.isDirectory && df.name?.equals("voices", ignoreCase = true) == true
            }
            for (v in voices) {
                if (v.hasUsableBinChildren()) return v
            }
            cursor = dir.parentFile
        }
        return null
    }

    private fun DocumentFile.hasUsableBinChildren(): Boolean =
        listFiles().any { child ->
            val n = child.name
            n != null && n.endsWith(".bin", ignoreCase = true) && child.length() >= MIN_KOKORO_VOICE_BIN_BYTES
        }

    private fun File.hasUsableKokoroVoiceBins(): Boolean =
        isDirectory && listFiles()?.any { f ->
            f.isFile && f.extension.equals("bin", true) && f.length() >= MIN_KOKORO_VOICE_BIN_BYTES
        } == true

    private data class ImportCandidate(
        val sourceDir: DocumentFile,
        val engineDirName: String,
        val destSlug: String,
        val isKokoroManifestRoot: Boolean = false,
    )

    private data class FileImportCandidate(
        val sourceDir: File,
        val engineDirName: String,
        val destSlug: String,
        val isKokoroManifestRoot: Boolean = false,
    )

    private data class ManifestMeta(
        val modelId: String,
        val precision: String,
        val runtimeFamily: String?,
    )

    private data class ChatterboxRuntimeProbe(
        val modelId: String,
        val precision: String,
        val runtimeFamily: String,
        val pointerLike: Boolean,
    )

    private data class PiperRemotePack(
        val downloadId: String,
        val voiceId: String,
        val voiceLabel: String,
        val packSlug: String,
        val archiveUrl: String,
        val estimatedSizeMb: Int = 70,
    ) {
        val packId: String = "piper:$packSlug"
    }

    companion object {
        private const val MODEL_MANIFEST = "model_manifest.json"
        private const val POINTER_MAX_BYTES = 16_384L
        private const val DETECTED_PACKS_CACHE_MS = 2_000L
        private const val MIN_KOKORO_VOICE_BIN_BYTES = 8_192L
        private const val MIN_NATASHA_MODEL_BYTES = 10_000_000L
        private const val MIN_UTROBIN_MODEL_BYTES = 10_000_000L
        private const val DOWNLOAD_ID_NATASHA = "download_natasha_vits2"
        private const val NATASHA_MODEL_URL =
            "https://huggingface.co/frappuccino/vits2_ru_natasha/resolve/main/natasha.onnx"
        private const val NATASHA_CONFIG_URL =
            "https://huggingface.co/frappuccino/vits2_ru_natasha/resolve/main/config.json"
        private const val NATASHA_SYMBOLS_URL =
            "https://huggingface.co/frappuccino/vits2_ru_natasha/resolve/main/symbols.py"
        private val NATASHA_MODEL_CANDIDATES = listOf("model.onnx", "natasha.onnx")
        private val COMMON_TTS_ROOTS = listOf(
            "tts",
            "Download/tts",
            "Documents/tts",
        )
        private val SKIPPED_STORAGE_DIRS = setOf(
            "Android",
            "android",
            "data",
            "obb",
            ".thumbnails",
            "DCIM",
            "Movies",
            "Pictures",
            "Music",
            "Alarms",
            "Notifications",
            "Ringtones",
        )
        private val PIPER_VOICE_REGEX = Regex("""ru_RU-([a-z0-9_-]+)-(?:medium|high)\.onnx$""", RegexOption.IGNORE_CASE)
        private val PIPER_REMOTE_PACKS = listOf(
            PiperRemotePack(
                downloadId = "download_piper_irina",
                voiceId = "irina",
                voiceLabel = "Ирина (ж)",
                packSlug = "vits-piper-ru_RU-irina-medium",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2",
            ),
            PiperRemotePack(
                downloadId = "download_piper_denis",
                voiceId = "denis",
                voiceLabel = "Денис (м)",
                packSlug = "vits-piper-ru_RU-denis-medium",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-denis-medium.tar.bz2",
            ),
            PiperRemotePack(
                downloadId = "download_piper_dmitri",
                voiceId = "dmitri",
                voiceLabel = "Дмитрий (м)",
                packSlug = "vits-piper-ru_RU-dmitri-medium",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-dmitri-medium.tar.bz2",
            ),
            PiperRemotePack(
                downloadId = "download_piper_ruslan",
                voiceId = "ruslan",
                voiceLabel = "Руслан (м)",
                packSlug = "vits-piper-ru_RU-ruslan-medium",
                archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-ruslan-medium.tar.bz2",
            ),
        )
    }
}
