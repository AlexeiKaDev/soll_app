package com.soll.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.soll.domain.portablessd.PortableSsdEntry
import com.soll.domain.portablessd.PortableSsdEntryCache
import com.soll.domain.portablessd.PortableSsdEntryContent
import com.soll.domain.portablessd.PortableSsdEntryContentSource
import com.soll.domain.portablessd.PortableSsdNode
import com.soll.domain.portablessd.PortableSsdSnapshot
import com.soll.domain.portablessd.PortableSsdSnapshotStatus
import com.soll.domain.portablessd.PortableSsdTreeReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortableSsdRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    val selectedTreeUri: String?
        get() = settingsRepository.portableSsdTreeUri

    suspend fun selectTree(uri: Uri): PortableSsdSnapshot = withContext(Dispatchers.IO) {
        persistReadPermission(uri)
        settingsRepository.portableSsdTreeUri = uri.toString()
        loadSnapshot(uri)
    }

    suspend fun refresh(): PortableSsdSnapshot = withContext(Dispatchers.IO) {
        val rawUri = settingsRepository.portableSsdTreeUri
            ?: return@withContext PortableSsdSnapshot(
                status = PortableSsdSnapshotStatus.NO_ROOT,
                message = "Выбери SSD через системный диалог",
            )
        loadSnapshot(Uri.parse(rawUri))
    }

    fun clearSelection() {
        settingsRepository.portableSsdTreeUri = null
    }

    suspend fun openEntry(entry: PortableSsdEntry): PortableSsdEntryContent = withContext(Dispatchers.IO) {
        val sourceText = readEntryTextFromSelectedTree(entry)
        if (sourceText != null) {
            return@withContext cacheEntry(entry, sourceText, PortableSsdEntryContentSource.SSD)
        }

        readCachedEntry(entry)?.let { return@withContext it }

        val snapshotText = entry.searchText.ifBlank { entry.preview }
        if (snapshotText.isNotBlank()) {
            return@withContext cacheEntry(entry, snapshotText, PortableSsdEntryContentSource.SNAPSHOT)
        }

        throw IllegalStateException("Не удалось открыть статью с SSD и локальной копии пока нет")
    }

    private fun loadSnapshot(uri: Uri): PortableSsdSnapshot {
        persistReadPermission(uri)
        val document = runCatching { DocumentFile.fromTreeUri(context, uri) }
            .onFailure { Timber.w(it, "Could not open portable SSD tree uri=%s", uri) }
            .getOrNull()
        val root = document?.takeIf { it.exists() && it.isDirectory }
            ?.let { DocumentPortableSsdNode(context, it) }
        return PortableSsdTreeReader.read(root)
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Timber.d(error, "Portable SSD URI permission was not persistable for %s", uri)
        }
    }

    private fun readEntryTextFromSelectedTree(entry: PortableSsdEntry): String? {
        val rawUri = settingsRepository.portableSsdTreeUri ?: return null
        val uri = Uri.parse(rawUri)
        persistReadPermission(uri)
        val root = runCatching { DocumentFile.fromTreeUri(context, uri) }
            .onFailure { Timber.w(it, "Could not reopen portable SSD tree for entry=%s", entry.relativePath) }
            .getOrNull()
            ?.takeIf { it.exists() && it.isDirectory }
            ?: return null
        val document = resolveEntryDocument(root, entry) ?: return null
        return readDocumentText(document, MAX_ENTRY_TEXT_BYTES)
    }

    private fun resolveEntryDocument(root: DocumentFile, entry: PortableSsdEntry): DocumentFile? {
        val vault = resolveVaultDocument(root) ?: return null
        val relativePath = entry.relativePath.substringBefore("#").replace('\\', '/')
        val parts = relativePath.split('/').filter { it.isNotBlank() }
        if (parts.any { it == "." || it == ".." }) return null
        var current = vault
        for (part in parts) {
            current = current.childIgnoreCase(part) ?: return null
        }
        return current.takeIf { it.isFile }
    }

    private fun resolveVaultDocument(root: DocumentFile): DocumentFile? =
        listOfNotNull(
            root.takeIf { it.looksLikeVaultDocument() },
            root.takeIf { it.looksLikeProjectDocument() }
                ?.childIgnoreCase("Soll")
                ?.takeIf { it.looksLikeVaultDocument() },
            root.childIgnoreCase("Soll")?.takeIf { it.looksLikeVaultDocument() },
            root.childIgnoreCase("Projects")
                ?.childIgnoreCase("Soll")
                ?.childIgnoreCase("Soll")
                ?.takeIf { it.looksLikeVaultDocument() },
        ).firstOrNull()

    private fun DocumentFile.looksLikeProjectDocument(): Boolean =
        childIgnoreCase("server") != null &&
            childIgnoreCase("desktop") != null &&
            childIgnoreCase("Soll")?.looksLikeVaultDocument() == true

    private fun DocumentFile.looksLikeVaultDocument(): Boolean =
        childIgnoreCase("wiki") != null ||
            childIgnoreCase("daily") != null ||
            childIgnoreCase(".soll")?.childIgnoreCase("tasks.json") != null

    private fun DocumentFile.childIgnoreCase(name: String): DocumentFile? =
        runCatching {
            listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }
        }.getOrElse { error ->
            Timber.w(error, "Could not list portable SSD document children for %s", uri)
            null
        }

    private fun readDocumentText(document: DocumentFile, maxBytes: Int): String? =
        runCatching {
            context.contentResolver.openInputStream(document.uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = maxBytes.coerceAtLeast(0)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.toString(Charsets.UTF_8.name())
            }
        }.getOrElse { error ->
            Timber.w(error, "Could not read portable SSD entry document %s", document.uri)
            null
        }

    private fun cacheEntry(
        entry: PortableSsdEntry,
        text: String,
        source: PortableSsdEntryContentSource,
    ): PortableSsdEntryContent {
        val file = cacheFile(entry)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text, Charsets.UTF_8)
        }.onFailure { error ->
            Timber.w(error, "Could not cache portable SSD entry %s", entry.relativePath)
        }
        return PortableSsdEntryContent(
            entry = entry,
            text = text,
            source = source,
            cachedAt = file.takeIf { it.exists() }?.lastModified() ?: 0L,
            cacheFileName = file.name,
        )
    }

    private fun readCachedEntry(entry: PortableSsdEntry): PortableSsdEntryContent? {
        val file = cacheFile(entry).takeIf { it.isFile } ?: return null
        return runCatching {
            PortableSsdEntryContent(
                entry = entry,
                text = file.readText(Charsets.UTF_8),
                source = PortableSsdEntryContentSource.PHONE_CACHE,
                cachedAt = file.lastModified(),
                cacheFileName = file.name,
            )
        }.getOrElse { error ->
            Timber.w(error, "Could not read cached portable SSD entry %s", file.name)
            null
        }
    }

    private fun cacheFile(entry: PortableSsdEntry): File =
        File(File(context.filesDir, CACHE_DIR_NAME), PortableSsdEntryCache.fileNameFor(entry))

    private companion object {
        const val MAX_ENTRY_TEXT_BYTES = 1024 * 1024
        const val CACHE_DIR_NAME = "portable-ssd-cache"
    }
}

private class DocumentPortableSsdNode(
    private val context: Context,
    private val document: DocumentFile,
) : PortableSsdNode {
    override val name: String
        get() = document.name.orEmpty()

    override val isDirectory: Boolean
        get() = document.isDirectory

    override val isFile: Boolean
        get() = document.isFile

    override val sizeBytes: Long
        get() = document.length()

    override val updatedAt: Long
        get() = document.lastModified()

    override fun children(): List<PortableSsdNode> =
        runCatching {
            document.listFiles().map { DocumentPortableSsdNode(context, it) }
        }.getOrElse { error ->
            Timber.w(error, "Could not list portable SSD children for %s", document.uri)
            emptyList()
        }

    override fun readText(maxBytes: Int): String =
        runCatching {
            val stream = context.contentResolver.openInputStream(document.uri) ?: return ""
            stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = maxBytes.coerceAtLeast(0)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.toString(Charsets.UTF_8.name())
            }
        }.getOrElse { error ->
            Timber.w(error, "Could not read portable SSD document %s", document.uri)
            ""
        }
}
