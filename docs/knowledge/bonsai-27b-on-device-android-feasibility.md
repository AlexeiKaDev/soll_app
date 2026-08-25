# Bonsai 27B on-device для `soll_app`: техническое заключение

Статус: глубокий анализ и план проверочного пилота. Production-интеграция
локальной LLM в Android **не одобрена** этим документом.

Источник задачи: статья Хабра **«ИИ Qwen3.6-27B запустили на смартфоне:
1 бит на вес и 90% интеллекта оригинала»**, 2026-07-15,
<https://habr.com/ru/articles/1059572/> (передана короткой ссылкой
<https://share.google/ljy8zNYi3CZCXnTcT>).

Task reference: `task:chat:962471563b17ded7b120`, `source_ref=android_app`.

## Короткий ответ

**Технически попробовать Bonsai 27B на Android можно, но встраивать его в
production-версию Soll app сейчас нельзя.** Разумный следующий шаг — отдельный
opt-in text-only spike на одном названном флагманском Android-устройстве:
`arm64-v8a`, 1-bit GGUF, upstream `llama.cpp`, контекст 4K, без vision tower и
без speculative drafter. Текущий серверный путь остается стандартным.

Причины такого решения:

1. 1-bit веса действительно занимают около 3,9 ГБ, но измеренный PrismML пик
   text-only процесса уже около 5,2 ГБ при контексте 4K. Это не включает весь
   Android UI, существующие ONNX/Sherpa-компоненты Soll и запас ОС.
2. Официальные 11 токенов/с получены на iPhone 17 Pro Max через MLX Swift. У
   релиза нет опубликованного PrismML Android APK/AAR или Android-бенчмарка.
3. Upstream `llama.cpp` поддерживает Android и формат Q1_0 на CPU/NEON и
   Vulkan, поэтому bounded spike реалистичен. Но переносимость runtime еще не
   доказывает, что именно 27B-модель стабильно работает в Soll на нашем
   целевом телефоне.
4. «90% интеллекта» — усредненное vendor-измерение, а не гарантия каждого
   сценария. На наиболее важных для Soll agent/tool-calling тестах результат
   падает с 80,0 до 66,03; instruction following — с 78,47 до 65,74; vision —
   с 72,61 до 59,57.
5. В текущем приложении уже есть безопасный backend-mediated контракт
   `SollGateway.askModelChat(...)`. Новый локальный runtime должен быть еще
   одним сменным engine за явным роутером, а не второй независимой системой
   чата, инструментов и политик.

Итоговое решение:

| Вариант | Решение сейчас | Почему |
| --- | --- | --- |
| Добавить Bonsai 27B в APK/AAB | **нет** | файл 3,8–3,9 ГБ, обновление и ABI нельзя связывать с релизом приложения |
| Сделать 1-bit text-only лабораторный Android spike | **условно да** | Android binding `llama.cpp` и Q1_0 существуют, но нужны реальные замеры |
| Включить vision, 262K и speculative decoding в первый spike | **нет** | дополнительные 0,63/1,79 ГБ и непроверенная мобильная цена |
| Заменить текущий серверный модельный путь | **нет** | сервер остается качественным fallback и владельцем сложного agent routing |
| Выполнять tool calls прямо из вывода модели | **нет** | только предложение, затем детерминированная policy и существующее подтверждение |

## Что именно заявлено в статье и что подтверждено

Анализ выполнен 2026-07-18. Статья сверена с announcement PrismML, model cards,
whitepaper/demo repository и актуальной Android-документацией `llama.cpp`.
Бенчмарки PrismML остаются измерениями производителя: независимого Android
воспроизведения в рамках этой задачи нет.

