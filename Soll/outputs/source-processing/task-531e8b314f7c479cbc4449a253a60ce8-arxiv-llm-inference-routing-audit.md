---
task_id: 531e8b314f7c479cbc4449a253a60ce8
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/7499605e77a7
source_processing_result: six_arxiv_deep_dive_candidates_selected
verification_artifact: Soll/outputs/source-processing/task-531e8b314f7c479cbc4449a253a60ce8-arxiv-llm-inference-routing-audit.md
value_metric: "6 specific arXiv IDs selected; 6/6 primary arXiv records verified; 6 distinct inference/routing layers covered; 1/1 focused contract test passed; 0 papers imported, 0 inference benchmarks run and 0 production/runtime changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# arXiv LLM inference and routing deep-dive shortlist

## Outcome

Six specific papers were selected for separate deep dives. The shortlist keeps
one candidate per distinct decision layer instead of treating all uses of
"routing" as the same technique: provider/model selection, token-level
conditional compute, MoE temporal locality, distributed expert placement,
dLLM request scheduling, and sparse GPU kernels.

The task-named snapshot
`monitored/arxiv-cs-lg-recent/20260710-230904-machine-learning-27ae12cc.md`
is not vendored in this isolated worktree. Selection was therefore limited to
the official arXiv July 2026 `cs.LG` listing and the six linked primary arXiv
records. Titles and abstract-level claims below were verified there; no paper
PDF, repository, dependency, model, or benchmark workload was imported.

## Selected arXiv IDs (6)

1. **`arXiv:2607.09015` — Correlation-Aware Contextual Bandits with
   Surrogate Rewards for LLM Routing.**
   Primary record: <https://arxiv.org/abs/2607.09015>.
   This is the first deep dive because it directly addresses prompt-to-model
   routing under quality/cost trade-offs and noisy surrogate rewards. Extract
   the coupled and decoupled estimators, misspecification fallback, benchmark
   protocol, regret assumptions, and the minimum offline replay needed to
   compare them with Soll's deterministic server-side provider order.

2. **`arXiv:2607.08991` — Sensitivity-Aware Thresholding and Token Routing
   for Activation Sparsification in Large Language Models.**
   Primary record: <https://arxiv.org/abs/2607.08991>.
   This separates token-level conditional compute from provider routing.
   Extract the sensitivity proxy, calibration data, base/modified path
   contract, supported open-weight models, and end-to-end quality/throughput
   evidence; reject a Soll pilot if the result is only a kernel microbenchmark
   or requires an unowned model modification.

3. **`arXiv:2607.08780` — Sticky Routing: Training MoE Models for
   Memory-Efficient Inference.**
   Primary record: <https://arxiv.org/abs/2607.08780>.
   This covers training-time MoE temporal locality. The deep dive must verify
   the routing-consistency loss, the reported expert-switch reduction and
   perplexity trade-off, hardware/storage assumptions, and whether locality
   produces measured expert-cache hits and end-to-end latency rather than only
   a lower switch-rate proxy.

4. **`arXiv:2607.08782` — Director: Accelerating Distributed MoE Serving via
   Online Proactive Expert Placement.**
   Primary record: <https://arxiv.org/abs/2607.08782>.
   This covers cluster-level expert placement. Extract predictor inputs,
   quantized-replica requirements, migration scheduling, capacity constraints,
   failure behavior, evaluated topologies, and latency/migration overhead.
   Keep it research-only unless Soll has a concrete multi-GPU MoE workload.

5. **`arXiv:2607.08930` — BlockServe: Block-Grained Continuous Batching for
   High-Throughput Diffusion LLM Serving.**
   Primary record: <https://arxiv.org/abs/2607.08930>.
   This covers request scheduling for diffusion LLMs rather than autoregressive
   serving. Extract the block boundary, mixed-state execution and admission
   controller, then validate throughput, p95/p99 latency and generation quality
   on identical workloads. Do not generalize the reported dLLM gains to Soll's
   current backend route without a matching dLLM runtime.

6. **`arXiv:2607.08786` — Accelerating GPU Inference of Large Language Models
   with Moderately Unstructured Sparse Weight Matrices.**
   Primary record: <https://arxiv.org/abs/2607.08786>.
   This covers the kernel/hardware layer. Extract the three-layer sparse format,
   pruning and accuracy prerequisites, supported GPU generations, kernel versus
   end-to-end measurements, memory overhead and reproducibility assets. A
   future pilot is valid only on Soll-owned target hardware with a dense
   baseline and unchanged model-quality gates.

## Selection audit

| Decision layer | Selected ID | Why it is non-duplicative | First measurable deep-dive output |
| --- | --- | --- | --- |
| Provider/model policy | `2607.09015` | Routes whole requests among models | Offline quality-cost replay with noisy-surrogate fallback |
| Token conditional compute | `2607.08991` | Routes tokens between compute paths | Quality/throughput curve at matched actual sparsity |
| MoE training/locality | `2607.08780` | Changes expert transitions during training | Switch rate, cache hit rate, latency and perplexity |
| MoE cluster placement | `2607.08782` | Places/migrates experts across GPUs | E2E latency, predictor error and migration overhead |
| dLLM scheduling | `2607.08930` | Batches heterogeneous diffusion requests | Throughput, p95/p99 latency and output quality |
| Sparse GPU kernel | `2607.08786` | Changes weight representation and SpMM | Kernel and E2E speedup, memory and quality on target GPU |

Adjacent July `cs.LG` items were not used to pad the count. In particular,
`arXiv:2607.08940` is a time-series modality/model selector,
`arXiv:2607.08960` is a warehouse application architecture, and
`arXiv:2607.08961` concerns supervision ambiguity rather than LLM inference or
routing. They can be reconsidered only for a matching Soll use case.

## Soll boundary

- Android keeps the existing backend-mediated
  `SollGateway.askModelChat(...)` contract; the shortlist adds no client-side
  model router, provider key, native inference dependency, or model artifact.
- The roadmap's server-side deterministic provider order and cooldown/fallback
  remain the baseline. Paper results are hypotheses until replayed on a fixed,
  non-sensitive Soll workload with quality, cost, p95 latency, failures and
  fallback behavior recorded.
- `Sticky Routing` is a training intervention, `Director` assumes distributed
  MoE serving, `BlockServe` targets diffusion LLMs, and the sparse-kernel paper
  is GPU-specific. None is represented as a drop-in improvement.
- Deep dives should proceed in the listed order and stop when primary evidence,
  reproducibility, target-runtime fit, or measurable value is absent.

## Focused smoke/audit artifact

`ArxivLlmInferenceRoutingShortlistTest` checks the exact task/source trace,
the six-and-only-six selected IDs, official primary links, six distinct layers,
scope exclusions, the backend-mediated Android boundary, and the quantified
value metric.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.ArxivLlmInferenceRoutingShortlistTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- specific arXiv IDs selected: `6`;
- primary arXiv records verified: `6/6`;
- non-duplicative inference/routing decision layers covered: `6`;
- focused contract tests passed: `1/1`;
- paper imports, inference benchmarks and production/runtime changes: `0`.

The observed value is a source-traced, bounded deep-dive queue. Runtime value
remains unmeasured and is not represented as delivered.
