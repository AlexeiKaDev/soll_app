---
task_id: 3726a86c0edd415d8f36d3112b7d5d4f
project: soll_app
source_ref: source-item/9011e13c06d6/14bcf766ab5a4439
source_item: "SeKV: Resolution-Adaptive KV Cache with Hierarchical Semantic Memory for Long-Context LLM Inference"
source_processing_result: full_text_and_pinned_code_audited_prototype_contract_defined
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-14bcf766ab5a4439-verification.md
source_value: "18-page paper and complete TeX archive verified by SHA-256; 1 MIT upstream implementation pinned at commit 6569d111 with 32 blobs and 0 test files; 11 implementation modules and 4 current Soll seams audited; 8 reproduction gaps and 3 prototype phases/gates documented; 1/1 focused contract test passed; 0 model downloads, SeKV runs, dependency imports or production/runtime changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# SeKV full-text and implementation verification

## Outcome

The complete SeKV paper, appendices and source archive were retrieved from
primary arXiv surfaces. The official MIT implementation was audited at commit
`6569d111d3ace5c7c1ad596bf36962a99cd7e94b`. A focused Soll analysis now
captures the algorithm, training recipe, reported evidence, implementation
gaps, current integration boundary and an approval-gated prototype contract:

- `docs/knowledge/sekv-adaptive-kv-cache-soll-analysis.md`;
- `SeKvAdaptiveCacheSourceAnalysisTest`.

No SeKV package, model, dataset or paper archive was imported. The source is
accepted as a server-side research candidate, not as a production dependency
or a measured improvement to Soll.

## Retrieval receipt

| Check | Observed result |
| --- | --- |
| Task raw path | absent from isolated worktree; not used as evidence |
| arXiv identity | `2606.31145v1`, submitted 2026-06-30, CC BY 4.0 |
| Full PDF | 18 page objects; 1,041,615 bytes; SHA-256 `931835c45ac1ac579732ea0d11b14e01845d3d545e8b71fe6790f801c9ba0302` |
| Full TeX/e-print | 637,994 bytes; SHA-256 `aff648c2ae94828e07e979040adfa8a5a48c0b26b26341ea8ea37a086d52088f` |
| Paper coverage | method, experiments, training, benchmarks, ablations, complexity, limitations and zoom-in appendix checked |
| Official code | `AmirAbaskohi/SeKV`, commit `6569d111d3ace5c7c1ad596bf36962a99cd7e94b`, MIT |
| Code inventory | 32 blobs; 11 implementation modules inspected; 0 upstream test files |

## Implementation audit result

The pinned code contains the paper's central mechanisms: surprisal boundaries,
anchor retention, separate key/value SVD factors, routing projections, learned
thresholds and rank gates, mixed-resolution single-softmax attention, teacher
signals, four-part training loss and CUDA-stream factor prefetch.

Eight blockers prevent treating its defaults as a reproduction or a drop-in
Soll integration:

1. repository `l_min=4` differs from paper `L_min=16`;
2. registry Mistral v0.2 differs from paper v0.3 and is rejected when its
   configured sliding window is active;
3. default RULER/NIAH stop at 32K and RULER is a reduced local fixture, not the
   paper's official suite through 128K;
4. GSM8K defaults to zero-shot and only four optional examples exist, not the
   reported 50-shot setting;
5. absolute token budgets do not reproduce matched percentage budgets;
6. `device_map=auto` is not tensor parallelism and evaluator omits latency,
   throughput, TTFT and TPOT;
7. GPU factors retained through `SpanEntry` can outlive LRU eviction and are not
   included in token-budget enforcement;
8. broad dependency ranges, unpinned model/data revisions, no lockfile and
   `0` upstream tests leave the run under-specified.

## Soll boundary and prototype decision

Four current seams were audited. Android remains the backend-mediated client
through `SollGateway.askModelChat(...)`; active llama.cpp policy remains
`androidRuntimeDefault: soll-backend-route` and
`packageIntoAndroidApp: false`; there is no Android PyTorch/CUDA serving layer;
and the historical `qwen3-coder:30b` status lacks a reproducible launch plan in
this isolated repository.

The retained prototype target has three gated layers: synthetic tensor/static
correctness, one-model offline A/B, then promotion gates covering exact pins,
quality, total GPU/CPU/H2D memory, prefill and decode latency, throughput,
eviction, soak and rollback. It belongs beside the actual server inference
runtime, not in Android production code.

## Focused contract result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.SeKvAdaptiveCacheSourceAnalysisTest" --console=plain
```

Observed final result: `BUILD SUCCESSFUL`; `1/1 focused contract test passed`
with `0` failures, `0` errors and `0` skipped tests.

The contract checks task/source traceability, full-text digests and coverage,
pinned implementation identity/license, algorithm and training details, all
eight reproduction blockers, four live repository seams, prototype metrics and
zero-runtime claims.

## Value metric update

- `source_processing_result`:
  `full_text_and_pinned_code_audited_prototype_contract_defined`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-14bcf766ab5a4439-verification.md`;
- `source_value`: 18-page paper and complete TeX archive verified by SHA-256;
  one MIT implementation pinned at commit `6569d111` with 32 blobs and 0 test
  files; 11 implementation modules and 4 current Soll seams audited; 8
  reproduction gaps and 3 prototype phases/gates documented; 1/1 focused
  contract test passed. Model downloads, SeKV training/inference/benchmark
  runs, dependency imports and production/runtime changes: `0`.

The measurable value is a complete source receipt, code-level feasibility
assessment and falsifiable prototype gate. Runtime value remains unmeasured and
is not represented as delivered.
