package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bot configuration entity for multi-bot support
 */
@Entity(tableName = "bot_configs")
data class BotConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "token")
    val token: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false,

    @ColumnInfo(name = "last_offset")
    val lastOffset: Long = 0,

    @ColumnInfo(name = "bot_username")
    val botUsername: String? = null,

    @ColumnInfo(name = "bot_id")
    val botId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long = System.currentTimeMillis()
)
