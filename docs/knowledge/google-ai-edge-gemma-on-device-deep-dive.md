---
task_id: eb2786f702454d47b8fe2376593523ef
project: soll_app
source_ref: source-item/db74e38e5609/cb1fea99535b326e
source_trust: untrusted_external_content
raw_ref: raw/monitored\google-ai-for-developers\20260709-203525-google-ai-developer-platform-overview-6aedb16f.md
raw_status: absent_in_isolated_worktree
reviewed_at: 2026-07-28 Europe/Chisinau
---

# Google AI Edge / Gemma: безопасный on-device deep dive для Soll app

## Решение

Сигнал имеет измеримую ценность, но не разрешает немедленную интеграцию.
Предпочтительный новый Android-маршрут — **LiteRT-LM Kotlin API** с локальным
файлом Gemma в формате `.litertlm`. Старый MediaPipe LLM Inference API уже
находится в maintenance-only mode, поэтому начинать на нём новую интеграцию не
следует.

После установки модели LiteRT-LM способен выполнять inference полностью на
устройстве и без API key. Это технически позволяет делать локальные:

- summarization текста;
- classification по закрытому списку меток;
- обработку private input без передачи prompt, результата или контекста во
  внешний inference API.

Вывод пока **conditional go for an isolated pilot**, а не production go:

- совместимость текущего `minSdk=26` и Java/Kotlin target 17 с конкретным
  LiteRT-LM AAR не доказана официальной Android-страницей;
- самый маленький подходящий chat-ready Gemma в текущей таблице LiteRT-LM —
  Gemma3-1B размером около `1005 MB`;
- официальные mobile-примеры ориентируются на Android 12+ и физические
  high-end устройства;
- выбранную модель нельзя распространять до фиксации её точной лицензии,
  NOTICE/terms и модели доставки;
- качество Russian summarization/classification и реальные RAM, latency,
  battery и thermal costs в Soll app ещё не измерены.

В этой задаче SDK, модель, permission, download code, UI и runtime adapter не
добавляются.

## Что проверено в текущем Soll app

| Поверхность | Факт репозитория | Следствие |
| --- | --- | --- |
| Android build | `compileSdk=36`, `minSdk=26`, `targetSdk=34` | `minSdk` сам по себе не доказывает поддержку LiteRT-LM |
| Toolchain | Java source/target и Kotlin `jvmTarget` равны `17` | конкретный pinned AAR нужно сначала разрешить и скомпилировать в изолированном spike |
| Google AI Edge | `0` production references на `com.google.ai.edge`, `com.google.mediapipe`, `tasks-genai` или `.litertlm` | миграционного долга нет; это новая capability |
| Network | manifest уже содержит `android.permission.INTERNET`; app использует Retrofit и Firebase Messaging | локальность нельзя доказывать отсутствием permission; нужен отдельный offline-only boundary |
| Backup | `android:allowBackup="false"` | app backup не должен копировать prompt/result/model state |
| Existing model chat | private/server routing уже отделяется через `ModelChatRequest.safeForServer()` | on-device adapter не должен неявно вызывать server fallback |
| Scope | 305 production files, из них 267 Kotlin, были просмотрены структурным поиском | прямой Google AI Edge/Gemma runtime seam отсутствует |

## Лицензии: runtime и model weights — разные объекты

Это инженерный аудит, а не юридическая консультация. Перед distribution нужна
проверка выбранной версии и фактически загружаемого model artifact.

| Объект | Проверенная лицензия/условия | Что должен сделать Soll |
| --- | --- | --- |
| LiteRT-LM source/runtime | upstream repository помечен Apache License 2.0 | pin версии, сохранить license/NOTICE и attribution для реально поставляемого AAR |
| Google AI Edge Gallery sample | Apache License 2.0 | код можно изучать, но копирование требует сохранения Apache notices |
| Gemma 3 / Gemma 3n / FunctionGemma / EmbeddingGemma | перечислены в Appendix Gemma Terms of Use, last modified 2026-04-01 | принять terms; при distribution передать agreement, включить обязательный Notice, сделать use restrictions enforceable для downstream users, отметить изменённые файлы |
| Gemma 4 | отдельная официальная страница указывает Apache License 2.0 | сохранить Apache license/NOTICE/attribution; не смешивать автоматически с обязательствами старых Gemma Terms |
| Generated output | Gemma Terms не заявляют права Google на output, но возлагают ответственность на пользователя | Soll всё равно должен маркировать AI output и проверять опасные/чувствительные сценарии |

Gemma Terms включают Gemma Prohibited Use Policy. Для Soll особенно важны
запреты на обработку или inference чувствительной персональной информации без
необходимых прав/согласий и на автоматические решения, влияющие на существенные
права или благополучие человека. Даже если конкретная будущая модель будет под
Apache 2.0, эти ограничения полезны как минимальная продуктовая safety policy.

### Distribution decision

Первый pilot не должен встраивать гигабайтный model file в APK и не должен
публиковать его из Soll backend. Разрешены только:

