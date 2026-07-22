package com.soll.project

import java.io.File
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceOptimizationBenchmarkReliabilityTest {
    @Test
    fun `performance benchmark paper becomes a bounded Soll reliability scorecard`() {
        val knowledge = projectFile(
            "docs/knowledge/performance-optimization-benchmark-reliability.md",
        ).readText().normalizeWhitespace()
        val contract = JSONObject(
            projectFile(
                "docs/knowledge/performance-optimization-benchmark-reliability-v1.json",
            ).readText(),
        )
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-4dd7eedded0ca608-verification.md",
        ).readText().normalizeWhitespace()

        assertEquals(1, contract.getInt("schema_version"))
        assertEquals(
            "soll-performance-optimization-benchmark-reliability-v1",
            contract.getString("contract_id"),
        )
        val source = contract.getJSONObject("source")
        assertEquals("arxiv:2607.01211v2", source.getString("version"))
        assertEquals(
            "158e3baf87b42faa481ce5b53f82c94618c61f014ef37c9b63b858cd997f942a",
            source.getString("pdf_sha256"),
        )
        assertEquals(
            "f3fcc2ebab992c6c5041581a8ee0759286c78c11b47407cf94c18342f0131860",
            source.getString("source_sha256"),
        )

        val scope = contract.getJSONObject("scope")
        assertEquals("offline_proposal_only", scope.getString("mode"))
        listOf(
            "executes_agents",
            "executes_arbitrary_commands_from_input",
            "uses_network_or_credentials",
            "mutates_production",
        ).forEach { flag -> assertFalse("Unsafe scope flag: $flag", scope.getBoolean(flag)) }

        val eligibility = contract.getJSONObject("task_eligibility")
        assertEquals(
            "only_reference_replay_valid_tasks",
            eligibility.getString("denominator_policy"),
        )
        val requiredChecks = eligibility.getJSONArray("required_boolean_checks").toStringSet()
        assertEquals(
            setOf(
                "base_correctness_passed",
                "reference_correctness_passed",
                "reference_faster_than_base_all_replays",
                "reference_original_rule_valid_all_replays",
            ),
            requiredChecks,
        )
        val replayDesign = eligibility.getJSONObject("promotion_replay_design")
        assertEquals(2, replayDesign.getInt("machine_profiles_min"))
        assertEquals(3, replayDesign.getInt("rounds_per_machine_min"))
        assertEquals("diagnostic_only", replayDesign.getString("single_machine_result_label"))

        assertEquals(8, contract.getJSONArray("task_metrics").length())
        assertEquals(14, contract.getJSONArray("aggregate_metrics").length())
        assertEquals(6, contract.getJSONArray("resource_guard_metrics").length())
        assertEquals(5, contract.getJSONArray("reporting_rules").length())
        assertEquals(7, contract.getJSONArray("promotion_gates").length())

        listOf(
            "candidate_correctness_rate",
            "faster_than_base_rate",
            "reference_level_coverage",
            "median_speedup_ratio",
            "harmonic_mean_speedup_ratio_bounded_floor_0_5_diagnostic",
            "worst_k_denominator_weight",
            "spearman_rank_correlation",
            "pairwise_rank_flip_count",
            "fleet_any_reference_level_rate",
        ).forEach { metric ->
            assertTrue(
                "Missing aggregate metric: $metric",
                contract.getJSONArray("aggregate_metrics").toStringSet().contains(metric),
            )
        }

        val smoke = contract.getJSONObject("synthetic_smoke")
        val allTasks = smoke.getJSONArray("tasks").toObjectList()
        val eligibleTasks = allTasks.filter { task ->
            val checks = task.getJSONObject("eligibility")
            requiredChecks.all { check -> checks.getBoolean(check) }
        }
        val expected = smoke.getJSONObject("expected_metrics")
        assertEquals(expected.getInt("eligible_task_count"), eligibleTasks.size)
        assertEquals(
            expected.getInt("excluded_unstable_task_count"),
            allTasks.size - eligibleTasks.size,
        )
        assertEquals(
            setOf(
                "stable_reference_beaten",
                "stable_partial_speedup",
                "fast_but_incorrect",
            ),
            eligibleTasks.map { it.getString("id") }.toSet(),
        )
        assertEquals(
            "unstable_reference_excluded",
            allTasks.single { it !in eligibleTasks }.getString("id"),
        )

        val results = eligibleTasks.map { task ->
            val base = task.getJSONArray("base_runtime_ms").mean()
            val reference = task.getJSONArray("reference_runtime_ms").mean()
            val candidate = task.getJSONArray("candidate_runtime_ms").mean()
            val correct = task.getBoolean("candidate_correctness_passed")
            val rawSpeedupRatio = (base / candidate) / (base / reference)
            SyntheticResult(
                correct = correct,
                fasterThanBase = correct && candidate < base,
                referenceLevel = correct && candidate <= reference,
                effectiveSpeedupRatio = if (correct) rawSpeedupRatio else 0.0,
            )
        }

        assertEquals(
            expected.getDouble("candidate_correctness_rate"),
            results.count { it.correct }.toDouble() / results.size,
            EPSILON,
        )
        assertEquals(
            expected.getDouble("faster_than_base_rate"),
            results.count { it.fasterThanBase }.toDouble() / results.size,
            EPSILON,
        )
        assertEquals(
            expected.getDouble("reference_level_coverage"),
            results.count { it.referenceLevel }.toDouble() / results.size,
            EPSILON,
        )

        val speedupRatios = results.map { it.effectiveSpeedupRatio }
        assertEquals(
            expected.getDouble("median_speedup_ratio"),
            speedupRatios.median(),
            EPSILON,
        )
        assertEquals(
            expected.getDouble("official_harmonic_mean_floor_0_001"),
            speedupRatios.harmonicMeanWithFloor(0.001),
            EPSILON,
        )
        assertEquals(
            expected.getDouble("bounded_harmonic_mean_floor_0_5"),
            speedupRatios.harmonicMeanWithFloor(0.5),
            EPSILON,
        )
        val officialDenominators = speedupRatios.map { ratio -> 1.0 / max(ratio, 0.001) }
        assertEquals(
            expected.getDouble("official_worst_1_denominator_weight"),
            officialDenominators.maxOrNull()!! / officialDenominators.sum(),
            EPSILON,
        )

        listOf(
            "task_id: 49b3763d37674cc39779d1e7f3e3581e",
            "source_ref: source-item/9011e13c06d6/4dd7eedded0ca608",
            "source_version: arxiv:2607.01211v2",
            "12-страничную статью",
            "415,035 bytes",
            "238,382 bytes",
            "все 24 archive entries",
            "39/102",
            "11/140",
            "411/498",
            "worst-1/5/10 denominator weight",
            "384/450",
            "fleet/task coverage",
            "correctness остаётся hard gate",
            "single-machine result маркируется только `diagnostic_only`",
            "Измеренные agent runs, repository benchmarks",
            "`0`",
        ).forEach { evidence ->
            assertTrue("Missing paper analysis evidence: $evidence", knowledge.contains(evidence))
        }

        listOf(
            "task_id: 49b3763d37674cc39779d1e7f3e3581e",
            "source_ref: source-item/9011e13c06d6/4dd7eedded0ca608",
            "source_processing_result: " +
                "full_paper_v2_downloaded_metrics_audited_soll_reliability_contract_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-4dd7eedded0ca608-verification.md",
            "source_value:",
            "12-page PDF plus complete TeX source",
            "8 task metrics, 14 aggregate metrics and 6 resource guards",
            "4 synthetic cases",
            "1/1 focused contract test passed",
            "0 agent/model runs, cloud replays, external data imports or runtime changes",
            "PerformanceOptimizationBenchmarkReliabilityTest",
        ).forEach { evidence ->
            assertTrue("Missing verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private data class SyntheticResult(
        val correct: Boolean,
        val fasterThanBase: Boolean,
        val referenceLevel: Boolean,
        val effectiveSpeedupRatio: Double,
    )

    private fun JSONArray.toStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
    }

    private fun JSONArray.toObjectList(): List<JSONObject> = buildList {
        repeat(length()) { index -> add(getJSONObject(index)) }
    }

    private fun JSONArray.mean(): Double =
        (0 until length()).sumOf { index -> getDouble(index) } / length()

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    private fun List<Double>.harmonicMeanWithFloor(floor: Double): Double =
        size / sumOf { value -> 1.0 / max(value, floor) }

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

    private companion object {
        const val EPSILON = 0.000000001
    }
}
