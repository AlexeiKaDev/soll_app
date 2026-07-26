package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceTrl180ExperimentReferenceTest {
    @Test
    fun `TRL 1 8 0 signal becomes a bounded RL experiment reference`() {
        val knowledge = projectFile(
            "docs/knowledge/hugging-face-trl-v1-8-0-rl-experiment-reference.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-f0bbb7448fc0420ab8146f4567422870-trl-v1-8-0-audit.md",
        ).readText()

        listOf(
            "task_id: f0bbb7448fc0420ab8146f4567422870",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/314a49ff1073",
            "source_trust: untrusted_external_content",
            "release: v1.8.0",
            "release_commit: 95809b9",
            "monitored/hugging-face-trl-releases/20260709-233804-v1-8-0-7556880b.md",
            "https://github.com/huggingface/trl/releases/tag/v1.8.0",
            "desktop/server experiment reference",
            "Stable KTO API",
            "Environment-owned reward",
            "Multiple environments",
            "GRPO entropy regularization",
            "Direct quantization configuration",
            "MoE auxiliary loss",
            "Packing-aware AsyncGRPO",
            "Compatibility changes",
            "`SollGateway.askModelChat(...)`",
            "`docs/knowledge/soll-source-monitoring-kb-eval-v1.json`",
            "`docs/knowledge/transfer-aware-curriculum-soll-ranking-hypothesis.md`",
            "## Five bounded experiment cards",
            "reward_call_count / completed_rollout_count",
            "cross-environment tool-schema leaks (target `0`)",
            "## Six promotion gates",
            "**Reward validity.**",
            "**Approval and rollback.**",
            "Actual TRL experiments completed: **0**",
            "model-quality, reward, throughput or memory improvement for Soll: **0**",
        ).forEach { control ->
            assertTrue("Missing TRL v1.8.0 knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: rl_experiment_reference_added_runs_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-f0bbb7448fc0420ab8146f4567422870-trl-v1-8-0-audit.md",
            "1 TRL v1.8.0 KB reference added",
            "8 release/API changes cataloged",
            "5 experiment cards defined",
            "4 current Soll seams audited",
            "6 promotion gates defined",
            "focused contract test",
            "0 TRL/model training runs",
            "0 Android production files changed",
            "HuggingFaceTrl180ExperimentReferenceTest",
        ).forEach { evidence ->
            assertTrue("Missing TRL v1.8.0 verification evidence: $evidence", verification.contains(evidence))
        }
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
