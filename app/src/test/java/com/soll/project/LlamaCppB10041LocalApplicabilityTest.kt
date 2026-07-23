package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10041LocalApplicabilityTest {
    @Test
    fun `b10041 is documented as an included log hygiene fix without a current CORS seam`() {
        val wiki = projectFile("wiki/b10041.md").readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-4ab630a0f9c24b1a9b9aa894708d2b50-llama-cpp-b10041-audit.md",
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
            "monitored/llama-cpp-releases/20260717-023007-b10041-9ab862fa.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10041",
            "8ee54c8b32a1b0cf13c03fc5723142bc62c775f6",
            "c7d8722922a2599dc4d77f8808d8e6c2fde5e7a2",
            "https://github.com/ggml-org/llama.cpp/pull/25756",
            "`tools/server/server-http.cpp`: `2` добавления и `2` удаления",
            "`req.get_header_value(\"Origin\")`",
            "Отсутствующий или пустой origin",
            "HTTP status, response body, CORS allow policy и inference path не изменяются",
            "`POST api/v1/chat/turn`",
            "`soll-backend-route` default",
            "на `27` commits впереди `b10041`",
            "Verification scripts запускают `llama-server` только с `--version`",
            "`approved_models.json` содержит `models: []`",
            "## Ворота будущего измеримого smoke",
            "Определены `6` ворот будущего focused smoke",
            "Выполнено `0` локальных b10041 HTTP requests",
        ).forEach { control ->
            assertTrue("Missing b10041 applicability control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: 4ab630a0f9c24b1a9b9aa894708d2b50",
            "source_ref: insight/311051324150",
            "source_processing_result: local_applicability_documented_no_current_cors_execution_seam",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-4ab630a0f9c24b1a9b9aa894708d2b50-llama-cpp-b10041-audit.md",
            "1 wiki local-applicability evaluation added",
            "3 official upstream surfaces and 5 current Soll seams audited",
            "b10068 verified 27 commits ahead of b10041",
            "6 smoke gates defined",
            "0 current direct llama-server HTTP/CORS config matches, 0 production/runtime changes, and 0 local b10041 HTTP requests",
            "no Android or runtime change is warranted now",
            "LlamaCppB10041LocalApplicabilityTest",
            "`1/1` focused test passed with `0` failures, `0` errors and `0` skipped tests",
        ).forEach { evidence ->
            assertTrue("Missing b10041 audit evidence: $evidence", audit.contains(evidence))
        }

        listOf(
            "\"tag\": \"b10068\"",
            "\"packageIntoAndroidApp\": false",
            "\"androidRuntimeDefault\": \"soll-backend-route\"",
        ).forEach { control ->
            assertTrue("Active standalone policy drifted: $control", activeDefaults.contains(control))
        }
        assertTrue(
            "Model provenance gate must stay deny-by-default and scoped to the CLI smoke",
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
