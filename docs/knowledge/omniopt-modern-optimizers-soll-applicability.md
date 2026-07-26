---
title: "OmniOpt: методы, бенчмарки и применимость к Soll"
task_id: 0b6e52fa00ba4cd7849623a666d6795f
source_ref: source-item/9011e13c06d6/7b2f1963bbb1c876
source_version: arxiv:2607.04033v1
reviewed_at: 2026-07-19 Europe/Chisinau
---

# OmniOpt: методы, бенчмарки и применимость к Soll

## Краткий вывод

OmniOpt — не новый универсальный оптимизатор, а три связанных инструмента:
операционная схема одного шага оптимизации, геометрическая интерпретация
направления обновления и многокритериальный benchmark-cookbook. Главный вывод
статьи полезен для Soll: выбирать надо не глобального победителя, а механизм,
который отвечает единственному ограничивающему фактору конкретного обучения —
качеству, времени шага, памяти, устойчивости, цене настройки или переносу.

Прямой runtime-эффект для текущего `soll_app` равен нулю. В приложении есть
ONNX/Sherpa inference и backend-mediated model-chat, но нет PyTorch, обучения,
backpropagation или optimizer state. Замена AdamW на Muon/SOAP/RMNP не может
ускорить ONNX TTS, `llama.cpp`, vLLM serving, UI, синхронизацию или Android
battery/runtime. Наиболее реалистичная точка применения — отдельный
desktop/server benchmark будущего Soll-owned PEFT/LoRA или иного обучения.

Полезность для текущей работы всё же есть уже сейчас: controlled-variable
протокол и оси O1–O6 можно переиспользовать как шаблон измеримого A/B, не
импортируя оптимизаторы и training stack в Android.

## Полная версия и provenance

Проверены первичные материалы:

