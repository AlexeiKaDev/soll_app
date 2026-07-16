---
task_id: 453ce98586194507be0261dcd0a7d6d0
source_ref: source-item/5d8b23e3c9e6/19472db6d6f483d5
source_item: lillog-building-agents-with-llm-297f7b2c
source_processing_result: architecture_audit_completed_server_first_agent_integration_feasible
verification_artifact: Soll/outputs/source-processing/source-item-5d8b23e3c9e6-19472db6d6f483d5-verification.md
source_value: "6 architecture blocks mapped; 3 requested integrations assessed; 1 bounded server-first path defined; 0 runtime or external side effects"
verified_at: 2026-07-15 Europe/Chisinau
---

# LilLog LLM-agent architecture audit for `soll_app`

## Outcome

The LilLog agent pattern is **conditionally feasible** for Soll, but the LLM
controller, semantic/project memory, goal scheduler and external API adapters
belong in the Soll desktop/server runtime. `soll_app` already has useful mobile
primitives for safe context submission, capability filtering, explicit
approval, durable local jobs, audit events and user-controlled memory. It
should remain the approval, observability and narrowly scoped device-execution
client rather than become a recursive autonomous agent runtime.

The safest integration is additive: a server-produced, versioned plan envelope
is shown on Android; each proposed device action is rechecked locally and is
executed by a typed handler only after the required user approval. Memory and
tool output provide context and evidence, never execution authority. No
planner, dependency, API integration, credential path, background loop or
production behavior was added by this review-only task.

## Source and verification boundary

The task-referenced raw file
`raw/monitored\lillog\20260702-195135-building-agents-with-llm-large-language-model-as-297f7b2c.md`
is absent from this isolated worktree (and is also absent under the nested
`Soll/raw` location). No network retrieval was performed because the task
allows proposal/import work only and does not approve an external integration.

The audit therefore uses the source signal supplied with the task and the
article's stated architecture frame: an LLM as controller, planning and
reflection, short- and long-term memory, and external tools/APIs. References to
AutoGPT, GPT-Engineer and BabyAGI in the task are treated as examples, not as
dependencies or architectures to copy. This document evaluates integration
against repository code; it does not claim an exhaustive reproduction of the
missing article text.

## Six-block architecture mapping

### 1. Controller and orchestration

**LilLog role.** The model interprets a goal, selects the next reasoning or
tool step and incorporates observations into later decisions.

**Current Soll evidence.** `MetaCoordinatorRequest` sends a safe user summary,
non-private context and the locally allowed capability set through
`SollGateway.askMetaCoordinator()`. `MetaCoordinatorServerBridge` turns that
request into the existing `/api/v1/assistant/ask` call and records a decision
chain. Complex reasoning is therefore already placed behind the Soll server
boundary rather than embedded in Android.

**Gap.** Current state: answer-only. `fromAssistantAnswer()` always returns an
empty `suggestedActions` list, so Android receives prose, not a machine-readable
plan. There is no plan identifier, version, dependency graph, step status,
stop condition, evidence contract, budget or replan rule.

**Decision.** Keep model orchestration server-side and add a versioned,
backward-compatible plan response only after its schema and safety gates pass
the promotion checks below. Do not parse free-form prose into executable
Android actions.

### 2. Planning and reflection

**LilLog role.** Planning decomposes the goal; reflection critiques results and
changes the next step when evidence disagrees with the plan.

**Current Soll evidence.** `ScenarioDetector` and `SuggestionEngine` implement
bounded deterministic suggestions, while task-board models preserve server
task status, acceptance criteria, test plans and execution metadata. These are
useful inputs and durable state, but they are not an agent planner.
`MetaCoordinatorActionGate` can validate a proposed action against the actual
capability decision and confirmation state.

**Gap.** No component persists an ordered plan or performs
observe/validate/replan. The same LLM answer is not separated from an
independent outcome validator. A successful handler return can describe a tool
operation, but it does not prove that a multi-step goal succeeded.

**Decision.** The first planner must be read-only: produce a reviewable plan
with expected evidence and no execution. Later reflection must compare typed
observations with deterministic step predicates. The model may propose a
replan, but cannot approve its own new capabilities, expand scope or mark an
external effect successful without an authoritative receipt.

### 3. Short-term and long-term memory

**LilLog role.** Short-term memory holds working context; long-term memory
retrieves relevant experience beyond the prompt window.

**Current Soll evidence.** Room-backed `AssistantMemory` stores explicit
categories, key, summary, source, confidence, timestamps and pinned state.
`AssistantMemoryRepository` writes accepted suggestions only when memory is
enabled; the Logs surface supports inspection, delete, clear and Markdown
export. `sendSummaryToSoll()` sends a sanitized summary through the existing
raw-note gateway and queues it when offline. `MetaCoordinatorRequest.safeForServer()`
removes context items marked private.

