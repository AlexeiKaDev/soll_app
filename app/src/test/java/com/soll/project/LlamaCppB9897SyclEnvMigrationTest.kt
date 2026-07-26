package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9897SyclEnvMigrationTest {
    @Test
    fun `b9897 documents the SYCL inversion without changing the Android runtime`() {
        val knowledge = projectFile(
            "docs/knowledge/llama-cpp-b9897-sycl-env-migration.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-fa338b12f83c33c7-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9897",
            "https://github.com/ggml-org/llama.cpp/pull/25042",
            "commit `26145b3`",
            "`GGML_SYCL_DISABLE_OPT=0` | `GGML_SYCL_ENABLE_OPT=1`",
            "`GGML_SYCL_DISABLE_OPT=1` | `GGML_SYCL_ENABLE_OPT=0`",
            "`GGML_SYCL_DISABLE_GRAPH=1` | `GGML_SYCL_ENABLE_GRAPH=0`",
            "`GGML_SYCL_DISABLE_GRAPH=0` | `GGML_SYCL_ENABLE_GRAPH=1`",
            "`GGML_SYCL_DISABLE_DNN=0` | `GGML_SYCL_ENABLE_DNN=1`",
            "`GGML_SYCL_DISABLE_DNN=1` | `GGML_SYCL_ENABLE_DNN=0`",
            "`ENABLE = 1 - DISABLE`",
            "`GGML_SYCL_USE_VMM`",
            "`GGML_SYCL_SUPPORT_VMM`",
            "текущих SYCL execution seam найдено `0`",
            "Этот список не является доказательством",
            "выполнено `0` фиктивных benchmark",
        ).forEach { control ->
            assertTrue("Missing b9897 migration control: $control", knowledge.contains(control))
        }

        listOf(
            "Task id: `0b6087e2f99c43a8b4956a25e07d9d47`",
            "Source ref: `source-item/d0cd9479f2a2/fa338b12f83c33c7`",
            "source_processing_result: sycl_migration_documented_runtime_not_applicable",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-fa338b12f83c33c7-verification.md",
            "source_value: knowledge_only",
            "0 applicable SYCL seams found",
            "0 production/runtime files changed",
            "0 local b9897 build or inference runs claimed",
            "LlamaCppB9897SyclEnvMigrationTest",
        ).forEach { evidence ->
            assertTrue("Missing b9897 verification evidence: $evidence", verification.contains(evidence))
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
