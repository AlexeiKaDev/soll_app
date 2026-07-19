package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10068LocalApplicationEvaluationTest {
    @Test
    fun `b10068 remains a measured DFlash correctness candidate instead of an Android default`() {
        val analysis = projectFile(
            "docs/knowledge/llama-cpp-b10068-local-application-evaluation.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-7ded04fb5e1443e88deb663cf2a4e992-llama-cpp-b10068-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Запрошенный `wiki/b10068.md`",
            "monitored/llama-cpp-releases/20260719-030008-b10068-2ceec587.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10068",
            "571d0d540df04f25298d0e159e520d9fc62ed121",
            "4937ca83f4f3da63004943fe05d8aa4f0217d238",
            "https://github.com/ggml-org/llama.cpp/pull/25823",
            "https://github.com/ggml-org/llama.cpp/issues/25725",
            "llama_mul_mat_hadamard",
            "Обычная inference и MTP не затронуты",
            "Draft acceptance: 0.97159",
            "`soll-backend-route` остаются default",
            "в app нет `CMakeLists.txt`, `externalNativeBuild`",
            "0 точных DFlash/cache-flag упоминаний",
            "Шесть ворот такого benchmark",
            "median draft acceptance не ниже `0.90`",
            "Выполнено `0` локальных b10068 inference/benchmark runs",
        ).forEach { control ->
            assertTrue("Missing b10068 local-application control: $control", analysis.contains(control))
        }

        listOf(
            "task_id: 7ded04fb5e1443e88deb663cf2a4e992",
            "source_ref: insight/166483ea3430",
            "source_processing_result: local_application_evaluated_no_current_dflash_workload",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-7ded04fb5e1443e88deb663cf2a4e992-llama-cpp-b10068-audit.md",
            "1 local-application evaluation added",
            "4 official upstream surfaces and 5 current Soll seams audited",
            "6 benchmark gates defined",
            "0 current DFlash runtime/config matches, 0 production/runtime changes, and 0 local b10068 inference runs",
            "The requested `wiki/b10068.md` and monitored source artifact are not vendored",
            "LlamaCppB10068LocalApplicationEvaluationTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b10068 audit evidence: $evidence", audit.contains(evidence))
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
