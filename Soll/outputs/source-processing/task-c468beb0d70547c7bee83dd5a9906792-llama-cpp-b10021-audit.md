---
task_id: c468beb0d70547c7bee83dd5a9906792
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/a817ab1e9a5c
source_item: llama-cpp-releases-b10021
source_processing_result: implementation_rejected_no_current_deepseek_v4_execution_seam
verification_artifact: Soll/outputs/source-processing/task-c468beb0d70547c7bee83dd5a9906792-llama-cpp-b10021-audit.md
value_metric: "1 wiki implementation decision added; 3 official upstream surfaces and 5 current Soll seams audited; b10068 verified 47 commits ahead of b10021; 6 benchmark gates defined; 0 production/runtime changes and 0 measured b10021 DeepSeek-V4 inference value"
verified_at: 2026-07-22 Europe/Chisinau
---

# llama.cpp b10021 implementation-decision audit

## Outcome

The implementation decision is recorded in `wiki/b10021.md`: reject a separate
b10021 rollout or Android implementation now. The release reduces DeepSeek-V4
graph splits, but Soll has no current DeepSeek-V4/native llama.cpp execution
seam and the checksummed b10068 verification baseline already contains the
change.

The requested wiki and monitored source were not vendored at task start. This
boundary is explicit; release classification was reconstructed from the
official b10021 release, exact commit and merged PR without claiming unavailable
source details.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b10021` -> `33a75f41c30052fd3d1c38e8ed2f86ee3c3f8fba`, released 2026-07-15 |
| Upstream scope | `src/models/deepseek4.cpp`, `+14/-12` |
| Functional delta | share KV/score row gathers across previous/current views; PR reports graph splits `5 -> 2` |
| Android seam | `SollApiService` uses `POST api/v1/chat/turn`; no direct DeepSeek-V4 runtime |
| Native seam | `0` project llama.cpp CMake/JNI/C++ integration points in `app/src/main` |
| Standalone baseline | b10068 is `47` commits ahead, `0` behind, with b10021 as merge base |
| Model prerequisite | deny-by-default allowlist has `0` approved models |
| Product change | none; wiki/test/audit only |
| Runtime proof | `0` DeepSeek-V4 inference or benchmark runs |

## Implementation decision

Keep `soll-backend-route`, Android API contracts, dependencies and the verified
b10068 manifest unchanged. A separate b10021 rollout has no measurable current
value because the only affected model is not an approved Soll workload and the
newer baseline already contains the commit.

If a DeepSeek-V4 workload is approved later, use the six benchmark gates in the
wiki. Promotion requires reproducible graph-split and latency evidence with
correctness/resource controls; the release claim alone is insufficient.

## Focused smoke/audit artifact

`LlamaCppB10021ImplementationDecisionTest` guards:

- exact task, source, release, commit, parent and PR identity;
- the missing-source boundary and precise DeepSeek-V4 graph change;
- five current Soll seams and b10068 ancestry evidence;
- six future benchmark gates and unchanged runtime policy;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB10021ImplementationDecisionTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `implementation_rejected_no_current_deepseek_v4_execution_seam`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-c468beb0d70547c7bee83dd5a9906792-llama-cpp-b10021-audit.md`
- `value_metric`: `1` wiki implementation decision added; `3` official upstream
  surfaces and `5` current Soll seams audited; b10068 verified `47` commits
  ahead of b10021; `6` benchmark gates defined; `0` production/runtime changes
  and `0` measured b10021 DeepSeek-V4 inference value.
