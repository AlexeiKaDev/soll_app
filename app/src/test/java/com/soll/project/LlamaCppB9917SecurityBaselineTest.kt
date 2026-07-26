package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9917SecurityBaselineTest {
    @Test
    fun `active llama cpp baseline rejects unverified GGUF models`() {
        val config = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_active_defaults.json").readText(),
        )
        val allowlist = JSONObject(
            projectFile("tools/llama-cpp/approved_models.json").readText(),
        )
        val releaseSmoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppActiveRelease.ps1",
        ).readText()
        val provenanceGate = projectFile(
            "tools/llama-cpp/Test-LlamaCppModelProvenance.ps1",
        ).readText()
        val launcher = projectFile(
            "tools/llama-cpp/Invoke-LlamaCppVerifiedModel.ps1",
        ).readText()
        val knowledge = projectFile(
            "docs/knowledge/llama-cpp-b9917-gguf-security-baseline.md",
        ).readText().normalizeWhitespace()
        val evidence = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-1299bfa0fb5892b8-verification.md",
        ).readText().normalizeWhitespace()

        val release = config.getJSONObject("release")
        assertEquals("b10068", release.getString("tag"))
        assertEquals(
            "571d0d540df04f25298d0e159e520d9fc62ed121",
            release.getString("commit"),
        )
        assertEquals("b9917", release.getString("securityBaseline"))

        val policy = config.getJSONObject("policy")
        assertEquals(9917, policy.getInt("minimumSafeRelease"))
        assertTrue(policy.getBoolean("verifySha256"))
        assertTrue(policy.getBoolean("requireModelAllowlist"))
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))

        val targets = config.getJSONArray("targets")
        assertEquals(2, targets.length())
        repeat(targets.length()) { targetIndex ->
            val packages = targets.getJSONObject(targetIndex).getJSONArray("packages")
            assertEquals(1, packages.length())
            val releasePackage = packages.getJSONObject(0)
            assertTrue(releasePackage.getLong("bytes") > 0)
            assertTrue(releasePackage.getString("sha256").matches(Regex("[0-9a-f]{64}")))
        }

        assertEquals("deny_unlisted", allowlist.getString("policy"))
        val approvedModels = allowlist.getJSONArray("models")
        assertEquals(1, approvedModels.length())
        val smokeModel = approvedModels.getJSONObject(0)
        assertEquals("stories15M-q8_0.gguf", smokeModel.getString("fileName"))
        assertEquals(26671328L, smokeModel.getLong("bytes"))
        assertEquals(
            "2eda49203f2f044f3dddf29a7dd7cc861ef5a0340f518a19613d73ba6d9c06b6",
            smokeModel.getString("sha256"),
        )
        assertEquals(
            "def3e2dd70df35ecbf6403ea347de4c5977220c1",
            smokeModel.getString("revision"),
        )
        assertEquals("b9945-chat-template-smoke-only", smokeModel.getString("purpose"))

        listOf(
            "minimumSafeRelease",
            "Get-FileHash",
            "llama-cli.exe",
            "llama-server.exe",
            "ELF64 AArch64",
        ).forEach { control -> assertTrue(releaseSmoke.contains(control)) }
        listOf(
            "Only .gguf model files",
            "deny_unlisted",
            "Get-FileHash",
            "sourceUrl",
            "immutable source revision",
            "not approved by exact file name and SHA-256",
        ).forEach { control -> assertTrue(provenanceGate.contains(control)) }
        assertTrue(launcher.contains("Test-LlamaCppModelProvenance.ps1"))
        assertTrue(launcher.indexOf("Test-LlamaCppModelProvenance.ps1") < launcher.indexOf(" -m "))

        listOf(
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9917",
            "https://github.com/ggml-org/llama.cpp/pull/18750",
            "GHSA-ppcr-mg43-5hq3",
            "GHSA-4383-xr9f-c744",
            "b9895 до b10068",
            "`0` GGUF было найдено при исходном аудите",
            "`1` маленькая test-only GGUF-модель",
            "deny_unlisted",
        ).forEach { control -> assertTrue(knowledge.contains(control)) }
        listOf(
            "task_id: f677a485a77841808898a65f53258cd9",
            "source_processing_result: llama_cpp_b9917_security_baseline_enforced",
            "b9895 -> b10068",
            "minimum safe release: `b9917`",
            "PASS for `2/2` targets",
            "`10068 (571d0d540)`",
            "`44/44` non-license files passed",
            "an unlisted synthetic `.gguf` was rejected",
            "`381/381` tests",
            "`869` entries and `0` names matching",
            "approved GGUF models: `0`",
            "APK/native runtime changes: `0`",
        ).forEach { control -> assertTrue(evidence.contains(control)) }
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
