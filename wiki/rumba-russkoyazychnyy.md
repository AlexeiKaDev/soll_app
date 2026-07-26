# RUMBA: решение об интеграции в Soll app

Дата проверки: 2026-07-26.

## Решение

Source signal **валидирован и релевантен** для Soll как основа будущей
офлайн-оценки долгосрочной памяти на русском языке. Интегрировать RUMBA в
Android runtime сейчас не нужно: текущая локальная память Soll сохраняет
принятые proactive suggestions, но не представляет полную историю
user-assistant сессий и не участвует в question-conditioned retrieval для
chat turn.

Из RUMBA принимается диагностический контракт: оценивать результат не только
одним aggregate score, а отдельно по четырём осям — semantic type, session
scope, temporality и temporal expression. Особенно полезны для Soll проверки
`UpdatingInfo`, `DeleteInfo` и `Abstention`: они разделяют актуализацию факта,
забывание по запросу пользователя и отказ от ответа при отсутствии данных.

Решение — **conditional offline evaluation candidate**. В этом slice не
импортируются dataset, Python runner или memory frameworks, не добавляются
dependencies и credentials, не выполняются model/API calls и не меняются
production/runtime contracts.

## Граница исходника

Запрошенная `wiki/rumba-russkoyazychnyy.md` и monitored source
`monitored/habr-sber-company/20260725-003006-rumba-b0e3fd2f.md` отсутствовали в
Base SHA. Недоступное содержимое monitored artifact не восстанавливается и не
цитируется как repository evidence.

Факты о benchmark проверены по трём первичным публичным surface:

