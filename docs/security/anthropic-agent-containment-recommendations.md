---
title: Anthropic agent containment recommendations for Soll
task_id: a67b4eeefdc94eaaaad5a141a9976802
source_ref: source-item/e1174ebfd950/64af1d1a2fd48283
review_status: recommendations_ready
review_owner: Soll security team
scope: security review and implementation gates only
---

# Agent containment recommendations for Soll

## Security review decision

The security review recommends adopting environment-first containment for
Soll's desktop/server agent workers. Android must remain the approval,
observability and evidence client; it must not become a shell, general-purpose
agent runtime or credential holder.

This review does not authorize a production rollout. Each implementation phase
below needs its own task, threat-model review and explicit approval. The first
trial must use synthetic data, no ambient credentials and a read-only agent
capability.

## Source lessons applied

Anthropic's article, "How we contain Claude across products", separates three
threat sources—user misuse, model misbehavior and external attackers—and three
defense surfaces: the execution environment, the model and external content.
The central lesson is that probabilistic model controls cannot replace a hard
environment boundary.

The following findings are directly relevant to Soll:

1. Per-action approval is not a containment boundary and creates approval
   fatigue. A bounded environment can reduce prompt volume while limiting the
   worst possible effect.
2. Project files and local configuration are untrusted input until the workspace
   trust decision is complete. Parsing hooks or config before that decision
   crosses the boundary too early.
3. Filesystem isolation and denied egress work together. Keeping credentials
   outside the worker still matters when a malicious prompt appears to come
   from the user.
4. A domain allowlist is a capability grant, not just a destination filter.
   Allowed methods, paths, credential provenance and server-side fetch behavior
   must be constrained too.
5. Isolation can hide activity from host monitoring. Structured security events
   must leave the sandbox without giving the sandbox control over the audit
   channel.
6. Remote tools, monitored sources, repository text and tool results remain
   untrusted data even when the connector or transport itself is trusted.
7. Persistent memory can preserve an injection, and sub-agent output must not
   silently become more trusted than the raw content it summarizes.
8. Mature isolation primitives should carry the hard boundary; custom brokers
   and proxies need disproportionate review because they become the weakest
   layer.

Source reviewed: <https://www.anthropic.com/engineering/how-we-contain-claude>
(published 2026-05-25). The raw path named by the task,
`raw/monitored\anthropic-engineering\20260702-192923-how-we-contain-claude-across-products-f328ce89.md`,
is not present in this isolated worktree, so the official article was used as
the read-only source.

## Current Soll boundary

The Android repository already has useful model-layer and application-layer
controls:

- `CapabilityRegistry` assigns risk tiers, Android permissions, confirmation
  requirements and audit requirements to registered commands.
- `CommandSafetyGate` rejects disabled capabilities, missing permissions and
  unconfirmed risky commands before their handlers run.
- `MetaCoordinatorActionGate` rejects unknown/mismatched capabilities and
  requires confirmation for risky server suggestions.
- the roadmap keeps heavy reasoning on desktop/server and describes Android as
  an approval and observability client;
- the release app denies cleartext traffic, Android backup is disabled, and
  most components are not exported;
- gadget background execution is limited to selected read-only commands, while
  write commands use an explicit manual flow.

These are defense-in-depth controls, not proof of agent containment. This
worktree does not contain evidence that desktop/server agent workers have an
OS-enforced filesystem boundary, default-deny egress, per-job identities or an
out-of-sandbox security event stream. It also exposes one Android-side trust
gap worth treating as P0: chat action metadata accepts any non-blank action
type and sends it to the server after a tap, without a local action-type
allowlist or capability mapping. The server may reject unknown actions, but the
mobile boundary should fail closed independently.

## Implementation recommendations

### C1. Isolate every execution job (P0, Platform + Security)

Run tool-capable agents in a disposable OS-isolated worker created for one task.
Mount only that task's worktree, with read/write mode declared per mount. Do not
mount the host profile, other repositories, coordinator state, SSH material,
cloud configuration or Android signing assets. Deny network by default. Destroy
the worker and its scoped identity on terminal state.

Prefer a maintained VM/container/sandbox primitive that fits the host threat
model. A worktree or process working directory alone is not isolation.

### C2. Establish trust before config parsing (P0, Platform)

Resolve canonical paths and symlinks before checking them against mount and
write allowlists. Treat repository instructions, hooks, build scripts, local
listeners and project configuration as untrusted until the coordinator records
a workspace-trust decision. No pre-trust file may register or execute a hook.

### C3. Replace broad network access with an egress capability broker (P0, Backend + Security)

