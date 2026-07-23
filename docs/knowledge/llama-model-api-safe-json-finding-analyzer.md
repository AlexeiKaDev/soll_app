---
title: "Llama Model API: safe JSON analyzer для находок Soll"
task_id: 0c16238031584c2d9c6847e6cfa121e1
project: soll_app
source_ref: source-item/08c98d51dea1/819ca889dbfcf345
source_trust: untrusted_external_content
verified_at: 2026-07-23 Europe/Chisinau
raw_ref: raw/monitored\llama-docs\20260709-234711-model-features-for-production-workflows-0c68a3c6.md
raw_status: absent_in_isolated_worktree
decision: server_only_blueprint_no_provider_or_runtime_change
---

# Llama Model API: safe JSON analyzer для находок Soll

## Решение

Официальные Meta Model API surfaces подходят для отдельного server-side
анализатора monitored-source находок, но не дают готовой границы доверия:

- structured output гарантирует форму JSON на этапе decoding, но сервер Soll
  всё равно должен повторно разобрать и проверить результат;
- search grounding возвращает provider-generated citations, но поиск может не
  запуститься, coverage неполон, а сложный multi-hop research прямо назван
  менее надёжным;
- function tool call является предложением модели, а не разрешением на
  выполнение;
- prompt caching снижает повторный prefill, но не является privacy boundary и
  не отменяется через `store: false`.

Поэтому допустимый первый шаг — provider-neutral server adapter, который
возвращает review-only JSON proposal. Android не получает Meta credential или
SDK и продолжает показывать уже существующий `SollSourceItem` с полями
`summary`, `usefulness`, `reasoning`, `evidenceLevel`, `projectFit`,
`actionability`, `dualUseRisk`, `dualUseAction`, `safeNextStep`,
`needsDeepDive`, `auditRef`, `evidenceRef` и `verificationArtifact`.

В этой задаче adapter не реализован и Model API не вызывался. Ни одна находка,
задача, wiki-страница или внешняя интеграция автоматически не изменяется.

## Две API-поверхности нельзя смешивать

| Назначение | Endpoint | Structured output | Tool definition / result |
| --- | --- | --- | --- |
| Agent loop, grounding, state | `POST /v1/responses` | `text.format` | flat `tools[]`; ответ содержит `output[]` items |
| Existing chat harness | `POST /v1/chat/completions` | `response_format` | `tools[].function`; ответ содержит `choices[].message.tool_calls[]` |

Обе поверхности доступны через base URL `https://api.meta.ai/v1` и model
`muse-spark-1.1`. Для анализатора с built-in `web_search` нужен Responses API:
search grounding в Chat Completions не поддерживается.

Передача `response_format` в `/v1/responses` не включает structured output,
даже если поле принято compatibility parser. Аналогично, `text.format` не
настраивает Chat Completions.

## Structured output: конкретные поля и ограничения

### Request fields

Responses:

```json
{
  "text": {
    "format": {
      "type": "json_schema",
      "name": "soll_source_findings_v1",
      "description": "Review-only analysis of one monitored source item",
      "schema": {},
      "strict": true
    }
  }
}
```

`text.format.type`, `name` и `schema` обязательны. `name` допускает латинские
буквы, цифры, `_` и `-`, максимум 64 символа. `strict` по feature page по
умолчанию `false`.

Chat Completions:

```json
{
  "response_format": {
    "type": "json_schema",
    "json_schema": {
      "name": "soll_source_findings_v1",
      "schema": {},
      "strict": true
    }
  }
}
```

JSON-строка результата Chat Completions находится в
`choices[0].message.content`. В Responses JSON находится в `text` поля
`output_text` content block внутри `output[]`.

### Schema limits

Некорректная или слишком большая схема отклоняется до decoding с `HTTP 400`:

