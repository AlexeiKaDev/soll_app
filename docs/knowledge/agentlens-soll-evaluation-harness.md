---
title: AgentLens deep dive and safe Soll trajectory evaluation harness
task_id: 9ee7bcc7152c404688d51155fd980765
source_ref: source-item/9011e13c06d6/d43b336ae9b8c696
review_status: ci_only_contract_ready
reviewed_upstream_revision: 54dff743a2a2fa06090827bd5548e00d51984c65
---

# AgentLens deep dive and safe Soll evaluation harness

## Decision

AgentLens is useful to Soll as a trajectory-review vocabulary and dump model,
but its released collector and LLM evaluator are not safe defaults for this
repository. The upstream collector opens task repositories, force-checks out a
configured branch, hard-resets it to a pinned revision, runs an agent and may
connect MCP services. Its full CI workflow also needs provider, GitHub and
dataset secrets. Importing that pipeline would violate the requirement to avoid
ambient secrets and never auto-run on an unrelated repository.

The accepted Soll slice is therefore deliberately smaller:

- document the upstream metrics and exact dump seams;
- define one normalized, redacted, machine-readable trajectory contract;
- score one embedded synthetic trajectory with deterministic JVM assertions;
- run only when a trusted operator or trusted CI explicitly invokes the focused
  test;
- do not add a GitHub Actions trigger, collector, agent adapter or LLM judge.

The contract is
`docs/knowledge/agentlens-soll-ci-harness-v1.json`. It is not an agent runner.

## Sources and scope

Read-only upstream evidence was reviewed on 2026-07-19:

- paper record: <https://huggingface.co/papers/2607.06624>;
- repository: <https://github.com/agent-lens/agent-lens-bench>;
- repository tree revision:
  `54dff743a2a2fa06090827bd5548e00d51984c65`;
- metric registry: `agent_lens/eval/metrics/tag_to_metrics.py`;
- dump reader/model code: `agent_lens/eval/data_framework/`,
  `idea-plugin/benchmark-common/.../schema/` and
  `AbstractAgentBenchmarkRunner.kt`;
- external-agent chat normalization:
  `agent_lens/agent_server/server/serializable_chat.py`;
- Quality Index: `agent_lens/eval/reporting/quality_index.py`;
- full CI: `.github/workflows/run-benchmark.yml`.

The raw path named by the task,
`raw/monitored\hugging-face-daily-papers\20260709-230009-agentlens-production-assessed-trajectory-reviews-71feaca6.md`,
is absent from this isolated worktree. The supplied public paper and repository
were used read-only. No upstream code, data, runner, dependency or secret file
was copied into Soll.

## Metric inventory

### LLM-written review metrics

The registry contains nine distinct judge metrics. Each single-run judge emits
a score plus a written evidence review; the registered classes also support
side-by-side scoring where applicable.

| Tag | Metrics |
| --- | --- |
| `general` | `Pitfalls`, `Pleasantness`, `ToolCalls` |
| `workflows` | `EndResult`, `InstructionCompliance`, `Pitfalls`, `Pleasantness`, `ToolCalls` |
| `tests` | `Pitfalls`, `Pleasantness`, `RelianceOnMocking`, `TestMaintainability`, `TestSemanticCoverage`, `TestUsefulness`, `ToolCalls` |

The five released workflow metrics cover the end state, compliance with every
user instruction, fixable failure patterns, interaction quality and tool choice,
arguments, failures, recovery and efficiency. The four testing-only additions
cover mocking discipline, maintainability, semantic coverage and developer
usefulness. Single-run scores use the discrete scale `0`, `0.5`, `1`; pairwise
reviews use their own comparison scale.

### Formal verification

Formal checks are scenario-configured and contribute a per-trajectory
`formal_verification_result` of `1` only when every verifier succeeds. The
released verifier families cover:

- repository-state changes (`NoChangesVerifier`, `YesChangesVerifier`);
- exact file, new-file and file-regex checks;
- chat and chat-or-tool regex counts;
- IDE file-error checks;
- build-system task execution;
- Java/Python test execution, with test count/pass/coverage metrics where the
  verifier supplies them.

These checks are objective but upstream runs them inside the collector against
the task repository. The Soll CI-only slice consumes already-declared synthetic
check results and never executes repository commands from a dump.

### Operational telemetry and Quality Index

AgentLens reports termination reasons, cost and latency distributions, input,
generation and cache-hit tokens, generation-token/second ratio, total and
parallel tool calls, tool calls per chat, per-tool success rates, chat/scenario
counts, formal-verification success rate and—for testing scenarios—runnable test
classes, passed-test fraction and coverage.

For the released workflows fold, Quality Index combines six `[0,1]` values:
formal verification plus `EndResult`, `InstructionCompliance`, `Pitfalls`,
`Pleasantness` and `ToolCalls`, then scales their sum to `0..100`. Missing or
unparseable values become zero with warnings; formal verification below `0.3`
also produces a warning.

## AgentLens trajectory dump format

The dump is a directory tree, not one portable JSON record.

