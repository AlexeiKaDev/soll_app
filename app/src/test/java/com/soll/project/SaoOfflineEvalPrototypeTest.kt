package com.soll.project

import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaoOfflineEvalPrototypeTest {
    @Test
    fun `SAO source contract documents algorithm value requirements and safety boundary`() {
        val knowledgeRaw = projectFile(
            "docs/knowledge/sao-single-rollout-offline-eval.md",
        ).readText()
        val knowledge = knowledgeRaw.normalizeWhitespace()
        val fixture = JSONObject(
            projectFile("docs/knowledge/sao-soll-agent-offline-eval-v1.json").readText(),
        )
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-e9091c296290ee7e-verification.md",
        ).readText().normalizeWhitespace()

        assertEquals("sao-soll-agent-offline-eval-v1", fixture.getString("suite_id"))
        assertEquals(1, fixture.getInt("version"))
        assertEquals("offline_synthetic_audit_only", fixture.getString("mode"))
        assertEquals(
            "source-item/9011e13c06d6/e9091c296290ee7e",
            fixture.getString("source_ref"),
        )

        listOf(
            "## SAO algorithm",
            "sample exactly one trajectory",
            "form a direct behavior ratio",
            "Apply the strict double-sided DIS calibration",
            "exclude observation tokens",
            "Update the critic more often than the actor",
            "## Direct double-sided Importance Sampling and clipping",
            "`r_t(theta) = exp(log pi_theta(a_t | s_t) - log pi_rollout(a_t | s_t))`",
            "`1 - epsilon_low < r < 1 + epsilon_high`",
            "an out-of-range token is zeroed, not numerically clamped",
            "## Value-model requirements",
            "**Cold-start quality.**",
            "**Faster critic schedule.**",
            "**Stable parameter subset.**",
            "**Token-level action values.**",
            "**Skip-observation GAE.**",
            "**Diagnostics before actor use.**",
            "freezes the critic's attention modules",
            "architecture-specific empirical intervention",
            "## Offline Soll prototype",
            "Passing an offline report would still not authorize training or production use",
            "production/runtime/API/UI/dependency changes: `0`",
        ).forEach { control ->
            assertTrue("Missing SAO knowledge control: $control", knowledge.contains(control))
        }

        val policy = fixture.getJSONObject("data_policy")
        assertEquals("synthetic", policy.getString("fixture_class"))
        assertFalse(policy.getBoolean("contains_personal_data"))
        assertFalse(policy.getBoolean("contains_credentials"))
        assertEquals("forbidden", policy.getString("production_task_history_access"))
        assertEquals("forbidden", policy.getString("model_or_agent_execution"))
        assertEquals("disabled", policy.getString("network_access"))
        assertEquals("forbidden", policy.getString("external_side_effects"))

        val gate = fixture.getJSONObject("promotion_gate")
        assertEquals("synthetic_smoke_only", gate.getString("status"))
        assertFalse(gate.getBoolean("production_eligible"))
        assertTrue(gate.getBoolean("requires_separately_approved_real_replay"))
        assertFalse(gate.getBoolean("model_training_allowed"))
        assertFalse(gate.getBoolean("model_weight_updates_allowed"))
        assertFalse(gate.getBoolean("automatic_task_mutation_allowed"))
        assertFalse(gate.getBoolean("agent_or_tool_execution_allowed"))
        assertFalse(gate.getBoolean("external_actions_allowed"))

        listOf(
            "task_id: a0ea55d17b354cdeaf385ab485645435",
            "source_ref: source-item/9011e13c06d6/e9091c296290ee7e",
            "source_processing_result: sao_deep_dive_offline_eval_prototype_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-e9091c296290ee7e-verification.md",
            "source_value:",
            "1 SAO deep-dive note",
            "3 single-rollout prompts",
            "6 action tokens and 3 observation tokens",
            "4/6 action tokens retained",
            "2/2 focused tests passed",
            "0 model training, production model updates, agent executions, external actions or runtime changes",
            "SaoOfflineEvalPrototypeTest",
        ).forEach { evidence ->
            assertTrue("Missing SAO verification evidence: $evidence", verification.contains(evidence))
        }
    }

    @Test
    fun `synthetic offline replay audits DIS and critic signals without training`() {
        val fixture = JSONObject(
            projectFile("docs/knowledge/sao-soll-agent-offline-eval-v1.json").readText(),
        )
        val controls = fixture.getJSONObject("paper_controls")
        val expected = fixture.getJSONObject("expected_summary")

        assertEquals(1, controls.getInt("rollouts_per_prompt"))
        assertTrue(controls.getBoolean("strict_open_interval"))
        assertEquals(2, controls.getInt("critic_updates_per_actor"))
        assertFalse(controls.getBoolean("observation_tokens_in_value_metrics"))
        assertTrue(controls.getBoolean("frozen_attention_is_architecture_specific"))
        assertTrue(controls.getBoolean("scaled_value_pretraining_required"))

        val epsilonLow = controls.getDouble("epsilon_low")
        val epsilonHigh = controls.getDouble("epsilon_high")
        val summary = evaluate(fixture, epsilonLow, epsilonHigh)

        assertEquals(expected.getInt("prompt_count"), summary.promptCount)
        assertEquals(expected.getInt("rollout_count"), summary.rolloutCount)
        assertEquals(expected.getInt("action_token_count"), summary.actionTokenCount)
        assertEquals(expected.getInt("observation_token_count"), summary.observationTokenCount)
        assertEquals(
            expected.getInt("action_to_action_bridge_count"),
            summary.actionToActionBridgeCount,
        )
        assertEquals(
            expected.getInt("retained_action_token_count"),
            summary.retainedActionTokenCount,
        )
        assertEquals(
            expected.getInt("masked_action_token_count"),
            summary.maskedActionTokenCount,
        )
        assertEquals(
            expected.getDouble("masked_action_token_rate"),
            summary.maskedActionTokenRate,
            1e-12,
        )
        assertEquals(expected.getDouble("value_mae"), summary.valueMae, 1e-12)
        assertEquals(
            expected.getDouble("value_explained_variance"),
            summary.valueExplainedVariance,
            1e-12,
        )

        val lowerBoundary = 1.0 - epsilonLow
        val upperBoundary = 1.0 + epsilonHigh
        assertEquals(0.0, disWeight(lowerBoundary, epsilonLow, epsilonHigh), 0.0)
        assertEquals(0.0, disWeight(upperBoundary, epsilonLow, epsilonHigh), 0.0)
        assertTrue(disWeight(lowerBoundary + 1e-6, epsilonLow, epsilonHigh) > 0.0)
        assertTrue(disWeight(upperBoundary - 1e-6, epsilonLow, epsilonHigh) > 0.0)
    }

    private fun evaluate(
        fixture: JSONObject,
        epsilonLow: Double,
        epsilonHigh: Double,
    ): Summary {
        val rollouts = fixture.getJSONArray("rollouts")
        val promptIds = mutableSetOf<String>()
        val values = mutableListOf<Double>()
        val returns = mutableListOf<Double>()
        var actionTokenCount = 0
        var observationTokenCount = 0
        var actionToActionBridgeCount = 0
        var retainedActionTokenCount = 0
        var maskedActionTokenCount = 0

        repeat(rollouts.length()) { rolloutIndex ->
            val rollout = rollouts.getJSONObject(rolloutIndex)
            val promptId = rollout.getString("prompt_id")
            require(promptIds.add(promptId)) { "More than one rollout for prompt $promptId" }

            val tokens = rollout.getJSONArray("tokens")
            var actionsInRollout = 0
            repeat(tokens.length()) { tokenIndex ->
                val token = tokens.getJSONObject(tokenIndex)
                when (token.getString("kind")) {
                    "observation" -> {
                        observationTokenCount += 1
                        require(!token.has("rollout_log_probability"))
                        require(!token.has("candidate_log_probability"))
                        require(!token.has("value_prediction"))
                        require(!token.has("return"))
                    }

                    "action" -> {
                        actionTokenCount += 1
                        actionsInRollout += 1
                        val rolloutLogProbability = token.getDouble("rollout_log_probability")
                        val candidateLogProbability = token.getDouble("candidate_log_probability")
                        require(rolloutLogProbability.isFinite())
                        require(candidateLogProbability.isFinite())

                        val ratio = exp(candidateLogProbability - rolloutLogProbability)
                        if (disWeight(ratio, epsilonLow, epsilonHigh) == 0.0) {
                            maskedActionTokenCount += 1
                        } else {
                            retainedActionTokenCount += 1
                        }
                        values += token.getDouble("value_prediction")
                        returns += token.getDouble("return")
                    }

                    else -> error("Unknown token kind: ${token.getString("kind")}")
                }
            }
            actionToActionBridgeCount += (actionsInRollout - 1).coerceAtLeast(0)
        }

        require(values.size == returns.size && values.isNotEmpty())
        val residuals = returns.zip(values) { actual, predicted -> actual - predicted }
        val valueMae = residuals.map(::abs).average()
        val returnVariance = variance(returns)
        require(returnVariance > 0.0) { "Explained variance requires non-constant returns" }
        val explainedVariance = 1.0 - variance(residuals) / returnVariance

        return Summary(
            promptCount = promptIds.size,
            rolloutCount = rollouts.length(),
            actionTokenCount = actionTokenCount,
            observationTokenCount = observationTokenCount,
            actionToActionBridgeCount = actionToActionBridgeCount,
            retainedActionTokenCount = retainedActionTokenCount,
            maskedActionTokenCount = maskedActionTokenCount,
            maskedActionTokenRate = maskedActionTokenCount.toDouble() / actionTokenCount,
            valueMae = valueMae,
            valueExplainedVariance = explainedVariance,
        )
    }

    private fun disWeight(
        ratio: Double,
        epsilonLow: Double,
        epsilonHigh: Double,
    ): Double = if (
        ratio > 1.0 - epsilonLow &&
        ratio < 1.0 + epsilonHigh
    ) {
        ratio
    } else {
        0.0
    }

    private fun variance(values: List<Double>): Double {
        val mean = values.average()
        return values.sumOf { value -> (value - mean) * (value - mean) } / values.size
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

    private data class Summary(
        val promptCount: Int,
        val rolloutCount: Int,
        val actionTokenCount: Int,
        val observationTokenCount: Int,
        val actionToActionBridgeCount: Int,
        val retainedActionTokenCount: Int,
        val maskedActionTokenCount: Int,
        val maskedActionTokenRate: Double,
        val valueMae: Double,
        val valueExplainedVariance: Double,
    )
}
