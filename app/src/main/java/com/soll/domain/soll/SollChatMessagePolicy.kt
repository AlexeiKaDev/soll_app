package com.soll.domain.soll

/**
 * Routine scheduler success records are machine telemetry, not a second
 * conversation with the user. Warnings and failures intentionally remain
 * visible in chat and eligible for a system notification.
 */
internal fun SollChatMessage.isRoutineSchedulerTelemetry(): Boolean {
    val extra = metadata["extra"] as? Map<*, *>
    val entityType = metadata.stringValue("entity_type")
        .ifBlank { extra.stringValue("entity_type") }
    if (entityType != "scheduler_task") return false

    val eventType = metadata.stringValue("event_type")
        .ifBlank { extra.stringValue("event_type") }
    val status = metadata.stringValue("status")
        .ifBlank { extra.stringValue("status") }
        .ifBlank { eventType.removePrefix("scheduler_") }
    return status in setOf("success", "neutral") ||
        eventType in setOf("scheduler_success", "scheduler_neutral")
}

private fun Map<*, *>?.stringValue(key: String): String =
    this?.get(key)?.toString()?.trim()?.lowercase().orEmpty()
