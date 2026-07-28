---
task_id: 7968f28912d24cbca2164067de27810e
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/220db783f7ba
wiki_ref: wiki/habr-yandex-company-1.md
monitored_ref: monitored/habr-yandex-company/20260727-230008-600-000-c611fa02.md
source_status: public_primary_verified_local_snapshots_absent
status: server_first_design_guidance_no_runtime_plugin
---

# Yandex Tracker plugin platform: boundary for Soll app

## Decision

The Yandex Tracker case study is useful as a security and developer-experience
contract for a future Soll extension system. It is not evidence that dynamic
third-party code should run inside the Android application.

Any Soll extension pilot belongs in a disposable desktop/server worker behind
a versioned manifest, default-deny capabilities and egress, a typed broker,
explicit approval and append-only receipts. LLM-generated code is an untrusted
proposal that must pass the same policy and verification path as human-written
code. Android remains the review, approval, status and evidence client through
the existing Chat, Tasks, `Источники` and `Ask Soll` surfaces.

This task adds guidance and an executable documentation contract only. It adds
no plugin loader, WebView/JavaScript bridge, credential path, dependency,
permission, server call or production behavior.

## Source validation

The task-named `wiki/habr-yandex-company-1.md` and
`monitored/habr-yandex-company/20260727-230008-600-000-c611fa02.md` are absent
from this isolated worktree, including the nested `Soll/` locations, current
`HEAD` and reachable Git history. Their contents were not invented.

The public primary article was checked read-only:

- Yandex company blog on Habr:
  <https://habr.com/ru/companies/yandex/articles/1062416/>;
- title: **"Платформа плагинов в Трекере, или Как мы пустили сторонний код в
  продукт с 600 000 пользователей"**;
- the Yandex company feed, publication position and unique `600 000` title
  marker match the monitored filename token `600-000`.

Six source patterns were confirmed:

1. extension points are explicit UI slots, external-service triggers and
   custom calculations instead of arbitrary core changes;
2. third-party UI runs on an isolated, cookie-less host with a restricted
   iframe and default-deny network policy;
3. a manifest declares entry points, data permissions, UI permissions and
   external domains;
4. a Bridge brokers request/response communication and the service checks every
   API request against plugin permissions and the current user's rights;
5. external-service credentials remain in protected host storage and are not
   exposed directly to plugin code;
6. LLM-assisted authoring is supported with templates, `AGENTS.md`, JSDoc and
   OpenAPI-derived types, while a small runtime proxy handles calls.

The article is a product architecture case study. It does not provide a Soll
threat model, an Android-compatible sandbox, a reproducible security proof or a
measured Soll workflow result. Its adoption and scale claims therefore remain
source claims, not Soll metrics.

## Existing Soll seams audited

| Soll seam | Current evidence | Adaptation |
| --- | --- | --- |
| `CapabilityRegistry` and `CommandSafetyGate` | registered risk tiers, settings, Android permissions and confirmation are checked before typed commands run | reuse the fail-closed capability vocabulary; a server extension may request a subset but cannot grant itself a capability |
| `SollChatActionPolicyRegistry` | task, approval and ingest actions require an explicit user tap | show install, scope-diff, run and revoke proposals through the same explicit-action principle |
| `SollGateway.askMetaCoordinator()` and `askModelChat()` | LLM reasoning is backend-mediated | keep extension generation, validation and execution off Android; return only proposals, status and redacted evidence |
| `SollGateway`, Retrofit and Moshi contracts | server calls are declared as typed Kotlin methods and models | keep compile-time DTO/API contracts; do not copy the article's TypeScript `Proxy` or accept model-generated URL/method/header tuples |
| `SollGateway.listSources()` and task-board source cards | source evidence and follow-up tasks already reach Android | reuse these surfaces for extension provenance, review tasks, findings and approval requests |
| `ToolJobRunner`, assistant events and `auditRef` | known jobs and decisions have durable state and visible audit evidence | require every extension attempt to produce a policy decision and terminal receipt before Android represents it as complete |

These six seams are useful integration boundaries, not a hidden plugin system.
The current Android repository has no general plugin manifest, sandbox,
extension registry or safe arbitrary-code executor.

## Server-first extension contract

A future manifest must be versioned, immutable for one release and reviewable
as data:

```text
SollExtensionManifest
  schema_version, extension_id, version
  owner, source_ref, artifact_digest, signature_ref
  entrypoint_type, input_schema_ref, output_schema_ref
  capabilities[], data_scopes[], egress_domains[]
  effect_class, approval_policy
  timeout_ms, memory_mb, output_bytes
  created_at, expires_at
```

Seven controls are mandatory:

1. **Provenance.** Pin owner, source, version, digest and build/evaluation
   evidence; a title or LLM response is not an artifact identity.
2. **Least privilege.** Capabilities, data scopes and egress domains are
   allowlists. Effective access is the intersection of manifest, worker, user
   and environment grants.
3. **Isolation.** Run code in a disposable server worker with read-only inputs,
   resource budgets and default-deny filesystem/network access. Android does
   not host plugin JavaScript or an iframe/WebView sandbox.
4. **Typed broker.** Route calls through schema-checked adapters with request
   IDs, deadlines, response-size limits, idempotency and sanitized errors.
   Unknown tool, capability and schema versions fail closed.
5. **Secret separation.** Keep credentials in server-held scoped identities.
   Code receives a broker capability, never a raw token, cookie or arbitrary
   header.
6. **Approval and scope diff.** Require an explicit user/admin decision for
   install, upgrade, newly requested access and every effect class that policy
   marks risky. Approval is bounded by version, scope and expiry.
7. **Audit and recovery.** Record proposal, policy result, approval reference,
   redacted input, tool receipt, output digest and terminal state. Support
   revoke, kill switch and rollback to the prior approved version.

Untrusted source text, task descriptions, tool output and generated code remain
data. None may rewrite the manifest, expand authority, waive a gate or mark its
own effect successful.

## Smallest measurable pilot

The first pilot should be one read-only server extension that turns a fixed,
synthetic set of Soll source/task records into a Markdown review summary. It
must have no Android code execution, device capability, secret, external
egress, arbitrary URL, shell access or production write.

Compare the extension with the current deterministic/manual summary path on
three to five frozen fixtures. Record:

- output contract success and factual errors;
- relevant evidence retained and unsupported claims added;
- requested versus used capabilities/data scopes;
- blocked capability, filesystem and egress attempts;
- review time and human corrections;
- p50/p95 latency, peak memory and output size;
- audit/receipt completeness and reproducibility;
- build/test time and LLM/provider cost when code generation is used.

Seven promotion gates apply:

1. `100%` of unknown schemas, tools and capabilities are rejected;
2. unauthorized external, filesystem and device effects remain exactly `0`;
3. secrets and private production records exposed to code remain exactly `0`;
4. every attempt has a policy decision and terminal receipt;
5. duplicate requests produce one idempotent result and no duplicate effect;
6. upgrade, scope expansion, expiry and revoke paths fail closed and preserve
   the last approved version;
7. the pilot improves one named quality or review-time metric over the frozen
   baseline without unacceptable latency, cost or human correction.

Until a separate desktop/server task passes all seven gates, the measurable
runtime value is `0` and the Android public contract remains unchanged.
