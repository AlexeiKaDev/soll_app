package com.soll.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_annotations",
    indices = [Index(value = ["bookId", "chapterIndex", "startOffset"])],
)
data class BookAnnotationEntity(
    @PrimaryKey val id: String,
    val bookId: Long,
    val kind: String,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val selectedText: String,
    val noteText: String = "",
    val color: String = "yellow",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
