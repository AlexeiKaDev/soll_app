package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergenceWorldSourceTriageTest {
    @Test
    fun `Emergence World signal extends the server eval contour without becoming a model ranking`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "emergence-world-0936930e",
            "must not rank or select Soll providers",
            "existing AgenticDataBench desktop/server eval harness",
            "long-horizon soak/resilience layer",
            "deterministic, non-sensitive source-monitoring/KB scenario",
            "memory writes/retrievals and stale or contradictory recall",
            "at least three repeated runs per configuration",
            "runtime capability/approval gates",
            "must not execute the soak test or hold provider credentials",
        ).forEach { decision ->
            assertTrue("Missing Emergence World triage decision: $decision", roadmap.contains(decision))
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
