package com.soll.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String?,
    val filePath: String,
    val coverPath: String?,
    val totalChapters: Int,
    val currentChapter: Int = 0,
    val currentPosition: Int = 0, // Position within chapter (character offset)
    val lastReadAt: Long = System.currentTimeMillis(),
    val addedAt: Long = System.currentTimeMillis()
)
