# llama.cpp b9922: n_keep_tail для recurrent summarization/RAG

Дата проверки: 2026-07-27.

## Решение

В `soll_app` используется проверочный standalone-контур llama.cpp/GGUF, но не
прямой production runtime Android. Контур закреплён на release `b10068`,
commit `571d0d540df04f25298d0e159e520d9fc62ed121`, скачивает только
checksummed CPU archives в build cache и допускает GGUF через deny-by-default
allowlist. `packageIntoAndroidApp` равен `false`, а Android runtime default
остаётся `soll-backend-route`.

Поэтому отдельный pin или rollout b9922 не нужен. Активный b10068 на `146`
commits впереди b9922, на `0` позади, а merge base равен exact b9922 commit.
Production code, Gradle dependencies, Android API и release defaults остаются
без изменений.

PR #25278 полезен как correctness/performance contract для возможного будущего
локального recurrent runtime. Сейчас в allowlist нет recurrent-модели и нет
одобренного summarization/RAG workload, поэтому измеренный runtime-эффект для
Soll_app равен `0`: model-backed inference и benchmark не выполнялись.

## Проверенный upstream scope

Указанный задачей monitored artifact
`raw/monitored/llama-cpp-releases/20260708-223009-b9922-214bd25b.md`
отсутствует в изолированном worktree. Поэтому релиз и изменение проверены по
официальным upstream surface:

