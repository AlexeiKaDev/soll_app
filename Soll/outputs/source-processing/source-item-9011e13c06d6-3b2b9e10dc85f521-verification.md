---
task_id: 2ae012807f2c4dc3beb74b08eadae655
project: soll_app
source_ref: source-item/9011e13c06d6/3b2b9e10dc85f521
source_item: AgenticDataBench - A Comprehensive Benchmark for Data Agents
source_processing_result: minimal_eval_template_defined_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-3b2b9e10dc85f521-verification.md
source_value: "1 machine-readable Soll eval template; 8 synthetic eval cases across 2 agent families; 14 controlled skill tags; 8 suite metrics; 1/1 focused contract test passed; 0 external agent runs and 0 production changes"
verified_at: 2026-07-19 Europe/Chisinau
---

# AgenticDataBench minimal Soll eval-template verification

## Outcome

The monitored source signal produced a minimal, measurable internal evaluation
template for Soll source-monitoring and knowledge-base agents:

- design and upstream mapping:
  `docs/knowledge/soll-source-monitoring-kb-eval-template.md`;
- machine-readable suite:
  `docs/knowledge/soll-source-monitoring-kb-eval-v1.json`;
- focused repository contract:
  `AgenticDataBenchSollEvalTemplateTest`.

The suite contains 8 synthetic eval cases, 14 controlled skill tags, exact
expected outputs, per-case success metrics, 8 suite metrics and safety gates.
No production agent, provider harness or external integration was executed.

## Evidence reviewed

- Hugging Face paper record: `https://huggingface.co/papers/2607.01647`;
- GitHub repository: `https://github.com/AgenticDataBench/AgenticDataBench`;
- public testbed seams: `testbed/tasks/dev.jsonl`, `testbed/gold/`,
  `testbed/evaluate.py`, `testbed/run.py`, provider run scripts and
  `skill_cluster/data/skill-descriptions.jsonl`.

The task-referenced raw file
`raw/monitored\hugging-face-daily-papers\20260705-203016-agenticdatabench-a-comprehensive-benchmark-for-d-2763da91.md`
is absent from this isolated worktree. Public pages were used read-only; no
upstream dataset, gold artifact, runner, dependency or credential file was
copied into Soll.

## Focused smoke/audit checks

| Check | Expected | Observed result |
| --- | --- | --- |
| Suite identity | versioned `soll-source-kb-eval-v1` with task/source trace | PASS |
| Safe data boundary | synthetic, non-personal, credential-free fixtures only | PASS |
| Task count | between 5 and 10 | PASS: 8 |
| Agent coverage | source-monitoring and knowledge-base | PASS: 2 families |
| Skill labels | declared taxonomy fully exercised by cases | PASS: 14/14 |
| Gold contract | every case has a structured expected output | PASS: 8/8 |
| Task scoring | every case has explicit metric targets | PASS: 8/8 |
| Suite scoring | measurable aggregate and promotion gate | PASS: 8 metrics; 7/8 minimum plus 3 critical cases |
| Safety scoring | all cases guard against side effects | PASS: 8/8 |
| Upstream scope | testbed pattern adapted without data/provider import | PASS |
| Runtime claims | no unexecuted model benchmark represented as delivered | PASS: 0 external agent runs |
| Focused contract | JSON structure, coverage, docs and value fields guarded | PASS: 1/1 |

## Focused test result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.AgenticDataBenchSollEvalTemplateTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); focused contract result
`1/1` passed with `0` failures, `0` errors and `0` skipped.

The test parses the JSON suite, verifies all 8 task ids, both agent families,
14/14 skill coverage, required input/gold/metric/safety fields, the eight suite
metrics, critical promotion cases, source-testbed mapping and the required
value-metric fields in this artifact.

## Value metric update

- `source_processing_result`: `minimal_eval_template_defined_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-3b2b9e10dc85f521-verification.md`;
- `source_value`: one machine-readable template with 8 synthetic eval cases,
  14 controlled skill tags, exact expected outputs, 8 suite metrics and one
  passing focused contract test. External agent evaluations, production data
  imports, persistent KB/task writes and production behavior changes: `0`.

The measurable value is the reusable eval contract and its auditability.
Model-quality value remains unmeasured until a separately approved isolated
runner executes the fixture.
