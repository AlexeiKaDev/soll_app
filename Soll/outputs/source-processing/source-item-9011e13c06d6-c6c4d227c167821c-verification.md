---
task_id: e819f1c0ccf04d5c8e1aac2feb1fe900
project: soll_app
source_ref: source-item/9011e13c06d6/c6c4d227c167821c
source_item: "Hierarchical Sparse Attention Done Right: Toward Infinite Context Modeling"
source_processing_result: hils_research_note_added_license_and_training_blocked
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-c6c4d227c167821c-verification.md
source_value: "1 backlog research note; 1 GitHub commit / 228 tracked entries audited; 0 explicit GitHub code licenses found; 3 training-scale recipes audited; 8 reproducibility blockers and 8 safe reproduction gates documented; 1/1 focused contract test passed; 0 training runs, model/dataset downloads, private-data reads, production/runtime changes or external writes"
verified_at: 2026-07-27 Europe/Chisinau
---

# HiLS-Attention research-note verification

## Outcome

The Hugging Face Daily Papers signal produced one bounded Soll backlog note:
`docs/knowledge/hils-attention-infinite-context-research-note.md`.

The note records the paper mechanism, verifies the absence of an explicit
license in the official GitHub code tree, audits the published GPU/training
scale and defines reproducibility and data-isolation gates. Adoption and all
training remain blocked. No upstream code was copied or executed.

## Primary-source and license receipt

Read-only public checks were performed on 27 July 2026:

| Check | Observed result |
| --- | --- |
| Hugging Face paper | `https://huggingface.co/papers/2607.02980` |
| arXiv record | `2607.02980v1`, submitted 3 July 2026 |
| Official GitHub | `Tencent-Hunyuan/HiLS-Attention` |
| Audited commit | `730105e1af294f098a04e33e5bee38578b79cabb` |
| Commit date | `2026-07-25T07:25:06Z` |
| GitHub license metadata | `null` |
| Recursive tree | complete (`truncated: false`), `228` entries |
| License files | `0` LICENSE/LICENCE/COPYING/NOTICE entries |
| Package license field | absent from `pyproject.toml` |

The Hugging Face 7B checkpoint's Apache-2.0 metadata was kept separate from the
unlicensed GitHub source tree. Result: GitHub license verified as absent at the
pinned commit; import, modification and execution are not approved.

The task-referenced raw file is absent from this isolated worktree and was not
used as evidence.

## GPU and reproducibility receipt

The audit confirms a Linux x86_64, Python 3.11, PyTorch 2.8, CUDA 12.8,
FlashAttention/Triton/TileLang and NCCL/FSDP training stack.

| Scale | Audited recipe evidence |
| --- | --- |
| 345M | 8K context, micro-batch 4, global batch 128, 30K steps/about 30B tokens |
| 1.4B | 8K context, micro-batch 4, global batch 256, 143K steps/300B tokens; launcher selects the 64-GPU recipe |
| 7B CPT | 8K context, micro-batch 4, global batch 512, 13K steps/about 50B sampled tokens |

The only explicit GPU model in the paper's hardware results is one NVIDIA H800
for a 345M BF16 batch-1 inference comparison. It is not a training minimum.
Training GPU model, per-GPU VRAM, full topology, wall time and cost are absent,
so local capacity is not claimed.

Eight blockers and eight safe reproduction gates are pinned by the note. They
include explicit code licensing, fixed source/data revisions and hashes,
non-sensitive corpus review, fixed seeds/determinism, exact hardware and peak
memory receipts, a matched full-attention baseline, multi-seed quality/compute
metrics and manual non-promotion review.

## Safety receipt

- public read-only paper/repository checks: completed;
- upstream repository clone or code execution: `0`;
- training/evaluation runs: `0`;
- model/checkpoint/dataset downloads: `0`;
- private, user or production data reads: `0`;
- credentials accessed: `0`;
- Android/server/runtime/dependency changes: `0`;
- external writes, commits, pushes or deploys: `0`.

Any later training proposal is restricted to a public or locally generated
non-sensitive corpus in a separate isolated environment. Private-data training
requires a separate reviewed task and is not authorized by this note.

## Focused smoke result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.HiLsAttentionResearchNoteTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); `1/1 focused contract test
passed` with `0` failures, `0` errors and `0` skipped.

`HiLsAttentionResearchNoteTest` pins the task/source trace, backlog status,
paper/code links, commit, negative GitHub-license finding, separate model-card
license, CUDA/64-GPU evidence, missing VRAM/seed/data receipts, non-sensitive
data boundary, zero-training scope and all three value-metric keys.

## Value metric update

- `source_processing_result`:
  `hils_research_note_added_license_and_training_blocked`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-c6c4d227c167821c-verification.md`;
- `source_value`: 1 backlog research note; 1 GitHub commit / 228 tracked
  entries audited; 0 explicit GitHub code licenses found; 3 training-scale
  recipes audited; 8 reproducibility blockers and 8 safe reproduction gates
  documented; 1/1 focused contract test passed. Training runs, model/dataset
  downloads, private-data reads, runtime changes and external writes: `0`.

The measurable Soll value is a durable license/compute/reproducibility gate.
Model quality, long-context accuracy and compute savings for Soll remain
unmeasured.
