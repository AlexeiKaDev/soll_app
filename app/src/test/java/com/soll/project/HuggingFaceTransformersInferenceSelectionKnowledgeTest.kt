package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceTransformersInferenceSelectionKnowledgeTest {
    @Test
    fun `Transformers source becomes a bounded inference selection note`() {
        val knowledge = projectFile(
            "docs/knowledge/hugging-face-transformers-inference-selection.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-2ac7c9adc8f0-ec1d24e8b04b4de0-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: d9b29c020651421dae4ea982ec865a87",
            "source_ref: source-item/2ac7c9adc8f0/ec1d24e8b04b4de0",
            "raw_status: absent_in_isolated_worktree",
            "Transformers `Pipeline`",
            "Transformers `generate()`",
            "| vLLM |",
            "| SGLang |",
            "| TGI |",
            "Android остаётся клиентом существующего server API",
            "`TextStreamer`",
            "`TextIteratorStreamer`",
            "`AsyncTextIteratorStreamer`",
            "`put()` и `end()`",
            "`TimeoutError`",
            "AutoClass выбирает реализацию",
            "`apply_chat_template(..., tokenize=True)`",
            "`trust_remote_code=True`",
            "pin commit hash через `revision`",
            "заявляют одинаковую performance с dedicated vLLM implementation",
            "только при выполнении всех backend requirements",
            "`_supports_attention_backend = True`",
            "TGI находится в maintenance mode",
            "https://huggingface.co/docs/transformers/main/en/generation_features",
            "https://docs.vllm.ai/en/stable/models/supported_models/",
            "https://docs.sglang.io/supported_models/transformers_fallback.html",
            "https://huggingface.co/docs/text-generation-inference/main/index",
        ).forEach { control ->
            assertTrue("Missing Transformers inference control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: d9b29c020651421dae4ea982ec865a87",
            "source_processing_result: kb_note_added_current_docs_verified",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-2ac7c9adc8f0-ec1d24e8b04b4de0-verification.md",
            "1 Soll KB note added",
            "5 inference paths compared",
            "3 generate streaming classes and custom streamer contract verified",
            "3 serving-engine compatibility boundaries recorded",
            "1/1 focused contract test passed",
            "0 runtime or dependency changes",
            "HuggingFaceTransformersInferenceSelectionKnowledgeTest",
            "отсутствует в isolated worktree",
        ).forEach { evidence ->
            assertTrue("Missing Transformers verification evidence: $evidence", verification.contains(evidence))
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
