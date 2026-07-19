package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SweReviewLocalDiffPilotTest {
    @Test
    fun `SWE Review signal becomes a bounded own-diff human-gated pilot`() {
        val knowledge = projectFile(
            "docs/knowledge/swe-review-local-diff-pilot.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-95d3fc37e0d98731-verification.md",
        ).readText()

        listOf(
            "source-item/9011e13c06d6/95d3fc37e0d98731",
            "SWE-Review: Closing the Loop on Issue Resolution with Agentic Code Review",
            "review -> revise -> test -> human approval",
            "e9931cb9c1912b5217d835a15d13dec183c11420",
            "Точный own-diff manifest пилота",
            "app/src/test/java/com/soll/project/SweReviewLocalDiffPilotTest.kt",
            "отсутствует и в корне worktree, и под `Soll/raw`",
            "внешний поиск и скачивание не выполнялись",
            "secret/config/profile reads = 0",
            "network, web, MCP/connectors и внешнее сканирование = 0",
            "commit, push, deploy, PR, branch/tag и auto-merge = 0",
            "accept_for_human_review",
            "human_approval_required: true",
        ).forEach { control ->
            assertTrue("Missing SWE-Review pilot control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: " +
                "knowledge_note_added_local_own_diff_pilot_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-95d3fc37e0d98731-verification.md",
            "source_value:",
            "1 short knowledge note",
            "3-file own-diff manifest",
            "9 mandatory safety gates",
            "1/1 focused contract test passed",
            "0 external scans",
            "0 secret reads",
            "0 auto-merges",
            "SweReviewLocalDiffPilotTest",
        ).forEach { evidence ->
            assertTrue("Missing SWE-Review verification evidence: $evidence", verification.contains(evidence))
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
