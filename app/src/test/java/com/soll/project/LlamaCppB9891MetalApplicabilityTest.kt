package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9891MetalApplicabilityTest {
    @Test
    fun `b9891 Metal change stays documented until an Apple target exists`() {
        val knowledge = projectFile(
            "docs/knowledge/llama-cpp-b9891-metal-col2im-applicability.md",
        ).readText().normalizeWhitespace()
        val evidence = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-e94cf86e6b4a008b-verification.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()

        listOf(
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9891",
            "https://github.com/ggml-org/llama.cpp/pull/25176",
            "f36e5c348bc8795c34f9a038e58876e7a8423d4d",
            "`COL2IM_1D` с `f32`, `f16` и `bf16`",
            "contiguous destination",
            "test-backend-ops -o COL2IM_1D",
            "Soll app является Android-приложением",
            "нет `CMakeLists.txt`, `externalNativeBuild`",
            "`0` Android dependencies",
            "отдельного Apple target",
        ).forEach { control -> assertTrue("Missing Metal control: $control", knowledge.contains(control)) }

        listOf(
            "task_id: c91d8e4aacb74e12b82c16b9b79c5358",
            "source_processing_result: documented_not_applicable_no_apple_metal_target",
            "documentation/documented_not_applicable",
            "`0` Apple/Metal production targets",
            "`0` Android dependency changes",
            "`0` local Metal builds or benchmarks claimed",
        ).forEach { control -> assertTrue("Missing Metal evidence: $control", evidence.contains(control)) }

        assertTrue(activeDefaults.contains("\"tag\": \"b10068\""))
        assertTrue(activeDefaults.contains("\"androidRuntimeDefault\": \"soll-backend-route\""))
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
