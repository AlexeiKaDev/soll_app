# llama.cpp b10068: оценка локального применения в Soll app

Дата проверки: 2026-07-19.

## Решение

`b10068` не следует внедрять в Android-приложение, назначать новым default или
подменять им текущий backend route только по release-сигналу. Это узкое
исправление корректности DFlash speculative decoding: при инъекции K/V в
квантизированный draft-context cache upstream теперь применяет Hadamard rotation
до записи в cache. Изменение не является общим ускорением local inference,
обычной генерации или MTP speculative decoding.

Текущий проверенный `b9895` manifest и `soll-backend-route` остаются default.
Production-код, Gradle dependencies, APK и runtime configuration не меняются.
В текущем репозитории найдено `0` runtime/config упоминаний `draft-dflash`,
`--cache-type-k-draft`, `--cache-type-v-draft` или
`LLAMA_ATTN_ROT_DISABLE`, поэтому непосредственной точки локального применения
нет.

Если Soll позже запустит отдельный локальный `llama-server` с DFlash и
квантизированным draft K/V cache, b10068 становится correctness-кандидатом для
approval-gated A/B проверки. До такой проверки безопасные варианты — оставить
проверенный backend route или использовать f16/bf16 draft cache в том отдельном
harness; это не изменение текущего Android runtime.

## Ограничение исходника

Запрошенный `wiki/b10068.md` и task source
`monitored/llama-cpp-releases/20260719-030008-b10068-2ceec587.md` не vendored в
этом изолированном worktree. Поэтому анализ не приписывает отсутствующему wiki
непроверенные детали. Прочитана доступная task-запись и проверены четыре
первичные upstream surface:

- [официальный релиз b10068](https://github.com/ggml-org/llama.cpp/releases/tag/b10068);
- [commit `571d0d540df04f25298d0e159e520d9fc62ed121`](https://github.com/ggml-org/llama.cpp/commit/571d0d540df04f25298d0e159e520d9fc62ed121);
- [upstream PR #25823](https://github.com/ggml-org/llama.cpp/pull/25823);
- [исходный bug report #25725](https://github.com/ggml-org/llama.cpp/issues/25725).

Релиз опубликован 18 июля 2026 года и указывает на commit
`571d0d540df04f25298d0e159e520d9fc62ed121`. Commit с parent
`4937ca83f4f3da63004943fe05d8aa4f0217d238` меняет только
`src/models/dflash.cpp`: 17 добавлений и 0 удалений.

## Что именно исправлено

1. В DFlash graph перед копированием в cache берутся `self_k_rot` и
   `self_v_rot`, включая отдельные `_swa` варианты для iSWA path.
2. Инъецированные `Kcur` и `Vcur` переводятся в rotated cache space через
   `llama_mul_mat_hadamard`; логика добавлена и для общего, и для iSWA path.
3. Bug report локализует дефект на DFlash draft-context K/V cache с
   quantized cache type: f16/bf16 давали примерно 80–99% acceptance, а q8_0 —
   0–2%. Обычная inference и MTP не затронуты.
4. После fix upstream-проверяющий сообщил `Draft acceptance: 0.97159` для
   `q8_0` K cache и `q5_1` V cache. Это полезное upstream evidence, но не
   локальный Soll benchmark и не доказательство общего прироста скорости.

## Пять проверенных seam текущего проекта

| Seam | Факт в репозитории | Следствие для b10068 |
| --- | --- | --- |
| Проверенная версия | `tools/llama-cpp/llama_cpp_b9895_defaults.json` pin-ит `b9895` с SHA-256 | Нельзя заменять проверенный default непроверенным tag |
| Android runtime | policy оставляет `soll-backend-route` default, `packageIntoAndroidApp: false` | Release binary не входит в пользовательский APK/runtime |
| Native integration | в app нет `CMakeLists.txt`, `externalNativeBuild` и собственного llama.cpp JNI | Upstream C++ fix не имеет точки исполнения в приложении |
| DFlash workload | в runtime/config найдено 0 точных DFlash/cache-flag упоминаний | Исправляемый defect сейчас не воспроизводится в Soll app |
| Product boundary | сложный reasoning идет через server meta-coordinator; heavy local LLM исключён из ранних Android phases | Локальный DFlash допустим только отдельным измеряемым server/harness spike |

## Возможное локальное применение после появления workload

Единственный обоснованный slice — disposable local/server benchmark вне
production UI. Он сравнивает upstream parent
`4937ca83f4f3da63004943fe05d8aa4f0217d238` и b10068 на одном host/backend,
одной main + DFlash draft model pair и одинаковых prompts/seeds.

Шесть ворот такого benchmark:

1. Зафиксировать одобренный standalone/server harness, OS, CPU/GPU/backend,
   model и draft-model revisions/checksums; не включать binaries или models в
   APK/AAB.
2. Зафиксировать полный CLI и доказать, что workload использует
   `--spec-type draft-dflash` и quantized `--cache-type-k-draft` или
   `--cache-type-v-draft`; обычная генерация не проверяет b10068.
3. На parent и b10068 выполнить одинаковый prompt/seed набор минимум в пяти
   повторах, отдельно сохранив f16/bf16 control и quantized candidate.
4. Измерить draft acceptance, generation tokens/s, p50/p95 latency, peak
   memory и output/correctness failures; crashes и invalid values должны быть
   `0`.
5. Для promotion требовать median draft acceptance не ниже `0.90` и не более
   чем на 2 процентных пункта ниже f16/bf16 control, без ухудшения latency,
   throughput или peak memory более чем на `10%`.
6. Сохранить `soll-backend-route` и b9895 как rollback до отдельного review и
   approval; результат не переносить между backend/device без повторной
   проверки.

## Наблюдаемая ценность

- Добавлен `1` local-application evaluation artifact.
- Проверены `4` официальные upstream surface и `5` текущих Soll seam.
- Определены `6` измеримых ворот для релевантного DFlash benchmark.
- Зафиксировано upstream-восстановление acceptance с `0–2%` до `0.97159`, но
  это не считается локальным измерением Soll.
- Найдено `0` текущих DFlash runtime/config seam; изменено `0`
  production-файлов, dependencies, API contracts и runtime routes.
- Выполнено `0` локальных b10068 inference/benchmark runs; измеренная
  runtime-ценность для Soll app остаётся `0` до появления применимого workload.
