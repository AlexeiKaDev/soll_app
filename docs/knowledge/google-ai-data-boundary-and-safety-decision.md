---
task_id: 053acb56309a4990a1a25d3a4db19d55
project: soll_app
source_ref: source-item/db74e38e5609/d6c4aab5eaf02a1b
source_trust: untrusted_external_content
raw_ref: raw/monitored\google-ai-for-developers\20260709-203525-on-device-responsible-ai-and-security-8abb4f49.md
raw_status: absent_in_isolated_worktree
official_sources_only: true
reviewed_at: 2026-07-28 Europe/Chisinau
---

# Google AI: data boundary и safety decision для Soll app

## Решение

Google AI stack для Soll состоит из трёх разных контуров:

| Контур | Где выполняется inference | Что им управляет | Решение Soll |
| --- | --- | --- | --- |
| Google AI Edge | на устройстве | приложение поставляет или выбирает model/runtime через LiteRT-LM, LiteRT или MediaPipe | отдельный путь для custom-моделей; не считать Gemini API и не включать без model/device audit |
| Gemini Nano on Android | на совместимом Android-устройстве | ML Kit GenAI вызывает системный AICore; модель, обновления и встроенные input/output safety filters управляются Android/Google | предпочтительный путь для короткой private foreground-обработки |
| Gemini API | в облаке Google | server-side Gemini model и per-request API configuration | только для разрешённого минимизированного cloud payload; private data не получает автоматический fallback |

Google AI Edge — семейство on-device runtimes, а Gemini Nano — конкретный
system-managed Android path. Ни один из них не является локальным режимом
Gemini API.

## Data routing

### Разрешено on-device

После отдельной runtime-интеграции и device canary локально можно обрабатывать:

- выбранные пользователем `private=true` сообщения или короткий видимый фрагмент
  чата для summary;
- локальную заметку, черновик или короткий текст для proofreading, rewriting,
  classification и Prompt API;
- выбранное пользователем изображение для image description или multimodal
  prompt;
- выбранный короткий audio fragment для on-device speech recognition на
  поддерживаемом устройстве.

ML Kit документирует local processing для input, inference и output. AICore
изолирует запросы и не хранит запись prompt или результата после обработки.
Это свойство AICore не освобождает Soll от своей ответственности: raw prompt,
partial output, result и input excerpts в exceptions нельзя писать в Timber,
analytics, crash breadcrumbs или push payload.

`On-device` не означает полное отсутствие служебного network traffic: ML Kit
может получать model/bugfix/hardware-compatibility updates и обрабатывать
metrics data, о чём Soll обязан информировать пользователя. AICore не имеет
прямого Internet access; model downloads проходят через Private Compute
Services. Ни один такой канал не должен получать Soll prompt или output.

Даже on-device route не получает carte blanche на все данные телефона.
Contacts, полный SMS/call history, location history и массовая media library
запрещены по умолчанию и требуют отдельного use-case/data-minimization review.
Credentials, API keys, pairing tokens и authentication material запрещены для
любого model route. Медицинские, юридические, финансовые и другие high-impact
решения нельзя автоматизировать результатом модели.

### Cloud-only capability, но не автоматически cloud-safe data

Cloud-модель нужна, когда задаче требуются:

- большие документы или context, не помещающиеся в лимиты Gemini Nano;
- server-side/cross-device state или несколько удалённых источников;
- актуальная внешняя информация и Google Search/Maps grounding;
- cloud-only tools, function calling, code execution или remote file access;
- background/long-running processing либо одинаковая поддержка на устройствах
  без AICore/Gemini Nano;
- качество сложного reasoning или multimodal video, которого локальный canary
  не подтвердил.

В cloud разрешается передать только public или явно разрешённый,
минимизированный payload. Текущий `ModelChatRequest.safeForServer()` удаляет
`private=true` turns и остаётся минимальным обязательным gate, но не заменяет
PII redaction и consent для attachments. Если локальный capability
`UNAVAILABLE`, private request заканчивается `LocalUnavailable`; он не
санитизируется молча и не уходит в cloud.

Cloud route должен оставаться server-mediated через
`SollGateway.askModelChat(...)`: provider credentials не помещаются в APK.
Для private content запрещены API logging/data sharing, Files API, explicit
context caching и Search/Maps grounding. Обычный paid Gemini API всё равно
может хранить prompts, context и output до 55 дней для abuse monitoring; значит
cloud payload нельзя считать эфемерным. Исключение возможно только после
отдельно подтверждённого для проекта Zero Data Retention режима.

