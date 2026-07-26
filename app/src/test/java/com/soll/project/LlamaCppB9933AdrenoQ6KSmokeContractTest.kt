package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9933AdrenoQ6KSmokeContractTest {
    @Test
    fun `b9933 adds the non-128 Q6K Adreno gate without changing Android runtime`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9933-android-adreno-q6-k-smoke.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-d4aa83030fe52bee-verification.md",
        ).readText().normalizeWhitespace()
        val activeDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()

        listOf(
            "raw/monitored/llama-cpp-releases/20260709-233427-b9933-5861f5ec.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9933",
            "Android с Qualcomm Adreno/OpenCL",
            "Q6_K",
            "dimension % 128 != 0",
            "exact llama.cpp release и commit",
            "SHA-256 тестируемого binary",
            "vocab size и конкретные tensor shapes",
            "CPU reference",
            "минимум `3` раза",
            "`0` crashes, `0` NaN/Inf, `0` unexpected fallbacks",
            "Обычная модель с размерностями, кратными `128`",
            "не заменяет обязательный non-128 fixture",
            "обычный regression/smoke контур Soll",
            "менять active defaults только отдельной review-задачей",
            "Выполнено `0` device/model inference runs",
        ).forEach { control ->
            assertTrue("Missing b9933 Android/Adreno Q6_K control: $control", note.contains(control))
        }

        listOf(
            "task_id: cd32673e2db04d698b3a222fe3eaa15b",
            "source_ref: source-item/d0cd9479f2a2/d4aa83030fe52bee",
            "source_processing_result: android_adreno_q6_k_smoke_check_documented_runtime_unchanged",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-d4aa83030fe52bee-verification.md",
            "1 Soll_app Q6_K regression note added",
            "6 provenance/result fields, 6 focused smoke steps and 6 runtime promotion gates",
            "0 production/runtime changes and 0 device/model inference runs",
            "LlamaCppB9933AdrenoQ6KSmokeContractTest",
            "`1/1` focused test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9933 smoke/audit evidence: $evidence", audit.contains(evidence))
        }

        assertTrue(activeDefaults.contains("\"androidRuntimeDefault\": \"soll-backend-route\""))
        assertTrue(activeDefaults.contains("\"packageIntoAndroidApp\": false"))
        assertFalse(activeDefaults.contains("\"tag\": \"b9933\""))
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
