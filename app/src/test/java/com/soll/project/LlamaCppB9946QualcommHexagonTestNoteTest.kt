package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9946QualcommHexagonTestNoteTest {
    @Test
    fun `b9946 pins a Qualcomm Hexagon benchmark without treating the CPU asset as proof`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9946-qualcomm-hexagon-unary-test-note.md",
        ).readText().normalizeWhitespace()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-ba5038e3a0db0c4f-verification.md",
        ).readText().normalizeWhitespace()
        val activeDefaultsText = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()
        val activeDefaults = JSONObject(activeDefaultsText)
        val approvedModels = projectFile(
            "tools/llama-cpp/approved_models.json",
        ).readText()

        listOf(
            "raw/monitored/llama-cpp-releases/20260711-001152-b9946-3469ed9c.md",
            "https://github.com/ggml-org/llama.cpp/pull/25474",
            "10 commits и 9 изменённых Hexagon файлов",
            "fb30ba9a6c5b4674174d06aed14794832ab33278",
            "82fce65d8be40ba55048e06f2e14a01deb363d41",
            "`122` commits ahead и `0` behind",
            "llama-b9946-bin-android-arm64.tar.gz",
            "label: `Android arm64 (CPU)`",
            "`74337414` bytes",
            "c54732403dc88c9a05edfef5b0ec31d63d720a52ec54154fce6b781ad2535712",
            "`44/44` binary files",
            "не доказательство эффекта PR #25474",
            "Qualcomm Snapdragon Android device",
            "Llama-3.2-1B-Instruct-Q4_0.gguf",
            "deny-by-default `tools/llama-cpp/approved_models.json`",
            "`ANDROID_ABI=arm64-v8a`",
            "`ANDROID_PLATFORM=android-31`",
            "`GGML_HEXAGON=ON`",
            "`GGML_OPENCL=ON`",
            "`GGML_OPENMP=OFF`",
            "`GGML_LLAMAFILE=OFF`",
            "`LLAMA_OPENSSL=OFF`",
            "`D=HTP0`, `NDEV=1`",
            "`GGML_HEXAGON_PROFILE=1`",
            "`1 warm-up + 5 measured repeats`",
            "prompt tokens/sec",
            "generation tokens/sec",
            "peak RSS/PSS",
            "`0` crashes",
            "Тест полностью offline",
            "offensive или security scenarios",
            "`0` device/model inference runs",
            "`0` production/runtime файлов",
        ).forEach { control ->
            assertTrue("Missing b9946 Qualcomm test control: $control", note.contains(control))
        }

        listOf(
            "task_id: ea94bdf1f8a143e9b8b271f6dbf7dcac",
            "source_ref: source-item/d0cd9479f2a2/ba5038e3a0db0c4f",
            "source_processing_result: pr_and_android_cpu_asset_verified_hexagon_benchmark_gated",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-ba5038e3a0db0c4f-verification.md",
            "PR #25474's 10 commits/9 Hexagon files and 25 uploaded b9946 assets audited",
            "44/44 binaries passed ELF64 AArch64 smoke",
            "2 matched custom-build arms and 6 measurement/stability gates defined",
            "0 production/runtime changes and 0 device/model inference runs",
            "LlamaCppB9946QualcommHexagonTestNoteTest",
            "`1/1` focused contract test passed",
        ).forEach { evidence ->
            assertTrue("Missing b9946 verification evidence: $evidence", artifact.contains(evidence))
        }

        val release = activeDefaults.getJSONObject("release")
        val policy = activeDefaults.getJSONObject("policy")
        val targets = activeDefaults.getJSONArray("targets")
        val androidTarget = (0 until targets.length())
            .map { targets.getJSONObject(it) }
            .single { it.getString("id") == "android-arm64-cpu" }

        assertEquals("b10068", release.getString("tag"))
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))
        assertEquals("cpu", androidTarget.getString("backend"))
        assertFalse(activeDefaultsText.contains("\"backend\": \"hexagon\""))
        assertFalse(activeDefaultsText.contains("\"tag\": \"b9946\""))
        assertTrue(approvedModels.contains("\"policy\": \"deny_unlisted\""))
        assertFalse(approvedModels.contains("Llama-3.2-1B-Instruct-Q4_0.gguf"))
        assertFalse(approvedModels.contains("b9946"))
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
