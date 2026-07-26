package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10045ImplementationLocationTest {
    @Test
    fun `b10045 is located in the server adapter without changing Android runtime`() {
        val wiki = projectFile("wiki/b10045.md").readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-77d8e39e3e464dbab2adb9ac4492c163-llama-cpp-b10045-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "monitored/llama-cpp-releases/20260717-023007-b10045-bc5a2e14.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10045",
            "a8dc0e3269a5378d212e6daea953fbbaa7ac8e4b",
            "https://github.com/ggml-org/llama.cpp/pull/25076",
            "`check_slot_no_media()`",
            "`get_text_tokens()`",
            "media по-прежнему должен быть отклонён с HTTP `501`",
            "Место продуктового внедрения",
            "inference adapter Soll server",
            "`POST /api/v1/chat/turn`",
            "tools/llama-cpp/Test-LlamaCppMultimodalSlotPersistence.ps1",
            "`b10068` на `23` commit впереди",
            "`approved_models.json` сейчас deny-by-default и содержит `0` моделей",
            "Android `ChatViewModel`, `SollRepository` и `SollApiService` при таком внедрении не меняются",
            "## Ворота измеримого smoke",
            "runtime-ценность для Soll app равна `0`",
        ).forEach { control ->
            assertTrue("Missing b10045 implementation-location control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: 77d8e39e3e464dbab2adb9ac4492c163",
            "source_ref: insight/d1315e5d1789",
            "source_processing_result: implementation_location_defined_runtime_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-77d8e39e3e464dbab2adb9ac4492c163-llama-cpp-b10045-audit.md",
            "1 wiki implementation-location contract added",
            "3 official upstream surfaces and 5 current Soll seams audited",
            "b10068 verified 23 commits ahead of b10045",
            "5 smoke gates defined",
            "0 production/runtime changes and 0 measured b10045 Soll inference value",
            "The implementation location is defined in `wiki/b10045.md`",
            "LlamaCppB10045ImplementationLocationTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b10045 audit evidence: $evidence", audit.contains(evidence))
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
