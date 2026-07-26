package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArxivLlmInferenceRoutingShortlistTest {
    @Test
    fun `cs LG signal produces a bounded six paper inference routing shortlist`() {
        val artifact = projectFile(
            "Soll/outputs/source-processing/" +
                "task-531e8b314f7c479cbc4449a253a60ce8-arxiv-llm-inference-routing-audit.md",
        ).readText()
        val shortlist = artifact.substringAfter("## Selected arXiv IDs (6)")
            .substringBefore("## Selection audit")
        val selectedIds = Regex("arXiv:(2607\\.\\d{5})")
            .findAll(shortlist)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(
            "The acceptance criterion requires a concrete 5-7 paper shortlist",
            6,
            selectedIds.size,
        )
        assertEquals(
            setOf(
                "2607.09015",
                "2607.08991",
                "2607.08780",
                "2607.08782",
                "2607.08930",
                "2607.08786",
            ),
            selectedIds,
        )

        listOf(
            "task_id: 531e8b314f7c479cbc4449a253a60ce8",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/7499605e77a7",
            "source_processing_result: six_arxiv_deep_dive_candidates_selected",
            "monitored/arxiv-cs-lg-recent/20260710-230904-machine-learning-27ae12cc.md",
            "not vendored in this isolated worktree",
            "https://arxiv.org/abs/2607.09015",
            "https://arxiv.org/abs/2607.08991",
            "https://arxiv.org/abs/2607.08780",
            "https://arxiv.org/abs/2607.08782",
            "https://arxiv.org/abs/2607.08930",
            "https://arxiv.org/abs/2607.08786",
            "Provider/model policy",
            "Token conditional compute",
            "MoE training/locality",
            "MoE cluster placement",
            "dLLM scheduling",
            "Sparse GPU kernel",
            "`SollGateway.askModelChat(...)` contract",
            "6 specific arXiv IDs selected; 6/6 primary arXiv records verified",
            "1/1 focused contract test passed",
            "paper imports, inference benchmarks and production/runtime changes: `0`",
        ).forEach { evidence ->
            assertTrue("Missing arXiv shortlist evidence: $evidence", artifact.contains(evidence))
        }

        listOf("2607.08940", "2607.08960", "2607.08961").forEach { excludedId ->
            assertFalse(
                "Adjacent source item must not be counted as selected: $excludedId",
                selectedIds.contains(excludedId),
            )
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
