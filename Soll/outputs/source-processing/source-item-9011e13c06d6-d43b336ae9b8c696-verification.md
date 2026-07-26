---
task_id: 9ee7bcc7152c404688d51155fd980765
project: soll_app
source_ref: source-item/9011e13c06d6/d43b336ae9b8c696
source_item: AgentLens - Production-Assessed Trajectory Reviews for Coding Agent Evaluation
source_processing_result: agentlens_deep_dive_ci_only_harness_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-d43b336ae9b8c696-verification.md
source_value: "9 upstream judge metrics and 4 dump layers extracted; 1 synthetic recovery trajectory and 8 deterministic CI metrics defined; 1/1 focused contract test passed; 0 external agent runs, 0 secret reads, and 0 repository auto-runs"
verified_at: 2026-07-19 Europe/Chisinau
---

# AgentLens deep dive and Soll CI-only harness verification

## Outcome

The monitored source signal produced a focused AgentLens deep dive and a safe,
repository-native evaluation contract:

- upstream metrics, dump anatomy and risk analysis:
  `docs/knowledge/agentlens-soll-evaluation-harness.md`;
- machine-readable policy, normalized dump and synthetic smoke trajectory:
  `docs/knowledge/agentlens-soll-ci-harness-v1.json`;
- deterministic CI-only evaluator:
  `AgentLensSollEvaluationHarnessTest`.

The contract extracts 9 upstream judge metrics and the four AgentLens dump
layers: root summary, simulated-user calls, agent chat and tool-call telemetry.
It defines 8 deterministic CI metrics over one synthetic trajectory with an
explicit failed-test/recovery pair. It disables agent execution, repository
discovery/mutation, network, secrets and LLM judging.

## Safety decision

The full AgentLens collector/CI pipeline was not imported or run. It needs
provider and repository secrets and includes checkout/hard-reset behavior on
configured task repositories. The Soll slice adds no workflow trigger and no
foreign-repository path. It only validates an embedded synthetic fixture when
the focused JVM test is explicitly invoked.

External agent/provider runs: `0`. Foreign repositories opened, cloned,
checked out or reset: `0`. Harness network calls, secret reads, external writes
and repository mutations: `0`.

## Focused smoke/audit checks

| Check | Expected | Observed result |
| --- | --- | --- |
| Metric extraction | 9 distinct judge metrics plus formal/telemetry inventory | PASS |
| Dump extraction | summary, simulator, chat and tool-call layers | PASS |
| Repository boundary | explicit synthetic manifest; foreign repository rejected | PASS |
| Execution boundary | no agent, checkout, tool command or auto-discovery | PASS |
| Secret/network boundary | secret access forbidden; network and LLM judge disabled | PASS |
| Redaction | raw reasoning and raw tool output forbidden | PASS |
| Recovery smoke | failed tool event linked to successful bounded retry | PASS: 1/1 recovered |
| Deterministic scoring | 8 named metrics match the expected synthetic result | PASS: 8/8 |
| Promotion gate | requirements/formal/recovery/evidence complete; unsafe effects zero | PASS |
| Focused contract | JSON, docs, value fields and smoke calculations guarded | PASS: 1/1 |

## Focused test result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.AgentLensSollEvaluationHarnessTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); focused contract result
`1/1` passed with `0` failures, `0` errors and `0` skipped. The successful rerun
followed one initial smoke failure that exposed and corrected a test-only field
mapping (`tool_call.success` versus formal-check `passed`).

## Value metric update

- `source_processing_result`:
  `agentlens_deep_dive_ci_only_harness_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-d43b336ae9b8c696-verification.md`;
- `source_value`: 9 upstream judge metrics and 4 dump layers were extracted;
  one versioned safe contract, one synthetic recovery trajectory and 8
  deterministic CI metrics were defined; `1/1` focused contract test passed.
  External agent/model evaluations and production behavior changes: `0`.
