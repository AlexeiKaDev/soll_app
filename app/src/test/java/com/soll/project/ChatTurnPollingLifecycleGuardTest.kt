package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTurnPollingLifecycleGuardTest {
    @Test
    fun `chat and voice poll jobs remain lifecycle cancelled`() {
        val chat = projectFile("app/src/main/java/com/soll/presentation/screens/chat/ChatViewModel.kt").readText()
        val voice = projectFile("app/src/main/java/com/soll/presentation/screens/voice/VoiceViewModel.kt").readText()
        val repository = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()

        assertTrue(chat.contains("pendingTurnStatusJobs.values.forEach(Job::cancel)"))
        assertTrue(chat.contains("pendingTurnStatusJobs.clear()"))
        assertTrue(voice.contains("replyWaitJob?.cancel()"))
        assertTrue(voice.contains("VOICE_EXACT_REPLY_POLL_ATTEMPTS = 90"))
        assertTrue(voice.contains("VOICE_LEGACY_REPLY_POLL_ATTEMPTS = 23"))

        val sendTurn = repository
            .substringAfter("override suspend fun sendChatTurn(")
            .substringBefore("override suspend fun getChatTurnStatus(")
        val getTurn = repository
            .substringAfter("override suspend fun getChatTurnStatus(")
            .substringBefore("override suspend fun synthesizeVoice(")
        assertTrue(sendTurn.contains("authorization = refreshAwareReadAuthorizationHeader()"))
        assertTrue(sendTurn.contains("nonceSeed = encryptionNonceSeed"))
        assertTrue(getTurn.contains("authorization = refreshAwareReadAuthorizationHeader()"))
    }

    private fun projectFile(relativePath: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, relativePath)
            if (candidate.isFile) return candidate
            current = current.parentFile ?: error("Project root not found for: $relativePath")
        }
        error("Project file not found: $relativePath")
    }
}