**Gap.** Local memory is currently a visible preference/event store, not agent
retrieval: `AskSollViewModel` sends only the client and entry-point fields and
does not query `AssistantMemoryRepository`. There is no relevance search,
session working set, retention/expiry policy, contradiction handling, source
trust label or record of which memories influenced a plan. Server project
memory is not exposed as a typed mobile retrieval contract.

**Decision.** Reuse local memory only for opt-in, read-only personalization and
assemble a small safe context summary before a request. Keep semantic/project
retrieval on the server. Every retrieved item must carry provenance, trust,
timestamp and memory ID; deletion must remove it from future retrieval.
Retrieved text can influence a proposal but can never grant a capability or
satisfy an approval.

### 4. Tools and external APIs

**LilLog role.** Tools connect model reasoning to current information and
actions that the model cannot perform itself.

**Current Soll evidence.** `SollApiService` and `SollRepository` provide typed
Retrofit/Moshi contracts for Soll health, chat, tasks, sources, raw notes,
assistant questions and other server features. `CapabilityRegistry`,
`CommandSafetyGate` and `MetaCoordinatorActionGate` enforce registered
capabilities, settings, Android permissions and confirmations. `ToolJobRunner`
persists queued/running/terminal states, progress, output and logs around typed
`ToolHandler` implementations.

**Gap.** There is no generalized server tool manifest or mapping from a plan
step to a typed Android handler. `WAITING_FOR_CONFIRMATION` exists in the job
model but the generic runner does not create that state. Existing Retrofit
calls are app features, not arbitrary model-callable APIs. A generic URL,
method, headers or raw payload supplied by a model would bypass the repository's
typed trust boundary.

**Decision.** External APIs are feasible only through allowlisted server-side
adapters with a typed input/output schema, short-lived server-held identity,
timeouts, rate and cost limits, idempotency key, retry policy, response-size
limit, provenance and sanitized error handling. Android must never receive API
credentials or execute an arbitrary URL. A device-local action must follow
plan proposal -> capability lookup -> permission/confirmation gate -> typed
handler -> persisted result. Unknown tools and schema versions fail closed.

### 5. Scheduling and execution lifecycle

**LilLog role.** A scheduler decides when a goal or next step is eligible,
resumes durable work and applies retry/termination budgets.

**Current Soll evidence.** `SollServerSyncScheduler` uses unique WorkManager
work; `SollServerSyncAlarmScheduler` and the foreground sync service keep chat
and task status refreshed within Android lifecycle limits. `ToolJobRunner` and
the sync queue preserve execution state and offline retry data.

**Gap.** These components schedule synchronization or a single known handler,
not goals or plan dependencies. WorkManager and AlarmManager are subject to
Android lifecycle/Doze constraints and are the wrong authority for an
open-ended LLM loop. There is no plan lease, maximum-step/time/cost budget,
dependency readiness check, crash reconciliation or loop detector for agent
plans.

**Decision.** Put the goal/plan scheduler on the server with durable leases,
bounded retries and explicit terminal states. Keep Android scheduling limited
to sync, status/approval notifications and an already-approved, typed local
operation when its lifecycle contract allows it. Never use a periodic Android
worker to wake an autonomous reasoning loop.

### 6. Observation, safety and evaluation

**LilLog role.** Tool observations feed later reasoning; reflection and
evaluation determine whether the goal is complete or another plan is needed.

**Current Soll evidence.** Assistant events, decision-chain Markdown, ToolJob
progress/terminal status, task execution fields and notifications provide a
strong audit base. The local gates are deterministic and remain outside model
control.

**Gap.** Observations are not tied to `plan_id/step_id`, expected evidence or a
goal-level success predicate. Tool output, monitored-source text and memory do
not yet carry a uniform trust/provenance label. There are no plan metrics for
retries, churn, denied calls, stale memory, human interventions or budget use.

**Decision.** Add append-only plan/step events with redacted input summary,
capability decision, approval reference, tool receipt, evidence verdict and
stop reason. Treat all model, memory and external-tool text as untrusted data.
Use deterministic policy for capability/approval/budget enforcement and a
separate verifier for completion; never use model reflection as the safety
gate.

## Feasibility assessment for the requested integrations

| Requested integration | Current readiness | Feasibility | Placement and decision |
| --- | --- | --- | --- |
| Planner | safe server question bridge and action-gate models exist; structured plan/execution loop does not | **Medium, conditional** | Add a server-side read-only plan envelope first. Android renders and approves; it does not infer actions from prose. |
| Memory | explicit, inspectable local memory and safe manual summary sync exist; retrieval and provenance are incomplete | **High for opt-in local preference context; medium for agent memory** | Keep small user-approved context on device; keep semantic/project memory and retrieval server-side. Memory never grants authority. |
| External API calls | typed Soll/Telegram transports and local capability/tool gates exist; no general tool adapter contract exists | **High through typed server adapters; rejected as arbitrary Android calls** | Server owns allowlist, credentials, budgets and API receipts. Android executes only registered device handlers after local gates. |
| Goal scheduler (supporting dependency) | Android sync scheduler and persistent jobs exist; no plan scheduler exists | **Medium on server; low/rejected as an Android autonomous loop** | Server owns leases, dependency checks and replan budgets. Android schedules sync/notifications only. |

