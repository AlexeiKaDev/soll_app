---
task_id: a4ba4570c24d4b2aa3aeb5a686fcd817
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/1a90b7a917d6
source_item: llama-cpp-releases-b9978
source_processing_result: validated_relevant_checkpoint_regression_contract_no_current_runtime_rollout
verification_artifact: Soll/outputs/source-processing/task-a4ba4570c24d4b2aa3aeb5a686fcd817-llama-cpp-b9978-audit.md
value_metric: "1 wiki implementation decision added; 5 official upstream surfaces and 6 current Soll seams audited; b10068 verified 90 commits ahead of b9978; 6 future runtime smoke steps defined; 0 production/runtime changes and 0 local checkpoint workloads"
verified_at: 2026-07-26 Europe/Chisinau
---

# llama.cpp b9978 implementation-decision audit

## Outcome

The source signal is validated and relevant. `wiki/b9978.md` records b9978 as
a complementary regression contract to the existing b9936 min-step knowledge:
b9936 avoids unnecessary prompt-batch splits, while b9978 evicts dense
cross-task checkpoints and preserves checkpoints created near the end of the
current task.

No production implementation or standalone release change is warranted.
Android stays behind `POST api/v1/chat/turn`; the active b10068 baseline already
contains b9978; no approved model fixture executes the affected checkpoint
path.

The requested wiki and monitored source were absent from the Base SHA. The
classification was reconstructed from official release, commit, PR, issue and
compare surfaces without attributing unavailable monitored content.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b9978` -> `0c4fa7a989f94a9fef9e52a887e3376bb60d0848`, released 2026-07-12 |
| Upstream scope | `common/common.h`, `tools/server/server-context.cpp`, `+27/-1` |
| Functional delta | older-task checkpoints within min-step are evicted; current-task and near-prompt-end checkpoints are retained |
| Existing Soll relevance | complements the documented b9936 min-step prompt-batch regression contract |
| Android seam | `ChatTurnRequest` uses the Soll backend and exposes 0 checkpoint flags |
| Standalone baseline | b10068 is `90` commits ahead, `0` behind and contains b9978 as merge base |
| Runtime prerequisite | deny-by-default model gate has 0 approved long-context checkpoint workloads |
| Product change | none; wiki/test/audit only |
| Runtime proof | `0` local checkpoint workloads |

## Implementation decision

Adopt b9978 as a repository regression contract and keep runtime code,
dependencies, release defaults, model allowlist and Android API unchanged.
Reopen implementation only when an approved local server workload enables
context checkpoints and can execute the six model-backed gates in the wiki.

## Focused smoke/audit artifact

`LlamaCppB9978ImplementationDecisionTest` guards:

- exact task, source, release, commit, parent, PR and issue identity;
- the missing-source boundary and the b9978 checkpoint lifecycle semantics;
- the explicit relationship to the existing b9936 min-step knowledge;
- six current Soll seams, b10068 ancestry and unchanged Android/runtime policy;
- six future runtime gates and the quantified value metric.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9978ImplementationDecisionTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `validated_relevant_checkpoint_regression_contract_no_current_runtime_rollout`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-a4ba4570c24d4b2aa3aeb5a686fcd817-llama-cpp-b9978-audit.md`
- `value_metric`: `1` wiki implementation decision added; `5` official
  upstream surfaces and `6` current Soll seams audited; b10068 verified `90`
  commits ahead of b9978; `6` future runtime smoke steps defined; `0`
  production/runtime changes and `0` local checkpoint workloads.
