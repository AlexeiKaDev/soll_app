package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9945ChatTemplateSmokeTest {
    @Test
    fun `active llama cpp loads the approved model with a non-standard chat template`() {
        val config = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_active_defaults.json").readText(),
        )
        val allowlist = JSONObject(
            projectFile("tools/llama-cpp/approved_models.json").readText(),
        )
        val template = projectFile(
            "tools/llama-cpp/soll-nonstandard-chat-template.jinja",
        ).readText()
        val smoke = projectFile(
            "tools/llama-cpp/Test-LlamaCppB9945ChatTemplate.ps1",
        ).readText()
        val readme = projectFile("tools/llama-cpp/README.md").readText()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-2e41768d5606f508-verification.md",
        ).readText().normalizeWhitespace()

        val release = config.getJSONObject("release")
        assertEquals("b10068", release.getString("tag"))
        assertEquals(
            "571d0d540df04f25298d0e159e520d9fc62ed121",
            release.getString("commit"),
        )
        val policy = config.getJSONObject("policy")
        assertEquals(9945, policy.getInt("minimumChatTemplateFixRelease"))
        assertTrue(release.getString("tag").removePrefix("b").toInt() >= 9945)
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))

        assertEquals("deny_unlisted", allowlist.getString("policy"))
        val models = allowlist.getJSONArray("models")
        assertEquals(1, models.length())
        val model = models.getJSONObject(0)
        assertEquals("stories15M-q8_0.gguf", model.getString("fileName"))
        assertEquals(26671328L, model.getLong("bytes"))
        assertEquals(
            "2eda49203f2f044f3dddf29a7dd7cc861ef5a0340f518a19613d73ba6d9c06b6",
            model.getString("sha256"),
        )
        assertEquals(
            "def3e2dd70df35ecbf6403ea347de4c5977220c1",
            model.getString("revision"),
        )
        assertEquals("b9945-chat-template-smoke-only", model.getString("purpose"))

        listOf(
            "<|soll_{{ message['role'] }}|>",
            "<|soll_end|>",
            "<|soll_assistant|>",
            "messages",
            "add_generation_prompt",
        ).forEach { marker ->
            assertTrue("Missing non-standard template marker: $marker", template.contains(marker))
        }

        listOf(
            "Test-LlamaCppActiveRelease.ps1",
            "Test-LlamaCppModelProvenance.ps1",
            "llama-cli.exe",
            "--chat-template-file",
            "soll-nonstandard-chat-template.jinja",
            "chat template, example_format:",
            "llama_server: model loaded",
            "SIGABRT|abort has been called",
            "nonStandardTemplateApplied = ${'$'}true",
            "modelLoaded = ${'$'}true",
        ).forEach { control ->
            assertTrue("Missing executable smoke control: $control", smoke.contains(control))
        }
        assertTrue(readme.contains("Test-LlamaCppB9945ChatTemplate.ps1"))

        listOf(
            "task_id: c5f1f18e21e8489eb4b55e6dce49fbaf",
            "source_ref: source-item/d0cd9479f2a2/2e41768d5606f508",
            "source_processing_result: llama_cpp_b9945_chat_template_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-2e41768d5606f508-verification.md",
            "source_value: accepted",
            "b10068",
            "82fce65d8be40ba55048e06f2e14a01deb363d41",
            "`123` commits ahead and `0` behind b9945",
            "`modelLoaded: true`",
            "`nonStandardTemplateApplied: true`",
            "`exitCode: 0`",
            "`419/419` tests passed",
            "LlamaCppB9945ChatTemplateSmokeTest",
        ).forEach { evidence ->
            assertTrue("Missing b9945 verification evidence: $evidence", artifact.contains(evidence))
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
