package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCompass052SollApplicabilityTest {
    @Test
    fun `OpenCompass 0_5_2 is shortlisted for server eval without entering Android runtime`() {
        val matrixFile = projectFile(
            "docs/knowledge/opencompass-0-5-2-soll-applicability-v1.json",
        )
        val matrix = JSONObject(matrixFile.readText())
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-e482e15e7a274545991652cde9707e44-opencompass-0-5-2-audit.md",
        ).readText().normalizeWhitespace()

        assertEquals("opencompass-0-5-2-soll-applicability-v1", matrix.getString("audit_id"))
        assertEquals("e482e15e7a274545991652cde9707e44", matrix.getString("task_id"))
        assertEquals("insight/6cd00d7bc573", matrix.getString("source_ref"))
        assertEquals("untrusted_external_content", matrix.getString("source_trust"))

        val release = matrix.getJSONObject("release")
        assertEquals("0.5.2", release.getString("version"))
        assertEquals(
            "974179240a1a4e3c0ff14c60621cf1f6c95b287a",
            release.getString("commit"),
        )
        assertFalse(release.getBoolean("monitored_source_vendored"))
        assertEquals("0.5.3", release.getString("current_upstream_release_observed"))

        val boundary = matrix.getJSONObject("integration_boundary")
        assertEquals("shortlist_server_offline_eval_only", boundary.getString("decision"))
        assertEquals("isolated_desktop_or_server_eval_harness", boundary.getString("target"))
        assertFalse(boundary.getBoolean("android_runtime_dependency"))
        assertFalse(boundary.getBoolean("android_public_contract_change"))
        assertFalse(boundary.getBoolean("production_write"))
        assertFalse(boundary.getBoolean("external_model_run_completed"))
        assertEquals(
            setOf(
                "POST /api/v1/chat/turn",
                "GET /api/v1/android/sync-status",
                "AssistantMemory",
                "docs/knowledge/soll-source-monitoring-kb-eval-v1.json",
            ),
            boundary.getJSONArray("current_soll_seams").toStringSet(),
        )

        val expectedBenchmarks = setOf(
            "HMMT2025",
            "AMO-Bench",
            "IMO-Bench",
            "ATLAS",
            "OpenSWI",
            "CMPhysBench",
            "Biology Instructions",
            "Mol Instructions",
            "SciReasoner",
            "ARC_AGI_2",
            "IFBench",
            "PI-LLM",
            "ProcessBench",
            "LCB_pro",
        )
        val benchmarks = matrix.getJSONArray("benchmarks")
        val observedBenchmarks = mutableSetOf<String>()
        val classificationCounts = mutableMapOf<String, Int>()
        val shortlist = mutableMapOf<String, JSONObject>()

        assertEquals(14, benchmarks.length())
        repeat(benchmarks.length()) { index ->
            val benchmark = benchmarks.getJSONObject(index)
            val name = benchmark.getString("name")
            val classification = benchmark.getString("classification")
            assertTrue("Duplicate benchmark: $name", observedBenchmarks.add(name))
            classificationCounts[classification] = classificationCounts.getOrDefault(classification, 0) + 1
            assertTrue(benchmark.getString("release_group").isNotBlank())
            assertTrue(benchmark.getString("decision").isNotBlank())
            if (classification == "pilot_shortlist") shortlist[name] = benchmark
        }

        assertEquals(expectedBenchmarks, observedBenchmarks)
        assertEquals(3, classificationCounts["pilot_shortlist"])
        assertEquals(2, classificationCounts["conditional_model_selection_signal"])
        assertEquals(9, classificationCounts["no_current_soll_scenario"])
        assertEquals(setOf("IFBench", "PI-LLM", "LCB_pro"), shortlist.keys)
        assertEquals(
            "chat_task_intake_and_structured_output_constraints",
            shortlist.getValue("IFBench").getString("soll_scenario"),
        )
        assertEquals(
            "latest_task_source_and_preference_value_retrieval_after_updates",
            shortlist.getValue("PI-LLM").getString("soll_scenario"),
        )
        assertEquals(
            "isolated_implementation_worker_model_selection",
            shortlist.getValue("LCB_pro").getString("soll_scenario"),
        )

        val telemetry = matrix.getJSONArray("cross_cutting_metrics")
        assertEquals(3, telemetry.length())
        assertEquals(
            setOf("output_length", "logprobs", "finish_reason"),
            buildSet {
                repeat(telemetry.length()) { index ->
                    val metric = telemetry.getJSONObject(index)
                    add(metric.getString("name"))
                    assertFalse(metric.getBoolean("chat_turn_dto_field_present"))
                }
            },
        )

        val pilot = matrix.getJSONObject("pilot_contract")
        assertEquals("synthetic_or_non_sensitive_only", pilot.getString("fixture_policy"))
        assertEquals("disabled_during_scoring", pilot.getString("network_access"))
        assertEquals("forbidden", pilot.getString("persistent_writes"))
        assertEquals("not_read", pilot.getString("provider_credentials"))
        assertEquals(5, pilot.getInt("minimum_cases_per_shortlisted_direction"))
        assertEquals(
            setOf(
                "task_success_rate",
                "constraint_pass_rate",
                "latest_value_exact_match",
                "stale_value_recall_count",
                "pass_at_1",
                "output_length",
                "finish_reason_distribution",
                "unsafe_side_effect_count",
            ),
            pilot.getJSONArray("required_metrics").toStringSet(),
        )
        val promotion = pilot.getJSONObject("promotion_gate")
        assertTrue(promotion.getBoolean("all_case_safety_assertions_pass"))
        assertEquals(0, promotion.getInt("unsafe_side_effect_count"))
        assertTrue(promotion.getBoolean("baseline_comparison_required"))
        assertTrue(promotion.getBoolean("manual_review_required"))
        assertFalse(promotion.getBoolean("android_contract_change_allowed"))

        val observed = matrix.getJSONObject("observed_value")
        assertEquals(14, observed.getInt("release_benchmarks_audited"))
        assertEquals(3, observed.getInt("pilot_shortlist_count"))
        assertEquals(2, observed.getInt("conditional_count"))
        assertEquals(9, observed.getInt("no_current_soll_scenario_count"))
        assertEquals(3, observed.getInt("cross_cutting_metrics_audited"))
        assertEquals(0, observed.getInt("current_chat_turn_metric_fields"))
        assertEquals(0, observed.getInt("android_dependencies_added"))
        assertEquals(0, observed.getInt("external_model_runs"))
        assertEquals(0, observed.getInt("production_changes"))

        val api = projectFile("app/src/main/java/com/soll/data/api/SollApiService.kt").readText()
        assertTrue(api.contains("@POST(\"api/v1/chat/turn\")"))
        assertTrue(api.contains("@GET(\"api/v1/android/sync-status\")"))
        val chatTurnDto = api.substringAfter("data class ChatTurnResponse(")
            .substringBefore("data class ChatTaskIntakeResponse(")
        listOf("outputLength", "logprobs", "finishReason").forEach { field ->
            assertFalse("Unexpected OpenCompass telemetry in ChatTurnResponse: $field", chatTurnDto.contains(field))
        }
        assertTrue(
            projectFile("app/src/main/java/com/soll/domain/assistant/memory/AssistantMemory.kt")
                .readText()
                .contains("data class AssistantMemory("),
        )

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
        ).joinToString("\n") { it.readText() }
        assertFalse(gradleInputs.contains("opencompass", ignoreCase = true))

        listOf(
            "task_id: e482e15e7a274545991652cde9707e44",
            "source_ref: insight/6cd00d7bc573",
            "source_processing_result: compatibility_validated_server_eval_shortlist_defined",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-e482e15e7a274545991652cde9707e44-opencompass-0-5-2-audit.md",
            "14 release benchmark directions and 3 cross-cutting metrics audited",
            "3 Soll pilot candidates shortlisted, 2 retained as conditional signals, and 9 deferred",
            "1/1 focused contract test passed",
            "0 Android dependencies, 0 external model runs, and 0 production changes",
            "OpenCompass052SollApplicabilityTest",
            "BUILD SUCCESSFUL",
        ).forEach { evidence ->
            assertTrue("Missing OpenCompass audit evidence: $evidence", audit.contains(evidence))
        }
    }

    private fun org.json.JSONArray.toStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
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
