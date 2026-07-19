---
task_id: aa8dfba5f72342bcb30624ed9b529173
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/2b0ac2f1734b
source_processing_result: requirements_analysis_completed_runtime_pilot_deferred
verification_artifact: Soll/outputs/source-processing/task-aa8dfba5f72342bcb30624ed9b529173-vllm-transformers-audit.md
value_metric: "1 requirements analysis added; 6 primary upstream surfaces and 5 current Soll seams audited; 5 promotion gates defined; 1 source-title mismatch resolved; 0 production/runtime changes and 0 measured Soll vLLM benchmark value"
verified_at: 2026-07-19 Europe/Chisinau
---

# vLLM Transformers backend requirements audit

## Outcome

The full canonical **Native-speed vLLM transformers modeling backend** post was
opened and checked against the vLLM PR, stable release, package metadata and
current vLLM/Transformers documentation. The complete Soll-specific analysis is
`docs/knowledge/vllm-transformers-backend-soll-app-analysis.md`.

The monitored source artifact is not vendored in this isolated worktree. The
task evidence title points to a separate NeMo Automodel/Diffusers training post;
the source filename, objective and safe action consistently identify the vLLM
post. The analysis records this mismatch and does not mix diffusion-training
requirements into the inference decision.

## Focused audit

| Check | Observed result |
| --- | --- |
| Feature identity | vLLM PR `#47187`, shipped in `0.25.0`; current patch release `0.25.1` |
| Core requirement | `--model-impl transformers` plus a compatible Transformers model implementation |
| Model boundary | encoder/decoder/MoE with full or sliding attention; post excludes linear attention |
| Published benchmark boundary | three Qwen3 configurations on H100; no `qwen3-coder:30b` Soll run |
| Current Soll evidence | historical endpoint alias only; exact checkpoint, versions, launch plan and hardware absent here |
| Android impact | none; existing `SollGateway` server contract remains the integration boundary |
| Runtime proof | `0` native-vs-Transformers Soll benchmark runs and `0` production/runtime changes |

## Applicability decision

Treat the backend as an approval-gated server benchmark candidate, not an
automatic local-inference optimization. A stable pilot must pin
`vllm==0.25.1`, preserve the resolved Transformers/PyTorch/accelerator stack,
use the exact current model and flags, prove the selected backend and applied
fusions in logs, and compare native vs Transformers on identical workloads.

Promotion requires five gates: correctness, at least 95% native throughput,
p95 latency and peak GPU memory within 5% of native, a concrete
maintenance/model-availability benefit, and a proven native rollback. Parity by
itself is not enough to switch an already supported Qwen model.

## Focused smoke/audit artifact

`VllmTransformersBackendAnalysisTest` guards:

- exact task/source trace and source-title mismatch boundary;
- six primary upstream surfaces and the stable `0.25.1` package requirement;
- model, package, security and runtime-proof requirements;
- five current Soll seams and five promotion gates;
- quantified value metric, `0` benchmark runs and `0` production/runtime
  changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.VllmTransformersBackendAnalysisTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `requirements_analysis_completed_runtime_pilot_deferred`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-aa8dfba5f72342bcb30624ed9b529173-vllm-transformers-audit.md`
- `value_metric`: `1` requirements analysis added; `6` primary upstream
  surfaces and `5` current Soll seams audited; `5` promotion gates defined; `1`
  source-title mismatch resolved; `0` production/runtime changes and `0`
  measured Soll vLLM benchmark value.
