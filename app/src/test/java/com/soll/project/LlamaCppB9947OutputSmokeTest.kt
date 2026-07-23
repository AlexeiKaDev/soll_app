package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9947OutputSmokeTest {
    @Test
    fun `active llama cli saves a safe inference artifact without stdout parsing`() {
        val config = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_active_defaults.json").readText(),
        )
        val allowlist = JSONObject(
            projectFile("tools/llama-cpp/approved_models.json").readText(),
        )
        val smoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppB9947Output.ps1",
        ).readText()
        val readme = projectFile("tools/llama-cpp/README.md").readText()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-01c5f16863b3ae97-verification.md",
        ).readText().normalizeWhitespace()

        val release = config.getJSONObject("release")
        assertEquals("b10068", release.getString("tag"))
        assertEquals(
            "571d0d540df04f25298d0e159e520d9fc62ed121",
            release.getString("commit"),
        )
        val policy = config.getJSONObject("policy")
        assertEquals(9947, policy.getInt("minimumOutputFileRelease"))
        assertTrue(release.getString("tag").removePrefix("b").toInt() >= 9947)
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))

        assertEquals("deny_unlisted", allowlist.getString("policy"))
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
                .contains("b9947-output-file-smoke"),
        )

        listOf(
            "Test-LlamaCppActiveRelease.ps1",
            "Test-LlamaCppModelProvenance.ps1",
            "llama-cli.exe",
            "minimumOutputFileRelease",
            "\"b9947-output-file-smoke\"",
            "\"--offline\"",
            "\"--output\"",
            "\"1\"",
            "RedirectStandardOutput = ${'$'}false",
            "RedirectStandardError = ${'$'}false",
            "stdoutParsed = ${'$'}false",
            "userPromptPersisted = ${'$'}true",
            "assistantContentPersisted = ${'$'}true",
        ).forEach { control ->
            assertTrue("Missing executable output-smoke control: $control", smoke.contains(control))
        }
        assertTrue(readme.contains("Test-LlamaCppB9947Output.ps1"))
        assertTrue(readme.contains("without parsing inference stdout"))

        listOf(
            "task_id: bee0b2b4e7fb4baa9be6a2496e05e34d",
            "source_ref: source-item/d0cd9479f2a2/01c5f16863b3ae97",
            "source_processing_result: llama_cpp_b9947_output_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-01c5f16863b3ae97-verification.md",
            "source_value: accepted",
            "3de7dd4c8f5d9806279249310b6c3db24a1a67ab",
            "`121` commits ahead and `0` behind b9947",
            "`stdoutParsed: false`",
            "`userPromptPersisted: true`",
            "`assistantContentPersisted: true`",
            "`exitCode: 0`",
            "`420/420` tests passed",
            "LlamaCppB9947OutputSmokeTest",
        ).forEach { evidence ->
            assertTrue("Missing b9947 output verification evidence: $evidence", artifact.contains(evidence))
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
