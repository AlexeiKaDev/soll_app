---
task_id: 747be3f0cb7e4a079ab504cdb4de20a4
project: soll_app
source_ref: source-item/47425428d2cf/361bfe3d58165f4f
source_processing_result: knowledge_note_added_comparability_guard_recorded
verification_artifact: Soll/outputs/source-processing/source-item-47425428d2cf-361bfe3d58165f4f-verification.md
source_value: "1 KB note, 4 task-family comparison guards, 8 provenance groups, and 1 bounded Windows ML smoke contract recorded; 1/1 focused contract test passed; 0 harness/backend runs, network calls, security tests, Android runtime changes, or production writes"
verified_at: 2026-07-23 Europe/Chisinau
---

# lm-evaluation-harness v0.4.11 knowledge-note audit

## Outcome

The requested versioning and result-comparability note is attached at
`docs/knowledge/lm-evaluation-harness-v0-4-11-result-comparability.md`.
It requires explicit harness and task versions and prevents direct v0.4.10 to
v0.4.11 score deltas for `afrobench_belebele`, `evalita_llm`, `include` and
`mgsm_direct`.

The note also contains a bounded, optional local Windows ML smoke contract.
No harness, backend, model or dataset was installed or executed. No network,
credential, security-test, Android runtime or production surface was touched.

## Evidence and trust boundary

- Task source reference:
  `source-item/47425428d2cf/361bfe3d58165f4f`.
- Release pointer:
  <https://github.com/EleutherAI/lm-evaluation-harness/releases/tag/v0.4.11>.
- Monitored capture identifier:
  `raw/monitored\lm-evaluation-harness-releases\20260709-235808-v0-4-11-daca52a1.md`.

The monitored capture is absent from this isolated worktree, so no claim in the
untrusted task payload was treated as executable instruction or independently
verified release behavior. The durable result is a conservative provenance and
comparison policy, not a certification of the upstream backend or benchmark
implementation.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Knowledge attachment | durable note under `docs/knowledge` | PASS |
| Version provenance | harness and effective task versions are mandatory | PASS |
| Changed-task boundary | all 4 named task families reject direct v0.4.10 comparison | PASS |
| Comparable baseline | baseline reruns inside the pinned v0.4.11 environment | PASS |
| Missing task version | unresolved versions fail closed for cross-release claims | PASS |
| Windows ML boundary | local Windows, tiny non-sensitive fixture, network disabled | PASS |
| Excluded work | no downloads, credentials, security tests or Android integration | PASS |
| Measurable value | 1 note, 4 guards, 8 provenance groups and 1 smoke contract | PASS |
| Focused contract test | `LmEvaluationHarness0411KnowledgeTest` | PASS |

## Value metric update

- `source_processing_result`:
  `knowledge_note_added_comparability_guard_recorded`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-47425428d2cf-361bfe3d58165f4f-verification.md`
- `source_value`: `1` KB note records comparison policy for `4` changed task
  families, `8` required provenance groups and `1` bounded Windows ML smoke
  contract; `1/1` focused contract test passed. Harness/backend runs, network
  calls, security tests, Android runtime changes and production writes remain
  `0`.

## Test evidence

- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.soll.project.LmEvaluationHarness0411KnowledgeTest" --console=plain`
- Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed.