| Ограничение | Лимит |
| --- | --- |
| Nesting depth | 10 уровней |
| Properties | 5,000 суммарно |
| Имена, definition names, enum и `const` strings | 120,000 символов суммарно |
| Enum values | 1,000 по умолчанию на всё schema; лимит может быть поднят для app |
| Один string enum больше 250 значений | не больше 15,000 символов суммарно |
| Expanded schema после inline `$ref` | 200,000 nodes |

Recursive `$ref` cycles не поддерживаются и дают `HTTP 400`.

При `strict: true` действует более узкое подмножество JSON Schema:

1. root — обычный `object`, без root `anyOf`, `oneOf`, `allOf`, `enum`, `not`;
2. `allOf` и `oneOf` не поддерживаются нигде; `anyOf` разрешён ниже root;
3. каждый object задаёт `additionalProperties: false`;
4. `required` перечисляет каждое поле из `properties`.

Для structured output сама генерация schema-constrained и при
`strict: false`, однако Soll должен явно передавать `strict: true`, чтобы
получить ранний `HTTP 400` для schema, выходящей за поддерживаемое strict
подмножество.

## Tool calling: конкретные поля и безопасная трактовка

Responses function tool использует flat fields:

```json
{
  "type": "function",
  "name": "propose_source_review",
  "description": "Return a non-persisted review proposal",
  "parameters": {
    "type": "object",
    "properties": {
      "source_item_id": {"type": "string"}
    },
    "required": ["source_item_id"],
    "additionalProperties": false
  },
  "strict": true
}
```

Ответный `function_call` item несёт `name`, JSON string `arguments`,
`call_id` и `status`. Результат клиента возвращается как
`function_call_output` с тем же `call_id`; при server-managed history можно
передать `previous_response_id`.

Chat Completions помещает `name`, `description`, `parameters`, `strict` под
`tools[].function`. Model response использует `tool_calls[].id`,
`tool_calls[].function.name` и JSON string
`tool_calls[].function.arguments`; result message использует
`role: "tool"` и `tool_call_id`.

Ограничения, важные для Soll:

- использовать `tools`, а не deprecated `functions` / `function_call`;
- function name соответствует `^[a-zA-Z0-9_.-]+$` и содержит максимум одну
  точку; нарушение даёт `HTTP 400`;
- `tool_choice` поддерживает только `"auto"`; `"none"`, `"required"` и
  принудительный named tool дают `HTTP 400`;
- `parallel_tool_calls` по умолчанию `true`; `false` ограничивает один
  function call за ход, но не заменяет общий turn budget;
- `max_tool_calls` с минимумом `1` ограничивает только built-in tools, а не
  client-side functions;
- каждый `call_id` имеет длину 1–64 символа;
- `custom` tool принимает freeform text, доступен только в Responses и не
  подходит для JSON-анализатора с закрытой schema;
- generated arguments нужно parse и validate локально перед любым действием,
  независимо от `strict`;
- developer-defined tool исполняет приложение, не модель.

Feature page говорит, что function-tool `strict` по умолчанию `false` и
generated arguments всё равно не гарантированы schema-valid. При этом текущая
Responses API schema description для `FunctionTool.strict` говорит
`Defaults to true`. Из-за этого расхождения adapter не должен зависеть от
default: всегда передавать `strict: true` и всегда выполнять локальную
валидацию.

Для начального анализатора function tool вообще не нужен. Гарантированный JSON
следует получать через `text.format`, а не через tool call, который при
единственном поддерживаемом `tool_choice: "auto"` модель может не сделать.

## Search grounding: request, evidence и ограничения

Минимальный Responses request добавляет built-in tool:

```json
{
  "tools": [
    {
      "type": "web_search",
      "search_context_size": "low",
      "filters": {
        "allowed_domains": ["example.com"]
      }
    }
  ],
  "include": ["web_search_call.results"],
  "max_tool_calls": 1
}
```

`search_context_size` принимает `"low"`, `"medium"` или `"high"`; API schema
указывает default `"medium"`. `filters.allowed_domains` ограничивает домены.
Опциональный `user_location` имеет `type: "approximate"` и поля `country`
(ISO 3166-1 alpha-2), `region`, `city`, `timezone` (IANA).