- [Hugging Face Daily Paper](https://huggingface.co/papers/2607.04033);
- [arXiv `2607.04033v1`](https://arxiv.org/abs/2607.04033), 91-page survey and
  benchmark preprint, submitted 2026-07-04;
- [project website](https://openraiser.github.io/OmniOpt/);
- [public code repository](https://github.com/OpenRaiser/OmniOpt), inspected at
  commit `c6ea5a9f718be3f9b6ba8309327e664885d45201`.

В ходе анализа полные PDF и TeX source были скачаны в игнорируемый cache
`build/source-processing/omniopt-2607.04033v1/`, а source archive был успешно
распакован до `paper.tex`, всех chapters, tables, figures и bibliography.

| Объект | Размер | SHA-256 |
| --- | ---: | --- |
| `omniopt-2607.04033v1.pdf` | 5,010,419 bytes | `62afd6af2d5463057172ec575d129d257447803b16b0a7d39bbe872351318a00` |
| `omniopt-2607.04033v1-source.tar` | 4,187,716 bytes | `ed19007257f8fd0481ce44440ce3df9de59f9b87a45a1ff2327e279be1cf621d` |

PDF/source не добавлены в Git: checksum receipt достаточно для воспроизводимой
идентификации прочитанной версии, а binary/source paper не нужен Android build.
Task-referenced raw-файл
`raw/monitored\hugging-face-daily-papers\20260707-213045-omniopt-taxonomy-geometry-and-benchmarking-of-mo-2f762a3f.md`
в этом isolated worktree отсутствует.

## Предложенный framework

### Один шаг как meta-pipeline

Статья отделяет внешний S0 от пяти внутренних стадий S1–S5:

| Стадия | Роль | Примеры вмешательства |
| --- | --- | --- |
| S0 Signal acquisition | получить first-order, variance-reduced или curvature-augmented сигнал | обычный gradient, MARS/STORM correction, Sophia HVP, SAM second gradient |
| S1 Routing | разделить параметры по topology/module type | matrix weights отдельно от bias/norm/vector parameters |
| S2 Transform | изменить пространство или геометрию gradient | sign, Newton–Schulz/polar map, Kronecker transform, low-rank projection |
| S3 State evolution | обновить optimizer memory | momentum, second moment, factors, quantized/shared state |
| S4 Reconstruction | вернуть update в full parameter space | inverse rotation, projection-back, factor reconstruction |
| S5 Finalization | применить LR, decay и ограничения перед writeback | clipping, trust ratio, mask, sharpness correction, decoupled weight decay |

Практически важно identity-mapping principle: большинство методов изменяет
только одну-две стадии. Это позволяет отличать основной механизм от обычных
defaults и заранее видеть композиционные конфликты. Разные stages чаще можно
сочетать; два сильных S2-оператора требуют явного порядка.

### Геометрия через LMO и четыре оси

Norm-constrained linear minimization oracle выбирает экстремальное направление
в заданном norm ball. Эта единая запись связывает:

- Euclidean ball с normalized-gradient direction;
- `l_inf` ball с sign direction;
- spectral-norm ball с matrix polar direction `U V^T`;
- adaptive box с Adam-like `m / sqrt(v)`;
- metric/preconditioned view с Adam, Shampoo/SOAP и Muon.

Статья затем описывает метод четырьмя независимыми решениями:

1. **Axis I — update domain:** full, matrix, rotated или low-rank space.
2. **Axis II — state estimator:** momentum, second moment, variance reduction,
   curvature/projection state.
3. **Axis III — geometry/preconditioner:** LMO direction или Hessian/Gram/
   Fisher proxy, который превращает state в update direction.
4. **Axis IV — finalization:** LR, weight decay, projection-back, routing,
   fallback и refresh schedule.

Это полезная объяснительная координатная система, но не доказательство
взаимозаменяемости алгоритмов. Одинаковая LMO-форма не устраняет различия в
state estimation, approximation error, operator order, numerical behavior и
стоимости реализации.

## Таксономия методов

Survey распределяет 108 оптимизаторов по 5 непересекающимся основным families
и 15 subclasses. Effect tags отдельно показывают цель, поэтому «memory-saving»
не подменяет доказательство качества или устойчивости.

| Family | Механизм и представители | Сильная сторона | Главный риск по статье |
| --- | --- | --- | --- |
| T1 element-wise moment/scalar control | AdamW, Adan, MARS-AdamW, AdEMAMix, schedule-free, Prodigy | понятный и устойчивый baseline; variance reduction можно наслаивать | дополнительные scalar/moment tweaks часто не превосходят хорошо настроенный AdamW |
| T2 matrix structure | Muon/RMNP spectral, SOAP/Shampoo Kronecker, GaLore low-rank | лучше использует matrix geometry; сильные quality/stability candidates | orthogonalization/preconditioning дорогие; результат зависит от topology и operator order |
| T3 discretized direction | Lion, SignSGD, MARS-Lion | дешёвый step, один moment, локальная LR tolerance | coarse magnitude даёт ожидаемый quality gap; flat LR curve не означает хорошее качество |
| T4 state compression | AdaFactor, 8-bit Adam, Adam-mini, APOLLO, Conda, LOMO | снижает optimizer-state или gradient lifetime | aggressive projection становится lossy при росте effective gradient rank |
| T5 curvature/geometry wrappers | SAM, Sophia, AdamP, cautious filters, LAMB | stability/generalization/trust-region mechanisms можно оборачивать вокруг base optimizer | extra backward/HVP/state и method-specific knobs; в benchmark преимущества situational |

Отдельно полезен composition rule:

- variance-reduced Axis-II signal может питать AdamW, Lion или Shampoo;
- low-rank signal projection и low-bit state затрагивают разные объекты;
- post-update filter/trust ratio можно применять после base direction;
- spectral orthogonalization + low-rank projection конфликтуют в S2 и требуют
  определения порядка;
- LOMO-style streaming конфликтует с global statistics, delayed basis refresh,
  SAM second gradient и matrix preconditioners.

## Шесть измеряемых целей

OmniOpt запрещает делать вывод по одному final loss. Его effect taxonomy:

- **O1 convergence:** final loss/PPL, steps/tokens/time-to-target;
- **O2 step cost:** optimizer step time, FLOPs, synchronization, extra backward;
- **O3 memory:** state, factors, projections, quantization buffers;
- **O4 stability:** gradient coefficient of variation, spikes, NaN/Inf,
  incomplete runs;
- **O5 hyperparameter robustness:** sensitivity to LR/decay/batch/method knobs;
- **O6 generalization:** validation, downstream, OOD and transfer across scale,
  data, context and architecture.

O1–O3 доступны из одного run; O4 и lightweight O6 требуют log analysis; O5 и
полный O6 требуют нескольких configurations. Это важнее paper tiers: tier —
summary конкретного protocol, а O1–O6 — переносимый evaluation contract.

## Benchmark protocol

### Stage 1: broad short-context screening

- 24 optimizers из всех T1–T5;
- C4, LLaMA, sequence length 256;
- 60M/130M/350M/1B models, соответственно 10k/20k/60k/100k steps;
- tuned optimizer-only hyperparameters; model, data and schedule fixed;
- weight decay и gradient clipping disabled для изоляции S2/S3;
- C4 validation PPL, isolated optimizer-state GB и optimizer-step ms.

Ключевые 1B точки: APOLLO — best short-context PPL `13.53` при `0.790 GB`;
Muon и MARS-Shampoo — `13.72`, но `379.0` и `513.7 ms`; RMNP — `13.87`,
`2.495 GB`, `16.94 ms`; AdamW — `14.48`, `4.989 GB`, `18.62 ms`; Lion —
fastest `12.48 ms`, но PPL `17.02`; AdaFactor — `0.004 GB`, но PPL `14.92`.
Следовательно, winner меняется между quality, runtime и memory Pareto views.

### Stage 2: long-context transfer

- 12 stronger Stage-1 optimizers;
- FineWeb-Edu, sequence length 32k, 340M и 1B, около 30,720 steps;
- Transformer++, Gated DeltaNet, DeltaNet и GLA;
- paper protocol включает одинаковые clipping/weight-decay finalization rules;
- WikiText PPL и average accuracy десяти commonsense tasks;
- cross-scenario ranks, gradient dynamics и transfer служат O4/O6 evidence.

SOAP имеет лучший PPL в 7/8 architecture-scale scenarios и всегда top-2, но
его Stage-1 1B cost (`1371.5 ms`, `29.299 GB`) делает его quality ceiling, не
default. Лучший downstream score распределён между SOAP, MARS-AdamW, RMNP и
Muon, то есть даже PPL leader не является универсальным quality leader.

### Auxiliary checks and Muon ablation

- O4: post-warmup `GNormCV = std(norm(g))/mean(norm(g))`; все summarized runs
  завершились без NaN/Inf, но volatility различалась более чем на два порядка.
  Muon имел лучший aggregate stability rank; GLA давала rare single-step spikes
  почти всем methods.
- O5: один Gated-DeltaNet/340M scenario и три LR points (`0.2x`, `1x`, `5x`).
  Lion/MARS-Lion были наиболее flat, APOLLO — наиболее sensitive; flatness у
  Lion сосуществовала со слабым tuned quality.
- Sequence-length ablation: APOLLO ухудшился `13.53 -> 35.40` (`+21.87 PPL`),
  против AdamW `+7.39`; это согласуется с rank-bounded compression.
- Muon: удаление AdamW second moment без замены дало `17.78 -> 70.74`; добавление
  Newton–Schulz восстановило `16.86`, full Muon дал `16.60`. Momentum должен
  накапливаться до orthogonalization; LR scaling перед NS хуже. Symmetric LR
  scaling + post-NS Nesterov stack на standard Transformer (`13.58` at 1B),
  но не дают дополнительного gain вместе на Gated DeltaNet.

Paper tier I — AdamW, RMNP, Muon; tier II включает SOAP, APOLLO, MARS variants,
Conda, AdamP, Adan и Lion. Это не global leaderboard. Для Soll tiers нельзя
копировать без собственного workload-specific baseline.

## Качество evidence и ограничения

Сильные стороны:

- controlled-variable comparison и явно разделённые O1–O6;
- четыре model scales, short/long context и несколько model topologies;
- separate runtime/memory accounting, downstream tasks, stability и LR stress;
- mechanistic ablation проверяет не только optimizer names, но и operator order.

Ограничения, важные для Soll:

1. Это arXiv v1; benchmark проверяет 24 representatives из 108 surveyed methods.
2. Central tables дают point estimates без repeated-seed confidence intervals.
3. Stage 2 одновременно меняет data, context и architecture; отдельный
   sequence-length ablation полезен, но authors признают unmatched token budget.
4. O5 — только три LR points в одном scenario, а GNormCV — auxiliary proxy, не
   доказательство causal stability или final quality.
5. Публичный code воспроизводит основные scripts, но Stage-1 requirements —
   непинованные `torch`, `transformers`, `bitsandbytes`; Stage-2 scripts требуют
   вручную задать dataset/tokenizer/validation paths и 8-GPU environment.
6. GitHub API не показывает root repository license. В `Stage2-FWE` есть
   Apache-2.0, но нельзя автоматически распространять этот grant на Stage 1 и
   весь root. До импорта нужен component-level license review.
7. Ссылка paper на `huggingface.co/datasets/OpenRaiser/collections` не дала
   публично разрешаемый OmniOpt dataset; public author listing на дату проверки
   такого dataset не показывал. Полная replication требует отдельно доказать
   доступность exact data/tokenizer/log artifacts.

Поэтому опубликованные PPL/ms/GB — claims авторов в их hardware/protocol, а не
измеренные Soll результаты.

## Применимость к текущим контурам Soll

| Soll contour | Прямая применимость optimizer algorithms | Что действительно полезно | Решение |
| --- | --- | --- | --- |
| Android ONNX/Sherpa TTS и возможный local inference | нет: это inference, gradient/update/state отсутствуют | O2/O3/O4-style latency, PSS, thermal, failure metrics | не менять APK/dependencies |
| server `vLLM`/`llama.cpp` serving optimization | нет: backend/kernel/KV-cache/batching не являются training optimizer | controlled-variable и Pareto A/B discipline | держать отдельным inference benchmark |
| deferred PEFT/LoRA or model fine-tuning | условно высокая после появления exact model/data/hardware owner | AdamW baseline + 2–3 constraint-matched candidates, full O1–O6 | будущий server-side pilot |
| agent/source/KB/evaluation workflows | алгоритмы не применимы | multi-objective scorecard, transfer/stress and stop gates | переиспользовать методологию, не код |

Для вероятного PEFT/LoRA workload не следует сразу повторять все 24 methods.
Начальный shortlist определяется binding constraint:

- всегда **AdamW** как reference;
- **RMNP** только для matrix-heavy Transformer training, если важен
  quality/runtime balance и implementation/license поддержаны;
- **AdaFactor** или **8-bit Adam** при реальном optimizer-state OOM;
- **SOAP** только как дорогой quality ceiling;
- **Muon** для mechanistic/stability study с обязательной target-topology check;
- **APOLLO** только для short-context memory pressure с обязательным 32k/target
  context transfer gate;
- **Lion** только для дешёвого exploratory baseline с заранее принятой
  возможной quality regression.

## Bounded Soll pilot contract

Pilot открывается только после фиксации exact training workload. До запуска:

1. Зафиксировать model/revision, data snapshot, tokenizer, context distribution,
   task quality metric, hardware, precision, batch/accumulation и budget.
2. В отдельном desktop/server environment снять AdamW baseline минимум на трёх
   seeds; Android остаётся status/approval client.
3. Выбрать не более 2–3 alternatives по одному binding constraint. Не менять
   model, data order, initialization/seeds, schedule, tokens и tuning budget.
4. Собрать O1–O6: validation/task quality и time-to-target; optimizer step and
   end-to-end wall time; peak optimizer/total memory; spikes/NaN/OOM; LR/WD
   sensitivity; held-out context/architecture/task transfer.
5. Записать resolved package versions, optimizer implementation commit,
   license, parameter routing/fallback, full config and raw logs.
6. Promotion требует reproducible gain за пределами run-to-run variance,
   отсутствия quality/safety regression, приемлемой extra complexity и
   rollback to AdamW. Иначе source остаётся knowledge-only.

## Решение и измеримая ценность

Источник принят как **knowledge/evaluation cookbook**, но отклонён как прямой
Android или текущий inference optimization. Добавлено 0 optimizer packages,
0 training/inference runs и 0 production changes. Измеримая ценность этой
задачи — полная проверенная версия статьи, разбор 5-stage/4-axis/5-family/6-
objective framework, аудит 24-optimizer benchmark и четыре явных Soll
applicability decisions с bounded pilot gates.
