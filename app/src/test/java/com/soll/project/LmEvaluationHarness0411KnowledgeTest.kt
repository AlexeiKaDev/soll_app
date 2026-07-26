package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LmEvaluationHarness0411KnowledgeTest {
    @Test
    fun `v0 4 11 results retain task versions and reject invalid comparisons`() {
        val knowledge = projectFile(
            "docs/knowledge/lm-evaluation-harness-v0-4-11-result-comparability.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-47425428d2cf-361bfe3d58165f4f-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 747be3f0cb7e4a079ab504cdb4de20a4",
            "project: soll_app",
            "source_ref: source-item/47425428d2cf/361bfe3d58165f4f",
            "source_trust: untrusted_external_content",
            "release: v0.4.11",
            "harness_version: 0.4.11",
            "effective `task_version`",
            "Do not compare directly against v0.4.10 results",
            "`afrobench_belebele`",
            "`evalita_llm`",
            "`include`",
            "`mgsm_direct`",
            "`historical_not_comparable`",
            "`task_version_unresolved`",
            "eight provenance groups",
            "rerun the baseline workload with v0.4.11",
            "disable network access",
            "perform no security or penetration testing",
            "Windows ML support is a desktop/server concern",
            "raw monitored capture is not present",
            "Actual Windows ML backend runs: **0**",
            "https://github.com/EleutherAI/lm-evaluation-harness/releases/tag/v0.4.11",
        ).forEach { control ->
            assertTrue("Missing lm-evaluation-harness control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: 747be3f0cb7e4a079ab504cdb4de20a4",
            "project: soll_app",
            "source_ref: source-item/47425428d2cf/361bfe3d58165f4f",
            "source_processing_result: knowledge_note_added_comparability_guard_recorded",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-47425428d2cf-361bfe3d58165f4f-verification.md",
            "1 KB note",
            "4 task-family comparison guards",
            "8 provenance groups",
            "1 bounded Windows ML smoke contract",
            "1/1 focused contract test passed",
            "0 harness/backend runs",
            "LmEvaluationHarness0411KnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing lm-evaluation-harness audit evidence: $evidence", verification.contains(evidence))
        }

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
            projectFile("gradle/libs.versions.toml"),
        ).joinToString("\n") { it.readText() }
        listOf("lm-evaluation-harness", "onnxruntime-genai", "windows ml").forEach { dependency ->
            assertFalse(
                "Knowledge-only task must not add runtime dependency $dependency",
                gradleInputs.contains(dependency, ignoreCase = true),
            )
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