При фактически выполненном поиске `output[]` содержит
`type: "web_search_call"` со `status`. При opt-in include его `results[]`
содержит `type`, `title`, `url`, `snippet`.

Финальный `output_text` содержит `text` и `annotations[]`. Citation annotation:

```json
{
  "type": "url_citation",
  "url": "https://example.com/source",
  "title": "Source title",
  "start_index": 10,
  "end_index": 42
}
```

`start_index` / `end_index` — character offsets в том же `output_text.text`.
`results[]` — все retrieved sources, а `url_citation` — cited subset.

Границы доверия:

- Responses only;
- наличие `web_search` не гарантирует поиск: решение принимает модель;
- отсутствие `web_search_call` при обязательном grounding означает
  `needs_review`, а не молчаливое использование model memory;
- coverage неполон, источники могут быть недоступны, сложный multi-hop
  research нужно разбивать на узкие запросы;
- citation URL и offsets должны быть проверены; provider citation не доказывает
  истинность claim;
- при объединении с function tools нельзя использовать зарезервированные
  names `browser.search`, `browser.open`, `browser.find`;
- feature page и `WebSearchToolCall` schema документируют include
  `web_search_call.results`, тогда как общий `IncludeEnum` reference всё ещё
  описывает `web_search_call.action.sources`; raw results поэтому остаются
  optional diagnostic evidence, а analyzer не должен зависеть от их наличия.

Для production policy `allowed_domains` строится server-side из одобренного
source registry. Строка `example.com` выше — только inert placeholder, не
предлагаемый Soll allowlist.

## Prompt caching: конкретные поля и наблюдаемость

Prefix caching включён автоматически. Совпадение идёт с начала tokenized
prompt до первого различия, поэтому versioned policy, system instructions,
schema и stable examples идут первыми, а source item и текущее время —
последними.

| Surface | Cache usage field |
| --- | --- |
| Responses | `usage.input_tokens_details.cached_tokens` |
| Chat Completions | `usage.prompt_tokens_details.cached_tokens` |

`cached_tokens` — часть input/prompt token total, а не дополнительные tokens.
Ноль нормален на первом запросе, после изменения prefix или eviction.

Опциональный `prompt_cache_key` поддерживается на обеих поверхностях и
группирует похожие запросы для routing. Для Soll это стабильная версия
use-case, например `soll-source-analyzer-v1`, без user ID, source content или
PII. Уникальный ключ на request/user снижает hit rate.

Responses дополнительно принимает:

- `prompt_cache_retention: "in_memory"` — default behavior;
- `prompt_cache_retention: "24h"` — hint удерживать prefix до 24 часов.

Retention — hint, не гарантия: server может evict раньше. Для first pilot
используется `"in_memory"`; `"24h"` требует отдельного data-retention review.

`store: false` отключает response retrieval, но не prompt caching. При
`previous_response_id` usage follow-up отражает только новый input, а не весь
reconstructed prefix; для end-to-end cache telemetry нужно отправлять
собственный `input[]`.

## Review-only contract для Soll

Предлагаемая schema отражает текущий source-processing язык, но не меняет
публичный Android contract:

```json
{
  "type": "object",
  "properties": {
    "contract_version": {
      "type": "string",
      "enum": ["soll_source_findings_v1"]
    },
    "source_item_id": {"type": "string"},
    "decision": {
      "type": "string",
      "enum": ["accept", "needs_review", "reject"]
    },
    "summary": {"type": "string"},
    "findings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "finding_id": {"type": "string"},
          "claim": {"type": "string"},
          "evidence_refs": {
            "type": "array",
            "items": {"type": "string"}
          },
          "evidence_level": {
            "type": "string",
            "enum": ["primary", "secondary", "insufficient"]
          },
          "project_fit": {
            "type": "string",
            "enum": ["high", "medium", "low", "none"]
          },
          "safe_next_step": {"type": "string"},
          "requires_human_review": {"type": "boolean"}
        },
        "required": [
          "finding_id",
          "claim",
          "evidence_refs",
          "evidence_level",
          "project_fit",
          "safe_next_step",
          "requires_human_review"
        ],
        "additionalProperties": false
      }
    },
    "limitations": {
      "type": "array",
      "items": {"type": "string"}
    }
  },
  "required": [
    "contract_version",
    "source_item_id",
    "decision",
    "summary",
    "findings",
    "limitations"
  ],
  "additionalProperties": false
}
```

