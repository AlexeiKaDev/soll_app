---
task_id: beb1f28015b14e1a80af6cb6eccf06ce
project: soll_app
source_ref: source-item/2ac7c9adc8f0/92b3fb6e7bafb440
source_processing_result: kb_note_added_safe_tei_poc_selected
verification_artifact: Soll/outputs/source-processing/source-item-2ac7c9adc8f0-92b3fb6e7bafb440-verification.md
source_value: "1 Soll KB note added; 4 Hugging Face stack roles compared; 1 fail-closed local TEI embedding PoC selected; 1/1 focused contract test passed; 0 models downloaded or loaded; 0 runtime or dependency changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# Hugging Face inference stack verification

## Outcome

Создана короткая Soll KB-заметка
`docs/knowledge/hugging-face-inference-stack-safe-rag-poc.md`. Она разделяет
роли TEI, TGI, Inference Providers и safetensors и выбирает один безопасный
следующий эксперимент: локальный TEI embedding-only retrieval на синтетическом
fixture.

PoC описан fail-closed: до отдельного approval не выбирает, не скачивает и не
загружает модель. В task slice выполнены `0` model loads, `0` external
inference calls и `0` runtime/dependency changes.

## Current-docs audit

Официальные страницы Transformers, TEI, TGI, Inference Providers и safetensors
открыты и проверены 2026-07-23.

| Проверка | Результат |
| --- | --- |
| TEI | embedding serving, dynamic batching, safetensors loading, `/embed`, local/air-gapped deployment и observability подтверждены |
| TGI | LLM generation serving, SSE/continuous batching и текущий maintenance mode подтверждены |
| Inference Providers | managed multi-provider calls, Feature Extraction и внешний token/provider boundary подтверждены |
| safetensors | безопасный относительно pickle tensor format и zero-copy loading подтверждены; provenance/model trust этим не доказываются |
| безопасный PoC | выбран только TEI contract: approved local safetensors model, pinned hash, no remote code, loopback, no egress, synthetic retrieval fixture |

## Source boundary

Заданный source path
`raw/monitored\hugging-face-transformers-docs\20260709-235037-inference-deployment-and-training-stack-be32e866.md`
отсутствует в isolated worktree. Заметка не реконструирует raw snapshot:
task/source identity сохранена, а технические границы проверены по официальным
страницам, перечисленным в KB.

## Focused smoke

`HuggingFaceInferenceStackSafeRagPocKnowledgeTest` проверяет:

- exact task/source trace и отсутствие raw snapshot;
- четыре разные роли Hugging Face stack;
- выбор только local TEI embedding PoC;
- model provenance, isolation и fail-closed gates;
- отсутствие generation, agents, external calls, downloads и runtime changes;
- измеримый `source_value`.

Команда:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.HuggingFaceInferenceStackSafeRagPocKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed,
`0` failures, `0` errors и `0` skipped.

## Value metric

- `source_processing_result`: `kb_note_added_safe_tei_poc_selected`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-2ac7c9adc8f0-92b3fb6e7bafb440-verification.md`
- `source_value`: `1` Soll KB note; `4` Hugging Face stack roles; `1`
  fail-closed local TEI embedding PoC; `1/1` focused contract test; `0` models
  downloaded or loaded; `0` runtime or dependency changes.
