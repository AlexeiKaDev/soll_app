package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RumbaRussianMemoryBenchmarkIntegrationReviewTest {
    @Test
    fun `RUMBA is retained as an offline memory evaluation blueprint without runtime wiring`() {
        val wiki = projectFile("wiki/rumba-russkoyazychnyy.md")
            .readText()
            .normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-092df8f4d66143d0a402c29aa74155cc-rumba-integration-audit.md",
        ).readText().normalizeWhitespace()
        val memoryModel = projectFile(
            "app/src/main/java/com/soll/domain/assistant/memory/AssistantMemory.kt",
        ).readText().normalizeWhitespace()
        val memoryRepository = projectFile(
            "app/src/main/java/com/soll/data/repository/AssistantMemoryRepository.kt",
        ).readText().normalizeWhitespace()
        val memoryDao = projectFile(
            "app/src/main/java/com/soll/data/local/dao/AssistantMemoryDao.kt",
        ).readText().normalizeWhitespace()
        val api = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText().normalizeWhitespace()
        val chatTurnRequest = api
            .substringAfter("data class ChatTurnRequest(")
            .substringBefore("data class ChatTurnResponse(")
        val productionSource = projectFile("app/src/main")
            .walkTopDown()
            .filter(File::isFile)
            .joinToString(separator = "\n") { it.readText() }
        val buildFiles = listOf(
            projectFile("build.gradle.kts"),
            projectFile("app/build.gradle.kts"),
            projectFile("settings.gradle.kts"),
        ).joinToString(separator = "\n") { it.readText() }

        listOf(
            "Source signal **валидирован и релевантен**",
            "**conditional offline evaluation candidate**",
            "monitored/habr-sber-company/20260725-003006-rumba-b0e3fd2f.md",
            "https://arxiv.org/abs/2607.21447",
            "https://github.com/ai-forever/RUMBA",
            "https://huggingface.co/datasets/ai-forever/RUMBA",
            "`85` user IDs и около `1.54k` QA rows",
            "Extraction, Reasoning, Abstention",
            "`UpdatingInfo`, `DeleteInfo` и `Abstention`",
            "semantic type, session scope, temporality и temporal expression",
            "## Проверенные seam Soll app",
            "## Будущий измеримый offline smoke",
            "семь ворот",
            "Импортировано `0` dataset rows; выполнено `0` benchmark/model runs",
            "Изменено `0` production/runtime файлов",
        ).forEach { control ->
            assertTrue("Missing RUMBA integration control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: 092df8f4d66143d0a402c29aa74155cc",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/e348746d9311",
            "source_processing_result: validated_relevant_offline_eval_blueprint_runtime_integration_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-092df8f4d66143d0a402c29aa74155cc-rumba-integration-audit.md",
            "1 wiki integration review added",
            "3 primary upstream surfaces and 6 current Soll memory seams audited",
            "4 diagnostic axes and 7 measurable promotion gates defined",
            "1/1 focused contract test passed",
            "0 dataset rows imported, 0 benchmark/model runs and 0 production/runtime changes",
            "RumbaRussianMemoryBenchmarkIntegrationReviewTest",
            "`1/1` focused test passed with `0` failures, `0` errors and `0` skipped tests",
        ).forEach { evidence ->
            assertTrue("Missing RUMBA audit evidence: $evidence", audit.contains(evidence))
        }

        listOf(
            "val source: String",
            "val confidence: Float",
            "val createdAt: Long",
            "val updatedAt: Long",
            "val lastUsedAt: Long?",
            "val pinned: Boolean",
            "Сырые логи, payload JSON и медиа не включены",
        ).forEach { currentField ->
            assertTrue("Current memory contract drifted: $currentField", memoryModel.contains(currentField))
        }

        listOf(
            "if (!settingsRepository.assistantMemoryEnabled) return",
            "suspend fun rememberAcceptedSuggestion(",
            "fun observeRecent(limit: Int = 100)",
            "suspend fun delete(id: String)",
            "suspend fun deleteAll()",
        ).forEach { currentContract ->
            assertTrue(
                "Current memory repository contract drifted: $currentContract",
                memoryRepository.contains(currentContract),
            )
        }
        assertFalse(memoryRepository.contains("sendChatTurn("))

        listOf(
            "ORDER BY pinned DESC, updated_at DESC LIMIT :limit",
            "suspend fun getAllForExport()",
            "suspend fun deleteById(id: String)",
        ).forEach { daoContract ->
            assertTrue("Current memory DAO contract drifted: $daoContract", memoryDao.contains(daoContract))
        }
        assertFalse(memoryDao.lowercase().contains("semantic"))
        assertFalse(memoryDao.lowercase().contains("query_date"))

        assertTrue(api.contains("@POST(\"api/v1/chat/turn\")"))
        assertTrue(api.contains("suspend fun sendChatTurn("))
        listOf(
            "val sessionId: String? = null",
            "val content: String? = null",
            "val metadata: Map<String, Any?>? = null",
        ).forEach { chatField ->
            assertTrue("Chat turn contract drifted: $chatField", chatTurnRequest.contains(chatField))
        }
        assertFalse(chatTurnRequest.lowercase().contains("memory"))

        assertFalse(productionSource.contains("RUMBA"))
        assertFalse(productionSource.contains("2607.21447"))
        assertFalse(buildFiles.lowercase().contains("lighteval"))
        assertFalse(buildFiles.lowercase().contains("ai-forever/rumba"))
    }

    private fun String.normalizeWhitespace(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }
}