Represent egress grants as `{scheme, host, port, method, path pattern,
principal, expiry, max bytes}`. Default to no grant. Strip caller-supplied auth
headers, redirects and server-side-fetch headers unless the capability
explicitly needs them. Bind any required token to the task principal and
operation, not merely to an allowed domain.

The broker must reject DNS/IP rebinding, redirects outside the grant, private
address pivots and uploads to a different account through an otherwise allowed
API.

### C4. Give the worker its own short-lived identity (P0, Backend)

Keep user and service credentials outside the execution environment. Exchange a
coordinator-held identity for a short-lived, least-privilege job token that is
revocable independently, cannot mint broader credentials and is accepted only
for the declared task/action. Record the principal and policy version in every
effect receipt.

### C5. Keep external content separate from executable authority (P0, Backend + Android)

Mark monitored-source text, web/tool output, repository content, memory and
sub-agent summaries with provenance and trust level. They may propose an action
but must not create executable authority.

On Android, map chat actions to a closed local action allowlist and a known
`Capability`; reject unknown types before `executeChatAction`. Require a
server-issued one-time action record bound to the session, task, action type and
expiry. Source cards and digest payloads must not be able to inject an action
object. Risky actions still pass `MetaCoordinatorActionGate`-equivalent local
confirmation even if the server labels them safe.

### C6. Approve bounded capability leases, not noisy individual commands (P1, Product + Security)

An approval must show the action, target, data exposed, filesystem mounts,
egress grant, expiry and maximum effect. Approval creates a narrow lease and is
never a blanket "allow all". A broader or different effect requires a new
approval. Android must provide revoke/stop and show which lease a running job is
using.

### C7. Export containment telemetry out of band (P0, Platform + Security)

Emit append-only events for job creation/teardown, policy digest, identity,
mounts, egress decisions, denied paths, permission/approval decisions, tool
calls, effect receipts, cancellation and cleanup. Redact content and credentials
at the producer. The worker must not be able to suppress or rewrite accepted
events. Surface degraded/missing telemetry as a failed security gate, not a
healthy job.

### C8. Preserve trust across memory and multi-agent boundaries (P1, AI Core + Security)

Store source provenance with persistent memory and scan it again when a session
loads. A sub-agent response inherits the lowest trust of the inputs it used;
structured output does not promote it. The coordinator alone can translate a
proposal into an action record, after policy and capability checks.

### C9. Red-team the boundary and the custom glue (P0, Security)

Before enabling writes, test path traversal and symlink escapes, pre-trust
hooks, poisoned source/tool output, a user-pasted exfiltration prompt, approved
domain cross-account upload, redirects/DNS rebinding, forged Android action
metadata, stale/replayed action records, memory poisoning, sub-agent trust
escalation, audit suppression and worker cleanup after timeout/crash.

## Promotion gates and measurable evidence

The security team should reject a production capability unless a synthetic,
non-production trial proves all of these gates:

1. **Job boundary:** 100% of tool-capable jobs record a unique worker ID,
   principal, sandbox policy digest and teardown result.
2. **Secret isolation:** a canary scan finds zero host/profile credentials or
   unrelated repositories inside the worker.
3. **Filesystem containment:** 100% of the traversal/symlink corpus is blocked
   outside declared canonical mounts; allowed read/write modes still work.
4. **Egress containment:** 100% of network attempts are denied or matched to a
   method/path/principal/expiry grant; cross-account upload and redirect tests
   are blocked.
5. **Content/action separation:** 100% of unknown, source-injected, forged,
   expired and replayed Android action records fail closed locally and on the
   server.
6. **Audit visibility:** every allowed or blocked effect has a correlated
   out-of-sandbox event and effect receipt; deliberate audit loss makes the job
   unhealthy.
7. **Failure cleanup:** timeout, crash and cancellation revoke the job identity,
   stop execution, remove ephemeral state and leave other worktrees unchanged.

## Delivery order

1. Define the worker policy, action-record and audit-event contracts plus the
   adversarial test corpus. No execution changes yet.
2. Run one disposable read-only worker against synthetic files with no network
   or credentials and prove gates 1, 2, 3, 6 and 7.
3. Add one read-only egress grant to a fake service and prove gate 4, including
   malicious redirects and cross-account credentials.
4. Add the closed Android chat-action mapping and one-time server record, then
   prove gate 5.
5. Only after independent security review, consider a narrowly scoped write
   capability with rollback and a named blast-radius budget.

## Review outcome

The source is actionable for Soll, but its value is a concrete containment
backlog and measurable gates—not a claim that production agents are already
isolated. This review produces 9 implementation recommendations and 7 promotion
gates. Production containment changes delivered by this task: **0**.
