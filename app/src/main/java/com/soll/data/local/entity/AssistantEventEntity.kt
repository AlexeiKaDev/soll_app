package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.assistant.AssistantEvent

@Entity(
    tableName = "assistant_events",
    indices = [Index(value = ["created_at"])],
)
data class AssistantEventEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    fun toDomain(): AssistantEvent = AssistantEvent(
        id = id,
        type = type,
        source = source,
        summary = summary,
        payloadJson = payloadJson,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(event: AssistantEvent): AssistantEventEntity = AssistantEventEntity(
            id = event.id,
            type = event.type,
            source = event.source,
            summary = event.summary,
            payloadJson = event.payloadJson,
            createdAt = event.createdAt,
        )
    }
}
