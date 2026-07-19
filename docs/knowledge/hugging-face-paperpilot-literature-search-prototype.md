# Hugging Face Daily Papers / PaperPilot: прототип поиска литературы для Soll app

## Решение

Подход PaperPilot применим к Soll как **серверный, proposal-first контракт
поисковой сессии**, но не как готовая Android-библиотека или новый источник
публикаций. Hugging Face Daily Papers уже даёт Soll исходный `source_ref` и
anchor paper; полезная часть сигнала — явный типизированный workflow, который
можно показать пользователю, проверить до исполнения и точечно изменить после
обратной связи.

В рамках задачи создан исполнимый по форме, но не по внешним эффектам JSON
прототип:
`docs/knowledge/hugging-face-paperpilot-literature-search-prototype-v1.json`.
Он описывает один синтетический proposal-only workflow из девяти операторов,
не выполняет поиск и не добавляет сетевые, модельные или storage зависимости.

Task: `51efb9a76b94469e86a3fd8b9181918a`.
Source: `source-item/9011e13c06d6/d4d88b2dcc5eb63f`.

## Проверка сигнала

Заданный raw artifact
`raw/monitored\hugging-face-daily-papers\20260707-213045-multi-turn-agentic-scientific-literature-search--b82eea3c.md`
отсутствует в изолированном worktree. Поэтому название и технические claims
сверены с первичным arXiv record `2607.00597v2` и соответствующей страницей
Hugging Face:

- <https://arxiv.org/abs/2607.00597>;
- <https://huggingface.co/papers/2607.00597>.

Статья задаёт поиск от anchor paper и пользовательского запроса как
редактируемый DAG. Полный toolset содержит 17 операторов: keyword search,
citation expansion, объединение, дедупликацию, фильтрацию, scoring, cutting,
reranking и evidence/graph output. В multi-turn цикле агент может уточнить
намерение, изменить workflow или завершить поиск.

Опубликованные Hit@5, MRR, nDCG и нулевая доля execution errors относятся к
PaperPilot-9B, контролируемому hidden-gold benchmark и преимущественно
computer-science данным. Они не являются измеренным результатом Soll. В статье
также отмечены ограничения предопределённого operator set, teacher-generated
supervision и LLM user simulator. Поэтому Soll заимствует только проверяемую
workflow abstraction; модель, training recipe и результаты не импортируются.

## Аудит текущих точек интеграции

В текущем Android worktree нет отдельного literature-search session или
workflow API. Есть пять полезных seams:

1. `SollGateway.listSources(...)` и `listSourceItemsPage(...)` отображают
   monitored sources и сохраняют truth metadata;
2. `SollSourceItem` несёт `sourceUrl`, `summary`, `evidenceRef`,
   `verificationArtifact` и может быть anchor/provenance record;
3. `SollGateway.sendChatTurn(...)` даёт существующий multi-turn UX, но его
   текстовый ответ не должен заменять типизированный workflow contract;
4. `createTaskFromSourceItem(...)` уже отделяет просмотр source item от
   подтверждаемого создания задачи;
5. `SollTaskGraph` и `TaskGraphReachabilityBuilder` моделируют и кэшируют граф
   проектов/задач. Их нельзя переиспользовать как execution DAG литературы:
   семантика узлов, lifecycle, permissions и cache ownership различаются.

Следовательно, поиск должен оставаться server-owned capability. Android
получает проекцию сессии и использует существующие Sources, Chat и Tasks, но не
выполняет citation/web search, reranking или модельные вызовы локально.

## Контракт прототипа

### Сессия

Минимальная сессия содержит:

- `session_id`, `source_ref` и неизменяемый `anchor_paper`;
- нормализованный `query`, `turn` и `state`;
- `workflow.revision`, список типизированных `nodes` и terminal node;
- `feedback_contract.allowed_actions`;
- `policy`, limits и наблюдаемые счётчики side effects.

`source_ref` сохраняется отдельно от URL, чтобы результат можно было вернуть в
карточку Hugging Face Daily Papers и связать с исходным Soll audit trail.

### Typed DAG

Каждый node содержит стабильный `id`, `operator`, `inputs`, `input_types`,
`output_type` и ограниченные `params`. Прототип использует минимальный
поднабор toolset:

| Operator | Input | Output | Назначение |
| --- | --- | --- | --- |
| `keyword_search` | none | `paper_set` | первичный candidate set |
| `citation_expand` | `paper_set` | `paper_set` | successors anchor/candidates |
| `union` | несколько `paper_set` | `paper_set` | объединение веток |
| `dedupe` | `paper_set` | `paper_set` | DOI/arXiv/title dedupe |
| `filter` | `paper_set` | `paper_set` | год, язык, тип evidence |
| `score` | `paper_set` | `scored_paper_set` | прозрачные признаки relevance |
| `top_k` | `scored_paper_set` | `paper_set` | ограничение candidate pool |
| `rerank` | `paper_set` | `paper_set` | query-aware ordering |
| `extract_evidence` | `paper_set` | `evidence_set` | grounded final cards |

