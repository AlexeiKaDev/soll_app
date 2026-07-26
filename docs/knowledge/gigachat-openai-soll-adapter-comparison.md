# GigaChat and OpenAI interfaces for the Soll adapter

Analysis date: 2026-07-22 (Europe/Chisinau).

Task: `9ca83e527e5d450dac232b7f16eadc3a`,
`source_ref=insight/d420d61dac77`.

## Decision

GigaChat is suitable as a **server-side provider candidate** for Soll, but it
is not a drop-in implementation of the complete current OpenAI interface.
Official GigaChat documentation describes only partial OpenAI compatibility
and demonstrates three OpenAI SDK operation families: Chat Completions,
embeddings, and models. OpenAI recommends the newer Responses API for new
projects, whose request, structured-output, tool-call, state, and typed-stream
contracts are different.

Soll should therefore add a dedicated GigaChat provider adapter behind the
server provider router. The adapter may reuse a generic HTTP/OpenAI
Chat-Completions transport, but a `base_url` change alone is not an acceptable
compatibility boundary. Android must continue to use
`ModelChatRequest.safeForServer()` and `SollGateway.askModelChat(...)`; it must
not receive provider credentials, refresh tokens, raw provider events, or
provider-specific request fields.

No provider SDK, credential, API call, Android enum, production route, or
runtime configuration is added by this task.

## Source boundary

The monitored artifact
`monitored/gigachat-api-docs/20260709-233905-api-08487485.md` is not vendored in
this isolated worktree. Its title, summary, and proposed benefit are treated as
an untrusted discovery signal rather than API proof. The comparison below was
validated against current official GigaChat and OpenAI documentation and the
current repository seams.

## Interface comparison

| Area | OpenAI interface | GigaChat interface | Soll adapter decision |
| --- | --- | --- | --- |
| Generation endpoint | OpenAI recommends `POST /v1/responses`; Chat Completions remains supported at `POST /v1/chat/completions` | OpenAI compatibility examples target `POST /v1/chat/completions`; GigaChat also documents a provider-specific `POST /v2/chat/completions` | Use a provider-neutral Soll request. Implement GigaChat v1 first. Do not send a Responses payload to GigaChat or expose provider paths to Android. |
| Authentication | API key is sent to the OpenAI API as a bearer credential | An authorization key is exchanged at `POST /api/v2/oauth` using `RqUID` and a scope; the returned bearer access token lasts 30 minutes | Add a server-held token supplier with expiry skew, single-flight refresh, and one refresh-and-retry on an authentication failure. Never model a GigaChat authorization key as a long-lived OpenAI `api_key`. |
| Basic text chat | Responses uses `input`/`instructions` and returns typed output items; Chat Completions uses `messages` and `choices` | v1 uses `messages` and a Chat-Completions-shaped `choices[].message.content` response | Basic Chat Completions mapping is reusable, but normalize it into Soll's provider-neutral response before it leaves the adapter. |
| Structured output | Responses uses `text.format`; Chat Completions uses its own `response_format` JSON Schema envelope | v1 uses `response_format={type,json_schema-compatible schema via schema,strict}`; v2 nests it under `model_options.response_format` | Store one internal schema object and render a provider-specific envelope. Buffer and JSON-validate the final value even when `strict=true`; do not pass an OpenAI envelope through unchanged. |
| Function/tool calling | Current OpenAI contracts use `tools`, `tool_choice`, tool-call IDs, and provider-specific Responses items or Chat `tool_calls` | GigaChat documents legacy-style `functions`, `function_call`, `finish_reason=function_call`, `message.function_call`, `functions_state_id`, and a result message with role `function` | Translate allowlisted functions explicitly. Preserve `functions_state_id` for the round trip, create an internal call ID when needed, validate arguments, and never treat a GigaChat function as already executed. |
| Streaming | Responses emits typed SSE events such as `response.output_text.delta`; Chat Completions emits `choices[].delta` chunks | v1 emits Chat-Completions-shaped SSE chunks ending in `data: [DONE]`; GigaChat v2 emits its own named `response.message.*` events | Implement separate provider parsers that emit common `TextDelta`, `FunctionCall`, `Completed`, and `Error` events. GigaChat v2 event names must not be mistaken for OpenAI Responses events. |
| Embeddings | `POST /v1/embeddings` accepts `model` and `input`, with OpenAI-specific options such as `dimensions` and `encoding_format` | `POST /v1/embeddings` accepts `model` and string/string-array `input`; response uses ordered `data[].embedding` and `index` | Reuse the core envelope only. Do not send unsupported optional fields. Pin provider/model/dimension metadata and keep query and corpus vectors in the same provider/model/revision space. |
| Model discovery | OpenAI exposes model listing/retrieval | GigaChat's OpenAI SDK guide demonstrates model listing/retrieval | Treat discovery as diagnostics, not routing policy. Production routing uses an allowlisted configured model and rejects an unexpected model/dimension change. |

### Structured-output shape is not wire-compatible

The shared words `response_format`, `json_schema`, and `strict` hide a material
wire difference:

```text
Soll internal:  schema(name, jsonSchema, strict)
OpenAI Responses -> text.format
OpenAI Chat      -> response_format with the OpenAI JSON-schema envelope
GigaChat v1      -> response_format { type, schema, strict }
GigaChat v2      -> model_options.response_format { type, schema, strict }
```

GigaChat returns the JSON object wrapped in message text. A streaming response
contains incomplete JSON fragments until completion. The adapter must collect
the final content, parse it as JSON, validate it with the Soll-owned schema,
and return a typed validation failure rather than forwarding malformed text to
a counterparty-risk workflow.

