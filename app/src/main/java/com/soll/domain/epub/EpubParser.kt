package com.soll.domain.epub

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class EpubBook(
    val title: String,
    val author: String?,
    val chapters: List<EpubChapter>,
    val coverData: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EpubBook
        return title == other.title && author == other.author
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        return result
    }
}

data class EpubChapter(
    val index: Int,
    val title: String,
    val content: String // Plain text content
)

class EpubParser(private val context: Context) {

    fun parseEpub(uri: Uri): EpubBook? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseEpubFromStream(inputStream)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing EPUB")
            null
        }
    }

    fun parseEpub(filePath: String): EpubBook? {
        return try {
            java.io.File(filePath).inputStream().use { inputStream ->
                parseEpubFromStream(inputStream)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing EPUB from path: $filePath")
            null
        }
    }

    private fun parseEpubFromStream(inputStream: InputStream): EpubBook? {
        val zipInputStream = ZipInputStream(inputStream)
        val entries = mutableMapOf<String, ByteArray>()

        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                entries[entry.name] = zipInputStream.readBytes()
            }
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()

        // Parse container.xml to find content.opf path
        val containerXml = entries["META-INF/container.xml"]?.decodeToString()
        val opfPath = extractOpfPath(containerXml) ?: return null

        // Get base path for relative references
        val basePath = opfPath.substringBeforeLast("/", "")

        // Parse content.opf for metadata and spine
        val opfContent = entries[opfPath]?.decodeToString() ?: return null
        val metadata = parseOpfMetadata(opfContent)
        val spineItems = parseOpfSpine(opfContent)

        // Parse chapters
        val chapters = mutableListOf<EpubChapter>()
        spineItems.forEachIndexed { index, itemPath ->
            val fullPath = if (basePath.isNotEmpty()) "$basePath/$itemPath" else itemPath
            val htmlContent = entries[fullPath]?.decodeToString()
                ?: entries[itemPath]?.decodeToString()

            if (htmlContent != null) {
                val plainText = extractTextFromHtml(htmlContent)
                if (plainText.isNotBlank()) {
                    val chapterTitle = extractChapterTitle(htmlContent) ?: "Глава ${index + 1}"
                    chapters.add(
                        EpubChapter(
                            index = index,
                            title = chapterTitle,
                            content = plainText
                        )
                    )
                }
            }
        }

        // Try to get cover image
        val coverData = findCoverImage(entries, opfContent, basePath)

        return EpubBook(
            title = metadata["title"] ?: "Без названия",
            author = metadata["creator"] ?: metadata["author"],
            chapters = chapters,
            coverData = coverData
        )
    }

    private fun extractOpfPath(containerXml: String?): String? {
        if (containerXml == null) return null
        val doc = Jsoup.parse(containerXml)
        return doc.select("rootfile").firstOrNull()?.attr("full-path")
    }

    private fun parseOpfMetadata(opfContent: String): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        val doc = Jsoup.parse(opfContent)

        doc.select("dc|title, title").firstOrNull()?.text()?.let {
            metadata["title"] = it
        }
        doc.select("dc|creator, creator").firstOrNull()?.text()?.let {
            metadata["creator"] = it
        }
        doc.select("dc|author, author").firstOrNull()?.text()?.let {
            metadata["author"] = it
        }

        return metadata
    }

    private fun parseOpfSpine(opfContent: String): List<String> {
        val doc = Jsoup.parse(opfContent)
        val manifest = mutableMapOf<String, String>()

        // Build manifest map (id -> href)
        doc.select("manifest item").forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isNotEmpty() && href.isNotEmpty()) {
                manifest[id] = href
            }
        }

        // Get spine order
        val spineItems = mutableListOf<String>()
        doc.select("spine itemref").forEach { itemref ->
            val idref = itemref.attr("idref")
            manifest[idref]?.let { href ->
                spineItems.add(href)
            }
        }

        return spineItems
    }

    private fun extractTextFromHtml(html: String): String = extractReadableTextFromHtml(html)

    private fun extractChapterTitle(html: String): String? {
        val doc = Jsoup.parse(html)
        // Try to find title in h1, h2, or title tags
        return doc.select("h1").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
            ?: doc.select("h2").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
            ?: doc.select("title").firstOrNull()?.text()?.takeIf { it.isNotBlank() }
    }

    private fun findCoverImage(
        entries: Map<String, ByteArray>,
        opfContent: String,
        basePath: String
    ): ByteArray? {
        val doc = Jsoup.parse(opfContent)

        // Try to find cover in metadata
        val coverMeta = doc.select("meta[name=cover]").firstOrNull()
        val coverId = coverMeta?.attr("content")

        if (coverId != null) {
            val coverItem = doc.select("manifest item[id=$coverId]").firstOrNull()
            val coverHref = coverItem?.attr("href")
            if (coverHref != null) {
                val fullPath = if (basePath.isNotEmpty()) "$basePath/$coverHref" else coverHref
                return entries[fullPath] ?: entries[coverHref]
            }
        }

        // Try to find cover by common naming patterns
        val coverPatterns = listOf("cover.jpg", "cover.jpeg", "cover.png", "Cover.jpg", "Cover.png")
        for (pattern in coverPatterns) {
            entries.keys.find { it.endsWith(pattern) }?.let { key ->
                return entries[key]
            }
        }

        return null
    }
}

internal fun extractReadableTextFromHtml(html: String): String {
    val doc = Jsoup.parse(html)
    doc.select("script, style").remove()
    val body = doc.body() ?: return ""

    body.select("br").forEach { element ->
        element.after(TextNode("\n"))
    }
    body.select(
        "address, article, aside, blockquote, dd, div, dl, dt, figcaption, figure, footer, " +
            "h1, h2, h3, h4, h5, h6, header, hr, li, main, nav, ol, p, pre, section, table, tr, ul",
    ).forEach { element ->
        element.before(TextNode("\n\n"))
        element.after(TextNode("\n\n"))
    }

    return body.wholeText()
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[\\t\\x0B\\f ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
