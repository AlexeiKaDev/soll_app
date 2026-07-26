package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10021ImplementationDecisionTest {
    @Test
    fun `b10021 is rejected for current runtime and retained as a measured benchmark candidate`() {
        val wiki = projectFile("wiki/b10021.md").readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-c468beb0d70547c7bee83dd5a9906792-llama-cpp-b10021-audit.md",
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
            "monitored/llama-cpp-releases/20260716-023002-b10021-2a83eb1c.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10021",
            "33a75f41c30052fd3d1c38e8ed2f86ee3c3f8fba",
            "d3fba0c79db8d25a76031783bf071d02234812e6",
            "https://github.com/ggml-org/llama.cpp/pull/25702",
            "`src/models/deepseek4.cpp`: `14` добавлений и `12` удалений",
            "`kv_rows` один раз получает KV строки",
            "`score_rows` один раз получает score строки",
            "сокращение graph splits с `5` до `2`",
            "на `47` commits впереди `b10021`, на `0` commits позади",
            "`POST api/v1/chat/turn`",
            "`soll-backend-route` default",
            "`approved_models.json` использует deny-by-default и содержит `models: []`",
            "Сигнал отклонён для текущего внедрения",
            "## Ворота будущего измеримого benchmark",
            "Определены `6` ворот будущего измеримого DeepSeek-V4 benchmark",
            "измеренная runtime-ценность b10021 для Soll app остаётся `0`",
        ).forEach { control ->
            assertTrue("Missing b10021 implementation control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: c468beb0d70547c7bee83dd5a9906792",
            "source_ref: insight/a817ab1e9a5c",
            "source_processing_result: implementation_rejected_no_current_deepseek_v4_execution_seam",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-c468beb0d70547c7bee83dd5a9906792-llama-cpp-b10021-audit.md",
            "1 wiki implementation decision added",
            "3 official upstream surfaces and 5 current Soll seams audited",
            "b10068 verified 47 commits ahead of b10021",
            "6 benchmark gates defined",
            "0 production/runtime changes and 0 measured b10021 DeepSeek-V4 inference value",
            "reject a separate b10021 rollout or Android implementation now",
            "LlamaCppB10021ImplementationDecisionTest",
            "`1/1` focused test passed with `0` failures, `0` errors and `0` skipped tests",
        ).forEach { evidence ->
            assertTrue("Missing b10021 audit evidence: $evidence", audit.contains(evidence))
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
            "Model provenance gate must stay deny-by-default and exclude DeepSeek-V4",
            approvedModels.contains("\"policy\": \"deny_unlisted\"") &&
                approvedModels.contains("\"purpose\": \"b9945-chat-template-smoke-only\"") &&
                !approvedModels.contains("DeepSeek-V4"),
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
