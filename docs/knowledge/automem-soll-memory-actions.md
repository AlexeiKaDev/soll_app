# AutoMem-inspired memory actions for Soll

- Task: `28913a13aa2447a48b971239464390cb`
- Source: `source-item/9011e13c06d6/308d80231b641bac`

## Decision and boundary

Soll should treat file memory as three explicit, auditable actions rather than
as implicit prompt context. This note defines a design contract only. It does
not add an autonomous memory loop, change Android/server production code, or
authorize writes.

The repository currently confirms one concrete portable artifact,
`Soll/.soll/tasks.json`; the Android SSD reader consumes it read-only. Other
paths in the offline fixture use the `fixture://` scheme and are logical test
references, not claims about current production filenames.

## Explicit memory actions

| Action | Required input | Bounded result | Safety contract |
| --- | --- | --- | --- |
| `memory.search` | query or stable task/source id, `.soll` scope, artifact kinds, limit | ranked refs with kind, revision/hash, timestamp and provenance | read-only; exact root; bounded result count; no secret/config roots |
| `memory.read` | exact refs returned by search or explicitly supplied by the operator, byte/record limit | cited excerpts or typed records plus revision/hash | no broad directory dump; fail closed on unknown/out-of-scope refs; retain provenance and freshness |
| `memory.write` | exact ref, `create`/`append`/`replace_section`, expected revision, provenance, reason, dry-run flag and approval ref | proposed diff and, only after approval, new revision | retrieval evidence required; compare-and-swap; atomic; never grants new authority; no production-code rewrite |

All action results must carry `action_id`, normalized `artifact_ref`, observed
revision, timestamp, provenance and an explicit error/empty state. A memory
record may inform a plan, but cannot grant tool, deployment or filesystem
authority.

## Retrieval-first rule

For every continuation request, including a bare `продолжай`, the coordinator
must use this order:

1. `memory.search` by task id, source ref, project and concrete terms under the
   allowed `.soll` scope.
2. `memory.read` the selected current record and any evidence refs needed to
   establish objective, completed/pending state, blockers and last verified
   revision.
3. Reconcile freshness and conflicts. Cite the selected refs; if required
   evidence is missing or contradictory, abstain and request/collect context.
4. Plan the next safe action. Do not infer a memory write from `continue`.
5. Invoke `memory.write` only for an exact memory artifact after retrieval,
   with an expected revision, explicit approval and a reviewed dry-run diff.

Consequences: search precedes every memory read; at least one relevant read
precedes every write; empty search never becomes a fabricated continuation;
and production source files are never memory-write targets.

## Offline continuation check

`automem-soll-continuation-offline-v1.json` contains five sanitized patterns
already represented by historical headings in `soll_status.md`: source-item to
task, task workspace, push/task handoff, task-board recheck with missing
evidence, and workspace/server continuation. It contains no old transcripts,
personal data, credentials or executable commands.

The fixture records simulated action traces only. All `5/5` traces start with
`memory.search`; every read follows search; the single write candidate follows
reads and is an approval-bound `dry_run`; executed writes, network calls,
external tools and production mutations are all `0`. The missing-evidence case
abstains instead of inventing state.

## Promotion gate

Do not implement these actions in production until a separate approved task
defines the authoritative `.soll` artifact registry and passes a sandboxed
runner with:

- retrieval-first order on `100%` of labeled continuations;
- required-context recall `100%` for critical objective/blocker/evidence refs;
- stale or contradictory state reported, never silently selected;
- secret/raw-payload leaks and writes without approval both `0`;
- atomic conflict handling proven against revision changes.

The paper signal therefore yields a falsifiable design and offline audit, not a
claim of production memory-quality improvement.
