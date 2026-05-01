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

    suspend fun importBook(uri: Uri): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            // Parse the EPUB to get metadata
            val epubBook = epubParser.parseEpub(uri)
                ?: return@withContext Result.failure(Exception("Failed to parse EPUB file"))

            // Copy file to internal storage
            val fileName = "book_${System.currentTimeMillis()}.epub"
            val bookFile = File(booksDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
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
