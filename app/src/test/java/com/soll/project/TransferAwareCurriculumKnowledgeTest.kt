package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferAwareCurriculumKnowledgeTest {
    @Test
    fun `TAC signal becomes a bounded transfer aware Soll ranking note`() {
        val knowledgeRaw = projectFile(
            "docs/knowledge/transfer-aware-curriculum-soll-ranking-hypothesis.md",
        ).readText()
        val knowledge = knowledgeRaw.normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-0efa7e920bd2c89b-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 040135ed3f2a488ba79e18061e5b0007",
            "source_ref: source-item/9011e13c06d6/0efa7e920bd2c89b",
            "https://arxiv.org/abs/2606.25178",
            "https://github.com/YangYongJin/transfer-aware-curriculum",
            "## TAC algorithm",
            "### Learnability metric",
            "`L_i = (1 / B) * sum_b |A_b|`",
            "current within-domain optimization signal",
            "### Transferability metric",
            "`p_i = R g_i / ||R g_i||`",
            "`T_i_raw = mean_{j != i} cosine(h_i, h_j)`",
            "cross-domain min-max transformation",
            "`S_i = beta * L_i_normalized + (1 - beta) * T_i_normalized`",
            "## Safe Soll hypothesis: transfer-aware advisory ranking",
            "Missing reuse evidence scores as unknown, not as success",
            "`Precision@5`, `nDCG@10`, useful-item recall, per-group exposure",
            "nDCG@10` improves by at least `5%` over both baselines",
            "fine-tuning/model-gradient/security-code generation runs: `0`",
            "source-priority and task-board behavior changes: `0`",
        ).forEach { control ->
            assertTrue("Missing TAC knowledge control: $control", knowledge.contains(control))
        }

        assertEquals(
            "TAC stage count drifted",
            5,
            Regex("(?m)^[1-5]\\. ").findAll(
                knowledgeRaw.substringAfter("The upstream procedure has five important parts:")
                    .substringBefore("The two-phase update matters"),
            ).count(),
        )

        listOf(
            "task_id: 040135ed3f2a488ba79e18061e5b0007",
            "source_ref: source-item/9011e13c06d6/0efa7e920bd2c89b",
            "source_processing_result: tac_research_note_added_replay_pilot_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-0efa7e920bd2c89b-verification.md",
            "source_value:",
            "1 TAC research note",
            "2 required signals documented",
            "1 replay-only Soll hypothesis",
            "2/2 primary links returned HTTP 200",
            "1/1 focused contract test passed",
            "0 fine-tuning, security-code generation, or production/runtime changes",
            "TransferAwareCurriculumKnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing TAC verification evidence: $evidence", verification.contains(evidence))
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
