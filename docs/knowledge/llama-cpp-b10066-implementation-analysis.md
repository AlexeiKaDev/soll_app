# llama.cpp b10066: анализ внедрения в Soll app

Дата проверки: 2026-07-19.

## Решение

`b10066` не следует внедрять в Android-приложение или назначать новым default
только по release-сигналу. Изменение релиза узкое: upstream добавил загрузку и
приоритет бинарного OpenCL kernel `kernel_gemm_moe_q6_k_f32_ns` для MoE-моделей
с Q6_K и исправил проверку выбора int8 dp4 kernel для q5_K MoE GEMM. Это
Qualcomm/Adreno OpenCL path, а не общий прирост CPU, Android API или
`llama-server` contract.

Текущий проверенный `b9895` manifest и `soll-backend-route` остаются default.
Production-код, Gradle dependencies, APK и runtime configuration не меняются.
`b10066` становится только benchmark-кандидатом для отдельной задачи, если
появится одобренный Qualcomm/Adreno OpenCL workload с MoE Q6_K/q5_K.

## Ограничение исходника

Запрошенный `wiki/b10066.md` и task source
`monitored/llama-cpp-releases/20260718-023020-b10066-64837e22.md` не vendored в
этом изолированном worktree. Поэтому анализ не приписывает отсутствующему wiki
непроверенные детали. Прочитана доступная task-запись и проверены три первичных
upstream surface:

- [официальный релиз b10066](https://github.com/ggml-org/llama.cpp/releases/tag/b10066);
- [commit `86a9c79f866799eb0e7e89c03578ccfbcc5d808e`](https://github.com/ggml-org/llama.cpp/commit/86a9c79f866799eb0e7e89c03578ccfbcc5d808e);
- [upstream PR #25797](https://github.com/ggml-org/llama.cpp/pull/25797).

Релиз опубликован 17 июля 2026 года и указывает на commit
`86a9c79f866799eb0e7e89c03578ccfbcc5d808e`. Commit меняет только
`ggml/src/ggml-opencl/ggml-opencl.cpp`: 31 добавление и 3 удаления.

## Что именно изменилось

1. OpenCL backend пробует получить `gemm_moe_q6_k_f32_ns_ila` из Adreno binary
   kernel library и строит `cl_program` из бинарного kernel.
2. Если kernel доступен, Q6_K MoE GEMM выбирает его вместо общего
   `kernel_gemm_moe_q6_k_f32_ns`.
3. Для бинарного kernel используется совместимый `CL_R` image layout вместо
   общего `CL_RGBA` layout.
4. Выбор q5_K int8 dp4 больше не зависит от ошибочной проверки наличия
   `kernel_gemm_moe_q4_k_f32_ns_bin`.
5. Для Q6_K бинарный kernel имеет приоритет над dp4 path.

Upstream PR не публикует benchmark, список проверенных SoC, Android
integration result или изменение качества модели. Поэтому release note
подтверждает capability/fix, но не подтверждает ускорение Soll app.

## Пять проверенных seam текущего проекта

| Seam | Факт в репозитории | Следствие для b10066 |
| --- | --- | --- |
| Проверенная версия | `tools/llama-cpp/llama_cpp_b9895_defaults.json` pin-ит `b9895` с checksum-ами | Нельзя заменять проверенный default непроверенным tag |
| Android runtime | policy manifest оставляет `soll-backend-route` default и запрещает упаковку бинарников в APK | Релиз не меняет текущий пользовательский runtime |
| Native integration | в app нет `CMakeLists.txt`, `externalNativeBuild` и собственного llama.cpp JNI | Upstream C++ diff не имеет точки исполнения в приложении |
| Release targets | Android asset — arm64 CPU; OpenCL/Adreno target в текущем manifest — Windows arm64 | Нельзя переносить Windows OpenCL proof на Android |
| Product roadmap | локальный LLM допускается только как отдельный измеряемый spike; ранний heavy LLM запрещён | Нужна отдельная approval-gated benchmark-задача, не скрытый dependency update |

## Возможное внедрение после появления workload

Единственный обоснованный slice — disposable Qualcomm/Adreno benchmark вне
production UI. Он сравнивает upstream parent
`6bdd77f13cf11b264b4231d320afc404f48d576e` и b10066 на одном устройстве,
одной MoE-модели и одинаковых параметрах.

Шесть ворот такого benchmark:

1. Зафиксировать устройство, Android/Windows build, Qualcomm SoC, Adreno GPU и
   OpenCL driver; подтвердить, что binary kernel действительно загружен.
2. Зафиксировать model revision/checksum и доказать наличие MoE Q6_K или q5_K
   матриц; dense или другая quantization не проверяет этот релиз.
3. Замерить prompt processing и generation tokens/s, p50/p95 latency, peak
   memory, thermal state и power на одинаковых prompts минимум в пяти повторах.
4. Проверить output parity и fallback при недоступном/невалидном binary kernel;
   crashes, invalid values и silent wrong-kernel selection должны быть `0`.
5. Требовать минимум `10%` устойчивого выигрыша целевой фазы без регрессии
   качества, памяти, температуры или CPU fallback.
6. Сохранить `soll-backend-route` и b9895 как rollback до отдельного
   review/approval; модель и binaries не включать в APK/AAB.

## Наблюдаемая ценность

- Добавлен `1` implementation-analysis artifact.
- Проверены `3` официальные upstream surface и `5` текущих Soll seam.
- Определены `6` измеримых ворот для релевантного Qualcomm/Adreno benchmark.
- Изменено `0` production-файлов, dependencies, API contracts и runtime routes.
- Выполнено `0` b10066 inference/benchmark runs; измеренная runtime-ценность для
  Soll app остаётся `0` до появления подходящего устройства и workload.