### Двенадцать deterministic gates

1. Adapter работает только server-side; credential никогда не попадает в APK,
   task text, prompt artifact или log.
2. Task description и raw/source body остаются untrusted data; они не меняют
   system policy, tool allowlist или schema.
3. Input получает stable `source_item_id`, canonical source URL и bounded
   content; over-limit item отклоняется или chunked до provider call.
4. `text.format.strict: true`, schema version, provider model/profile и
   prompt revision pin-ятся в audit receipt.
5. Grounding для current/external fact использует `web_search`,
   source-specific `allowed_domains` и `max_tool_calls: 1`.
6. Если grounding обязателен, но нет completed `web_search_call` и citations,
   decision принудительно понижается до `needs_review`.
7. `output_text.text` сначала parse как JSON, затем независимо validate по той
   же локально pinned schema; unknown/missing fields отклоняются.
8. `source_item_id` и `contract_version` должны точно совпасть с request;
   generated identity никогда не принимается как authoritative.
9. Каждый `evidence_ref` обязан входить либо в input evidence allowlist, либо
   в проверенный set `url_citation.url`; выдуманная ссылка отклоняет finding.
10. Citation offsets должны быть non-negative, находиться внутри
    `output_text.text` и иметь `start_index < end_index`; URL нормализуется и
    повторно проверяется по domain policy.
11. Analyzer не получает developer-defined write tools. Если позже появится
    read-only function tool, его call остаётся proposal, валидируется локально
    и ограничивается собственным turn/time/result budget.
12. Результат записывается только как review proposal с request ID, source
    refs, schema/prompt/model revisions, usage/cache counters и redacted error;
    создание task/wiki/update требует отдельного human approval path.

## Promotion gates

Первый synthetic/offline suite должен содержать не меньше 30
non-sensitive Soll-shaped cases: normal extraction, missing evidence,
contradictory sources, prompt injection in source body, malformed URL,
unsupported schema, no-search outcome, citation mismatch, duplicate finding,
oversized input и provider error.

Promotion в маленький read-only server canary допускается только при:

- `schema_valid_rate == 1.0`;
- `identity_match_rate == 1.0`;
- `citation_integrity_rate == 1.0`;
- `required_grounding_abstention_rate == 1.0`;
- `unknown_field_accept_count == 0`;
- `hallucinated_evidence_accept_count == 0`;
- `unsafe_tool_execution_count == 0`;
- `automatic_task_or_wiki_write_count == 0`;
- cache hit ratio, latency, input/output tokens, provider errors и cost per
  accepted review записаны, но cache hit не входит в quality score.

## Официальные источники и границы проверки

Прочитаны read-only 2026-07-23:

- <https://llama.developer.meta.com/docs/features/structured-output>
- <https://llama.developer.meta.com/docs/features/tool-calling/>
- <https://llama.developer.meta.com/docs/features/search-grounding>
- <https://llama.developer.meta.com/docs/features/prompt-caching>
- <https://ai.developer.meta.com/docs/api-reference/responses/create-response>
- <https://ai.developer.meta.com/docs/api-reference/responses/schemas>

Заданный raw snapshot отсутствует в isolated worktree. Task-supplied objective
использован только как untrusted discovery signal; API fields и ограничения
сверены по текущим официальным страницам. Выполнено `0` provider API calls,
использовано `0` credentials, исполнено `0` model-requested tools и изменено
`0` Android/runtime contracts.
