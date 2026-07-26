package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10066ImplementationAnalysisTest {
    @Test
    fun `b10066 remains a measured OpenCL benchmark candidate instead of an Android default`() {
        val analysis = projectFile(
            "docs/knowledge/llama-cpp-b10066-implementation-analysis.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-f5201a778ee04baba0b60a73b2ee15c0-llama-cpp-b10066-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Запрошенный `wiki/b10066.md`",
            "monitored/llama-cpp-releases/20260718-023020-b10066-64837e22.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10066",
            "86a9c79f866799eb0e7e89c03578ccfbcc5d808e",
            "https://github.com/ggml-org/llama.cpp/pull/25797",
            "kernel_gemm_moe_q6_k_f32_ns",
            "q5_K int8 dp4",
            "не общий прирост CPU",
            "`soll-backend-route` остаются default",
            "в app нет `CMakeLists.txt`, `externalNativeBuild`",
            "Android asset — arm64 CPU",
            "Шесть ворот такого benchmark",
            "минимум `10%` устойчивого выигрыша",
            "Выполнено `0` b10066 inference/benchmark runs",
        ).forEach { control ->
            assertTrue("Missing b10066 implementation control: $control", analysis.contains(control))
        }

        listOf(
            "task_id: f5201a778ee04baba0b60a73b2ee15c0",
            "source_ref: insight/2e2a544eb69d",
            "source_processing_result: implementation_analysis_completed_benchmark_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-f5201a778ee04baba0b60a73b2ee15c0-llama-cpp-b10066-audit.md",
            "1 implementation analysis added",
            "3 official upstream surfaces and 5 current Soll seams audited",
            "6 benchmark gates defined",
            "0 production/runtime changes and 0 measured b10066 Soll inference value",
            "The requested `wiki/b10066.md` and monitored source artifact are not vendored",
            "LlamaCppB10066ImplementationAnalysisTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b10066 audit evidence: $evidence", audit.contains(evidence))
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