| Утверждение | Проверка | Вывод для Soll |
| --- | --- | --- |
| Binary Bonsai 27B — 3,9 ГБ и 1,125 bit/weight | model card указывает 3,79 ГБ Q1_0, знак плюс FP16 scale на 128 весов | правдоподобно и подходит только как весовой footprint |
| Ternary — 5,9 ГБ | 5,9 ГБ — идеальный размер при 1,71 effective bpw; текущий Q2_0 хранит значения в 2-bit slots и занимает около 7,2 ГБ | статья смешивает идеальный и реально развернутый размер |
| 90% исходной модели | 76,11 / 85,07 = 89,47% по среднему 15 benchmark scores | маркетинговое агрегирование, не SLA для чата или tools |
| 262K контекст на телефоне | vendor указывает около 9,4 ГБ peak с 4-bit KV для полного окна, text-only | техническая возможность формата не равна безопасному Android-бюджету |
| 11 токенов/с на смартфоне | опубликовано для iPhone 17 Pro Max через MLX Swift | нельзя переносить число на Android CPU/GPU |
| Мультимодальность | vision tower поставляется отдельно, около 0,63 ГБ | не включать в первый spike; vision сильнее всего просел по качеству |
| Tool calling сохранен | формат и шаблон поддерживаются, но category score падает на 13,97 пункта | все действия обязаны оставаться за deterministic Soll policy |
| Android поддерживается | PrismML перечисляет iOS XCFramework, но не Android artifact; upstream `llama.cpp` имеет Android binding | возможна инженерная сборка, готового PrismML Android продукта нет |

Важная терминологическая поправка: в binary-варианте один sign bit плюс один
FP16 scale на группу из 128 весов дает `1 + 16/128 = 1,125` bit/weight. Это
низкобитное хранение весов, а не «вся модель работает на одном бите»: scale,
нормализации, активации, runtime buffers, KV cache и optional vision/drafter
имеют другую точность и отдельно расходуют память.

## Реальный memory envelope

Ниже — опубликованные PrismML значения для language model. Они нужны как
верхнеуровневая отправная точка, но не заменяют Android `PSS`, LMK и thermal
измерения на целевом устройстве.

| Компонент / режим | Опубликованный размер или peak | Решение первого spike |
| --- | ---: | --- |
| 1-bit Q1_0 language weights | 3,79 ГБ на диске/resident weights | использовать |
| 1-bit, context 4K | около 5,2 ГБ peak | стартовый предел |
| 1-bit, context 10K | около 5,6 ГБ peak | проверять только после 4K |
| 1-bit, context 100K без KV compression | около 11,6 ГБ peak | запретить |
| 1-bit, context 100K с 4-bit KV | около 6,8 ГБ peak | не обещать до отдельного теста |
| 1-bit, полный 262K с 4-bit KV | около 9,4 ГБ peak | не mobile-production budget |
| 4-bit vision tower | около 0,63 ГБ на диске | выключить |
| DSpark Q4_1 drafter | около 1,79 ГБ на диске | выключить |
| Ternary Q2_0, context 4K | около 8,4 ГБ peak | исключить из phone spike |

Android не задает универсальный «6 ГБ на приложение» по аналогии с цифрой для
iOS из статьи. Пределы и поведение зависят от устройства: ART heap, native
allocations, memory-mapped pages, GPU buffers, доступная RAM/zRAM и low-memory
killer учитываются по-разному. Поэтому `largeHeap=true` не является решением.
Перед загрузкой нужны `ActivityManager.MemoryInfo`, `memoryClass`, PSS baseline,
свободное место и thermal state; во время работы — PSS plateau, LMK/native
crash, latency и unload evidence.

Реалистичная начальная политика: только явно разрешенный телефон с 16 ГБ RAM
как безопасный кандидат, а 12 ГБ считать экспериментальным до фактического
30-минутного soak. Это не новый системный requirement для всего Soll app:
устройства, не прошедшие gate, продолжают использовать сервер.

## Состояние текущего `soll_app`

Проверка выполнена в разрешенном Android-репозитории, без чтения секретов и
без внешнего Soll runtime.

