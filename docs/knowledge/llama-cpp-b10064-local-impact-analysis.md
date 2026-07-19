# llama.cpp b10064: анализ локального влияния на Soll app

Дата проверки: 2026-07-19.

## Решение

`b10064` не следует внедрять в Android-приложение, назначать новым default или
загружать как отдельный runtime только по release-сигналу. Релиз меняет узкий
Qualcomm/Adreno OpenCL path: layout 12-байтовых `scales` репакованных Q4_K весов
транспонируется для coalesced reads в no-shuffle GEMM/GEMV kernels. Upstream
описывает ускорение prefill и token generation для dense Q4_K моделей, а не
общий прирост Android CPU, новый API или изменение качества модели.

Текущий проверенный `b9895` manifest и `soll-backend-route` остаются default.
Production-код, Gradle dependencies, APK и runtime configuration не меняются.
`b10064` имеет локальную ценность только как классифицированный benchmark-сигнал
для отдельного Qualcomm/Adreno Q4_K workload. Репозиторий уже содержит анализ
более позднего `b10066` для соседнего, но отличающегося MoE OpenCL/Adreno
workload. Поэтому общий device/driver harness будущих проверок нужно объединить
в одну актуальную approval-gated benchmark matrix с отдельными dense Q4_K и MoE
строками, а не продвигать `b10064` как runtime самостоятельно.

## Ограничение исходника

Запрошенный `wiki/b10064.md` и task source
`monitored/llama-cpp-releases/20260718-023020-b10064-2e624b01.md` не vendored в
этом изолированном worktree. Поэтому анализ не приписывает отсутствующему wiki
непроверенные детали. Прочитана доступная task-запись и проверены три первичных
upstream surface:

- [официальный релиз b10064](https://github.com/ggml-org/llama.cpp/releases/tag/b10064);
- [commit `86d86ed4396b4130922f7b9af26e3d9fc11a591b`](https://github.com/ggml-org/llama.cpp/commit/86d86ed4396b4130922f7b9af26e3d9fc11a591b);
- [upstream PR #25805](https://github.com/ggml-org/llama.cpp/pull/25805).

Релиз опубликован 17 июля 2026 года и указывает на commit
`86d86ed4396b4130922f7b9af26e3d9fc11a591b`. Commit меняет `4` файла только в
`ggml/src/ggml-opencl`: `49` добавлений и `38` удалений. Его parent для
изолированного A/B сравнения —
`7d56da7e546f54fb1fa54ef2bc9ad9a872860ab0`.

## Что именно изменилось

1. При загрузке Q4_K tensor OpenCL backend теперь транспонирует буфер `s`
   (`scales`) вместе с уже репакуемыми `q`, `d` и `dm`; для новой 8-bit
   transpose операции driver может сам выбрать local workgroup size.
2. При выгрузке tensor `s` транспонируется обратно во временный buffer и именно
   этот восстановленный buffer передаётся в `kernel_restore_block_q4_K_noshuffle`.
3. `gemm_noshuffle_q4_k_f32.cl` читает scale/min codes с новым stride, чтобы
   соседние work-items обращались к соседним адресам.
4. Та же адресация добавлена в dp4a kernel
   `gemm_noshuffle_q4_k_q8_1_dp4a.cl`.
5. Generation path `gemv_noshuffle_q4_k_f32.cl` также читает
   транспонированные scales через stride.
6. PR ограничивает ожидаемый эффект dense-моделями с Q4_K: заявлен прирост
   prefill и token generation, качественно «massive» для `A7x` при выключенном
   по умолчанию dp4a и «moderate» для более новых GPU с включённым dp4a.

Upstream PR не публикует числовой benchmark, точный список устройств и
драйверов, число повторов, memory/thermal/power результат или проверку качества
вывода. Поэтому он подтверждает механизм оптимизации, но не измеримый выигрыш
Soll app.

## Шесть проверенных seam текущего проекта

| Seam | Факт в репозитории | Следствие для b10064 |
| --- | --- | --- |
| Проверенная версия | `tools/llama-cpp/llama_cpp_b9895_defaults.json` pin-ит `b9895` с размерами и SHA-256 | Нельзя заменять проверенный default непроверенным tag |
| Android runtime | manifest оставляет `soll-backend-route` default и задаёт `packageIntoAndroidApp: false` | Релиз не меняет текущий пользовательский runtime |
| Native integration | в production app нет `CMakeLists.txt`, `externalNativeBuild` и собственного llama.cpp JNI | Upstream C++/OpenCL diff не имеет точки исполнения в приложении |
| Release targets | Android asset — arm64 CPU, а OpenCL/Adreno asset — Windows arm64 | Релиз не предоставляет доказательство Android OpenCL эффекта |
| Применимый workload | в проекте не закреплены Adreno driver/device и dense Q4_K модель для OpenCL no-shuffle path | Нечего безопасно benchmark-ить в рамках этой задачи |
| Соседний сигнал | `docs/knowledge/llama-cpp-b10066-implementation-analysis.md` уже оставляет отличающийся MoE OpenCL/Adreno workload benchmark-кандидатом | Device/driver harness можно переиспользовать, сохраняя отдельные dense Q4_K и MoE строки |

## Возможный benchmark после появления workload

Единственный обоснованный следующий slice — disposable benchmark вне
production UI. Для доказательства именно b10064 он сравнивает upstream parent
`7d56da7e546f54fb1fa54ef2bc9ad9a872860ab0` и b10064 на одном устройстве,
одной dense Q4_K модели и одинаковых параметрах.

Шесть ворот такого benchmark:

1. Зафиксировать Windows arm64 устройство, Qualcomm SoC, Adreno GPU, OpenCL
   driver и доказать выбор Q4_K no-shuffle OpenCL kernels.
2. Зафиксировать model revision/checksum и наличие dense Q4_K weights; CPU,
   MoE и другая quantization не проверяют этот релиз.
3. На одинаковых prompts минимум в пяти повторах измерить prefill и generation
   tokens/s, p50/p95 latency, peak memory, thermal state и power.
4. Проверить output parity, set/get tensor round-trip и fallback; crashes,
   invalid values и повреждение scales должны быть `0`.
5. Требовать минимум `10%` устойчивого выигрыша целевой фазы без регрессии
   качества, памяти, температуры или fallback path.
6. Сохранить `soll-backend-route` и b9895 как rollback до отдельного
   review/approval; модель и binaries не включать в APK/AAB.

## Наблюдаемая ценность

- Добавлен `1` local-impact analysis artifact.
- Проверены `3` официальные upstream surface и `6` текущих Soll seam.
- Определены `6` измеримых ворот применимого Qualcomm/Adreno Q4_K benchmark.
- Изменено `0` production-файлов, dependencies, API contracts и runtime routes.
- Выполнено `0` b10064 inference/benchmark runs; измеренная runtime-ценность для
  Soll app остаётся `0` до появления подходящего устройства и workload.
