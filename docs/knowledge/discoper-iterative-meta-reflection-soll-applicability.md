---
title: "DiscoPER: итеративная мета-рефлексия и применимость к Soll"
task_id: 003292f1865441999e0567eac5f521b3
source_ref: source-item/9011e13c06d6/8780e805a5e2ff9d
source_version: arxiv:2607.01131v1
reviewed_at: 2026-07-22 Europe/Chisinau
---

# DiscoPER: итеративная мета-рефлексия и применимость к Soll

## Краткий вывод

DiscoPER формализует автономный поиск как цикл **Propose–Evaluate–Reflect**.
LLM предлагает проверяемую гипотезу, отдельный планировщик выбирает
статистический тест, результат проверяется на train и held-out split, а затем
мета-рефлексия рассматривает накопленные принятые и отклонённые утверждения как
новый набор данных. Она ищет пробелы, повторяющиеся конфаунды, противоречия и
составные связи и направляет следующий раунд поиска.

Главная полезная для Soll идея — не «LLM сам пишет и запускает произвольный
Python», а **проверяемая рефлексия над историей source-processing**. В текущем
`soll_app` уже есть `SollSourceItem` с reasoning/evidence/audit полями,
`SollLearningItem` для инсайтов и `SollTask` с `valueMetric`, acceptance и test
планом. Поэтому ограниченный server/desktop prototype возможен без нового
Android-модуля: frozen snapshot завершённых source items, фиксированный набор
read-only анализов, отдельный sealed audit split, advisory insight с точными
evidence IDs и ручное approve/reject.

Прямой перенос DiscoPER отклонён. Произвольный код, повторное адаптивное
использование одного validation split, некорректированные множественные тесты,
VLM/API-вызовы и автоматическое создание задач не должны появляться ни в
Android, ни в production worker. Текущая задача даёт проверенный research и
pilot contract, но не измеренное улучшение Soll runtime.

## Полная версия и provenance

Проверены первичные материалы:

