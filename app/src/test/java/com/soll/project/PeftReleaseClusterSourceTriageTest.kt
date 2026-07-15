package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PeftReleaseClusterSourceTriageTest {
    @Test
    fun `repeated PEFT releases remain one prerequisite-gated adapter cluster`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val status = projectFile("soll_status.md").readText()

        listOf(
            "peft-v015-release",
            "peft-v016-release",
            "peft-v017-release",
            "peft-v018-release",
            "single deferred adapter-routing cluster",
            "must not create duplicate Today work",
            "local PEFT/LoRA adapter registry",
            "adapter-serving profile",
            "base-vs-adapter eval suite",
            "rollback/provenance policy",
            "Soll/outputs/source-processing/peft-adapter-routing-cluster-20260713.md",
            "Do not add PEFT, LoRA weights, adapter selection or model loading to Android",
        ).forEach { decision ->
            assertTrue("Missing PEFT cluster decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "source-item/f0e146e58291/b7249e78e8eb4240",
            "source-item/f0e146e58291/69416b3a5ebac78f",
            "source-item/f0e146e58291/46071db2129b00dd",
            "source-item/f0e146e58291/32a8bf180007d702",
            "Soll/wiki/peft-v015-release.md",
            "Soll/wiki/peft-v016-release.md",
            "Soll/wiki/peft-v017-release.md",
            "Soll/wiki/peft-v018-release.md",
            "retain the four source references as one deferred adapter-routing cluster",
            "do not create duplicate Today work",
        ).forEach { evidence ->
            assertTrue("Missing PEFT cluster evidence: $evidence", status.contains(evidence))
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
