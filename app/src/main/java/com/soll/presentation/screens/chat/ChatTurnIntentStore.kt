package com.soll.presentation.screens.chat

import androidx.lifecycle.SavedStateHandle
import java.util.UUID

internal data class PendingChatTurn(
    val clientTurnId: String,
    val encryptionNonceSeed: String,
    val content: String,
    val sessionId: String,
)

internal class ChatTurnIntentStore(
    private val savedStateHandle: SavedStateHandle,
    private val idFactory: () -> String = {
        "android-chat:${UUID.randomUUID()}"
    },
    private val nonceSeedFactory: () -> String = {
        "android-chat-nonce:${UUID.randomUUID()}"
    },
) {
    fun restore(): PendingChatTurn? {
        val clientTurnId = savedStateHandle.get<String>(KEY_CLIENT_TURN_ID)
            ?.trim()
            ?.takeIf(::isValidClientTurnId)
            ?: return null
        val content = savedStateHandle.get<String>(KEY_CONTENT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val encryptionNonceSeed = savedStateHandle.get<String>(KEY_ENCRYPTION_NONCE_SEED)
            ?.trim()
            ?.takeIf(::isValidNonceSeed)
            ?: return null
        val sessionId = savedStateHandle.get<String>(KEY_SESSION_ID)
            ?.trim()
            ?.ifBlank { DEFAULT_SESSION_ID }
            ?: DEFAULT_SESSION_ID
        return PendingChatTurn(
            clientTurnId = clientTurnId,
            encryptionNonceSeed = encryptionNonceSeed,
            content = content,
            sessionId = sessionId,
        )
    }

    fun resolve(content: String, sessionId: String): PendingChatTurn {
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank()) { "Сообщение пустое" }
        val cleanSessionId = sessionId.trim().ifBlank { DEFAULT_SESSION_ID }
        restore()?.takeIf {
            it.content == cleanContent && it.sessionId == cleanSessionId
        }?.let { return it }

        val clientTurnId = idFactory().trim()
        require(isValidClientTurnId(clientTurnId)) { "Некорректный client_turn_id" }
        val encryptionNonceSeed = nonceSeedFactory().trim()
        require(isValidNonceSeed(encryptionNonceSeed)) { "Некорректный nonce seed" }
        return PendingChatTurn(
            clientTurnId = clientTurnId,
            encryptionNonceSeed = encryptionNonceSeed,
            content = cleanContent,
            sessionId = cleanSessionId,
        ).also(::save)
    }

    fun invalidateIfContentChanged(content: String) {
        val pending = restore() ?: return
        if (pending.content != content.trim()) clear()
    }

    fun complete(clientTurnId: String) {
        if (restore()?.clientTurnId == clientTurnId) clear()
    }

    private fun save(turn: PendingChatTurn) {
        savedStateHandle[KEY_CLIENT_TURN_ID] = turn.clientTurnId
        savedStateHandle[KEY_ENCRYPTION_NONCE_SEED] = turn.encryptionNonceSeed
        savedStateHandle[KEY_CONTENT] = turn.content
        savedStateHandle[KEY_SESSION_ID] = turn.sessionId
    }

    private fun clear() {
        savedStateHandle.remove<String>(KEY_CLIENT_TURN_ID)
        savedStateHandle.remove<String>(KEY_ENCRYPTION_NONCE_SEED)
        savedStateHandle.remove<String>(KEY_CONTENT)
        savedStateHandle.remove<String>(KEY_SESSION_ID)
    }

    private companion object {
        const val DEFAULT_SESSION_ID = "soll-main"
        const val KEY_CLIENT_TURN_ID = "chat.pending.client_turn_id"
        const val KEY_ENCRYPTION_NONCE_SEED = "chat.pending.encryption_nonce_seed"
        const val KEY_CONTENT = "chat.pending.content"
        const val KEY_SESSION_ID = "chat.pending.session_id"

        fun isValidClientTurnId(value: String): Boolean =
            value.length in 1..128 && value.all { character ->
                character in 'A'..'Z' ||
                    character in 'a'..'z' ||
                    character in '0'..'9' ||
                    character in setOf('_', '.', ':', '-')
            }

        fun isValidNonceSeed(value: String): Boolean = value.length in 16..128
    }
}
