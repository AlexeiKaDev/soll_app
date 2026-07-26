# llama.cpp b9933: Android/Adreno Q6_K regression check

Дата проверки: 2026-07-24.

## Решение

Исправление `b9933` сохраняется в Soll_app как обязательный correctness-check
для будущих тестов локального llama.cpp на Android с Qualcomm Adreno/OpenCL.
Сигнал относится к Q6_K-весам, у которых vocab или другая проверяемая
размерность tensor shape не кратна `128`: без исправления такой tail мог давать
искажённый вывод или тихо повреждать соседний tensor.

Это не основание самостоятельно обновлять runtime. Текущий Android contract
по-прежнему использует `soll-backend-route`, а
`llama_cpp_active_defaults.json` не упаковывает standalone llama.cpp в APK.
Изменять binary, release defaults, model allowlist или production route в этой
задаче нельзя.

## Граница исходника

Указанный задачей monitored artifact
`raw/monitored/llama-cpp-releases/20260709-233427-b9933-5861f5ec.md`
не vendored в изолированном worktree. Поэтому заметка фиксирует только
переданное задачей проверяемое утверждение: release
[b9933](https://github.com/ggml-org/llama.cpp/releases/tag/b9933) исправляет
OpenCL/Adreno обработку Q6_K tail для размерностей весов, не кратных `128`.
Заметка не приписывает отсутствующему raw-файлу commit, PR, benchmark или
список изменённых файлов.

## Обязательная запись каждого Android/Adreno прогона

Результат нельзя считать воспроизводимым без одной audit-записи, содержащей:

1. exact llama.cpp release и commit, SHA-256 тестируемого binary, ABI и
   фактический backend `OpenCL`;
2. Android build, устройство, SoC, GPU `Adreno`, версию OpenCL/driver и режим
   offload;
3. имя GGUF, immutable revision и SHA-256, quantization `Q6_K`, vocab size и
   конкретные tensor shapes с остатком `dimension % 128`;
4. prompt fixture, seed, context/batch параметры, число prompt/generated
   tokens и сохранённый несекретный digest результата;
5. результат тех же model/prompt/seed на CPU reference и на Adreno/OpenCL;
6. число повторов, crashes, load/kernel errors, NaN/Inf, output mismatches и
   unexpected CPU fallbacks.

Запись только `Q6_K` без exact версии llama.cpp и без хотя бы одной фактической
размерности с `dimension % 128 != 0` считается неполной.

## Focused smoke для non-128 Q6_K

На одной и той же одобренной checksummed GGUF-модели smoke обязан:

1. Доказать по GGUF metadata или loader/kernel audit, что модель действительно
   использует Q6_K и содержит vocab/shape, не кратный `128`; сохранить exact
   dimension и ненулевой remainder.
2. Запустить fixed candidate (`b9933` или более новый release с доказанной
   ancestry) на Adreno/OpenCL с фиксированными prompt, seed и sampler.
3. Выполнить model load, короткий prefill и generation минимум `3` раза после
   холодной загрузки; каждый прогон должен завершиться без crash, kernel/load
   error, NaN/Inf и неожиданного CPU fallback.
4. Повторить fixture на CPU reference из той же версии и сравнить token IDs
   либо logits в заранее заданном tolerance. Несовпадение нельзя списывать на
   «особенность GPU» без отдельного расследования.
5. Если доступен pre-fix baseline, выполнить тот же A/B на том же
   device/driver/model. Baseline нужен для воспроизведения дефекта, но не
   является обязательным условием продвижения, если небезопасен или
   недоступен.
6. Считать gate пройденным только при `0` crashes, `0` NaN/Inf, `0`
   unexpected fallbacks и `0` correctness mismatches во всех повторах.

Обычная модель с размерностями, кратными `128`, может остаться regression
control, но не заменяет обязательный non-128 fixture. Визуально правдоподобный
текст сам по себе не доказывает отсутствие тихой порчи tensor.

## Ворота обновления runtime

Даже успешный focused smoke не меняет runtime автоматически. Candidate можно
продвигать только через обычный regression/smoke контур Soll:

- повторить существующие model-provenance, release archive, CLI/server и
  Android ABI checks;
- прогнать минимум одну обычную модель и обязательную Q6_K non-128 fixture на
  CPU и целевом Adreno/OpenCL;
- проверить cold/warm load, prompt processing, generation, повторный запуск,
  memory pressure и fallback/error paths;
- сравнить correctness, latency, peak memory и thermals с закреплённым
  baseline;
- сохранить checksummed rollback binary/model и
  `soll-backend-route` как безопасный fallback;
- менять active defaults только отдельной review-задачей после зелёного
  regression/smoke отчёта.

Fail, неполная provenance-запись, отсутствующий CPU reference или отсутствие
реальной non-128 Q6_K модели блокируют обновление runtime.

## Наблюдаемая ценность

- Добавлена `1` Soll_app note с `6` обязательными provenance/result полями.
- Определены `6` шагов focused Android/Adreno Q6_K smoke и `6` regression
  gate перед обновлением runtime.
- Явно закреплены Q6_K, exact llama.cpp version и обязательный
  `dimension % 128 != 0` fixture.
- Изменено `0` production/runtime файлов, dependencies, API contracts,
  release defaults и model allowlist.
- Выполнено `0` device/model inference runs; эта задача проверяет наличие и
  полноту regression-check, но не заявляет физическую Adreno-валидацию.
