---
task_id: 9ca83e527e5d450dac232b7f16eadc3a
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/d420d61dac77
source_processing_result: official_api_comparison_validated_adapter_boundary_ready
verification_artifact: Soll/outputs/source-processing/task-9ca83e527e5d450dac232b7f16eadc3a-gigachat-openai-adapter-audit.md
value_metric: "1 adapter comparison added; 14 official documentation/API surfaces reviewed; 8 interface areas compared; 3 partial OpenAI SDK operation families confirmed; 6 provider-specific translation boundaries and 6 live-canary gates defined; 1/1 focused contract test passed; 0 provider API calls, credentials, Android/runtime changes, or measured GigaChat inference value"
verified_at: 2026-07-22 Europe/Chisinau
---

# GigaChat/OpenAI Soll adapter audit

## Outcome

The comparison is complete and recorded in
`docs/knowledge/gigachat-openai-soll-adapter-comparison.md`.

GigaChat can reuse an OpenAI Chat-Completions-shaped transport for a bounded
subset, but it is not a drop-in implementation of the current OpenAI Responses
interface. A safe Soll integration needs a dedicated server-side provider
adapter. Android remains on `ModelChatRequest.safeForServer()` ->
`SollGateway.askModelChat(...)`; no provider key, SDK, enum, raw stream, or
provider-specific payload is added to the app.

The monitored source artifact is not vendored in this isolated worktree. It
was used only as an untrusted discovery signal. Fourteen current official
GigaChat/OpenAI documentation and API-reference surfaces provide the evidence.

## Focused comparison

| Check | Observed result |
| --- | --- |
| OpenAI compatibility claim | GigaChat documents partial compatibility, with OpenAI SDK examples for Chat Completions, embeddings, and models |
| Current OpenAI baseline | Responses is recommended for new projects; Chat Completions remains supported |
| Auth compatibility | not wire-compatible: GigaChat exchanges a scoped authorization key for a 30-minute access token |
| Structured output | same goal, different envelopes: OpenAI Responses `text.format`; GigaChat v1 `response_format.schema`; GigaChat v2 `model_options.response_format` |
| Functions/tools | semantic overlap, different fields/state: OpenAI tools/tool-call IDs vs GigaChat functions/function_call/functions_state_id |
| Streaming | GigaChat v1 resembles Chat Completions delta SSE; OpenAI Responses and GigaChat v2 have different typed events |
| Embeddings | core endpoint shape overlaps; optional fields, models, dimensions, and vector spaces require isolation |
| Soll location | future implementation belongs in the server provider router; the Android public contract remains unchanged |

## Adapter controls

The comparison defines six required translation boundaries:

1. OAuth/token lifecycle;
2. Responses-vs-Chat request and response mapping;
3. structured-output envelope and schema validation;
4. function calls, results, and provider state;
5. normalized streaming events;
6. embedding model/dimension/index isolation.

It also defines six approval-gated live-canary gates: auth, chat, streaming,
structured output, allowlisted function round trip, and two-input embeddings.
Promotion must measure each gate with synthetic data. SDK construction or a
successful base-URL substitution is explicitly not sufficient evidence.

## Focused smoke/audit artifact

`GigaChatOpenAiAdapterComparisonTest` guards:

- exact task/source trace and missing monitored-source boundary;
- all 14 official source URLs;
- all 8 interface comparison areas;
- current Soll Android/server seams and the no-Android-provider decision;
- all 6 translation boundaries and 6 later live-canary gates;
- the updated value metric and honest zero-runtime evidence.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.GigaChatOpenAiAdapterComparisonTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors, and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `official_api_comparison_validated_adapter_boundary_ready`
- official documentation/API surfaces reviewed: `14`;
- interface areas compared: `8`;
- partial OpenAI SDK operation families confirmed: `3`;
- provider-specific translation boundaries defined: `6`;
- measurable live-canary gates defined: `6`;
- focused contract tests passed: `1/1`;
- provider API calls and credentials used: `0`;
- Android/runtime contract changes: `0`;
- measured GigaChat inference or embedding quality: `0`.
