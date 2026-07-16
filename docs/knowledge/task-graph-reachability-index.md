# Task graph reachability index for `soll_app`

Status: implemented as a local Room cache and project filter for the existing
Soll task workspace. The server remains the source of truth; no separate graph
workspace or public server endpoint was added.

Source: [Yandex 360, "Как мы научили реляционую базу хранить оргструктуру в
виде графа на 500к пользователей"](https://habr.com/ru/companies/yandex/articles/1046483/).

## What was reusable

The source compares adjacency lists, materialized paths, nested sets, a bridge
table and closure tables. Its relevant workload is a read-heavy DAG where the
hot query asks for a flat, paginated set of all descendants. The selected model
keeps direct edges in a bridge table and one closure row per
`(ancestor, descendant)`. `path_count` records how many paths make that pair
reachable, so alternative paths do not create duplicate rows or require a
read-time `DISTINCT`. Every node has one reflexive reachability row with a count
of one.

This shape matches the existing Soll task graph closely enough to reuse as a
bounded mobile index: project, subproject, source and task nodes form a DAG; one
task can be reachable through more than one container/source edge; and callers
need stable node IDs plus task metadata, not arbitrary graph algorithms.

## Applied Room schema

Database version 24 adds four tables:

| Table | Role | Key |
| --- | --- | --- |
| `task_graph_snapshots` | atomic snapshot metadata | `scope` |
| `task_graph_nodes` | node metadata kept beside the graph | `(scope, id)` |
| `task_graph_edges` | direct bridge edges returned by Soll | `(scope, id)` |
| `task_graph_reachability` | deduplicated closure index | `(scope, ancestor_id, descendant_id)` |

Two independent scopes, `open` and `all`, preserve the `includeDone` contract.
Foreign keys cascade a replaced snapshot through its nodes, direct edges and
reachability rows. `TaskGraphCacheDao.replaceGraph` derives all path counts
before deleting the previous snapshot, then swaps the four sets in one Room
transaction. A cycle, missing node or duplicate ID rejects the new index and
leaves the previous transactionally committed cache intact.

The mobile builder stops before persistence if a snapshot would exceed 50,000
closure rows. The 30-second board refresh does not refetch the graph; initial and
manual graph refreshes also skip rebuilding an unchanged canonical node/edge
snapshot.

Five explicit secondary indexes cover node type/task lookup, both bridge-edge
directions and reverse reachability. The composite primary key on
`task_graph_reachability` is also the hot forward index because it begins with
`(scope, ancestor_id)`.

## Indexed descendant query

`TaskGraphCacheDao.getReachableNodes` uses the closure row before joining node
metadata, so it neither recurses nor deduplicates a large intermediate result:

```sql
SELECT node.*
FROM task_graph_reachability AS reachability
INNER JOIN task_graph_nodes AS node
  ON node.scope = reachability.scope
 AND node.id = reachability.descendant_id
WHERE reachability.scope = :scope
  AND reachability.ancestor_id = :ancestorId
  AND reachability.descendant_id != :ancestorId
  AND (:kind IS NULL OR node.kind = :kind)
ORDER BY node.id
LIMIT :limit OFFSET :offset;
```

This is the production query behind the existing task board's `Проект` filter:
the selected root resolves to descendant task IDs and filters the board already
loaded on screen. The companion `getPathCount` query makes alternative-path
behavior auditable.
For the focused diamond fixture with a direct `A -> D` edge plus paths through
`B` and `C`, the schema stores one `A/D` closure row with `path_count = 3`.

## Service behavior

`SollRepository.getTaskGraph` now follows this order:

1. Read the existing `/api/v1/soll/tasks/graph` response and atomically cache
   the matching `open` or `all` snapshot.
2. Preserve the existing 404 compatibility path by building a graph from the
   Android sync board, then cache that graph too.
3. If live and compatibility reads fail, return the last valid Room snapshot
   for the requested scope.
4. Load project roots into the existing task filters; when the user selects one,
   call `getTaskGraphDescendants` and filter the current board by descendant
   `taskId`. Clearing the chip restores the unfiltered list.

Cache write/read failures never turn a valid live response into a failure.
Coroutine cancellation is still propagated. The task-graph endpoint and DTOs
remain unchanged, and the existing task workspace gains only a bounded filter.

## Deliberate Android adaptation

The source's PostgreSQL functions increment/decrement closure counts for each
edge mutation and move very large cross products to per-organization background
workers. `soll_app` receives complete task-graph snapshots capped at 700 nodes;
it does not own individual server mutations. The mobile implementation therefore
rebuilds reachability deterministically from each complete DAG and swaps it in
one transaction. This avoids copying PostgreSQL PL/pgSQL, a write queue and
eventual-consistency machinery into a client that has neither the workload nor
the ownership boundary for them.

If the authoritative Soll server later needs descendant listing at much larger
scale, it should benchmark the source's incremental bridge + reference-counted
closure design in PostgreSQL, including fat-edge background serialization,
dual-write comparison and staged read rollout. The Android cache is evidence
that the data/query model fits; it is not a claim that the source's 500,000-user
latency numbers transfer to this device or service.

## Verification and measurable value

- Four Room tables and five explicit secondary indexes are exported in schema
  24; primary keys provide the forward closure and node lookup indexes.
- Two cache scopes keep open/all snapshots isolated.
- One non-recursive, paginated SQL query returns unique reachable nodes.
- The focused builder test proves three alternative `A -> D` paths collapse to
  one row, proves cycles are rejected and enforces a configurable row budget.
- `TaskGraphMigrationTest` is defined to create schema 23, validate migration
  23→24, then exercise transactional replace/read/descendant/path-count DAO
  behavior. It compiles into the debug instrumentation APK; execution still
  requires an ADB device or emulator.
- The repository now has a real offline fallback and the existing task list has
  a production descendant-query caller. Production latency, cache-hit rate and
  device storage impact have not yet been measured and must not be inferred from
  the source article.