| Текущий seam | Факт в репозитории | Следствие |
| --- | --- | --- |
| Android build | `minSdk=26`, `targetSdk=34`, `compileSdk=36` | modern arm64 spike возможен, но minSdk не означает достаточную RAM |
| Native build | нет `CMakeLists.txt`, `externalNativeBuild` и собственного LLM JNI | потребуется новый изолированный NDK contour, его нельзя прятать в обычной Kotlin-правке |
| Existing native ML | ONNX Runtime Android и `sherpa-onnx.aar` уже поставляют JNI для TTS/STT | LLM конкурирует с существующей native memory; одновременно держать engines нельзя по умолчанию |
| Model chat | `askModelChat` санитизирует private messages и вызывает серверный `/assistant/ask` | сохранить контракт и добавить engine/router только после spike |
| Production chat | `ChatViewModel` использует `sendChatTurn`, а `askModelChat` пока не подключен к экрану | сначала выбрать один пользовательский сценарий, не создавать второй чат |
| Tool safety | chat actions проходят `SollChatActionPolicyRegistry` и server contract | raw model output никогда не исполняется напрямую |
| Product scope | roadmap содержит `No heavy local LLM on Android in early phases` | пилот должен явно оставаться экспериментальным и удаляемым |

Наиболее полезные сценарии Bonsai для Soll:

- приватное offline summary явно выбранной заметки;
- offline Q&A по короткому, локально переданному контексту;
- черновик названия/описания задачи, который пользователь подтверждает;
- ограниченный fallback чата без сети с явной меткой «только на устройстве».

Не подходят для первого внедрения:

- always-on ассистент и фоновая переработка всех источников;
- автономный агент, который сам выполняет Android/server tools;
- vision/camera/screenshot analysis;
- полная история чата или 262K-контекст;
- одновременная LLM + heavy TTS/STT/media-сессия;
- незаметный fallback с локального режима в облако.

## Целевая архитектура после успешного spike

```text
Chat / explicit Offline Assistant UI
              |
              v
       ModelExecutionRouter
       |                   |
       |                   +--> ServerModelEngine
       |                        (existing SollGateway path)
       v
 OnDeviceBonsaiEngine
       |
       v
 isolated :local_llm process / bound visible service
       |
       +--> ModelManager
       |    pinned manifest, resumable download, SHA-256/signature,
       |    atomic install, no-backup private storage, delete/rollback
       |
       +--> llama.cpp JNI, arm64-v8a, Q1_0, context=4096
       |
       +--> token Flow + cancellation + memory/thermal telemetry
              |
              v
      proposed text / proposed tool call
              |
              v
 deterministic Soll policy -> explicit confirmation -> existing executor
```

### Контракты, которые нельзя смешивать

1. **`ModelEngine`.** Один интерфейс для streaming text, cancel, model metadata
   и failure reason. Серверная и локальная реализации возвращают одинаковый
   продуктовый результат, но явно сообщают route.
2. **`ModelExecutionPolicy`.** Учитывает пользовательский выбор, offline state,
   совместимость, наличие модели, память, батарею и thermal state. Если выбран
   privacy-only local route, cloud fallback запрещен без нового согласия.
3. **`ModelManager`.** Модель не входит в Git/APK/AAB. Download — отдельный
   opt-in артефакт с pin версии, размером, license/NOTICE, checksum, временным
   файлом, atomic rename, rollback и кнопкой полного удаления.
4. **`OnDeviceInferenceService`.** Работа идет вне main thread и желательно в
   отдельном `:local_llm` процессе, чтобы native OOM/crash не унес основную UI
   сессию. Длинная видимая генерация имеет notification/cancel; unload
   срабатывает по завершению, idle timeout и критическому memory trim.
5. **`ToolProposalGateway`.** Модель может вернуть только proposal. Парсер
   проверяет schema, allowlist, capability и аргументы; существующий action
   policy и пользовательское подтверждение остаются обязательными.

Не следует копировать 3,9 ГБ в `assets/`, включать `largeHeap`, хранить модель
в backup, открывать произвольные GGUF из внешнего storage или позволять модели
выбирать cloud route и tools через prompt-only правила.

## Поэтапный план

