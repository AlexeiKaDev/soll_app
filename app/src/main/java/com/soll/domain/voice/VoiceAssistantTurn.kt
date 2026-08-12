package com.soll.domain.voice

data class VoiceAssistantTurn(
    val content: String,
    val requestId: String,
    val sessionId: String = DEFAULT_VOICE_SESSION_ID,
    val runAssistant: Boolean = true,
    val taskIntake: Boolean = false,
    val allowActions: Boolean = false,
) {
    val metadata: Map<String, Any?> = mapOf(
        "source" to "android_voice",
        "input_mode" to "push_to_talk",
        "request_id" to requestId,
        "locale" to "ru-RU",
        "safety_mode" to "read_only",
    )

    companion object {
        fun create(
            transcript: String,
            requestId: String,
            sessionId: String = DEFAULT_VOICE_SESSION_ID,
        ): VoiceAssistantTurn {
            val content = transcript
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(MAX_VOICE_TURN_CHARS)
            require(content.isNotBlank()) { "Голосовой запрос пуст" }
            require(requestId.isNotBlank()) { "ID голосового запроса не задан" }
            return VoiceAssistantTurn(
                content = content,
                requestId = requestId,
                sessionId = sessionId.trim().ifBlank { DEFAULT_VOICE_SESSION_ID },
            )
        }
    }
}

internal const val MAX_VOICE_TURN_CHARS = 4_000
private const val DEFAULT_VOICE_SESSION_ID = "soll-main"
