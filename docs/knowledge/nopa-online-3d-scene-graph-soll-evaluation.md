---
title: "NoPA: экспериментальная оценка online 3D scene graph для Soll"
task_id: 9f64e52d3f9d4380a823d56cf5aff30e
source_ref: source-item/9011e13c06d6/cf371816a49777af
source_version: arxiv:2607.00529v1
reviewed_at: 2026-07-22 Europe/Chisinau
---

# NoPA: экспериментальная оценка online 3D scene graph для Soll

## Краткий вывод

NoPA решает реальную, но пока отсутствующую в Soll задачу: на потоке RGB-D
кадров, depth maps и известных camera poses он строит и обновляет 3D semantic
scene graph. Вместо одного Gaussian ellipsoid на объект метод хранит
фиксированный particle set, сравнивает сложные распределения через MMD только в
неоднозначной зоне, после merge повторно выбирает фиксированное число particles
и восстанавливает потерянные relations через affinity clusters.

Полная статья проверена, а ключевая гипотеза о различимости распределений
проверена локальным deterministic microexperiment. На скрытом holdout
NoPA-inspired particle MMD дал `6/6` корректных merge/spawn решений, тогда как
moment-only proxy, видящий только centroid и covariance, дал `3/6`. Это
подтверждает узкую алгоритмическую идею на специально построенном synthetic
наборе, но не воспроизводит NoPA и не доказывает качество на реальной сцене.

С текущим Soll сравнение дало более важный отрицательный результат: Android
scanner принимает один RGB frame через CameraX, запускает ML Kit barcode
recognition и сохраняет строку кода. Он не производит depth, world pose, 2D
scene graph или 3D candidates, поэтому текущий Soll path: `0/6 runnable` на
association holdout. Без названного пользовательского сценария, RGB-D sensor,
calibration и серверного/desktop testbed интеграцию отложить. Production code,
Android dependencies и UI не менялись.

## Полная версия и provenance

Проверены первичные материалы:

