package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeKvAdaptiveCacheSourceAnalysisTest {
    @Test
    fun `SeKV source becomes a bounded server prototype contract`() {
        val analysis = projectFile(
            "docs/knowledge/sekv-adaptive-kv-cache-soll-analysis.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-14bcf766ab5a4439-verification.md",
        ).readText().normalizeWhitespace()
        val activeLlamaDefaults = projectFile(
            "tools/llama-cpp/llama_cpp_active_defaults.json",
        ).readText()
        val gateway = projectFile(
            "app/src/main/java/com/soll/domain/soll/SollGateway.kt",
        ).readText()

        listOf(
            "Task: `3726a86c0edd415d8f36d3112b7d5d4f`",
            "`source_ref=source-item/9011e13c06d6/14bcf766ab5a4439`",
            "отсутствует в изолированном worktree",
            "https://huggingface.co/papers/2606.31145",
            "https://arxiv.org/abs/2606.31145",
            "https://arxiv.org/html/2606.31145",
            "`2606.31145v1`",
            "**CC BY 4.0**",
            "`18` page objects",
            "`931835c45ac1ac579732ea0d11b14e01845d3d545e8b71fe6790f801c9ba0302`",
            "`aff648c2ae94828e07e979040adfa8a5a48c0b26b26341ea8ea37a086d52088f`",
            "https://github.com/AmirAbaskohi/SeKV",
            "`6569d111d3ace5c7c1ad596bf36962a99cd7e94b`",
            "MIT, `32` blobs и `0` upstream test files",
            "`H_t = -log p(x_t | x_<t)`",
            "`H_t > mean(H) + alpha * std(H)`",
            "paper `L_min=16`",
            "paper cap `R_max=32`",
            "`log(|S|)` size prior",
            "одном softmax",
            "около `4.3M` параметров",
            "около `0.5B` tokens",
            "`8xA100 80GB`",
            "`20/20` compressed benchmark/model cells",
            "`53.3%` меньше GPU memory",
            "## Pinned implementation audit",
            "**Segmentation default:**",
            "**Model identity:**",
            "**Context protocol:**",
            "**GSM8K protocol:**",
            "**Budget protocol:**",
            "**Parallelism and performance:**",
            "**GPU residency risk:**",
            "**Reproducibility:**",
            "`SollGateway.askModelChat(...)`",
            "`androidRuntimeDefault: soll-backend-route`",
            "`packageIntoAndroidApp: false`",
            "## Approval-gated prototype contract",
            "### Phase A — static and tensor correctness",
            "### Phase B — one-model offline pilot",
            "### Promotion gates",
            "TTFT p50/p95",
            "TPOT p50/p95",
            "`0` model downloads",
            "`0` production/runtime changes",
        ).forEach { control ->
            assertTrue("Missing SeKV analysis control: $control", analysis.contains(control))
        }

        listOf(
            "task_id: 3726a86c0edd415d8f36d3112b7d5d4f",
            "source_ref: source-item/9011e13c06d6/14bcf766ab5a4439",
            "source_processing_result: full_text_and_pinned_code_audited_prototype_contract_defined",
            "verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-14bcf766ab5a4439-verification.md",
            "18-page paper and complete TeX archive verified by SHA-256",
            "11 implementation modules and 4 current Soll seams audited",
            "8 reproduction gaps and 3 prototype phases/gates documented",
            "1/1 focused contract test passed",
            "0 model downloads, SeKV runs, dependency imports or production/runtime changes",
        ).forEach { evidence ->
            assertTrue("Missing SeKV verification evidence: $evidence", verification.contains(evidence))
        }

        assertTrue(activeLlamaDefaults.contains("\"androidRuntimeDefault\": \"soll-backend-route\""))
        assertTrue(activeLlamaDefaults.contains("\"packageIntoAndroidApp\": false"))
        assertTrue(gateway.contains("suspend fun askModelChat("))
        assertFalse(projectFile("app/src/main").walkTopDown().any { it.name.contains("sekv", ignoreCase = true) })
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
