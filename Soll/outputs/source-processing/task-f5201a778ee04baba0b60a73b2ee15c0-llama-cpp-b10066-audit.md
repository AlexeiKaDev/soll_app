---
task_id: f5201a778ee04baba0b60a73b2ee15c0
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/2e2a544eb69d
source_item: llama-cpp-releases-b10066
source_processing_result: implementation_analysis_completed_benchmark_deferred
verification_artifact: Soll/outputs/source-processing/task-f5201a778ee04baba0b60a73b2ee15c0-llama-cpp-b10066-audit.md
value_metric: "1 implementation analysis added; 3 official upstream surfaces and 5 current Soll seams audited; 6 benchmark gates defined; 0 production/runtime changes and 0 measured b10066 Soll inference value"
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b10066 implementation audit

## Outcome

The accessible b10066 task record was read and the implementation signal was
checked against the official release, exact commit and upstream PR. The durable
analysis is `docs/knowledge/llama-cpp-b10066-implementation-analysis.md`.

The requested `wiki/b10066.md` and monitored source artifact are not vendored
in this isolated repository. This is recorded explicitly rather than claiming
unavailable article details. Canonical upstream evidence is sufficient to
classify the implementation delta: b10066 is an OpenCL/Adreno MoE kernel-path
change, not a general Android/CPU upgrade.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b10066` -> `86a9c79f866799eb0e7e89c03578ccfbcc5d808e`, released 2026-07-17 |
| Upstream scope | one OpenCL C++ file, `+31/-3` |
| Functional delta | load/prefer Q6_K MoE binary kernel and correct q5_K dp4 selection guard |
| Soll execution seam | no llama.cpp JNI/CMake/NDK/OpenCL runtime in the Android app |
| Current default | checksummed b9895 manifest plus `soll-backend-route` |
| Product change | none; documentation/test/audit only |
| Runtime proof | `0` b10066 inference or device benchmark runs |

## Implementation decision

Keep b10066 as a deferred benchmark candidate. Do not replace the verified
b9895 manifest, add an Android native dependency, package binaries, or change
the backend route from this signal. The release can create measurable value
only for an approved Qualcomm/Adreno OpenCL workload using MoE Q6_K/q5_K.

The knowledge note defines six gates for that later benchmark: pinned device
and driver, applicable model/quantization, repeated performance/resource
measurements, correctness/fallback, a `10%` promotion threshold, and retained
b9895/backend rollback.

## Focused smoke/audit artifact

`LlamaCppB10066ImplementationAnalysisTest` guards:

- exact task, source, release and commit identity;
- the missing-wiki boundary and three canonical upstream links;
- the OpenCL/Adreno-only classification;
- five current Soll seams and six benchmark gates;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB10066ImplementationAnalysisTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `implementation_analysis_completed_benchmark_deferred`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-f5201a778ee04baba0b60a73b2ee15c0-llama-cpp-b10066-audit.md`
- `value_metric`: `1` analysis added; `3` official upstream surfaces and `5`
  current Soll seams audited; `6` benchmark gates defined; `0` production or
  runtime changes and `0` measured b10066 Soll inference value.
