package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PeftMeshTransportSourceTriageTest {
    @Test
    fun `PEFT release is separated from the future Meshtastic transport plan`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "`peft-v016-release`",
            "does not provide a Meshtastic or offline note/task transport design",
            "`HF_HUB_OFFLINE` item tests model loading from a local Hugging Face cache",
            "cross-domain false positive",
            "Do not add PEFT, PyTorch, model training or a second outbox to Android",
            "compact versioned allowlisted envelope",
            "ACK only after durable local note/task insertion",
            "preserve command rejection",
            "validate payload limits, loss, retry and idempotency on real hardware",
            "desktop/server adapter-training evaluation",
        ).forEach { decision ->
            assertTrue("Missing PEFT/mesh transport triage decision: $decision", roadmap.contains(decision))
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
