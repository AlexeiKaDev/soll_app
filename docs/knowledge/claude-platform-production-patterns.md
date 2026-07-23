---
title: Claude Platform production patterns for Soll
task_id: 7338bb761a114c699c358a2a3081d923
source_ref: source-item/69ab93825377/8319dac620e06275
reviewed_at: 2026-07-23 Europe/Chisinau
status: knowledge-only; server pilot requires a separate approved task
---

# Claude Platform: KB card and production deep dive

## KB card

**What it is.** Claude Platform exposes two different build surfaces. The
Messages API gives an application direct model access while the application
owns conversation state and the tool loop. Managed Agents supplies stateful,
autonomous sessions and persistent event history. The platform also documents
tool use, streaming, files, web search, prompt caching, evaluations, rate
limits, and usage/cost administration.

**Soll fit.** If Claude is evaluated later, start with a provider-neutral,
server-only Messages adapter. That boundary lets Soll keep deterministic
policy, approvals, context selection, retry behavior, and audit evidence under
its own control. Do not add Anthropic credentials, SDKs, model IDs, or tool
execution to the Android app. Managed Agents is not the default because its
stateful autonomous surface needs a separate retention, identity, isolation,
and audit review.

**Decision.** This task creates knowledge and promotion gates only. It makes no
Claude API call and changes no runtime contract. Autonomous `bash`, computer
use, and other general execution remain rejected unless a later approved
server task first supplies an isolated sandbox, a default-deny tool allowlist,
explicit approval for side effects, and an append-only audit log.

## Production deep dive

### 1. Tool schemas are typed proposals, not authority

Define each client tool with a stable name, a detailed description, and a
minimal JSON `input_schema`. Use closed objects (`additionalProperties: false`),
required fields, narrow enums and bounds. For complex inputs, add
schema-valid `input_examples`. Where the supported JSON Schema subset is
sufficient, set `strict: true` so the emitted arguments conform to the schema.

Schema conformance is not authorization. A server-owned executor must still:

1. resolve the tool name through a versioned allowlist;
2. validate arguments again at the trust boundary;
3. bind the request to a user, task, capability, and idempotency key;
4. require explicit approval for writes or other side effects;
5. enforce time, result-size, concurrency, filesystem, and network limits; and
6. append proposal, decision, execution, and result metadata to an audit log.

Keep `tool_choice` on `auto` for the first read-only pilot. Do not enable
parallel side-effecting calls. A `tool_use` block is a proposal that the
deterministic Soll gate may execute or reject; it is never executable content
by itself.

### 2. Evals gate every prompt, model, schema, and policy change

Build a versioned fixture set that mirrors real Soll traffic and its edge
cases. Start with at least 30 non-sensitive cases across Russian source
summarization, structured task extraction, conflicting/stale context, safe
read-only tool selection, and explicit rejection of unapproved writes.

Prefer code-based grading for exact JSON, required evidence, tool name and
argument matching, and forbidden-side-effect counts. Use blinded human review
for ambiguous quality. An LLM grader may supplement those checks only after its
rubric is validated against human labels; it must not be the sole safety
grader.

Record per case: task success, critical-constraint pass, schema validity,
evidence correctness, tool exact match, unsafe proposal/execution count,
latency, provider errors, retries, input/output/cache tokens, and USD per
successful task. Re-run the same suite for prompt, schema, context policy, and
model changes.

### 3. Context is a curated server resource

Keep canonical Soll task and source state outside the model transcript. Build
each request from the minimum authorized snapshot, label provenance, and count
tokens against the exact target model before sending. Stable instructions and
tool definitions are prompt-cache candidates; user-specific or rapidly
changing material is not. Track cache creation/read tokens and invalidate a
prefix deliberately when policy or tool definitions change.

