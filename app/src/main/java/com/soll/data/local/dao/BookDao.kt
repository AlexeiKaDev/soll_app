package com.soll.data.local.dao

import androidx.room.*
import com.soll.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE filePath = :filePath")
    suspend fun getBookByFilePath(filePath: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("UPDATE books SET currentChapter = :chapter, currentPosition = :position, lastReadAt = :lastReadAt WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: Long, chapter: Int, position: Int, lastReadAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)
}
