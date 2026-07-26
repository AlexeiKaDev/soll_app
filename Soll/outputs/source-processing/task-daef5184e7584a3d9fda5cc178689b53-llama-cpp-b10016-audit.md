---
task_id: daef5184e7584a3d9fda5cc178689b53
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/6027b57155f5
source_item: llama-cpp-releases-b10016
source_processing_result: integration_defined_benchmark_deferred_no_current_sycl_battlemage_seam
verification_artifact: Soll/outputs/source-processing/task-daef5184e7584a3d9fda5cc178689b53-llama-cpp-b10016-audit.md
value_metric: "1 wiki integration decision added; 3 official upstream surfaces and 5 current Soll seams audited; b10068 verified 52 commits ahead of b10016; 6 benchmark gates defined; 0 current SYCL/Battlemage integration matches, 0 production/runtime changes, and 0 local b10016 inference runs"
verified_at: 2026-07-22 Europe/Chisinau
---

# llama.cpp b10016 integration-decision audit

## Outcome

The integration approach is defined in `wiki/b10016.md`: do not change the
Android app or current runtime. b10016 is a oneDNN Flash Attention prefill path
for Intel Battlemage/Xe2 SYCL. If Soll later gains an approved applicable host,
integrate it in the server-side inference layer behind `POST api/v1/chat/turn`
or in an isolated standalone hardware runner, not in APK/Retrofit code.

The requested wiki and monitored source were not vendored at task start. This
boundary is explicit; release classification was reconstructed from the
official b10016 release, exact commit and merged PR without claiming
unavailable source details.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b10016` -> `32b741c336decea914e4c1c24a9c9815485901b2`, released 2026-07-15 |
| Upstream scope | 5 `ggml/src/ggml-sycl` files, `+298/-1` |
| Functional delta | guarded oneDNN Graph SDPA for Battlemage/Xe2 F16 prefill with TILE fallback |
| Android seam | `SollApiService` uses `POST api/v1/chat/turn`; no direct SYCL runtime |
| Runtime policy | llama.cpp is not packaged; `soll-backend-route` remains default |
| Standalone baseline | b10068 is `52` commits ahead, `0` behind, with b10016 as merge base |
| Active targets | b10068 manifest contains two CPU targets and `0` SYCL targets |
| Model prerequisite | deny-by-default allowlist has `0` approved models |
| Current integration | `0` SYCL/Battlemage runtime configuration matches |
| Product change | none; wiki/test/audit only |
| Runtime proof | `0` local b10016 inference or Battlemage benchmark runs |

## Integration decision

Keep Android contracts, dependencies, Gradle, the CPU-only b10068 verification
manifest and `soll-backend-route` unchanged. The newer baseline already
contains the upstream commit, but the active release manifest deliberately has
no SYCL target and no applicable device/model workload exists in this
repository, so enabling or packaging the backend now would have no measurable
runtime value.

For a later approved Battlemage workload, add a checksummed SYCL build and a
focused runner adjacent to `Test-LlamaCppActiveRelease.ps1`. The six wiki gates
require pinned hardware/toolchain/model provenance, controlled
`GGML_SYCL_FA_ONEDNN=0/1` comparison, repeated prefill/TTFT measurements,
correctness and fallback checks, a `10%` promotion threshold, multi-device
synchronization coverage and retained backend/CPU rollback.

## Focused smoke/audit artifact

`LlamaCppB10016IntegrationDecisionTest` guards:

- exact task, source, release, commit, parent and PR identity;
- the missing-source boundary and Battlemage/Xe2-only classification;
- the exact future integration layer and unchanged Android contract;
- five current Soll seams, b10068 ancestry and six benchmark gates;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB10016IntegrationDecisionTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `integration_defined_benchmark_deferred_no_current_sycl_battlemage_seam`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-daef5184e7584a3d9fda5cc178689b53-llama-cpp-b10016-audit.md`
- `value_metric`: `1` wiki integration decision added; `3` official upstream
  surfaces and `5` current Soll seams audited; b10068 verified `52` commits
  ahead of b10016; `6` benchmark gates defined; `0` current SYCL/Battlemage
  integration matches, `0` production/runtime changes, and `0` local b10016
  inference runs.