## Обязательные safety controls

### Gemini Nano / Google AI Edge

1. Не обходить встроенные AICore input/output safety filters.
2. До показа результата выполнять Soll-specific validation: bounded input и
   output, allowlist для classification, schema check и safe fallback.
3. Использовать только user-triggered foreground flow. ML Kit GenAI запрещает
   inference, когда приложение не является top foreground app.
4. Не выдавать output за факт и не выполнять Android/server action без явного
   подтверждения пользователя.
5. Проверять prompt quality и safety на Soll-specific и adversarial fixtures для
   каждой поддерживаемой Gemini Nano version/device class.

### Gemini API

1. На каждом запросе явно передавать все четыре категории
   `HARM_CATEGORY_HARASSMENT`, `HARM_CATEGORY_HATE_SPEECH`,
   `HARM_CATEGORY_SEXUALLY_EXPLICIT` и `HARM_CATEGORY_DANGEROUS_CONTENT`.
   Начальная Soll policy — `BLOCK_MEDIUM_AND_ABOVE` для каждой категории.
   Изменение порогов требует documented risk review и regression set.
2. Не полагаться на default: официальный guide указывает `OFF` по умолчанию
   для Gemini 2.5 и 3, а фильтр оценивает probability, не severity.
3. Обрабатывать `promptFeedback.blockReason`, `Candidate.finishReason=SAFETY`
   и `Candidate.safetyRatings` как terminal safe result. Не повторять запрос с
   более слабым threshold и не показывать отсутствующий blocked content.
4. Сохранять narrow system instruction, ограниченный tool allowlist,
   server-side authorization и human confirmation для любого side effect.
5. До release пройти use-case safety benchmark и adversarial/prompt-injection
   suite с заранее зафиксированными acceptance thresholds.
6. Включить per-user rate limit, bounded retries, model/config canary и
   независимый kill switch. Core-harm protections не обходятся.

## Обязательный privacy-safe monitoring

Логи и метрики содержат только route, pseudonymous request/user id, model id и
version, safety-config version, latency, token counts, enum/error code и
агрегаты. Raw prompt/output, private identifiers, attachment content и feedback
text по умолчанию не записываются.

Минимальный dashboard:

- `request_count{route=local|cloud}` и `local_unavailable_count`;
- `private_cloud_attempt_count` с invariant `== 0` и немедленным alert;
- `prompt_block_count{reason}` и
  `response_safety_finish_count{category,probability}`;
- `safety_signal_handled_rate == 1.0`;
- `invalid_or_empty_output_count`, timeout/error/rate-limit counts и latency
  p50/p95;
- model/version/safety-config coverage `== 100%`;
- user thumbs up/down counts через monitored feedback channel;
- `raw_prompt_or_output_log_count == 0`.

Каждое изменение model id, Nano version, prompt или safety threshold проходит
canary и сравнение safety/quality metrics с предыдущей версией. Рост block,
invalid-output или negative-feedback rate останавливает rollout; нарушение
privacy invariants отключает cloud route.

## Promotion boundary

Этот note не разрешает runtime-интеграцию. Текущий repository не содержит ML
Kit GenAI, Firebase AI Logic или Google AI Edge model runtime. Следующий
implementation slice требует отдельного approval, compatible physical device,
offline traffic capture, non-sensitive fixtures и доказательства:

- `private_cloud_attempt_count == 0`;
- `raw_prompt_or_output_log_count == 0`;
- все safety signals обработаны;
- local input/output не появляется в outbound traffic;
- cloud payload проходит private/PII/attachment gates;
- kill switch и monitored feedback работают.

## Official evidence reviewed read-only

- <https://developers.google.com/edge>
- <https://developer.android.com/ai/overview>
- <https://developer.android.com/ai/gemini-nano>
- <https://developers.google.com/ml-kit/genai>
- <https://developers.google.com/ml-kit/genai/prompt/android/get-started>
- <https://developers.google.com/ml-kit/genai/prompt/android/evaluate-prompt>
- <https://developers.google.com/ml-kit/terms>
- <https://ai.google.dev/gemini-api/docs/safety-settings>
- <https://ai.google.dev/gemini-api/docs/safety-guidance>
- <https://ai.google.dev/gemini-api/docs/usage-policies>
- <https://ai.google.dev/gemini-api/docs/logs-policy>
- <https://ai.google.dev/gemini-api/docs/zdr>
