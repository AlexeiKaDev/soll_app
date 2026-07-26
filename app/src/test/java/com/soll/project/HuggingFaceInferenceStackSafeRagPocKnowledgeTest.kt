package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceInferenceStackSafeRagPocKnowledgeTest {
    @Test
    fun `Hugging Face stack note selects a fail closed local embedding PoC`() {
        val knowledge = projectFile(
            "docs/knowledge/hugging-face-inference-stack-safe-rag-poc.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-2ac7c9adc8f0-92b3fb6e7bafb440-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: beb1f28015b14e1a80af6cb6eccf06ce",
            "source_ref: source-item/2ac7c9adc8f0/92b3fb6e7bafb440",
            "raw_status: absent_in_isolated_worktree",
            "**TEI (Text Embeddings Inference)**",
            "**TGI (Text Generation Inference)**",
            "**Inference Providers**",
            "**safetensors**",
            "maintenance mode",
            "внешний data/credential/billing boundary",
            "не знак доверия модели",
            "локальный TEI embedding-only retrieval",
            "модель не выбирается, не скачивается и не загружается",
            "без `trust_remote_code`",
            "отключённый egress",
            "loopback `/embed`",
            "cosine similarity",
            "`3` document embeddings + `1` query embedding",
            "`0` outbound calls",
            "`0` downloads",
            "`0` tool calls",
            "`0` persistent runtime changes",
            "Если approved model или изоляция не подтверждены",
            "safetensors + pinned hash + allowlist + no remote code + isolation",
            "https://huggingface.co/docs/text-embeddings-inference/index",
            "https://huggingface.co/docs/text-generation-inference/index",
            "https://huggingface.co/docs/inference-providers/index",
            "https://huggingface.co/docs/safetensors/index",
        ).forEach { control ->
            assertTrue("Missing safe RAG control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: beb1f28015b14e1a80af6cb6eccf06ce",
            "source_processing_result: kb_note_added_safe_tei_poc_selected",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-2ac7c9adc8f0-92b3fb6e7bafb440-verification.md",
            "1 Soll KB note added",
            "4 Hugging Face stack roles compared",
            "1 fail-closed local TEI embedding PoC selected",
            "1/1 focused contract test passed",
            "0 models downloaded or loaded",
            "0 runtime or dependency changes",
            "HuggingFaceInferenceStackSafeRagPocKnowledgeTest",
            "отсутствует в isolated worktree",
        ).forEach { evidence ->
            assertTrue("Missing safe RAG verification evidence: $evidence", verification.contains(evidence))
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
