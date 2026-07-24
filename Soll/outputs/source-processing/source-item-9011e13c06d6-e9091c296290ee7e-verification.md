---
task_id: a0ea55d17b354cdeaf385ab485645435
project: soll_app
source_ref: source-item/9011e13c06d6/e9091c296290ee7e
source_item: Single-Rollout Asynchronous Optimization for Agentic Reinforcement Learning
source_processing_result: sao_deep_dive_offline_eval_prototype_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-e9091c296290ee7e-verification.md
source_value: "1 SAO deep-dive note; 8 algorithm stages and 6 value-model requirement groups documented; 1 synthetic offline-eval fixture with 3 single-rollout prompts, 6 action tokens and 3 observation tokens; 4/6 action tokens retained and 2/6 masked under DIS; 2/2 focused tests passed; 0 model training, production model updates, agent executions, external actions or runtime changes"
verified_at: 2026-07-24 Europe/Chisinau
---

# SAO deep-dive and offline-eval verification

## Outcome

The Hugging Face Daily Papers signal produced a bounded SAO research and
offline-evaluation package:

- algorithm, value-model, clipping, evidence and limitation review:
  `docs/knowledge/sao-single-rollout-offline-eval.md`;
- synthetic, machine-readable single-rollout fixture:
  `docs/knowledge/sao-soll-agent-offline-eval-v1.json`;
- deterministic test-scope evaluator and contract:
  `SaoOfflineEvalPrototypeTest`.

The evaluator only audits synthetic token metadata. It does not train or call a
model, calculate gradients, read production task history, execute an agent or
tool, mutate task priority, or contact an external system.

## Primary-source receipt

| Check | Observed result |
| --- | --- |
| Task raw path | absent from isolated worktree; not used as evidence |
| Hugging Face item | `https://huggingface.co/papers/2607.07508` resolved read-only to the paper content |
| arXiv identity | `2607.07508v1`, submitted 2026-07-08, CC BY 4.0 |
| Full text | method, experiments, ablations, additional results, limitations and broader impact reviewed |
| PDF | `%PDF-1.7`; 664,828 bytes; SHA-256 `44c695be0428c666d06c914ba76c037e3ac77eeb5db0a81bbe239719c21bda48` |
| External writes | `0`; no login, repository clone, model/data download or external mutation |

## Deep-dive audit

| Required area | Observed result |
| --- | --- |
| SAO algorithm | 8 ordered stages from asynchronous single-rollout collection through critic and masked actor updates |
| DIS formula | current-vs-rollout token log-probability ratio captured |
| Strict clipping | open interval, boundary masking, zero-not-clamp semantics and difference from PPO recorded |
| Reported thresholds | reasoning `(0.7, 6.0)` and coding `(0.2, 4.0)` separated from Soll defaults |
| Value cold start | scaled value pretraining and missing reproducibility detail recorded |
| Critic schedule | `K>1`, reported `K=2`, and one-update ablation captured |
| Frozen Attention | MoE-specific intervention recorded without generalizing it to all architectures |
| Agent traces | token-level values and skip-observation GAE equations documented |
| Diagnostics | value MAE, explained variance, gradient norm, missing data and task-family slices required |
| Limitations | upstream training claims separated from Soll value; transfer and online-learning limits retained |

## Focused offline smoke

The versioned fixture is synthetic and contains no personal data, credentials,
production history, model output, or executable command. It has exactly one
rollout for each of three prompt ids.

| Check | Expected | Observed result |
| --- | --- | --- |
| Prompt/rollout shape | one rollout per prompt | PASS: `3/3`, no duplicate prompt |
| Token split | action and observation spans remain distinct | PASS: 6 action, 3 observation |
| DIS reasoning interval | strict `(0.7, 6.0)` | PASS |
| Boundary behavior | exact lower and upper boundary masked | PASS: `2/2` |
| Synthetic token result | ratios inside retained; outside masked | PASS: 4/6 retained, 2/6 masked |
| Observation handling | excluded from log-probability and value metrics | PASS: 3/3 excluded |
| Action bridges | next action found across observation span | PASS: 3 |
| Critic diagnostics | deterministic finite MAE and explained variance | PASS: `0.3`, `0.555` |
| Safety gate | no training, weights, task mutation, agent/tools or external actions | PASS: all disabled |
| Production delta | runtime/API/UI/dependencies unchanged | PASS: 0 files |

## Focused test result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.SaoOfflineEvalPrototypeTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); `2/2 focused tests passed`
with `0` failures, `0` errors and `0` skipped tests.

The first test pins task/source traceability, the paper formulas, all six
value-model requirement groups, architecture-specific caveats, data policy,
non-production gate, and the three required value-metric keys. The second
executes the test-only evaluator, checks one rollout per prompt, reconstructs
six token ratios, proves strict boundary masking, excludes all observations,
and reproduces the fixture's mask and critic diagnostic summary.

## Value metric update

- `source_processing_result`:
  `sao_deep_dive_offline_eval_prototype_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-e9091c296290ee7e-verification.md`;
- `source_value`: one SAO deep-dive note with 8 algorithm stages and 6
  value-model requirement groups; one synthetic offline fixture with 3
  single-rollout prompts, 6 action tokens and 3 observations; 4/6 action tokens
  retained and 2/6 masked; 2/2 focused tests passed. Model training, production
  model updates, production task reads, agent/tool executions, external
  actions and runtime changes: `0`.

The measurable Soll value is a source-traceable, executable offline audit
contract and explicit rejection boundary. Real critic quality, training
stability, benchmark improvement and production value remain unmeasured and
are not represented as delivered.
