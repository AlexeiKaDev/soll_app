---
title: HPC/LLM: оптимизация нейросетей без потери измеримого смысла
task_id: 18476224ced6466187f4a292cee8fdbf
source_ref: insight/5d5e682a1aa1
review_status: knowledge_note_added_runtime_change_deferred
scope: HPC/LLM infrastructure
---

# HPC/LLM: оптимизация нейросетей без потери измеримого смысла

## Короткий вывод

Оптимизация нейросети — это не поиск одного «самого быстрого» ядра. Это выбор
эквивалентного в пределах заранее заданного качества вычислительного пути под
конкретные данные, форму тензоров, оборудование и режим нагрузки. Ускорение
имеет смысл только вместе с неизменным набором входов, проверкой качества и
измерением реального узкого места.

Для Soll практический порядок такой: оставить тяжёлый LLM на backend route,
разделить измерения `prefill` и `decode`, сначала профилировать переносы данных,
память и синхронизации, а затем проверять по одному изменению. Этот документ не
разрешает новый runtime, модель, GPU-кластер или изменение Android API.

## Граница источника

Задача ссылается на материал «Нейро сети для самых маленьких. Часть первая
(которая после нулевой). Удобство в прокрустовом ложе оптимизации»:
`monitored/habr-yandex-company/20260703-211111-item-a9338673.md`.

Файл источника отсутствует в изолированном worktree. Поэтому ниже сохранён
исходный сигнал и дан самостоятельный инженерный конспект, но материалу не
приписываются непроверяемые здесь цитаты, цифры, архитектура или результаты.

## Модель производительности

Время выполнения удобно раскладывать на четыре части:

```text
Trequest = Tqueue + Ttransfer + Tcompute + Tsync
```

- `Tqueue` — ожидание batch/worker и конкуренция за устройство;
- `Ttransfer` — чтение весов, KV-cache и активаций, копии host/device и между
  узлами;
- `Tcompute` — матричные операции, attention, нормализации и прочие kernels;
- `Tsync` — запуск kernels, barriers и коллективные коммуникации.

Если операция ограничена вычислениями, помогают более эффективные kernels,
tensor cores и подходящая precision. Если ограничена bandwidth, добавление
FLOPS почти бесполезно: нужны меньше байтов, лучше layout/reuse, fusion или
quantization. Если доминируют очередь и синхронизация, нужны scheduling,
continuous batching и уменьшение числа запусков/collectives. Без профиля эти
случаи легко перепутать.

## Шесть слоёв оптимизации

1. **Алгоритм и модель.** Уменьшать бесполезную работу: выбирать подходящий
   context, pruning/distillation, sparse или более дешёвый attention только
   после проверки качества на целевой задаче.
2. **Представление чисел.** FP16/BF16/FP8/INT8/INT4 уменьшают трафик и память,
   но веса, активации и KV-cache имеют разную чувствительность. Проверять надо
   не только perplexity, а ответы и tool-calling Soll.
3. **Graph и kernels.** Operator fusion, compiled/static graph, подходящие
   attention/matmul kernels и кратные tile dimensions сокращают промежуточные
   записи и launch overhead. Цена — меньшая гибкость shapes и сложнее fallback.
4. **Память и движение данных.** Layout, reuse, pinned buffers, KV-cache
   policy, paged allocation и отсутствие лишних host/device copies часто
   важнее пиковой вычислительной мощности.
5. **Serving и scheduling.** Batch повышает throughput, но может ухудшить TTFT;
   continuous batching, admission control и разделение длинных/коротких
   запросов должны держать tail latency в бюджете.
6. **Распределение по устройствам.** Data/tensor/pipeline/expert parallelism
   выбирают по модели и interconnect. Добавление GPU полезно только пока
   compute перекрывает стоимость all-reduce/all-to-all, pipeline bubbles и
   пересылки KV/активаций.

