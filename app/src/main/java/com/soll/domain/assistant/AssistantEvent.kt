package com.soll.domain.assistant

import java.util.UUID

data class AssistantEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val source: String,
    val summary: String,
    val payloadJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

interface AssistantEventLogger {
    suspend fun logEvent(event: AssistantEvent)
}
