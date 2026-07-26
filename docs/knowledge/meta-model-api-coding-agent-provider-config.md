---
title: "Meta Model API: provider config для coding agents"
task_id: bc5e9fc1a57a4f4292a81f3deef06f51
project: soll_app
source_ref: source-item/08c98d51dea1/b05c13c0e5686fcc
source_trust: untrusted_external_content
verified_at: 2026-07-23 Europe/Chisinau
raw_ref: raw/monitored\llama-docs\20260709-234711-coding-agent-configuration-c02c4a0b.md
raw_status: absent_in_isolated_worktree
---

# Meta Model API provider config для coding agents

## Короткий конфиг

Официальная Meta Model API guide на дату проверки задаёт единый model ID
`muse-spark-1.1`, но разные base URL для разных wire formats:

| Клиент / API surface | Base URL | Auth и важные параметры |
| --- | --- | --- |
| OpenAI Responses или Chat Completions | `https://api.meta.ai/v1` без trailing slash | Bearer key из `MODEL_API_KEY`; model `muse-spark-1.1` |
| Anthropic Messages / Claude Code | `https://api.meta.ai` без `/v1` | клиент добавляет `/v1/messages`; `ANTHROPIC_AUTH_TOKEN="$MODEL_API_KEY"` используется как Bearer token |

Заявленные Meta лимиты модели: context `1048576` токенов и maximum output
`131072` токена. Responses (`/v1/responses`) нужен клиенту, который явно
поддерживает эту поверхность. Большинство generic OpenAI-compatible клиентов
по умолчанию используют Chat Completions (`/v1/chat/completions`). Anthropic
Messages использует `/v1/messages`.

Ключ хранится только в credential store агента или в `MODEL_API_KEY`. Нельзя
вставлять реальное значение в committed JSON/TOML, пример, лог или Android
конфигурацию.

## Рекомендуемый ручной OpenCode config

Для OpenCode Meta рекомендует Responses adapter `@ai-sdk/openai`: он передаёт
encrypted reasoning между ходами и поддерживает полный multimodal набор.
Добавлять config нужно вручную после review; в Soll нельзя просить coding agent
самостоятельно переписать собственный config.

```json
{
  "provider": {
    "meta": {
      "name": "Meta Model API",
      "npm": "@ai-sdk/openai",
      "options": {
        "baseURL": "https://api.meta.ai/v1"
      },
      "models": {
        "muse-spark-1.1": {
          "name": "muse-spark-1.1",
          "reasoning": true,
          "limit": {
            "context": 1048576,
            "output": 131072
          },
          "modalities": {
            "input": ["text", "image", "pdf", "video"],
            "output": ["text"]
          },
          "options": {
            "reasoningEffort": "high",
            "reasoningSummary": "auto",
            "include": ["reasoning.encrypted_content"]
          }
        }
      }
    }
  }
}
```

После review ключ передаётся через OpenCode `/connect`, а не добавляется в
`opencode.json`. `include: ["reasoning.encrypted_content"]` позволяет Responses
adapter воспроизводить зашифрованное reasoning в следующих запросах, включая
цикл tools и context compaction.

Fallback `@ai-sdk/openai-compatible` переключает OpenCode на Chat Completions.
Он проще, но не сохраняет reasoning между ходами; по официальной guide это
может приводить к повторению или потере шага в длинном agent loop. Поэтому для
Muse Spark в OpenCode это fallback, а не эквивалентный default.

## Другие coding agents

- Codex: provider block использует `base_url = "https://api.meta.ai/v1"`,
  `env_key = "MODEL_API_KEY"` и `wire_api = "responses"`; model context
  задаётся как `1048576`, а auto-compaction должен срабатывать раньше лимита.
- Claude Code: использовать host `https://api.meta.ai` и
  `ANTHROPIC_AUTH_TOKEN`, не `ANTHROPIC_API_KEY`, потому что Model API ожидает
  Bearer auth. Основную модель, aliases `opus`/`sonnet`/`haiku` и subagent
  model нужно явно pin на `muse-spark-1.1`, иначе отдельный execution path
  может выбрать несуществующую на Model API Claude model.

Это reference-конфигурация desktop/server tooling. Она не добавляет Meta
provider, SDK, credentials или новый model choice в Android: приложение
продолжает использовать существующий server boundary.

## Если понадобится sandbox spike

Текущая задача spike не запускает. Отдельный явно одобренный spike допустим
только при выполнении всех условий:

1. отдельный temporary config/home и synthetic non-sensitive repository;
2. task-scoped test credential без доступа к production secrets;
3. file-write, shell, MCP и external-integration tools отключены deny-by-default;
4. если агент не умеет технически запретить эти tools, запуск отменяется;
5. фиксированные лимиты времени, токенов и стоимости плюс redacted audit log;
6. сначала config-parse/offline check, затем отдельное одобрение любого live API
   call; никакого deploy, commit или push.

Измеряемый результат spike — config load, выбранные provider/model/wire API,
отказ запрещённого tool call и отсутствие workspace diff. Ответ модели сам по
себе не является доказательством безопасной интеграции.

## Источники и границы

- <https://ai.developer.meta.com/docs/guides/coding-agents/>
- <https://ai.developer.meta.com/docs/getting-started/overview/>
- <https://ai.meta.com/blog/introducing-muse-spark-meta-model-api/>

Официальные страницы прочитаны read-only 2026-07-23. Coding-agents guide
подтверждает конкретные base URL, auth mapping, model ID, лимиты и OpenCode
config; overview подтверждает OpenAI SDK, Anthropic SDK и coding-agent
compatibility; launch post подтверждает public preview Meta Model API,
1M-token context и OpenAI-compatible package.

Заданный raw snapshot отсутствует в isolated worktree, поэтому его содержимое
не реконструировалось и не считалось доверенной инструкцией. Выполнено `0`
provider API calls, использовано `0` credentials, запущено `0` coding-agent
spikes и изменено `0` Android/runtime contracts.