До исполнения server validator обязан проверить уникальность node ids,
существование input references, type compatibility, отсутствие cycle,
разрешённый operator allowlist, terminal `evidence_set` и limits. Невалидный
workflow возвращается как `invalid_proposal` без частичного исполнения.

### Multi-turn lifecycle

1. `propose` — по anchor/source item и запросу создаётся workflow revision без
   внешнего поиска.
2. `clarify` — если intent неполон, Android показывает один явный вопрос через
   Chat и сохраняет ответ как feedback, а не как скрытый prompt append.
3. `validate` — server проверяет DAG и показывает пользователю operator diff,
   limits и ожидаемые providers.
4. `approve` — отдельное подтверждение разрешает только одну revision и один
   bounded external-search run.
5. `execute` — server выполняет allowlisted read-only adapters, пишет per-node
   status, latency, errors и provenance; Android только наблюдает.
6. `refine` — feedback `add_node`, `modify_node`, `remove_node` или
   `update_query` создаёт новую revision; повторный внешний run требует нового
   approval.
7. `finalize` — terminal evidence cards содержат paper id, canonical URL,
   matched intent, supporting snippets и operator path.

## Предлагаемая server/Android граница

Новые endpoints являются дизайном, а не изменением публичного контракта в этой
задаче:

- `POST /api/v1/literature/search/sessions` — создаёт proposal-only session;
- `GET /api/v1/literature/search/sessions/{id}` — возвращает revision, node
  status, clarification и evidence cards;
- `POST /api/v1/literature/search/sessions/{id}/feedback` — предлагает новую
  revision с явным diff;
- `POST /api/v1/literature/search/sessions/{id}/execute` — требует approval id
  для exact revision и запускает bounded server execution.

Generic `sendChatTurn(...)` можно использовать как UI entry point и для
clarification text, но session state, workflow nodes и evidence должны идти
через отдельный typed response. Это сохраняет текущий chat contract и делает
ошибки/частичные результаты наблюдаемыми.

## Safety и approval boundary

- Android не хранит provider credentials и не вызывает paper providers
  напрямую;
- proposal/validation не имеют network access и persistent writes;
- external execution требует explicit approval для exact revision;
- adapters, operator names и URL schemes работают по allowlist;
- запрещены shell, arbitrary code, arbitrary URL fetch и workflow-generated
  tool names;
- query получает privacy label: private text блокирует внешний run до явного
  разрешения или редактирования;
- pilot limits: не более 50 candidates, 10 returned papers, 9 workflow nodes,
  2 citation hops, 1 rerank и 60 секунд на approved run;
- retries идемпотентны по `session_id + revision + approval_id`;
- каждый claim в final card имеет paper id, canonical URL и producing node;
- `createTaskFromSourceItem(...)` остаётся отдельным подтверждаемым действием.

## Focused pilot и promotion gates

Первый runtime pilot допустим только после отдельного server task и approval.
Он сравнивает текущий single-query baseline с typed workflow на одинаковом
наборе минимум из 20 размеченных Soll literature intents, покрывающем
predecessor, successor, sibling, benchmark и survey directions.

Promotion требует все шесть gates:

1. schema parse rate и DAG validation rate `100%`;
2. циклы, missing refs, type mismatch и неизвестные operators отклоняются в
   `100%` негативных fixtures до execution;
3. execution error rate не выше `2%` и ни одного unapproved provider call;
4. citation precision и provenance recall равны `1.0` для финальных cards;
5. Hit@5 не хуже baseline и nDCG@10 выше минимум на `5%` на тех же intents;
6. median clarification satisfaction не ниже `4/5`, p95 latency не выше
   `60 s`, candidate/cost limits соблюдены в `100%` runs.

Reject/defer условия: нет named server owner или approved provider; workflow не
даёт измеримого качества поверх baseline; feedback не улучшает ranking;
provenance неполон; стоимость/latency превышает limits; либо реальный
literature-search use case отсутствует. В этих случаях остаются текущие
Sources/Chat/Tasks без production изменений.

## Измеренный результат этой задачи

- proposal-only designs: `1`;
- syntactically valid synthetic workflows: `1`;
- typed operators represented: `9`;
- current Soll seams audited: `5`;
- promotion gates defined: `6`;
- external literature searches/provider calls/model runs: `0`;
- Android production/API/UI/dependency changes: `0`.

Это измеримая design value: будущий server spike получает готовую границу,
fixture, validation invariants и stop conditions. Retrieval value остаётся
неизмеренным до approved runtime pilot.