### P0 — воспроизводимый compatibility spike вне production UI

Отдельная реализационная задача должна зафиксировать:

- один точный телефон, Android build, SoC/GPU и объем RAM;
- один commit upstream `llama.cpp` и Android NDK/CMake toolchain;
- `prism-ml/Bonsai-27B-gguf/Bonsai-27B-Q1_0.gguf` с точным revision и SHA-256;
- `arm64-v8a`, text-only, CPU/NEON baseline, Vulkan только как второй профиль;
- context 4096, max output 512, без vision, drafter и tool execution;
- frozen набор русских prompts и ожидаемые machine-checkable ответы.

Сначала модель запускается в официальном `examples/llama.android` или
минимальном disposable harness. До успешного load/generate/cancel/unload и
повторяемого benchmark production-код Soll не меняется.

### P1 — экспериментальный engine в Soll

Только если P0 прошел:

1. добавить изолированный `ModelEngine` и `ModelExecutionRouter`;
2. реализовать pin/checksum/atomic model manager, не включая веса в приложение;
3. показать compatibility, download size, storage, privacy route и remove UI;
4. добавить foreground-visible generation с cancel и process recovery;
5. подключить один сценарий — offline summary выбранного текста;
6. оставить server route default и remote kill switch для эксперимента;
7. не добавлять vision и реальные tool calls.

### P2 — Soll-specific evaluation

Замороженный набор минимум из 60 неперсональных заданий:

- русский диалог и factual Q&A;
- краткое/длинное summary;
- извлечение структурированного task draft;
- instruction following с конфликтующими ограничениями;
- JSON/tool proposal без выполнения;
- prompt-injection, unsafe action и privacy-route проверки.

Ответы локального engine сравниваются с текущим server route и утвержденными
ожиданиями. Vendor average не используется как acceptance threshold.

### P3 — ограниченное продвижение

Только после прохождения всех gates включить opt-in для allowlist устройств.
Расширять context, vision или tools можно отдельными задачами с новым memory,
quality и safety baseline. Общий rollout без device allowlist не допускается.

## Восемь ворот продвижения

1. **Совместимость.** 20 из 20 cold load/generate/cancel/unload циклов проходят
   на точном target device; нет native crash, ANR, corrupted output или утечки
   file descriptors/threads.
2. **Память.** 30-минутный 20-turn soak не вызывает OOM/LMK; Android не
   сообщает `lowMemory`; PSS выходит на plateau, рост после прогрева меньше
   10%, а unload возвращает основную часть LLM delta.
3. **Скорость.** При frozen 1K-input/256-output prompt p50 decode не ниже
   8 tok/s, p95 time-to-first-token не выше 10 с; CPU и Vulkan измеряются
   отдельно, цифра iPhone не используется.
4. **Тепло и батарея.** 10 минут непрерывной генерации не достигают Android
   thermal status `SEVERE`, не теряют более 25% decode throughput и имеют
   зафиксированный расход battery/Wh, приемлемый владельцем устройства.
5. **Качество.** Минимум 85% из 60 Soll prompts получают приемлемую слепую
   оценку; structured task drafts валидны не менее чем в 95% случаев; ни один
   safety/privacy case не считается успешным только из-за красивого текста.
6. **Tool safety.** 100% model outputs проходят deterministic parser/policy;
   0 из минимум 50 adversarial prompts выполняют действие без allowlist,
   capability check и явного подтверждения.
7. **Privacy/offline.** При заблокированной сети local-only сценарий завершает
   frozen prompts без socket/HTTP запросов; cloud fallback требует отдельного
   видимого согласия; удаление стирает model и локальный prompt cache.
8. **Доставка и откат.** Прерванная/поврежденная загрузка никогда не становится
   active model; checksum/signature, atomic activation, version pin, NOTICE,
   remove, rollback и server fallback проходят automated/instrumented tests.

Провал любого gate оставляет Bonsai только исследовательским артефактом.

## Основные риски

