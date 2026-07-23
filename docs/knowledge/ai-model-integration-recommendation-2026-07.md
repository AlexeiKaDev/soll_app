# AI model integration recommendation for Soll

Analysis date: 2026-07-23 (Europe/Chisinau).

Task: `e4bc6efce56249899f1d74a8ebae5788`,
`source_ref=source-item/37d75cbacc7c/741cfe0e55f8bad5`.

## Decision

Do not add GPT, Claude, Gemini, Llama, Mistral, DeepSeek, Qwen, or MCP
dependencies to the Android app. Keep the current backend-mediated
`ModelChatRequest.safeForServer()` -> `SollGateway.askModelChat(...)` path and
evaluate model candidates behind a provider-neutral server router.

The monitored roundup is useful as a discovery signal, but it is not a safe
model-selection benchmark. Several named releases have already moved in their
official lifecycle: OpenAI describes GPT-5 as a previous model and recommends
GPT-5.6; Anthropic has retired Claude Sonnet 4 from its first-party API; Google
still lists `gemini-2.5-pro` as a stable model. A production route must therefore
pin an exact model ID and resolve lifecycle, capabilities, context, and price
from a server-owned catalog instead of compiling marketing family names into
the APK.

Recommended next integration slice: a **server-only, approval-gated offline
pilot** with three capability tiers and one local/private challenger. No model
is promoted until the same Soll-shaped fixtures show a measurable quality,
safety, latency, and cost improvement over the current backend route.

## Source boundary

The task-named raw artifact
`raw/monitored/aia/20260622-173018-ai-model-releases-benchmarks-465a8c96.md`
is not vendored in this isolated worktree. The task title, summary, and family
list are treated as untrusted discovery metadata, not as proof of model
parameters, prices, or benchmark superiority. The facts below were checked
against current official model pages, pricing pages, and model cards.

Prices are a 2026-07-23 snapshot in USD per one million tokens for standard
first-party service unless stated otherwise. They are planning inputs, not a
contract; the future server catalog must refresh them before a paid canary.
Parameter counts marked `undisclosed` were not published on the cited official
model/API page and must not be guessed.

## Model-family comparison

| Family / representative | Parameters | Context and modalities | Price snapshot | Soll interpretation |
| --- | --- | --- | --- | --- |
| OpenAI `gpt-5` | undisclosed | 400k context; text/image input, text output; structured output, functions, web/file search and MCP | $1.25 input / $10 output | Source-era baseline only. Officially a previous model; use the current stable GPT tier through Responses in a server adapter and pin its resolved snapshot. |
| Anthropic Claude Sonnet 4 | undisclosed | 200k context after retirement of its old 1M beta; text/image input, text output and tools | $3 input / $15 output on the current pricing table | Do not start a new first-party integration on this retired ID. If Anthropic enters the pilot, use a current pinned model such as Sonnet 4.6/5 and re-read its capability/retention contract. |
| Google `gemini-2.5-pro` | undisclosed | 1,048,576 input / 65,536 output; audio, image, video, text and PDF input; text output; functions, structured output, search and code execution | up to 200k: $1.25 input / $10 output; above 200k: $2.50 / $15 | Strong long-context/vision challenger, but multimodal support belongs in a server DTO; the current Android model-chat bridge is text-only. |
| Meta Llama 4 Scout | 109B total / 17B active | 10M context; multilingual text and image input; text/code output | no first-party token tariff; self-hosting/inference-provider cost | The model is far beyond an APK runtime and uses the Llama 4 Community License. Consider only a separately sized server benchmark; do not infer low memory from the 17B active count. |
| Mistral Small 4 | 119B total / 6.5B active | 256k context; hybrid instruct/reasoning/coding, function calling and structured outputs | $0.15 input / $0.60 output | Best documented low-cost server challenger in this comparison. Keep API and self-hosted deployment as separate cost/security profiles. |
| DeepSeek V4 Flash | undisclosed on current API page | 1M context / up to 384k output; thinking/non-thinking, JSON and tool calls | cache miss $0.14 input / $0.28 output | Low-price challenger only after data-governance, availability, error-shape, and tool-call tests. The older open-weight V3 card reports 671B total / 37B active and 128k context; those figures must not be assigned to V4. |
| Qwen3-30B-A3B | 30.5B total / 3.3B active | 32,768 native, validated to 131,072 with YaRN; text, thinking/non-thinking and agent/tool use | open weights: infrastructure cost; Qwen Cloud `qwen3.6-flash` is $0.25 / $1.50 up to 256k and $1 / $4 above, with 1M context | Most plausible local/private challenger, but only on desktop/server hardware. Treat native and YaRN context as different profiles and benchmark Russian quality at the chosen quantization. |

## Benchmark interpretation

The public benchmark tables do not establish a Soll winner. They mix different
snapshots, prompts, shot counts, reasoning budgets, tool access, context sizes,
languages, safety policies, and sometimes vendor-reported results. A parameter
count is also not a quality score: MoE total and active parameters describe
different resource dimensions.

Use public benchmarks only to shortlist capabilities. Selection must be based
on a fixed Soll evaluation set with identical inputs and acceptance checks:

1. Russian task/source summarization with required evidence references.
2. Structured task extraction with an exact JSON Schema and no missing fields.
3. Latest-value retrieval after conflicting task, source, and preference updates.
4. Read-only tool selection plus explicit rejection of unapproved writes.
5. Repository implementation-worker cases scored by compile/test `pass@1`.
6. Screenshot/document understanding for candidates that claim vision support.

