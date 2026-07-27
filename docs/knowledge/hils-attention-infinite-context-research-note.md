---
title: HiLS-Attention infinite-context research note
source: Hugging Face Daily Papers
source_url: https://huggingface.co/papers/2607.02980
arxiv: 2607.02980v1
code: https://github.com/Tencent-Hunyuan/HiLS-Attention
code_commit: 730105e1af294f098a04e33e5bee38578b79cabb
code_license: no_explicit_license_at_audited_commit
source_ref: source-item/9011e13c06d6/c6c4d227c167821c
task_id: e819f1c0ccf04d5c8e1aac2feb1fe900
status: backlog_research_note_only_license_and_compute_blocked
reviewed_at: 2026-07-27 Europe/Chisinau
---

# HiLS-Attention: research note for future Soll long-context work

## Backlog decision

Keep HiLS-Attention in the Soll backlog as a research note only. The useful
idea is hierarchical chunk retrieval whose scores participate in the language
modeling loss, so chunk selection is learned end to end instead of being a
detached heuristic. This may eventually matter for server-side navigation over
large, non-sensitive knowledge collections.

There is no current Soll adoption or training task. The audited GitHub code has
no explicit code license, the released training recipes require a substantial
CUDA/distributed stack, and the upstream evidence does not establish value on
a Soll corpus. Do not copy, vendor, modify or run the upstream training code
until the license and compute gates below are closed.

Any later reproduction must use only public datasets or locally generated,
non-sensitive corpora in a separate isolated environment. Private Soll data,
user conversations, task history, credentials and production memory are
excluded. Training on private data requires a separate task, isolation design,
data-owner approval and security/privacy review.

## Source trace

- Hugging Face item: <https://huggingface.co/papers/2607.02980>;
- primary paper: <https://arxiv.org/abs/2607.02980>;
- paper version reviewed: `arXiv:2607.02980v1`, submitted 3 July 2026;
- official code linked by the paper:
  <https://github.com/Tencent-Hunyuan/HiLS-Attention>;
- audited GitHub commit:
  `730105e1af294f098a04e33e5bee38578b79cabb` from 25 July 2026;
- task source ref: `source-item/9011e13c06d6/c6c4d227c167821c`.

The task-referenced raw snapshot
`raw/monitored\hugging-face-daily-papers\20260708-220900-hierarchical-sparse-attention-done-right-toward--c732d8fb.md`
is absent from this isolated worktree and is not used as evidence. Paper,
repository and model-card facts were checked against public, read-only primary
pages. No repository clone, model/dataset download or upstream code execution
was performed.

## What the paper contributes

HiLS factorizes sparse attention into chunk retrieval and attention within the
retrieved chunks. A learned landmark representation supplies a chunk score;
per-chunk attention outputs are fused using those scores. Because the retrieval
score affects the forward result, the language-modeling loss can train chunk
selection end to end.

The paper reports:

- small-scale 345M studies at an 8K training context;
- a 1.4B model trained from scratch for 300B tokens;
- continued pretraining of an OLMo3-style 7B model for about 50B tokens;
- retrieval extrapolation beyond the training context and long-context
  comparisons against full and other sparse-attention baselines;
- a 345M inference latency comparison on one NVIDIA H800, batch size 1 and
  BF16, reaching parity around 16K and larger reported gains at longer inputs.

These are upstream results, not Soll measurements. The paper does not prove
quality on mixed Soll documents, agent history, noisy multilingual text or
Android/server constraints.

## GitHub license audit

The official code repository was checked at the pinned commit through its
public GitHub repository and recursive-tree APIs:

| Check | Observed result |
| --- | --- |
| GitHub license metadata | `license: null` |
| Recursive tree receipt | `truncated: false`; `228` tracked entries |
| License-like files | no `LICENSE`, `LICENCE`, `COPYING` or `NOTICE` entry |
| Package metadata | `pyproject.toml` has no `license` field |
| README | no code-license grant observed |

Therefore no explicit permission to copy, modify or redistribute the GitHub
implementation was established. A public repository is not by itself a
software license. The Hugging Face `tencent/HiLS-Attention-7B` model card
separately declares Apache-2.0 metadata for the released checkpoint; that does
not grant a license for the GitHub source tree.

**License gate:** do not import or execute GitHub training code in Soll. A
future owner must pin a later upstream commit that contains an applicable
license, or obtain documented permission, then review code, model, dataset and
transitive dependency licenses separately.

## GPU and environment requirements

The released environment is not an Android or CPU experiment:

