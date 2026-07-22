package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoBenchSourceMonitoringTriageTest {
    @Test
    fun `DiscoBench becomes a short measurable source triage checklist`() {
        val knowledge = projectFile(
            "docs/knowledge/discobench-source-monitoring-triage-checklist.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-1a64df4ff985ceb0-verification.md",
        ).readText()

        val ambiguityHeadings = Regex(
            pattern = "^### \\d\\. (Entity|Version|Criteria|Factual Inaccuracy)$",
            option = RegexOption.MULTILINE,
        ).findAll(knowledge).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("Entity", "Version", "Criteria", "Factual Inaccuracy"),
            ambiguityHeadings,
        )

        listOf(
            "End-to-end accuracy",
            "Checkpoint pass rate",
            "Detection accuracy",
            "Detection F1",
            "CE-A, clarification-question accuracy",
            "CE-B, clarification-to-advance rate",
            "average Ask turns",
            "tool-use turns",
            "token consumption",
        ).forEach { metric ->
            assertTrue("Missing DiscoBench evaluation metric: $metric", knowledge.contains(metric))
        }

        listOf(
            "Search -> Detect -> Ask -> Search",
            "smallest discriminative clue",
            "needs_clarification",
            "Never turn unresolved ambiguity into a guessed fact",
            "93.4%",
            "56.5%",
            "51.9%",
            "## Minimal audit record",
            "Runtime model-quality improvement remains unmeasured",
        ).forEach { control ->
            assertTrue("Missing SearchThenAsk triage control: $control", knowledge.contains(control))
        }

        listOf(
            "source_ref: source-item/9011e13c06d6/1a64df4ff985ceb0",
            "source_processing_result: discobench_triage_checklist_added_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-1a64df4ff985ceb0-verification.md",
            "source_value:",
            "PASS: 4/4",
            "PASS: 3/3",
            "1/1 focused contract test passed",
            "DiscoBenchSourceMonitoringTriageTest",
            "0 production changes",
        ).forEach { evidence ->
            assertTrue("Missing source-processing verification evidence: $evidence", verification.contains(evidence))
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
}
