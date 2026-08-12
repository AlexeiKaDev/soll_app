package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataSpaceCurrentMetricsAlignmentTest {
    @Test
    fun `DataSpace directions are mapped without changing the current Soll suite`() {
        val alignment = JSONObject(
            projectFile("docs/knowledge/dataspace-current-metrics-alignment-v1.json").readText(),
        )
        val currentSuite = JSONObject(
            projectFile("docs/knowledge/soll-source-monitoring-kb-eval-v1.json").readText(),
        )
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-e380bceb922e417391144b806510af1d-dataspace-metrics-alignment-audit.md",
        ).readText().normalizeWhitespace()

        assertEquals("dataspace-current-metrics-alignment-v1", alignment.getString("audit_id"))
        assertEquals("e380bceb922e417391144b806510af1d", alignment.getString("task_id"))
        assertEquals("fdf52463-9152-453a-b186-68e7d76c3edb", alignment.getString("project"))
        assertEquals("insight/d6cd7eaccde2", alignment.getString("source_ref"))
        assertEquals("untrusted_external_content", alignment.getString("source_trust"))

        val source = alignment.getJSONObject("source")
        assertEquals("https://arxiv.org/abs/2608.03451", source.getString("paper_url"))
        assertEquals(410, source.getInt("paper_tasks"))
        assertEquals(7439, source.getInt("paper_artifacts"))
        assertEquals(15.01, source.getDouble("paper_storage_gb"), 0.0)
        assertEquals(
            setOf("csv", "json", "sqlite", "markdown", "pdf", "video"),
            source.getJSONArray("paper_carriers").toStringSet(),
        )
        assertEquals("task_accuracy", source.getString("paper_primary_metric"))
        assertEquals(0.6634, source.getDouble("best_reported_task_accuracy"), 0.0)

        val expectedSuiteMetrics = setOf(
            "schema_valid_rate",
            "task_success_rate",
            "macro_metric_score",
            "skill_coverage",
            "citation_precision",
            "citation_recall",
            "hallucinated_reference_count",
            "unsafe_side_effect_count",
        )
        val expectedComparatorTypes = setOf(
            "count",
            "coverage",
            "exact_match",
            "f1",
            "normalized_exact_match",
            "precision",
            "recall",
            "sequence_exact_match",
            "set_exact_match",
            "token_f1",
        )

        val tasks = currentSuite.getJSONArray("tasks")
        val actualAgentFamilies = mutableSetOf<String>()
        val actualSkillTags = mutableSetOf<String>()
        val actualComparatorTypes = mutableSetOf<String>()
        var actualMetricCount = 0
        repeat(tasks.length()) { taskIndex ->
            val task = tasks.getJSONObject(taskIndex)
            actualAgentFamilies += task.getString("agent_family")
            actualSkillTags += task.getJSONArray("skill_tags").toStringSet()
            val metrics = task.getJSONArray("metrics")
            actualMetricCount += metrics.length()
            repeat(metrics.length()) { metricIndex ->
                actualComparatorTypes += metrics.getJSONObject(metricIndex).getString("type")
            }
        }
        val actualSuiteMetrics = currentSuite.getJSONArray("suite_metrics").toObjectStringSet("name")

        assertEquals("soll-source-kb-eval-v1", currentSuite.getString("suite_id"))
        assertEquals(8, tasks.length())
        assertEquals(setOf("source_monitoring", "knowledge_base"), actualAgentFamilies)
        assertEquals(14, actualSkillTags.size)
        assertEquals(25, actualMetricCount)
        assertEquals(expectedComparatorTypes, actualComparatorTypes)
        assertEquals(expectedSuiteMetrics, actualSuiteMetrics)
        assertEquals(
            setOf("EVAL-SMKB-02", "EVAL-SMKB-07", "EVAL-SMKB-08"),
            currentSuite.getJSONObject("promotion_gate")
                .getJSONArray("critical_tasks")
                .toStringSet(),
        )

        val declaredSuite = alignment.getJSONObject("current_suite")
        assertEquals("soll-source-kb-eval-v1", declaredSuite.getString("suite_id"))
        assertEquals(tasks.length(), declaredSuite.getInt("task_count"))
        assertEquals(actualAgentFamilies, declaredSuite.getJSONArray("agent_families").toStringSet())
        assertEquals(actualSkillTags.size, declaredSuite.getInt("skill_tag_count"))
        assertEquals(actualMetricCount, declaredSuite.getInt("per_case_metric_count"))
        assertEquals(
            actualComparatorTypes,
            declaredSuite.getJSONArray("comparator_types").toStringSet(),
        )
        assertEquals(
            actualSuiteMetrics,
            declaredSuite.getJSONArray("suite_metrics").toStringSet(),
        )
        assertFalse(declaredSuite.getBoolean("heterogeneous_workspace_files"))
        assertFalse(declaredSuite.getBoolean("tabular_analytics_track_present"))
        assertEquals(0, declaredSuite.getInt("model_quality_runs_completed"))

        val expectedDirections = setOf(
            "task_accuracy",
            "complete_table_schema_and_shape",
            "header_invariant_column_alignment",
            "typed_and_precision_aware_cell_equivalence",
            "ordered_sequence_or_unordered_row_multiset_equivalence",
            "token_usage",
            "api_cost",
            "tool_action_count",
            "wall_clock_latency",
            "cross_language_accuracy_slice",
            "workspace_scale_accuracy_slice",
            "carrier_and_multimodal_accuracy_slice",
            "join_accuracy_slice",
            "failure_stage_taxonomy",
        )
        val mappings = alignment.getJSONArray("metric_alignment")
        val actualDirections = mutableSetOf<String>()
        val alignmentCounts = mutableMapOf<String, Int>()
        repeat(mappings.length()) { index ->
            val mapping = mappings.getJSONObject(index)
            val direction = mapping.getString("paper_direction")
            val status = mapping.getString("alignment")
            assertTrue("Duplicate DataSpace direction: $direction", actualDirections.add(direction))
            assertTrue(mapping.getString("kind").isNotBlank())
            assertTrue(mapping.getString("reason").isNotBlank())
            alignmentCounts[status] = alignmentCounts.getOrDefault(status, 0) + 1
        }
        assertEquals(14, mappings.length())
        assertEquals(expectedDirections, actualDirections)
        assertEquals(0, alignmentCounts.getOrDefault("direct", 0))
        assertEquals(4, alignmentCounts["partial"])
        assertEquals(10, alignmentCounts["gap"])

        assertEquals(
            setOf(
                "skill_coverage",
                "citation_precision",
                "citation_recall",
                "hallucinated_reference_count",
                "unsafe_side_effect_count",
            ),
            alignment.getJSONArray("retained_project_metrics_not_replaced").toStringSet(),
        )

        val decision = alignment.getJSONObject("decision")
        assertEquals("metric_gap_mapped_current_suite_retained", decision.getString("result"))
        assertFalse(decision.getBoolean("current_suite_modified"))
        assertFalse(decision.getBoolean("android_runtime_change"))
        assertFalse(decision.getBoolean("dataset_imported"))
        assertFalse(decision.getBoolean("external_model_run_completed"))
        assertEquals(
            "defer_dataspace_track_until_named_tabular_analytics_workload",
            decision.getString("next_step"),
        )

        val future = alignment.getJSONObject("future_track_contract")
        assertEquals("isolated_desktop_or_server_eval", future.getString("placement"))
        assertEquals("synthetic_or_non_sensitive_only", future.getString("fixture_policy"))
        assertEquals(5, future.getInt("minimum_cases"))
        assertEquals(9, future.getJSONArray("candidate_metrics").length())
        assertEquals(
            setOf("language", "workspace_size", "carrier_family", "join_required"),
            future.getJSONArray("required_slices").toStringSet(),
        )
        val gates = future.getJSONObject("promotion_gates")
        assertTrue(gates.getBoolean("named_soll_workload_required"))
        assertTrue(gates.getBoolean("baseline_comparison_required"))
        assertTrue(gates.getBoolean("all_safety_assertions_pass"))
        assertEquals(0, gates.getInt("unsafe_side_effect_count"))
        assertTrue(gates.getBoolean("manual_gold_review_required"))
        assertFalse(gates.getBoolean("android_dependency_allowed"))

        val observed = alignment.getJSONObject("observed_value")
        assertEquals(14, observed.getInt("paper_metric_and_diagnostic_directions_audited"))
        assertEquals(0, observed.getInt("direct_alignments"))
        assertEquals(4, observed.getInt("partial_alignments"))
        assertEquals(10, observed.getInt("gaps"))
        assertEquals(8, observed.getInt("current_suite_metrics_preserved"))
        assertEquals(8, observed.getInt("current_tasks_rechecked"))
        assertEquals(0, observed.getInt("upstream_dataset_rows_or_artifacts_imported"))
        assertEquals(0, observed.getInt("external_model_runs"))
        assertEquals(0, observed.getInt("production_runtime_files_changed"))

        val gradleFiles = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
        ).joinToString("\n") { it.readText() }
        assertFalse(gradleFiles.contains("dataspace", ignoreCase = true))
        assertFalse(gradleFiles.contains("HKUSTDial", ignoreCase = true))

        listOf(
            "task_id: e380bceb922e417391144b806510af1d",
            "source_ref: insight/d6cd7eaccde2",
            "source_processing_result: metric_gap_mapped_current_suite_retained",
            "14 DataSpace metric and diagnostic directions mapped to 8 current Soll suite metrics",
            "0 direct equivalents, 4 partial alignments and 10 explicit gaps found",
            "8/8 current synthetic cases rechecked",
            "1/1 focused contract test passed",
            "0 dataset imports, 0 model runs and 0 production/runtime changes",
            "DataSpaceCurrentMetricsAlignmentTest",
            "BUILD SUCCESSFUL",
        ).forEach { evidence ->
            assertTrue("Missing DataSpace audit evidence: $evidence", audit.contains(evidence))
        }
    }

    private fun org.json.JSONArray.toStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
    }

    private fun org.json.JSONArray.toObjectStringSet(field: String): Set<String> = buildSet {
        repeat(length()) { index -> add(getJSONObject(index).getString(field)) }
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ").trim()
}
