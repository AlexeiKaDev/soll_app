---
task_id: 9b29d65c175e48f4bf9f6a604e012fe9
source_ref: source-item/0d75242b770a/97f971eb6ef1eec9
source_item: yandex-org-graph-reference-counted-closure
source_processing_result: implemented_task_graph_reachability_cache
verification_artifact: Soll/outputs/source-processing/source-item-0d75242b770a-97f971eb6ef1eec9-verification.md
source_value: "4 Room tables, 5 secondary indexes, 2 cache scopes, 1 indexed reachability query, and focused alternative-path/cycle tests; production latency measurements remain 0"
verified_at: 2026-07-15 Europe/Chisinau
---

# Yandex organization-graph model applied to `soll_app`

## Outcome

The source's bridge-table plus reference-counted closure model is now applied to
the existing Soll task-graph service as a bounded Room cache. Database schema 24
stores full node metadata, direct graph edges and one closure row per reachable
node pair. `path_count` preserves alternative paths without duplicate closure
rows, and the primary/secondary indexes support non-recursive descendant reads.

`SollRepository.getTaskGraph` caches successful live or existing 404-compatible
task-board graphs and returns the matching `open`/`all` snapshot after a later
network/service failure. The indexed query now powers a `Проект` filter inside
the existing task workspace; no separate graph screen or server endpoint was
added.

## Source and scope evidence

- Public source article:
  `https://habr.com/ru/companies/yandex/articles/1046483/`.
- Task source reference: `source-item/0d75242b770a/97f971eb6ef1eec9`.
- The task-referenced raw snapshot
  `raw/monitored\habr-yandex-company\20260702-194200-500-07ff70ec.md` is not
  present in this isolated worktree. The public article and actual repository
  seams were used; missing raw content was not invented.
- Existing seam: `SollGateway.getTaskGraph` and `SollRepository.getTaskGraph`
  already expose a server-driven graph capped at 700 nodes, with an Android-sync
  compatibility builder but no durable graph cache before this change.
- Durable implementation contract:
  `docs/knowledge/task-graph-reachability-index.md`.

## Applied schema/query audit

| Check | Evidence | Result |
| --- | --- | --- |
| Bridge table retained | `task_graph_edges` stores direct API edges | PASS |
| Duplicate-free closure | PK `(scope, ancestor_id, descendant_id)` plus `path_count` | PASS |
| Reflexive paths | builder creates one count-1 self row for every node | PASS |
| Forward lookup indexed | closure PK begins `(scope, ancestor_id)` | PASS |
| Reverse lookup indexed | `(scope, descendant_id, ancestor_id)` index | PASS |
| Node join indexed | node PK is `(scope, id)` | PASS |
| Read avoids recursion/DISTINCT | `getReachableNodes` joins one closure row per node, orders and paginates | PASS |
| Snapshot replacement atomic | reachability derives first; Room `@Transaction` replaces one scope | PASS |
| Cycles rejected | topological build requires a DAG | PASS |
| Mobile density bounded | snapshots above 50,000 closure rows are not persisted | PASS |
| Public contract preserved | server endpoint and graph DTOs are unchanged | PASS |
| Offline service value | live/compatibility successes cache; later failures read the same scope | PASS |
| Product query used | project chip calls `getTaskGraphDescendants` and filters the loaded board | PASS |

## Focused smoke result

`TaskGraphReachabilityBuilderTest` uses a diamond graph plus a direct edge:
`A -> B -> D`, `A -> C -> D`, and `A -> D`. It verifies:

- one `A/D` closure row with `path_count = 3`;
- one reflexive `A/A` row with `path_count = 1`;
- nine total unique closure rows for the four-node fixture;
- no reverse `D/A` row;
- a cyclic `A -> B -> A` snapshot is rejected.

`HabrYandexOrgGraphSourceTriageTest` ties the source reference, knowledge
contract, Room schema 24, migration, indexed SQL, repository fallback and value
fields to this task.

`TaskGraphMigrationTest` is defined to create schema 23, run Room validation for
migration 23→24, then perform replace/read/descendant/path-count operations
through the generated DAO. The test compiles into the debug instrumentation target; this
workstation has no attached ADB device or installed emulator, so device execution
is a follow-up rather than claimed evidence.

## Applicability decision

Use the model for bounded, read-heavy, server-provided DAG snapshots such as the
Soll task graph. Do not introduce a graph database or copy the source's
incremental PL/pgSQL functions into Android. The client receives complete
snapshots and owns no individual authoritative edge mutations, so a deterministic
rebuild plus transactional swap is the smaller correct adaptation.

For a future large authoritative server graph, separately benchmark PostgreSQL
incremental counters, fat-edge background workers, per-scope serialization,
dual-write parity and staged read rollout. The article's 500,000-user and
sub-50-ms results are not mobile measurements.

## Value metric update

- `source_processing_result`: `implemented_task_graph_reachability_cache`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-0d75242b770a-97f971eb6ef1eec9-verification.md`
- `source_value`: 4 Room tables, 5 explicit secondary indexes, 2 isolated cache
  scopes, 1 indexed non-recursive reachability query used by the task-board
  project filter, an actual repository offline fallback, a 50,000-row mobile
  budget, focused alternative-path/cycle verification and a compiled migration
  smoke contract. Production
  p50/p95 latency, cache-hit rate and storage measurements remain `0` until a
  real device/runtime observation is collected.
