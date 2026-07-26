---
task_id: 7ded04fb5e1443e88deb663cf2a4e992
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/166483ea3430
source_item: llama-cpp-releases-b10068
source_processing_result: local_application_evaluated_no_current_dflash_workload
verification_artifact: Soll/outputs/source-processing/task-7ded04fb5e1443e88deb663cf2a4e992-llama-cpp-b10068-audit.md
value_metric: "1 local-application evaluation added; 4 official upstream surfaces and 5 current Soll seams audited; 6 benchmark gates defined; 0 current DFlash runtime/config matches, 0 production/runtime changes, and 0 local b10068 inference runs"
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b10068 local-application audit

## Outcome

The accessible b10068 task record was read and the release signal was checked
against the official release, exact commit, upstream PR and originating bug
report. The durable evaluation is
`docs/knowledge/llama-cpp-b10068-local-application-evaluation.md`.

The requested `wiki/b10068.md` and monitored source artifact are not vendored
in this isolated repository. This is recorded explicitly rather than claiming
unavailable article details. Canonical upstream evidence is sufficient to
classify b10068 as a DFlash quantized draft-K/V-cache correctness fix, not a
general local-inference or Android performance upgrade.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b10068` -> `571d0d540df04f25298d0e159e520d9fc62ed121`, released 2026-07-18 |
| Upstream scope | `src/models/dflash.cpp`, `+17/-0` |
| Functional delta | rotate injected K/V into DFlash's rotated cache space before cache copy, including iSWA |
| Upstream defect/result | quantized draft-cache acceptance was 0–2%; post-fix report was `0.97159` |
| Soll execution seam | 0 exact DFlash/cache-flag runtime or config matches and no llama.cpp JNI/CMake integration |
| Current default | checksummed b9895 manifest plus `soll-backend-route` |
| Product change | none; documentation/test/audit only |
| Runtime proof | `0` local b10068 inference or device/server benchmark runs |

## Local-application decision

Keep the verified b9895 manifest and backend route. Do not add an Android native
dependency, package b10068 binaries, or change runtime configuration from this
signal. There is no current DFlash workload in the repository, so the fix has
no present execution point and measured local runtime value is `0`.

If a separately approved local `llama-server` workload later combines DFlash
with quantized draft K/V cache, evaluate b10068 against its exact parent in a
disposable harness. The knowledge note defines six gates covering pinned
workload identity, exact DFlash/cache flags, repeated A/B plus f16/bf16 control,
performance/resource/correctness measures, an acceptance threshold, and
retained b9895/backend rollback.

## Focused smoke/audit artifact

`LlamaCppB10068LocalApplicationEvaluationTest` guards:

- exact task, source, release, commit and parent identity;
- the missing-wiki boundary and four canonical upstream links;
- the DFlash-only K/V rotation and upstream acceptance evidence;
- five current Soll seams and six benchmark gates;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB10068LocalApplicationEvaluationTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `local_application_evaluated_no_current_dflash_workload`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-7ded04fb5e1443e88deb663cf2a4e992-llama-cpp-b10068-audit.md`
- `value_metric`: `1` local-application evaluation added; `4` official
  upstream surfaces and `5` current Soll seams audited; `6` benchmark gates
  defined; `0` current DFlash runtime/config matches, `0` production/runtime
  changes, and `0` local b10068 inference runs.