The three requested capabilities can therefore integrate without an Android
architecture rewrite, but only with a server-first action-plan contract and
the existing mobile safety boundaries preserved.

## Proposed additive contract

A future server response should use a versioned structure equivalent to:

```text
PlanEnvelope
  schema_version, plan_id, plan_version, goal_summary
  created_at, expires_at, status
  max_steps, max_replans, time_budget_ms, cost_budget
  memory_refs[]: memory_id, source, trust, retrieved_at
  steps[]:
    step_id, title, capability_id, tool_id, dependencies[]
    input_summary, effect_class, approval_required
    idempotency_key, expected_evidence, stop_condition
```

Raw credentials, arbitrary URLs/headers, private memory payloads and executable
free-form text are forbidden fields. The client must reject an unknown schema,
expired plan, missing capability, changed effect class, missing approval or
reused/absent idempotency key for an external effect.

## Reference flow

```text
User -> Ask Soll safe summary + allowed capabilities
     -> server controller retrieves provenance-labelled memory
     -> server returns read-only PlanEnvelope
     -> Android displays goal, steps, evidence and risks
     -> user approves the bounded step/lease where required
     -> local capability + permission + confirmation gates run again
     -> typed Android handler OR allowlisted server API adapter executes
     -> receipt/observation is persisted against plan_id + step_id
     -> deterministic verifier accepts, stops or requests a bounded replan
```

External API adapters and provider credentials stay on the server branch of
this flow. Android receives only summaries, approval requests, status and
redacted receipts.

## Smallest safe delivery sequence

1. **Read-only plan contract.** Add an additive server plan endpoint/response
   with schema validation and no executor. Render it in the existing Ask Soll
   or task/action surface and record decision-chain evidence.
2. **Opt-in memory context.** Retrieve a small, inspectable set of safe local
   memory summaries plus server-side project memories; show why each item was
   selected and honor deletion immediately.
3. **One read-only typed adapter.** Trial a server-held, allowlisted API adapter
   with fixed schema and no external mutation. Compare plan evidence with the
   existing answer-only path.
4. **One approval-gated effect.** Only after the read-only trial passes, map one
   named capability to one handler with an approval lease, idempotency and an
   authoritative receipt. Do not add a generic HTTP or shell tool.
5. **Bounded server scheduler.** Add durable plan leases, dependency checks,
   crash recovery and stop budgets only after manual single-plan execution is
   reliable. Android remains a status/approval client.

Each stage is a separate implementation task and must preserve the existing
`/api/v1/assistant/ask`, task, chat and device contracts for older clients.

## Promotion and rejection gates

Promote beyond read-only planning only when a synthetic, non-sensitive audit
proves all of the following:

1. unknown plan schema, capability and tool IDs are blocked in 100% of cases;
2. external/device effects without the required approval remain exactly `0`;
3. every attempted step has `plan_id`, `step_id`, policy decision and a redacted
   terminal receipt;
4. duplicate delivery of an effect step produces at most one authoritative
   effect through idempotency/reconciliation;
5. plan limits terminate step, retry, replan, time and cost exhaustion;
6. private/deleted memory is absent from later requests and retrieved memories
   expose provenance plus trust;
7. injected instructions in memory or tool output cannot add capabilities,
   bypass approval or change budgets;
8. the read-only plan improves a named task success/review metric over the
   current answer-only baseline without unacceptable latency or intervention
   cost.

Reject or defer any design that puts provider/tool credentials on Android,
accepts arbitrary model-generated HTTP/shell input, hides memory from the user,
lets memory/tool text grant authority, runs recursive background reasoning on
Android, or treats LLM self-reflection as proof of completion.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Architectural block analysis | controller, planning, memory, tools/APIs, scheduling and observation mapped to real code | PASS |
| Requested feasibility | planner, memory and external API calls receive explicit placement and decision | PASS |
| Current gaps are concrete | answer-only bridge, no memory retrieval, no plan scheduler and no generalized safe adapter identified | PASS |
| Integration path is bounded | five additive stages and eight measurable promotion gates | PASS |
| Mobile safety boundary is preserved | Android remains approval/observability plus typed device execution | PASS |
| Review respects approval scope | documentation/test/artifact only; no external call or runtime mutation | PASS |
| Value metrics are attached | result, artifact path and quantified source value present | PASS |

`LilLogAgentArchitectureSourceTriageTest` verifies the six-block mapping, the
three requested feasibility decisions, the server-first boundary, the
promotion gates and the value-metric fields in this artifact.

## Value metric update

- `source_processing_result`:
  `architecture_audit_completed_server_first_agent_integration_feasible`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-5d8b23e3c9e6-19472db6d6f483d5-verification.md`
- `source_value`: `6` architectural blocks were mapped to current repository
  evidence, all `3` requested integrations were assessed, `1` bounded
  server-first delivery path with `5` stages and `8` promotion gates was
  defined, and runtime/external side effects remain `0`.
