package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IkceLongHorizonRolloutDiagnosticKnowledgeTest {
    @Test
    fun `iKCE signal is attached as diagnosis only and never as a controller`() {
        val knowledgeRaw = projectFile(
            "docs/knowledge/ikce-long-horizon-world-model-rollout-diagnostic.md",
        ).readText()
        val knowledge = knowledgeRaw.normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-785c12f7c946fcd9-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: ab2aa53a1dc1411980b8e1142323022a",
            "source_ref: source-item/9011e13c06d6/785c12f7c946fcd9",
            "https://huggingface.co/papers/2607.05966",
            "https://arxiv.org/abs/2607.05966",
            "arxiv:2607.05966v1",
            "20260709-230009-imagined-rollouts-are-kinematic-not-dynamic-a-di-a125bdae.md",
            "not present in this isolated worktree",
            "imagined Kinematic-Consistency Error (iKCE)",
            "closed-form kinematic null",
            "kinematic-not-dynamic signature",
            "regime-invariance, not an absolute score",
            "A low iKCE does not prove dynamic understanding",
            "## Six-step offline evaluation pattern",
            "horizon longer than the system's characteristic motion period",
            "iKCE slope and confidence interval",
            "## Interpretation and safety guards",
            "generated trajectories cannot become robot, vehicle, gadget or navigation commands",
            "not a controller, a safety certificate or evidence that a rollout is safe",
            "Measured Soll model-quality improvement remains `0`",
        ).forEach { control ->
            assertTrue("Missing iKCE knowledge control: $control", knowledge.contains(control))
        }

        assertEquals(
            "Offline evaluation step count drifted",
            6,
            Regex("(?m)^[1-6]\\. ").findAll(
                knowledgeRaw.substringAfter("## Six-step offline evaluation pattern")
                    .substringBefore("Minimum report fields"),
            ).count(),
        )
        assertEquals(
            "Interpretation/safety guard count drifted",
            6,
            Regex("(?m)^[1-6]\\. ").findAll(
                knowledgeRaw.substringAfter("## Interpretation and safety guards")
                    .substringBefore("This boundary is deliberate"),
            ).count(),
        )

        listOf(
            "task_id: ab2aa53a1dc1411980b8e1142323022a",
            "project: soll_app",
            "source_ref: source-item/9011e13c06d6/785c12f7c946fcd9",
            "source_processing_result: ikce_research_note_added_diagnostic_only",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-785c12f7c946fcd9-verification.md",
            "source_value:",
            "1 iKCE research note",
            "6 offline evaluation steps",
            "6 interpretation/safety guards",
            "1/1 focused contract test passed",
            "0 model/simulator/rollout runs",
            "0 robot or autonomous-control actions",
            "IkceLongHorizonRolloutDiagnosticKnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing iKCE verification evidence: $evidence", verification.contains(evidence))
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
