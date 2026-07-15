package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicContainmentSourceTriageTest {
    @Test
    fun `Anthropic containment signal becomes a measurable Soll security review`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val recommendations = projectFile(
            "docs/security/anthropic-agent-containment-recommendations.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-e1174ebfd950-64af1d1a2fd48283-verification.md",
        ).readText()

        listOf(
            "source-item/e1174ebfd950/64af1d1a2fd48283",
            "docs/security/anthropic-agent-containment-recommendations.md",
            "Android remains the approval and observability client",
            "seven measurable promotion gates",
        ).forEach { decision ->
            assertTrue("Missing containment roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "review_owner: Soll security team",
            "### C1. Isolate every execution job",
            "### C2. Establish trust before config parsing",
            "### C3. Replace broad network access with an egress capability broker",
            "### C4. Give the worker its own short-lived identity",
            "### C5. Keep external content separate from executable authority",
            "### C6. Approve bounded capability leases",
            "### C7. Export containment telemetry out of band",
            "### C8. Preserve trust across memory and multi-agent boundaries",
            "### C9. Red-team the boundary and the custom glue",
            "gap worth treating as P0: chat action metadata",
            "accepts any non-blank action",
            "Production containment changes delivered by this task: **0**",
        ).forEach { control ->
            assertTrue("Missing Soll containment recommendation: $control", recommendations.contains(control))
        }

        listOf(
            "source_processing_result: security_review_completed_recommendations_ready",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-e1174ebfd950-64af1d1a2fd48283-verification.md",
            "9 containment recommendations",
            "7 measurable promotion gates",
            "1 Android trust gap identified",
            "0 production containment changes",
            "AnthropicContainmentSourceTriageTest",
        ).forEach { evidence ->
            assertTrue("Missing containment verification evidence: $evidence", verification.contains(evidence))
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
