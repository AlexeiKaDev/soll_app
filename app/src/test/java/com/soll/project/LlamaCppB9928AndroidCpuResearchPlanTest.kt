package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9928AndroidCpuResearchPlanTest {
    @Test
    fun `b9928 keeps a safe Android CPU research plan without claiming Hexagon results`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9928-android-arm64-cpu-research-plan.md",
        ).readText().normalizeWhitespace()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-4bbc4cd3077d5d33-verification.md",
        ).readText().normalizeWhitespace()
        val active = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_active_defaults.json").readText(),
        )

        listOf(
            "raw/monitored/llama-cpp-releases/20260708-223009-b9928-0080d667.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9928",
            "81ff7abe50b95fb81cc70a6cdba1eb1a02a48f62",
            "PR #25425",
            "MUL_MAT",
            "MUL_MAT_ID",
            "FLASH_ATTN_EXT",
            "llama-b9928-bin-android-arm64.tar.gz",
            "74325550",
            "f29eb0f1b58b13926965450d9972d12b176855d561c2980777ad669739dffeca",
            "`140` commits ahead and `0` behind",
            "CPU-прогон не измеряет эффект Hexagon kernels",
            "Arm A — current product runtime",
            "Arm B — b9928 Android arm64 CPU",
            "Arm C — active b10068 Android arm64 CPU control",
            "soll-backend-route",
            "synthetic fixture",
            "network-capable agents",
            "security automation",
            "1 warm-up + 5 measured repeats",
            "`3/3` facts",
            "`3/3` citations",
            "`0` unsupported facts",
            "`0` tool/action calls",
            "GGML_HEXAGON=ON",
            "`adb` unavailable",
            "`0` device/model inference runs",
            "`0` production/runtime",
        ).forEach { control ->
            assertTrue("Missing b9928 research-plan control: $control", note.contains(control))
        }

        val release = active.getJSONObject("release")
        assertEquals("b10068", release.getString("tag"))
        assertEquals(
            "571d0d540df04f25298d0e159e520d9fc62ed121",
            release.getString("commit"),
        )

        val policy = active.getJSONObject("policy")
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))
        assertEquals(
            "upstream-harness-or-adb-smoke-only",
            policy.getString("standaloneAndroidBinaryUse"),
        )
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))

        val targets = active.getJSONArray("targets")
        val androidCpuTargets = (0 until targets.length())
            .map { targets.getJSONObject(it) }
            .filter {
                it.getString("id") == "android-arm64-cpu" &&
                    it.getString("backend") == "cpu"
            }
        assertEquals(1, androidCpuTargets.size)
        assertFalse(active.toString().contains("\"tag\":\"b9928\""))

        listOf(
            "task_id: ee8517e9f913409b82e97cf0d9f8e0ce",
            "source_ref: source-item/d0cd9479f2a2/4bbc4cd3077d5d33",
            "source_processing_result: research_plan_documented_cpu_control_cannot_validate_hexagon",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-4bbc4cd3077d5d33-verification.md",
            "1 research note",
            "3 comparison arms",
            "1 synthetic local RAG fixture",
            "5 measured repeats per arm specified",
            "LlamaCppB9928AndroidCpuResearchPlanTest",
            "1/1 focused contract test passed",
            "0 production/runtime changes",
            "0 device/model inference runs",
        ).forEach { evidence ->
            assertTrue("Missing b9928 verification evidence: $evidence", artifact.contains(evidence))
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
