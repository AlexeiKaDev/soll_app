package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.assistant.memory.AssistantMemory
import com.soll.domain.assistant.memory.AssistantMemoryCategory

@Entity(
    tableName = "assistant_memories",
    indices = [
        Index(value = ["category"]),
        Index(value = ["source"]),
        Index(value = ["updated_at"]),
        Index(value = ["category", "memory_key"], unique = true),
    ],
)
data class AssistantMemoryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "memory_key")
    val memoryKey: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long?,

    @ColumnInfo(name = "pinned")
    val pinned: Boolean,
) {
    fun toDomain(): AssistantMemory = AssistantMemory(
        id = id,
        category = runCatching { AssistantMemoryCategory.valueOf(category) }
            .getOrDefault(AssistantMemoryCategory.SYSTEM),
        key = memoryKey,
        title = title,
        summary = summary,
        source = source,
        confidence = confidence.coerceIn(0f, 1f),
        payloadJson = payloadJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        pinned = pinned,
    )

    companion object {
        fun fromDomain(memory: AssistantMemory): AssistantMemoryEntity = AssistantMemoryEntity(
            id = memory.id,
            category = memory.category.name,
            memoryKey = memory.key,
            title = memory.title,
            summary = memory.summary,
            source = memory.source,
            confidence = memory.confidence.coerceIn(0f, 1f),
            payloadJson = memory.payloadJson,
            createdAt = memory.createdAt,
            updatedAt = memory.updatedAt,
            lastUsedAt = memory.lastUsedAt,
            pinned = memory.pinned,
        )
    }
}
