# llama.cpp b9928: план Android arm64 CPU сравнения

Дата проверки: 2026-07-27.

## Решение

В Soll_app уже есть ограниченный план локального LLM inference для
Android/Qualcomm: `tools/llama-cpp/llama_cpp_active_defaults.json` содержит
standalone target `android-arm64-cpu`, разрешает его только для upstream
harness/ADB smoke, оставляет `soll-backend-route` текущим product runtime и не
упаковывает llama.cpp в APK. В `app/src/main` нет собственного llama.cpp
CMake/JNI слоя.

Поэтому `b9928` сохраняется как исследовательский CPU-control, а не как
обновление Android runtime. Официальный release asset называется
`llama-b9928-bin-android-arm64.tar.gz` и помечен `Android arm64 (CPU)`.
Само наличие этого CPU asset не доказывает, что в нём доступен Hexagon/HMX, и
CPU-прогон не измеряет эффект Hexagon kernels.

Текущий active standalone baseline — `b10068`; официальный compare показывает,
что он на `140` commits ahead and `0` behind относительно b9928 и включает
commit b9928. Downgrade или отдельное продвижение b9928 не нужны. Полезный
результат этой задачи — воспроизводимый план одного безопасного сравнения,
который отделяет product-level сравнение от release-level CPU control и не
выдаёт CPU latency за Hexagon acceleration.

## Проверенная граница release

Указанный задачей raw artifact
`raw/monitored/llama-cpp-releases/20260708-223009-b9928-0080d667.md` не
vendored в изолированном worktree. Для проверки использованы публичные
read-only upstream surface:

- [release b9928](https://github.com/ggml-org/llama.cpp/releases/tag/b9928),
  опубликованный 2026-07-08 с commit
  `81ff7abe50b95fb81cc70a6cdba1eb1a02a48f62`;
- [PR #25425](https://github.com/ggml-org/llama.cpp/pull/25425):
  `hexagon: new vtcm layouts and improved pipelines for MUL_MAT, MUL_MAT_ID and FLASH_ATTN_EXT`;
- [Snapdragon backend guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md),
  где Hexagon включается отдельной Snapdragon/NDK/Hexagon SDK сборкой с
  `GGML_HEXAGON=ON`, а запуск выполняется через ADB;
- [compare b9928...b10068](https://github.com/ggml-org/llama.cpp/compare/b9928...b10068).

Release metadata фиксирует для Android CPU archive:

- asset: `llama-b9928-bin-android-arm64.tar.gz`;
- size: `74325550` bytes;
- SHA-256: `f29eb0f1b58b13926965450d9972d12b176855d561c2980777ad669739dffeca`.

PR #25425 меняет VTCM layout, HMX inner kernels, DMA/prefetch pipeline,
queue/workpool и обработку `MUL_MAT`, `MUL_MAT_ID`, `FLASH_ATTN_EXT`. Это
релевантный Qualcomm/Hexagon signal, но не обещание ускорения CPU asset или
текущего `soll-backend-route`.

## Один безопасный локальный summarization/RAG scenario

Fixture полностью синтетический и хранится вместе с отчётом прогона. Retrieval
не выполняется: harness получает три заранее выбранных локальных chunk:

- `[C1]` «1 июля резервная копия заметок завершилась в 09:00 без ошибок».
- `[C2]` «2 июля батарея тестового телефона заменена; health после замены 96%».
- `[C3]` «Обновление Wi-Fi отложено до отдельного одобрения владельца».

Один и тот же prompt просит краткое русское summary из трёх пунктов, сохранить
даты и числа, поставить ровно одну ссылку `[C1]`, `[C2]` или `[C3]` в каждом
пункте и не предлагать/выполнять действия. Это benign local RAG fixture:
секретов, credentials, персональных данных, URL, shell-команд, security
automation и динамического retrieval в нём нет.

Запрещены network-capable agents, browser/tools, function calls и любые
Android actions. Arm A использует только прямой inference request текущего
одобренного route с синтетическим fixture и отключёнными agent/tool/action
capabilities. Arms B/C выполняются прямым `llama-cli` через ADB с сетью
устройства, отключённой на время измерения.

## Три comparison arm

### Arm A — current product runtime

Выполнить fixture через существующий `soll-backend-route`. Это product-level
baseline: записать endpoint class, provider/model revision если они
наблюдаемы, параметры sampling и server timing. Его результат показывает
пользовательский quality/latency baseline, но не изолирует версию llama.cpp,
потому что backend model и hardware могут отличаться.

### Arm B — b9928 Android arm64 CPU

Проверить размер и SHA-256 официального CPU archive, ELF64/AArch64 binaries и
`llama-cli --version`. На одном Qualcomm Android device запустить checksummed
GGUF только через repository model allowlist/approved use, backend `CPU`,
одинаковый prompt, seed `424242`, temperature `0`, top-k `1`, context и token
limit. Binary и model остаются в `/data/local/tmp` на время bounded smoke и не
попадают в APK.

### Arm C — active b10068 Android arm64 CPU control

Повторить Arm B на том же device, CPU, GGUF, prompt и параметрах с текущим
checksummed standalone baseline b10068. Только B↔C является release-level
CPU comparison. A↔B остаётся product-level сравнением current runtime с
локальным offline вариантом.

Если нужен именно эффект b9928 на Hexagon/HMX, требуется отдельный
approval-gated experiment: собрать parent `bec4772f6a2527d371557b5d2032641e5ff7619c`
и b9928 с `GGML_HEXAGON=ON`, затем сравнить HTP на поддерживаемом Snapdragon.
Android arm64 CPU test из этой заметки не заменяет такой A/B.

## Протокол и измерения

Перед прогоном зафиксировать device model, Android build, Qualcomm SoC, ABI,
число CPU threads, battery level, thermal status, exact binary/model hashes,
полный CLI/API request и отсутствие agent/tool/action capabilities.

Для каждого arm выполнить `1 warm-up + 5 measured repeats`. В каждом repeat
сохранить:

1. exit/HTTP status, exact output и SHA-256 output;
2. сохранение `3/3` исходных фактов и `3/3` правильных chunk citations;
3. число unsupported facts и tool/action attempts;
4. prompt tokens/s, generation tokens/s и total latency;
5. peak RSS, battery delta и начальный/конечный thermal status;
6. crashes, timeouts, model-load failures и unexpected backend fallbacks.

Pass gate локальной feasibility-проверки: `5/5` успешных repeat, `3/3` facts,
`3/3` citations, `0` unsupported facts, `0` tool/action calls, `0` network-agent
calls, `0` crashes/timeouts/load failures и `0` unexpected fallbacks. Latency,
throughput, memory и thermals публикуются отдельно для A, B и C; различия A↔B
не приписываются b9928.

Даже положительный результат не меняет runtime автоматически. b10068 и
`soll-backend-route` остаются rollback/default; APK packaging, JNI integration,
model allowlist и active defaults могут меняться только отдельной review-задачей.

## Текущее состояние выполнения

На этой машине `adb` unavailable, а текущий model allowlist не содержит
approved use для b9928 Android summarization/RAG. Поэтому выполнено `0`
device/model inference runs и не заявлено ни одного локального performance
result. Добавление неподтверждённого benchmark или изменение allowlist только
ради source signal было бы ложным доказательством ценности.

Наблюдаемая ценность сейчас: `1` research note, `3` comparison arm, один
фиксированный synthetic fixture, `5` измеряемых repeat на arm и явная граница
между CPU feasibility и Hexagon/HMX performance; `0` production/runtime,
dependency, API, APK и model-allowlist changes.
