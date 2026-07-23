package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9982LocalApplicabilityTest {
    @Test
    fun `b9982 is documented as an included server fix without a current Android seam`() {
        val wiki = projectFile("wiki/b9982.md").readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-5a30dadc1edb4ce8b7d0c28d256a5f93-llama-cpp-b9982-audit.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText().normalizeWhitespace()
        val approvedModels = projectFile(
            "tools/llama-cpp/approved_models.json",
        ).readText().normalizeWhitespace()
        val api = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText().normalizeWhitespace()
        val chatTurnRequest = api.substringAfter("data class ChatTurnRequest(")
            .substringBefore("data class ChatTurnResponse(")

        listOf(
            "monitored/llama-cpp-releases/20260714-013002-b9982-b22d8508.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9982",
            "99f3dc32296f825fec94f202da1e9fede1e78cf9",
            "34558825a27f4d74dcfd7a91bfde4464baa2a30a",
            "https://github.com/ggml-org/llama.cpp/pull/23116",
            "`tools/server/server-common.cpp` и `tests/test-chat.cpp` (`70` добавлений, `2` удаления)",
            "`reasoning_budget_tokens` читается первым",
            "`reasoning_budget_message` читается из request body",
            "включая `0` для подавления thinking",
            "`POST api/v1/chat/turn`",
            "`soll-backend-route` default",
            "на `86` commits впереди `b9982`, на `0` commits позади",
            "`ChatTurnRequest` не содержит три reasoning-budget поля",
            "единственный tiny fixture разрешён только для b9945/b9947 smoke",
            "## Ворота будущего измеримого smoke",
            "Определены `6` ворот будущего focused smoke",
            "Выполнено `0` локальных b9982 chat-completion requests",
        ).forEach { control ->
            assertTrue("Missing b9982 applicability control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: 5a30dadc1edb4ce8b7d0c28d256a5f93",
            "source_ref: insight/5caf87d9fb56",
            "source_processing_result: local_applicability_documented_no_current_reasoning_budget_execution_seam",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-5a30dadc1edb4ce8b7d0c28d256a5f93-llama-cpp-b9982-audit.md",
            "1 wiki local-applicability evaluation added",
            "3 official upstream surfaces and 5 current Soll seams audited",
            "b10068 verified 86 commits ahead of b9982",
            "6 smoke gates defined",
            "0 production/runtime changes and 0 local b9982 chat-completion requests",
            "no Android or runtime change is warranted now",
            "LlamaCppB9982LocalApplicabilityTest",
            "`1/1` focused test passed with `0` failures, `0` errors and `0` skipped tests",
        ).forEach { evidence ->
            assertTrue("Missing b9982 audit evidence: $evidence", audit.contains(evidence))
        }

        listOf(
            "\"tag\": \"b10068\"",
            "\"commit\": \"571d0d540df04f25298d0e159e520d9fc62ed121\"",
            "\"packageIntoAndroidApp\": false",
            "\"androidRuntimeDefault\": \"soll-backend-route\"",
        ).forEach { control ->
            assertTrue("Active standalone policy drifted: $control", activeDefaults.contains(control))
        }
        assertTrue(
            "Model provenance gate must stay deny-by-default and scoped to existing smokes",
            approvedModels.contains("\"policy\": \"deny_unlisted\"") &&
                approvedModels.contains("\"purpose\": \"b9945-chat-template-smoke-only\"") &&
                !approvedModels.contains("b9982"),
        )
        assertTrue(
            "Android chat must stay behind the Soll backend contract",
            api.contains("@POST(\"api/v1/chat/turn\")") &&
                api.contains("suspend fun sendChatTurn("),
        )
        listOf(
            "reasoning_budget_tokens",
            "thinking_budget_tokens",
            "reasoning_budget_message",
        ).forEach { unsupportedField ->
            assertFalse(
                "Android ChatTurnRequest unexpectedly exposes $unsupportedField",
                chatTurnRequest.contains(unsupportedField),
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