Это и есть «прокрустово ложе»: ускоритель вознаграждает статические shapes,
крупные batches, выровненные dimensions и ограниченный набор операций. Нельзя
подгонять под него workload так, чтобы исчезла исходная семантика, редкие
длинные запросы или проверка качества.

## Почему LLM надо измерять по фазам

`Prefill` обрабатывает prompt параллельно и при достаточной длине чаще ближе к
compute-bound режиму. `Decode` генерирует токены последовательно, многократно
читает веса и растущий KV-cache и чаще ограничен bandwidth/latency. Поэтому
одно среднее `tokens/s` скрывает две разные задачи.

Минимальный набор метрик:

- TTFT p50/p95 для `prefill` и inter-token latency/TPOT p50/p95 для `decode`;
- output tokens/s и requests/min при фиксированных concurrency и batch policy;
- prompt/output tokens, context lengths и доля cache hits;
- peak VRAM/RAM/PSS, объём KV-cache и host/device/network bytes;
- очередь, kernel/collective time, ошибки, OOM и отмены;
- качество на фиксированном Soll-наборе, стоимость и энергия на успешный
  запрос, если они доступны.

Сравнение действительно только для одинаковых model revision, tokenizer,
prompt set, output limit, sampling/seed policy, hardware/backend, warm-up и
параллелизма. Среднее без p95 и разбивки по длине запросов не является
достаточным доказательством.

## Четыре проверенных seam Soll

1. `tools/llama-cpp/llama_cpp_b9895_defaults.json` оставляет
   `androidRuntimeDefault: soll-backend-route` и
   `packageIntoAndroidApp: false`: HPC/LLM benchmark должен оставаться
   server/desktop-задачей, а не попадать в APK.
2. `SollGateway.askModelChat(...)` и meta-coordinator дают backend-mediated
   seam для выбора worker/model без прямого исполнения тяжёлой модели на
   телефоне.
3. В `app/build.gradle.kts` нет собственного llama.cpp JNI/CMake integration;
   существующий `sherpa-onnx` относится к локальным speech workloads и не
   доказывает готовность общего LLM runtime.
4. `docs/soll_app-superassistant-roadmap-2026-05-06.md` прямо сохраняет правило
   `No heavy local LLM on Android in early phases`; данный сигнал его не меняет.

## Семь ворот измеримого эксперимента

1. **Workload.** Зафиксировать непроизводственный prompt set, распределение
   context/output lengths, concurrency и критерий качества Soll.
2. **Baseline.** Записать model/tokenizer/runtime revisions, hardware,
   backend, precision, CLI/config и результаты до изменения.
3. **Профиль.** Разделить queue/transfer/compute/sync, `prefill`/`decode` и
   подтвердить bottleneck инструментом, а не предположением.
4. **Одна гипотеза.** Менять один слой за раз и явно фиксировать shapes,
   batching, cache и fallback; затем повторять серию с warm-up.
5. **Качество и безопасность.** Не допустить регрессии ответов, policy,
   tool-calling, ошибок/OOM и утечки prompt/content в telemetry.
6. **Ресурсы и tail.** Сравнить TTFT/TPOT p50/p95, throughput, peak memory,
   network/collective cost и стоимость/энергию на успешный запрос.
7. **Promotion и rollback.** До запуска объявить бюджеты выигрыша/регрессии,
   сохранить текущий backend route и отклонить изменение, если локальная
   измеримая ценность отсутствует.

## Наблюдаемая ценность

Добавлена одна краткая HPC/LLM заметка: документированы шесть слоёв
оптимизации, проверены четыре текущих seam Soll и определены семь ворот
измеримого эксперимента. Производственные файлы, runtime routes, dependencies,
модели и конфигурация не менялись. Выполнено **0** HPC/LLM benchmark или inference runs;
измеренный runtime-выигрыш остаётся **0** до отдельного
approval-gated эксперимента с доступным workload и оборудованием.
