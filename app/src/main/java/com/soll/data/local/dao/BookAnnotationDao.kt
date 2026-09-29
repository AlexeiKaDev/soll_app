package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.BookAnnotationEntity

@Dao
interface BookAnnotationDao {
    @Query("SELECT * FROM book_annotations WHERE bookId = :bookId ORDER BY chapterIndex, startOffset, createdAt")
    suspend fun getForBook(bookId: Long): List<BookAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: BookAnnotationEntity)

    @Delete
    suspend fun delete(annotation: BookAnnotationEntity)
}
