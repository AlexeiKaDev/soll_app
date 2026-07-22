---
task_id: 5921307b9b854acaa30401b438124927
project: soll_app
source_ref: source-item/9011e13c06d6/1a64df4ff985ceb0
source_item: "When Search Agents Should Ask: DiscoBench for Clarification-Aware Deep Search"
source_processing_result: discobench_triage_checklist_added_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-1a64df4ff985ceb0-verification.md
source_value: "1 short Soll triage checklist; 4/4 ambiguity types and 9 evaluation measures extracted; 1 SearchThenAsk decision rule grounded in 3 paper behavioral baselines; 1/1 focused contract test passed; 0 external agent runs and 0 production behavior changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# DiscoBench Soll triage checklist verification

## Outcome

The monitored paper signal produced one durable, proposal-only source-
monitoring checklist:

- `docs/knowledge/discobench-source-monitoring-triage-checklist.md`;
- `DiscoBenchSourceMonitoringTriageTest` as the focused repository contract.

The note contains all four paper ambiguity types, all nine reported evaluation
measures, a bounded SearchThenAsk rule, an abstention state and the minimum
audit fields needed to measure the rule later. It changes no production,
Android, API, scheduler, task-board or source-monitor runtime behavior.

## Evidence reviewed

- arXiv v2 HTML/PDF: `https://arxiv.org/html/2606.27669` and
  `https://arxiv.org/pdf/2606.27669`;
- Hugging Face paper record: `https://huggingface.co/papers/2606.27669`.

The task-referenced raw file
`raw/monitored\hugging-face-daily-papers\20260705-203016-when-search-agents-should-ask-discobench-for-cla-922a9d4e.md`
is not present in this isolated worktree. No upstream code, dataset, model,
search provider, credential or runtime was imported or executed.

## Extracted contract

| Requirement | Extracted value | Audit result |
| --- | --- | --- |
| Ambiguity taxonomy | Entity, Version, Criteria, Factual Inaccuracy | PASS: 4/4 |
| Task utility | end-to-end accuracy, checkpoint pass rate | PASS: 2/2 |
| Detection | detection accuracy, detection F1 | PASS: 2/2 |
| Interaction quality | CE-A, CE-B | PASS: 2/2 |
| Cost efficiency | average Ask turns, tool-use turns, token consumption | PASS: 3/3 |
| SearchThenAsk | bounded search, detect, targeted Ask, clue-constrained re-search | PASS |
| Unresolved ambiguity | `needs_clarification`; no guessing/unbounded search | PASS |
| Auditability | candidates/conflict, evidence, question/clue, outcome and cost fields | PASS |
| Runtime boundary | guidance/test/artifact only | PASS: 0 production changes |

The source's behavioral comparison is preserved as paper evidence, not a Soll
runtime claim: on the common subset SearchThenAsk averaged 93.4% checkpoint
pass rate, DirectGuess 56.5%, and SearchHeavyGuess 51.9%.

## Focused test result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.DiscoBenchSourceMonitoringTriageTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); focused contract result
`1/1` passed with `0` failures, `0` errors and `0` skipped. The successful run
followed an initial content-assertion failure caused by a Markdown line wrap;
the guard was narrowed to the complete semantic phrase and rerun successfully.

## Value metric update

- `source_processing_result`:
  `discobench_triage_checklist_added_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-1a64df4ff985ceb0-verification.md`;
- `source_value`: one short checklist with 4/4 ambiguity types, nine named
  evaluation measures, one auditable SearchThenAsk decision rule and `1/1`
  passing focused contract test. External agent/search runs and production
  behavior changes: `0`. Measured runtime model-quality improvement: `0`
  pending a separately approved evaluation.
