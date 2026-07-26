---
title: "Снижение ошибок ссылок на данные в табличном LLM-pipeline"
task_id: 798292d19f554cc6b64f0ecc2c9eaeb4
source_ref: source-item/9011e13c06d6/43bc9d100c528d57
source_version: arxiv:2606.32029v1
assigned_analyst: "Soll Table Reliability Analyst"
assignment_status: assigned_analysis_completed
reviewed_at: 2026-07-23 Europe/Chisinau
---

# Снижение ошибок ссылок на данные в табличном LLM-pipeline

## Назначение и решение

Роль **Soll Table Reliability Analyst** назначена владельцу аналитической части
server/AI table pipeline. В рамках назначения выполнены детальный разбор статьи,
аудит текущих точек Soll и proposal-only интеграционный контракт
`table-data-referencing-error-reduction-v1.json`. Следующий runtime owner —
**Soll Server/AI Pipeline**; Android-клиент не должен сам запускать critic,
хранить provider credentials или выбирать модель.

Текущий `soll_app` не содержит table QA/extraction pipeline. Поиск в
`app/src/main` не обнаружил tabular evaluator, DRE critic или отдельный table
inference route. Доступный model-chat путь идёт через
`SollGateway.askModelChat(...)` к backend. Поэтому в этой задаче методы статьи
интегрированы как проверяемый pipeline contract и promotion gates, а не как
вымышленное production-подключение к отсутствующему runtime.

## Provenance и полный текст

Проверены первичные материалы:

