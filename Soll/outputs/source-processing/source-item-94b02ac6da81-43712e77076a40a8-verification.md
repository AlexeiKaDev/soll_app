---
task_id: 23f03e5cbcb74f3f860623c652a8a064
source_ref: source-item/94b02ac6da81/43712e77076a40a8
source_item: habr-sber-olap-semantic-layer-excel-860b82fa
source_processing_result: bi_knowledge_added_semantic_layer_deferred_no_financial_reports
verification_artifact: Soll/outputs/source-processing/source-item-94b02ac6da81-43712e77076a40a8-verification.md
source_value: "1 BI knowledge note added; 6 adoption gates defined; 0 current financial reports and 0 measured report-automation value in soll_app"
verified_at: 2026-07-15 Europe/Chisinau
---

# Habr Sber BI semantic-layer source audit

## Outcome

The article signal was added to the durable BI knowledge base at
`docs/knowledge/bi-semantic-layer-financial-reporting.md`. The note records the
OLAP -> semantic layer -> live web/Excel/LibreOffice pattern, defines the
semantic metric contract, and provides a six-gate pilot and measurement plan.

No OLAP, Excel parser, financial schema, BI SDK, endpoint or Android UI was
added. The repository audit found no current financial report to migrate, so
implementation is deferred rather than claiming value against a nonexistent
workflow.

## Source limitation

The task-referenced raw file
`raw/monitored\habr-sber-company\20260702-194414-olap--excel-860b82fa.md`
is not present at the repository root or under `Soll/raw` in this isolated
worktree. The supplied URL is the author's Habr profile, not a canonical
article URL. The knowledge note therefore uses only the task's title and stated
benefit and explicitly avoids unverified article-specific stack, result, and
benchmark claims.

## Repository audit evidence

- `app/build.gradle.kts` defines a Kotlin/Compose Android application with
  Room, Retrofit/Moshi, media and on-device ML dependencies; it has no OLAP,
  cube, BI, spreadsheet-query or financial-reporting dependency.
- `SollApiService` exposes task, chat, source, book, mesh and gadget contracts,
  but no finance, ledger, plan/actual, P&L or report-model endpoint.
- Room entities cover tasks, sync/tool jobs, notes, events, devices, books and
  media rather than financial facts/dimensions.
- `FilesHandler` recognizes `xls`/`xlsx` only to label a file as
  `Excel-таблица`; it does not parse or connect that file to a model.
- `DeviceQaReportFormatter` creates a diagnostic Markdown report, not a
  financial report.
- Focused repository search found no production financial model or semantic
  layer to migrate.

## Knowledge artifact coverage

The knowledge note now preserves:

1. the governed ingestion -> OLAP model -> semantic model -> live client
   separation;
2. ownership of measures, grain, dimensions, time/version, relationships,
   access and observability;
3. a reusable metric-passport template;
4. an evidence table for the current `soll_app` applicability decision;
5. safe placement of any later pilot in the report-owning backend/BI system;
6. six adoption gates and baseline/pilot metrics.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| BI knowledge note exists | durable note under `docs/knowledge` | PASS |
| Source limitation is explicit | no invented full-article details | PASS |
| Semantic-layer responsibilities exist | measure through observability contract | PASS |
| Current repo is audited | API, Room, Gradle, Excel label and QA report | PASS |
| Pilot is bounded | 6 gates with owner/report/baseline requirements | PASS |
| Android production behavior is unchanged | documentation/test/artifact only | PASS |
| Value metrics are attached | result, artifact path and quantified value | PASS |

The focused unit audit `HabrSberBiSemanticLayerSourceTriageTest` guards the
knowledge-base entry, the defer decision, all six pilot gates and the three
required value-metric fields.

## Promotion decision

Keep semantic-layer implementation **deferred in `soll_app`**. A separately
approved task may evaluate one real financial report only in the repository
that owns its data and business rules. It must first identify the report owner,
capture a manual-time/error baseline, publish metric passports, reconcile a
read-only prototype, test one live client, and demonstrate accepted improvement
over two consecutive reporting cycles with rollback retained.

Android may later consume a permission-filtered aggregate/status/approval
contract. It must not own accounting data, warehouse credentials or duplicate
financial formulas locally.

## Value metric update

- `source_processing_result`:
  `bi_knowledge_added_semantic_layer_deferred_no_financial_reports`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-94b02ac6da81-43712e77076a40a8-verification.md`
- `source_value`: `1 BI knowledge note added`; `6 adoption gates defined`;
  `0 current financial reports` and `0 measured report-automation value` in
  this `soll_app` worktree. The source has measurable knowledge/planning value,
  while production value remains unproven until a report-owning pilot passes.
