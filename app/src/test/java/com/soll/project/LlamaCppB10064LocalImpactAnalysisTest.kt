package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB10064LocalImpactAnalysisTest {
    @Test
    fun `b10064 remains a measured Adreno Q4K candidate instead of an Android default`() {
        val analysis = projectFile(
            "docs/knowledge/llama-cpp-b10064-local-impact-analysis.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-82670a2518ca489bb667b7f08132d9a0-llama-cpp-b10064-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Запрошенный `wiki/b10064.md`",
            "monitored/llama-cpp-releases/20260718-023020-b10064-2e624b01.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b10064",
            "86d86ed4396b4130922f7b9af26e3d9fc11a591b",
            "7d56da7e546f54fb1fa54ef2bc9ad9a872860ab0",
            "https://github.com/ggml-org/llama.cpp/pull/25805",
            "`4` файла только в `ggml/src/ggml-opencl`",
            "транспонирует буфер `s`",
            "gemm_noshuffle_q4_k_f32.cl",
            "gemm_noshuffle_q4_k_q8_1_dp4a.cl",
            "gemv_noshuffle_q4_k_f32.cl",
            "dense-моделями с Q4_K",
            "не общий прирост Android CPU",
            "`soll-backend-route` остаются default",
            "в production app нет `CMakeLists.txt`, `externalNativeBuild`",
            "Android asset — arm64 CPU",
            "Шесть проверенных seam текущего проекта",
            "Шесть ворот такого benchmark",
            "минимум `10%` устойчивого выигрыша",
            "Выполнено `0` b10064 inference/benchmark runs",
        ).forEach { control ->
            assertTrue("Missing b10064 local-impact control: $control", analysis.contains(control))
        }

        listOf(
            "task_id: 82670a2518ca489bb667b7f08132d9a0",
            "source_ref: insight/7d1711a2ae70",
            "source_processing_result: local_impact_analysis_completed_benchmark_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-82670a2518ca489bb667b7f08132d9a0-llama-cpp-b10064-audit.md",
            "1 local-impact analysis added",
            "3 official upstream surfaces and 6 current Soll seams audited",
            "6 benchmark gates defined",
            "0 production/runtime changes and 0 measured b10064 Soll inference value",
            "The requested `wiki/b10064.md` and monitored source artifact are not vendored",
            "LlamaCppB10064LocalImpactAnalysisTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b10064 audit evidence: $evidence", audit.contains(evidence))
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
