---
task_id: 51efb9a76b94469e86a3fd8b9181918a
project: soll_app
source_ref: source-item/9011e13c06d6/d4d88b2dcc5eb63f
source_processing_result: paperpilot_workflow_design_completed_runtime_pilot_deferred
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-d4d88b2dcc5eb63f-verification.md
source_value: "1 proposal-only workflow design and 1 valid synthetic DAG added; 9 typed operators and 5 current Soll seams audited; 6 promotion gates defined; 1/1 focused contract test passed; 0 external searches/provider calls/model runs and 0 Android production/API/UI/dependency changes"
verified_at: 2026-07-19 Europe/Chisinau
---

# PaperPilot literature-search prototype verification

## Outcome

The Hugging Face Daily Papers signal produced a bounded, testable design for a
future Soll literature-search session. The design is documented in
`docs/knowledge/hugging-face-paperpilot-literature-search-prototype.md`, with a
syntactically valid proposal fixture at
`docs/knowledge/hugging-face-paperpilot-literature-search-prototype-v1.json`.

The prototype keeps Hugging Face as anchor/source provenance and places typed
workflow validation/execution on a future Soll server capability. Android
remains a projection through the existing Sources, Chat and Tasks surfaces. No
production API, UI, dependency, permission, credential or runtime path changed.

## Source audit

The task-specified monitored raw file is not vendored in this isolated
worktree. The title, PaperPilot DAG/toolset description, reported retrieval
metrics and limitations were checked against primary arXiv record
`2607.00597v2` and the supplied Hugging Face paper URL. Published model metrics
are recorded only as source claims and are not represented as Soll results.

## Focused prototype audit

| Check | Observed result |
| --- | --- |
| Traceability | exact task id, source ref, Hugging Face URL and `arxiv:2607.00597v2` retained |
| Current architecture | 5 current Soll seams audited; no dedicated literature workflow/session API found |
| Workflow | 1 proposal-only topologically ordered DAG with 9 typed operators |
| Terminal contract | `extract_evidence` produces `evidence_set` with provenance fields |
| Multi-turn contract | ask/update/add/modify/remove/finalize actions create a new revision and require reapproval |
| Approval | external search, persistence and task creation are separately approval-gated |
| Safety | network disabled and external side effects forbidden in the fixture; shell/arbitrary code/arbitrary URL fetch forbidden |
| Limits | 50 candidates, 10 results, 9 nodes, 2 citation hops, 1 rerank and 60 seconds |
| Promotion | 6 measurable gates define schema, safety, quality, provenance, UX and runtime thresholds |
| Runtime activity | 0 external searches/provider calls/model runs; 0 persistent/task-board writes |
| Android delta | 0 production/API/UI/dependency/permission changes |

The audit explicitly rejects reuse of `SollTaskGraph` as a literature execution
DAG because task hierarchy/cache semantics differ from typed search nodes and
approval lifecycle.

## Focused smoke/audit artifact

Test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.PaperPilotLiteratureSearchPrototypeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

`PaperPilotLiteratureSearchPrototypeTest` parses the JSON, validates task/source
trace, checks unique topological node ids and exact input/output type
compatibility, pins the 9-operator allowlist, terminal evidence type,
multi-turn revision rules, approval boundary, safety policy, limits, zero
side-effect counters, design seams and verification value keys.

## Value metric update

- `source_processing_result`:
  `paperpilot_workflow_design_completed_runtime_pilot_deferred`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-d4d88b2dcc5eb63f-verification.md`;
- `source_value`: `1` proposal-only workflow design and `1` valid synthetic
  DAG added; `9` typed operators and `5` current Soll seams audited; `6`
  promotion gates defined; `1/1` focused contract test passed; `0` external
  searches/provider calls/model runs and `0` Android production/API/UI/
  dependency changes.

The measurable value is a source-traceable contract, fixture, validation
invariants and stop conditions for a later approved server pilot. Retrieval
quality remains unmeasured, so runtime adoption stays deferred until a named
server owner, approved provider and baseline evaluation exist.
