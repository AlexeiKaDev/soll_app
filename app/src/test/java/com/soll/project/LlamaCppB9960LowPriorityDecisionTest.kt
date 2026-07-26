package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9960LowPriorityDecisionTest {
    @Test
    fun `b9960 is rejected at low priority because its web ui has no current Soll seam`() {
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-0c7839e80ae54776831aadcfc7e3c172-llama-cpp-b9960-audit.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText().normalizeWhitespace()
        val api = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText().normalizeWhitespace()
        val productionSource = projectFile("app/src/main")
            .walkTopDown()
            .filter(File::isFile)
            .joinToString(separator = "\n") { it.readText() }

        listOf(
            "task_id: 0c7839e80ae54776831aadcfc7e3c172",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/d1d74e584f16",
            "priority: low",
            "task_board_translation: reject",
            "source_processing_result: rejected_low_priority_no_current_soll_execution_seam",
            "wiki/b9960.md` and `daily/2026-07-25.md` are absent",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9960",
            "a935fbffe1a3d31509c325c116454ab5d56b2eb8",
            "https://github.com/ggml-org/llama.cpp/pull/25500",
            "9 server/UI files (`99` additions, `60` deletions)",
            "deletes `tools/ui/static/loading.html`",
            "detects HTTP `503`",
            "every `1000 ms`",
            "Reject for current implementation; priority `low`",
            "next step: none",
            "active b10068 verified 108 commits ahead",
            "0 applicable Soll production paths",
            "0 production/runtime changes and 0 measured runtime value",
        ).forEach { control ->
            assertTrue("Missing b9960 low-priority decision control: $control", audit.contains(control))
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
            "Android chat must remain behind the Soll backend contract",
            api.contains("@POST(\"api/v1/chat/turn\")") &&
                api.contains("suspend fun sendChatTurn("),
        )
        listOf("loading.html", "llama_ui_get_assets").forEach { unsupportedUiSeam ->
            assertFalse(
                "Android production source unexpectedly owns llama-server UI seam: $unsupportedUiSeam",
                productionSource.contains(unsupportedUiSeam),
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
