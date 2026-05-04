package com.soll.domain.tts.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.soll.domain.tts.TtsEngineType
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject
import javax.inject.Singleton

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

    fun importFromTreeUri(treeUri: Uri): TtsPackImportResult {
        Timber.i("TTS import requested for treeUri=%s", treeUri)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onSuccess {
            Timber.d("Persisted read permission for treeUri=%s", treeUri)
        }.onFailure { Timber.w(it, "takePersistableUriPermission failed for %s", treeUri) }

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return TtsPackImportResult(detectedCount = 0, importedCount = 0, failedCount = 0)
        if (!root.isDirectory || !root.canRead()) {
            return TtsPackImportResult(detectedCount = 0, importedCount = 0, failedCount = 0)
        }

        importedRoot.mkdirs()
        val candidates = collectImportCandidates(root)
        Timber.i("TTS import scan found %d candidates under %s", candidates.size, treeUri)
        var importedCount = 0
        var failedCount = 0
        candidates.forEach { candidate ->
            val dest = File(importedRoot, "${candidate.engineDirName}/${candidate.destSlug}")
            runCatching {
                Timber.d(
                    "Importing TTS candidate engine=%s slug=%s from=%s to=%s",
                    candidate.engineDirName,
                    candidate.destSlug,
                    candidate.sourceDir.uri,
                    dest.absolutePath,
                )
                if (dest.exists()) dest.deleteRecursively()
                copyDocumentDirToFiles(candidate.sourceDir, dest)
                if (candidate.isKokoroManifestRoot) {
                    mergeKokoroVoicesFromNearbyTree(dest, candidate.sourceDir)
                    if (!File(dest, "voices").hasUsableKokoroVoiceBins()) {
                        mergeKokoroVoicesFromSelectedTree(dest, root)
                    }
                }
                Timber.d("Imported TTS candidate slug=%s files=%d", candidate.destSlug, dest.walkTopDown().count())
            }.onSuccess {
                importedCount++
            }.onFailure {
                failedCount++
                dest.deleteRecursively()
                Timber.e(it, "Failed to import TTS pack ${candidate.destSlug}")
            }
        }
        return TtsPackImportResult(
            detectedCount = candidates.size,
            importedCount = importedCount,
            failedCount = failedCount,
        )
    }

    fun listDetectedPacks(): List<DetectedTtsPack> {
        val packs = mutableListOf<DetectedTtsPack>()
        scanImportedPacks(packs)
        scanLegacyRuntimePacks(packs)
        scanLegacyOnnxPacks(packs)
        return packs
            .distinctBy { it.packId }
            .sortedWith(
                compareByDescending<DetectedTtsPack> { it.isRussianCapable }
                    .thenBy { it.engineFamily.ordinal }
                    .thenBy { it.displayName.lowercase(Locale.getDefault()) },
            )
            .also { detected ->
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
    }

    fun listDownloadableRussianPacks(): List<DownloadableTtsPack> =
        PIPER_REMOTE_PACKS.map { remote ->
            DownloadableTtsPack(
                id = remote.downloadId,
                engineFamily = TtsPackEngineFamily.PIPER,
                displayName = "Piper ${remote.voiceLabel}",
                description = "Готовый Sherpa-совместимый русский голос Piper ${remote.voiceLabel} (~${remote.estimatedSizeMb} MB).",
                estimatedSizeMb = remote.estimatedSizeMb,
                isRussianCapable = true,
                suggestedEnginePackId = remote.packId,
            )
        } + listOf(
            DownloadableTtsPack(
            id = DOWNLOAD_ID_NATASHA,
            engineFamily = TtsPackEngineFamily.NATASHA,
            displayName = "Natasha VITS2",
            description = "Русская оффлайн-модель Natasha VITS2 (~80 MB).",
            estimatedSizeMb = 80,
            isRussianCapable = true,
            ),
        )

    fun findPackById(packId: String): DetectedTtsPack? =
        listDetectedPacks().firstOrNull { it.packId == packId }

    fun deletePack(packId: String): Boolean {
        val pack = findPackById(packId) ?: return false
        if (!pack.canDelete) return false
        return runCatching {
            File(pack.rootDir).deleteRecursively()
        }.getOrDefault(false)
    }

    fun deleteSuggestedPacks(): Int {
        val targets = listDetectedPacks()
            .filter { it.suggestedDeletion && it.canDelete }
            .map { it.packId }
            .distinct()
        var deleted = 0
        for (packId in targets) {
            if (deletePack(packId)) deleted++
        }
        return deleted
    }

    suspend fun downloadPack(packId: String): Boolean {
        PIPER_REMOTE_PACKS.firstOrNull { it.downloadId == packId }?.let { remote ->
            return downloadPiperPack(
                downloadId = remote.downloadId,
                label = "Piper ${remote.voiceLabel}",
                url = remote.archiveUrl,
                destSlug = remote.packSlug,
            )
        }
        return when (packId) {
            DOWNLOAD_ID_NATASHA -> downloadNatashaPack()
            else -> false
        }
    }

    fun findBestPack(type: TtsEngineType): DetectedTtsPack? {
        val family = when (type) {
            TtsEngineType.SILERO -> TtsPackEngineFamily.PIPER
            TtsEngineType.NATASHA -> TtsPackEngineFamily.NATASHA
            TtsEngineType.UTROBIN -> TtsPackEngineFamily.UTROBIN
            TtsEngineType.ONNX_EXTERNAL -> TtsPackEngineFamily.ONNX_EXTERNAL
            TtsEngineType.SYSTEM -> return null
        }
        return listDetectedPacks()
            .asSequence()
            .filter { it.engineFamily == family }
            .filter { it.isRunnable }
            .filter { it.isRussianCapable }
            .firstOrNull()
    }

    fun listPacksFor(type: TtsEngineType): List<DetectedTtsPack> {
        val family = when (type) {
            TtsEngineType.SILERO -> TtsPackEngineFamily.PIPER
            TtsEngineType.NATASHA -> TtsPackEngineFamily.NATASHA
            TtsEngineType.UTROBIN -> TtsPackEngineFamily.UTROBIN
            TtsEngineType.ONNX_EXTERNAL -> TtsPackEngineFamily.ONNX_EXTERNAL
            TtsEngineType.SYSTEM -> return emptyList()
        }
        return listDetectedPacks().filter { it.engineFamily == family }
    }

    private fun scanImportedPacks(out: MutableList<DetectedTtsPack>) {
        if (!importedRoot.exists()) return
        val families = importedRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (familyDir in families) {
            when (familyDir.name) {
                "piper" -> familyDir.listFiles()?.filter { it.isDirectory }?.forEach { out += inspectPiperPack(it, TtsPackSourceType.IMPORTED) }
                "natasha" -> familyDir.listFiles()?.filter { it.isDirectory }?.forEach { out += inspectNatashaPack(it, TtsPackSourceType.IMPORTED) }
                "utrobin" -> familyDir.listFiles()?.filter { it.isDirectory }?.forEach { out += inspectUtrobinPack(it, TtsPackSourceType.IMPORTED) }
                "onnx" -> familyDir.walkTopDown()
                    .filter { it.isFile && it.name == MODEL_MANIFEST }
                    .forEach { manifest -> inspectOnnxPack(manifest, TtsPackSourceType.IMPORTED)?.let(out::add) }
            }
        }
    }

    private fun downloadNatashaPack(): Boolean {
        val destDir = File(importedRoot, "natasha/natasha_vits2_remote")
        destDir.mkdirs()
        val outFile = File(destDir, "model.onnx")
        _downloadState.value = TtsPackDownloadState(
            packId = DOWNLOAD_ID_NATASHA,
            label = "Natasha VITS2",
            progress = 0f,
            message = "Начинаю загрузку",
        )
        return runCatching {
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
        }.onFailure { error ->
            Timber.e(error, "Failed to download Natasha pack")
            _downloadState.value = TtsPackDownloadState(
                packId = DOWNLOAD_ID_NATASHA,
                label = "Natasha VITS2",
                progress = null,
                message = error.message ?: "Ошибка загрузки Natasha",
                isError = true,
            )
        }.getOrDefault(false)
    }

    private fun downloadPiperPack(
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
        return runCatching {
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
                message = "Распаковка Piper pack",
            )
            extractTarBz2(archiveFile, familyRoot)
            Timber.d(
                "Finished Piper extraction slug=%s directChildren=%s",
                destSlug,
                familyRoot.listFiles()?.joinToString { it.name } ?: "<empty>",
            )
            val extracted = familyRoot.listFiles()
                ?.firstOrNull { it.isDirectory && it.name == destSlug }
                ?: error("Не удалось найти распакованный Piper pack: $destSlug")
            val pack = inspectPiperPack(extracted, TtsPackSourceType.IMPORTED)
            if (!pack.isRunnable) {
                error(pack.reason ?: "Piper pack распакован, но не распознан как runnable")
            }
            Timber.i(
                "Piper pack ready packId=%s voices=%s root=%s",
                pack.packId,
                pack.voices.joinToString { it.label },
                pack.rootDir,
            )
            archiveFile.delete()
            _downloadState.value = null
            true
        }.onFailure { error ->
            Timber.e(error, "Failed to download Piper pack $destSlug")
            archiveFile.delete()
            if (destDir.exists()) destDir.deleteRecursively()
            _downloadState.value = TtsPackDownloadState(
                packId = downloadId,
                label = label,
                progress = null,
                message = error.message ?: "Ошибка загрузки Piper pack",
                isError = true,
            )
        }.getOrDefault(false)
    }

    private fun downloadFileWithProgress(
        url: String,
        outFile: File,
        packId: String,
        label: String,
    ) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Пустой ответ сервера")
            val total = body.contentLength().takeIf { it > 0L }
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
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
                    }
                }
            }
        }
    }

    private fun downloadSmallFile(url: String, outFile: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Пустой ответ сервера")
            outFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }
    }

    private fun extractTarBz2(archiveFile: File, destDir: File) {
        runCatching {
            extractTarBz2WithSystemTar(archiveFile, destDir)
        }.onFailure { error ->
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

    private fun extractTarBz2WithSystemTar(archiveFile: File, destDir: File) {
        val process = ProcessBuilder(
            "/system/bin/tar",
            "-xjf",
            archiveFile.absolutePath,
            "-C",
            destDir.absolutePath,
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        val exitCode = process.waitFor()
        Timber.d("System tar exit=%d output=%s", exitCode, output.ifBlank { "<empty>" })
        if (exitCode != 0) {
            error("System tar extraction failed with code $exitCode: ${output.ifBlank { "no output" }}")
        }
    }

    private fun extractTarEntries(input: TarArchiveInputStream, destDir: File) {
        val canonicalRoot = destDir.canonicalFile
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val entry = input.nextTarEntry ?: break
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
            canDelete = isManagedPackRoot(root),
        )
    }

    private fun inspectUtrobinPack(root: File, sourceType: TtsPackSourceType): DetectedTtsPack {
        val model = File(root, "model.onnx")
        val tokens = File(root, "tokens.txt")
        val status = when {
            !model.exists() -> TtsPackStatus.INCOMPLETE
            isLikelyPointerFile(model) -> TtsPackStatus.BROKEN_POINTER
            model.length() < MIN_UTROBIN_MODEL_BYTES -> TtsPackStatus.INVALID_FILESET
            !tokens.exists() -> TtsPackStatus.INCOMPLETE
            else -> TtsPackStatus.READY
        }
        val reason = when (status) {
            TtsPackStatus.INCOMPLETE -> if (!tokens.exists()) "Нет tokens.txt" else "Нет model.onnx"
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
            canDelete = isManagedPackRoot(root),
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
            canDelete = isManagedPackRoot(root),
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

            val status = when {
                runtimeFamily == "kokoro_v1" && hasUsableKokoroRuntimeFiles(root) ->
                    if (hasRussian) TtsPackStatus.READY else TtsPackStatus.READY_NON_RUSSIAN
                runtimeFamily == "kokoro_v1" -> TtsPackStatus.INCOMPLETE
                else -> TtsPackStatus.UNSUPPORTED_RUNTIME
            }
            val reason = when (status) {
                TtsPackStatus.INCOMPLETE -> "Для Kokoro не хватает model/config/voices"
                TtsPackStatus.UNSUPPORTED_RUNTIME -> "Runtime family '${runtimeFamily ?: "unknown"}' не поддержан на Android"
                TtsPackStatus.READY_NON_RUSSIAN -> "Пак рабочий, но не рассчитан на русский TTS"
                else -> null
            }
            val voices = if (runtimeFamily == "kokoro_v1") {
                listKokoroVoices(root)
            } else {
                emptyList()
            }
            DetectedTtsPack(
                packId = "onnx:$modelId:$precision",
                engineFamily = TtsPackEngineFamily.ONNX_EXTERNAL,
                displayName = modelId,
                rootDir = root.absolutePath,
                sourceType = sourceType,
                status = status,
                reason = reason,
                isRussianCapable = hasRussian,
                runtimeFamily = runtimeFamily,
                voices = voices,
                suggestedDeletion = status == TtsPackStatus.UNSUPPORTED_RUNTIME || status == TtsPackStatus.READY_NON_RUSSIAN,
                canDelete = isManagedPackRoot(root),
                modelId = modelId,
                precision = precision,
                estimatedSizeMb = json.optInt("estimatedSizeMb", -1).takeIf { it > 0 },
            )
        }.onFailure {
            Timber.w(it, "Failed to inspect ONNX manifest ${manifestFile.absolutePath}")
        }.getOrNull()
    }

    private fun hasUsableKokoroRuntimeFiles(root: File): Boolean {
        val hasConfig = File(root, "config.json").exists()
        val voicesDir = File(root, "voices")
        val hasVoices = voicesDir.hasUsableKokoroVoiceBins()
        val onnxCandidates = listOf(
            File(root, "onnx/model.onnx"),
            File(root, "onnx/model_fp16.onnx"),
            File(root, "onnx/model_quantized.onnx"),
            File(root, "model.onnx"),
            File(root, "model_fp16.onnx"),
            File(root, "model_quantized.onnx"),
        )
        return hasConfig && hasVoices && onnxCandidates.any { it.exists() && !isLikelyPointerFile(it) }
    }

    private fun listKokoroVoices(root: File): List<DetectedTtsVoice> {
        val voicesDir = File(root, "voices")
        return voicesDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("bin", true) && it.length() >= MIN_KOKORO_VOICE_BIN_BYTES }
            ?.sortedBy { it.name }
            ?.map { DetectedTtsVoice(it.nameWithoutExtension, it.nameWithoutExtension.replace('_', ' '), language = "en", isRussian = false, sourcePath = it.absolutePath) }
            .orEmpty()
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
            val childNames = children.mapNotNull { it.name }.toSet()

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
            if (lowerName.contains("natasha") || childNames.contains("natasha.onnx")) {
                out += ImportCandidate(dir, "natasha", sanitizeSlug(name.ifBlank { "natasha_vits2" }))
                continue
            }
            if (lowerName.contains("utrobin") || lowerName.contains("tts_ru_free_hf_vits")) {
                out += ImportCandidate(dir, "utrobin", sanitizeSlug(name.ifBlank { "utrobin_vits" }))
                continue
            }
            if (children.any { child -> child.isFile && PIPER_VOICE_REGEX.containsMatchIn(child.name.orEmpty()) }) {
                out += ImportCandidate(dir, "piper", sanitizeSlug(name.ifBlank { "piper_pack" }))
                continue
            }

            children.filter { it.isDirectory }.forEach(queue::add)
        }
        return out.distinctBy { "${it.engineDirName}:${it.destSlug}" }
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

    private data class ManifestMeta(
        val modelId: String,
        val precision: String,
        val runtimeFamily: String?,
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
        private val PIPER_VOICE_REGEX = Regex("""ru_RU-([a-z0-9_-]+)-medium\.onnx$""", RegexOption.IGNORE_CASE)
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
