package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceTrl171PostTrainingKnowledgeTest {
    @Test
    fun `TRL 1 7 1 signal becomes a bounded post training note`() {
        val knowledge = projectFile(
            "docs/knowledge/hugging-face-trl-v1-7-1-non-nvlink-post-training.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-14ce4b268f384af6ab4d5b19ddb40a46-trl-v1-7-1-audit.md",
        ).readText()

        listOf(
            "task_id: 14ce4b268f384af6ab4d5b19ddb40a46",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/6931f077418d",
            "source_trust: untrusted_external_content",
            "section: LLM/post-training",
            "release: v1.7.1",
            "monitored/hugging-face-trl-releases/20260709-233804-v1-7-1-7dfd65ac.md",
            "Файл monitored source отсутствует в изолированном worktree",
            "**GRPO + vLLM + PEFT**",
            "## Применимость к Soll",
            "**Pinned environment.**",
            "**Topology proof.**",
            "**Three-area smoke.**",
            "**Measured comparison.**",
            "**Promotion and rollback.**",
            "Выполнено **0** training/inference runs",
            "Production/runtime files и Android dependencies не менялись",
        ).forEach { control ->
            assertTrue("Missing TRL v1.7.1 knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: llm_post_training_kb_note_added_runtime_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-14ce4b268f384af6ab4d5b19ddb40a46-trl-v1-7-1-audit.md",
            "1 LLM/post-training KB note added",
            "3 compatibility areas captured",
            "5 experiment gates defined",
            "1/1 focused contract test passed",
            "0 training/inference runs",
            "0 production/runtime files changed",
            "HuggingFaceTrl171PostTrainingKnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing TRL v1.7.1 audit evidence: $evidence", verification.contains(evidence))
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
