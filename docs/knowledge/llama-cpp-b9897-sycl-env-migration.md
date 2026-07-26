# llama.cpp b9897: миграция переменных окружения SYCL

Дата проверки: 2026-07-19.

## Решение для Soll app

Релиз `b9897` не нужно внедрять в Android-приложение или активный AI-core
Soll. Изменение относится только к конфигурации SYCL backend в `llama.cpp`,
тогда как текущий Android runtime использует `soll-backend-route`, а локальный
AI-core работает через WSL `vLLM` с `safetensors`. В production app нет
`CMakeLists.txt`, `externalNativeBuild`, JNI/libllama или встроенного GGUF
runtime.

На текущей машине также не найдено GGUF-моделей в `D:/AI/Models`. Поэтому
компиляция произвольного SYCL binary без Intel SYCL target, модели и
воспроизводимого workload не проверила бы пользовательский путь Soll. Build и
inference smoke сознательно не выдаются за выполненные.

## Что изменил upstream

[Официальный релиз b9897](https://github.com/ggml-org/llama.cpp/releases/tag/b9897)
указывает на commit `26145b3` и merged
[PR #25042](https://github.com/ggml-org/llama.cpp/pull/25042). PR заменил три
отрицательные переменные окружения на положительные и сохранил прежнее
поведение по умолчанию:

| До b9897 | Начиная с b9897 | Эквивалентное значение |
| --- | --- | --- |
| `GGML_SYCL_DISABLE_OPT=0` | `GGML_SYCL_ENABLE_OPT=1` | оптимизации включены |
| `GGML_SYCL_DISABLE_OPT=1` | `GGML_SYCL_ENABLE_OPT=0` | оптимизации выключены |
| `GGML_SYCL_DISABLE_GRAPH=1` | `GGML_SYCL_ENABLE_GRAPH=0` | SYCL Graph выключен |
| `GGML_SYCL_DISABLE_GRAPH=0` | `GGML_SYCL_ENABLE_GRAPH=1` | SYCL Graph включен |
| `GGML_SYCL_DISABLE_DNN=0` | `GGML_SYCL_ENABLE_DNN=1` | oneDNN включен |
| `GGML_SYCL_DISABLE_DNN=1` | `GGML_SYCL_ENABLE_DNN=0` | oneDNN выключен |

Старые `GGML_SYCL_DISABLE_*` имена больше не читаются изменённым кодом. Для
явной конфигурации правило миграции одно: `ENABLE = 1 - DISABLE`. Кроме того,
compile-time macro `GGML_SYCL_USE_VMM` переименован в
`GGML_SYCL_SUPPORT_VMM`; это не переменная окружения.

## Проверка применимости

| Поверхность | Фактический runtime | Результат b9897 |
| --- | --- | --- |
| Soll AI-core | WSL `vLLM`, NVIDIA/CUDA, `safetensors` | не применимо |
| Android app | backend-mediated `soll-backend-route` | не применимо |
| Intel GPU / SYCL | нет закреплённого target device и workload | build отложен до появления target |

Release page перечисляет готовые artifacts для Linux, Windows, Android и
других платформ. Этот список не является доказательством, что b9897 добавил
поддержку всех этих платформ, и не означает, что они встроены в Soll app.

## Если SYCL станет target

Перед обновлением нужно:

1. Зафиксировать Intel GPU, driver, oneAPI/DPC++ version, модель GGUF и SHA-256.
2. Сохранить полный набор текущих `GGML_SYCL_*` переменных и перевести три
   `DISABLE_*` значения по таблице выше.
3. Собрать parent и `b9897` одинаковым toolchain, затем подтвердить выбранный
   SYCL device в startup log.
4. Выполнить одинаковые prompts минимум в пяти повторах и измерить load time,
   prefill/generation tokens/s, p50/p95 latency, peak memory и output parity.
5. Не менять `soll-backend-route` до отдельного review и rollback test.

## Наблюдаемая ценность

- Проверены release, merged PR и patch из первичного upstream.
- Зафиксированы `3` переименования с `6` явными mapping-вариантами.
- Проверены `3` поверхности Soll; текущих SYCL execution seam найдено `0`.
- Изменено `0` production/runtime файлов и выполнено `0` фиктивных benchmark.
