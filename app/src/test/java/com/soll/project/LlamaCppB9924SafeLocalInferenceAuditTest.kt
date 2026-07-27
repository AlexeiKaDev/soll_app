package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9924SafeLocalInferenceAuditTest {
    @Test
    fun `b9924 Android package is ABI compatible but must not replace active runtime`() {
        val note = projectFile(
            "docs/knowledge/llama-cpp-b9924-safe-local-inference-audit.md",
        ).readText().normalizeWhitespace()
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-04ee2c4d8c554c7f-verification.md",
        ).readText().normalizeWhitespace()
        val active = JSONObject(
            projectFile("tools/llama-cpp/llama_cpp_active_defaults.json").readText(),
        )
        val productionSource = projectFile("app/src/main")
            .walkTopDown()
            .filter(File::isFile)
            .filter { it.extension in setOf("kt", "java", "kts", "xml") }
            .joinToString(separator = "\n") { it.readText() }

        listOf(
            "raw/monitored/llama-cpp-releases/20260708-223009-b9924-de42dc6d.md",
            "https://github.com/ggml-org/llama.cpp/releases/tag/b9924",
            "90e0f5cfcb6cdb4b7b60a4f81b0a26e542149ad5",
            "PR #24646",
            "`4` commits",
            "`6` source files",
            "`122` additions, `131` deletions",
            "llm_graph_fused_node",
            "resolve_fused_ops",
            "model.dev_layer()",
            "--no-kv-offload",
            "issue #25644",
            "`Not a regression`",
            "`25/25` successful checks",
            "`3/3` success",
            "failed Ubuntu x64 `Test`",
            "failed `Python setup`",
            "llama-b9924-bin-android-arm64.tar.gz",
            "`78812406` bytes",
            "018f1db4fced30044b90f95b44ab6a18d439142e5d3a125b5b5ec5a0a06d4ad5",
            "`44/44` binary files",
            "`e_machine = 0xB7` AArch64",
            "`llama-cli`, `llama-server` и `libllama.so`",
            "`144` commits ahead and `0` behind",
            "minimumChatTemplateFixRelease = 9945",
            "`adb` на worker unavailable",
            "`0` device runs и `0` model inference runs",
            "`llama-server` присутствует в архиве, но не запускался",
            "сетевые или autonomous agent capabilities",
            "`0` production/runtime",
        ).forEach { control ->
            assertTrue("Missing b9924 safe-inference control: $control", note.contains(control))
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
        assertEquals(9945, policy.getInt("minimumChatTemplateFixRelease"))
        assertTrue(policy.getBoolean("verifySha256"))
        assertTrue(policy.getBoolean("requireModelAllowlist"))
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))

        val targets = active.getJSONArray("targets")
        val androidCpuTargets = (0 until targets.length())
            .map { targets.getJSONObject(it) }
            .filter {
                it.getString("id") == "android-arm64-cpu" &&
                    it.getString("backend") == "cpu"
            }
        assertEquals(1, androidCpuTargets.size)
        assertFalse(active.toString().contains("\"tag\":\"b9924\""))
        assertFalse(productionSource.contains("llama-b9924"))
        assertFalse(productionSource.contains("libllama.so"))

        listOf(
            "task_id: 6b3d4519fd40430ba1627de15dee3d40",
            "project: soll_app",
            "source_ref: source-item/d0cd9479f2a2/04ee2c4d8c554c7f",
            "source_processing_result: " +
                "android_arm64_package_verified_runtime_update_rejected_as_downgrade",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-04ee2c4d8c554c7f-verification.md",
            "1 focused audit",
            "25/25 PR checks and 5/5 Android/release jobs verified",
            "44/44 Android binaries passed ELF64 AArch64 validation",
            "0/869 APK entries matched b9924",
            "active b10068 verified 144 commits ahead",
            "LlamaCppB9924SafeLocalInferenceAuditTest",
            "1/1 focused contract test passed",
            "0 production/runtime changes",
            "0 device/model inference runs",
        ).forEach { evidence ->
            assertTrue("Missing b9924 verification evidence: $evidence", artifact.contains(evidence))
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
