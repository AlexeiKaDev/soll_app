package com.soll.domain.portablessd

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

enum class PortableSsdSection(val label: String) {
    WIKI("Wiki"),
    DAILY("Daily"),
    TASKS("Tasks"),
}

enum class PortableSsdSnapshotStatus {
    NO_ROOT,
    INVALID,
    READY,
}

data class PortableSsdEntry(
    val id: String,
    val title: String,
    val relativePath: String,
    val section: PortableSsdSection,
    val preview: String = "",
    val searchText: String = "",
    val sizeBytes: Long = 0L,
    val updatedAt: Long = 0L,
)

enum class PortableSsdEntryContentSource {
    SSD,
    PHONE_CACHE,
    SNAPSHOT,
}

data class PortableSsdEntryContent(
    val entry: PortableSsdEntry,
    val text: String,
    val source: PortableSsdEntryContentSource,
    val cachedAt: Long = 0L,
    val cacheFileName: String = "",
)

object PortableSsdEntryCache {
    fun fileNameFor(entry: PortableSsdEntry): String {
        val sourcePath = entry.relativePath.substringBefore("#").replace('\\', '/')
        val rawName = sourcePath.substringAfterLast('/').ifBlank { entry.id }
        val safeName = rawName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .take(72)
            .ifBlank { "entry" }
        val hash = sha256("${entry.section.name}:${entry.id}:$sourcePath").take(16)
        return "${entry.section.name.lowercase()}_${hash}_$safeName"
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

data class PortableSsdSnapshot(
    val status: PortableSsdSnapshotStatus,
    val rootLabel: String = "",
    val projectPath: String = "",
    val vaultPath: String = "",
    val portableIdentityLabel: String? = null,
    val hasPortableIdentity: Boolean = false,
    val wiki: List<PortableSsdEntry> = emptyList(),
    val daily: List<PortableSsdEntry> = emptyList(),
    val tasks: List<PortableSsdEntry> = emptyList(),
    val message: String = "",
) {
    val isReady: Boolean get() = status == PortableSsdSnapshotStatus.READY
    val totalEntries: Int get() = wiki.size + daily.size + tasks.size
}

interface PortableSsdNode {
    val name: String
    val isDirectory: Boolean
    val isFile: Boolean
    val sizeBytes: Long
    val updatedAt: Long
    fun children(): List<PortableSsdNode>
    fun readText(maxBytes: Int = PortableSsdTreeReader.MAX_TEXT_BYTES): String
}

object PortableSsdTreeReader {
    const val MAX_TEXT_BYTES = 96 * 1024
    private const val MAX_MARKDOWN_FILES = 700
    private const val MAX_TASKS = 250

    fun read(root: PortableSsdNode?): PortableSsdSnapshot {
        if (root == null) {
            return PortableSsdSnapshot(
                status = PortableSsdSnapshotStatus.NO_ROOT,
                message = "Корень SSD не выбран",
            )
        }
        if (!root.isDirectory) {
            return PortableSsdSnapshot(
                status = PortableSsdSnapshotStatus.INVALID,
                rootLabel = root.name,
                message = "Выбранный объект не является папкой",
            )
        }

        val resolved = resolveVault(root) ?: return PortableSsdSnapshot(
            status = PortableSsdSnapshotStatus.INVALID,
            rootLabel = root.name,
            message = "Не найден portable Soll vault. Выбери корень SSD, Projects/Soll или папку Soll.",
        )
        val identity = resolveIdentity(root)

        val wikiRoot = resolved.vault.child("wiki")
        val dailyRoot = resolved.vault.child("daily")
        val taskBoard = resolved.vault.child("wiki")?.child("task-board.md")
        val taskJson = resolved.vault.child(".soll")?.child("tasks.json")

        val wiki = collectMarkdown(wikiRoot, PortableSsdSection.WIKI, "wiki")
        val daily = collectMarkdown(dailyRoot, PortableSsdSection.DAILY, "daily")
        val tasks = parseTasks(taskJson, taskBoard)

        if (wiki.isEmpty() && daily.isEmpty() && tasks.isEmpty()) {
            return PortableSsdSnapshot(
                status = PortableSsdSnapshotStatus.INVALID,
                rootLabel = root.name,
                projectPath = resolved.projectPath,
                vaultPath = resolved.vaultPath,
                portableIdentityLabel = identity?.label,
                hasPortableIdentity = identity != null,
                message = "Soll vault найден, но wiki/daily/tasks пустые или недоступны",
            )
        }

        return PortableSsdSnapshot(
            status = PortableSsdSnapshotStatus.READY,
            rootLabel = root.name,
            projectPath = resolved.projectPath,
            vaultPath = resolved.vaultPath,
            portableIdentityLabel = identity?.label,
            hasPortableIdentity = identity != null,
            wiki = wiki,
            daily = daily,
            tasks = tasks,
            message = "SSD готов: wiki ${wiki.size}, daily ${daily.size}, tasks ${tasks.size}",
        )
    }

    private fun resolveVault(root: PortableSsdNode): ResolvedVault? {
        val candidates = listOfNotNull(
            root.takeIf { it.looksLikeVault() }?.let { ResolvedVault(it, "Soll", "Soll") },
            root.takeIf { it.looksLikeProject() }?.child("Soll")?.takeIf { it.looksLikeVault() }
                ?.let { ResolvedVault(it, root.name, "${root.name}/Soll") },
            root.child("Soll")?.takeIf { it.looksLikeVault() }
                ?.let { ResolvedVault(it, "Soll", "Soll") },
            root.child("Projects")?.child("Soll")?.child("Soll")?.takeIf { it.looksLikeVault() }
                ?.let { ResolvedVault(it, "Projects/Soll", "Projects/Soll/Soll") },
        )
        return candidates.firstOrNull()
    }

    private fun resolveIdentity(root: PortableSsdNode): PortableSsdIdentity? {
        val identityNode = listOfNotNull(
            root.child(".soll-portable")?.child("identity.json"),
            root.child("Projects")?.child("Soll")?.child(".soll-portable")?.child("identity.json"),
            root.takeIf { it.looksLikeProject() }?.child(".soll-portable")?.child("identity.json"),
        ).firstOrNull { it.isFile } ?: return null
        val raw = identityNode.readText(maxBytes = 16 * 1024)
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        val label = parsed?.optString("label").orEmpty()
            .ifBlank { parsed?.optString("name").orEmpty() }
            .ifBlank { parsed?.optString("id").orEmpty() }
            .ifBlank { "Soll Portable SSD" }
            .take(80)
        return PortableSsdIdentity(label = label)
    }

    private fun PortableSsdNode.looksLikeProject(): Boolean =
        child("server") != null && child("desktop") != null && child("Soll")?.looksLikeVault() == true

    private fun PortableSsdNode.looksLikeVault(): Boolean =
        child("wiki") != null || child(".soll")?.child("tasks.json") != null || child("daily") != null

    private fun collectMarkdown(
        root: PortableSsdNode?,
        section: PortableSsdSection,
        basePath: String,
    ): List<PortableSsdEntry> {
        if (root == null || !root.isDirectory) return emptyList()
        val result = mutableListOf<PortableSsdEntry>()
        val queue = ArrayDeque<Pair<PortableSsdNode, String>>()
        queue.add(root to basePath)
        while (queue.isNotEmpty() && result.size < MAX_MARKDOWN_FILES) {
            val (current, currentPath) = queue.removeFirst()
            current.children()
                .sortedWith(compareByDescending<PortableSsdNode> { it.isDirectory }.thenBy { it.name.lowercase() })
                .forEach { child ->
                    if (child.name.startsWith(".")) return@forEach
                    val childPath = "$currentPath/${child.name}"
                    when {
                        child.isDirectory -> queue.add(child to childPath)
                        child.isFile && child.name.endsWith(".md", ignoreCase = true) -> {
                            val text = child.readText()
                            result += PortableSsdEntry(
                                id = "${section.name.lowercase()}:$childPath",
                                title = titleFromMarkdown(child.name, text),
                                relativePath = childPath,
                                section = section,
                                preview = previewFromText(text),
                                searchText = text,
                                sizeBytes = child.sizeBytes.coerceAtLeast(0L),
                                updatedAt = child.updatedAt.coerceAtLeast(0L),
                            )
                        }
                    }
                }
        }
        return result.sortedBy { it.relativePath.lowercase() }
    }

    private fun parseTasks(
        taskJson: PortableSsdNode?,
        taskBoard: PortableSsdNode?,
    ): List<PortableSsdEntry> {
        val entries = mutableListOf<PortableSsdEntry>()
        if (taskBoard?.isFile == true) {
            val text = taskBoard.readText()
            entries += PortableSsdEntry(
                id = "task-board:wiki/task-board.md",
                title = "Task Board",
                relativePath = "wiki/task-board.md",
                section = PortableSsdSection.TASKS,
                preview = previewFromText(text),
                searchText = text,
                sizeBytes = taskBoard.sizeBytes.coerceAtLeast(0L),
                updatedAt = taskBoard.updatedAt.coerceAtLeast(0L),
            )
        }
        if (taskJson?.isFile != true) return entries

        val raw = taskJson.readText()
        val parsed = runCatching {
            when {
                raw.trim().startsWith("[") -> JSONArray(raw)
                else -> JSONObject(raw).optJSONArray("tasks") ?: JSONArray()
            }
        }.getOrNull() ?: return entries

        for (index in 0 until minOf(parsed.length(), MAX_TASKS)) {
            val item = parsed.optJSONObject(index) ?: continue
            val id = item.optString("id").ifBlank { index.toString() }
            val title = item.optString("title").ifBlank { "Task $id" }
            val status = item.optString("status").ifBlank { "unknown" }
            val project = item.optString("project_name").ifBlank { item.optString("project_id") }
            val description = item.optString("description")
            entries += PortableSsdEntry(
                id = "task:$id",
                title = title,
                relativePath = ".soll/tasks.json#$id",
                section = PortableSsdSection.TASKS,
                preview = listOf(status, project, description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .take(420),
                searchText = "$title\n$status\n$project\n$description",
                sizeBytes = taskJson.sizeBytes.coerceAtLeast(0L),
                updatedAt = taskJson.updatedAt.coerceAtLeast(0L),
            )
        }
        return entries
    }

    private fun PortableSsdNode.child(name: String): PortableSsdNode? =
        children().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun titleFromMarkdown(fileName: String, text: String): String {
        val heading = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("#") }
            ?.trimStart('#')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return heading ?: fileName.removeSuffix(".md")
    }

    private fun previewFromText(text: String): String =
        text.lineSequence()
            .map { it.trim().trimStart('#').trim() }
            .filter { it.isNotBlank() && !it.startsWith("---") }
            .take(4)
            .joinToString(" ")
            .take(360)

    private data class ResolvedVault(
        val vault: PortableSsdNode,
        val projectPath: String,
        val vaultPath: String,
    )

    private data class PortableSsdIdentity(
        val label: String,
    )
}

enum class PortableSsdAttachNoticeKind {
    VERIFIED,
    NEED_SELECTION,
    NOT_READY,
}

data class PortableSsdAttachNotice(
    val kind: PortableSsdAttachNoticeKind,
    val title: String,
    val message: String,
)

object PortableSsdAttachNotificationPolicy {
    fun noticeFor(snapshot: PortableSsdSnapshot, hasSelectedRoot: Boolean): PortableSsdAttachNotice? =
        when {
            snapshot.status == PortableSsdSnapshotStatus.READY -> PortableSsdAttachNotice(
                kind = PortableSsdAttachNoticeKind.VERIFIED,
                title = "Soll SSD подключен",
                message = buildString {
                    val label = snapshot.portableIdentityLabel?.takeIf { it.isNotBlank() }
                    if (label != null) {
                        append("$label распознан как portable SSD Soll. ")
                    } else {
                        append("Распознан portable Soll vault. ")
                    }
                    append("Wiki: ${snapshot.wiki.size}, Daily: ${snapshot.daily.size}, Tasks: ${snapshot.tasks.size}.")
                },
            )
            !hasSelectedRoot || snapshot.status == PortableSsdSnapshotStatus.NO_ROOT -> PortableSsdAttachNotice(
                kind = PortableSsdAttachNoticeKind.NEED_SELECTION,
                title = "USB SSD подключен",
                message = "Открой SSD Wiki и выбери корень portable SSD, чтобы Soll мог узнавать этот диск автоматически.",
            )
            snapshot.status == PortableSsdSnapshotStatus.INVALID -> PortableSsdAttachNotice(
                kind = PortableSsdAttachNoticeKind.NOT_READY,
                title = "SSD не распознан как Soll",
                message = snapshot.message.ifBlank {
                    "Подключенный диск не похож на подготовленный portable SSD Soll."
                },
            )
            else -> null
        }
}