- [arXiv:2607.21447v1](https://arxiv.org/abs/2607.21447), submitted
  2026-07-23;
- [ai-forever/RUMBA](https://github.com/ai-forever/RUMBA), upstream code and
  evaluation pipeline;
- [ai-forever/RUMBA dataset](https://huggingface.co/datasets/ai-forever/RUMBA),
  RU/EN schema and published test split.

RUMBA состоит из timestamped multi-session user-assistant dialogues и QA pairs.
Публичный dataset surface показывает `85` user IDs и около `1.54k` QA rows.
Русская версия является основной, а английская — aligned subset. Benchmark
предусматривает два разных режима: full-context и memory-system pipeline с
последовательным ingestion, retrieval, answer generation и evaluation.

Upstream reproduction environment — отдельный Python/server workflow: README
фиксирует Python 3.11, базы для части memory frameworks, model endpoints и
LLM-as-a-Judge. Эти требования не переносятся в Android app и не исполнялись в
этом review.

## Что переносится в Soll eval

| RUMBA contract | Soll eval mapping | Ограничение |
| --- | --- | --- |
| Extraction, Reasoning, Abstention | Три обязательные supergroup в отчёте | Не сводить всё к одному aggregate score |
| 17 semantic types | Отдельные результаты хотя бы для каждого представленного типа | Не объявлять покрытие отсутствующего типа |
| Single- и multi-session scope | Отдельно измерять поиск внутри одной и нескольких сессий | Сохранять session identity и evidence refs |
| Atemporal/temporal | Отдельно измерять использование question/session timestamp | Не подменять temporal reasoning простой recency sort |
| Explicit/implicit/no temporal expression | Диагностировать способ восстановления времени | Не выводить timestamp только из порядка ingestion |
| UpdatingInfo/DeleteInfo | Проверять актуальный факт и tombstone/delete semantics | Удалённый или устаревший факт не должен возвращаться |
| Abstention | Измерять hallucination/unsupported-answer rate | Отсутствие evidence должно приводить к отказу |

RUMBA используется как evaluation dataset и taxonomy, а не как готовая product
memory implementation. Upstream score другой системы не является прогнозом
качества Soll.

## Проверенные seam Soll app

| Seam | Наблюдаемый repository contract | Вывод для интеграции |
| --- | --- | --- |
| Capture | `rememberAcceptedSuggestion()` пишет только принятое proactive suggestion и только при включённой памяти | Нет ingestion полной chat history |
| Schema | `AssistantMemory` хранит category, key, summary, source, confidence и timestamps | Нет user/session/evidence identity, temporal validity и tombstone |
| Retrieval | `observeRecent(limit = 100)` сортирует pinned/updated records; export читает все records | Нет question-conditioned или temporal retrieval |
| Lifecycle | DAO поддерживает upsert и ручные delete-by-id/delete-all | Нет измеримого UpdatingInfo/DeleteInfo protocol из диалога |
| Chat boundary | `ChatTurnRequest` несёт session, content, metadata и flags через `POST api/v1/chat/turn` | Локальная `AssistantMemory` явно не подключена к chat turn |
| Privacy/export | Server summary исключает raw payload JSON; UI позволяет удалить memory | Eval не должен читать реальные пользовательские memory/logs |

Это не дефекты текущего UI: локальная память имеет более узкий продуктовый
контракт. Они объясняют, почему полный RUMBA run сейчас не даст честного
измерения Soll memory system.

## Будущий измеримый offline smoke

RUMBA pilot разрешён только отдельной задачей после pinning dataset/repository
revision. Он должен пройти семь ворот:

1. Зафиксировать arXiv version, repository commit, dataset revision, license и
   checksums; записать exact evaluator/model versions.
2. Использовать только опубликованный benchmark или отдельные sanitized
   synthetic fixtures; читать local user memory, chat logs, credentials и
   payload JSON запрещено.
3. Доказать `100%` chronological ingestion parity для выбранных sessions и
   `0` cross-user records после reset/isolation check.
4. Запустить минимум по `3` представительных QA на каждый доступный из `17`
   semantic types и явно перечислить отсутствующие/исключённые slices.
5. Отчёт должен включать aggregate score, token F1, abstention accuracy,
   UpdatingInfo/DeleteInfo accuracy и отдельные single/multi,
   atemporal/temporal и temporal-expression slices.
6. Детерминированный scorer должен давать одинаковый результат в `3/3`
   повторениях; любой LLM-as-a-Judge фиксируется отдельно и требует явно
   одобренного provider/runtime.
7. Promotion возможен только по заранее заданному product threshold, при `0`
   privacy leaks, `0` cross-user leaks и без регрессии критичных DeleteInfo и
   Abstention slices; aggregate improvement сам по себе недостаточен.

До такого pilot выполняется только repository contract test. Он проверяет
решение и текущие seams, но не считается RUMBA benchmark run.

## Дедупликация task record

Канонической записью для этой wiki-страницы остаётся задача
`092df8f4d66143d0a402c29aa74155cc` (`insight/e348746d9311`) со статусом
`validated`. Она единственная хранит результат анализа и решение
`conditional offline evaluation candidate`.

Задача `87c44d38824e4d4b8f3678683128a943`
(`insight/e202a3afd00a`) сопоставлена с тем же project и
`wiki/rumba-russkoyazychnyy.md`, поэтому закрыта как связанный дубликат. Новая
копия анализа или отдельное решение о внедрении не создаётся. Активной
канонической записью остаётся `1` задача.

## Наблюдаемая ценность

- Добавлено `1` wiki-решение об интеграции RUMBA.
- Проверены `3` primary upstream surface и `6` текущих Soll memory seam.
- В eval contract перенесены `4` diagnostic axis и определены `7` измеримых
  promotion gate.
- Выполнен `1/1` focused repository contract test.
- Импортировано `0` dataset rows; выполнено `0` benchmark/model runs.
- Изменено `0` production/runtime файлов, dependencies, permissions и API
  contracts.

Измеримая ценность этого slice — проверяемый offline-eval контракт и точная
граница текущей готовности, а не неподтверждённое улучшение качества памяти.

---
task_id: 092df8f4d66143d0a402c29aa74155cc
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/e348746d9311
source_item: habr-sber-company-rumba
source_trust: untrusted_external_content
source_processing_result: validated_relevant_offline_eval_blueprint_runtime_integration_deferred
verification_artifact: Soll/outputs/source-processing/task-092df8f4d66143d0a402c29aa74155cc-rumba-integration-audit.md
canonical_task_id: 092df8f4d66143d0a402c29aa74155cc
canonical_task_status: validated
linked_duplicate_task_id: 87c44d38824e4d4b8f3678683128a943
linked_duplicate_source_ref: insight/e202a3afd00a
linked_duplicate_status: closed_linked
active_task_count: 1
duplicate_resolution_artifact: Soll/outputs/source-processing/task-87c44d38824e4d4b8f3678683128a943-rumba-task-deduplication-audit.md
deduplication_value_metric: "2 task IDs matched; 1 canonical active task retained; 1 duplicate linked and closed; 1 shared status, analysis result and integration decision preserved; 2/2 focused contract tests passed; 0 runtime files changed"
value_metric: "1 wiki integration review added; 3 primary upstream surfaces and 6 current Soll memory seams audited; 4 diagnostic axes and 7 measurable promotion gates defined; 1/1 focused contract test passed; 0 dataset rows imported, 0 benchmark/model runs and 0 production/runtime changes"
verified_at: 2026-07-26 Europe/Chisinau
---
