package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramAsWeightsSourceTriageSmokeTest {
    @Test
    fun `PAW deep dive records license safe smoke and a fail closed adoption decision`() {
        val fixture = JSONObject(
            projectFile(
                "docs/knowledge/program-as-weights-soll-source-triage-smoke-v1.json",
            ).readText(),
        )
        val knowledge = projectFile(
            "docs/knowledge/program-as-weights-soll-source-triage.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-9070c5ba9670178c-verification.md",
        ).readText()

        assertEquals(1, fixture.getInt("schema_version"))
        assertEquals("638c73b7018f4572bb7fac10562f6672", fixture.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/9070c5ba9670178c",
            fixture.getString("source_ref"),
        )
        assertFalse(fixture.getJSONObject("source").getBoolean("raw_present_in_worktree"))

        val licenses = fixture.getJSONObject("license_audit")
        val paper = licenses.getJSONObject("paper")
        assertEquals("verified", paper.getString("status"))
        assertEquals("CC BY 4.0", paper.getString("license"))
        assertTrue(paper.getBoolean("acceptance_criterion_met"))
        assertEquals("MIT", licenses.getJSONObject("python_sdk").getString("license"))
        assertEquals(
            "Apache-2.0",
            licenses.getJSONObject("interpreter_base").getString("license"),
        )
        assertEquals(3, licenses.getJSONArray("paw_release_artifacts").length())
        assertEquals("not_cleared", licenses.getString("runtime_import_clearance"))
        assertEquals(0, licenses.getInt("repository_imports"))

        val candidate = fixture.getJSONObject("candidate_contract")
        assertEquals(
            setOf("high", "medium", "low", "noise"),
            candidate.getJSONArray("target_labels").toStringSet(),
        )
        assertEquals("advisory_only", candidate.getString("authority"))
        assertTrue(candidate.getBoolean("human_review_required"))
        assertFalse(candidate.getBoolean("automatic_acceptance"))
        assertFalse(candidate.getBoolean("automatic_task_creation"))
        assertFalse(candidate.getBoolean("persistent_writes"))

        val smoke = fixture.getJSONObject("smoke")
        val runtime = smoke.getJSONObject("runtime")
        assertEquals("0.4.4", runtime.getString("sdk_version"))
        assertEquals("d67162f3ab9562fe2826", runtime.getString("program_id"))
        assertEquals("qwen3-0.6b-q6_k", runtime.getString("runtime_id"))
        assertTrue(runtime.getBoolean("offline_ready"))

        val preparation = smoke.getJSONObject("preparation")
        assertEquals("task_local_temporary", preparation.getString("cache_scope"))
        assertEquals(0, preparation.getInt("custom_compile_calls"))
        assertEquals(0, preparation.getInt("specification_uploads"))
        assertEquals(0, preparation.getInt("private_inputs_during_preparation"))

        val boundary = smoke.getJSONObject("inference_boundary")
        assertTrue(boundary.getBoolean("sdk_offline_mode"))
        assertEquals("raise_if_called", boundary.getString("socket_network_guard"))
        assertEquals(0, boundary.getInt("network_attempts"))
        assertEquals("raise_if_called", boundary.getString("credential_lookup_guard"))
        assertEquals(0, boundary.getInt("credential_lookup_attempts"))
        assertEquals(8, boundary.getInt("synthetic_inputs"))
        assertEquals(0, boundary.getInt("private_inputs"))
        assertEquals(0, boundary.getInt("automatic_decisions"))
        assertEquals(0, boundary.getInt("persistent_soll_writes"))

        val cases = smoke.getJSONArray("cases")
        val observedTiers = mutableSetOf<String>()
        var calculatedMatches = 0
        repeat(cases.length()) { index ->
            val case = cases.getJSONObject(index)
            observedTiers += case.getString("target_tier")
            assertTrue(case.getString("text").lowercase().contains("synthetic"))
            assertTrue(case.getString("observed_binary") in setOf("immediate", "wait"))
            assertTrue(case.getInt("latency_ms") > 0)
            if (case.getBoolean("passed")) calculatedMatches += 1
        }
        assertEquals(8, cases.length())
        assertEquals(setOf("high", "medium", "low", "noise"), observedTiers)
        assertEquals(7, calculatedMatches)

        val metrics = smoke.getJSONObject("metrics")
        assertEquals(8, metrics.getInt("cases"))
        assertEquals(7, metrics.getInt("proxy_matches"))
        assertEquals(0.875, metrics.getDouble("binary_proxy_accuracy"), 0.0001)
        assertEquals(1.0, metrics.getDouble("high_recall"), 0.0001)
        assertEquals(0, metrics.getInt("false_deescalations"))
        assertEquals(1, metrics.getInt("false_escalations"))
        assertEquals(8, metrics.getInt("stable_outputs_across_two_runs"))
        assertEquals(4, metrics.getInt("target_label_count"))
        assertEquals(2, metrics.getInt("runtime_output_label_count"))
        assertFalse(metrics.getBoolean("exact_four_label_behavior_tested"))

        val decision = fixture.getJSONObject("decision")
        assertTrue(decision.getBoolean("source_value_measurable"))
        assertFalse(decision.getBoolean("production_adoption"))
        assertEquals(
            "local_binary_proxy_smoke_completed_four_label_adoption_deferred",
            decision.getString("result"),
        )
        assertTrue(decision.getJSONObject("next_gate").getBoolean("human_review_required"))
        assertTrue(
            decision.getJSONObject("next_gate")
                .getBoolean("explicit_approval_for_synthetic_compile"),
        )

        listOf(
            "## License audit",
            "**CC BY 4.0 verified**",
            "`paw.compile(spec, ...)`",
            "## Local synthetic smoke",
            "proxy matches: `7/8` (`87.5%`)",
            "exact four-label behavior was not tested",
            "## Promotion decision and next gate",
            "No task, alert, source priority or other Soll state may be changed",
        ).forEach { evidence ->
            assertTrue("Missing PAW deep-dive evidence: $evidence", knowledge.contains(evidence))
        }

        listOf(
            "task_id: 638c73b7018f4572bb7fac10562f6672",
            "source_processing_result: " +
                "paw_deep_dive_local_binary_proxy_smoke_completed_adoption_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-9070c5ba9670178c-verification.md",
            "source_value:",
            "Paper license: **CC BY 4.0 verified**",
            "| Local PAW smoke | **7/8** binary proxy matches (`87.5%`) |",
            "| Private inputs | `0` |",
            "ProgramAsWeightsSourceTriageSmokeTest",
        ).forEach { evidence ->
            assertTrue("Missing PAW verification evidence: $evidence", verification.contains(evidence))
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
}
