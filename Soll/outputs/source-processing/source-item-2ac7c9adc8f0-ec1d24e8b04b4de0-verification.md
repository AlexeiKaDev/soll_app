---
task_id: d9b29c020651421dae4ea982ec865a87
project: soll_app
source_ref: source-item/2ac7c9adc8f0/ec1d24e8b04b4de0
source_processing_result: kb_note_added_current_docs_verified
verification_artifact: Soll/outputs/source-processing/source-item-2ac7c9adc8f0-ec1d24e8b04b4de0-verification.md
source_value: "1 Soll KB note added; 5 inference paths compared; 3 generate streaming classes and custom streamer contract verified; 3 serving-engine compatibility boundaries recorded; 1/1 focused contract test passed; 0 runtime or dependency changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# Hugging Face Transformers inference-selection verification

## Outcome

Создана короткая Soll KB-заметка
`docs/knowledge/hugging-face-transformers-inference-selection.md`. Она отделяет:

- `Pipeline` для task-oriented in-process прототипов и измеренной offline/batch
  обработки;
- прямой `generate()` для точного управления generation и correctness
  baseline;
- vLLM/SGLang для конкурентного production serving;
- TGI только как уже существующий проверенный deployment, поскольку upstream
  переведён в maintenance mode.

Android/runtime contracts и dependencies не менялись.

## Current-docs audit

Официальные страницы Transformers, vLLM, SGLang и TGI открыты и проверены
2026-07-23.

| Проверка | Результат |
| --- | --- |
| `generate()` streaming | подтверждены `TextStreamer`, `TextIteratorStreamer`, `AsyncTextIteratorStreamer` и custom `put()`/`end()` contract |
| non-blocking consumption | sync/async iterator examples запускают `generate()` в отдельном thread; queue timeout/`TimeoutError` зафиксированы |
| direct model compatibility | AutoClass/task head, model/tokenizer revision, chat template и reviewed pinned remote code выделены как отдельные gates |
| vLLM compatibility | current Supported Models и backend requirements являются gates; заявленная upstream parity применима только при выполнении всех требований, а не по факту успешной загрузки |
| SGLang compatibility | native/fallback разделены; fallback attention contract зафиксирован |
| TGI compatibility | maintenance status и ограничения non-core fallback отражены |

Страница Transformers `main` на дату проверки указывала `v5.14.0` как latest
stable. KB хранит дату проверки, потому что `main` и support matrices могут
изменяться.

## Source boundary

Заданный test-plan/source path
`raw/monitored\hugging-face-transformers-docs\20260709-235037-transformers-library-overview-84124871.md`
отсутствует в isolated worktree. Поэтому заметка не цитирует и не
реконструирует содержимое raw snapshot. Task identity сохранена, а технические
утверждения проверены по перечисленным в KB официальным актуальным страницам.
Отсутствие snapshot не блокирует acceptance criterion «KB-note created for
Soll».

## Focused smoke

`HuggingFaceTransformersInferenceSelectionKnowledgeTest` проверяет:

- exact task/source trace и границу отсутствующего raw snapshot;
- пять вариантов inference и Soll server/Android boundary;
- три streamer class, custom contract и thread/timeout caveats;
- direct, vLLM, SGLang и TGI compatibility gates;
- измеримый `source_value` и отсутствие runtime/dependency изменений.

Команда:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.HuggingFaceTransformersInferenceSelectionKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed,
`0` failures, `0` errors и `0` skipped.

## Value metric

- `source_processing_result`: `kb_note_added_current_docs_verified`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-2ac7c9adc8f0-ec1d24e8b04b4de0-verification.md`
- `source_value`: `1` Soll KB note; `5` inference paths; `3` generate
  streaming classes plus custom streamer contract; `3` serving-engine
  compatibility boundaries; `1/1` focused contract test; `0` runtime or
  dependency changes.
