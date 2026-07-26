---
title: Dual-memory pattern for read-only Soll source triage
task_id: 08940ee570904d13b27286a5dffbc2a7
source_ref: source-item/9011e13c06d6/9c2c0ff8f35e233d
source_url: https://huggingface.co/papers/2607.07608
prototype: docs/knowledge/dual-memory-source-triage-read-only-v1.json
status: read_only_pattern_transfer_smoke_passed
---

# Dual-memory pattern for read-only Soll source triage

## Decision

Transfer only the paper signal's safe organizational pattern:

- **recent findings** hold bounded, time-stamped source observations;
- the **durable KB** holds reviewed rules and stable knowledge;
- retrieval searches both layers, preserves freshness/conflicts and cites every selected record.

This is a static, synthetic, read-only prototype. It does not import LaMem-VLA,
latent tokens, model code or robotics data. It cannot create tasks, change
source priority, invoke tools, write memory, or emit robot/control actions.
Memory supplies evidence, never authority.

The task-referenced raw item is absent from this isolated worktree. The paper
title, URL and dual-memory description are retained as task-supplied,
unverified source metadata. No additional upstream claim is made here.

## Safe mapping

| Paper-inspired idea | Soll triage counterpart | Required guard |
| --- | --- | --- |
| short-horizon memory | recent findings with `observed_at`, `expires_at`, source and evidence refs | bounded retention; stale records stay visible when needed for conflict explanation |
| long-horizon memory | durable KB records with stable repository citations | reviewed/versioned content; no silent rewrite from a recent finding |
| memory mixed into current context | one retrieval result can cite both layers | layer label and evidence ref on every selected record |
| long-task continuity | current findings are interpreted using durable policy | latest observation may supersede an older observation, never an unrelated durable rule |

Retrieval order is deterministic: search `recent_findings`, search
`durable_kb`, merge without erasing layer identity, preserve contradictions,
then return a cited advisory summary. If evidence is missing, abstain. A
retrieval result never becomes an approval, command or write request.

## Read-only prototype

`dual-memory-source-triage-read-only-v1.json` contains:

- 3 synthetic recent findings;
- 2 durable KB records citing existing Soll knowledge files;
- 3 synthetic triage queries that search and cite both layers;
- 1 freshness conflict retained in the response;
- explicit zero counters for writes, network/tool calls and robotic control.

The fixture is a contract receipt, not a ranking engine or production memory
store. Its focused test checks layer separation, expiry/provenance fields,
repository-backed durable citations, citation-to-record integrity and the
read-only/action-free boundary.

## Promotion boundary

A later production proposal would need a separate approved task and a labeled
replay showing citation precision/recall, stale-selection rate, conflict
handling and abstention quality. Until then:

1. no source-monitor, task-board, Android or server behavior changes;
2. no automatic promotion of recent findings into the durable KB;
3. no model, tool or external integration calls;
4. no robotic perception, planning, actuation or action execution.

The measurable value of this slice is the auditable two-layer triage contract,
not a claim of model-quality or runtime improvement.
