package com.soll.data.repository

import android.content.Context
import android.net.Uri
import com.soll.data.local.dao.BookDao
import com.soll.data.local.entity.BookEntity
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao
) {
    private val epubParser = EpubParser(context)
    private val booksDir = File(context.filesDir, "books")
    private val coversDir = File(context.filesDir, "covers")

    init {
        booksDir.mkdirs()
        coversDir.mkdirs()
    }

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun getLastReadWidgetState(): ReaderWidgetBookState? = withContext(Dispatchers.IO) {
        val book = bookDao.getLastReadBook() ?: return@withContext null
        val epub = epubParser.parseEpub(book.filePath)
        val chapter = epub?.chapters?.getOrNull(
            book.currentChapter.coerceIn(0, max((epub.chapters.size - 1), 0)),
        )
        val excerpt = chapter?.content
            ?.let { extractReaderWidgetExcerpt(it, book.currentPosition) }
            .orEmpty()
        val fallback = chapter?.title
            ?.takeIf { it.isNotBlank() }
            ?: if (book.totalChapters > 0) {
                "Глава ${(book.currentChapter + 1).coerceAtLeast(1)} / ${book.totalChapters}"
            } else {
                "Откройте книгу"
            }

        ReaderWidgetBookState(
            title = book.title,
            subtitle = excerpt.ifBlank { fallback },
            coverPath = book.coverPath,
        )
    }

    suspend fun importBook(uri: Uri): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            // Parse the EPUB to get metadata
            val epubBook = epubParser.parseEpub(uri)
                ?: return@withContext Result.failure(Exception("Не удалось разобрать EPUB-файл"))

            // Copy file to internal storage
            val fileName = "book_${System.currentTimeMillis()}.epub"
            val bookFile = File(booksDir, fileName)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Не удалось открыть EPUB-файл"))
            inputStream.use { input ->
                FileOutputStream(bookFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Save cover if available
            var coverPath: String? = null
            epubBook.coverData?.let { coverData ->
                val coverFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                FileOutputStream(coverFile).use { output ->
                    output.write(coverData)
                }
                coverPath = coverFile.absolutePath
            }

            // Create database entry
            val bookEntity = BookEntity(
                title = epubBook.title,
                author = epubBook.author,
                filePath = bookFile.absolutePath,
                coverPath = coverPath,
                totalChapters = epubBook.chapters.size
            )

            val id = bookDao.insertBook(bookEntity)
            Result.success(bookEntity.copy(id = id))
        } catch (e: Exception) {
            Timber.e(e, "Error importing book")
            Result.failure(e)
        }
    }

    suspend fun parseBook(bookEntity: BookEntity): EpubBook? = withContext(Dispatchers.IO) {
        epubParser.parseEpub(bookEntity.filePath)
    }

    suspend fun updateReadingProgress(bookId: Long, chapter: Int, position: Int) {
        bookDao.updateReadingProgress(bookId, chapter, position)
    }

    suspend fun deleteBook(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId)
        book?.let {
            // Delete files
            File(it.filePath).delete()
            it.coverPath?.let { coverPath -> File(coverPath).delete() }
            // Delete from database
            bookDao.deleteBookById(bookId)
        }
    }
}

data class ReaderWidgetBookState(
    val title: String,
    val subtitle: String,
    val coverPath: String?,
)

fun extractReaderWidgetExcerpt(
    content: String,
    position: Int,
    maxLength: Int = 140,
): String {
    val text = content.replace("\r\n", "\n").replace('\r', '\n')
    if (text.isBlank()) return ""
    val safePosition = position.coerceIn(0, text.length)

    val paragraph = paragraphAround(text, safePosition)
        .takeIf { it.replace(Regex("\\s+"), " ").trim().length >= 24 }
        ?: sentenceWindowAround(text, safePosition)

    return paragraph.toCompactExcerpt(maxLength)
}

private fun paragraphAround(content: String, position: Int): String {
    val searchPosition = position.coerceIn(0, content.length)
    val start = content.lastIndexOf('\n', (searchPosition - 1).coerceAtLeast(0))
        .let { if (it >= 0) it + 1 else 0 }
    val end = content.indexOf('\n', searchPosition)
        .let { if (it >= 0) it else content.length }
    return content.substring(start, end)
}

private fun sentenceWindowAround(content: String, position: Int): String {
    val safePosition = position.coerceIn(0, content.length)
    val punctuation = charArrayOf('.', '!', '?', '…')
    val previousBoundary = punctuation
        .map { content.lastIndexOf(it, (safePosition - 1).coerceAtLeast(0)) }
        .maxOrNull()
        ?.takeIf { it >= 0 && safePosition - it <= 220 }
    val nextBoundary = content.indexOfAny(punctuation, safePosition)
        .takeIf { it >= 0 && it - safePosition <= 260 }

    val start = previousBoundary?.plus(1) ?: (safePosition - 110).coerceAtLeast(0)
    val end = nextBoundary?.plus(1) ?: (safePosition + 180).coerceAtMost(content.length)
    if (start >= end) return content
    return content.substring(start, end)
}

private fun String.toCompactExcerpt(maxLength: Int): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    if (compact.length <= maxLength) return compact
    return compact
        .take(maxLength)
        .trimEnd(' ', ',', ';', ':', '-', '—')
        .plus("…")
}
