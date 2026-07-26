---
task_id: 092df8f4d66143d0a402c29aa74155cc
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/e348746d9311
status: validated
confidence: medium
source_trust: untrusted_external_content
source_processing_result: validated_relevant_offline_eval_blueprint_runtime_integration_deferred
verification_artifact: Soll/outputs/source-processing/task-092df8f4d66143d0a402c29aa74155cc-rumba-integration-audit.md
value_metric: "1 wiki integration review added; 3 primary upstream surfaces and 6 current Soll memory seams audited; 4 diagnostic axes and 7 measurable promotion gates defined; 1/1 focused contract test passed; 0 dataset rows imported, 0 benchmark/model runs and 0 production/runtime changes"
verified_at: 2026-07-26 Europe/Chisinau
---

# RUMBA integration review audit

## Outcome

The integration review is complete and documented in
`wiki/rumba-russkoyazychnyy.md`. RUMBA is retained as a conditional,
offline-only evaluation blueprint for Russian long-term conversational memory.
Its fine-grained taxonomy can expose update, deletion, temporal and abstention
failures that an aggregate retrieval score hides.

No runtime integration is justified by the current repository. Soll local
memory records accepted proactive suggestions, supports recency/export reads
and manual deletion, and is not a question-conditioned memory system behind
`POST api/v1/chat/turn`.

The named monitored source and wiki page were absent from the Base SHA. The
review treats that source as an untrusted lead and validates benchmark identity
against arXiv v1, the official ai-forever repository and the official
ai-forever Hugging Face dataset. No unavailable monitored text was imported.

## Focused smoke/audit artifact

| Check | Observed result |
| --- | --- |
| Benchmark identity | RUMBA / arXiv:2607.21447v1, submitted 2026-07-23 |
| Primary surfaces | arXiv paper record, `ai-forever/RUMBA` code, `ai-forever/RUMBA` dataset |
| Dataset shape | RU/EN timestamped multi-session QA; 85 user IDs and about 1.54k rows on the public dataset surface |
| Diagnostic contract | 3 supergroups, 17 semantic types and 4 cross-cutting axes |
| Current Soll capture | accepted proactive suggestions only; no full chat-history ingestion |
| Current Soll read path | bounded recency/export; no question-conditioned temporal retrieval |
| Current chat seam | local memory is not an explicit field of `ChatTurnRequest` |
| Safe adoption | taxonomy plus seven future offline promotion gates |
| Dataset/model execution | 0 rows imported; 0 benchmark, model or judge runs |
| Product change | wiki, audit and focused test only; 0 production/runtime changes |

`RumbaRussianMemoryBenchmarkIntegrationReviewTest` guards:

- exact task, source, monitored-path and primary-source trace;
- the offline-only integration decision and no-benchmark-run boundary;
- all four RUMBA diagnostic axes and seven future promotion gates;
- six current Soll memory seams against production source;
- the quantified `value_metric` and absence of RUMBA runtime/dependency wiring.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.RumbaRussianMemoryBenchmarkIntegrationReviewTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- wiki integration reviews added: `1`;
- primary upstream surfaces reviewed: `3`;
- current Soll memory seams audited: `6`;
- RUMBA diagnostic axes retained: `4`;
- measurable future promotion gates defined: `7`;
- focused repository contract tests passed: `1/1`;
- dataset rows imported and benchmark/model runs: `0`;
- production/runtime files, dependencies, permissions and API contracts
  changed: `0`.

The observed value is a falsifiable evaluation boundary. No runtime memory
quality gain is claimed until a separate approved offline pilot measures it.