```text
<dump-root>/
  summary*.json
  <scenario>-<persona>-<random>/
    simulated_user_000.json
    simulated_user_000.txt
    simulated_user_001.json
    ...
    agent_chat_dump.json
    tool_calls_dump.json
```

The evaluator prefers `summary_merged.json`; otherwise it recursively selects
the largest `summary*.json` file. The root object has `timestamp`, `run_info`
and `projects_results`. `run_info` carries timestamp/experiment, dataset and
config hashes, plugin hash, model/provider and optional model URL. Each project
contains a title and result list.

A successful result includes scenario/persona identity and `message_path`,
errors, task description, binary formal result, termination reason, tags,
individual verifier results, an `agent_trace_prompt` containing final-state
trace context, per-turn cost/time/token/cache/tool telemetry, message counts and
total interaction time. A failure result keeps scenario/persona/path/errors and
the failure reason.

Each `simulated_user_NNN.json` contains:

- `role: simulator`;
- `request`: the full role/content message list sent to the simulator;
- `response`: simulator role/content;
- `response_price` and `response_time`.

The adjacent text file is a readable mirror; the evaluator reads the JSON files
whose names contain `simulated_user`.

`agent_chat_dump.json` is supplied by the selected agent engine. The bundled
external-agent adapter normalizes it to `chatUuid`, `agentConfig`, `messages`.
Messages are `UserMessage` (`uuid`, `createdAt`, `prompt`, attachments),
`AgentTurn` (`uuid`, `createdAt`, response, reasoning, tool calls, properties)
or `ToolMessage` (tool call and response). Other IDE-native engines may retain
provider-specific detail, so this file must be treated as untrusted and
potentially sensitive input.

`tool_calls_dump.json` is an object keyed by assistant-message index. Each value
is a list of calls with `name`, serialized `arguments`, `success`,
`response_content` and `system_reminder`. During dataset construction the
evaluator joins the summary point with all simulator calls, agent chat and tool
calls as `all_llm_calls`, `agent_chat_dump` and `tool_calls_dump`.

## Minimal Soll normalized dump

The Soll contract does not accept an arbitrary upstream dump directly. An
explicit, separately approved local collector would first normalize and redact
one authorized trajectory into these sections:

| Section | Required meaning |
| --- | --- |
| `repository` | stable local id, explicit authorization, synthetic/local origin and `foreign_repository=false` |
| `task` | task id, instruction digest and explicit requirement ids |
| `events` | ordered assistant/tool/final events; tools include success and optional `recovery_of` |
| `requirement_checks` | deterministic pass plus evidence refs for every requirement |
| `formal_checks` | already-produced check result and evidence; never a command to execute |
| `outcome` | terminal status and refs to requirement/formal evidence |
| `safety` | zero-count telemetry for network, secret reads, external writes, repository mutations and auto-repository actions |
| `redaction` | raw reasoning and raw tool output excluded; only digests and redacted evidence summaries allowed |

The schema intentionally excludes executable commands, checkout URLs,
credentials, environment-variable names/values and raw chain-of-thought. Event
references must resolve within the same dump. A failed tool call is not itself a
failed trajectory when a later successful event explicitly references it via
`recovery_of`; this makes recovery measurable without hiding the failure.

## CI-only evaluator

`AgentLensSollEvaluationHarnessTest` is the minimal executable variant. It reads
only the versioned JSON contract, documentation and verification artifact. Its
embedded synthetic trajectory contains one failed focused test followed by a
successful bounded retry, so recovery is exercised rather than merely declared.

The deterministic metrics are:

1. `schema_valid`;
2. `requirement_completion_rate`;
3. `formal_verification_rate`;
4. `tool_success_rate`;
5. `recovered_failure_rate`;
6. `final_evidence_coverage`;
7. `unsafe_effect_count`;
8. `qualitative_judge_metrics_scored`.

The smoke gate requires valid schema, all requirements and formal checks passed,
at least half of tool calls successful, every failed tool call recovered, full
final evidence coverage and zero unsafe effects. Qualitative judge metrics are
explicitly `0`/not scored: deterministic proxies must not be mislabeled as
AgentLens LLM reviews.

Trusted local or CI invocation:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.AgentLensSollEvaluationHarnessTest" --console=plain
```

No workflow file is added. In particular there is no `pull_request`, scheduled,
repository-discovery or foreign-checkout trigger. A future workflow must remain
manual/trusted-branch only, reject forks before checkout or dump parsing, disable
credentials and network, and call only this validator. Enabling collection,
repository tools or a local/remote judge is a separate reviewed task.

## Value and limits

The source produced one auditable deep dive, one versioned safe dump contract,
one synthetic recovery trajectory, eight deterministic metrics and one focused
CI-only contract test. Agent/model evaluations completed: **0**. Foreign
repositories opened, cloned, checked out or reset: **0**. Provider/MCP/network
calls made by the harness: **0**. Production or Android runtime changes: **0**.

This is measurable design and validation value, not evidence that one agent or
model is better than another. Such a claim requires a separately approved,
redacted local collection and review protocol.
