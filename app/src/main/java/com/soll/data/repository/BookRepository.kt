package com.soll.data.repository

import android.content.Context
import android.net.Uri
import com.soll.data.local.dao.BookDao
import com.soll.data.local.dao.BookAnnotationDao
import com.soll.data.local.entity.BookEntity
import com.soll.data.local.entity.BookAnnotationEntity
import com.soll.domain.epub.EpubBook
import com.soll.domain.epub.EpubParser
import com.soll.domain.soll.SollManagedBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val bookAnnotationDao: BookAnnotationDao,
) {
    private val epubParser = EpubParser(context)
    private val booksDir = File(context.filesDir, "books")
    private val coversDir = File(context.filesDir, "covers")

    init {
        booksDir.mkdirs()
        coversDir.mkdirs()
    }

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    suspend fun getAnnotations(bookId: Long): List<BookAnnotationEntity> =
        bookAnnotationDao.getForBook(bookId)

    suspend fun saveAnnotation(annotation: BookAnnotationEntity) =
        bookAnnotationDao.upsert(annotation)

    suspend fun deleteAnnotation(annotation: BookAnnotationEntity) =
        bookAnnotationDao.delete(annotation)

    suspend fun getAllBooksSnapshot(): List<BookEntity> = bookDao.getAllBooksSnapshot()

    suspend fun reconcileManagedDuplicates(): Int = withContext(Dispatchers.IO) {
        var removed = 0
        bookDao.getAllBooksSnapshot()
            .mapNotNull { book -> managedBookId(book)?.let { it to book } }
            .groupBy({ it.first }, { it.second })
            .values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                val keeper = duplicates.minBy { it.id }
                val progress = duplicates.maxWithOrNull(
                    compareBy<BookEntity> { it.lastReadAt }
                        .thenBy { it.currentChapter }
                        .thenBy { it.currentPosition }
                ) ?: keeper
                bookDao.updateBook(keeper.copy(
                    currentChapter = progress.currentChapter,
                    currentPosition = progress.currentPosition,
                    lastReadAt = progress.lastReadAt,
                    addedAt = duplicates.minOf { it.addedAt },
                ))
                duplicates.filterNot { it.id == keeper.id }.forEach {
                    bookDao.deleteBookById(it.id)
                    removed += 1
                }
            }
        removed
    }

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun hasManagedBook(remoteId: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(booksDir, "managed_${remoteId.lowercase()}.epub")
        bookDao.getBookByFilePath(target.absolutePath) != null && target.isFile
    }

    fun managedBookId(book: BookEntity): String? =
        Regex("^managed_([0-9a-f]{32})\\.epub$")
            .matchEntire(File(book.filePath).name.lowercase())?.groupValues?.get(1)

    suspend fun readBookBytes(book: BookEntity): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(book.filePath)
            require(source.isFile && source.length() in 1..MAX_MANAGED_EPUB_BYTES) {
                "EPUB отсутствует или имеет недопустимый размер"
            }
            source.readBytes()
        }
    }

    suspend fun adoptManagedIdentity(book: BookEntity, remote: SollManagedBook): Result<BookEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(remote.id.matches(Regex("[0-9a-f]{32}"))) { "Некорректный ID книги" }
                val source = File(book.filePath)
                require(source.isFile) { "Локальный EPUB отсутствует" }
                val digest = source.inputStream().use { input ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) md.update(buffer, 0, count)
                    }
                    md.digest().joinToString("") { "%02x".format(it) }
                }
                require(digest == remote.sha256 && digest.startsWith(remote.id)) {
                    "Сервер вернул другую контрольную сумму EPUB"
                }
                val target = File(booksDir, "managed_${remote.id}.epub")
                val existing = bookDao.getBookByFilePath(target.absolutePath)
                if (existing != null && existing.id != book.id) {
                    val preferred = listOf(book, existing).maxWithOrNull(
                        compareBy<BookEntity> { it.lastReadAt }
                            .thenBy { it.currentChapter }
                            .thenBy { it.currentPosition }
                    ) ?: existing
                    val merged = existing.copy(
                        title = remote.title.ifBlank { preferred.title },
                        author = remote.author.ifBlank { preferred.author.orEmpty() },
                        currentChapter = preferred.currentChapter,
                        currentPosition = preferred.currentPosition,
                        lastReadAt = preferred.lastReadAt,
                        addedAt = minOf(book.addedAt, existing.addedAt),
                    )
                    bookDao.updateBook(merged)
                    bookDao.deleteBookById(book.id)
                    if (source.absolutePath != target.absolutePath) source.delete()
                    return@runCatching merged
                }
                if (source.absolutePath != target.absolutePath) {
                    val temporary = File(booksDir, ".${target.name}.adopt")
                    source.inputStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    check(!target.exists() || target.delete()) { "Не удалось заменить управляемый EPUB" }
                    check(temporary.renameTo(target)) { "Не удалось принять управляемый EPUB" }
                }
                val rowsSharingSource = bookDao.getBooksByFilePath(source.absolutePath)
                val preferred = (rowsSharingSource + book).distinctBy { it.id }.maxWithOrNull(
                    compareBy<BookEntity> { it.lastReadAt }
                        .thenBy { it.currentChapter }
                        .thenBy { it.currentPosition }
                ) ?: book
                val updated = book.copy(
                    title = remote.title.ifBlank { preferred.title },
                    author = remote.author.ifBlank { preferred.author.orEmpty() },
                    filePath = target.absolutePath,
                    currentChapter = preferred.currentChapter,
                    currentPosition = preferred.currentPosition,
                    lastReadAt = preferred.lastReadAt,
                    addedAt = (rowsSharingSource + book).minOf { it.addedAt },
                )
                bookDao.updateBook(updated)
                rowsSharingSource.filterNot { it.id == book.id }.forEach {
                    bookDao.deleteBookById(it.id)
                }
                if (source.absolutePath != target.absolutePath) source.delete()
                updated
            }
        }

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

    suspend fun importManagedBook(book: SollManagedBook, bytes: ByteArray): Result<BookEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(book.id.matches(Regex("[0-9a-f]{32}"))) { "Некорректный ID книги" }
                require(bytes.isNotEmpty() && bytes.size <= 200 * 1024 * 1024) { "Некорректный размер EPUB" }
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes).joinToString("") { "%02x".format(it) }
                require(digest == book.sha256 && digest.startsWith(book.id)) {
                    "Контрольная сумма EPUB не совпадает с каталогом Soll"
                }
                val target = File(booksDir, "managed_${book.id}.epub")
                val existing = bookDao.getBookByFilePath(target.absolutePath)
                if (existing != null && target.isFile) return@runCatching existing
                val temporary = File(booksDir, ".${target.name}.tmp")
                var targetCreated = false
                try {
                    FileOutputStream(temporary).use { output ->
                        output.write(bytes)
                        output.fd.sync()
                    }
                    check(!target.exists()) { "EPUB с таким ID уже существует без записи каталога" }
                    check(temporary.renameTo(target)) { "Не удалось атомарно сохранить EPUB" }
                    targetCreated = true
                    val parsed = epubParser.parseEpub(target.absolutePath)
                        ?: error("Не удалось разобрать EPUB-файл")
                    var coverPath: String? = null
                    parsed.coverData?.let { cover ->
                        val coverFile = File(coversDir, "managed_${book.id}.jpg")
                        FileOutputStream(coverFile).use { it.write(cover) }
                        coverPath = coverFile.absolutePath
                    }
                    if (existing != null) {
                        existing.copy(
                            title = book.title.ifBlank { parsed.title },
                            author = book.author.ifBlank { parsed.author.orEmpty() },
                            coverPath = coverPath ?: existing.coverPath,
                            totalChapters = parsed.chapters.size,
                        ).also { bookDao.updateBook(it) }
                    } else {
                        val entity = BookEntity(
                            title = book.title.ifBlank { parsed.title },
                            author = book.author.ifBlank { parsed.author.orEmpty() },
                            filePath = target.absolutePath,
                            coverPath = coverPath,
                            totalChapters = parsed.chapters.size,
                        )
                        entity.copy(id = bookDao.insertBook(entity))
                    }
                } catch (error: Exception) {
                    temporary.delete()
                    if (targetCreated) target.delete()
                    throw error
                }
            }
        }
}

private const val MAX_MANAGED_EPUB_BYTES = 200L * 1024L * 1024L

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
