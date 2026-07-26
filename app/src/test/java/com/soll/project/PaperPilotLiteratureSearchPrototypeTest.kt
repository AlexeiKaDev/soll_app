package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperPilotLiteratureSearchPrototypeTest {
    @Test
    fun `Hugging Face PaperPilot signal becomes a proposal only Soll workflow design`() {
        val prototype = JSONObject(
            projectFile(
                "docs/knowledge/hugging-face-paperpilot-literature-search-prototype-v1.json",
            ).readText(),
        )
        val design = projectFile(
            "docs/knowledge/hugging-face-paperpilot-literature-search-prototype.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-d4d88b2dcc5eb63f-verification.md",
        ).readText().normalizeWhitespace()

        assertEquals("soll-paperpilot-literature-search-v1", prototype.getString("prototype_id"))
        assertEquals(1, prototype.getInt("version"))
        assertEquals("51efb9a76b94469e86a3fd8b9181918a", prototype.getString("task_id"))
        assertEquals(
            "source-item/9011e13c06d6/d4d88b2dcc5eb63f",
            prototype.getString("source_ref"),
        )
        assertEquals("proposal_only", prototype.getString("mode"))

        val anchor = prototype.getJSONObject("anchor_paper")
        assertEquals("arxiv:2607.00597v2", anchor.getString("canonical_id"))
        assertEquals(
            "https://huggingface.co/papers/2607.00597",
            anchor.getString("source_url"),
        )

        val workflow = prototype.getJSONObject("workflow")
        val nodes = workflow.getJSONArray("nodes")
        assertEquals(9, nodes.length())
        assertEquals("n9", workflow.getString("terminal_node_id"))

        val expectedOperators = listOf(
            "keyword_search",
            "citation_expand",
            "union",
            "dedupe",
            "filter",
            "score",
            "top_k",
            "rerank",
            "extract_evidence",
        )
        val declaredIds = mutableSetOf<String>()
        val outputTypes = mutableMapOf<String, String>()
        val declaredOperators = mutableListOf<String>()

        repeat(nodes.length()) { index ->
            val node = nodes.getJSONObject(index)
            val nodeId = node.getString("id")
            assertTrue("Duplicate node id: $nodeId", declaredIds.add(nodeId))
            declaredOperators += node.getString("operator")

            val inputs = node.getJSONArray("inputs")
            val inputTypes = node.getJSONArray("input_types")
            assertEquals("Input/type arity mismatch for $nodeId", inputs.length(), inputTypes.length())
            repeat(inputs.length()) { inputIndex ->
                val inputId = inputs.getString(inputIndex)
                assertTrue("Input must reference an earlier node: $inputId", outputTypes.containsKey(inputId))
                assertEquals(
                    "Type mismatch at $nodeId <- $inputId",
                    outputTypes.getValue(inputId),
                    inputTypes.getString(inputIndex),
                )
            }
            outputTypes[nodeId] = node.getString("output_type")
        }

        assertEquals(expectedOperators, declaredOperators)
        assertEquals("evidence_set", outputTypes.getValue(workflow.getString("terminal_node_id")))

        val feedback = prototype.getJSONObject("feedback_contract")
        assertEquals(
            setOf("ask_user", "update_query", "add_node", "modify_node", "remove_node", "finalize"),
            feedback.getJSONArray("allowed_actions").toStringSet(),
        )
        assertTrue(feedback.getBoolean("new_revision_required"))
        assertTrue(feedback.getBoolean("reapproval_required_after_change"))

        val policy = prototype.getJSONObject("policy")
        assertEquals("disabled", policy.getString("network_access"))
        assertEquals("forbidden", policy.getString("external_side_effects"))
        assertFalse(policy.getBoolean("provider_credentials_on_android"))
        assertEquals(
            setOf("execute_external_search", "persist_workflow", "create_task"),
            policy.getJSONArray("requires_approval_before").toStringSet(),
        )
        assertEquals(
            setOf("shell", "arbitrary_code", "arbitrary_url_fetch", "workflow_generated_tool_names"),
            policy.getJSONArray("forbidden_capabilities").toStringSet(),
        )

        val limits = prototype.getJSONObject("limits")
        assertEquals(50, limits.getInt("max_candidate_papers"))
        assertEquals(10, limits.getInt("max_returned_papers"))
        assertEquals(9, limits.getInt("max_workflow_nodes"))
        assertEquals(2, limits.getInt("max_citation_hops"))
        assertEquals(1, limits.getInt("max_rerank_runs"))
        assertEquals(60, limits.getInt("max_runtime_seconds"))

        val sideEffects = prototype.getJSONObject("observed_side_effects")
        listOf("external_search_runs", "provider_calls", "persistent_writes", "task_board_writes")
            .forEach { metric -> assertEquals(metric, 0, sideEffects.getInt(metric)) }

        listOf(
            "## Проверка сигнала",
            "17 операторов",
            "## Аудит текущих точек интеграции",
            "SollGateway.listSources(...)` и `listSourceItemsPage(...)",
            "SollGateway.sendChatTurn(...)",
            "createTaskFromSourceItem(...)",
            "SollTaskGraph` и `TaskGraphReachabilityBuilder",
            "## Контракт прототипа",
            "## Safety и approval boundary",
            "## Focused pilot и promotion gates",
            "Promotion требует все шесть gates",
            "external literature searches/provider calls/model runs: `0`",
        ).forEach { control ->
            assertTrue("Missing PaperPilot design control: $control", design.contains(control))
        }

        listOf(
            "source_processing_result: paperpilot_workflow_design_completed_runtime_pilot_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-d4d88b2dcc5eb63f-verification.md",
            "source_value:",
            "1 proposal-only workflow design",
            "9 typed operators",
            "5 current Soll seams audited",
            "6 promotion gates",
            "1/1 focused contract test passed",
            "0 external searches/provider calls/model runs",
            "PaperPilotLiteratureSearchPrototypeTest",
        ).forEach { evidence ->
            assertTrue("Missing PaperPilot verification evidence: $evidence", verification.contains(evidence))
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
