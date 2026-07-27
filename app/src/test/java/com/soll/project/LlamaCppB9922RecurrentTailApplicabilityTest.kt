package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9922RecurrentTailApplicabilityTest {
    @Test
    fun `b9922 recurrent tail change is evaluated against the standalone runtime without Android drift`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9922-recurrent-tail-splitting.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-8043cb40647203f6-verification.md",
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
            "raw/monitored/llama-cpp-releases/20260708-223009-b9922-214bd25b.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9922",
            "https://github.com/ggml-org/llama.cpp/pull/25278",
            "230ea9d214320c5e79cc8166ed708ac60514c71e",
            "f296fdfbed71e900a3e0d6579673960e6a560654",
            "PR содержит `1` commit, меняет `10` файлов (`102` добавления, `38` удалений)",
            "`src/llama-batch.cpp` (`+70/-4`)",
            "`n_keep_tail`",
            "n_rs_seq > 0 ? n_rs_seq + 1 : 0",
            "`n_ubatch > n_keep_tail`",
            "`POST api/v1/chat/turn`",
            "standalone-контур llama.cpp/GGUF",
            "release `b10068`",
            "на `146` commits впереди b9922, на `0` позади",
            "нет одобренного summarization/RAG workload",
            "## Оценка влияния на summarization/RAG",
            "parallelism `1`, `2`, `4` и `8`",
            "минимум `5` раз",
            "минимум на `10%`",
            "Определены `6` шагов будущего model-backed benchmark",
            "выполнено `0` local model inference runs",
        ).forEach { control ->
            assertTrue("Missing b9922 applicability control: $control", note.contains(control))
        }

        listOf(
            "task_id: 9c6ce128efae4e1b96a766b9b4e6e4f5",
            "source_ref: source-item/d0cd9479f2a2/8043cb40647203f6",
            "source_processing_result: standalone_b10068_confirmed_b9922_included_no_current_recurrent_workload",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-8043cb40647203f6-verification.md",
            "1 Soll_app recurrent-tail KB note added",
            "4 official upstream surfaces and 6 current Soll seams audited",
            "standalone b10068 confirmed 146 commits ahead of b9922",
            "6 future benchmark steps and 4 parallelism levels defined",
            "0 production/runtime changes and 0 local model inference runs",
            "LlamaCppB9922RecurrentTailApplicabilityTest",
            "`1/1` focused contract test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9922 audit evidence: $evidence", audit.contains(evidence))
        }

        listOf(
            "\"tag\": \"b10068\"",
            "\"commit\": \"571d0d540df04f25298d0e159e520d9fc62ed121\"",
            "\"packageIntoAndroidApp\": false",
            "\"androidRuntimeDefault\": \"soll-backend-route\"",
            "\"standaloneAndroidBinaryUse\": \"upstream-harness-or-adb-smoke-only\"",
            "\"requireModelAllowlist\": true",
        ).forEach { control ->
            assertTrue("Active standalone policy drifted: $control", activeDefaults.contains(control))
        }
        assertFalse(activeDefaults.contains("\"tag\": \"b9922\""))

        assertTrue(activeSmoke.contains("& \$serverPath.FullName --version"))
        assertFalse(activeSmoke.contains("n_keep_tail"))

        assertTrue(approvedModels.contains("\"policy\": \"deny_unlisted\""))
        assertTrue(approvedModels.contains("\"purpose\": \"b9945-chat-template-smoke-only\""))
        assertFalse(approvedModels.contains("recurrent", ignoreCase = true))
        assertFalse(approvedModels.contains("b9922"))

        assertTrue(
            "Android chat must stay behind the Soll backend contract",
            api.contains("@POST(\"api/v1/chat/turn\")") &&
                api.contains("suspend fun sendChatTurn("),
        )

        val productionRoots = listOf(
            projectFile("app/src/main"),
            projectFile("app/build.gradle.kts"),
            projectFile("gradle/libs.versions.toml"),
        )
        val productionFiles = productionRoots.flatMap { root ->
            if (root.isFile) {
                listOf(root)
            } else {
                root.walkTopDown().filter { it.isFile }.toList()
            }
        }
        val directRuntimeMarkers = listOf(
            "llama-server",
            "llama-cli",
            ".gguf",
            "libllama",
            "loadLibrary(\"llama",
            "#include \"llama",
            "externalNativeBuild",
        )
        val productionText = productionFiles.joinToString(separator = "\n") { it.readText() }
        directRuntimeMarkers.forEach { marker ->
            assertFalse(
                "Direct llama.cpp/GGUF runtime marker found in Android production inputs: $marker",
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