Each candidate runs at least 20 deterministic or reviewable cases per relevant
profile. Record these 17 metrics: task success, critical-constraint pass rate,
schema-valid rate, citation correctness, stale-value recall, tool-call exact
match, unsafe side effects, p50 latency, p95 latency, time to first token, input
tokens, output tokens, cached-input tokens, provider errors, retries, fallbacks,
and **USD per successful task**:

```text
(input_tokens * input_rate + cached_tokens * cache_rate +
 output_tokens * output_rate + tool_fees) / 1_000_000 / successful_tasks
```

Vendor benchmark rank is not a promotion gate. Required promotion gates are:

- all critical safety and approval assertions pass;
- `unsafe_side_effect_count = 0`;
- structured output is valid on 100% of required-schema cases;
- task success is no worse than the current baseline and improves the named
  target metric by at least 10% or lowers USD per successful task by at least
  25% without a quality regression;
- p95 latency and provider error/fallback rates stay within explicit budgets;
- a human reviews the per-case diffs and approves the pinned model/profile.

## Soll integration shape

### Android contract remains unchanged

The current repository already has the right boundary:

1. `ModelChatProviderHint` exposes only `AUTO` and `LLAMA`.
2. `ModelChatRequest.safeForServer()` removes private messages and bounds the
   request.
3. `ModelChatServerBridge` says provider keys stay server-side.
4. `SollGateway.askModelChat(...)` is provider-neutral.
5. `SollRepository.askModelChat(...)` sends the normalized request to the Soll
   backend.
6. `SollApiService` owns the existing `POST /api/v1/chat/turn` route.
7. The Gradle graph has no model-provider SDK or native LLM runtime.

Do not expand the Android enum with one entry per vendor. If user-visible
diagnostics become valuable, the server may return normalized `provider`,
`resolved_model`, `profile`, and `fallback_used` metadata through a separately
versioned backward-compatible response.

### Server-owned capability catalog

A future server task should introduce a provider-neutral catalog similar to:

```text
ModelProfile(
  provider, modelId, revision, lifecycle,
  inputModalities, outputModalities,
  contextTokens, maxOutputTokens,
  structuredOutput, tools, mcp,
  inputUsdPerM, cachedInputUsdPerM, outputUsdPerM,
  dataPolicy, deployment, enabled
)
```

Routing profiles should express workload intent rather than vendor names:

- `fast`: routine chat, extraction, and source classification;
- `balanced`: default Russian assistant and task/source synthesis;
- `deep`: complex planning, implementation, and multi-tool reasoning;
- `vision`: screenshot/PDF/image analysis;
- `local_private`: sensitive or offline-compatible server workloads.

Start with shadow/offline evaluation. Then canary one server provider at a time
with a deterministic fallback to the existing route. Log normalized usage,
latency, finish/error class, resolved model and route reason, but never prompt
text, credentials, or hidden reasoning.

### MCP boundary

MCP is a tool interoperability layer, not a model benchmark and not proof that
provider APIs are wire-compatible. Keep MCP clients and allowlisted tool
servers in the server/meta-coordinator boundary. The model may propose a tool
call, but deterministic Soll policy must validate capability, arguments,
approval, timeout, retries, result size, and audit metadata before execution.
Android continues to receive normalized results and approval tasks; it does not
receive MCP credentials or execute arbitrary provider tools.

## Recommended pilot order

1. Freeze the current backend route as the baseline and create the six-profile
   non-sensitive fixture set plus scoring contract.
2. Evaluate one current stable cloud balanced model and one low-cost cloud
   challenger. Candidate IDs are resolved at pilot time, not copied from the
   monitored roundup.
3. Evaluate Qwen3-30B-A3B or a then-current Mistral small open model only if
   owned server hardware passes memory, cold-start, throughput, and license
   checks; no Android-native trial.
4. Add a vision profile only after the server request/response contract owns
   attachment limits and redaction.
5. Add MCP only after the provider-neutral tool contract and deterministic
   approval gate pass without it.

## Observed value

This task compared 7 model-family representatives, audited 7 current Soll
integration seams, defined 5 routing profiles, 6 workload groups, 17 recorded
metrics, and 6 promotion gates. The focused repository contract passed 1/1
tests. It made 0 provider API calls, read 0 credentials, added 0 Android or
provider dependencies, changed 0 production contracts, and measured 0 external
model quality. The measurable result is an implementation recommendation and
a reject-by-default evaluation contract, not a claim that a new model is ready
for production.

## Primary sources

- <https://developers.openai.com/api/docs/models/gpt-5>
- <https://developers.openai.com/api/docs/guides/latest-model>
- <https://developers.openai.com/api/docs/pricing>
- <https://platform.claude.com/docs/en/about-claude/models/overview>
- <https://platform.claude.com/docs/en/about-claude/pricing>
- <https://ai.google.dev/gemini-api/docs/models/gemini-2.5-pro>
- <https://ai.google.dev/gemini-api/docs/pricing>
- <https://huggingface.co/meta-llama/Llama-4-Scout-17B-16E-Instruct>
- <https://docs.mistral.ai/models/model-cards/mistral-small-4-0-26-03>
- <https://api-docs.deepseek.com/quick_start/pricing/>
- <https://huggingface.co/deepseek-ai/DeepSeek-V3>
- <https://huggingface.co/Qwen/Qwen3-30B-A3B>
- <https://docs.qwencloud.com/developer-guides/getting-started/text-generation-models>
- <https://docs.qwencloud.com/developer-guides/getting-started/pricing>
