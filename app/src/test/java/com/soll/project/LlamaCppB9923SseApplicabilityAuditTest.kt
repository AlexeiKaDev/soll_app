package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9923SseApplicabilityAuditTest {
    @Test
    fun `b9923 SSE replay changes stay audit only without a Soll runtime seam`() {
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-cf8d73813e57d6bc-verification.md",
        ).readText().normalizeWhitespace()
        val api = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText()
        val repository = projectFile(
            "app/src/main/java/com/soll/data/repository/SollRepository.kt",
        ).readText()
        val modelChat = projectFile(
            "app/src/main/java/com/soll/domain/modelchat/ModelChatModels.kt",
        ).readText()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()
        val activeReleaseSmoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppActiveRelease.ps1",
        ).readText()

        listOf(
            "task_id: bfafbcef4fff4702b3ce03ba57352158",
            "source_ref: source-item/d0cd9479f2a2/cf8d73813e57d6bc",
            "source_processing_result: llama_cpp_b9923_sse_audit_completed_no_runtime_seam",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-cf8d73813e57d6bc-verification.md",
            "source_value: audit_only_no_current_llama_server_sse_regression_surface",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9923",
            "bbebeec4a87355896e3faac0c2baca8130c91b6a",
            "https://github.com/ggml-org/llama.cpp/pull/25047",
            "c6c84644213fa8e9eab84fcb9e2251963988af51",
            "6 files, 180 additions and 193 deletions",
            "No functional change to the resumable stream behavior",
            "145 commits ahead and 0 behind",
            "301 Kotlin/KTS/XML/Java/C/C++/header files",
            "streaming response",
            "reconnect/resume",
            "cancel",
            "session cleanup",
            "bind only to `127.0.0.1`",
            "`0.0.0.0` is prohibited",
            "0 local SSE requests and 0 public listeners",
            "LlamaCppB9923SseApplicabilityAuditTest",
            "`1/1` focused contract test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9923 SSE audit evidence: $evidence", audit.contains(evidence))
        }

        assertTrue(modelChat.contains("LLAMA,"))
        assertTrue(modelChat.contains("Backend-mediated model chat request from Soll Android."))
        assertTrue(api.contains("@POST(\"api/v1/assistant/ask\")"))
        assertTrue(api.contains("suspend fun askAssistant("))
        assertTrue(repository.contains("override suspend fun askModelChat("))
        assertTrue(repository.contains("service().askAssistant("))

        listOf(
            "\"tag\": \"b10068\"",
            "\"packageIntoAndroidApp\": false",
            "\"androidRuntimeDefault\": \"soll-backend-route\"",
        ).forEach { control ->
            assertTrue("Active standalone policy drifted: $control", activeDefaults.contains(control))
        }
        assertTrue(
            "The active release verifier should inspect llama-server without opening a listener",
            activeReleaseSmoke.contains("& \$serverPath.FullName --version"),
        )
        assertFalse(activeReleaseSmoke.contains("--host"))

        val productionFiles = projectFile("app/src/main")
            .walkTopDown()
            .filter { file ->
                file.isFile && file.extension.lowercase() in
                    setOf("kt", "kts", "xml", "java", "c", "cpp", "h")
            }
            .toList()
        assertTrue("Expected the complete Android production tree", productionFiles.size >= 300)
        val productionText = productionFiles.joinToString(separator = "\n") { it.readText() }
        listOf(
            "text/event-stream",
            "EventSource",
            "Last-Event-ID",
            "X-Conversation-Id",
            "/v1/stream",
            "/v1/streams",
            "/v1/chat/completions",
            "llama-server",
        ).forEach { marker ->
            assertFalse(
                "Direct llama.cpp server/SSE marker found in Android production code: $marker",
                productionText.contains(marker, ignoreCase = true),
            )
        }
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
