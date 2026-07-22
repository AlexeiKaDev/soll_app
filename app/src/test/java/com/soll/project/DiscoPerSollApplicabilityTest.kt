package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoPerSollApplicabilityTest {
    @Test
    fun `DiscoPER paper becomes a bounded reflect only Soll proposal`() {
        val analysis = projectFile(
            "docs/knowledge/discoper-iterative-meta-reflection-soll-applicability.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-8780e805a5e2ff9d-verification.md",
        ).readText().normalizeWhitespace()
        val gateway = projectFile(
            "app/src/main/java/com/soll/domain/soll/SollGateway.kt",
        ).readText()

        listOf(
            "task_id: 003292f1865441999e0567eac5f521b3",
            "source_ref: source-item/9011e13c06d6/8780e805a5e2ff9d",
            "source_version: arxiv:2607.01131v1",
            "3,085,273 bytes",
            "2e332008e059b144a6527cf97e06751165effd966d2d2110a8aa10841f5fb980",
            "2,581,504 bytes",
            "21edcf7b5df99f66fca0e83c204b134b5118fcf907903525a09c6027eac806ce",
            "## Формальная модель алгоритма",
            "abs(delta_validation) >= 0.6 * abs(delta_train)",
            "100 итераций, одна гипотеза на итерацию, reflection каждые 5 итераций",
            "Adaptive holdout reuse",
            "Multiple testing",
            "Specification drift",
            "## Bounded Soll prototype: Source Meta-Reflection Audit",
            "Benjamini–Hochberg FDR `q <= 0.05`",
            "audit precision не ниже `0.80`",
            "unique useful findings минимум на `20%` выше обоих baselines",
            "Android остаётся review client",
            "0` arbitrary-code executions",
            "Измеренная runtime/model-quality ценность пока равна `0`",
        ).forEach { evidence ->
            assertTrue("Missing DiscoPER analysis evidence: $evidence", analysis.contains(evidence))
        }

        val statisticalTools = listOf(
            "corr_test",
            "group_diff_test",
            "predictive_test",
            "cluster_and_enrich",
            "stratified_retest",
            "visual_attribute_test",
            "visual_group_comparison",
        )
        assertEquals(
            "DiscoPER statistical primitive count drifted",
            7,
            statisticalTools.count { tool -> analysis.contains("`$tool`") },
        )

        listOf(
            "Confound Pattern",
            "Variable Cluster",
            "Gap",
            "Success Pattern",
            "Contradiction",
            "Interaction Hint",
        ).forEach { metaType ->
            assertTrue("Missing DiscoPER meta-insight type: $metaType", analysis.contains(metaType))
        }

        listOf(
            "data class SollLearningItem(",
            "data class SollSourceItem(",
            "val verificationArtifact: String",
            "data class SollTask(",
            "val valueMetric: String",
            "val approvalId: String?",
            "val acceptanceCriteria: List<String>",
            "val testPlan: List<String>",
        ).forEach { contract ->
            assertTrue("Missing current Soll review contract: $contract", gateway.contains(contract))
        }

        listOf(
            "task_id: 003292f1865441999e0567eac5f521b3",
            "source_ref: source-item/9011e13c06d6/8780e805a5e2ff9d",
            "source_processing_result: full_paper_downloaded_algorithm_reviewed_soll_reflect_only_prototype_scoped",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-8780e805a5e2ff9d-verification.md",
            "source_value:",
            "1 full PDF plus TeX source downloaded and SHA-256 verified",
            "3-stage loop, 7 statistical primitives and 6 meta-insight types",
            "3 Soll adoption contours",
            "1/1 focused contract test passed",
            "0 external agent/model runs, arbitrary-code executions or production/runtime changes",
            "DiscoPerSollApplicabilityTest",
        ).forEach { evidence ->
            assertTrue("Missing DiscoPER verification evidence: $evidence", verification.contains(evidence))
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
