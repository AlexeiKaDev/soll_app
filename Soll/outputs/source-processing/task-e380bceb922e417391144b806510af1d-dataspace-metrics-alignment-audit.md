---
task_id: e380bceb922e417391144b806510af1d
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/d6cd7eaccde2
source_processing_result: metric_gap_mapped_current_suite_retained
verification_artifact: Soll/outputs/source-processing/task-e380bceb922e417391144b806510af1d-dataspace-metrics-alignment-audit.md
value_metric: "14 DataSpace metric and diagnostic directions mapped to 8 current Soll suite metrics; 0 direct equivalents, 4 partial alignments and 10 explicit gaps found; 8/8 current synthetic cases rechecked; 1/1 focused contract test passed; 0 dataset imports, 0 model runs and 0 production/runtime changes"
verified_at: 2026-08-09 Europe/Chisinau
---

# DataSpace metrics alignment with the current Soll eval contract

## Outcome

DataSpace is relevant as an evaluator-design signal, but it does not provide a
score that can be compared directly with the current Soll project metrics. The
paper evaluates complete tabular answers over heterogeneous workspaces; Soll's
current `soll-source-kb-eval-v1` suite evaluates source-monitoring and knowledge
base behavior over small inline synthetic JSON fixtures.

The current suite remains unchanged. Its `8` tasks, `2` agent families, `14`
skill tags, `25` per-case metric declarations, `10` comparator types and `8`
suite metrics were re-read from
`docs/knowledge/soll-source-monitoring-kb-eval-v1.json`. The alignment found
`0` direct metric equivalents, `4` partial alignments and `10` explicit gaps.
Merging the two contracts would imply false comparability, so a DataSpace-like
tabular track is deferred until Soll has a named tabular-analytics workload.

## Source verification

The monitored record
`monitored/hugging-face-daily-papers/20260808-003008-dataspace-benchmarking-data-agents-for-verifiabl-6bef8c30.md`
was inspected read-only. It contains the source identity and a minimal summary,
not enough detail to reproduce the evaluation contract. The claims used here
were therefore checked against the primary paper:

- <https://arxiv.org/abs/2608.03451>
- <https://arxiv.org/html/2608.03451>

The paper reports `410` tasks, `7,439` workspace artifacts, `15.01 GB` of data
and six carriers: CSV, JSON, SQLite, Markdown, PDF and video. Its official
metric is Task Accuracy: a task passes only when the complete predicted table
matches the reference after task-specific canonicalization, one-to-one column
alignment and ordered-row or unordered-row-multiset comparison. Missing or
invalid outputs, runtime failures and exhausted budgets count as incorrect.

The reported best Task Accuracy is `66.34%`. That figure is an upstream result,
not a Soll baseline: the current project has run `0` DataSpace tasks and owns no
equivalent heterogeneous tabular track.

## Current Soll baseline

The current project suite deliberately measures another workload family:

- `schema_valid_rate`, `task_success_rate`, `macro_metric_score` and
  `skill_coverage` measure deterministic structured-task completion;
- `citation_precision`, `citation_recall` and
  `hallucinated_reference_count` protect evidence quality;
- `unsafe_side_effect_count` protects the no-write/no-external-action boundary;
- promotion requires at least `7/8` tasks, all safety assertions and the three
  critical policy, abstention and review-only update cases.

Those provenance and safety metrics have no DataSpace replacement and remain
mandatory.

## Metric alignment

| DataSpace direction | Closest current Soll metric | Alignment | Result |
| --- | --- | --- | --- |
| Task Accuracy | `task_success_rate` | partial | both aggregate passed tasks, but their task-pass semantics differ |
| complete table schema and shape | `schema_valid_rate` | partial | structured JSON validation is not full row-by-column equivalence |
| header-invariant column alignment | none | gap | no semantic one-to-one column mapper exists |
| typed and precision-aware cells | `normalized_exact_match` | partial | one normalized scalar case does not cover typed columns, units or precision |
| ordered sequence / unordered row multiset | `sequence_exact_match`, `set_exact_match` | partial | current comparators do not compare whole rows or preserve duplicate multiplicity |
| token usage | none | gap | no efficiency telemetry in the current fixture |
| API cost | none | gap | no provider-cost metric in the offline fixture |
| tool action count | `unsafe_side_effect_count` | gap | unsafe actions and total actions are intentionally different measures |
| wall-clock latency | none | gap | no duration metric in the current fixture |
| cross-language accuracy | none | gap | no declared language slice |
| workspace-scale accuracy | none | gap | inline cases have no artifact-count or byte strata |
| carrier / multimodal accuracy | none | gap | no CSV, SQLite, Markdown, PDF or video routing case |
| join accuracy | none | gap | no relational-join case or label |
| failure-stage taxonomy | none | gap | failures are not split into intent, discovery, extraction, grounding, computation, materialization and termination |

The paper's efficiency diagnostics—token usage, API cost, tool actions and
wall-clock latency—are useful only when a real candidate runner exists. The
paper also shows why modality and join slices matter: multimodal tasks and joins
reduce accuracy consistently in its experiments. These are research findings,
not evidence that current Soll behavior improved.

## Integration decision

No Android, server runtime or existing suite changes are justified. If a
future Soll feature needs verifiable analytics across multiple files, create a
separate isolated desktop/server track with at least five synthetic or
non-sensitive cases. That track should add tabular Task Accuracy, schema
alignment, typed-cell equivalence, ordered/unordered row equivalence and
token/action/latency/cost diagnostics, stratified by language, workspace size,
carrier family and join requirement.

Promotion must require a named Soll workload, a pinned baseline, manually
reviewed gold, all safety assertions and `unsafe_side_effect_count = 0`. It must
not add an Android dependency or import the 15.01-GB upstream dataset by
default.

## Focused smoke/audit artifact

`DataSpaceCurrentMetricsAlignmentTest` guards:

- exact task, source and current suite identity;
- the live `8` tasks, `2` agent families, `14` tags, `25` per-case metrics,
  `10` comparator types and `8` suite metrics;
- all `14` unique comparison directions and the `0 / 4 / 10`
  direct/partial/gap split;
- preservation of Soll-specific provenance and safety metrics;
- deferred dataset/runtime integration and the quantified `value_metric`.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.DataSpaceCurrentMetricsAlignmentTest" --console=plain
```

Observed result on a clean `HEAD` snapshot with only this task's three files
applied: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0` failures, `0`
errors and `0` skipped tests. The main checkout remains independently blocked
by a pre-existing uncommitted syntax error in `GadgetServerSyncWorker.kt`; that
unrelated work was not changed for this task.

## Value metric update

- `source_processing_result`: `metric_gap_mapped_current_suite_retained`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-e380bceb922e417391144b806510af1d-dataspace-metrics-alignment-audit.md`
- `value_metric`: `14` DataSpace metric and diagnostic directions mapped to `8`
  current Soll suite metrics; `0` direct equivalents, `4` partial alignments
  and `10` explicit gaps found; `8/8` current synthetic cases rechecked; `1/1`
  focused contract test passed; `0` dataset imports, `0` model runs and `0`
  production/runtime changes.