For long conversations, prefer server-side compaction before selective context
editing. Preserve a structured summary, unresolved decisions, evidence refs,
and trust labels, then verify them after compaction. Context editing is a beta
surface and must stay behind a server feature flag with a pinned beta header
and regression fixtures. Never treat a generated summary or memory item as a
new grant of authority. Token-counting estimates are planning inputs; record
actual response usage for billing and limit control.

### 4. Rate limits require admission control

Messages limits are expressed as RPM, input tokens per minute (ITPM), and
output tokens per minute (OTPM), use token-bucket behavior, and can be hit by a
short burst. A `429` response includes `retry-after`; the gateway must honor it
with bounded exponential backoff and jitter instead of immediate retries.

Use a bounded per-workspace queue, concurrency caps, request deadlines, and
idempotency so retries cannot duplicate a write. Ramp new traffic gradually to
avoid acceleration limits. Read configured organization/workspace limits from
the server-side Rate Limits Admin API when available rather than hardcoding a
documentation snapshot. Cached-input accounting differs by model, so capacity
logic must use response fields and the resolved model profile, not one global
assumption.

### 5. Cost monitoring closes the quality loop

Capture response usage next to the normalized request ID, workspace, feature,
resolved model/profile, cache status, latency, result class, retry count, and
eval outcome. Do not log prompt bodies, credentials, or hidden reasoning.
Aggregate per-task estimates immediately and reconcile them with the Usage &
Cost Admin API, which requires a separate Admin API key and therefore belongs
only in a protected server job.

The primary decision metric is **USD per successful task**, not raw token price:

```text
(input + cache-write + cache-read + output + server-tool costs)
/ successful tasks
```

Set server-owned daily/monthly budgets and alerts at 50%, 75%, and 90% of the
approved budget. At 100%, stop non-essential experiments rather than silently
falling through to an unbounded model. Break down cost by workspace, feature,
model, cache class, and success/failure so cheaper but lower-quality routing
cannot look like a win.

## Promotion contract

| Area | Evidence required before any production canary |
| --- | --- |
| Tool schemas | 100% schema-valid required calls; 100% allowlist enforcement; 0 unauthorized executions; proposal/execution audit correlation on every case |
| Evals | At least 30 Soll-shaped cases; all critical constraints pass; 0 unsafe side effects; human-approved per-case diff |
| Context | Token budget respected in every fixture; provenance and unresolved decisions survive compaction; no authority elevation; cache invalidation test passes |
| Rate limits | Synthetic RPM/ITPM/OTPM exhaustion honors `retry-after`; bounded queue and retry budget; 0 duplicate side effects; graceful user-visible failure |
| Cost | 100% requests carry usage attribution; daily provider reconciliation is within 1%; 50/75/90/100% budget actions fire; USD/success is reported beside quality |
| Runtime boundary | 0 provider keys or SDKs in Android; one provider-neutral server adapter; rollback and deterministic fallback proven |

Meeting the table permits only a small, read-only server canary. It does not
permit autonomous shell or computer use. Those require the additional sandbox,
default-deny allowlist, explicit side-effect approval, isolated credentials and
network, and out-of-band append-only audit evidence stated in the KB decision.

## Primary documentation reviewed

- <https://platform.claude.com/docs/en/home>
- <https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools>
- <https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use>
- <https://platform.claude.com/docs/en/test-and-evaluate/develop-tests>
- <https://platform.claude.com/docs/en/build-with-claude/token-counting>
- <https://platform.claude.com/docs/en/build-with-claude/prompt-caching>
- <https://platform.claude.com/docs/en/build-with-claude/context-editing>
- <https://platform.claude.com/docs/en/api/rate-limits>
- <https://platform.claude.com/docs/en/manage-claude/rate-limits-api>
- <https://platform.claude.com/docs/en/manage-claude/usage-cost-api>

The task-named raw capture
`raw/monitored\\anthropic-docs\\20260713-085515-claude-platform-overview-and-getting-started-9e24bb80.md`
is not present in this isolated worktree. It was treated as untrusted discovery
metadata; the claims above were checked read-only against the official Claude
Platform documentation on the review date.