- [release b9922](https://github.com/ggml-org/llama.cpp/releases/tag/b9922);
- [PR #25278](https://github.com/ggml-org/llama.cpp/pull/25278);
- [merge commit `230ea9d214320c5e79cc8166ed708ac60514c71e`](https://github.com/ggml-org/llama.cpp/commit/230ea9d214320c5e79cc8166ed708ac60514c71e);
- [compare b9922...b10068](https://github.com/ggml-org/llama.cpp/compare/b9922...b10068).

Release b9922 опубликован 8 июля 2026 года и закреплён на merge commit PR.
PR содержит `1` commit, меняет `10` файлов (`102` добавления, `38` удалений) и
добавляет внутренний аргумент `n_keep_tail` в
`llama_batch_allocr::split_equal`. Parent release commit:
`f296fdfbed71e900a3e0d6579673960e6a560654`.

Главный diff находится в `src/llama-batch.cpp` (`+70/-4`) и
`src/llama-batch.h` (`+2/-1`). Остальные изменения передают новое значение из
recurrent/hybrid memory paths и сохраняют `0` для обычных KV-cache paths.
Отдельный upstream unit test для allocator splits в PR не добавлен; maintainer
оставил его follow-up.

## Что меняет n_keep_tail

До b9922 recurrent/hybrid path с partial rollback (`n_rs_seq > 0`) уходил в
`split_seq`, то есть не использовал equal split для параллельных sequences.
После b9922 он вызывает:

```text
split_equal(n_ubatch, sequential, n_rs_seq > 0 ? n_rs_seq + 1 : 0)
```

`split_equal` теперь гарантирует, что последние `n_keep_tail` токенов каждой
sequence не разделятся между micro-batches (`ubatch`). Это сохраняет валидность
rollback snapshots для recurrent state и позволяет обрабатывать несколько
sequences параллельно. Код требует `n_ubatch > n_keep_tail`.

Это внутренний allocator argument, не новый CLI/HTTP параметр. Изменение не
переключает модель, не меняет prompt/RAG retrieval, context selection, sampler
или Android contract. При `n_rs_seq == 0`, для non-recurrent model и для
single-sequence workload ожидаемая функциональная ценность отсутствует.

## Применимость к Soll/Soll_app

| Soll seam | Проверенный факт | Вывод |
| --- | --- | --- |
| Android chat | `SollApiService` вызывает `POST api/v1/chat/turn` | Android не управляет ubatch или recurrent rollback |
| Android package | В `app/src/main` и Gradle нет llama.cpp/GGUF/JNI/CMake runtime marker | b9922 не требует APK/dependency change |
| Standalone runtime | `llama_cpp_active_defaults.json` pin-ит b10068, `packageIntoAndroidApp=false` | Репозиторный llama.cpp используется только для standalone verification |
| Active ancestry | b10068 на 146 commits впереди b9922, 0 позади | n_keep_tail уже присутствует в текущем проверяемом release |
| GGUF gate | Единственный allowlisted model разрешён только для трёх tiny smoke uses | Recurrent summarization/RAG fixture отсутствует |
| Current smoke | Active release smoke запускает `llama-server --version` | Изменённый allocator path не исполняется |

Реализация backend за `soll-backend-route` не находится в этом worktree.
Поэтому аудит подтверждает только локальный Soll_app contract и не делает
непроверенных утверждений о runtime удалённого Soll server.

## Оценка влияния на summarization/RAG

Потенциальная ценность есть только для recurrent-модели с partial rollback и
двумя или более параллельными sequences:

- parallel chunk summarization может получить меньший wall time и больший
  aggregate tokens/s, потому что sequences снова допускаются в equal ubatches;
- parallel RAG branches (несколько запросов или контекстных веток) могут
  получить тот же throughput/latency эффект;
- качество summary, retrieval relevance и grounding само по себе не должно
  измениться: PR меняет размещение токенов по ubatches, а не алгоритм RAG;
- неправильная граница tail могла бы повредить rollback state, поэтому
  отсутствие crashes недостаточно — нужно проверить состояние и outputs;
- single-sequence, transformer/non-recurrent и rollback-disabled workloads
  служат отрицательными controls и не должны показывать значимый эффект.

Текущему Soll_app это не даёт измеримой runtime-ценности: approved recurrent
GGUF, local summarization/RAG adapter и model-backed fixture отсутствуют.

## Будущий model-backed benchmark

Создавать этот benchmark следует только после одобрения recurrent GGUF и
конкретного локального workload:

1. Закрепить exact recurrent GGUF/revision/SHA-256, backend, host, seed,
   sampler, context size, `n_batch`, `n_ubatch`, `n_rs_seq` и два builds:
   parent `f296fdfbed71e900a3e0d6579673960e6a560654` и b9922 либо более новый
   build с доказанной ancestry.
2. Проверить precondition `n_ubatch > n_rs_seq + 1` и сохранить allocator debug
   evidence, что последние `n_rs_seq + 1` токенов каждой sequence находятся в
   одном ubatch.
3. Запустить fixed summarization fixture и fixed RAG fixture с parallelism
   `1`, `2`, `4` и `8`; single-sequence run оставить отрицательным control.
4. Повторить каждый cold run минимум `5` раз; измерить aggregate tokens/s,
   end-to-end wall time, p50/p95 TTFT, peak RSS/VRAM и число ubatches.
5. При fixed seed сравнить output token IDs или logits в заданном tolerance,
   summary/RAG assertions и rollback state; потребовать `0` crashes,
   corruptions и unexpected fallback.
6. Продвигать runtime только при `0` correctness regressions, улучшении
   parallel throughput или p95 latency минимум на `10%`, росте peak memory не
   более `5%` и отсутствии single-sequence regression более `5%`.

До появления fixture это измеримый benchmark contract, а не выполненный
model-backed smoke.

## Наблюдаемая ценность

- Добавлена `1` Soll_app KB-оценка n_keep_tail для recurrent summarization/RAG.
- Проверены `4` official upstream surface и `6` текущих Soll seam.
- Зафиксированы standalone llama.cpp `b10068` и его ancestry: `146` commits
  впереди b9922, `0` позади.
- Определены `6` шагов будущего model-backed benchmark и `4` уровня
  parallelism.
- Изменено `0` production/runtime файлов, dependencies, defaults и API
  contracts; выполнено `0` local model inference runs.
