---
task_id: 520850f55a864bf58f347cf8211d04fe
project: soll_app
source_ref: source-item/9011e13c06d6/f0bb9e2e01664c42
source_item: "When Classic Cache Policies Fail: Learning-Augmented Replacement for Semantic Retrieval Buffers"
source_processing_result: research_note_and_offline_eviction_benchmark_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-f0bb9e2e01664c42-verification.md
source_value: "1 research note and 1 executable benchmark fixture added; 3 policies replayed on 3 synthetic anonymized traces/30 requests; FIFO 5 hits/16.67%, LRU 5 hits/16.67%, score-based 12 hits/40.00%; score-based gained 7 hits and reduced evictions 16 to 9; 2/2 focused tests passed; 0 runtime/user-memory reads, user-data records, offline-evaluator network/model/external calls, memory writes, automatic actions or production changes"
verified_at: 2026-07-27 Europe/Chisinau
---

# Semantic retrieval buffer eviction verification

## Outcome

The source produced a bounded Soll research and evaluation package:

- research note:
  `docs/knowledge/semantic-retrieval-buffer-eviction-offline-benchmark.md`;
- deterministic anonymized replay:
  `docs/knowledge/semantic-retrieval-buffer-eviction-offline-v1.json`;
- executable evaluator and contract audit:
  `SemanticRetrievalBufferEvictionOfflineBenchmarkTest`.

The task-referenced raw monitored file is absent from this isolated worktree.
Paper identity and the abstract-level claims used by the note were checked on
the public Hugging Face and arXiv read-only pages. No upstream code, model,
dataset or dependency was downloaded or executed.

## Current Soll cache audit

| Check | Observed result |
| --- | --- |
| Local memory seam | `AssistantMemory` and `AssistantMemoryRepository` present |
| Bounded view | DAO orders by pinned/updated and applies `LIMIT :limit` |
| Semantic matching | absent |
| Capacity enforcement | absent |
| Automatic eviction | absent |
| Runtime decision | no production policy change |

The benchmark is therefore an isolated evaluator, not a replay of a currently
deployed semantic cache.

## Offline replay result

| Scenario | Requests | FIFO hits | LRU hits | score-based hits |
| --- | ---: | ---: | ---: | ---: |
| recency trap | 12 | 3 | 1 | 7 |
| recency friendly | 10 | 2 | 4 | 5 |
| uniform cycle | 8 | 0 | 0 | 0 |
| **aggregate** | **30** | **5** | **5** | **12** |

- aggregate hit rate: FIFO `16.67%`, LRU `16.67%`, score-based `40.00%`;
- relevance-weighted hit rate: FIFO `21.47%`, LRU `21.47%`,
  score-based `49.21%`;
- aggregate evictions: FIFO `16`, LRU `16`, score-based `9`;
- score-based incoming-candidate rejections: `4`;
- gain over either classic aggregate: `+7` hits and `+23.33pp` hit rate.

The recency-trap result (`3` FIFO hits versus `1` LRU hit) confirms that the
test can represent the source's qualitative failure mode. The recency-friendly
result (`2` versus `4`) prevents that designed case from becoming a universal
FIFO claim.

## Safety audit

- synthetic anonymized task IDs only: `T01`–`T05`;
- runtime/user-memory reads and user-data records: `0`;
- network, model and external integration calls by the evaluator: `0`;
- credentials or environment reads: `0`;
- memory writes, automatic actions and task/source mutations: `0`;
- Android/runtime/API/dependency changes: `0`;
- commits, pushes and deploys: `0`.

## Focused test result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.SemanticRetrievalBufferEvictionOfflineBenchmarkTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); `2/2 focused tests passed`
with `0` failures, `0` errors and `0` skipped.

The first test pins source/task traceability, the current AssistantMemory
boundary, anonymization, all three policy contracts, safety counters and the
required value-metric keys. The second executes every request, checks each
scenario and aggregate metric against the durable fixture and confirms both
the LRU recency trap and the bounded score-policy advantage.

## Value metric update

- `source_processing_result`:
  `research_note_and_offline_eviction_benchmark_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-f0bb9e2e01664c42-verification.md`;
- `source_value`: one research note and one executable benchmark fixture;
  three policies replayed on three synthetic anonymized traces and `30`
  requests; FIFO `5` hits/`16.67%`, LRU `5` hits/`16.67%`, score-based `12`
  hits/`40.00%`; score-based gained `7` hits and reduced evictions from `16`
  to `9`; `2/2` focused tests passed. Runtime/user-memory reads, user data,
  offline-evaluator network/model/external calls, memory writes, automatic
  actions and production changes: `0`.

This small designed replay proves the benchmark contract, not production
superiority. Runtime promotion remains rejected pending separately approved
sanitized non-production traces and continuous semantic-quality evaluation.
