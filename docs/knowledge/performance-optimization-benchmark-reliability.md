---
title: "Надёжность performance-бенчмарков кодирующих агентов"
task_id: 49b3763d37674cc39779d1e7f3e3581e
source_ref: source-item/9011e13c06d6/4dd7eedded0ca608
source_version: arxiv:2607.01211v2
reviewed_at: 2026-07-23 Europe/Chisinau
---

# Надёжность performance-бенчмарков кодирующих агентов

## Краткий вывод

Статья показывает, что единый leaderboard score нельзя интерпретировать как
прямую меру способности coding agent оптимизировать программы. До сравнения
агентов нужно отдельно доказать стабильность reference signal, после сравнения
— показать вклад каждой задачи и чувствительность ranking к формуле агрегации,
а any-of-N покрытие нескольких конфигураций нельзя приписывать одному агенту.

Для Soll метрики стоит адаптировать, но не переносить буквально. Полезный
результат этой задачи — proposal-only scorecard contract
`performance-optimization-benchmark-reliability-v1.json`: correctness остаётся
hard gate, нестабильные reference tasks исключаются из performance denominator,
центральные и tail metrics публикуются вместе, а single-agent и fleet coverage
разделены. Benchmark runner, внешние агенты и production-интеграция не добавлены.

## Полная версия и provenance

Проверены первичные материалы:

- [Hugging Face Daily Paper](https://huggingface.co/papers/2607.01211);
- [arXiv record](https://arxiv.org/abs/2607.01211);
- [полный PDF](https://arxiv.org/pdf/2607.01211v2);
- [полный TeX source](https://arxiv.org/e-print/2607.01211v2);
- [публичные данные авторов](https://github.com/chenzhi-cz/performance-optimization-benchmark-reliability).

На момент проверки актуальна версия `arxiv:2607.01211v2` от 16 июля 2026;
это более новая версия, чем v1 от 1 июля, попавшая в monitored source. arXiv
описывает PDF как 12-страничную статью с семью рисунками.

| Объект | Размер | SHA-256 |
| --- | ---: | --- |
| `performance-benchmark-reliability-2607.01211v2.pdf` | 415,035 bytes | `158e3baf87b42faa481ce5b53f82c94618c61f014ef37c9b63b858cd997f942a` |
| `performance-benchmark-reliability-2607.01211v2-source.tar` | 238,382 bytes | `f3fcc2ebab992c6c5041581a8ee0759286c78c11b47407cf94c18342f0131860` |

Оба файла находятся в ignored cache
`build/source-processing/perf-bench-2607.01211v2/` и не vendored в Git или
Android APK. Перед распаковкой все 24 archive entries проверены: absolute и
path-traversal names отсутствуют. Изучены основной TeX, отдельные секции RQ1–RQ3,
discussion, threats, conclusion и data availability. Код и публичный dataset
не клонировались и не исполнялись.

Указанный задачей raw-файл
`raw/monitored\hugging-face-daily-papers\20260702-190417-are-performance-optimization-benchmarks-reliably-19692a00.md`
в isolated worktree отсутствует. Это не подменяется утверждением о локальном
raw ingestion: анализ опирается на canonical primary source.

## Что именно исследует paper

Авторы аудируют три repository-level benchmark:

| Benchmark | Объём | Performance workload |
| --- | ---: | --- |
| GSO | 102 tasks, 10 repos | generated performance tests между base и reference commits |
| SWE-Perf | 140 tasks, 9 repos | отфильтрованные repository unit tests |
| SWE-fficiency | 498 tasks, 9 repos | аннотированные workload scripts |

Всего 740 official reference patches воспроизведены на четырёх Google Cloud
профилях с 64 vCPU/256 GB: Intel Cascade Lake, AMD Milan, Intel Emerald Rapids
и AMD Turin. Для каждой машины выполнены три rounds, то есть строгая проверка
задачи охватывает 12 machine-round observations.

Исследование разделено на три вопроса:

1. сохраняется ли исходный reference signal между машинами;
2. как scoring rule меняет ranking одних и тех же submission outputs;
3. какие replay-valid tasks ещё не покрыты хотя бы одной из 10 public submissions.

## RQ1: метрики надёжности reference signal

### Два обязательных task-level gate

`Faster-than-base` требует для каждого round `r` и machine `m`:

```text
T_ref(r, m) < T_base(r, m)
```

`Original-rule valid` повторно применяет исходный construction rule benchmark:

- GSO: correctness/equivalence и обычно speedup не меньше `1.2x`; для
  low-test fallback допускается порог выше `1.1x`;
- SWE-Perf: base и modified проходят unit test; после 20 repetitions и IQR
  filtering Mann–Whitney minimum-gain `delta_i > 0.05`;
- SWE-fficiency: workload и correctness guards успешны, а разница средних
  base/reference runtime больше двух standard deviations post-edit runtime.

Runnable не означает valid. Полностью не воспроизвелись только `4/102` GSO,
`2/140` SWE-Perf и `0/498` SWE-fficiency tasks. Но быстрее base на всех
машинах остались лишь `91/102`, `48/140` и `470/498`; исходный rule на всех 12
replays сохранили только `39/102`, `11/140` и `411/498`.

### Signal-to-noise audit

Для общего масштаба speedup `s = T_base / T_ref` переводится в runtime change:

```text
runtime_change = 1 / s - 1
```

Paper публикует median runtime change, median within-task standard deviation в
percentage points и отношение `std / abs(signal)`. Результат объясняет провал
SWE-Perf не большим абсолютным шумом, а почти нулевым signal:

| Benchmark | Median change | Median std | Std/signal |
| --- | ---: | ---: | ---: |
| GSO | -54.20% | 3.81pp | 0.07x |
| SWE-Perf | -0.03% | 1.41pp | 43.23x |
| SWE-fficiency | -56.04% | 2.41pp | 0.04x |

У SWE-Perf `101/138` evaluable tasks имеют median change в пределах пяти
percentage points от нуля. Поэтому небольшая смена hardware или round стирает
significance даже при меньшем absolute variation.

## RQ2: метрики scoring-rule sensitivity

### GSO: binary reference-level coverage

```text
OPT@1(m) = 100 * reference_level_successes(m) / N
```

Correct patch ниже reference получает ноль, как и failed patch. Каждая из 102
задач имеет одинаковый максимальный вклад около `0.98` score point.

### SWE-fficiency: SpeedUp Ratio и harmonic mean

Для model/submission `m` и task `i`:

```text
SR(m, i) = speedup_candidate(m, i) / speedup_reference(i)
HM(m) = N / sum_i(1 / max(SR(m, i), 0.001))
```

`SR=1` соответствует reference, `SR<1` отстаёт, `SR>1` превосходит его.
Harmonic reward сверху ограничен: `SR=2` экономит в denominator `0.5`, а
`SR=10` — `0.9`. Penalty несимметричен: `SR=0.5` добавляет `1`, `SR=0.01`
добавляет `99`, floor `0.001` — `999` denominator units.

Per-task denominator weight делает эту концентрацию видимой:

```text
w_i = (1 / max(SR_i, 0.001)) / sum_j(1 / max(SR_j, 0.001))
```

У восьми shared submissions худшая одна задача несёт `6.3–33.6%`, худшие пять
— `31.4–73.1%`, худшие десять — `58.5–82.8%` official denominator. Поэтому
paper добавляет bounded-penalty diagnostic с floor `0.5`: worst task вносит не
более двух units, то есть extra penalty не больше одной neutral unit. Это не
предложенный новый официальный score, а sensitivity check.

### Ranking diagnostics

Нужны не только scalar scores, но и:

- Spearman rank correlation;
- число discordant head-to-head pairs;
- максимальное rank movement;
- median SR и count `SR > 1` рядом с harmonic score;
- worst-1/5/10 denominator weight.

Official GSO и SWE-fficiency ranks расходятся в `9/28` pairs. Пересчёт обоих
на GSO-style scoring улучшает correlation с `0.452` до `0.762` и сокращает
flips до `6/28`; harmonic rescoring даёт `0.238` и `11/28`. Замена только floor
на `0.5` двигает `6/8` ranks и переворачивает `8/28` pairs. Следовательно,
ranking измеряет одновременно submission outputs, task set и penalty design.

## RQ3: task frontier, а не capability одного агента

На replay-valid subset из `39` GSO и `411` SWE-fficiency tasks paper смотрит
на union десяти public submissions. Для каждой задачи фиксируются три слоя:

1. существует ли хотя бы один passing patch;
2. существует ли passing patch быстрее base;
3. существует ли patch, matching/beating reference.

Результат: `450/450` имеют passing patch, `449/450` — faster-than-base patch,
`384/450` (`85.3%`) — reference-level patch. Оставшиеся 66 — это `9/39` GSO и
`57/411` SWE-fficiency. У всех есть correct public patch, у `65/66` он быстрее
base; median best patch достигает `85.3%` и `87.9%` reference speedup.

Strategy annotation не сводит tail к выбору категории: `32/66` best patches
используют ту же high-level category, но всё ещё отстают; `11/32` patches с
другой category достигают `90–100%` reference. Это чаще gap глубины реализации,
а не отсутствие любой рабочей идеи.

Any-of-10 — optimistic fleet/task coverage. Это не результат одного агента и
не реализованный multi-agent system. Внутренняя оценка обязана сохранять это
название и отдельный denominator.

## Ограничения paper

- Аудит покрывает три benchmark и snapshots до 30 апреля 2026, а не все coding
  agents или performance workloads.
- Четыре машины имеют одинаковый cloud provider и resource size; строгий
  all-12 rule может недооценивать практически используемые tasks.
- SWE-Perf исключён из RQ2/RQ3 из-за отсутствия comparable public outputs.
- Any-of-10 — task-coverage proxy, не оценка единой конфигурации.
- RQ3 categories размечены GPT-5.5 с manual sanity check; это не deterministic
  ground truth.
- Paper в основном измеряет runtime. Авторы сами рекомендуют CPU time, latency,
  allocations, memory footprint и regression risk для будущих benchmark.

## Адаптация для внутренней оценки Soll

### Почему адаптация нужна

Текущий `AgentLens`-контракт Soll измеряет requirement completion, formal checks,
tool recovery и safety trajectory, но не отделяет hardware-stable performance
signal от noise и не аудирует score concentration. Поэтому новая схема дополняет,
а не заменяет trajectory evaluation.

### Task eligibility

Performance denominator включает task только когда:

1. base и reference проходят correctness;
2. reference быстрее base на каждом обязательном replay;
3. заранее объявленный task-specific original rule проходит на каждом replay;
4. machine profile, runtime, warmup/repetition policy и evidence сохранены.

Promotion-grade local pilot требует минимум два заранее объявленных hardware
profile и три rounds на profile. Это более дешёвая Soll-адаптация, а не
репликация paper `4 x 3`; single-machine result маркируется только
`diagnostic_only`. Excluded unstable tasks остаются в отчёте отдельным count и
никогда незаметно не удаляются.

### Candidate math и correctness gate

Для eligible task:

```text
candidate_speedup = T_base / T_candidate
reference_speedup = T_base / T_reference
speedup_ratio = candidate_speedup / reference_speedup
```

Invalid candidate получает runtime credit `0`, даже если broken code работает
быстрее. Отчёт показывает как минимум:

- candidate correctness rate;
- faster-than-base rate;
- reference-level coverage;
- median speedup ratio;
- official-floor HM как compatibility diagnostic;
- bounded-floor HM и worst-k denominator weight как sensitivity diagnostics.

Ни один HM не публикуется без component rates. Rank comparison двух и более
конфигураций добавляет Spearman и pair flips. Any-of-N milestones публикуются
отдельно как `fleet_*`, не как score лучшего отдельного агента.

### Resource и safety guards

Runtime улучшение не может скрывать рост `cpu_time`, `peak_rss`, allocations,
`p95_latency` или regression count. Конкретные thresholds принадлежат workload
spec и должны быть preregistered; текущая задача их не выдумывает.

Контракт offline/proposal-only: trusted allowlisted tasks, no commands from
input, no ambient credentials/network, no foreign checkout, no production
mutation. Реальный runner и сбор measurements требуют отдельной задачи и
явного approval.

## Детерминированный smoke

Machine-readable contract содержит четыре synthetic tasks:

- candidate быстрее reference;
- correct partial speedup ниже reference;
- очень быстрый, но incorrect candidate;
- task с unstable reference, исключённый из denominator.

Focused JVM test пересчитывает eligible cohort и получает:

| Metric | Expected |
| --- | ---: |
| eligible / excluded | `3 / 1` |
| candidate correctness | `2/3` |
| faster than base | `2/3` |
| reference-level coverage | `1/3` |
| median SR | `0.6666667` |
| official-floor HM | `0.0029927` |
| bounded-floor HM | `0.6760563` |
| official worst-1 weight | `0.9975684` |

Эта разница намеренно демонстрирует tail domination и одновременно проверяет,
что incorrect candidate не получает speed credit, а unstable reference не
попадает в cohort. Это contract smoke, не запуск coding agent или реального
performance benchmark.

## Решение

Paper принят как сильный evaluation-design signal. Soll получает versioned
scorecard schema и deterministic smoke, но не новую production metric и не
заявление о превосходстве модели. Измеренные agent runs, repository benchmarks,
cloud replays, external data imports и runtime behavior changes в этой задаче:
`0`.