- [Hugging Face Daily Paper](https://huggingface.co/papers/2607.00529);
- [arXiv `2607.00529v1`](https://arxiv.org/abs/2607.00529);
- [полный PDF](https://arxiv.org/pdf/2607.00529v1);
- [полный TeX source](https://arxiv.org/src/2607.00529v1).

Полная версия содержит 28 страниц и включает supplementary experiments. PDF и
TeX bundle скачаны в игнорируемый repository-local cache
`build/source-processing/nopa-2607.00529v1/`. Все 19 archive entries перед
распаковкой прошли проверку: absolute paths и `..` traversal отсутствуют.
Извлечены `main.tex`, `supple.tex`, bibliography, ECCV styles и 10 assets.

| Объект | Размер | SHA-256 |
| --- | ---: | --- |
| `nopa-2607.00529v1.pdf` | 6,513,698 bytes | `79b0cc49e139a20617f6c03d81f278202bf619c0871e41ea4a56e24469244c43` |
| `nopa-2607.00529v1-source.tar` | 5,629,628 bytes | `bb9d95a54811b125894cd2c625f957d1df02e183268f0232e0cce82fd4139b7a` |

ArXiv record фиксирует v1 от 1 July 2026, ECCV 2026 acceptance и paper license
CC BY-SA 4.0. Код NoPA, pinned repository revision, weights и отдельный dataset
license receipt в paper source не опубликованы. Поэтому upstream runtime,
модели и datasets не импортировались и не запускались.

Указанный в задаче raw path
`raw/monitored\hugging-face-daily-papers\20260702-190417-nopa-non-parametric-online-3d-scene-graph-genera-0161213d.md`
в isolated worktree отсутствует. Это не подменяется утверждением о локальном raw
ingestion: анализ опирается на canonical primary source и его checksums.

## Что именно предлагает NoPA

### Вход и состояние

Для каждого streaming frame нужен предсказанный 2D scene graph, depth map и
camera pose в `SE(3)`. Pixels внутри 2D object box back-project в world-frame
3D points. Один объект хранится как `n` samples его неизвестного occupancy
distribution, а не как одна Gaussian model.

Paper experiments используют `n=256`. Kernel density задаётся RBF kernel:

```text
k(x, y) = exp(-||x - y||^2 / (2 sigma^2))
f_hat(x | object) = mean_k k(x, particle_k)
```

### Двухэтапная association

1. Для local candidate и global object по particles оцениваются первые два
   moments и Hellinger distance между fitted Gaussians.
2. Пары ниже `delta_H - epsilon` сразу merge, выше `delta_H + epsilon` spawn.
3. Только margin band передаётся в RBF maximum mean discrepancy. `sigma^2`
   выбирается median heuristic, а MMD threshold калибруется на held-out
   sequence.
4. После merge KDE строится по union, но сохраняется снова ровно `n` particles.
   Поэтому память на object не растёт вместе с количеством кадров.

Важная деталь: MMD не является полной заменой пространственного gate. В
supplement авторы показывают, что слишком широкая ambiguous band ухудшает
качество, потому что MMD не кодирует Euclidean separation так явно, как
Hellinger pre-filter.

### Relationship propagation

MMD distances повторно используются как affinity. Relations между похожими
object candidates объединяются внутри cluster, а итоговый predicate выбирается
majority vote. Это не создаёт relation из ничего: хотя бы одно корректное 2D
observation должно существовать. Ошибка initial 2D object/predicate detector
остаётся верхней границей качества.

## Что показывают paper experiments

NoPA и baselines используют RGB-D и ground-truth poses. Основной runtime —
PyTorch на одной RTX 3090. 3DSSG содержит 1,482 scenes, 21,974 objects и 16,324
predicate relations; ReplicaSSG — 18 scenes, 1,526 objects и 582 relations.

### 3DSSG: reproduced FROSS против NoPA

| Метод | Rel recall | Obj recall | Pred recall | Obj mRecall | Pred mRecall | Latency | VRAM |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| FROSS reproduced | 25.7% | 60.6% | 30.7% | 62.4% | 17.7% | 22 ms | 1,204 MB |
| NoPA, 256 particles | 53.2% | 69.0% | 61.4% | 66.4% | 29.4% | 27 ms | 1,206 MB |

Относительно reproduced FROSS relationship recall вырос на 27.5 percentage
points, а latency — на 5 ms. Это сильный paper result с почти неизменным VRAM,
но не измерение на Soll hardware.

### ReplicaSSG: reproduced FROSS против NoPA

| Метод | Rel recall | Obj recall | Pred recall | Obj mRecall | Pred mRecall | Latency | VRAM |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| FROSS reproduced | 22.3% | 25.3% | 27.5% | 27.6% | 12.6% | 17 ms | 1,206 MB |
| NoPA, 256 particles | 36.9% | 28.6% | 39.5% | 29.8% | 18.6% | 23 ms | 1,230 MB |

### Почему нельзя переносить только particles

Ablation особенно важна для integration decision:

- FROSS baseline: relationship recall `25.7%`;
- заменить representation на particles без tailored merge: `17.6%`;
- добавить MMD merge: `26.3%`;
- добавить relationship propagation: `53.2%`.

То есть particle container сам по себе ухудшает graph result. Ценность возникает
из согласованной association и relation logic. Supplement также показывает
trade-off `64/128/256/512` particles: validation relationship recall достигает
максимума `53.7%` при 256, а 512 уже немного хуже. Заявление «больше particles
лучше» не подтверждается.

## Текущее решение Soll

Аудит ограничен реальными repository contracts:

```text
CameraX Preview + ImageAnalysis (keep only latest)
  -> ML Kit BarcodeScanning
  -> rawValue + barcode format
  -> ScannerRepository / Room scan item
```

`app/build.gradle.kts` подключает CameraX core/camera2/lifecycle/view и ML Kit
barcode scanning. `ScannerScreen.kt` отдаёт analyzer только `rawValue` и
`format`; `ScannerRepository.addScan()` нормализует строку, обрабатывает
duplicates и сохраняет scan item.

Для NoPA pipeline отсутствуют пять обязательных уровней:

1. metric depth или синхронизированный RGB-D stream;
2. calibrated intrinsics/extrinsics и stable world camera pose;
3. per-frame object plus predicate 2D scene graph;
4. world-frame particle/object association state;
5. global 3D scene graph consumer: navigation, manipulation или named spatial
   audit workflow.

Поэтому NoPA не является заменой scanner. Scanner отвечает на вопрос «какой код
в кадре», а NoPA — «какие объекты и relations накоплены в 3D мире». Их input,
output, quality metric и hardware budget различаются.

## Воспроизводимый локальный эксперимент

Machine-readable fixture:
`docs/knowledge/nopa-particle-merge-synthetic-v1.json`.

Executable audit:
`NopaOnline3dSceneGraphEvaluationTest`.

### Дизайн

Эксперимент использует calibration `6` + holdout `6` пар synthetic 3D particle
sets. Половина пар — один object с небольшим coordinate jitter; половина —
разные supports с одинаковыми centroid и covariance. Личные, production и
внешние данные не используются.

Сравниваются два component-level решения:

- **moment-only proxy:** merge, если centroid distance и covariance Frobenius
  distance не выше `0.1`;
- **particle MMD:** biased empirical RBF-MMD по всем pairs, deterministic median
  non-zero squared distance как `sigma^2`. Threshold `0.02646054724029792`
  получен только из calibration split как midpoint между worst merge и closest
  spawn; holdout после этого не менялся.

Это контролируемая проверка paper motivation, а не копия full NoPA. В paper MMD
запускается внутри Hellinger margin gate, median оценивается по random pairs,
threshold имеет dataset-specific scale, а object candidates приходят из RGB-D
и learned 2D SSG.

### Результат holdout

| Case | Gold | Moment proxy | Particle MMD | MMD score |
| --- | --- | --- | --- | ---: |
| same square + jitter | merge | merge | merge | 0.003662 |
| same 3D axes + jitter | merge | merge | merge | 0.005791 |
| same L-shape + jitter | merge | merge | merge | 0.004591 |
| different square density, same moments | spawn | merge | spawn | 0.052241 |
| different rotated support, same moments | spawn | merge | spawn | 0.028279 |
| different line support, same moments | spawn | merge | spawn | 0.069712 |

- moment-only proxy: `3/6` correct, accuracy `0.50`;
- particle MMD: `6/6` correct, accuracy `1.00`;
- current Soll scanner: `0/6 runnable`, потому что fixture требует 3D object
  particles, которых текущий pipeline не создаёт;
- unsafe side effects, network calls и production writes: `0`.

Результат намеренно adversarial для moment matching и слишком мал, чтобы
оценивать generalization. Он подтверждает только то, что higher-order support
может быть полезен, когда moments совпадают. Он не подтверждает paper recall,
latency, memory, sensor robustness или Android feasibility.

## Оценка интеграции

### Решение сейчас

Direct Android integration отклоняется. NoPA требует новый sensor/data/model
stack и не улучшает существующий barcode workflow. Добавлять PyTorch, RT-DETR,
RGB-D/AR dependency, 3D database или autonomous action surface без use case и
baseline означало бы создать отдельный продуктовый контур без измеримой Soll
ценности.

### Минимальный будущий pilot

Возвращаться к NoPA стоит только после появления named spatial workflow и
доступного calibrated RGB-D device. Первый pilot должен быть desktop/server,
offline и read-only:

```text
approved synthetic/simulated or consented RGB-D sequence
  -> pinned 2D object/predicate model
  -> pose + depth quality gates
  -> FROSS-style moment baseline and NoPA-style particle candidate
  -> immutable graph/evidence artifact
  -> Android review card only; no actuation
```

Promotion требует одновременно:

1. named user task и owner, где 3D relations меняют reviewable decision;
2. primary implementation repository, pinned revision, model/data licenses и
   reproducible environment;
3. calibrated depth/pose with explicit invalid-frame and drift handling;
4. fixed train/calibration/holdout scene split with no threshold leakage;
5. comparison against both current no-3D workflow and a simple moment baseline;
6. object/predicate/relationship recall, merge precision, fragmentation,
   latency p50/p95, peak RAM/VRAM, energy and thermal measurements;
7. at least `20%` relative relationship-recall lift at no more than `20%` p95
   latency regression on target hardware, plus bounded memory per object;
8. zero actuator calls, personal-data leakage and automatic task mutation;
   human approval and deletion/rollback remain mandatory.

До выполнения этих gates итог — **research accepted, интеграцию отложить**.

## Измеримая ценность

- полная 28-page article и TeX source скачаны и SHA-256 verified;
- формализованы particle representation, Hellinger/MMD association, fixed-size
  resampling и relationship propagation;
- проверены два paper benchmark и ключевые ablations;
- выполнен reproducible 12-case comparison: particle MMD `6/6` против
  moment-only `3/6` на holdout;
- текущая Soll 3D association coverage честно измерена как `0/6 runnable`;
- добавлено `0` production/runtime behavior, dependencies, model/dataset imports,
  UI changes, networked inference или external actions.

Текущая ценность — full-paper receipt, executable falsifiable microexperiment и
ясная reject/defer граница. Измеренная end-to-end Soll ценность остаётся `0`,
пока не появятся sensor prerequisites и конкретный spatial workflow.
