package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10016IntegrationDecisionTest {
    @Test
    fun `b10016 is placed behind the backend route and gated by a Battlemage benchmark`() {
        val wiki = projectFile("wiki/b10016.md").readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-daef5184e7584a3d9fda5cc178689b53-llama-cpp-b10016-audit.md",
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

        listOf(
            "monitored/llama-cpp-releases/20260716-023002-b10016-20163f78.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10016",
            "32b741c336decea914e4c1c24a9c9815485901b2",
            "12127defda4f41b7679cb2477a4b0d65ee6a0c8f",
            "https://github.com/ggml-org/llama.cpp/pull/25222",
            "пять файлов только в `ggml/src/ggml-sycl`: `298` добавлений и `1` удаление",
            "Battlemage/Xe2 (`BMG-G21` или `BMG-G31`)",
            "`GGML_SYCL_FA_ONEDNN=1`",
            "существующему TILE kernel",
            "на `52` commits впереди `b10016`, на `0` commits позади",
            "`POST api/v1/chat/turn`",
            "`soll-backend-route`",
            "server-side inference host",
            "`Test-LlamaCppSyclBattlemageFlashAttention.ps1`",
            "`approved_models.json` использует `deny_unlisted` и содержит `models: []`",
            "## Ворота будущего измеримого benchmark",
            "Определены `6` ворот будущего измеримого Battlemage benchmark",
            "runtime-ценность для Soll app остаётся `0`",
        ).forEach { control ->
            assertTrue("Missing b10016 integration control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: daef5184e7584a3d9fda5cc178689b53",
            "source_ref: insight/6027b57155f5",
            "source_processing_result: integration_defined_benchmark_deferred_no_current_sycl_battlemage_seam",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-daef5184e7584a3d9fda5cc178689b53-llama-cpp-b10016-audit.md",
            "1 wiki integration decision added",
            "3 official upstream surfaces and 5 current Soll seams audited",
            "b10068 verified 52 commits ahead of b10016",
            "6 benchmark gates defined",
            "0 current SYCL/Battlemage integration matches, 0 production/runtime changes, and 0 local b10016 inference runs",
            "server-side inference layer behind `POST api/v1/chat/turn`",
            "LlamaCppB10016IntegrationDecisionTest",
            "`1/1` focused test passed with `0` failures, `0` errors and `0` skipped tests",
        ).forEach { evidence ->
            assertTrue("Missing b10016 audit evidence: $evidence", audit.contains(evidence))
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
            "Active manifest must not claim an unverified SYCL target",
            !activeDefaults.contains("\"backend\": \"sycl\""),
        )
        assertTrue(
            "Model provenance gate must stay deny-by-default and scoped to the test fixture",
            approvedModels.contains("\"policy\": \"deny_unlisted\"") &&
                approvedModels.contains("\"purpose\": \"b9945-chat-template-smoke-only\""),
        )
        assertTrue(
            "Android chat must stay behind the Soll backend contract",
            api.contains("@POST(\"api/v1/chat/turn\")") &&
                api.contains("suspend fun sendChatTurn("),
        )
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