- [Hugging Face Daily Paper](https://huggingface.co/papers/2607.01131);
- [arXiv `2607.01131v1`](https://arxiv.org/abs/2607.01131);
- [полный PDF](https://arxiv.org/pdf/2607.01131);
- [полный TeX source](https://arxiv.org/src/2607.01131).

PDF и source bundle скачаны в игнорируемый cache
`build/source-processing/discoper-2607.01131v1/`. Перед распаковкой все 14
archive entries были проверены на absolute/path-traversal names. В source
подтверждены `main.tex`, `appendix.tex`, `main.bbl`, style, README и восемь
figure PDF.

| Объект | Размер | SHA-256 |
| --- | ---: | --- |
| `discoper-2607.01131v1.pdf` | 3,085,273 bytes | `2e332008e059b144a6527cf97e06751165effd966d2d2110a8aa10841f5fb980` |
| `discoper-2607.01131v1-source.tar` | 2,581,504 bytes | `21edcf7b5df99f66fca0e83c204b134b5118fcf907903525a09c6027eac806ce` |

Binary/source не добавлены в Git: checksum receipt воспроизводимо фиксирует
прочитанную версию, а paper assets не нужны Android build. Task-referenced raw
file
`raw/monitored\hugging-face-daily-papers\20260702-190417-autonomous-scientific-discovery-via-iterative-me-b3bc849c.md`
в isolated worktree отсутствует; это явно не подменяется утверждением о
локальном raw ingestion. Исследование выполнено по canonical primary source.

В v1 source нет ссылки на воспроизводимый implementation/data repository с
revision и component license. ArXiv distribution license не является лицензией
на предполагаемый agent code или iNatDisco bundle, поэтому код и данные не
импортировались.

## Формальная модель алгоритма

### Состояние

Пусть `X` — набор из `N` наблюдений, возможно с изображениями. Дополнительное
prior-содержание `P` бывает пустым, частичным или полным. Гипотеза `h` —
программа, которая получает `X` и возвращает `supported`/`rejected` плюс
статистическое evidence.

Система поддерживает четыре состояния:

- `C_t` — принятые claims/discoveries после итерации `t`;
- `C_hat_t` — отклонённые claims;
- `G_t` — guidance, полученное из принятых и отклонённых claims;
- `H` — пространство гипотез: от pairwise edges до open code hypotheses.

Обобщённый шаг:

```text
h_t[1..K] ~ Propose(X, C_(t-1), G_(t-1), P)
(delta_C_t, delta_C_hat_t) = Evaluate(h_t[1..K], X, history)
C_t = C_(t-1) union delta_C_t
C_hat_t = C_hat_(t-1) union delta_C_hat_t
G_t = Reflect(C_t, C_hat_t)
```

В paper runs выполняются 100 итераций, одна гипотеза на итерацию, reflection
каждые 5 итераций и три повтора для среднего и стандартного отклонения.

### 1. Propose

Генератор получает schema, column statistics, sample rows, последние результаты,
список запрещённых повторов и `G_(t-1)`. Для мультимодального набора ему также
передаются sample images. Выход implementation prompt — JSON со `statement`,
`scope`, `variables`, `expected_direction` и `risk_flags`.

Новизна не равна случайности: prompt требует не повторять отклонённый угол,
искать unexplored combinations, interactions, thresholds и conditional
relationships. `P = empty` означает отсутствие заданного research question,
но prompt, schema и tool catalog всё равно создают сильный inductive bias.

### 2. Evaluate

Experiment planner обязан согласовать claim, grouping variable, metric и data
slice. Код/план сначала можно настраивать на train split. После этого тот же
анализ один раз применяется к held-out validation split.

Утверждение принимается только при одновременном выполнении:

```text
abs(delta_train) >= 0.2
p_train <= 0.05
abs(delta_validation) >= 0.2
p_validation <= 0.05
abs(delta_validation) >= 0.6 * abs(delta_train)
```

Seven statistical primitives описаны в appendix:

| Primitive | Назначение и evidence |
| --- | --- |
| `corr_test` | Spearman/Pearson; coefficient и p-value |
| `group_diff_test` | Cliff's delta/Cohen's d; permutation, Mann–Whitney U или t-test |
| `predictive_test` | random forest, 5-fold CV, AUC как effect size |
| `cluster_and_enrich` | k-means + chi-squared, Cramer's V |
| `stratified_retest` | повтор primary test по strata и stability score |
| `visual_attribute_test` | VLM labels + chi-squared, Cramer's V |
| `visual_group_comparison` | VLM group differences и mean confidence |

Это уже, чем формальная декларация «все Turing-computable tests»: опубликованный
appendix показывает фиксированный tool catalog и structured planner. Более того,
experiment-planning prompt перечисляет шесть tools и пропускает
`cluster_and_enrich`, хотя соседняя таблица говорит о семи.

### 3. Reflect

Отдельный LLM получает полную историю `C_t` и `C_hat_t`. Его выход — не claim о
raw data, а meta-insight с `observation`, `actionable_recommendation`,
`affected_variables`, `meta_type`, `priority` и `source_insight_ids`.

Шесть типов guidance:

1. **Confound Pattern** — один moderator повторяется в разных claims.
2. **Variable Cluster** — переменные совместно появляются в успешных claims.
3. **Gap** — часть variable/relationship space не проверена.
4. **Success Pattern** — тип гипотез имеет лучший support rate.
5. **Contradiction** — claims дают противоположные эффекты.
6. **Interaction Hint** — пересечения claims подразумевают compound test.

Guided Hypothesis Prompt требует, чтобы следующая гипотеза ссылалась на
конкретный meta-insight. В iNatDisco examples reflection отбрасывает
непродуктивную positional accuracy, предлагает joint latitude-by-longitude
niche test и требует stratification by hemisphere для seasonal claims.

## Что показали эксперименты

### Primary iNatDisco

| Dataset | DiscoPER без Reflect | DiscoPER | Guided baselines |
| --- | --- | --- | --- |
| iNatDisco-800: 800 rows, 8 species, 9 patterns | recall `7/9`, support `70.0% +/- 2%` | `8/9`, `72.7% +/- 3%` | Heureka-like `3/9`; ExperiGen-like `3/9` |
| iNatDisco-50K: 50,000 rows, 9,776 species, 12 patterns | `6/12`, `66.6% +/- 3%` | `8/12`, `74.2% +/- 3%` | Heureka-like `2/12`; ExperiGen-like `3/12` |

Reflection уменьшила долю простых «X выше Y» hypotheses с `92%` до `69%`,
добавила interactions `0% -> 2%`, correlations `0% -> 2%` и seasonal/visual
формы «X peaks in Y» `6% -> 14%`. Это небольшой сдвиг по количеству, но один
compound test дал поддержанный результат с `p < 0.001`, которого baseline не
предложил.

### Проверки на prior и vision

- В iNatDisco-800-CF пять известных ecological relations инвертированы в
  metadata. Модель продолжает предлагать prior-based hypotheses, но Evaluate
  отклоняет противоречащие данным claims; это полезная qualitative проверка,
  а не гарантия отсутствия leakage.
- Synthetic visual benchmark содержит 5,000 изображений и 8 известных
  patterns. После 100 итераций найдено только `3/8`: `2` metadata, `1` vision,
  `0` cross-modal; support rate `54.2%`. Узкое место — шумное VLM feature
  extraction и statistical validation, а не только generation.
- Model choice сильно меняет результат: appendix указывает Sonnet 4.5
  `8/9`, Opus 4.6 `4/9`, GPT-5.4 `3/9`, text-only DeepSeek V4 Pro `2/9`.
- User guidance повышает topic adherence до `68%`, но снижает recall до `6/9`
  и support rate до `36.7%`. Управляемость сужает open exploration.
- Полный experimental budget: около `86` runs, `8,250` API calls, `33M` input
  tokens, `8M` output tokens, `$220` и `72` wall-clock hours.

## Критический разбор evidence

### Что действительно подтверждено

1. Ablation с одинаковым framework показывает устойчивое направление выигрыша
   Reflect на двух iNatDisco размерах и двух causal datasets.
2. Counterfactual data ослабляет объяснение «модель только вспоминает экологию».
3. Effect-size threshold, validation split и overfit ratio лучше, чем принятие
   claim по LLM confidence.
4. Synthetic benchmark честно показывает слабость текущего visual path.

### Что не позволяет считать claims научными открытиями

1. **Adaptive holdout reuse.** Один validation split используется на протяжении
   до 100 адаптивных итераций; его accept/reject результаты попадают в Reflect и
   меняют следующие hypotheses. «Один validation call на hypothesis» не делает
   holdout sealed. Нужен отдельный final audit set или reusable-holdout protocol.
2. **Multiple testing.** Для десятков адаптивных tests используется `p <= 0.05`,
   но для DiscoPER не описана FDR/Bonferroni correction. Effect threshold не
   контролирует false discovery rate.
3. **Не хватает split protocol.** V1 не фиксирует доли train/validation,
   stratification/group boundaries и split seeds. Нельзя исключить temporal,
   spatial, species или near-duplicate leakage.
4. **Judge-dependent recall.** Sonnet judge считает pattern найденным уже при
   partial score `1`; manually проверены только score-2 matches, agreement `95%`.
   Partial matches, которые тоже увеличивают recall, не получили такой проверки.
5. **Incomplete/circular target set.** Literature patterns неполны и заранее
   отфильтрованы по поддержке в тех же observational data. Support rate можно
   повышать очевидными hypotheses; сами авторы признают эту gaming surface.
6. **Association is not causation.** Correlation, group difference, clustering
   и prediction не идентифицируют causal effects без DAG/assumption/design.
   «Supported» должно означать data association, не установленный механизм.
7. **Specification drift.** Main text называет default `Claude Sonnet 4.6`,
   appendix и baseline protocol — `Claude Sonnet 4.5`. Formal method обещает
   arbitrary Python, implementation — seven primitives, а planner prompt
   перечисляет только шесть. Для `visual_group_comparison` указан confidence,
   но общий acceptance contract требует p-value.
8. **Reproducibility and safety.** Нет pinned code/data repository, exact
   environment, dataset license receipt, execution sandbox, egress policy,
   resource limits или untrusted-code threat model. Human scrutiny указана как
   limitation, но не встроена в claim state machine.

Следовательно, paper results — evidence перспективного search heuristic, а не
готовый validation или safety contract для Soll.

## Применимость к текущему Soll

Текущая Android domain model уже несёт необходимую review metadata:

- `SollSourceItem`: `summary`, `usefulness`, `reasoning`, `evidenceLevel`,
  `projectFit`, `actionability`, `dualUseRisk`, `safeNextStep`, `needsDeepDive`,
  `auditRef`, `evidenceRef`, `verificationArtifact`, `statusReason`;
- `SollLearningItem`: существующая advisory surface для `INSIGHTS`;
- `SollTask`: `sourceRef`, `outcomeArtifacts`, `valueMetric`, `approvalId`,
  `acceptanceCriteria`, `testPlan` и execution receipt;
- существующие Tasks / Insights / Sources и approve/reject UI уже отделяют
  наблюдение от разрешённого действия.

| Контур | Применимость | Решение |
| --- | --- | --- |
| Android-hosted autonomous discovery | низкая: нет statistical runtime, sandbox или данных; батарея/PII/egress риски | отклонить; Android остаётся review client |
| Server worker с arbitrary code/VLM/API tools | теоретически высокая, практически небезопасная и невоспроизводимая | отклонить до отдельного sandbox/security проекта |
| Offline reflect-only audit source-processing | высокая: типизированные existing records и review surfaces | допустимый proposal-only prototype |

## Bounded Soll prototype: Source Meta-Reflection Audit

Prototype не реализуется в этой задаче: isolated worktree не содержит
утверждённого redacted export завершённых source items и operator labels. Без
этих данных любое измерение было бы синтетическим и не доказывало бы Soll value.

### Контракт данных

1. Экспортировать только terminal source/task receipts: opaque ID, source type,
   timestamps, evidence level, project fit, actionability, terminal status,
   verification-artifact presence, source-value completeness и human outcome.
2. Исключить raw content, chat text, credentials, personal data и arbitrary
   artifact bodies. Зафиксировать snapshot SHA-256 и schema version.
3. Разделить данные до анализа по time/group boundary на discovery, tuning и
   sealed audit. Audit раскрывается один раз после frozen hypotheses.

### Safe Propose–Evaluate–Reflect

1. **Propose** возвращает только JSON query plan из allowlist: coverage gap,
   group-rate difference, duplicate theme, contradiction или missing evidence.
   Никакого Python/shell/SQL generation.
2. **Evaluate** выполняет deterministic read-only functions с minimum cell size,
   confidence interval, effect size и Benjamini–Hochberg FDR `q <= 0.05`.
   Недостаток данных даёт `unknown`, не supported claim.
3. **Reflect** получает aggregate accepted/rejected receipts, ищет шесть типов
   meta-insight и сохраняет exact input IDs, test/version и counterevidence.
4. **Publish** создаёт только draft `SollLearningItem`/insight. Создание task,
   смена source priority, notification, write или integration call возможны
   лишь после существующего human approval flow.

### Baselines, metrics и promotion gates

Сравнить reflect-only вариант с двумя baseline на одном frozen snapshot:
deterministic coverage report и такой же proposer без Reflect.

Обязательные metrics:

- human-reviewed precision и Wilson interval на sealed audit;
- число unique useful findings и lift над обоими baselines;
- duplicate/contradiction rate;
- FDR-adjusted supported count и abstention rate;
- evidence traceability/completeness;
- false high-confidence claims;
- wall time, local compute/API cost и side-effect count.

Promotion возможен только если одновременно:

1. не менее 100 terminal records и заранее размеченный audit checklist;
2. audit precision не ниже `0.80`, lower Wilson bound не ниже `0.65`;
3. unique useful findings минимум на `20%` выше обоих baselines;
4. duplicate rate не выше `10%`, FDR `q <= 0.05`;
5. `100%` insight cards имеют input IDs, method/version и counterevidence;
6. false high-confidence claims, external calls, autonomous actions и
   production writes равны `0`;
7. human reviewer принимает каждое дальнейшее действие, а rollback означает
   отключение producer без миграции Android/public API.

До прохождения всех gates статус — **knowledge accepted, prototype deferred**.

## Решение и измеримая ценность

Полная v1 статья и TeX source скачаны, распакованы и SHA-256 проверены. Детально
разобраны 3 стадии, 7 statistical primitives, 6 meta-insight types, acceptance
thresholds, primary/counterfactual/synthetic/causal evidence, compute и восемь
критических ограничений. Оценены 3 Soll contour и сформирован один bounded
reflect-only pilot с 7 promotion gates.

Добавлено `0` agent/model runs, `0` paper code/datasets/dependencies, `0`
arbitrary-code executions, `0` Android/server runtime changes и `0` external
side effects. Измеренная runtime/model-quality ценность пока равна `0`; текущая
ценность — воспроизводимый full-paper receipt, проверяемая архитектурная оценка
и безопасный контракт будущего offline pilot.
