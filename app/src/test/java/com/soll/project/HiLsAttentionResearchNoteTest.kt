package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HiLsAttentionResearchNoteTest {
    @Test
    fun `HiLS signal becomes a license gated non sensitive backlog note`() {
        val note = projectFile(
            "docs/knowledge/hils-attention-infinite-context-research-note.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-c6c4d227c167821c-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: e819f1c0ccf04d5c8e1aac2feb1fe900",
            "source_ref: source-item/9011e13c06d6/c6c4d227c167821c",
            "status: backlog_research_note_only_license_and_compute_blocked",
            "https://huggingface.co/papers/2607.02980",
            "https://arxiv.org/abs/2607.02980",
            "https://github.com/Tencent-Hunyuan/HiLS-Attention",
            "730105e1af294f098a04e33e5bee38578b79cabb",
            "## Backlog decision",
            "## GitHub license audit",
            "`license: null`",
            "`truncated: false`; `228` tracked entries",
            "no `LICENSE`, `LICENCE`, `COPYING` or `NOTICE` entry",
            "`pyproject.toml` has no `license` field",
            "model card separately declares Apache-2.0",
            "does not grant a license for the GitHub source tree",
            "## GPU and environment requirements",
            "PyTorch `2.8.0` with CUDA `12.8`",
            "`pretrain_1.4B_8K_300B_64gpu.yaml`",
            "single-H800 result is an inference benchmark",
            "training GPU model, per-GPU VRAM",
            "## Reproducibility audit",
            "`enable_full_determinism: false`",
            "do not pin an explicit seed value",
            "dataset revisions, complete manifests and content hashes",
            "## Safe reproduction checklist",
            "public dataset or locally generated non-sensitive corpus",
            "Training on private data requires a separate task, isolation design",
            "upstream training/evaluation commands executed: `0`",
            "private/user/production records read: `0`",
        ).forEach { control ->
            assertTrue("Missing HiLS backlog control: $control", note.contains(control))
        }

        listOf(
            "task_id: e819f1c0ccf04d5c8e1aac2feb1fe900",
            "source_ref: source-item/9011e13c06d6/c6c4d227c167821c",
            "source_processing_result: hils_research_note_added_license_and_training_blocked",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-c6c4d227c167821c-verification.md",
            "source_value:",
            "1 backlog research note",
            "1 GitHub commit / 228 tracked entries audited",
            "0 explicit GitHub code licenses found",
            "3 training-scale recipes audited",
            "8 reproducibility blockers",
            "8 safe reproduction gates",
            "1/1 focused contract test passed",
            "0 training runs",
            "HiLsAttentionResearchNoteTest",
        ).forEach { evidence ->
            assertTrue(
                "Missing HiLS verification evidence: $evidence",
                verification.contains(evidence),
            )
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
