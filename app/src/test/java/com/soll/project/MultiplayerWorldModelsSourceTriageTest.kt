package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplayerWorldModelsSourceTriageTest {
    @Test
    fun `world-model signal has a bounded server-side evaluation placement`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "multiplayer-interactive-world-models-with-repres-18709be4",
            "isolated desktop/server research sandbox",
            "synthetic, non-sensitive cooperative environment",
            "deterministic scenario/test baseline",
            "cross-agent consistency",
            "disconnected from production tasks/device control",
            "Integrate with the meta-coordinator only after",
        ).forEach { decision ->
            assertTrue("Missing multiplayer world-model triage decision: $decision", roadmap.contains(decision))
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