1. exact allowlisted model id/version;
2. официальный HTTPS origin;
3. явное license/size/network consent до загрузки;
4. ожидаемые byte size и SHA-256;
5. atomic download в app-private storage;
6. загрузка только data model; никакие скачанные `.so`, `.dex` или executable
   files не исполняются.

До legal/product approval pilot остаётся developer-only и использует
нечувствительные fixtures.

## Android SDK/API

### Рекомендуемый путь: LiteRT-LM

Официальная Android-страница описывает Gradle artifact:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:<pinned-version>")
```

Документация показывает `latest.release`, но Soll не должен использовать
плавающую версию. Pilot сначала фиксирует точный Maven version и lock/provenance.

Основной lifecycle:

1. создать `EngineConfig(modelPath, backend, cacheDir)`;
2. создать `Engine` и вызвать `initialize()` вне main thread;
3. создать `Conversation`;
4. получать streaming output через coroutine
   `sendMessageAsync(...): Flow<Message>`;
5. закрыть `Conversation` и `Engine`.

Официальная страница предупреждает, что `initialize()` может занять до 10
секунд. Для GPU backend manifest должен объявить optional native libraries
`libvndksupport.so` и `libOpenCL.so`. CPU, GPU и NPU указаны как Android
backends, но первая версия Soll pilot должна использовать только CPU/GPU
fallback и не зависеть от NPU availability.

Модели имеют формат `.litertlm`. Текущая таблица LiteRT-LM содержит:

| Model | Размер | Официальный Android measurement | Пригодность для pilot |
| --- | ---: | --- | --- |
| Gemma3-1B | `1005 MB` | Samsung S24 Ultra; CPU/GPU measurements опубликованы | основной минимальный chat-ready кандидат |
| Gemma-3n-E2B | `2965 MB` | Samsung S24 Ultra | слишком тяжёлый для первого text-only pilot |
| Gemma4-E2B | `2583 MB` | Samsung S26 Ultra; peak CPU memory `1733 MB`, GPU `676 MB` в опубликованном workload | проще по Apache license, но storage/device cost выше |
| FunctionGemma | `289 MB` | Samsung S25 Ultra | base/tool model, не считать готовой общей summarization model |

Цифры — upstream benchmark, а не обещание производительности Soll. Prompt
length, context, temperature, backend, device load и thermal state отличаются.

### Не выбирать для greenfield: MediaPipe LLM Inference

MediaPipe guide прямо помечает API как maintenance-only и рекомендует
LiteRT-LM Android Kotlin API. Он всё ещё документирует
`com.google.mediapipe:tasks-genai:0.10.27`, `.task` files,
`generateResponse()` и `generateResponseAsync()`, но это только fallback для
существующей legacy integration, которой в Soll app нет.

## Требования к устройству

Официальная LiteRT-LM Android page не публикует достаточный minimum API/device
contract, поэтому нельзя утверждать, что любой Soll device с API 26 подходит.
Наблюдаемые upstream границы:

- Google AI Edge Gallery требует Android 12+;
- MediaPipe LLM guide ориентирует на Pixel 8, Samsung S23 или более новые
  high-end устройства и предупреждает, что emulator ненадёжен;
- LiteRT-LM benchmarks используют Samsung S24/S25/S26 Ultra;
- smallest chat-ready Gemma занимает около 1 GB только на диске;
- engine initialization может занимать до 10 секунд.

Начальный Soll capability gate:

```text
Android >= 12
physical arm64 device
exact model is downloaded and hash-verified
free app-private storage >= 2.5 x model bytes
foreground activity is visible
supported CPU or GPU backend initializes
```

Множитель `2.5 x` — Soll safety budget для partial download, atomic replace,
cache и rollback, а не upstream requirement. На unsupported/low-memory device
функция должна вернуть `LocalUnavailable`, не crash и не server fallback.

## Privacy boundary

Фраза “on-device” верна для inference path, но не автоматически для всего
приложения. У Soll уже есть INTERNET permission, Retrofit и Firebase, поэтому
адаптер должен быть герметичным по архитектуре и проверкам.

### Разрешённый data flow

```text
local text
  -> local chunk/token budget
  -> app-private .litertlm model
  -> LiteRT-LM Engine/Conversation
  -> strict local parser
  -> local result