- [Hugging Face paper record](https://huggingface.co/papers/2606.32029);
- [arXiv record](https://arxiv.org/abs/2606.32029);
- [полный arXiv HTML](https://arxiv.org/html/2606.32029);
- [PDF v1](https://arxiv.org/pdf/2606.32029v1);
- [TeX source v1](https://arxiv.org/e-print/2606.32029v1);
- [публичная реализация авторов](https://github.com/ayyyq/table-referencing),
  проверенная на `7e17ba3ec2d8f0238df8f2a1094491162ae10946`.

Canonical версия — `arxiv:2606.32029v1`, опубликованная 30 июня 2026 и
помеченная авторами как ACL 2026 Oral. Локальный ignored cache содержит:

| Объект | Размер | SHA-256 |
| --- | ---: | --- |
| PDF | 558,235 bytes; 19 page objects | `9fd9f94b6b21136f44fe529e5eab5715970338e35277392de6c0db7d2787d551` |
| TeX archive | 264,898 bytes; 9 entries | `e04af27216e44f3609afb7c2e62226b49a4ea207f3769872971902c5f4742c10` |

Все 9 archive entries имеют относительные traversal-free пути. Проверены
основной TeX, метод, эксперименты, приложения, critic prompt, synthetic-positive
правила, training details и limitations. Указанный задачей raw-файл
`raw/monitored\hugging-face-daily-papers\20260702-190417-when-llms-read-tables-carelessly-measuring-and-r-c5d56294.md`
в isolated worktree отсутствует, поэтому он не представлен как локальное
доказательство. Upstream code, dataset, model и dependencies не импортировались
и не исполнялись. На проверенной ревизии upstream нет `LICENSE` файла, поэтому
код нельзя копировать в Soll без отдельной license-проверки.

## Что измеряет paper

Data Referencing Error (DRE) — ошибка не обязательно в логике ответа, а в том,
как модель извлекает и использует значения исходной таблицы.

1. **Incorrect Citation**: неверное значение, перепутанная строка/колонка или
   выдуманное табличное содержимое.
2. **Omitted Information**: пропущен элемент обязательного множества, например
   одна строка из ответа на запрос «перечислить все».

Это отдельная ось качества. Финальный ответ может оказаться правильным при
ошибочном промежуточном цитировании: для SciTab paper сообщает
`Correct-in-DRE = 65.57%`. Обычная инструкция «не пропускай и не выдумывай» не
решила проблему: для Qwen3-8B/WTQ DRE rate изменился лишь с `14.04%` до
`12.50%`, а accuracy — с `77.14%` до `77.51%`.

Paper определяет три обязательные метрики:

```text
DRE Rate             = responses_with_DRE / all_responses
Correct-in-DRE Ratio = correct_responses_with_DRE / responses_with_DRE
DRE-in-Incorrect     = incorrect_responses_with_DRE / incorrect_responses
```

Нужны также отдельные rates для `incorrect_citation` и
`omitted_information`: единый DRE rate не показывает, сломался cell lookup или
completeness.

## Critic и заявленные способы снижения

### Судья для исследования

Sonnet-3.7+gt получает table, question, response segment и ground-truth answer.
Ground truth снижает false negatives, но судья обязан отличать вычислительную
ошибку от DRE. Три PhD-аннотатора проверили случайные 100 примеров; paper
сообщает среднюю accuracy `92.67%`. Это author-reported judge validation, а не
измерение Soll и не основание автоматически доверять любому LLM judge.

### Critic-based filtering

Для каждого вопроса paper генерирует `N=8` кандидатов. Critic не выбирает один
«лучший» ответ: он оставляет **множество с минимальным числом DRE**, после чего
можно применить majority voting. Ограничение существенно: два DRE-free ответа
могут давать разные финальные ответы из-за дальнейшей reasoning-ошибки.

### Segment-level rejection sampling

Paper делит reasoning response по маркеру `Wait`, перепроверяет сегмент и
перегенерирует только не прошедшую часть до acceptance или лимита `N=8`.
Максимальный выигрыш full-set accuracy в Table 3 — `+11.96` percentage points
для Distill-Qwen-7B/TableBench. Это не универсальная гарантия и не результат
Soll.

Для Soll скрытая chain-of-thought и literal `Wait` не являются API contract.
Безопасная адаптация проверяет явный answer draft/evidence block или весь
ответ. После retry limit результат становится `needs_human_review`/`abstain`, а
не бесконечно регенерируется и не принимается молча.

### Малый critic

Авторы обучили Qwen3-4B-Instruct в два этапа: SFT warm-up на 2,000 balanced
examples, затем RLVR/GRPO на 5,712 samples. Evaluation содержит 3,600 balanced
segments: 3 generation models x 3 datasets x 400. Critic-4B достиг `78.16%`
overall F1 против `69.51%` у исходного Qwen3-4B-Instruct.

Synthetic critic использовал четыре perturbation: swap row, swap column,
remove source row и remove one enumerated row. Его OOD качество ухудшалось на
FinQA и Llama4-Scout, поэтому synthetic-only validation запрещена как promotion
evidence.

## Интеграционный контракт Soll v1

Целевая точка — будущий server-side table-processing adapter до публикации
LLM-ответа. Пять стадий должны оставаться наблюдаемыми:

1. **Normalize** — table snapshot получает digest, стабильные `row_id`,
   `column_id`, raw cell value и формат источника; нельзя сравнивать только
   отрендеренный текст.
2. **Generate** — model request сохраняет question, snapshot digest, model
   identity и bounded sampling configuration; output содержит явный evidence
   block, когда задача требует табличные значения.
3. **Deterministic checks** — exact normalized cell match ловит неверные
   цитаты, а set comparison проверяет полноту запросов «all/list every».
4. **Critic** — получает table/question/public answer segment, возвращает
   `incorrect_citation`, `omitted_information`, evidence и confidence. Он не
   подменяет final-answer evaluator.
5. **Policy** — в audit mode только измеряет; в filtering mode сохраняет весь
   minimum-DRE subset; в rejection mode делает bounded retries и fail-closed;
   публикация хранит decision/cost receipt.

Machine-readable contract фиксирует paper metrics, 12 runtime/evaluation
metrics, три policy mode, fail-closed states и promotion gates. Он не вызывает
модель, сеть, credentials или произвольные команды.

## Promotion gates

Runtime pilot разрешён только отдельной задачей, если владелец Server/AI
Pipeline предоставляет non-sensitive table workload и baseline. До promotion:

- versioned table snapshots и human-reviewed DRE labels с обоими типами ошибок;
- critic precision/recall/F1 и особенно false-negative rate по каждому домену;
- DRE rate, incorrect-citation rate, omission rate и final accuracy до/после;
- отдельная оценка DRE-challenging subset и полного набора;
- p50/p95 latency, tokens, retries, candidate count и cost per accepted answer;
- no-regression для clean answers, deterministic validators и abstention path;
- synthetic-only dataset не считается OOD proof;
- rollback к single-pass backend path без изменения Android public contract.

Paper не исследует причины DRE интерпретируемо и лишь предварительно связывает
их с недостаточным attention к таблице. Поэтому attention steering, обучение
Critic-4B и импорт upstream остаются вне этой задачи.

## Детерминированный smoke

Контракт содержит четыре synthetic outcomes: clean/correct,
correct-with-citation-DRE, incorrect-with-omission и incorrect-without-DRE.
Focused JVM test пересчитывает:

| Metric | Expected |
| --- | ---: |
| DRE rate | `2/4 = 0.5` |
| incorrect-citation rate | `1/4 = 0.25` |
| omission rate | `1/4 = 0.25` |
| Correct-in-DRE | `1/2 = 0.5` |
| DRE-in-Incorrect | `1/2 = 0.5` |
| final accuracy | `2/4 = 0.5` |

Filtering smoke оставляет два minimum-DRE кандидата вместо выдуманного single
best. Rejection smoke принимает clean segment с первой попытки, исправленный —
со второй, а не прошедший retry limit переводит в `needs_human_review`.

Это измеримый contract smoke, но не model-quality claim: production table
requests, model/critic executions, external integrations и runtime changes в
этой задаче равны `0`.