| Риск | Вероятность / ущерб | Контроль |
| --- | --- | --- |
| Android-путь не воспроизводит iPhone 11 tok/s | высокая / высокий UX ущерб | сначала CPU/NEON baseline и реальный benchmark, затем опциональный Vulkan |
| Native OOM или LMK при 5+ ГБ | высокая / критический | allowlist устройства, отдельный процесс, 4K cap, unload и PSS/LMK gates |
| Tool/instruction качество хуже среднего headline | высокая / критический | Soll-specific corpus, deterministic policy, no direct execution |
| Формат/runtime быстро меняются | средняя / высокий maintenance | pin commits/model revision, adapter boundary, kill switch, rollback |
| 4 ГБ download поврежден или съедает storage | средняя / высокий | resumable staging, space gate, hash/signature, explicit delete |
| APK/release раздувается | высокая / высокий | weights никогда не входят в APK/AAB или Git |
| Локальный режим ошибочно считается полностью приватным | средняя / высокий | network-denied test, route label, no logs/backups, explicit cloud consent |
| Vision/long context обещаны раньше измерений | высокая / средний | text-only 4K first; отдельные promotion tasks |
| LLM конкурирует с ONNX TTS/STT/media | высокая / высокий | resource arbiter; heavy engines не resident одновременно по умолчанию |
| Лицензия/NOTICE теряются при model update | низкая / высокий | pinned manifest, Apache 2.0 LICENSE/NOTICE и update review |

## Решение для roadmap

Принять статью как сильный сигнал для **изолированного Android compatibility
spike**, но не как доказательство готовности production-интеграции. Не менять
Gradle, Manifest, NDK, UI и runtime в рамках исследовательской задачи.

Следующая реализационная задача допустима только после выбора точного target
device. Ее минимальный результат: reproducible `arm64-v8a` Q1_0 text-only
benchmark, frozen prompts, PSS/LMK/thermal/battery report и решение по восьми
gates. Без этого измеренная Android-ценность равна нулю.

### Named target preflight — 2026-08-25

Physical ADB preflight выбрал точный target: **DOOGEE S200 Plus / Android 15**
(`M24PST`, SoC `MT6878`, GPU family `mali`, ABI `arm64-v8a`). Устройство
сообщило `MemTotal=15,889,132 kB`, `MemAvailable=11,215,128 kB` на момент
замера и `383 GB` свободно в `/data`; battery state был `43%`, USB powered,
температура `37.0 C`. Это снимает старый blocker отсутствующего named 16-GB
Android target и подтверждает статическую вместимость download/harness, но не
доказывает load, peak PSS, LMK safety, скорость или thermal stability.

Модель не скачивалась, NDK/JNI runtime не добавлялся и inference не запускался:
следующая граница остается отдельным явным opt-in на загрузку примерно
`3.8 GB` и disposable upstream Android harness. Production Soll по-прежнему
не меняется до прохождения всех восьми gates. Санитизированный preflight:
`Soll/outputs/android-smoke/bonsai-s200-plus-preflight-20260825.md`.

## Первичные источники

- PrismML announcement и category benchmarks:
  <https://prismml.com/news/bonsai-27b>
- PrismML 1-bit GGUF model card, memory, components и limitations:
  <https://huggingface.co/prism-ml/Bonsai-27B-gguf>
- PrismML ternary GGUF model card, ideal vs deployed size:
  <https://huggingface.co/prism-ml/Ternary-Bonsai-27B-gguf>
- Whitepaper и reproducible demo repository:
  <https://github.com/PrismML-Eng/Bonsai-demo>
- Upstream binary/ternary backend status и prebuilt platform matrix:
  <https://github.com/PrismML-Eng/Bonsai-demo#upstream-status-for-binary>
- Upstream `llama.cpp` Android binding/build guide:
  <https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md>
- Android memory management:
  <https://developer.android.com/topic/performance/memory-overview>
- Android long-running local ML work:
  <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>

Все численные показатели модели в этом документе помечены как данные
производителя до воспроизведения на target Android device.
