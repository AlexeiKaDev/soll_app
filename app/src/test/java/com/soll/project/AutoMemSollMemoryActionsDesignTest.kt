package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMemSollMemoryActionsDesignTest {
    @Test
    fun `AutoMem signal becomes explicit retrieval first Soll memory actions`() {
        val fixture = JSONObject(
            projectFile(
                "docs/knowledge/automem-soll-continuation-offline-v1.json",
            ).readText(),
        )
        val note = projectFile(
            "docs/knowledge/automem-soll-memory-actions.md",
        ).readText()
        val status = projectFile("soll_status.md").readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-308d80231b641bac-verification.md",
        ).readText()

        assertEquals("automem-soll-continuation-offline-v1", fixture.getString("suite_id"))
        assertEquals(1, fixture.getInt("schema_version"))
        assertEquals("28913a13aa2447a48b971239464390cb", fixture.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/308d80231b641bac",
            fixture.getString("source_ref"),
        )
        assertEquals("static_offline_policy_contract", fixture.getString("mode"))

        val policy = fixture.getJSONObject("data_policy")
        assertEquals("sanitized_historical_patterns", policy.getString("fixture_class"))
        assertFalse(policy.getBoolean("contains_transcripts"))
        assertFalse(policy.getBoolean("contains_personal_data"))
        assertFalse(policy.getBoolean("contains_credentials"))
        assertEquals("disabled", policy.getString("network_access"))
        assertEquals("disabled", policy.getString("external_tools"))
        assertEquals("simulated_only", policy.getString("action_traces"))
        assertEquals(0, policy.getInt("writes_executed"))
        assertEquals(0, policy.getInt("production_mutations"))

        val contracts = fixture.getJSONArray("action_contracts")
        val contractIds = buildSet {
            repeat(contracts.length()) { index -> add(contracts.getJSONObject(index).getString("id")) }
        }
        assertEquals(setOf("memory.search", "memory.read", "memory.write"), contractIds)
        val writeContract = (0 until contracts.length())
            .map { contracts.getJSONObject(it) }
            .single { it.getString("id") == "memory.write" }
        assertEquals("approval_required", writeContract.getString("authority"))
        assertTrue(writeContract.getBoolean("retrieval_evidence_required"))
        assertTrue(writeContract.getBoolean("compare_and_swap_required"))
        assertTrue(writeContract.getBoolean("production_code_targets_forbidden"))

        val cases = fixture.getJSONArray("cases")
        var retrievalFirstPasses = 0
        var abstentions = 0
        var dryRunWritePlans = 0
        repeat(cases.length()) { caseIndex ->
            val scenario = cases.getJSONObject(caseIndex)
            assertTrue(
                "Historical continuation anchor is not present: ${scenario.getString("historical_anchor")}",
                status.contains(scenario.getString("historical_anchor")),
            )

            val available = scenario.getJSONArray("available_artifacts")
            repeat(available.length()) { index ->
                assertTrue(available.getString(index).startsWith("fixture://Soll/.soll/"))
            }

            val trace = scenario.getJSONArray("simulated_trace")
            assertTrue(trace.length() >= 2)
            assertEquals("memory.search", trace.getJSONObject(0).getString("action"))
            var lastReadIndex = -1
            var observedWriteIndex = -1
            repeat(trace.length()) { traceIndex ->
                val step = trace.getJSONObject(traceIndex)
                when (step.getString("action")) {
                    "memory.search" -> assertEquals(0, traceIndex)
                    "memory.read" -> {
                        assertTrue(traceIndex > 0)
                        assertTrue(
                            available.toStringSet().contains(step.getString("artifact_ref")),
                        )
                        lastReadIndex = traceIndex
                    }
                    "memory.write" -> {
                        observedWriteIndex = traceIndex
                        assertTrue(lastReadIndex > 0)
                        assertTrue(observedWriteIndex > lastReadIndex)
                        assertTrue(step.getBoolean("dry_run"))
                        assertTrue(step.getBoolean("simulated"))
                        assertTrue(step.getString("approval_ref").isNotBlank())
                        assertTrue(step.getString("expected_revision").isNotBlank())
                        assertTrue(step.getString("provenance").isNotBlank())
                        dryRunWritePlans += 1
                    }
                    else -> error("Unknown memory action: ${step.getString("action")}")
                }
            }
            assertTrue(lastReadIndex > 0)
            if (observedWriteIndex >= 0) assertTrue(observedWriteIndex > lastReadIndex)
            if (scenario.getString("expected_decision").startsWith("abstain_")) abstentions += 1
            retrievalFirstPasses += 1
        }

        val metrics = fixture.getJSONObject("metrics")
        assertEquals(5, cases.length())
        assertEquals(5, retrievalFirstPasses)
        assertEquals(retrievalFirstPasses, metrics.getInt("retrieval_first_passes"))
        assertEquals(1, abstentions)
        assertEquals(abstentions, metrics.getInt("abstentions_on_missing_evidence"))
        assertEquals(1, dryRunWritePlans)
        assertEquals(
            dryRunWritePlans,
            metrics.getInt("approval_bound_dry_run_write_plans"),
        )
        assertEquals(0, metrics.getInt("writes_executed"))
        assertEquals(0, metrics.getInt("network_calls"))
        assertEquals(0, metrics.getInt("external_tool_calls"))
        assertEquals(0, metrics.getInt("production_files_mutated"))

        listOf(
            "## Explicit memory actions",
            "`memory.search`",
            "`memory.read`",
            "`memory.write`",
            "## Retrieval-first rule",
            "Do not infer a memory write from `continue`",
            "All `5/5` traces start with",
            "executed writes, network calls",
            "production source files are never memory-write targets",
        ).forEach { control ->
            assertTrue("Missing AutoMem design control: $control", note.contains(control))
        }

        listOf(
            "task_id: 28913a13aa2447a48b971239464390cb",
            "source_processing_result: " +
                "memory_actions_design_offline_continuation_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-308d80231b641bac-verification.md",
            "source_value:",
            "3 explicit memory actions",
            "5/5 retrieval-first traces",
            "1 approval-bound dry-run write plan",
            "0 executed writes",
            "1/1 focused contract test passed",
            "AutoMemSollMemoryActionsDesignTest",
        ).forEach { evidence ->
            assertTrue("Missing AutoMem verification evidence: $evidence", verification.contains(evidence))
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
