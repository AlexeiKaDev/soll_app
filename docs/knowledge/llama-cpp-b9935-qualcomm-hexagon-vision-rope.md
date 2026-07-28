# llama.cpp b9935: Qualcomm Hexagon VISION RoPE

Дата проверки: 2026-07-28.

## Короткое решение

PR [#25216](https://github.com/ggml-org/llama.cpp/pull/25216) применим
**только для локального on-device inference и vision preprocessing** на
устройствах с Qualcomm Hexagon. Изменение добавляет в Hexagon backend поддержку
VISION RoPE для Qwen2-VL/Qwen3-VL vision encoder и исправляет обработку
strided/non-contiguous tensor rows.

Это локальная вычислительная оптимизация. Она не разрешает сетевое сканирование,
поиск устройств или источников в сети и не разрешает скрытый сбор данных.
Изменённый upstream diff не добавляет camera capture, telemetry, upload или
другую передачу содержимого. Любой будущий Soll-сценарий обязан принимать только
явно выбранный пользователем локальный input и не должен расширять эту границу.

## Проверенный upstream scope

Указанный задачей monitored artifact
`raw/monitored/llama-cpp-releases/20260709-233427-b9935-02f5a101.md`
отсутствует и в repo-root, и под `Soll/` этого изолированного worktree. Поэтому
заметка проверена по двум официальным upstream surface:

- [release b9935](https://github.com/ggml-org/llama.cpp/releases/tag/b9935);
- [PR #25216](https://github.com/ggml-org/llama.cpp/pull/25216) и его diff.

PR был merged 9 июля 2026 года commit `f2d1c2f` после пяти commits. Diff меняет
только `ggml/src/ggml-hexagon/ggml-hexagon.cpp` и
`ggml/src/ggml-hexagon/htp/rope-ops.c`:

- VISION RoPE разрешён, когда rotation охватывает полную половину row;
- Qwen2-VL/Qwen3-VL vision sections получают независимый theta reset;
- src0 и dst могут быть row-strided при contiguous elements внутри row;
- DMA row payload отделён от DDR row stride;
- non-contiguous dst записывается с отдельным stride и исправленным SPAD pitch.

Это не общий Android, CPU, GPU или camera feature и не готовый рецепт
интеграции модели.

## Граница Soll_app

Текущий `llama_cpp_active_defaults.json` закрепляет более новый b10068, содержит
только Android arm64 CPU и Windows x64 CPU targets, сохраняет
`packageIntoAndroidApp: false` и направляет Android chat через
`soll-backend-route`. В Soll_app нет активного Qualcomm Hexagon target, поэтому
production code, dependencies, API и release defaults не меняются.

Возвращаться к реализации следует только для одобренного локального
Qualcomm/Hexagon vision workload. Минимальная проверка должна зафиксировать
device/DSP и exact llama.cpp build, выполнить VISION RoPE на contiguous,
strided и non-contiguous fixtures, сравнить результат с CPU reference и
подтвердить `0` network scans, `0` uploads, `0` hidden collection и
`0` unexpected fallback. До этого b9935 остаётся KB-сигналом без runtime rollout.

## Наблюдаемая ценность

- Добавлена `1` короткая Soll_app KB-заметка.
- Проверены `2` official upstream surface, `5` commits и `2` изменённых файла.
- Зафиксированы `4` privacy/safety запрета для будущего локального сценария.
- Изменено `0` production/runtime файлов; выполнено `0` on-device inference runs.
