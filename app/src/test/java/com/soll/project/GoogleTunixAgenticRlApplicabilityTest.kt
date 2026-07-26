package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleTunixAgenticRlApplicabilityTest {
    @Test
    fun `Tunix signal becomes a bounded Soll server experiment candidate`() {
        val knowledge = projectFile(
            "docs/knowledge/google-tunix-agentic-rl-soll-applicability.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-810c7bbad21a46d0a3094cc767e22477-tunix-agentic-rl-audit.md",
        ).readText()
        val androidBuild = projectFile("app/build.gradle.kts").readText().lowercase()

        listOf(
            "task_id: 810c7bbad21a46d0a3094cc767e22477",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/3223b4da61f2",
            "source_trust: untrusted_external_content",
            "section: LLM/post-training",
            "monitored/google-developers-blog/" +
                "20260723-223002-scaling-agentic-rl-high-throughput-agentic-train-28c30f53.md",
            "https://developers.googleblog.com/" +
                "scaling-agentic-rl-high-throughput-agentic-training-with-tunix/",
            "**Applicable to Soll app workflow**",
            "## Four relevant capability groups",
            "**Asynchronous rollouts.**",
            "**Barrier-free training pipeline.**",
            "**Composable agent and environment boundary.**",
            "**Continuous RL pipeline telemetry.**",
            "`app/build.gradle.kts`",
            "`SollGateway.askModelChat(...)`",
            "`docs/knowledge/soll-source-monitoring-kb-eval-v1.json`",
            "`docs/knowledge/hugging-face-trl-v1-8-0-rl-experiment-reference.md`",
            "## One framework-selection smoke",
            "completed trajectories per second",
            "accelerator idle-time fraction",
            "trainer input-starvation fraction",
            "unsafe side-effect and cross-environment tool-leak count",
            "## Observed value and limits",
            "Training or inference runs: **0**",
            "Android production files and runtime contracts changed:",
            "**0**",
        ).forEach { control ->
            assertTrue("Missing Tunix applicability control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: " +
                "applicable_to_bounded_server_experiment_runtime_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-810c7bbad21a46d0a3094cc767e22477-tunix-agentic-rl-audit.md",
            "1 Tunix applicability note added",
            "4 upstream capability groups captured",
            "4 current Soll seams audited",
            "1 framework-selection smoke with 8 metrics and 7 gates defined",
            "1/1 focused contract test passed",
            "0 Tunix/JAX installs or training runs",
            "0 Android production/runtime changes",
            "GoogleTunixAgenticRlApplicabilityTest",
        ).forEach { evidence ->
            assertTrue("Missing Tunix audit evidence: $evidence", verification.contains(evidence))
        }

        assertFalse("Android must not depend directly on Tunix", androidBuild.contains("google:tunix"))
        assertFalse("Android must not depend directly on JAX", androidBuild.contains("jaxlib"))
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
}
