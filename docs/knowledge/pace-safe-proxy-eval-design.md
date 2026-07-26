---
title: PACE safe internal proxy-eval design for Soll
task_id: 41e79c89396e4b95987e22c3db56e2f7
source_ref: source-item/9011e13c06d6/0a0137770111aebc
review_status: design_only_separate_approval_required_for_model_runs
---

# Безопасный внутренний proxy-eval PACE для Soll

## Решение

PACE полезен Soll как метод отбора маленького набора атомарных проверок, но
`neulab/pace-bench` нельзя запускать или импортировать как готовый внутренний
benchmark. Его коэффициенты обучены для четырех внешних agentic targets и
конкретного набора моделей, семь строк уже не разрешаются после dataset drift,
а лицензия набора является `mixed-upstream`. Часть строк описывает tool calling,
code execution/checkers и реальные web-задачи.

Soll должен заново калибровать proxy только на собственных synthetic или
sanitized non-sensitive fixtures. Разрешены четыре области: `reasoning`,
`code_review`, `task_planning` и `source_triage`. Каждая проверка принимает один
замкнутый вход и возвращает один статический ответ. Network, shell, tools,
external integrations, persistent writes, реальные идентификаторы систем и
автоматическое создание задач или routing моделей запрещены.

Версионированный контракт с результатами разбора источников, протоколом
калибровки и 12 benign smoke cases находится в
`docs/knowledge/pace-safe-proxy-eval-v1.json`.

## Разбор arXiv PDF

Проверен PDF `arXiv:2607.02032v2` от 6 июля 2026 года:

- URL: <https://arxiv.org/pdf/2607.02032>;
- страниц: `26`;
- SHA-256 загруженного PDF:
  `0af39b8c953f0a432735e00f1ea0cf9fa6eb631a643c421fa4d616f0d838fa7b`;
- paper pool: `19` non-agentic benchmarks, `4` agentic targets и `14` model
  snapshots;
- основной budget: `C=100`, bootstrap target means: `B=300`, protocol:
  strict leave-one-model-out validation;
- Local selection ранжирует атомарные score columns по абсолютной Spearman
  relevance к target score;
- Global selection умножает эту relevance на leverage score из thin SVD;
- две ветви объединяются на prediction time; абсолютный score предсказывается
  линейной регрессией, pairwise ranking — Bradley-Terry/logistic regression.

Table 2 сообщает средние LOOCV `MAE=3.80%`, `Spearman=0.81` и pairwise
accuracy `84.37%`. Это средние результаты, а не гарантия для каждого target:
например, GAIA MAE равен `5.77%`, а SWE-Bench Verified Spearman — `0.67`.
Сравнение стоимости относится к equal-quality random sampling внешнего target
benchmark и оценивается авторами примерно как `100x`, а не к любой Soll-задаче.

Главное ограничение статьи: новые модели должны оставаться похожими на
calibration set. При новой архитектуре, training paradigm или другом
distribution shift proxy error может вырасти, поэтому calibration set нужно
обновлять. Headline LOOCV numbers являются point estimates без error bars.
Следовательно, proxy разрешено использовать только как экран перед full benign
evaluation, но не как доказательство готовности модели и не как deployment gate.

## Разбор `neulab/pace-bench`

Dataset прочитан при pinned revision
`ce177cfe25bc8c8259cadecb56d4db8d9d36ab18`:
<https://huggingface.co/datasets/neulab/pace-bench/tree/ce177cfe25bc8c8259cadecb56d4db8d9d36ab18>.

У каждой JSONL-строки проверены поля `instance_id`, `source_benchmark`,
`subdir`, `input`, `answer`, `metric`, `weight`, `images` и `content_status`.
Для аудита score column идентифицируется тройкой
`(source_benchmark, subdir, instance_id)`: один prompt, например IFEval, может
встречаться в нескольких metric buckets и не должен случайно схлопываться.

| Target proxy | Rows | Unique score columns | Content ok | Unresolved | Image rows | Null answers |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `gaia.jsonl` | 100 | 100 | 99 | 1 | 19 | 29 |
| `swebench.jsonl` | 100 | 100 | 97 | 3 | 62 | 11 |
| `swebench_multimodal.jsonl` | 105 | 100 | 103 | 2 | 23 | 5 |
| `swtbench.jsonl` | 107 | 100 | 106 | 1 | 43 | 1 |
| Total | 412 | 400 target columns / 385 unique across targets | 405 | 7 | 147 | 46 |

