package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9978ImplementationDecisionTest {
    @Test
    fun `b9978 is a relevant checkpoint regression contract with no current runtime rollout`() {
        val wiki = projectFile("wiki/b9978.md").readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-a4ba4570c24d4b2aa3aeb5a686fcd817-llama-cpp-b9978-audit.md",
        ).readText().normalizeWhitespace()
        val b9936Knowledge = projectFile(
            "docs/knowledge/llama-cpp-b9936-min-step-prompt-batch-splitting.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText().normalizeWhitespace()
        val activeSmoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppActiveRelease.ps1",
        ).readText().normalizeWhitespace()
        val approvedModels = projectFile(
            "tools/llama-cpp/approved_models.json",
        ).readText().normalizeWhitespace()
        val api = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText().normalizeWhitespace()
        val productionSource = projectFile("app/src/main")
            .walkTopDown()
            .filter(File::isFile)
            .joinToString(separator = "\n") { it.readText() }

        listOf(
            "Source signal **валидирован и релевантен**",
            "monitored/llama-cpp-releases/20260713-010004-b9978-281ae5e2.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9978",
            "0c4fa7a989f94a9fef9e52a887e3376bb60d0848",
            "6b4dc2116a92c5c8f2782bfe51fabe5ee66fb5ef",
            "https://github.com/ggml-org/llama.cpp/pull/25472",
            "https://github.com/ggml-org/llama.cpp/issues/25023",
            "`common/common.h` и `tools/server/server-context.cpp` (`27` добавлений, `1` удаление)",
            "`common_prompt_checkpoint` хранит optional `id_task`",
            "Checkpoints текущей task не удаляются",
            "`near_prompt_end` может создать критический checkpoint",
            "`POST api/v1/chat/turn`",
            "b10068 на `90` commits впереди b9978, на `0` позади",
            "b9978 добавляет complementary checkpoint-lifecycle contract",
            "## Будущий измеримый smoke",
            "Определены `6` шагов будущего model-backed checkpoint smoke",
            "выполнено `0` local checkpoint workloads",
        ).forEach { control ->
            assertTrue("Missing b9978 implementation-decision control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: a4ba4570c24d4b2aa3aeb5a686fcd817",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/1a90b7a917d6",
            "source_processing_result: validated_relevant_checkpoint_regression_contract_no_current_runtime_rollout",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-a4ba4570c24d4b2aa3aeb5a686fcd817-llama-cpp-b9978-audit.md",
            "1 wiki implementation decision added",
            "5 official upstream surfaces and 6 current Soll seams audited",
            "b10068 verified 90 commits ahead of b9978",
            "6 future runtime smoke steps defined",
            "0 production/runtime changes and 0 local checkpoint workloads",
            "The source signal is validated and relevant",
            "LlamaCppB9978ImplementationDecisionTest",
            "`1/1` focused test passed with `0` failures, `0` errors and `0` skipped tests",
        ).forEach { evidence ->
            assertTrue("Missing b9978 audit evidence: $evidence", audit.contains(evidence))
        }

        listOf(
            "https://github.com/ggml-org/llama.cpp/pull/25420",
            "`--checkpoint-min-step` имеет default `256` tokens",
            "## Возможный regression test",
        ).forEach { existingControl ->
            assertTrue(
                "Existing b9936 min-step contract drifted: $existingControl",
                b9936Knowledge.contains(existingControl),
            )
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
            "Active release smoke must remain version-only for llama-server",
            activeSmoke.contains("& ${'$'}serverPath.FullName --version"),
        )
        assertFalse(activeSmoke.contains("--checkpoint-min-step"))
        assertFalse(activeSmoke.contains("--ctx-checkpoints"))
        assertTrue(
            "Model provenance gate must stay deny-by-default and scoped to existing smokes",
            approvedModels.contains("\"policy\": \"deny_unlisted\"") &&
                approvedModels.contains("\"purpose\": \"b9945-chat-template-smoke-only\"") &&
                !approvedModels.contains("b9978"),
        )
        assertTrue(
            "Android chat must stay behind the Soll backend contract",
            api.contains("@POST(\"api/v1/chat/turn\")") &&
                api.contains("suspend fun sendChatTurn("),
        )
        listOf(
            "checkpoint_min_step",
            "checkpoint-min-step",
            "ctx-checkpoints",
            "near_prompt_end",
        ).forEach { unsupportedCheckpointSeam ->
            assertFalse(
                "Android production source unexpectedly owns llama-server checkpoint seam: " +
                    unsupportedCheckpointSeam,
                productionSource.contains(unsupportedCheckpointSeam),
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
