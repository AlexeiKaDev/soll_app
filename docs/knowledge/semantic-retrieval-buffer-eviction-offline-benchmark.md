---
title: Classic and score-based eviction for Soll semantic retrieval buffers
source: Hugging Face Daily Papers
source_url: https://huggingface.co/papers/2607.00394
arxiv: 2607.00394v1
source_ref: source-item/9011e13c06d6/f0bb9e2e01664c42
task_id: 520850f55a864bf58f347cf8211d04fe
benchmark: docs/knowledge/semantic-retrieval-buffer-eviction-offline-v1.json
status: offline_synthetic_benchmark_passed_no_runtime_promotion
reviewed_at: 2026-07-27 Europe/Chisinau
---

# FIFO, LRU и score-based eviction для Soll retrieval buffer

## Короткий вывод

Статья формализует semantic retrieval buffer как online replacement со
switching cost и непрерывным качеством semantic hit. На LoCoMo и DialSim авторы
сообщают, что LRU/LFU хуже FIFO при слабой temporal locality и frequency
concentration. SOLAR отделяет момент изменения буфера, основанный на накопленном
regret, от выбора содержимого через Bayesian online learning по неявному
retrieval feedback. Это paper evidence, а не измерение Soll.

Локальный аудит нашёл `AssistantMemory`, но не production semantic retrieval
cache. `AssistantMemoryDao.observeRecent(limit)` возвращает ограниченное
представление по `pinned DESC, updated_at DESC`; repository сохраняет только
принятые предложения при включённой памяти. `lastUsedAt` хранится в модели, но
не участвует в DAO ordering, capacity enforcement или automatic eviction.
Поэтому в runtime ничего не менялось.

Полезный результат для Soll — executable offline benchmark
`semantic-retrieval-buffer-eviction-offline-v1.json` и
`SemanticRetrievalBufferEvictionOfflineBenchmarkTest`. Он сравнивает FIFO, LRU
и простой static score-based policy на трёх синтетических обезличенных traces.

## Source receipt

- Hugging Face item: <https://huggingface.co/papers/2607.00394>;
- primary record: <https://arxiv.org/abs/2607.00394>;
- paper identity: `arXiv:2607.00394v1`, submitted 1 July 2026;
- evaluated by the paper: eight policies on MemoryBench-Full LoCoMo and
  DialSim;
- author-reported SOLAR result: about `17%` modification rate and `5–75%`
  relative improvement over FIFO at tight capacities.

The task-referenced raw snapshot
`raw/monitored\hugging-face-daily-papers\20260708-220900-when-classic-cache-policies-fail-learning-augmen-65c24ed0.md`
is absent from this isolated worktree. The raw ingestion state is not inferred
from the public paper.

## Offline benchmark contract

The replay uses only IDs `T01`–`T05`, static relevance scores and request
sequences. It contains no titles, descriptions, user text, personal data,
credentials, production history or runtime memory exports.

Capacity is three entries in every scenario:

| Policy | Deterministic eviction key |
| --- | --- |
| FIFO | oldest insertion sequence |
| LRU | oldest access sequence, then oldest insertion |
| score-based | lowest static relevance score, then oldest insertion |

On a miss the score-based policy evaluates the incoming candidate together
with resident entries. A low-score candidate may therefore evict itself. Scores
are fixed fixture metadata: the evaluator does not learn, call a model, use
future trace positions or update scores during replay. This is deliberately
much simpler than SOLAR.

Three traces prevent one recency pattern from being treated as universal:

1. `recency_trap` revisits an item that LRU evicts after a recent distraction;
2. `recency_friendly` gives LRU useful temporal locality;
3. `uniform_cycle` exceeds capacity with equal scores, where all policies miss.

## Measured offline result

| Scenario | Requests | FIFO hits | LRU hits | score hits |
| --- | ---: | ---: | ---: | ---: |
| recency trap | 12 | 3 | 1 | 7 |
| recency friendly | 10 | 2 | 4 | 5 |
| uniform cycle | 8 | 0 | 0 | 0 |
| **aggregate** | **30** | **5** | **5** | **12** |

Aggregate hit rate is `16.67%` for FIFO, `16.67%` for LRU and `40.00%` for
score-based eviction. Relevance-weighted hit rate is `21.47%`, `21.47%` and
`49.21%`, respectively. The score policy records `4` explicit incoming
candidate rejections and reduces eviction events from `16` to `9`.

The recency trap reproduces the source's qualitative warning locally:
FIFO gets `3/12` hits while LRU gets `1/12`. The recency-friendly trace also
shows the opposite (`2/10` versus `4/10`), so the result does not support a
blanket claim that FIFO is always better.

## Decision

The source has measurable Soll value as a benchmark and non-promotion guard,
not as a runtime policy. The score-based policy gains `7` aggregate hits over
either classic baseline in this small designed fixture, but the fixture is
synthetic, scores are predeclared and binary ID hits do not model embedding
similarity, continuous hit quality, switching cost, learned timing or Bayesian
feedback.

Promotion remains rejected until a separately approved, sanitized,
non-production export demonstrates a real bounded semantic buffer and reports
capacity sweeps, continuous retrieval quality, update cost, score calibration,
adversarial/no-locality traces and uncertainty over repeated replays. No result
may automatically mutate memory, tasks, source priorities or runtime policy.

## Safety boundary

- network/model/external integration calls made by the evaluator: `0`;
- runtime or user-memory reads: `0`;
- user-data records: `0`;
- memory writes or automatic actions: `0`;
- Android/runtime/API/dependency changes: `0`.

The benchmark is deterministic repository-only evaluation evidence. It has no
code path for network access, credentials, external tools or production
actions.