### Tool calls are semantic compatibility, not field compatibility

Both providers let a model propose function arguments, but their state fields
are not interchangeable. The provider-neutral contract needs at least:

```text
ToolCall(providerCallId, name, argumentsJson, providerState)
ToolResult(providerCallId, name, resultJson)
```

For GigaChat, `providerState` retains `functions_state_id`. The adapter converts
the GigaChat arguments object into validated JSON, executes only a Soll
allowlisted server tool after the existing approval/policy gate, and renders a
role-`function` result for the follow-up request. Android never executes an
arbitrary provider function.

### Embedding isolation is mandatory

Matching endpoint and response field names do not make embedding vectors
cross-provider comparable. The risk-radar index must record provider, exact
model, vector dimension, normalization policy, and index revision. Documents
and queries must use the same tuple. A GigaChat rollout requires a new or fully
rebuilt index; it must never append GigaChat vectors to an OpenAI-created index
or silently fall back between providers for a query.

## Current Soll seams

Five repository facts were verified:

1. `ModelChatProviderHint` contains only `AUTO` and `LLAMA`; no GigaChat choice
   is exposed to Android.
2. `ModelChatRequest.safeForServer()` removes private messages, bounds message
   count and size, and normalizes roles before a server request.
3. `ModelChatServerBridge` explicitly keeps provider keys server-side.
4. `SollRepository.askModelChat(...)` sends the sanitized request through the
   existing Soll assistant route; Android does not call a model provider.
5. This worktree contains the Android client but no owned provider-router or
   model-serving implementation, and its Gradle graph contains no OpenAI or
   GigaChat SDK.

These facts make the implementation location unambiguous: the future adapter
belongs in the Soll server/desktop provider router, while the existing Android
public contract remains unchanged. Adding `GIGACHAT` to the Android enum before
the server capability exists would create a non-functional public contract and
is rejected by this comparison.

## Proposed server adapter contract

The server implementation should own a small provider-neutral interface:

```text
capabilities() -> chat, structuredOutput, tools, streaming, embeddings
complete(ProviderChatRequest) -> ProviderChatResponse
stream(ProviderChatRequest) -> ProviderEvent
embed(ProviderEmbeddingRequest) -> ProviderEmbeddingResponse
```

The GigaChat implementation owns six translation boundaries:

1. base URL, OAuth exchange, token cache, and refresh;
2. OpenAI Responses vs GigaChat Chat Completions request/response mapping;
3. structured-output envelope and final schema validation;
4. functions, function results, and `functions_state_id` preservation;
5. SSE chunk parsing and normalized terminal/error events;
6. embedding optional fields, model identity, dimension, and index isolation.

Unknown fields are rejected or deliberately ignored by an allowlist; they are
not blindly proxied between providers. Each normalized response records the
provider, requested model, resolved model, request ID when available, finish
reason, usage, latency, and normalized error class without logging prompt text
or credentials.

## Approval-gated live canary

No live provider call is needed to validate this comparison. A later canary
requires server-owned test credentials and synthetic, non-counterparty data.
Promotion requires all six gates:

1. **Auth:** concurrent requests cause one token refresh; expiry skew works;
   one authentication failure is refreshed and retried once, never in a loop.
2. **Chat:** a deterministic Russian prompt returns non-empty normalized text,
   provider/model metadata, finish reason, and usage without Android changes.
3. **Streaming:** deltas assemble to the same semantic answer as non-streaming,
   exactly one terminal event is emitted, and cancellation closes the stream.
4. **Structured output:** 100/100 fixed extraction fixtures parse and pass the
   Soll JSON Schema; refusals, truncation, invalid JSON, and schema violations
   are distinct failures.
5. **Functions:** one allowlisted read-only function completes a full
   proposal-result-answer round trip with state preserved; zero arbitrary or
   duplicate executions occur.
6. **Embeddings:** a two-input batch preserves order/index, returns stable
   dimensions for the pinned model, and reads/writes only the matching
   provider/model/index revision.

Record success rate, schema-valid rate, p50/p95 latency, first-delta latency,
token usage, refresh count, retry count, function duplicate count, embedding
dimension, and normalized error counts. A provider is not promoted merely
because the OpenAI SDK accepts its base URL.

## Observed value

This task reviewed 14 official documentation/API surfaces, compared 8
interface areas, confirmed 3 partial OpenAI SDK operation families, identified
6 required provider-specific translation boundaries, and defined 6 measurable
live-canary gates. The focused repository contract passed 1/1 tests. This task
made 0 provider API calls, used 0 credentials, changed 0 Android/runtime
contracts, and measured 0 GigaChat inference or embedding quality. The
measurable value is a validated implementation boundary and a reject-by-default
compatibility contract, not a runtime performance claim.

## Primary sources

GigaChat:

- <https://developers.sber.ru/docs/ru/gigachat/guides/compatible-openai>
- <https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api>
- <https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat>
- <https://developers.sber.ru/docs/ru/gigachat/guides/structured-output>
- <https://developers.sber.ru/docs/ru/gigachat/guides/response-token-streaming>
- <https://developers.sber.ru/docs/ru/gigachat/guides/functions/generating-arguments-for-custom-functions>
- <https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-embeddings>

OpenAI:

- <https://developers.openai.com/api/docs/guides/migrate-to-responses>
- <https://developers.openai.com/api/docs/guides/structured-outputs>
- <https://developers.openai.com/api/docs/guides/streaming-responses>
- <https://developers.openai.com/api/docs/guides/embeddings>
- <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create>
- <https://developers.openai.com/api/reference/resources/embeddings/methods/create>
- <https://developers.openai.com/api/reference/resources/responses/methods/create>
