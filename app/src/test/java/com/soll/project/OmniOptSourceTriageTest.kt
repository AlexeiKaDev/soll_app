package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniOptSourceTriageTest {
    @Test
    fun `optimizer signal has a workload-gated server-side evaluation placement`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "omniopt-taxonomy-geometry-and-benchmarking-of-mo-2f762a3f",
            "desktop/server KB and evaluation cookbook",
            "AdamW as the reference",
            "2-3 alternatives by the binding quality/runtime/memory/stability constraint",
            "identical model, data, initialization/seeds, schedule and tuning budget",
            "verify code/license and target-architecture support",
            "must not choose or run training optimizers",
        ).forEach { decision ->
            assertTrue("Missing OmniOpt triage decision: $decision", roadmap.contains(decision))
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
