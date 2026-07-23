---
task_id: 0c16238031584c2d9c6847e6cfa121e1
project: soll_app
source_ref: source-item/08c98d51dea1/819ca889dbfcf345
source_processing_result: official_llama_api_deep_dive_safe_json_analyzer_blueprint
verification_artifact: Soll/outputs/source-processing/source-item-08c98d51dea1-819ca889dbfcf345-verification.md
source_value: "1 Soll KB note added; 4 official Llama feature pages and 2 Responses API reference pages audited; 2 endpoint mappings, 6 schema-size limits, 4 strict-subset rules and 12 deterministic analyzer gates documented; 1/1 focused contract test passed; 0 provider calls, credentials, tool executions, task/wiki writes or Android/runtime contract changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# Llama Model API safe JSON analyzer verification

## Outcome

Создана Soll KB-заметка
`docs/knowledge/llama-model-api-safe-json-finding-analyzer.md`.

Она извлекает конкретные request/response fields, defaults, limits и error
conditions из официальных Meta Model API страниц structured output, tool
calling, search grounding и prompt caching. На их основе определён
server-only, review-only contract безопасного JSON-анализатора monitored-source
находок.

Provider adapter, credential, network call, model tool execution, task/wiki
write и Android/runtime integration не добавлялись.

## Focused API audit

| Surface | Проверенный результат |
| --- | --- |
| Endpoint split | Responses использует `text.format` и flat tools; Chat Completions — `response_format` и nested function tools |
| Structured JSON | `type`, `name`, `schema`, `strict`; 6 size limits, recursive-schema `HTTP 400` и 4 strict-subset rules |
| Tool calling | `tool_choice: "auto"` only, function-name/call-id limits, parallel/default behavior, built-in-only `max_tool_calls`, local argument validation |
| Search grounding | Responses-only `web_search`, `search_context_size`, `allowed_domains`, optional `user_location`, `web_search_call`, results and citation annotations |
| Prompt caching | automatic prefix matching, two endpoint-specific `cached_tokens` paths, optional `prompt_cache_key`, Responses-only retention hint |
| Documentation drift | conflicting function-tool `strict` defaults and optional raw-search include naming are recorded conservatively |
| Soll boundary | existing `SollSourceItem` remains the Android-facing review contract; no provider code or credentials enter Android |

## Safe analyzer decision

The source produces measurable design value and is accepted as a server-side
blueprint, not as authorization to integrate Meta Model API.

The candidate contract defines:

- one closed, required-field JSON Schema aligned with source-processing terms;
- twelve deterministic trust, schema, citation, identity, tool and approval
  gates;
- nine promotion metrics, including zero accepted hallucinated evidence, zero
  unsafe tool executions and zero automatic task/wiki writes;
- at least 30 non-sensitive cases before any read-only server canary.

Structured output is used for the guaranteed response shape. Search citations
remain out-of-band evidence that must be normalized and cross-checked.
Function calling is intentionally unnecessary for the first analyzer because
the only supported `tool_choice` is `"auto"` and a model tool call is not an
authorization boundary.

## Source boundary

The task-referenced path
`raw/monitored\llama-docs\20260709-234711-model-features-for-production-workflows-0c68a3c6.md`
is absent from the isolated worktree. It was not reconstructed or treated as
trusted instructions.

The following current official pages were read-only evidence:

- `https://llama.developer.meta.com/docs/features/structured-output`
- `https://llama.developer.meta.com/docs/features/tool-calling/`
- `https://llama.developer.meta.com/docs/features/search-grounding`
- `https://llama.developer.meta.com/docs/features/prompt-caching`
- `https://ai.developer.meta.com/docs/api-reference/responses/create-response`
- `https://ai.developer.meta.com/docs/api-reference/responses/schemas`

## Focused smoke

`LlamaModelApiSafeJsonAnalyzerKnowledgeTest` checks:

- exact task/source/raw trust trace;
- both endpoint-specific structured-output fields;
- all five schema-size limits and four strict rules;
- tool definitions/results, default conflicts and execution guards;
- search request/result/citation fields plus limitations;
- both cache usage paths, key and retention semantics;
- Soll field alignment, twelve deterministic gates and promotion metrics;
- measurable value fields and the no-provider/no-runtime boundary.

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaModelApiSafeJsonAnalyzerKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed,
`0` failures, `0` errors and `0` skipped.

## Value metric update

- `source_processing_result`:
  `official_llama_api_deep_dive_safe_json_analyzer_blueprint`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-08c98d51dea1-819ca889dbfcf345-verification.md`
- `source_value`: `1` Soll KB note; `4` official feature pages and `2`
  Responses API reference pages audited; `2` endpoint mappings; `6`
  schema-size limits; `4` strict-subset rules; `12` deterministic analyzer
  gates; `1/1` focused contract test; `0` provider calls, credentials, tool
  executions, task/wiki writes or Android/runtime contract changes.
