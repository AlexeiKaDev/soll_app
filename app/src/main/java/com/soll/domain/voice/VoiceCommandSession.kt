package com.soll.domain.voice

import java.util.UUID

data class VoiceCommandSession(
    val id: String = UUID.randomUUID().toString(),
    val startedAt: Long = System.currentTimeMillis(),
    val status: VoiceCommandSessionStatus = VoiceCommandSessionStatus.LISTENING,
    val recognizedText: String = "",
    val responseText: String = "",
    val errorMessage: String? = null,
    val finishedAt: Long? = null,
) {
    fun processing(text: String): VoiceCommandSession =
        copy(
            status = VoiceCommandSessionStatus.PROCESSING,
            recognizedText = text,
            errorMessage = null,
        )

    fun completed(response: String): VoiceCommandSession =
        copy(
            status = VoiceCommandSessionStatus.COMPLETED,
            responseText = response,
            errorMessage = null,
            finishedAt = System.currentTimeMillis(),
        )

    fun failed(message: String): VoiceCommandSession =
        copy(
            status = VoiceCommandSessionStatus.FAILED,
            errorMessage = message,
            finishedAt = System.currentTimeMillis(),
        )

    fun cancelled(): VoiceCommandSession =
        copy(
            status = VoiceCommandSessionStatus.CANCELLED,
            finishedAt = System.currentTimeMillis(),
        )
}

enum class VoiceCommandSessionStatus {
    LISTENING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
