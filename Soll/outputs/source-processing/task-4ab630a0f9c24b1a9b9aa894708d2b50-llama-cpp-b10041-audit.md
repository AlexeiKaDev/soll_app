---
task_id: 4ab630a0f9c24b1a9b9aa894708d2b50
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/311051324150
source_item: llama-cpp-releases-b10041
source_processing_result: local_applicability_documented_no_current_cors_execution_seam
verification_artifact: Soll/outputs/source-processing/task-4ab630a0f9c24b1a9b9aa894708d2b50-llama-cpp-b10041-audit.md
value_metric: "1 wiki local-applicability evaluation added; 3 official upstream surfaces and 5 current Soll seams audited; b10068 verified 27 commits ahead of b10041; 6 smoke gates defined; 0 current direct llama-server HTTP/CORS config matches, 0 production/runtime changes, and 0 local b10041 HTTP requests"
verified_at: 2026-07-22 Europe/Chisinau
---

# llama.cpp b10041 local-applicability audit

## Outcome

Local applicability is determined in `wiki/b10041.md`: no Android or runtime
change is warranted now. b10041 suppresses one false warning when
`llama-server` uses localhost-only CORS and receives an empty or missing
`Origin`; it does not change the CORS allow policy, HTTP contract or inference
performance.

The requested wiki and monitored source were not vendored at task start. The
release classification was reconstructed from the official b10041 release,
exact commit and merged PR without claiming unavailable source details.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b10041` -> `8ee54c8b32a1b0cf13c03fc5723142bc62c775f6`, released 2026-07-16 |
| Upstream scope | `tools/server/server-http.cpp`, `+2/-2` |
| Functional delta | empty/missing `Origin` produces no blank CORS warning; localhost and non-localhost behavior remains unchanged |
| Android seam | `SollApiService` uses `POST api/v1/chat/turn`, not direct llama-server HTTP |
| Standalone baseline | b10068 is `27` commits ahead, `0` behind and contains the b10041 merge base |
| Current execution | `0` direct llama-server HTTP/CORS config matches; verification scripts use `--version` only |
| Model prerequisite | deny-by-default allowlist has `0` approved models |
| Product change | none; wiki/test/audit only |
| Runtime proof | `0` local b10041 HTTP requests |

## Applicability decision

Keep `soll-backend-route`, the Android API contract and the checksummed b10068
standalone baseline unchanged. The fix is already present in that newer
baseline and there is no current CORS execution seam, so a b10041 rollout or
production implementation would add no measurable value.

If an approved local llama-server HTTP workload is introduced later, use the
six gates in the wiki to verify warning count and unchanged CORS behavior. The
appropriate implementation surface is the server launch/adapter layer, not
Android UI, Retrofit or a new native dependency.

## Focused smoke/audit artifact

`LlamaCppB10041LocalApplicabilityTest` guards:

- exact task, source, release, commit, parent and PR identity;
- the missing-source boundary and precise empty-Origin behavior;
- five current Soll seams and the b10068 ancestry result;
- six future smoke gates and unchanged CORS security behavior;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB10041LocalApplicabilityTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `local_applicability_documented_no_current_cors_execution_seam`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-4ab630a0f9c24b1a9b9aa894708d2b50-llama-cpp-b10041-audit.md`
- `value_metric`: `1` wiki local-applicability evaluation added; `3` official
  upstream surfaces and `5` current Soll seams audited; b10068 verified `27`
  commits ahead of b10041; `6` smoke gates defined; `0` current direct
  llama-server HTTP/CORS config matches, `0` production/runtime changes, and
  `0` local b10041 HTTP requests.
