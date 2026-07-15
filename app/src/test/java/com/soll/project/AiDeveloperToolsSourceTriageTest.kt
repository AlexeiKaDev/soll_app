package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDeveloperToolsSourceTriageTest {
    @Test
    fun `review-only AI developer tools signal has an evidence-gated placement plan`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "ai-developer-tools-a5895398",
            "near-duplicate of `ai-0ad60b3f`",
            "3-5 representative tasks",
            "current `rg`/manual baseline",
            "no automatic deploy",
            "explicit approve/reject actions",
        ).forEach { decision ->
            assertTrue("Missing AI developer-tools triage decision: $decision", roadmap.contains(decision))
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
