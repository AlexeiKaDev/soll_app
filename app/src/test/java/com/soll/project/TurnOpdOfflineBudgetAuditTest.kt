package com.soll.project

import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnOpdOfflineBudgetAuditTest {
    @Test
    fun `TurnOPD source becomes a formula note with an offline-only safety boundary`() {
        val knowledge = projectFile(
            "docs/knowledge/turnopd-turn-aware-budget-offline-audit.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-f94e66941d30b4cb-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 28b03fff52b24ac8902b9f0c2e1673a1",
            "source_ref: source-item/9011e13c06d6/f94e66941d30b4cb",
            "https://huggingface.co/papers/2607.05804",
            "https://arxiv.org/abs/2607.05804",
            "arxiv: 2607.05804v1",
            "raw/monitored\\hugging-face-daily-papers\\" +
                "20260708-220900-turnopd-making-on-policy-distillation-turn-aware-ba3fa721.md",
            "absent from this isolated worktree",
            "## Controller 1: adaptive rollout depth",
            "`m_t = max(K_t, 0) * (n_t / n_0)`",
            "`q_t = m_t / (sum_j m_j + epsilon)`",
            "`H_eff_bar = sum_t t * q_t`",
            "`H_cov = Q_hat_p(L_succ) = min{H : F_succ(H) >= p}`",
            "`H_ctrl = max(H_eff, H_cov)`",
            "`H_hat_(k+1) = clip(round(H_bar_k) + 1, H_min, H_max)`",
            "Only uncensored full-depth probe rollouts update the controller",
            "## Controller 2: progressive turn-normalized loss",
            "`q_traj_t = n_t / sum_j n_j`",
            "`q_turn_t = 1 / T`",
            "`alpha = clip((progress - s) / (e - s), 0, 1)`",
            "`q_blend_t = (1 - alpha) * q_traj_t + alpha * q_turn_t`",
            "one sampled action probability cannot reconstruct the reverse-KL",
            "successful full-depth probes eligible for the reference coverage update: `2/8`",
            "production/user trace reads: `0`",
            "model training, gradient or weight updates: `0`",
        ).forEach { control ->
            assertTrue("Missing TurnOPD knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: 28b03fff52b24ac8902b9f0c2e1673a1",
            "project: soll_app",
            "source_ref: source-item/9011e13c06d6/f94e66941d30b4cb",
            "source_processing_result: turnopd_formulas_extracted_offline_budget_audit_smoke_passed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-f94e66941d30b4cb-verification.md",
            "source_value:",
            "2 existing synthetic non-production fixtures audited",
            "3 SAO rollouts/6 action turns",
            "exact turn loss allocation 50%/50%",
            "diagnostic 80% success depth 2 rejected for controller refresh at 2/8 successful probes",
            "2/2 focused tests passed",
            "0 exact reverse-KL traces",
            "TurnOpdOfflineBudgetAuditTest",
        ).forEach { evidence ->
            assertTrue(
                "Missing TurnOPD verification evidence: $evidence",
                verification.contains(evidence),
            )
        }
    }

    @Test
    fun `existing synthetic traces expose turn budget value and controller gaps`() {
        val sao = JSONObject(
            projectFile("docs/knowledge/sao-soll-agent-offline-eval-v1.json").readText(),
        )
        val saoPolicy = sao.getJSONObject("data_policy")
        assertEquals("offline_synthetic_audit_only", sao.getString("mode"))
        assertEquals("synthetic", saoPolicy.getString("fixture_class"))
        assertFalse(saoPolicy.getBoolean("contains_personal_data"))
        assertFalse(saoPolicy.getBoolean("contains_credentials"))
        assertEquals("forbidden", saoPolicy.getString("production_task_history_access"))
        assertEquals("forbidden", saoPolicy.getString("model_or_agent_execution"))

        val turns = mutableListOf<ActionTurn>()
        val completionDepths = mutableListOf<Int>()
        val successfulCompletionDepths = mutableListOf<Int>()
        val rollouts = sao.getJSONArray("rollouts")

        repeat(rollouts.length()) { rolloutIndex ->
            val rollout = rollouts.getJSONObject(rolloutIndex)
            assertFalse(
                "Existing SAO fixture has no uncensored full-depth probe marker",
                rollout.has("full_depth_probe"),
            )
            val tokens = rollout.getJSONArray("tokens")
            var actionTurn = 0
            var terminalReturn = 0.0
            repeat(tokens.length()) { tokenIndex ->
                val token = tokens.getJSONObject(tokenIndex)
                if (token.getString("kind") == "action") {
                    terminalReturn = token.getDouble("return")
                    assertFalse("Fixture must not invent exact reverse KL", token.has("reverse_kl"))
                    assertFalse(
                        "Fixture must not invent a teacher distribution",
                        token.has("teacher_distribution"),
                    )
                    turns += ActionTurn(
                        turn = actionTurn,
                        absoluteSampledLogRatio = abs(
                            token.getDouble("candidate_log_probability") -
                                token.getDouble("rollout_log_probability"),
                        ),
                    )
                    actionTurn += 1
                }
            }
            completionDepths += actionTurn
            if (terminalReturn == 1.0) successfulCompletionDepths += actionTurn
        }

        assertEquals(3, rollouts.length())
        assertEquals(listOf(2, 2, 2), completionDepths)
        assertEquals(6, turns.size)
        assertEquals(listOf(3, 3), (0..1).map { turn -> turns.count { it.turn == turn } })
        assertEquals(2, successfulCompletionDepths.size)

        val trajectoryShares = (0..1).map { turn ->
            turns.count { it.turn == turn }.toDouble() / turns.size
        }
        val turnShares = List(2) { 1.0 / 2.0 }
        val blendedSharesAtHalfProgress = trajectoryShares.zip(turnShares).map { (trajectory, turn) ->
            0.5 * trajectory + 0.5 * turn
        }
        assertEquals(listOf(0.5, 0.5), trajectoryShares)
        assertEquals(listOf(0.5, 0.5), turnShares)
        assertEquals(listOf(0.5, 0.5), blendedSharesAtHalfProgress)

        val coverageDepth = empiricalQuantile(successfulCompletionDepths, 0.80)
        assertEquals(2, coverageDepth)
        assertTrue(
            "Two successes must remain below the paper-like eight-probe refresh guard",
            successfulCompletionDepths.size < 8,
        )

        val driftMeans = (0..1).map { turn ->
            turns.filter { it.turn == turn }.map { it.absoluteSampledLogRatio }.average()
        }
        assertEquals(0.706754512066697, driftMeans[0], 1e-12)
        assertEquals(0.9293643565706362, driftMeans[1], 1e-12)
        val driftTotal = driftMeans.sum()
        val diagnosticProxyMass = driftMeans.map { it / driftTotal }
        assertEquals(0.4319701493665484, diagnosticProxyMass[0], 1e-12)
        assertEquals(0.5680298506334516, diagnosticProxyMass[1], 1e-12)

        val agentLens = JSONObject(
            projectFile("docs/knowledge/agentlens-soll-ci-harness-v1.json").readText(),
        )
        val ciSmoke = agentLens.getJSONObject("ci_smoke")
        assertEquals("embedded_synthetic", ciSmoke.getString("fixture_class"))
        val trajectory = ciSmoke.getJSONObject("trajectory")
        val events = trajectory.getJSONArray("events")
        assertEquals(6, events.length())
        assertEquals(
            3,
            (0 until events.length()).count { index ->
                events.getJSONObject(index).getString("kind") == "tool_call"
            },
        )
        repeat(events.length()) { index ->
            val event = events.getJSONObject(index)
            assertFalse("AgentLens fixture has no explicit turn ids", event.has("turn_id"))
            assertFalse("AgentLens fixture has no per-turn token budget", event.has("token_count"))
            assertFalse("AgentLens fixture has no exact reverse KL", event.has("reverse_kl"))
        }

        val safety = trajectory.getJSONObject("safety")
        listOf(
            "network_call_count",
            "secret_read_count",
            "external_write_count",
            "repository_mutation_count",
            "auto_repository_action_count",
        ).forEach { key -> assertEquals("Unsafe count for $key", 0, safety.getInt(key)) }

        assertFalse(sao.getJSONObject("promotion_gate").getBoolean("production_eligible"))
        assertFalse(
            "Current fixtures cannot instantiate the TurnOPD controller",
            successfulCompletionDepths.size >= 8 &&
                turns.all { it.hasExactReverseKl } &&
                events.length() > 0,
        )
    }

    private fun empiricalQuantile(values: List<Int>, probability: Double): Int {
        require(values.isNotEmpty())
        require(probability > 0.0 && probability <= 1.0)
        val sorted = values.sorted()
        val rank = ceil(probability * sorted.size).toInt().coerceAtLeast(1)
        return sorted[rank - 1]
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

    private data class ActionTurn(
        val turn: Int,
        val absoluteSampledLogRatio: Double,
        val hasExactReverseKl: Boolean = false,
    )
}
