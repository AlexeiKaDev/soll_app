package com.soll.domain.tts.onnx

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledOnnxPack(
    val modelId: String,
    val precision: String,
    val rootDir: String,
    val estimatedSizeMb: Int,
    /** e.g. kokoro_v1 — см. prepare_onnx_pack.py / model_manifest.json */
    val runtimeFamily: String? = null,
    /** Голос Kokoro: файл voices/{id}.bin */
    val kokoroVoice: String? = null,
) {
    fun effectiveRuntimeFamily(): String = runtimeFamily ?: when (modelId) {
        "kokoro_82m" -> "kokoro_v1"
        else -> "unsupported"
    }
}

@Singleton
class OnnxModelPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val internalRoot: File = File(context.filesDir, "external_models/tts")
    private val externalRoot: File? = context.getExternalFilesDir(null)?.let { File(it, "tts_models") }

    /**
     * Импорт из выбранной пользователем папки (например Downloads/tts): ищет подпапки с
     * [MODEL_MANIFEST], читает modelId/precision и копирует всё в [internalRoot]/modelId/precision.
     * Нужен открытый tree URI из [android.provider.DocumentsContract.ACTION_OPEN_DOCUMENT_TREE].
     */
    fun importPacksFromTreeUri(treeUri: Uri): Int {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "takePersistableUriPermission failed") }

        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        if (!root.isDirectory || !root.canRead()) return 0

        var count = 0
        for (packRoot in collectPackRootsWithManifest(root)) {
            val manifest = packRoot.listFiles()
                ?.firstOrNull { it.isFile && it.name == MODEL_MANIFEST }
                ?: continue
            val meta = parseManifestDocument(manifest) ?: continue
            val modelId = meta.modelId
            val precision = meta.precision
            if (!isSafePathSegment(modelId) || !isSafePathSegment(precision)) {
                Timber.w("skip unsafe onnx pack path: $modelId / $precision")
                continue
            }
            val dest = File(internalRoot, "$modelId/$precision")
            runCatching {
                if (dest.exists()) dest.deleteRecursively()
                dest.mkdirs()
                copyDocumentDirToFiles(packRoot, dest)
                if (modelId == "kokoro_82m") {
                    mergeKokoroVoicesFromNearbyTree(dest, packRoot)
                    if (!File(dest, "voices").hasUsableKokoroVoiceBins()) {
                        mergeKokoroVoicesFromSelectedTree(dest, root)
                    }
                }
                count += 1
            }.onFailure { Timber.e(it, "failed to import pack $modelId $precision") }
        }
        return count
    }

    fun listInstalledPacks(): List<InstalledOnnxPack> {
        val roots = listOfNotNull(internalRoot, externalRoot)
        val result = mutableListOf<InstalledOnnxPack>()
        roots.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkTopDown()
                .filter { it.isFile && it.name == MODEL_MANIFEST }
                .forEach { manifestFile ->
                    parseManifest(manifestFile)?.let(result::add)
                }
        }
        return result.sortedWith(compareBy({ it.modelId }, { it.precision }))
    }

    fun pickBestRussianPack(): InstalledOnnxPack? {
        val installed = listInstalledPacks()
        val byId = installed.groupBy { it.modelId }
        val candidates = OnnxTtsModelCatalog.recommendedRussianByQualitySize(maxModelSizeMb = 2000)
        for (model in candidates) {
            val packs = byId[model.id] ?: continue
            return packs.minByOrNull { it.estimatedSizeMb }
        }
        // Kokoro не помечен как RU в каталоге, но даёт рабочий ONNX-путь для англ. текста
        return installed.firstOrNull { it.modelId == "kokoro_82m" }
    }

    private fun parseManifest(file: File): InstalledOnnxPack? {
        return runCatching {
            val json = JSONObject(file.readText())
            InstalledOnnxPack(
                modelId = json.optString("modelId"),
                precision = json.optString("precision", "fp32"),
                rootDir = file.parentFile?.absolutePath.orEmpty(),
                estimatedSizeMb = json.optInt("estimatedSizeMb", -1),
                runtimeFamily = json.optString("runtimeFamily").takeIf { it.isNotBlank() },
                kokoroVoice = json.optString("kokoroVoice").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private data class ManifestMeta(val modelId: String, val precision: String)

    private fun parseManifestDocument(manifestFile: DocumentFile): ManifestMeta? {
        return runCatching {
            val text = context.contentResolver.openInputStream(manifestFile.uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return@runCatching null
            val json = JSONObject(text)
            val modelId = json.optString("modelId")
            val precision = json.optString("precision", "fp32")
            if (modelId.isBlank()) return@runCatching null
            ManifestMeta(modelId = modelId, precision = precision)
        }.getOrNull()
    }

    private fun collectPackRootsWithManifest(root: DocumentFile): List<DocumentFile> {
        val out = mutableListOf<DocumentFile>()
        fun walk(node: DocumentFile) {
            if (!node.isDirectory || !node.canRead()) return
            val children = node.listFiles() ?: return
            if (children.any { it.isFile && it.name == MODEL_MANIFEST }) {
                out.add(node)
                return
            }
            children.forEach { child ->
                if (child.isDirectory) walk(child)
            }
        }
        walk(root)
        return out
    }

    private fun copyDocumentDirToFiles(srcDir: DocumentFile, destDir: File) {
        destDir.mkdirs()
        val children = srcDir.listFiles() ?: return
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                copyDocumentDirToFiles(child, File(destDir, name))
            } else {
                // Файлы: иногда провайдер не выставляет isFile — всё же копируем потоком.
                copyDocumentFileToFile(child, File(destDir, name))
            }
        }
    }

    private fun copyDocumentFileToFile(child: DocumentFile, outFile: File) {
        outFile.parentFile?.mkdirs()
        runCatching {
            context.contentResolver.openInputStream(child.uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        }.onFailure { Timber.w(it, "skip copy ${child.uri} -> ${outFile.name}") }
    }

    /**
     * У Kokoro часто структура `…/kokoro_82m/fp32/` (манифест) и `…/kokoro_82m/voices/` рядом.
     * При импорте дерева копируется только папка с манифестом — подтягиваем voices с предков SAF.
     */
    private fun mergeKokoroVoicesFromNearbyTree(destPack: File, packRootDoc: DocumentFile) {
        val voicesDest = File(destPack, "voices")
        if (voicesDest.hasUsableKokoroVoiceBins()) return
        val src = findVoicesDocumentNearPack(packRootDoc) ?: return
        voicesDest.mkdirs()
        copyDocumentDirToFiles(src, voicesDest)
        Timber.i(
            "Kokoro import: voices подмешаны в ${voicesDest.absolutePath} " +
                "(источник: ${src.name ?: src.uri})",
        )
    }

    /** Голоса Kokoro на HF ~500 KiB; меньше — обычно указатель Git LFS, не валидные веса. */
    private fun File.hasUsableKokoroVoiceBins(): Boolean =
        isDirectory && listFiles()?.any { f ->
            f.isFile && f.extension.equals("bin", true) && f.length() >= MIN_KOKORO_VOICE_BIN_BYTES
        } == true

    private fun mergeKokoroVoicesFromSelectedTree(destPack: File, treeRoot: DocumentFile) {
        val voicesDest = File(destPack, "voices")
        if (voicesDest.hasUsableKokoroVoiceBins()) return
        val src = findVoicesDocumentWithUsableBinsInSubtree(treeRoot) ?: run {
            Timber.w(
                "Kokoro import: в выбранном дереве нет каталога voices с реальными .bin " +
                    "(≥${MIN_KOKORO_VOICE_BIN_BYTES} B). Часто голоса — Git LFS: качай через " +
                    "huggingface_hub snapshot_download / python tools/tts/prepare_onnx_pack.py --download.",
            )
            return
        }
        voicesDest.mkdirs()
        copyDocumentDirToFiles(src, voicesDest)
        Timber.i(
            "Kokoro import: voices из обхода дерева → ${voicesDest.absolutePath}",
        )
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
            dir.listFiles()?.filter { it.isDirectory }?.forEach { queue.add(it) }
        }
        return null
    }

    private fun DocumentFile.hasUsableBinChildren(): Boolean =
        listFiles()?.any { child ->
            val n = child.name
            n != null && n.endsWith(".bin", ignoreCase = true) && child.length() >= MIN_KOKORO_VOICE_BIN_BYTES
        } == true

    /** Ищем каталог voices с .bin среди родителей папки пака (до 6 уровней). */
    private fun findVoicesDocumentNearPack(packRootDoc: DocumentFile): DocumentFile? {
        var cursor: DocumentFile? = packRootDoc.parentFile
        repeat(6) {
            val dir = cursor ?: return null
            val voices = dir.listFiles()?.filter { df ->
                df.isDirectory && df.name?.equals("voices", ignoreCase = true) == true
            } ?: emptyList()
            for (v in voices) {
                if (v.hasUsableBinChildren()) return v
            }
            cursor = dir.parentFile
        }
        return null
    }

    private fun isSafePathSegment(s: String): Boolean =
        s.isNotBlank() &&
            !s.contains("..") &&
            !s.contains('/') &&
            !s.contains('\\')

    private companion object {
        const val MODEL_MANIFEST = "model_manifest.json"
        const val MIN_KOKORO_VOICE_BIN_BYTES = 8192L
    }
}

