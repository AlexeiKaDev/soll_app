package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VllmTransformersBackendAnalysisTest {
    @Test
    fun `Transformers backend remains a measured server candidate instead of an Android default`() {
        val analysis = projectFile(
            "docs/knowledge/vllm-transformers-backend-soll-app-analysis.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-aa8dfba5f72342bcb30624ed9b529173-vllm-transformers-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Task: `aa8dfba5f72342bcb30624ed9b529173`",
            "`source_ref=insight/2b0ac2f1734b`",
            "Fine-tune video and image models at scale",
            "отдельному посту",
            "https://huggingface.co/blog/native-speed-vllm-transformers-backend",
            "https://github.com/vllm-project/vllm/pull/47187",
            "https://github.com/vllm-project/vllm/releases/tag/v0.25.0",
            "https://pypi.org/project/vllm/0.25.1/",
            "https://docs.vllm.ai/en/latest/models/supported_models/",
            "https://huggingface.co/docs/transformers/main/transformers_as_backend",
            "`vllm==0.25.1`",
            "`>=3.10,<3.15`",
            "совместимую Transformers v5 dependency",
            "`ALL_ATTENTION_FUNCTIONS`",
            "`_supports_attention_backend = True`",
            "linear attention на момент публикации не поддержан",
            "`--trust-remote-code` не является безопасным shortcut",
            "`Fused:` operations",
            "пять проверенных seams текущего worktree",
            "`SollGateway.sendChatTurn(...)`",
            "`SollGateway.askModelChat(...)`",
            "Transformers throughput не ниже `95%` native",
            "не хуже native более чем на `5%`",
            "Выполнено `0` Soll inference benchmark runs",
        ).forEach { control ->
            assertTrue("Missing vLLM/Transformers analysis control: $control", analysis.contains(control))
        }

        listOf(
            "task_id: aa8dfba5f72342bcb30624ed9b529173",
            "source_ref: insight/2b0ac2f1734b",
            "source_processing_result: requirements_analysis_completed_runtime_pilot_deferred",
            "6 primary upstream surfaces and 5 current Soll seams audited",
            "5 promotion gates defined",
            "1 source-title mismatch resolved",
            "0 production/runtime changes and 0 measured Soll vLLM benchmark value",
            "The monitored source artifact is not vendored",
            "VllmTransformersBackendAnalysisTest",
            "`0` native-vs-Transformers Soll benchmark runs",
        ).forEach { evidence ->
            assertTrue("Missing vLLM/Transformers audit evidence: $evidence", audit.contains(evidence))
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
