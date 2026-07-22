---
task_id: fd7dc4c927504b4a9fef2a0da186c365
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/6972c43000d1
status: validated
confidence: medium
source_trust: untrusted_external_content
source_processing_result: architecture_patterns_extracted_runtime_deferred
verification_artifact: Soll/outputs/source-processing/task-fd7dc4c927504b4a9fef2a0da186c365-architecture-patterns-audit.md
value_metric: "1 read-only architecture audit added; 5 memory/hook patterns extracted; 6 current Soll seams audited; 5 adoption gates defined; 21/21 existing focused tests passed; 0 production/runtime files changed"
verified_at: 2026-07-22 Europe/Chisinau
---

# Claude Code memory and hooks: bounded Soll architecture audit

## Decision

The signal produces measurable architecture value, but it does not justify a
runtime integration. Five reusable patterns are retained below; product code,
dependencies, generic command hooks and external integrations remain unchanged.

The task-named monitored file
`monitored/qwwiwi/20260708-053006-public-architecture-claude-code-492ccf25.md`
is absent from this isolated worktree. The closest public title/slug match is
[Claude Code Agent — Complete Architecture Deep Dive](https://gist.github.com/yanchuk/0c47dd351c2805236e44ec3935e9095d),
a secondary source-code analysis. It is treated as an untrusted lead, not as an
authoritative or version-stable description. The public concepts retained here
were cross-checked against the official Claude Code documentation for
[memory](https://code.claude.com/docs/en/memory),
[hooks](https://code.claude.com/docs/en/hooks) and
[permissions](https://code.claude.com/docs/en/permissions).

Version-specific internal filenames, token thresholds, hook counts, model
choices and unpublished implementation details from the secondary source are
excluded. No source text, scripts or configuration were imported.

## Five extracted patterns

### P1 — Scoped, inspectable memory with provenance

Keep durable instructions, user-approved facts and transient run context in
separate scopes. Every saved fact needs visible provenance and confidence, and
must remain reviewable and deletable rather than becoming an opaque prompt
blob.

Soll fit: `AssistantMemory` already records category, source, confidence,
timestamps and pinning. `AssistantMemoryRepository` writes accepted suggestions
only when memory is enabled and exposes export/delete operations. The safe
server summary omits raw payload JSON and media. This is a strong local-memory
base; it is not evidence for silently capturing chats or tool transcripts.

Adoption state: **reuse now**. If server-side agent memory is added later, add
explicit user/project/run scope and retention metadata instead of widening the
Android capture surface.

### P2 — Bounded compaction that preserves run invariants

Reduce context progressively: discard replaceable bulk before summarizing
decisions, and preserve the objective, completed/pending state, evidence and an
exact recent tail. Compaction is a state transition with a budget and failure
policy, not an unconstrained summary request.

Soll fit: `SollAgentContextAssembler` already compacts older typed events into
an inspectable `AgentCompactionCheckpoint` while retaining recent events. The
checkpoint deduplicates evidence and preserves completed/pending state. It is a
transport-neutral prototype, not an Android-hosted autonomous loop.

Adoption state: **reuse the invariant, defer extra tiers**. A multi-tier
server implementation is justified only after real context-size and replay
measurements; do not copy source-specific thresholds.

### P3 — Deterministic pre-action policy, separate post-action audit

Actions that may change state must pass a deterministic gate before execution.
Observation, metrics and audit persistence happen after the decision and cannot
retroactively authorize a blocked action. Deny must win over allow, and an
unknown capability must fail closed.

Soll fit: `CapabilityRegistry` rejects unregistered/blocked/disabled
capabilities; `CommandSafetyGate` then checks Android permissions and explicit
confirmation; `DualUsePolicy` blocks prohibited activities before execution.
`AssistantEventLogger` is a separate best-effort observation seam. These
components already express the safe half of pre/post hooks without installing a
generic shell-hook runtime on the phone.

Adoption state: **reuse now** for every new action surface. Audit failure may be
reported or queued, but it must never turn a denied preflight into an allow.

### P4 — Typed and bounded lifecycle hooks

Represent extension points as a small typed lifecycle contract such as
`before_action`, `after_success`, `after_failure` and `before_compaction`.
Each hook needs a matcher, input/output schema, timeout, failure mode,
idempotency key and recursion guard. Only synchronous pre-action hooks may
block; asynchronous observers cannot control an action that already happened.

Soll fit: `AssistantEvent` provides an event envelope, but `type` and `source`
are free-form strings and there is no generic hook registry, ordering contract,
timeout or recursion guard. That is a deliberate gap, not an invitation to run
arbitrary commands from Android.

Adoption state: **server-only prototype later**. Start with in-process typed
callbacks over synthetic actions; keep command/HTTP/plugin hook execution and
credentials out of Android.

### P5 — Lazy context materialization and safe summaries

Load indexes and short summaries first; materialize full instructions, memory
records or tool schemas only after an explicit selection. Bound every query and
prevent raw payloads from leaking into the compact context.

Soll fit: `SollAgentContextAssembler` publishes the complete skill summary
index while loading only requested known skill instructions.
`AssistantMemoryRepository.observeRecent(limit)` bounds the memory view, and
`AssistantMemoryExporter.toServerSummaryMarkdown()` excludes raw payloads.

Adoption state: **reuse now**. Any future retrieval must measure relevance and
context size and must fail closed for unknown IDs; no vector store or external
memory service is justified by this signal.

## Six audited Soll seams

| Seam | Observed repository contract | Gap or boundary |
| --- | --- | --- |
| `CLAUDE.md` | Inspectable project instructions | Repository guidance, not product memory |
| `AssistantMemory.kt` | Categorized memory with source/confidence and safe exports | No explicit user/project/run scope |
| `AssistantMemoryRepository.kt` | Opt-in write, bounded view, export/delete and safe sync | No silent transcript capture |
| `AssistantEvent.kt` + repository | Local event envelope and best-effort logging | Free-form type/source; not a hook engine |
| `SollAgentPrototype.kt` | Selected skills, allowlisted tool IDs and deterministic checkpoint | Prototype only; one compaction tier |
| Capability and policy gates | Fail-closed registry, permission/confirmation gate and blocked dual-use policy | Must stay before execution; no generic mobile hook runner |

## Measurable adoption gates

Do not promote P2 or P4 into a runtime until a separate approved server task
passes all five gates on synthetic, non-sensitive actions:

1. **Lifecycle contract:** `100%` of test actions emit the expected ordered
   before/after event sequence; blocked actions execute `0` actuators.
2. **Replay invariant:** after each compaction tier, objective, pending work,
   completed decisions and evidence references have `100%` fixture parity.
3. **Idempotency and recursion:** duplicate delivery produces one durable audit
   record and recursive hook invocations remain `0` across at least `100` runs.
4. **Budget and failure:** every hook has a tested timeout; timeout/error behavior
   matches its declared fail-open observation or fail-closed policy mode.
5. **Retrieval quality:** on a labeled Soll task set, report relevant-context
   precision, missed required context, injected bytes/tokens and raw-payload
   leaks; raw-payload leaks must equal `0`.

## Focused smoke/audit result

Repository base before this slice: `af240c4f44cb977f8b4c1c22662560ed688980e5`;
the initial worktree was clean. The existing focused domain suites were run:

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.soll.domain.agent.SollAgentPrototypeTest" `
  --tests "com.soll.domain.assistant.memory.AssistantMemoryExporterTest" `
  --tests "com.soll.domain.assistant.CapabilityRegistryTest" `
  --tests "com.soll.domain.securitylab.DualUsePolicyTest" `
  --console=plain
```

Observed result on 2026-07-22: exit code `0` (`BUILD SUCCESSFUL`), `21/21`
tests passed with `0` failures, `0` errors and `0` skipped. A focused artifact
contract also verified the exact task/source trace, five numbered patterns, six
audited seams, five adoption gates, the updated `value_metric` and a one-file
artifact-only diff.

## Value metric update

- read-only architecture audits added: `1`;
- memory/hook patterns extracted: `5`;
- current Soll seams audited: `6`;
- measurable adoption gates defined: `5`;
- existing focused domain tests passed: `21/21`;
- monitored source files imported: `0`;
- production/runtime files changed: `0`;
- dependencies, permissions, hook runners and external integrations added: `0`.

The measurable value is a bounded reuse/defer map plus falsifiable gates. No
runtime benefit is claimed, because no agent runtime, hook execution or memory
retrieval benchmark was run.
