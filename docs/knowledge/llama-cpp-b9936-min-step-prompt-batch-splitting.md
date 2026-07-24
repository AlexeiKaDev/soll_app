# llama.cpp b9936: min-step при разбиении prompt batches

Дата проверки: 2026-07-24.

## Решение

PR #25420 действительно исправляет server-side разбиение prompt batches:
начало каждого user message больше не обязано останавливать текущий batch,
если новый checkpoint оказался бы слишком близко к предыдущему. Изменение
относится только к checkpoint-path `llama-server` для completion task и не
меняет HTTP API, формат chat messages, sampler, модель или backend.

Исправление полезно как regression contract для будущего локального inference,
но не требует текущего rollout. Android-клиент Soll_app отправляет chat turn
через `POST api/v1/chat/turn`, standalone llama.cpp не упаковывается в APK, а
репозиторный release smoke запускает `llama-server --version`, не prompt
processing. Активный b10068 уже содержит b9936.

## Проверенный upstream scope

Указанный задачей monitored artifact
`raw/monitored/llama-cpp-releases/20260709-233427-b9936-1bfd8906.md`
отсутствует в изолированном worktree. Поэтому вывод проверен по официальным
upstream surface:

- [release b9936](https://github.com/ggml-org/llama.cpp/releases/tag/b9936);
- [PR #25420](https://github.com/ggml-org/llama.cpp/pull/25420);
- [release commit `64c8b7db72fbd871512b371b5c141c00fd0a8ba6`](https://github.com/ggml-org/llama.cpp/commit/64c8b7db72fbd871512b371b5c141c00fd0a8ba6);
- [compare b9936...b10068](https://github.com/ggml-org/llama.cpp/compare/b9936...b10068).

Release b9936 опубликован 9 июля 2026 года и указывает прямо на merge commit
PR. PR был merged с `25` успешными checks. Commit имеет parent
`f2d1c2f3984cb0934b575069489a052654b4037b` и меняет ровно
`tools/server/server-context.cpp`: `8` добавлений и `3` удаления. Отдельный
upstream regression test или benchmark этим commit не добавлен.

## Что именно меняет diff

До PR цикл prompt processing безусловно останавливал batch перед каждым
началом user message. Эта остановка происходила даже тогда, когда последующая
checkpoint-логика отвергала checkpoint из-за `checkpoint_min_step`. В итоге
лишний маленький decode batch уже был создан, а параметр min-step не защищал
prefill от такого дробления.

После PR остановка на user boundary выполняется только при `do_checkpoint` и
одном из условий:

```text
pos == last_user_pos
checkpoints.empty()
pos > checkpoints.back().n_tokens + params_base.checkpoint_min_step
```

То есть последнее user message по-прежнему отделяется, первый checkpoint
может быть создан, а промежуточное начало user message не дробит batch, пока
позиция не прошла min-step относительно последнего checkpoint. В b9936
`--checkpoint-min-step` имеет default `256` tokens (`0` отключает минимум).

Сам `do_checkpoint` ограничивает эффект: context checkpoints должны быть
включены, task должен быть completion task, а используемый memory path должен
соответствовать checkpoint-условиям server. Обычный CLI inference,
`llama-server --version`, request без активного checkpoint-path и generation
после prefill этим diff не меняются.

## Применимость к Soll/Soll_app

| Soll seam | Проверенный факт | Вывод |
| --- | --- | --- |
| Android chat | `SollApiService` вызывает `POST api/v1/chat/turn` | Android не управляет prompt batches или checkpoint flags напрямую |
| Runtime policy | `androidRuntimeDefault` равен `soll-backend-route`, `packageIntoAndroidApp` равен `false` | b9936 не требует APK/dependency change |
| Standalone baseline | b10068 на `132` commits впереди b9936, на `0` позади; merge base равен exact b9936 commit | Fix уже присутствует в закреплённом release |
| Release smoke | `Test-LlamaCppActiveRelease.ps1` запускает `llama-server --version` | Изменённая prompt-processing ветка сейчас не исполняется |
| Model gate | Единственная allowlisted tiny GGUF разрешена только для b9945/b9947 CLI smoke | В репозитории нет одобренного server/model regression fixture |

Реализация inference backend за `soll-backend-route` не находится в этом
worktree, поэтому заметка не утверждает, какой runtime работает за удалённым
API. По проверяемому Soll_app контракту и локальным tools прямого
checkpoint/prompt-batching execution seam сейчас нет. Production code,
release defaults, model allowlist и Android contract остаются без изменений.

## Возможный regression test

Когда появится одобренный локальный `llama-server` model fixture, проверить
min-step следует так:

1. Зафиксировать checksummed GGUF, binary SHA-256, CPU backend, seed, sampler,
   chat template, `n_batch`, `n_ubatch`, context size и два exact builds:
   parent `f2d1c2f3984cb0934b575069489a052654b4037b` и b9936 либо более новый
   build с доказанной ancestry.
2. Запустить server с context checkpoints и явным
   `--checkpoint-min-step 256`; сохранить effective flags и debug log,
   подтверждающий активный `do_checkpoint` path.
3. Сформировать один multi-turn chat fixture с tokenized user boundaries по
   обе стороны порога: `pos <= checkpoint + 256` и
   `pos > checkpoint + 256`, а последнее user message проверить отдельно.
4. По instrumentation/debug log зафиксировать границы decode batches и
   checkpoints. Candidate обязан не разрывать промежуточный batch при
   `pos <= checkpoint + 256`, разрывать его при
   `pos > checkpoint + 256` и всегда сохранять last-user boundary.
5. Повторить cold prefill минимум `5` раз на каждом build и сравнить число
   decode batches, размеры маленьких batches, prompt tokens/s и TTFT. Главный
   correctness oracle — границы batch/checkpoint; performance — вторичная
   метрика, а не замена структурной проверке.
6. При фиксированном seed сравнить token IDs или logits в заданном tolerance и
   потребовать `0` crashes, `0` decode errors и `0` неожиданных fallback.
   Любое нарушение boundary invariant блокирует runtime promotion.

До появления такого fixture выполняется только repository contract test; он
не считается model-backed smoke.

## Наблюдаемая ценность

- Добавлена `1` Soll_app KB-заметка о min-step/prompt batch splitting.
- Проверены `4` official upstream surface и `5` текущих Soll seam.
- Подтверждены `1` изменённый server-файл, `11` изменённых строк и ancestry
  active b10068: `132` commits впереди, `0` позади.
- Определены `6` шагов будущего model-backed regression test.
- Изменено `0` production/runtime файлов, dependencies, defaults и API
  contracts; выполнено `0` local model inference runs.
