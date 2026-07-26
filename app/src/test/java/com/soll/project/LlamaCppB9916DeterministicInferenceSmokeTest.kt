package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9916DeterministicInferenceSmokeTest {
    @Test
    fun `b9916 and active baseline keep a reproducible isolated inference comparison`() {
        val comparison = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_b9916_comparison.json").readText(),
        )
        val active = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_active_defaults.json").readText(),
        )
        val allowlist = JSONObject(
            projectFile("tools/llama-cpp/approved_models.json").readText(),
        )
        val smoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppB9916DeterministicInference.ps1",
        ).readText()
        val readme = projectFile("tools/llama-cpp/README.md").readText()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-e0f7e52c1bda5c12-verification.md",
        ).readText().normalizeWhitespace()

        val release = comparison.getJSONObject("release")
        assertEquals("b9916", release.getString("tag"))
        assertEquals(
            "57b50e1f6b50e01eb14c43fc1253602af74c1870",
            release.getString("commit"),
        )
        assertEquals(25390, release.getInt("pullRequest"))

        val policy = comparison.getJSONObject("policy")
        assertTrue(policy.getBoolean("verifySha256"))
        assertTrue(policy.getBoolean("historicalComparisonOnly"))
        assertTrue(policy.getBoolean("notApprovedAsActiveBaseline"))
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))
        assertEquals(
            "b9916-deterministic-inference-smoke",
            policy.getString("approvedModelUse"),
        )

        val target = comparison.getJSONObject("target")
        assertEquals("windows-x64-cpu", target.getString("id"))
        assertEquals("cpu", target.getString("backend"))
        assertEquals("llama-completion.exe", target.getString("inferenceExecutable"))
        assertEquals("version: 9916 \\(57b50e1f6\\)", target.getString("versionPattern"))
        val releasePackage = target.getJSONObject("package")
        assertEquals(17498364L, releasePackage.getLong("bytes"))
        assertEquals(
            "b9421aa043ef9e93d518246e26a2c89aa073237ad9122a8b327792177cae7c8b",
            releasePackage.getString("sha256"),
        )

        val activeRelease = active.getJSONObject("release")
        assertEquals("b10068", activeRelease.getString("tag"))
        assertEquals(
            "571d0d540df04f25298d0e159e520d9fc62ed121",
            activeRelease.getString("commit"),
        )
        assertEquals("b9917", activeRelease.getString("securityBaseline"))
        assertTrue(
            activeRelease.getString("tag").removePrefix("b").toInt() >
                release.getString("tag").removePrefix("b").toInt(),
        )

        val models = allowlist.getJSONArray("models")
        assertEquals(1, models.length())
        val model = models.getJSONObject(0)
        assertEquals("stories15M-q8_0.gguf", model.getString("fileName"))
        assertEquals(
            "2eda49203f2f044f3dddf29a7dd7cc861ef5a0340f518a19613d73ba6d9c06b6",
            model.getString("sha256"),
        )
        val approvedUses = model.getJSONArray("approvedUses")
        assertTrue(
            (0 until approvedUses.length())
                .map { approvedUses.getString(it) }
                .contains("b9916-deterministic-inference-smoke"),
        )

        listOf(
            "Test-LlamaCppActiveRelease.ps1",
            "Test-LlamaCppModelProvenance.ps1",
            "inferenceExecutable",
            "Invoke-DeterministicInference",
            "424242",
            "\"0\"",
            "\"1\"",
            "\"128\"",
            "\"--offline\"",
            "\"--no-display-prompt\"",
            "\"--no-warmup\"",
            "\"--no-mmap\"",
            "withinReleaseDeterministic = ${'$'}true",
            "crossReleaseOutputMatch",
            "allRunsExitCodeZero = ${'$'}true",
            "TimeoutSeconds = 120",
            "CacheDirectory must stay inside the repository",
        ).forEach { control ->
            assertTrue("Missing deterministic smoke control: $control", smoke.contains(control))
        }
        assertTrue(readme.contains("Test-LlamaCppB9916DeterministicInference.ps1"))
        assertTrue(readme.contains("b9917 GGUF security baseline"))

        listOf(
            "task_id: d684628fc9304beebcaf4e55bc792097",
            "source_ref: source-item/d0cd9479f2a2/e0f7e52c1bda5c12",
            "source_processing_result: llama_cpp_b9916_deterministic_inference_smoke_passed_pin_unchanged",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-e0f7e52c1bda5c12-verification.md",
            "source_value: accepted",
            "A[i * K + kk]",
            "`152` commits ahead and `0` behind",
            "`2/2` runs deterministic on b9916",
            "`2/2` on active b10068",
            "`26`-byte output matched across releases",
            "193a9313cf55adbde15b7742e5e36fa69a328149c2db5d33cb82305c9c3329ff",
            "`inferenceRuns: 4`",
            "`allRunsExitCodeZero: true`",
            "`crossReleaseOutputMatch: true`",
            "Keep b10068 pinned",
            "`1/1` focused contract test passed",
            "LlamaCppB9916DeterministicInferenceSmokeTest",
        ).forEach { evidence ->
            assertTrue("Missing b9916 verification evidence: $evidence", artifact.contains(evidence))
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