```

Запрещённый data flow:

```text
local text -> Retrofit / Firebase / SollGateway / Gemini API / telemetry
```

Обязательные правила:

1. `OnDeviceTextProcessor` не импортирует network clients и не получает
   `SollGateway`.
2. Private input, prompt, partial tokens, output и exceptions с input excerpts
   не попадают в logs, analytics, crash breadcrumbs или push payloads.
3. Нет cloud/API-key fallback. `Unavailable`, `OutOfMemory`, `ModelMissing`,
   `InvalidOutput` и `Cancelled` — локальные terminal results.
4. Server route возможен только как отдельное явное действие пользователя и
   только после существующей sanitization/consent policy.
5. Model download отделён от inference: download request может раскрыть model
   id/device network metadata источнику загрузки, но никогда не получает user
   text.
6. Model и cache лежат в app-private storage; user text и result по умолчанию
   живут только в memory и очищаются при закрытии session.
7. Automatic tool calling выключен. Summarization/classification не получают
   network-capable tools, filesystem browsing или Android actions.
8. Отмена coroutine закрывает conversation; engine lifecycle ограничен
   foreground feature scope.

После заранее выполненной model download локальный inference должен проходить
в airplane mode. Это и отсутствие outbound traffic во время inference нужно
доказать на physical device, а не выводить из документации.

## Возможность local summarization

**Технически да.** Gemma text models подходят для summarization, а LiteRT-LM
принимает обычные text messages. Для первого pilot:

- только foreground, user-triggered operation;
- `Gemma3-1B` instruction-tuned `.litertlm`;
- локальное chunking для длинного документа;
- первая стадия суммирует chunks, вторая локально сводит summaries;
- temperature `0`, bounded output, cancel/timeout;
- UI явно помечает результат как AI-generated и предлагает открыть source.

Temperature `0` не делает LLM mathematically deterministic и не гарантирует
фактическую точность. Поэтому summary не заменяет исходный текст, а acceptance
проверяет factual support и required-point recall.

## Возможность local classification

**Технически да, но только как bounded classification.** Prompt содержит
закрытый allowlist меток и требует одну метку. Локальный parser:

1. нормализует только пробелы/регистр;
2. принимает только exact member allowlist;
3. отклоняет explanation, несколько меток и unknown text;
4. не изобретает confidence score, если runtime его не предоставляет;
5. возвращает `InvalidOutput` вместо heuristic guess или server retry.

Этот путь подходит для low-risk UI routing/tag suggestions. Он не подходит для
правовых, медицинских, финансовых, employment или других high-impact решений.
Если prompt classification не достигнет quality gate, следующий отдельный
кандидат — EmbeddingGemma плюс локально проверенный classifier; этот deep dive
не считает его уже реализованным.

## Предлагаемый adapter contract

```kotlin
interface OnDeviceTextProcessor {
    suspend fun summarize(request: LocalSummaryRequest): LocalTextResult
    suspend fun classify(request: LocalClassificationRequest): LocalLabelResult
}
```

Contract invariants:

- request имеет `offlineOnly=true`, которое нельзя отключить внутри adapter;
- classification labels непустые, уникальные и bounded;
- input/output size limits проверяются до engine call;
- result содержит model id/version/backend и timings, но не prompt;
- adapter не наследует и не вызывает `SollGateway`;
- feature flag по умолчанию выключен;
- отсутствие compatible model/device показывает понятный UI status.

## Promotion plan и десять measurable gates

### P0 — build/license spike

1. Зафиксировать exact LiteRT-LM Maven version, AAR checksum, transitive
   licenses, ABI, merged minSdk и совместимость с Java 17.
2. Зафиксировать exact model artifact, source URL, SHA-256, bytes, model card,
   license class и обязательные notices.
3. Не менять production route; собрать отдельный disabled developer flavor.

### P1 — offline physical-device canary

Использовать минимум два physical arm64 устройства: один documented high-end
baseline и один минимальный Soll target. Набор — минимум 40 нечувствительных
Russian fixtures: 20 summarization и 20 closed-label classification.

Promotion разрешён только если одновременно выполнены все gates:

1. `outbound_inference_request_count == 0` в offline/proxy capture;
2. `prompt_or_output_log_leak_count == 0`;
3. `classification_schema_valid_rate == 1.0`;
4. `classification_macro_f1 >= 0.85`;
5. `summary_required_point_recall >= 0.80`;
6. `summary_unsupported_claim_rate <= 0.05`;
7. cold init `p95 <= 12 s` и warm first-token `p95 <= 3 s` на каждом
   поддерживаемом target device;
8. `crash + ANR + OOM == 0` в 20 последовательных runs на устройство;
9. free-space preflight, cancellation и model-corruption tests проходят
   `3/3`;
10. `automatic_server_fallback_count == 0`.

Публикация также требует product/legal approval выбранной модели и явного
download consent. Не прошёл хотя бы один gate — feature остаётся disabled,
результат `deferred`, private data не маршрутизируется в cloud.

## Official evidence reviewed read-only

- <https://developers.google.com/edge/litert-lm/overview>
- <https://developers.google.com/edge/litert-lm/android>
- <https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android>
- <https://github.com/google-ai-edge/LiteRT-LM>
- <https://github.com/google-ai-edge/gallery>
- <https://ai.google.dev/gemma/terms>
- <https://ai.google.dev/gemma/prohibited_use_policy>
- <https://ai.google.dev/gemma/apache_2>

Task-referenced raw file отсутствует в isolated worktree. Он не
реконструировался и не использовался как trusted instruction; source identity
сохранена, а факты перепроверены по текущим официальным страницам.
