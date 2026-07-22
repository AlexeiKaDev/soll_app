package com.soll.project

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableDataReferencingErrorReductionTest {
    @Test
    fun `assigned analyst produces a bounded table DRE integration contract`() {
        val knowledge = projectFile(
            "docs/knowledge/table-data-referencing-error-reduction.md",
        ).readText().normalizeWhitespace()
        val contract = JSONObject(
            projectFile(
                "docs/knowledge/table-data-referencing-error-reduction-v1.json",
            ).readText(),
        )
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-43bc9d100c528d57-verification.md",
        ).readText().normalizeWhitespace()

        assertEquals(1, contract.getInt("schema_version"))
        assertEquals(
            "soll-table-data-referencing-error-reduction-v1",
            contract.getString("contract_id"),
        )
        assertEquals(
            "798292d19f554cc6b64f0ecc2c9eaeb4",
            contract.getString("task_id"),
        )
        assertEquals(
            "source-item/9011e13c06d6/43bc9d100c528d57",
            contract.getString("source_ref"),
        )

        val assignment = contract.getJSONObject("assignment")
        assertEquals("assigned_analysis_completed", assignment.getString("status"))
        assertEquals("Soll Table Reliability Analyst", assignment.getString("analyst_role"))
        assertEquals("Soll Server/AI Pipeline", assignment.getString("target_runtime_owner"))
        assertEquals(
            "display_reviewable_server_result_only",
            assignment.getString("android_role"),
        )

        val scope = contract.getJSONObject("scope")
        assertEquals("offline_proposal_only", scope.getString("mode"))
        assertEquals(
            "no_table_processing_pipeline_in_soll_app",
            scope.getString("current_runtime_state"),
        )
        listOf(
            "executes_models",
            "uses_network_or_credentials",
            "imports_upstream_code_or_data",
            "mutates_production",
        ).forEach { flag -> assertFalse("Unsafe scope flag: $flag", scope.getBoolean(flag)) }

        assertEquals(
            setOf("incorrect_citation", "omitted_information"),
            contract.getJSONArray("dre_types").toStringSet(),
        )
        assertEquals(
            setOf("dre_rate", "correct_in_dre_ratio", "dre_in_incorrect_ratio"),
            contract.getJSONArray("paper_metrics").toStringSet(),
        )
        assertEquals(
            listOf("normalize", "generate", "deterministic_checks", "critic", "policy"),
            contract.getJSONArray("pipeline_stages").toObjectList().map { it.getString("id") },
        )
        assertEquals(12, contract.getJSONArray("evaluation_metrics").length())
        assertEquals(5, contract.getJSONArray("cost_metrics").length())
        assertEquals(10, contract.getJSONArray("promotion_gates").length())
        assertEquals(
            setOf("needs_human_review", "abstain"),
            contract.getJSONArray("fail_closed_states").toStringSet(),
        )

        val smoke = contract.getJSONObject("synthetic_smoke")
        val outcomes = smoke.getJSONArray("outcomes").toObjectList()
        val expected = smoke.getJSONObject("expected_metrics")
        val dreOutcomes = outcomes.filter { it.getJSONArray("dre_types").length() > 0 }
        val correctOutcomes = outcomes.filter { it.getBoolean("final_correct") }
        val incorrectOutcomes = outcomes.filterNot { it.getBoolean("final_correct") }

        assertMetric(expected, "dre_rate", dreOutcomes.size.toDouble() / outcomes.size)
        assertMetric(
            expected,
            "incorrect_citation_rate",
            outcomes.count { "incorrect_citation" in it.getJSONArray("dre_types").toStringSet() }
                .toDouble() / outcomes.size,
        )
        assertMetric(
            expected,
            "omitted_information_rate",
            outcomes.count { "omitted_information" in it.getJSONArray("dre_types").toStringSet() }
                .toDouble() / outcomes.size,
        )
        assertMetric(
            expected,
            "correct_in_dre_ratio",
            dreOutcomes.count { it.getBoolean("final_correct") }.toDouble() / dreOutcomes.size,
        )
        assertMetric(
            expected,
            "dre_in_incorrect_ratio",
            incorrectOutcomes.count { it.getJSONArray("dre_types").length() > 0 }
                .toDouble() / incorrectOutcomes.size,
        )
        assertMetric(
            expected,
            "final_answer_accuracy",
            correctOutcomes.size.toDouble() / outcomes.size,
        )

        val candidates = smoke.getJSONArray("filtering_candidates").toObjectList()
        val minimumDreCount = candidates.minOf { it.getInt("dre_count") }
        val selected = candidates
            .filter { it.getInt("dre_count") == minimumDreCount }
            .mapTo(linkedSetOf()) { it.getString("id") }
        assertEquals(smoke.getJSONArray("expected_minimum_dre_subset").toStringSet(), selected)
        assertEquals(2, selected.size)
        assertTrue(candidates.single { it.getString("id").startsWith("candidate_c") }
            .getBoolean("final_correct").not())

        smoke.getJSONArray("rejection_traces").toObjectList().forEach { trace ->
            val attempts = trace.getJSONArray("critic_dre_by_attempt")
            val maxAttempts = trace.getInt("max_attempts")
            val firstClean = (0 until minOf(attempts.length(), maxAttempts))
                .firstOrNull { index -> !attempts.getBoolean(index) }
            val decision = if (firstClean == null) "needs_human_review" else "accepted"
            val attemptCount = firstClean?.plus(1) ?: maxAttempts
            assertEquals(trace.getString("expected_decision"), decision)
            assertEquals(trace.getInt("expected_attempt_count"), attemptCount)
        }

        listOf(
            "assigned_analyst: \"Soll Table Reliability Analyst\"",
            "assignment_status: assigned_analysis_completed",
            "`SollGateway.askModelChat(...)`",
            "arxiv:2606.32029v1",
            "558,235 bytes; 19 page objects",
            "264,898 bytes; 9 entries",
            "`92.67%`",
            "`N=8`",
            "`+11.96` percentage points",
            "2,000 balanced examples",
            "5,712 samples",
            "3,600 balanced segments",
            "`78.16%` overall F1",
            "Пять стадий",
            "needs_human_review",
            "production table requests, model/critic executions, external integrations",
        ).forEach { evidence ->
            assertTrue("Missing analyst evidence: $evidence", knowledge.contains(evidence))
        }

        listOf(
            "task_id: 798292d19f554cc6b64f0ecc2c9eaeb4",
            "source_ref: source-item/9011e13c06d6/43bc9d100c528d57",
            "source_processing_result: table_dre_analysis_assigned_integration_contract_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-43bc9d100c528d57-verification.md",
            "source_value:",
            "1 assigned analyst role",
            "2 DRE classes, 3 paper metrics and 2 mitigation methods",
            "5-stage server-side integration contract",
            "4 synthetic outcomes, 4 filtering candidates and 3 rejection traces",
            "1/1 focused contract test passed",
            "0 model/critic runs, upstream imports, external integrations or runtime changes",
        ).forEach { evidence ->
            assertTrue("Missing verification evidence: $evidence", verification.contains(evidence))
        }

        val gateway = projectFile("app/src/main/java/com/soll/domain/soll/SollGateway.kt").readText()
        assertTrue(gateway.contains("suspend fun askModelChat("))
        assertFalse(projectFile("app/src/main").walkTopDown().any {
            it.isFile && it.name.contains("TableDre", ignoreCase = true)
        })
    }

    private fun assertMetric(expected: JSONObject, key: String, actual: Double) {
        assertEquals("Unexpected $key", expected.getDouble(key), actual, EPSILON)
    }

    private fun JSONArray.toStringSet(): Set<String> = buildSet {
        repeat(length()) { index -> add(getString(index)) }
    }

    private fun JSONArray.toObjectList(): List<JSONObject> = buildList {
        repeat(length()) { index -> add(getJSONObject(index)) }
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

    private companion object {
        const val EPSILON = 0.000000001
    }
}
