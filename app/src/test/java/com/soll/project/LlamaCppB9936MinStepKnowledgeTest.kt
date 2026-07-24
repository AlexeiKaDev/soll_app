package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9936MinStepKnowledgeTest {
    @Test
    fun `b9936 min-step diff is documented for future server regression without runtime drift`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9936-min-step-prompt-batch-splitting.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-cc70bc8f024388b1-verification.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()
        val activeSmoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppActiveRelease.ps1",
        ).readText()
        val approvedModels = projectFile(
            "tools/llama-cpp/approved_models.json",
        ).readText()
        val api = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText()

        listOf(
            "raw/monitored/llama-cpp-releases/20260709-233427-b9936-1bfd8906.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9936",
            "https://github.com/ggml-org/llama.cpp/pull/25420",
            "64c8b7db72fbd871512b371b5c141c00fd0a8ba6",
            "f2d1c2f3984cb0934b575069489a052654b4037b",
            "`tools/server/server-context.cpp`: `8` добавлений и `3` удаления",
            "`do_checkpoint`",
            "pos == last_user_pos",
            "checkpoints.empty()",
            "pos > checkpoints.back().n_tokens + params_base.checkpoint_min_step",
            "`--checkpoint-min-step` имеет default `256` tokens",
            "`POST api/v1/chat/turn`",
            "b10068 на `132` commits впереди b9936, на `0` позади",
            "`llama-server --version`",
            "нет одобренного server/model regression fixture",
            "## Возможный regression test",
            "`pos <= checkpoint + 256`",
            "`pos > checkpoint + 256`",
            "минимум `5` раз",
            "Определены `6` шагов будущего model-backed regression test",
            "выполнено `0` local model inference runs",
        ).forEach { control ->
            assertTrue("Missing b9936 min-step control: $control", note.contains(control))
        }

        listOf(
            "task_id: 16b9095f66114292925ca44a66ccf142",
            "source_ref: source-item/d0cd9479f2a2/cc70bc8f024388b1",
            "source_processing_result: min_step_prompt_batch_fix_verified_no_current_direct_server_execution_seam",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-cc70bc8f024388b1-verification.md",
            "1 Soll_app min-step note added",
            "4 official upstream surfaces and 5 current Soll seams audited",
            "b10068 verified 132 commits ahead of b9936",
            "6 future regression steps defined",
            "0 production/runtime changes and 0 local model inference runs",
            "LlamaCppB9936MinStepKnowledgeTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9936 audit evidence: $evidence", audit.contains(evidence))
        }

        assertTrue(activeDefaults.contains("\"tag\": \"b10068\""))
        assertTrue(activeDefaults.contains("\"androidRuntimeDefault\": \"soll-backend-route\""))
        assertTrue(activeDefaults.contains("\"packageIntoAndroidApp\": false"))
        assertEquals(2, Regex("\"backend\"\\s*:\\s*\"cpu\"").findAll(activeDefaults).count())
        assertFalse(activeDefaults.contains("\"tag\": \"b9936\""))

        assertTrue(activeSmoke.contains("& ${'$'}serverPath.FullName --version"))
        assertFalse(activeSmoke.contains("--checkpoint-min-step"))
        assertTrue(approvedModels.contains("\"policy\": \"deny_unlisted\""))
        assertFalse(approvedModels.contains("b9936"))
        assertTrue(
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
