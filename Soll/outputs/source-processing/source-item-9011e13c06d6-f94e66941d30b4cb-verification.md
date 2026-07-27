---
task_id: 28b03fff52b24ac8902b9f0c2e1673a1
project: soll_app
source_ref: source-item/9011e13c06d6/f94e66941d30b4cb
source_item: "TurnOPD: Making On-Policy Distillation Turn-Aware for Efficient Long-Horizon Agent Training"
source_processing_result: turnopd_formulas_extracted_offline_budget_audit_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-f94e66941d30b4cb-verification.md
source_value: "1 KB note with 2 budget controllers and their formula set; 2 existing synthetic non-production fixtures audited; 3 SAO rollouts/6 action turns and 1 AgentLens trace/6 events checked; exact turn loss allocation 50%/50%; diagnostic 80% success depth 2 rejected for controller refresh at 2/8 successful probes; 2/2 focused tests passed; 0 exact reverse-KL traces, training, user-data reads, model updates, agent executions, task mutations, deploys or runtime changes"
verified_at: 2026-07-27 Europe/Chisinau
---

# TurnOPD KB and offline turn-budget verification

## Outcome

The monitored source produced a bounded research and offline-audit package:

- formulas, controller semantics, reference settings, limitations and Soll
  decision: `docs/knowledge/turnopd-turn-aware-budget-offline-audit.md`;
- deterministic existing-fixture audit and contract:
  `TurnOpdOfflineBudgetAuditTest`.

No new trace was synthesized. The audit reuses the existing
`sao-soll-agent-offline-eval-v1.json` and
`agentlens-soll-ci-harness-v1.json` fixtures, both explicitly synthetic and
non-production.

## Primary-source and formula audit

| Check | Observed result |
| --- | --- |
| Primary identity | `arXiv:2607.05804v1`, submitted 2026-07-07 |
| Raw task snapshot | absent from isolated worktree; not used as evidence |
| Rollout controller | survivor-weighted mass, effective centroid, success-coverage quantile, max controller, EMA and zero-based-to-turn-count conversion documented |
| Loss controller | trajectory/token share, uniform turn share, progress schedule, linear blend and final weighted loss documented |
| Probe boundary | only uncensored full-depth probes may update controller statistics |
| KL boundary | sampled-token log-probability difference explicitly rejected as exact reverse-KL |
| Reference settings | `p=0.80`, minimum 8 success probes, `alpha_ema=0.30`, bounds `2/50`, warm-up 3, probe interval 8 separated from Soll defaults |

## Existing-trace audit

| Check | Expected | Observed result |
| --- | --- | --- |
| Fixture provenance | synthetic and non-production only | PASS: 2/2 |
| SAO shape | 3 rollouts, 2 action turns each | PASS: 3 rollouts / 6 turns |
| Survivor count | all traces reach both turns | PASS: `3 / 3` |
| Token allocation | compare trajectory and turn normalization | PASS: both `50% / 50%`; blend unchanged |
| Success completion | diagnostic 80% coverage depth | PASS: 2 successful rollouts, both depth 2 |
| Coverage guard | at least 8 successful full-depth probes | REJECTED: `2/8`, no probe marker |
| Exact `H_eff` input | full student/teacher reverse-KL per turn | REJECTED: `0` eligible traces |
| Readiness-only drift | never label sampled-token drift as reverse-KL | PASS: means `0.7067545121 / 0.9293643566`, proxy mass `43.197% / 56.803%` |
| AgentLens shape | inventory existing recovery trace | PASS: 1 trace / 6 events / 3 tool calls |
| AgentLens controller fields | explicit turns, token counts, KL and probe state | REJECTED: all absent |
| Promotion | no training/runtime controller from insufficient data | PASS: non-promotion pinned |

## Safety audit

- production or user trace reads: `0`;
- model/teacher calls, training, gradients or weight updates: `0`;
- agent/tool executions from fixtures: `0`;
- automatic task mutations, external writes or side effects: `0`;
- Android/runtime/API/dependency changes: `0`;
- commits, pushes and deploys: `0`.

The paper was read through public read-only pages. The focused evaluator itself
uses only repository files and has no network, credential or side-effect path.

## Focused test result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.TurnOpdOfflineBudgetAuditTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); `2/2 focused tests passed`
with `0` failures, `0` errors and `0` skipped.

The first test pins source/task traceability, both controllers, formulas,
probe/censoring guards, explicit non-training scope and all three required
value-metric keys. The second reads the two existing synthetic fixtures,
reproduces turn counts, loss shares, completion depth, sampled-token drift
diagnostics and missing-controller-input rejection.

## Value metric update

- `source_processing_result`:
  `turnopd_formulas_extracted_offline_budget_audit_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-f94e66941d30b4cb-verification.md`;
- `source_value`: one KB note with 2 budget controllers and their formula set;
  two existing synthetic non-production fixtures, 3 SAO rollouts/6 action
  turns and one AgentLens trace/6 events audited; exact turn loss allocation
  measured at `50% / 50%`; diagnostic 80% success depth `2` rejected for
  controller refresh at `2/8` successful probes; `2/2` focused tests passed.
  Exact reverse-KL traces, training, user-data reads, model updates, agent
  executions, task mutations, deploys and runtime changes: `0`.

The current Soll data is sufficient to validate turn accounting and expose
missing prerequisites, but not to reproduce TurnOPD or justify adoption.
