---
task_id: 8bddd9e2a59a430799a4c8fbf2329e14
project: soll_app
source_ref: source-item/d0cd9479f2a2/d89412dfe05ae7da
source_item: llama-cpp-releases-b9934
source_processing_result: upstream_webgpu_benchmark_recorded_local_runtime_unchanged
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-d89412dfe05ae7da-verification.md
source_value: "1 WebGPU monitoring note added; 6 upstream benchmark rows, 2 WebGPU CI jobs and 5 current Soll seams audited; b10068 verified 134 commits ahead of b9934; 6 future A/B gates defined; 0 production/runtime changes and 0 local WebGPU inference runs"
verified_at: 2026-07-24 Europe/Chisinau
---

# llama.cpp b9934 WebGPU Flash Attention verification

## Outcome

PR #25418 has enough evidence for a monitoring-only Soll note: it publishes six
numeric WebGPU token-generation A/B rows for NVIDIA V100 and Apple M2 at 16K
context. The affected scenario and regression surface are documented in
`docs/knowledge/llama-cpp-b9934-webgpu-flash-attention-monitoring.md`.

No runtime rollout is justified. The active b10068 standalone baseline already
contains b9934 and is 134 commits newer, but its two active targets are CPU.
Soll Android has no WebGPU execution seam, does not package llama.cpp, and
continues to use `soll-backend-route`.

The task-referenced raw file is not vendored in this isolated worktree. Release
classification was reconstructed from the official release, commit and merged
PR without claiming unavailable raw-source details.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b9934` -> `32e41fa5b48e15b93c7a40ce677226b2e773c351`, released 2026-07-09 |
| Upstream scope | 2 `ggml-webgpu` files, `+16/-23` |
| Functional delta | replace hard-coded `VEC_NE` cases with head-dimension/subgroup-derived `D_SPLIT` in `flash_attn_vec` |
| Affected scenario | WebGPU Flash Attention token generation; no CUDA/Metal/Vulkan/OpenCL/CPU code changed |
| Upstream benchmark | 6 rows, V100 and M2, 16K context, 3 repeats; reported deltas `+0.6%` to `+36.1%` |
| Upstream CI | `gpu-webgpu-nvidia` and `gpu-webgpu-apple` succeeded on PR head |
| Regression status | no confirmed regression; unbenchmarked subgroup/head-dimension/context surface retained as a monitoring risk |
| Soll execution seam | 0 WebGPU/WGSL/`flash_attn_vec` runtime matches |
| Standalone baseline | b10068 is 134 commits ahead, 0 behind, with b9934 as merge base |
| Product change | none; monitoring note, audit artifact and focused test only |
| Runtime proof | 0 local WebGPU inference or benchmark runs |

## Scenario decision

Record the measured V100/M2 WebGPU cases and keep current defaults unchanged.
The largest reported speedup cannot be generalized beyond the tested
device/model/context combinations. M2 gemma4 `+0.6%` is inside its published
variability, while the other five rows are positive but still upstream-only.

A future Soll WebGPU trial must pin adapter/subgroup provenance, compare the
exact parent to a containing release, cover F16 equal and asymmetric head
dimensions, measure short and 16K prompt/generation workloads, verify
correctness/resources, and retain the backend route/CPU rollback.

## Focused smoke/audit artifact

`LlamaCppB9934WebGpuMonitoringTest` guards:

- exact task, source, release, commit, parent and PR identity;
- the missing-source boundary and exact WebGPU-only shader change;
- all six upstream benchmark rows and two successful WebGPU CI jobs;
- five current Soll seams, b10068 ancestry and six future A/B gates;
- the quantified source value and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9934WebGpuMonitoringTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `upstream_webgpu_benchmark_recorded_local_runtime_unchanged`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-d89412dfe05ae7da-verification.md`
- `source_value`: `1` WebGPU monitoring note; `6` upstream benchmark rows; `2`
  successful WebGPU CI jobs; `5` current Soll seams audited; b10068 verified
  `134` commits ahead of b9934; `6` future A/B gates; `0` production/runtime
  changes and `0` local WebGPU inference runs.
