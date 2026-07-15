package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrSberEventForecastSourceTriageTest {
    @Test
    fun `event detection research records bounded Soll value and verification`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-94b02ac6da81-f4c9532907757955-verification.md",
        ).readText()

        listOf(
            "source-item/94b02ac6da81/f4c9532907757955",
            "AssistantEvent.type/source/createdAt",
            "temporal IoU",
            "single-class frequency baseline",
            "research-only and is not wired into notifications",
        ).forEach { decision ->
            assertTrue("Missing event-forecast decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "source_processing_result: prototype_validated_on_synthetic_soll_events",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-94b02ac6da81-f4c9532907757955-verification.md",
            "prototype F1 `0.9412`",
            "baseline F1 `0.6154`",
            "synthetic Soll-shaped holdout",
            "No production forecast value is claimed",
        ).forEach { evidence ->
            assertTrue("Missing event-forecast verification evidence: $evidence", verification.contains(evidence))
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
