package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9934WebGpuMonitoringTest {
    @Test
    fun `b9934 stays a measured WebGPU monitoring signal instead of a runtime rollout`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9934-webgpu-flash-attention-monitoring.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-d89412dfe05ae7da-verification.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()

        listOf(
            "raw/monitored/llama-cpp-releases/20260709-233427-b9934-5e34e4da.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9934",
            "32e41fa5b48e15b93c7a40ce677226b2e773c351",
            "92366df30d4eaa4b85139b5fd694360237731b19",
            "https://github.com/ggml-org/llama.cpp/pull/25418",
            "два WebGPU-файла",
            "`16` добавлениями и `23` удалениями",
            "`VEC_NE` заменен на `D_SPLIT`",
            "больше не требует равенства двух head dimensions",
            "pipeline variant (`_dsplit...`)",
            "NVIDIA V100 | gemma4 E4B Q4_K_M | 26.02 ± 0.16 | 27.21 ± 0.15 | +4.6%",
            "NVIDIA V100 | llama 3B Q4_K_M | 25.55 ± 1.44 | 34.78 ± 2.57 | +36.1%",
            "Apple M2 | gpt-oss 20B MXFP4 MoE | 20.12 ± 0.17 | 22.22 ± 0.16 | +10.4%",
            "Apple M2 | llama 3B Q4_K_M | 18.18 ± 0.05 | 20.18 ± 0.01 | +11.0%",
            "`gpu-webgpu-nvidia`",
            "`gpu-webgpu-apple`",
            "V100 в опубликованном опыте работает через WebGPU, не через CUDA",
            "M2 gemma4 `+0.6%` находится внутри опубликованного разброса",
            "Подтвержденной регрессии в PR нет",
            "b10068 на `134` commits впереди b9934",
            "Ворота будущего WebGPU A/B",
            "Выполнено `0` локальных WebGPU inference/benchmark runs",
        ).forEach { control ->
            assertTrue("Missing b9934 WebGPU monitoring control: $control", note.contains(control))
        }

        listOf(
            "task_id: 8bddd9e2a59a430799a4c8fbf2329e14",
            "source_ref: source-item/d0cd9479f2a2/d89412dfe05ae7da",
            "source_processing_result: upstream_webgpu_benchmark_recorded_local_runtime_unchanged",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-d89412dfe05ae7da-verification.md",
            "1 WebGPU monitoring note added",
            "6 upstream benchmark rows, 2 WebGPU CI jobs and 5 current Soll seams audited",
            "b10068 verified 134 commits ahead of b9934",
            "6 future A/B gates defined",
            "0 production/runtime changes and 0 local WebGPU inference runs",
            "LlamaCppB9934WebGpuMonitoringTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9934 audit evidence: $evidence", audit.contains(evidence))
        }

        assertTrue(activeDefaults.contains("\"tag\": \"b10068\""))
        assertTrue(activeDefaults.contains("\"androidRuntimeDefault\": \"soll-backend-route\""))
        assertTrue(activeDefaults.contains("\"packageIntoAndroidApp\": false"))
        assertEquals(2, Regex("\"backend\"\\s*:\\s*\"cpu\"").findAll(activeDefaults).count())
        assertFalse(activeDefaults.contains("\"backend\": \"webgpu\""))
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
