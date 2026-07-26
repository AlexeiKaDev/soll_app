package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRaceCoachSafePatternsKnowledgeTest {
    @Test
    fun `race coach signal is retained as safe patterns and audited evidence only`() {
        val knowledge = projectFile(
            "docs/knowledge/ai-race-coach-safe-patterns.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-3689d0cc6ff347b5a79ca0196da72ab9-ai-race-coach-safe-patterns-audit.md",
        ).readText()

        listOf(
            "Task | `3689d0cc6ff347b5a79ca0196da72ab9`",
            "Source reference | `insight/231da40935d9`",
            "monitored/google-developers-blog/20260709-204007-bridging-the-domain-gap-ai-race-coach-built-with-3a06c56a.md",
            "case-study signal, not a production specification",
            "## Six safe patterns",
            "Split the latency paths",
            "Make degraded operation explicit",
            "Ground advice before delivery",
            "Bound local alerts",
            "Measure the telemetry path without copying sensitive payloads",
            "Promote from replay, not from a demo",
            "It never reaches a device",
            "`GadgetCloudSnapshot`, `GadgetPayloadParser` and `GadgetSensorCatalog`",
            "`SollNotificationChannel.ALERTS`",
            "`TextToSpeechManager`",
            "`SollServerSyncWorker`",
            "## Six adoption claims deliberately excluded",
            "No automatic cloud sync or raw telemetry upload is enabled",
            "require `0` actuator commands and `0` raw payload uploads",
            "live alerts and on-device inference runs are all `0`",
        ).forEach { control ->
            assertTrue("Missing safe Race Coach control: $control", knowledge.contains(control))
        }
        assertEquals(
            "Safe-pattern count drifted",
            6,
            Regex("(?m)^\\*\\*[1-6]\\.").findAll(knowledge).count(),
        )

        listOf(
            "task_id: 3689d0cc6ff347b5a79ca0196da72ab9",
            "source_ref: insight/231da40935d9",
            "source_processing_result: research_note_added_safe_patterns_only",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-3689d0cc6ff347b5a79ca0196da72ab9-ai-race-coach-safe-patterns-audit.md",
            "1 Soll app research note added",
            "6 safe patterns extracted",
            "4 existing seams audited",
            "6 adoption claims excluded",
            "1/1 focused contract test passed",
            "0 production files changed",
            "AiRaceCoachSafePatternsKnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing Race Coach audit evidence: $evidence", verification.contains(evidence))
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