- Python is constrained to `>=3.11,<3.12` on Linux `x86_64`;
- the documented stack pins PyTorch `2.8.0` with CUDA `12.8`;
- CUDA runtime, cuDNN, cuBLAS, NCCL, Triton, FlashAttention 2/3 and TileLang
  dependencies are present;
- distributed training uses `torchrun`, NCCL, FSDP2 and all GPUs discovered
  through `CUDA_VISIBLE_DEVICES` or `nvidia-smi`;
- the 1.4B launcher explicitly selects
  `pretrain_1.4B_8K_300B_64gpu.yaml`, so that published path is a 64-GPU
  recipe;
- LongBench evaluation defaults to GPU ids `0 1 2 3 4 5 6 7`, one job per GPU;
- the single-H800 result is an inference benchmark for the 345M architecture,
  not a minimum hardware claim for training.

The main released recipes expose the following scale:

| Recipe | Sequence and batch | Training budget |
| --- | --- | --- |
| 345M from scratch | 8K; micro-batch 4; global batch 128 | 30K steps, about 30B tokens |
| 1.4B from scratch | 8K; micro-batch 4; global batch 256 | 143K steps, 300B tokens; 64-GPU recipe |
| OLMo3-style 7B CPT | 8K; micro-batch 4; global batch 512 | 13K steps, about 50B sampled tokens |

Neither the paper nor the audited README/recipes specifies the training GPU
model, per-GPU VRAM, node count for every run, wall-clock duration, energy or
cost. The 7B script also carries cluster-specific NCCL/RDMA interface settings.
Consequently local feasibility and a minimum GPU requirement cannot be
calculated from the release. No Soll machine should be represented as capable
until a separately reviewed memory estimate and tiny dry-run plan exist.

## Reproducibility audit

Useful released evidence:

- paper version and code commit can be pinned;
- environment packages and CUDA/PyTorch versions are substantially pinned;
- architecture configs, training launchers and evaluation scripts are public;
- the paper names OLMo/Dolma 3 corpora, a LongMino mixture and 5% synthesized
  RULER-style examples;
- checkpoint conversion and PPL, RULER, LongBench and OpenCompass entry points
  are documented.

Open reproducibility blockers:

1. there is no explicit GitHub code license;
2. training GPU model, VRAM, complete topology and runtime budget are absent;
3. recipes set `enable_full_determinism: false`;
4. training code consumes `args.train.seed`, but the audited top-level recipes
   do not pin an explicit seed value;
5. dataset revisions, complete manifests and content hashes are not pinned by
   the launch commands;
6. the 7B script defaults to a local tokenized 500B-token pool and
   cluster-specific paths/settings;
7. the paper reports selected aggregate results without released per-run
   variance for the main training claims;
8. the released 7B model needs the custom HiLS code path and cannot be treated
   as a standard `AutoModel` compatibility smoke.

## Safe reproduction checklist

A later proposal may proceed only after all of these are attached:

1. an explicit applicable code license at a pinned commit;
2. code/dependency review in a network-restricted isolated environment with no
   ambient credentials;
3. a public dataset or locally generated non-sensitive corpus with pinned
   revision, license, manifest, hashes and a documented privacy scan;
4. a tiny 345M-or-smaller smoke with fixed seeds, deterministic settings where
   supported, exact GPU model/VRAM, peak memory, duration and cost;
5. an unchanged full-attention baseline under the same data, seed, token
   budget and hardware;
6. held-out PPL and retrieval accuracy plus latency, peak memory and failure
   rate, with at least three seeds or an explicit variance limitation;
7. no private/user/production data, no model promotion and no Android/runtime
   integration;
8. manual review of receipts before any larger run.

Reject the experiment if licensing is unresolved, the corpus provenance cannot
be reproduced, the run requires private data, resource use is not bounded, or
HiLS fails to improve a predeclared quality/compute metric against the matched
baseline.

## Measured value of this task

- backlog research notes created: `1`;
- official GitHub commits and complete trees audited: `1` commit / `228`
  entries;
- explicit GitHub code licenses found: `0`;
- training-scale recipes audited: `3`;
- reproducibility blockers recorded: `8`;
- safe reproduction gates defined: `8`;
- upstream training/evaluation commands executed: `0`;
- model or dataset downloads: `0`;
- private/user/production records read: `0`;
- Android, server, dependency or runtime changes: `0`.

The present Soll value is a durable stop/go checklist for a strategically
interesting attention design. Runtime, quality and compute savings for Soll
remain unmeasured.
