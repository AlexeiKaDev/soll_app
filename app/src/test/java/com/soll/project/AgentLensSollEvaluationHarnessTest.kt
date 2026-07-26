package com.soll.project

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLensSollEvaluationHarnessTest {
    @Test
    fun `AgentLens signal becomes a safe deterministic CI-only trajectory harness`() {
        val contract = JSONObject(
            projectFile("docs/knowledge/agentlens-soll-ci-harness-v1.json").readText(),
        )
        val knowledge = projectFile(
            "docs/knowledge/agentlens-soll-evaluation-harness.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-d43b336ae9b8c696-verification.md",
        ).readText()

        assertEquals("soll-agent-trajectory-ci-v1", contract.getString("harness_id"))
        assertEquals(1, contract.getInt("version"))
        assertEquals("ci_only_offline_validation", contract.getString("mode"))
        assertEquals(
            "source-item/9011e13c06d6/d43b336ae9b8c696",
            contract.getString("source_ref"),
        )

        val policy = contract.getJSONObject("execution_policy")
        assertEquals("explicit_manifest_only", policy.getString("repository_input"))
        assertFalse(policy.getBoolean("auto_discover_repositories"))
        assertFalse(policy.getBoolean("auto_run_agents"))
        assertEquals("reject", policy.getString("foreign_repository_policy"))
        assertEquals("reject", policy.getString("pull_request_from_fork_policy"))
        assertEquals("disabled", policy.getString("network_access"))
        assertEquals("forbidden", policy.getString("secret_access"))
        assertEquals("disabled", policy.getString("llm_judge"))
        assertEquals("forbidden", policy.getString("repository_mutation"))
        assertEquals("ci_build_directory_only", policy.getString("writes"))

        val upstream = contract.getJSONObject("upstream_metric_inventory")
        assertEquals(
            setOf("Pitfalls", "Pleasantness", "ToolCalls"),
            upstream.getJSONArray("general").toStringSet(),
        )
        assertEquals(
            setOf(
                "EndResult",
                "InstructionCompliance",
                "Pitfalls",
                "Pleasantness",
                "ToolCalls",
            ),
            upstream.getJSONArray("workflows").toStringSet(),
        )
        assertEquals(
            setOf(
                "Pitfalls",
                "Pleasantness",
                "RelianceOnMocking",
                "TestMaintainability",
                "TestSemanticCoverage",
                "TestUsefulness",
                "ToolCalls",
            ),
            upstream.getJSONArray("tests").toStringSet(),
        )
        val allJudgeMetrics = buildSet {
            addAll(upstream.getJSONArray("general").toStringSet())
            addAll(upstream.getJSONArray("workflows").toStringSet())
            addAll(upstream.getJSONArray("tests").toStringSet())
        }
        assertEquals(9, allJudgeMetrics.size)
        assertEquals(6, upstream.getJSONArray("quality_index_components").length())
        assertEquals(15, upstream.getJSONArray("operational_telemetry").length())

        val upstreamDump = contract.getJSONObject("upstream_dump_contract")
        assertEquals(
            setOf("timestamp", "run_info", "projects_results"),
            upstreamDump.getJSONArray("summary_root_fields").toStringSet(),
        )
        assertEquals(
            setOf(
                "simulated_user_NNN.json",
                "agent_chat_dump.json",
                "tool_calls_dump.json",
            ),
            upstreamDump.getJSONArray("point_directory_files").toStringSet(),
        )
        assertEquals(
            setOf("UserMessage", "AgentTurn", "ToolMessage"),
            upstreamDump.getJSONArray("agent_chat_message_types").toStringSet(),
        )
        assertEquals(
            setOf("name", "arguments", "success", "response_content", "system_reminder"),
            upstreamDump.getJSONArray("tool_call_fields").toStringSet(),
        )

        val normalized = contract.getJSONObject("normalized_dump_contract")
        val trajectory = contract.getJSONObject("ci_smoke").getJSONObject("trajectory")
        assertEquals(
            normalized.getJSONArray("required_sections").toStringSet(),
            trajectory.keySet(),
        )
        assertFalse(normalized.getBoolean("raw_reasoning_allowed"))
        assertFalse(normalized.getBoolean("raw_tool_output_allowed"))

        val repository = trajectory.getJSONObject("repository")
        assertTrue(repository.getBoolean("explicitly_authorized"))
        assertFalse(repository.getBoolean("foreign_repository"))
        assertEquals("embedded_synthetic", repository.getString("origin_kind"))

        val eventKinds = normalized.getJSONArray("event_kinds").toStringSet()
        val events = trajectory.getJSONArray("events").toObjectList()
        assertEquals((1..events.size).toList(), events.map { it.getInt("sequence") })
        assertEquals(events.size, events.map { it.getString("id") }.toSet().size)
        assertTrue(events.all { it.getString("kind") in eventKinds })
        val eventIds = events.map { it.getString("id") }.toSet()

        val requirements = trajectory.getJSONObject("task")
            .getJSONArray("requirements")
            .toStringSet()
        val requirementChecks = trajectory.getJSONArray("requirement_checks").toObjectList()
        assertEquals(requirements, requirementChecks.map { it.getString("requirement_id") }.toSet())
        assertTrue(requirementChecks.all { check ->
            check.getJSONArray("evidence_refs").toStringSet().all { it in eventIds }
        })

        val formalChecks = trajectory.getJSONArray("formal_checks").toObjectList()
        assertTrue(formalChecks.all { check ->
            check.getJSONArray("evidence_refs").toStringSet().all { it in eventIds }
        })

        val toolEvents = events.filter { it.getString("kind") == "tool_call" }
        assertTrue(toolEvents.all { !it.getBoolean("external_side_effect") })
        val failedToolIds = toolEvents
            .filter { !it.getBoolean("success") }
            .map { it.getString("id") }
            .toSet()
        val recoveredFailureIds = toolEvents
            .filter { it.getBoolean("success") && it.has("recovery_of") }
            .map { it.getString("recovery_of") }
            .toSet()
        assertTrue(recoveredFailureIds.all { it in failedToolIds })

        val outcomeRefs = trajectory.getJSONObject("outcome")
            .getJSONArray("evidence_refs")
            .toStringSet()
        val requiredOutcomeRefs = buildSet {
            addAll(requirementChecks.map { it.getString("id") })
            addAll(formalChecks.map { it.getString("id") })
        }
        assertTrue(outcomeRefs.all { it in requiredOutcomeRefs })

        val safety = trajectory.getJSONObject("safety")
        val unsafeEffectCount = safety.keySet().sumOf { safety.getInt(it) }
        assertEquals(0, unsafeEffectCount)
        val redaction = trajectory.getJSONObject("redaction")
        assertFalse(redaction.getBoolean("raw_reasoning_included"))
        assertFalse(redaction.getBoolean("raw_tool_output_included"))

        val actualMetrics = mapOf(
            "schema_valid" to 1.0,
            "requirement_completion_rate" to requirementChecks.passRate(),
            "formal_verification_rate" to formalChecks.passRate(),
            "tool_success_rate" to toolEvents.successRate(),
            "recovered_failure_rate" to if (failedToolIds.isEmpty()) {
                1.0
            } else {
                recoveredFailureIds.intersect(failedToolIds).size.toDouble() / failedToolIds.size
            },
            "final_evidence_coverage" to
                outcomeRefs.intersect(requiredOutcomeRefs).size.toDouble() / requiredOutcomeRefs.size,
            "unsafe_effect_count" to unsafeEffectCount.toDouble(),
            "qualitative_judge_metrics_scored" to 0.0,
        )
        val expectedMetrics = contract.getJSONObject("ci_smoke").getJSONObject("expected_metrics")
        assertEquals(expectedMetrics.keySet(), actualMetrics.keys)
        actualMetrics.forEach { (name, actual) ->
            assertEquals("Unexpected metric $name", expectedMetrics.getDouble(name), actual, 0.000001)
        }

        val gate = contract.getJSONObject("promotion_gate")
        assertTrue(actualMetrics.getValue("schema_valid") >= gate.getDouble("schema_valid"))
        assertTrue(
            actualMetrics.getValue("requirement_completion_rate") >=
                gate.getDouble("requirement_completion_rate_min"),
        )
        assertTrue(
            actualMetrics.getValue("formal_verification_rate") >=
                gate.getDouble("formal_verification_rate_min"),
        )
        assertTrue(
            actualMetrics.getValue("tool_success_rate") >= gate.getDouble("tool_success_rate_min"),
        )
        assertTrue(
            actualMetrics.getValue("recovered_failure_rate") >=
                gate.getDouble("recovered_failure_rate_min"),
        )
        assertTrue(
            actualMetrics.getValue("final_evidence_coverage") >=
                gate.getDouble("final_evidence_coverage_min"),
        )
        assertTrue(
            actualMetrics.getValue("unsafe_effect_count") <= gate.getInt("unsafe_effect_count_max"),
        )
        assertFalse(gate.getBoolean("qualitative_judges_required"))

        listOf(
            "## Metric inventory",
            "nine distinct judge metrics",
            "## AgentLens trajectory dump format",
            "`simulated_user_NNN.json`",
            "`agent_chat_dump.json`",
            "`tool_calls_dump.json`",
            "## Minimal Soll normalized dump",
            "## CI-only evaluator",
            "no `pull_request`, scheduled",
            "Agent/model evaluations completed: **0**",
        ).forEach { control ->
            assertTrue("Missing AgentLens harness control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: agentlens_deep_dive_ci_only_harness_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-d43b336ae9b8c696-verification.md",
            "source_value:",
            "9 upstream judge metrics",
            "8 deterministic CI metrics",
            "1/1 focused contract test passed",
            "0 external agent runs",
            "AgentLensSollEvaluationHarnessTest",
        ).forEach { evidence ->
            assertTrue("Missing AgentLens verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun JSONArray.toStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
    }

    private fun JSONArray.toObjectList(): List<JSONObject> = buildList {
        repeat(length()) { index -> add(getJSONObject(index)) }
    }

    private fun List<JSONObject>.passRate(): Double =
        if (isEmpty()) 0.0 else count { it.getBoolean("passed") }.toDouble() / size

    private fun List<JSONObject>.successRate(): Double =
        if (isEmpty()) 0.0 else count { it.getBoolean("success") }.toDouble() / size

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