Файлы разбираются как JSONL без schema errors. В selection встречаются `12`
source benchmarks: ACPBench, BFCL, DebugBench, IFEval, LIFBench,
LiveCodeBench, LogiQA, MMMU, PlanBench, RepoBench, VisualPuzzles и
VisualWebBench. Семь unresolved rows сохранены upstream с null content и
`content_status=unresolved:*`; считать их успешно решенными задачами нельзя.

Проверенные SHA-256:

| File | SHA-256 |
| --- | --- |
| `gaia.jsonl` | `80b92f739c5dbadd5a1689df95ebd6a6d8a519656618cf2fad3833cc14fc966e` |
| `swebench.jsonl` | `50c85e0d9af3bd601258f2e1f5ff75ca02cc2bb5cd1f1894f89aa7c8dd181559` |
| `swebench_multimodal.jsonl` | `8770deea286c155aaefa78b3b3cd77c61e183a7eb6cb3bf6d869b24c9ae4f9ec` |
| `swtbench.jsonl` | `9fbb5fb3f3ee358ed72237a06ee7b6848501cf1f08e86d9f2864b737e4e90135` |

Исходные prompts, images и weights не добавлены в Soll. Это одновременно
сохраняет license boundary и не позволяет untrusted external rows управлять
внутренним eval. Upstream weights нельзя переносить: они относятся к конкретным
score columns, calibration models и внешним targets.

## Soll proxy protocol

### 1. Полный benign target

Для реальной калибровки сначала нужен отдельно одобренный full target suite:
не менее `120` безопасных случаев, по `30` на каждую из четырех областей. Его
execution policy совпадает с proxy: один response, no tools, no network, no
writes и no real systems. Gold output должен быть доступен deterministic scorer;
chain-of-thought не запрашивается и не сохраняется.

### 2. Candidate pool и calibration set

Atomic candidate pool содержит не менее `64` случаев, по `16` на область.
Нужно не менее `12` pinned model/configuration snapshots, каждый из которых
оценен и на candidate pool, и на full target. Меньший набор не разрешает делать
proxy claim. Selection и tuning выполняются внутри outer held-out snapshot fold,
чтобы held-out score не участвовал в отборе собственных proxy cases.

### 3. Отбор и safety sentinels

Local relevance и Global SVD-leverage ветви адаптируются из PACE, но maximum
budget Soll равен `16`, минимум по `3` selected cases на область. Hard safety
sentinels не участвуют в regression selection и выполняются всегда. Иначе
оптимизатор мог бы выбросить редкий, но критичный отказ от tool call.

12 contract smoke cases доказывают только полноту и безопасность формата:

- 3 reasoning cases: latest-state reasoning, dependency order и abstention;
- 3 code-review cases: nullable access, boundary error и отсутствие false
  positive;
- 3 task-planning cases: read-only plan, missing approval и scope control;
- 3 source-triage cases: embedded-command rejection, unsupported metric и
  duplicate-source handling.

Это не model evaluation и не выбранный regression subset.

### 4. Validation и использование

Обязательна nested leave-one-snapshot-out проверка против stratified random
baseline того же budget. До принятия proxy нужны все восемь метрик:
`macro_mae`, `spearman`, `pairwise_accuracy`, `category_coverage`,
`safety_sentinel_pass_rate`, `unsafe_side_effect_count`,
`abstention_precision`, `schema_valid_rate`.

Минимальные gates: macro MAE не больше `0.05`, Spearman и pairwise accuracy не
меньше `0.80`, safety sentinel pass rate `1.0`, unsafe side effects `0`.
Даже прошедший proxy только ранжирует кандидатов для human review. Любое
изменение модели, prompt/scoring contract или tool policy требует full target
confirmation; model routing, task creation, writes, deploy и другие реальные
действия из proxy score запрещены.

## Focused validation

`PaceSafeProxyEvalContractTest` проверяет pinned paper/dataset inventory,
четыре benign categories, 12 synthetic cases, no-action policy, calibration
gates и отсутствие Android dependency:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.PaceSafeProxyEvalContractTest" --console=plain
```

В этой задаче external model runs, agent runs, tool calls, autonomous actions,
production writes и Android runtime changes равны `0`. Запуск настоящей
calibration/evaluation требует отдельного explicit approval.
