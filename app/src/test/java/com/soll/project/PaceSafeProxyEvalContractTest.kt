package com.soll.project

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceSafeProxyEvalContractTest {
    @Test
    fun `PACE sources produce a benign no-action Soll proxy eval design`() {
        val contract = JSONObject(
            projectFile("docs/knowledge/pace-safe-proxy-eval-v1.json").readText(),
        )
        val knowledge = projectFile(
            "docs/knowledge/pace-safe-proxy-eval-design.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-0a0137770111aebc-verification.md",
        ).readText().normalizeWhitespace()

        assertEquals(1, contract.getInt("schema_version"))
        assertEquals("pace-safe-soll-proxy-eval-v1", contract.getString("suite_id"))
        assertEquals("41e79c89396e4b95987e22c3db56e2f7", contract.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/0a0137770111aebc",
            contract.getString("source_ref"),
        )
        assertEquals("untrusted_external_content", contract.getString("source_trust"))

        val sourceParse = contract.getJSONObject("source_parse")
        val paper = sourceParse.getJSONObject("paper")
        assertEquals("2607.02032", paper.getString("arxiv_id"))
        assertEquals(2, paper.getInt("version"))
        assertEquals(26, paper.getInt("pages"))
        assertEquals(
            "0af39b8c953f0a432735e00f1ea0cf9fa6eb631a643c421fa4d616f0d838fa7b",
            paper.getString("pdf_sha256"),
        )

        val method = paper.getJSONObject("method")
        assertEquals(14, method.getInt("calibration_models"))
        assertEquals(19, method.getInt("candidate_source_benchmarks"))
        assertEquals(4, method.getInt("agentic_targets"))
        assertEquals(100, method.getInt("proxy_budget"))
        assertEquals(300, method.getInt("bootstrap_replicates"))
        assertTrue(method.getBoolean("strict_leave_one_model_out_validation"))
        assertEquals(
            setOf(
                "local_absolute_spearman_relevance",
                "global_svd_leverage_times_absolute_spearman_relevance",
            ),
            method.getJSONArray("selection_signals").toStringSet(),
        )
        assertEquals(
            setOf("linear_absolute_score", "bradley_terry_pairwise_ranking"),
            method.getJSONArray("prediction_goals").toStringSet(),
        )

        val reported = paper.getJSONObject("reported_average")
        assertEquals(0.038, reported.getDouble("mae"), 0.0001)
        assertEquals(0.81, reported.getDouble("spearman"), 0.0001)
        assertEquals(0.8437, reported.getDouble("pairwise_accuracy"), 0.0001)
        assertTrue(
            paper.getJSONArray("limitations").toStringSet().containsAll(
                setOf(
                    "calibration_models_must_represent_future_models",
                    "proxy_error_can_increase_under_distribution_shift",
                    "proxy_does_not_replace_full_target_evaluation",
                ),
            ),
        )

        val dataset = sourceParse.getJSONObject("dataset")
        assertEquals("neulab/pace-bench", dataset.getString("repository"))
        assertEquals(
            "ce177cfe25bc8c8259cadecb56d4db8d9d36ab18",
            dataset.getString("revision"),
        )
        assertEquals("mixed-upstream", dataset.getString("license"))
        assertEquals(
            listOf("source_benchmark", "subdir", "instance_id"),
            dataset.getJSONArray("identity_key").toStringList(),
        )
        assertEquals(
            setOf(
                "instance_id",
                "source_benchmark",
                "subdir",
                "input",
                "answer",
                "metric",
                "weight",
                "images",
                "content_status",
            ),
            dataset.getJSONArray("row_fields").toStringSet(),
        )

        val expectedFiles = mapOf(
            "gaia.jsonl" to listOf(100, 100, 99, 1, 19, 29),
            "swebench.jsonl" to listOf(100, 100, 97, 3, 62, 11),
            "swebench_multimodal.jsonl" to listOf(105, 100, 103, 2, 23, 5),
            "swtbench.jsonl" to listOf(107, 100, 106, 1, 43, 1),
        )
        val files = dataset.getJSONArray("files")
        val observedFiles = mutableSetOf<String>()
        repeat(files.length()) { index ->
            val file = files.getJSONObject(index)
            val name = file.getString("name")
            assertTrue("Duplicate dataset file: $name", observedFiles.add(name))
            assertEquals(
                expectedFiles.getValue(name),
                listOf(
                    file.getInt("rows"),
                    file.getInt("unique_score_columns"),
                    file.getInt("content_ok"),
                    file.getInt("unresolved"),
                    file.getInt("image_rows"),
                    file.getInt("null_answers"),
                ),
            )
            assertTrue(file.getString("sha256").matches(Regex("[0-9a-f]{64}")))
        }
        assertEquals(expectedFiles.keys, observedFiles)

        val totals = dataset.getJSONObject("totals")
        assertEquals(412, totals.getInt("rows"))
        assertEquals(405, totals.getInt("content_ok"))
        assertEquals(7, totals.getInt("unresolved"))
        assertEquals(147, totals.getInt("image_rows"))
        assertEquals(46, totals.getInt("null_answers"))
        assertEquals(400, totals.getInt("per_target_score_columns"))
        assertEquals(385, totals.getInt("unique_score_columns_across_targets"))
        assertEquals(12, totals.getInt("selected_source_benchmarks"))
        assertEquals(
            setOf(
                "acp_gen",
                "bfcl",
                "debugbench",
                "ifeval",
                "lifbench",
                "livecodebench",
                "logiqa",
                "mmmu",
                "planbench",
                "repobench",
                "visualpuzzles",
                "visualwebbench",
            ),
            dataset.getJSONArray("selected_source_benchmarks").toStringSet(),
        )

        val parseDecision = dataset.getJSONObject("parse_decision")
        assertFalse(parseDecision.getBoolean("upstream_rows_imported"))
        assertFalse(parseDecision.getBoolean("upstream_images_imported"))
        assertFalse(parseDecision.getBoolean("upstream_weights_reused"))
        assertEquals(4, parseDecision.getJSONArray("reasons").length())

        val policy = contract.getJSONObject("execution_policy")
        assertEquals("isolated_desktop_or_server_offline_harness", policy.getString("placement"))
        assertFalse(policy.getBoolean("android_runtime_dependency"))
        assertEquals("synthetic_non_sensitive", policy.getString("fixture_class"))
        assertEquals("single_static_response", policy.getString("response_mode"))
        listOf("network_access", "tool_calls", "shell_execution", "external_integrations")
            .forEach { field ->
                assertTrue(policy.getString(field) in setOf("disabled", "forbidden"))
            }
        assertEquals("forbidden", policy.getString("persistent_writes"))
        assertEquals("forbidden", policy.getString("real_system_identifiers"))
        assertEquals("forbidden", policy.getString("hidden_reasoning_collection"))
        assertFalse(policy.getBoolean("automatic_task_creation"))
        assertFalse(policy.getBoolean("automatic_model_routing"))
        assertTrue(policy.getBoolean("human_review_required"))

        val calibration = contract.getJSONObject("calibration_protocol")
        assertEquals("design_only_not_executed", calibration.getString("status"))
        assertEquals(64, calibration.getJSONObject("candidate_pool").getInt("minimum_cases"))
        assertEquals(120, calibration.getJSONObject("full_target_suite").getInt("minimum_cases"))
        assertEquals(
            12,
            calibration.getJSONObject("calibration_set")
                .getInt("minimum_model_or_configuration_snapshots"),
        )
        val selection = calibration.getJSONObject("selection")
        assertEquals("pace_local_global_ensemble", selection.getString("method"))
        assertEquals(16, selection.getInt("maximum_proxy_cases"))
        assertEquals(3, selection.getInt("minimum_selected_per_category"))
        assertFalse(selection.getBoolean("safety_sentinels_selected_by_regression"))
        assertTrue(selection.getBoolean("safety_sentinels_always_run"))

        val validation = calibration.getJSONObject("validation")
        assertEquals("nested_leave_one_snapshot_out", validation.getString("protocol"))
        assertTrue(validation.getBoolean("equal_budget_stratified_baseline_required"))
        assertEquals(
            setOf(
                "macro_mae",
                "spearman",
                "pairwise_accuracy",
                "category_coverage",
                "safety_sentinel_pass_rate",
                "unsafe_side_effect_count",
                "abstention_precision",
                "schema_valid_rate",
            ),
            validation.getJSONArray("metrics").toStringSet(),
        )
        assertEquals(0.05, validation.getDouble("maximum_macro_mae"), 0.0001)
        assertEquals(0.8, validation.getDouble("minimum_spearman"), 0.0001)
        assertEquals(0.8, validation.getDouble("minimum_pairwise_accuracy"), 0.0001)
        assertEquals(1.0, validation.getDouble("minimum_safety_sentinel_pass_rate"), 0.0001)
        assertEquals(0, validation.getInt("maximum_unsafe_side_effect_count"))
        assertFalse(validation.getBoolean("proxy_claim_allowed_before_validation"))

        val cases = contract.getJSONArray("contract_smoke_cases")
        val expectedCategories = setOf("reasoning", "code_review", "task_planning", "source_triage")
        val categoryCounts = mutableMapOf<String, Int>()
        val caseIds = mutableSetOf<String>()
        assertEquals(12, cases.length())
        repeat(cases.length()) { index ->
            val case = cases.getJSONObject(index)
            val id = case.getString("id")
            val category = case.getString("category")
            val prompt = case.getString("prompt")
            assertTrue("Duplicate case id: $id", caseIds.add(id))
            assertTrue("Unexpected category: $category", category in expectedCategories)
            categoryCounts[category] = categoryCounts.getOrDefault(category, 0) + 1
            assertTrue(prompt.contains("synthetic", ignoreCase = true))
            assertFalse(prompt.contains(Regex("https?://|[A-Za-z]:\\\\|/etc/")))
            assertTrue(case.getJSONObject("expected_output").length() > 0)
            assertEquals("exact_structured_match", case.getString("scoring"))
            assertTrue(
                case.getJSONArray("safety_assertions").toStringSet().containsAll(
                    setOf(
                        "synthetic_input_only",
                        "no_tool_calls",
                        "no_network",
                        "no_persistent_writes",
                    ),
                ),
            )
        }
        assertEquals(expectedCategories.associateWith { 3 }, categoryCounts)

        val gate = contract.getJSONObject("promotion_gate")
        assertTrue(gate.getBoolean("design_contract_test_must_pass"))
        assertTrue(gate.getBoolean("calibration_run_requires_separate_approval"))
        assertTrue(gate.getBoolean("all_safety_sentinels_must_pass"))
        assertEquals(0, gate.getInt("unsafe_side_effect_count"))
        assertTrue(gate.getBoolean("full_target_confirmation_required_before_model_change"))
        assertTrue(gate.getBoolean("human_approval_required"))
        assertFalse(gate.getBoolean("proxy_score_can_trigger_autonomous_action"))

        val observed = contract.getJSONObject("observed_value")
        assertEquals(26, observed.getInt("paper_pages_parsed"))
        assertEquals(412, observed.getInt("dataset_rows_parsed"))
        assertEquals(4, observed.getInt("dataset_files_parsed"))
        assertEquals(12, observed.getInt("benign_contract_cases"))
        assertEquals(4, observed.getInt("capability_categories"))
        assertEquals(0, observed.getInt("external_model_runs"))
        assertEquals(0, observed.getInt("autonomous_actions"))
        assertEquals(0, observed.getInt("android_runtime_changes"))
        assertEquals(0, observed.getInt("production_changes"))

        listOf(
            "Разбор arXiv PDF",
            "Разбор `neulab/pace-bench`",
            "Soll proxy protocol",
            "12 contract smoke cases",
            "nested leave-one-snapshot-out",
            "external model runs, agent runs, tool calls, autonomous actions",
        ).forEach { evidence ->
            assertTrue("Missing PACE design evidence: $evidence", knowledge.contains(evidence))
        }

        listOf(
            "task_id: 41e79c89396e4b95987e22c3db56e2f7",
            "source_ref: source-item/9011e13c06d6/0a0137770111aebc",
            "source_processing_result: paper_dataset_parsed_safe_proxy_eval_designed_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-0a0137770111aebc-verification.md",
            "26-page arXiv PDF and 412 dataset rows parsed",
            "12 benign contract cases",
            "1/1 focused contract test passed",
            "0 agent/model runs, 0 autonomous actions, and 0 Android/runtime changes",
            "PaceSafeProxyEvalContractTest",
            "BUILD SUCCESSFUL",
        ).forEach { evidence ->
            assertTrue("Missing PACE verification evidence: $evidence", verification.contains(evidence))
        }

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
        ).joinToString("\n") { it.readText() }
        assertFalse(gradleInputs.contains("pace-bench", ignoreCase = true))
        assertFalse(gradleInputs.contains("neulab/pace", ignoreCase = true))
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        repeat(length()) { index -> add(getString(index)) }
    }

    private fun JSONArray.toStringSet(): Set<String> = toStringList().toSet()

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
