---
task_id: 5a30dadc1edb4ce8b7d0c28d256a5f93
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/5caf87d9fb56
source_item: llama-cpp-releases-b9982
source_processing_result: local_applicability_documented_no_current_reasoning_budget_execution_seam
verification_artifact: Soll/outputs/source-processing/task-5a30dadc1edb4ce8b7d0c28d256a5f93-llama-cpp-b9982-audit.md
value_metric: "1 wiki local-applicability evaluation added; 3 official upstream surfaces and 5 current Soll seams audited; b10068 verified 86 commits ahead of b9982; 6 smoke gates defined; 0 production/runtime changes and 0 local b9982 chat-completion requests"
verified_at: 2026-07-23 Europe/Chisinau
---

# llama.cpp b9982 local-applicability audit

## Outcome

Local applicability is determined in `wiki/b9982.md`: no Android or runtime
change is warranted now. b9982 makes canonical per-request
`reasoning_budget_tokens` and `reasoning_budget_message` override
`llama-server` defaults in OpenAI-compatible chat parsing.

The requested wiki and monitored source were not vendored at task start. The
release classification was reconstructed from the official b9982 release,
exact commit and merged PR without claiming unavailable source details.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b9982` -> `99f3dc32296f825fec94f202da1e9fede1e78cf9`, released 2026-07-13 |
| Upstream scope | `tools/server/server-common.cpp`, `tests/test-chat.cpp`, `+70/-2` |
| Functional delta | canonical token/message per-request overrides now reach `llama_params`; token alias/default fallback remains |
| Android seam | `ChatTurnRequest` has 0 reasoning-budget fields and uses `POST api/v1/chat/turn`, not direct llama-server chat completion |
| Standalone baseline | b10068 is `86` commits ahead, `0` behind and contains b9982 as merge base |
| Model prerequisite | deny-by-default tiny fixture has 0 approved b9982/reasoning-budget uses |
| Product change | none; wiki/test/audit only |
| Runtime proof | `0` local b9982 chat-completion requests |

## Applicability decision

Keep `soll-backend-route`, Android request DTO, dependencies and the
checksummed b10068 standalone baseline unchanged. The fix is already included
in that newer baseline, while this repository has no current adapter or
approved model workload that sends the affected canonical fields.

If an approved reasoning-capable local server is introduced later, apply the
six gates in the wiki to its server-side adapter. The adapter, not Android UI
or Retrofit, should validate product policy and map it to llama.cpp-specific
fields without cross-request state leakage.

## Focused smoke/audit artifact

`LlamaCppB9982LocalApplicabilityTest` guards:

- exact task, source, release, commit, parent and PR identity;
- the missing-source boundary and corrected field precedence;
- five current Soll seams and the b10068 ancestry result;
- six future smoke gates and unchanged Android/runtime policy;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9982LocalApplicabilityTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `local_applicability_documented_no_current_reasoning_budget_execution_seam`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-5a30dadc1edb4ce8b7d0c28d256a5f93-llama-cpp-b9982-audit.md`
- `value_metric`: `1` wiki local-applicability evaluation added; `3` official
  upstream surfaces and `5` current Soll seams audited; b10068 verified `86`
  commits ahead of b9982; `6` smoke gates defined; `0` production/runtime
  changes and `0` local b9982 chat-completion requests.
