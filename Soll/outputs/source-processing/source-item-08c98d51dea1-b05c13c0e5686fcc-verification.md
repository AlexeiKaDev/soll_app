---
task_id: bc5e9fc1a57a4f4292a81f3deef06f51
project: soll_app
source_ref: source-item/08c98d51dea1/b05c13c0e5686fcc
source_processing_result: kb_note_added_official_provider_config_verified
verification_artifact: Soll/outputs/source-processing/source-item-08c98d51dea1-b05c13c0e5686fcc-verification.md
source_value: "1 Soll KB note added; 3 API surfaces separated; 2 exact token limits recorded; 1 manual OpenCode config and 6 sandbox/credential guards documented; 1/1 focused contract test passed; 0 credentials, provider API calls, autonomous write tools, coding-agent spikes, runtime or dependency changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# Meta Model API coding-agent provider-config verification

## Outcome

Создана короткая Soll KB-заметка
`docs/knowledge/meta-model-api-coding-agent-provider-config.md`.

Она фиксирует официальные параметры Meta Model API для coding agents:

- OpenAI-compatible base URL `https://api.meta.ai/v1`;
- Anthropic Messages host `https://api.meta.ai`;
- Bearer credential из `MODEL_API_KEY`;
- model `muse-spark-1.1`;
- context `1048576` и maximum output `131072`;
- ручной OpenCode config через recommended Responses adapter
  `@ai-sdk/openai`.

Self-configuration coding agent не запускался. Android/runtime contracts,
dependencies и provider choices не менялись.

## Current-docs audit

Официальные Meta coding-agents guide, overview и launch post прочитаны
read-only 2026-07-23.

| Проверка | Результат |
| --- | --- |
| Wire formats | Responses, Chat Completions и Anthropic Messages разделены |
| Base URL | OpenAI-compatible использует `/v1`; Claude Code получает host без `/v1`, затем добавляет `/v1/messages` |
| Credential | `MODEL_API_KEY` хранится вне committed config; для Claude Code используется bearer `ANTHROPIC_AUTH_TOKEN` |
| Model limits | model `muse-spark-1.1`, context `1048576`, output `131072` |
| OpenCode | recommended `@ai-sdk/openai` Responses config и weaker `@ai-sdk/openai-compatible` fallback различены |
| Reasoning continuity | encrypted reasoning replay зафиксирован для Responses; Chat Completions fallback не объявлен эквивалентным |
| Soll boundary | reference относится к desktop/server tooling; Android provider/runtime не изменён |

## Source boundary

Заданный test-plan/source path
`raw/monitored\llama-docs\20260709-234711-coding-agent-configuration-c02c4a0b.md`
отсутствует в isolated worktree. Его task-supplied summary использован только
как untrusted discovery signal. Технические параметры проверены по текущей
официальной странице
<https://ai.developer.meta.com/docs/guides/coding-agents/>.

Отсутствие snapshot не блокирует acceptance criterion
«Official Meta model API provider config documentation created», поскольку
KB-заметка и её current official documentation trace являются durable
repository evidence.

## Focused smoke

`MetaModelApiCodingAgentProviderConfigKnowledgeTest` проверяет:

- exact task/source/raw trace и trust boundary;
- три API surfaces и разные base URL;
- model ID, credential mapping и оба token limits;
- recommended manual OpenCode Responses config и reasoning replay;
- шесть sandbox/credential guards;
- измеримый `source_value` и отсутствие Android/runtime integration.

Команда:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.MetaModelApiCodingAgentProviderConfigKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed,
`0` failures, `0` errors и `0` skipped.

## Value metric

- `source_processing_result`:
  `kb_note_added_official_provider_config_verified`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-08c98d51dea1-b05c13c0e5686fcc-verification.md`
- `source_value`: `1` Soll KB note; `3` API surfaces; `2` exact token limits;
  `1` manual OpenCode config; `6` sandbox/credential guards; `1/1` focused
  contract test; `0` credentials, provider API calls, autonomous write tools,
  coding-agent spikes, runtime or dependency changes.
