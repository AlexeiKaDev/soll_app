---
title: "ResearchStudio-Reel: применимость к pipeline генерации контента Soll"
task_id: 58f2b64172fd4bb7b00045fa4e858190
source_ref: source-item/9011e13c06d6/605d37143f879378
source_version: arxiv:2607.04438v2
reviewed_at: 2026-07-22 Europe/Chisinau
---

# ResearchStudio-Reel: применимость к pipeline генерации контента Soll

## Решение

Из ResearchStudio-Reel в `soll_app` перенесён не desktop media stack, а его
узкий архитектурный контракт:

1. один `shared_evidence_bundle` как источник фактов для всех производных
   материалов;
2. fan-out из этого bundle в текущие Soll-форматы `source_digest` и
   `article_card`;
3. стабильное section alignment между двумя материалами;
4. hard release gate, который требует успешные receipts, evidence refs и один
   общий bundle id;
5. только proposal/review на Android, без генерации, публикации и внешних
   вызовов.

Контракт реализован в
`app/src/main/java/com/soll/domain/soll/ResearchContentPipeline.kt`. Он не имеет
side effects и не добавляет dependencies. Тяжёлые Paper2Poster, Paper2Video,
Paper2Blog DOCX и Paper2Reel явно оставлены в `deferredModules`.

## Полная статья и provenance

Проверены первичные источники:

- [Hugging Face Daily Papers](https://huggingface.co/papers/2607.04438);
- [arXiv `2607.04438v2`](https://arxiv.org/abs/2607.04438v2);
- [полный PDF v2](https://arxiv.org/pdf/2607.04438v2);
- [полный TeX source v2](https://export.arxiv.org/e-print/2607.04438v2);
- [официальный Microsoft ResearchStudio](https://github.com/microsoft/ResearchStudio/tree/298ca64ae5e3f242d58278601db34bfa6daa53b8/ResearchStudio-Reel).

PDF и TeX source скачаны в игнорируемый cache
`build/source-processing/researchstudio-reel-2607.04438/`. Все 49 archive
entries проверены на absolute/path-traversal names до распаковки.

| Объект | Размер | SHA-256 |
| --- | ---: | --- |
| `researchstudio-reel-2607.04438.pdf` | 32,898,632 bytes | `af5ccf02150a5b8a4845349fd142bfb3d91ed50fa048d2d4aaf7679ba918ac4e` |
| `researchstudio-reel-2607.04438-source.tar` | 32,191,795 bytes | `b751c92857c5350e61f40c6f1a79d3c28903cc4e46e87991e257eb91f4d73571` |

Скачана текущая на момент аудита версия v2 от 19 июля 2026 года; v1 была
опубликована 5 июля. Paper распространяется по CC BY 4.0. PDF и source bundle
не добавлены в Git: checksum receipt фиксирует прочитанную версию, а бинарные
paper assets не нужны Android build.

Task-referenced raw file
`raw/monitored\hugging-face-daily-papers\20260707-213045-researchstudio-reel-automate-the-last-mile-of-re-0a27576d.md`
в isolated worktree отсутствует. Это не подменяется утверждением о локальном
raw ingestion: аудит выполнен по canonical primary sources.

## Что реализует ResearchStudio-Reel

### 1. Paper2Assets — единственный владелец extraction

Один проход по PDF создаёт переносимый bundle:

- полный текст с page breaks;
- captions и очищенные figures;
- metadata статьи;
- nine-section paper spec;
- `sections.json` со стабильными section ids;
- `narration.json` в reading order;
- `manifest.json` с inventory и SHA-256 исходного PDF.

Downstream-модули не открывают PDF повторно. Poster, video и blog используют
те же section ids, figure handles и claim anchors. Скрипты отдельных этапов
идемпотентны, поэтому упавший этап можно повторить без полного re-extract.

### 2. Paper2Poster — measured-fill вместо soft-score plateau

Poster собирается из независимых layout/style/header/QR осей. Headless browser
измеряет отношение высоты painted content к высоте card и присваивает один из
пяти verdicts: `EMPTY`, `SPARSE`, `FULL`, `SPILLAGE`, `OVERFLOW`. Целевая зона
`FULL` — 90–98%; figure должна занимать не менее 90% card по одной оси.

За одну итерацию меняется один section, overshoot не повторяется, а on-disk
circuit breaker ограничивает число rounds. Финальный gate требует HTML, PDF,
PNG и editable PPTX, а также точный canvas. Это сильнее, чем «LLM считает
результат красивым», но измеряет геометрическую плотность, не понимание.

### 3. Paper2Video — timeline как контракт согласования

Video использует общий bundle, строит editable PPTX, narration, captions,
visual cues, captioned/no-subtitle MP4 и section-addressable timeline. Gate
сопоставляет deck, script, cues, audio и rendered timeline; это обнаруживает
referential drift вида «в narration Figure 3, а на slide Figure 2».

### 4. Paper2Blog — общий evidence map и layout repair

Два DOCX — Chinese WeChat и English research blog — не являются буквальными
переводами, но должны совпадать по числам, терминам, figure order и evidence.
Проверка включает package completeness, cross-language factual consistency и
layout-aware repair после LibreOffice render.

### 5. Paper2Reel — convergence, не четвёртая генерация

Reel повторно не пересказывает paper. Он связывает готовые poster sections,
slides, video times, captions и blog passages через `content_alignment.json`.
Static и Playwright gates проверяют package paths, media ranges, section clicks,
seeking, captions, downloads и broken assets.

## Аудит опубликованной реализации

Upstream закреплён на commit
`298ca64ae5e3f242d58278601db34bfa6daa53b8` (MIT):

- 171 tracked files, 8,643,584 bytes;
- 5 top-level paper2* skill directories и 6 `SKILL.md` с вложенным
  `html2pptx` sub-skill;
- 64 Python files;
- 3 `requirements.txt`;
- 2 test files и 41 `test_*` functions, сконцентрированных в Paper2Poster и
  `html2pptx`.

Проверены не только README, но и `paper2assets/scripts/build_package.py`,
poster fill/gate utilities, `check_video_package.py`,
`check_blog_package.py`, `check_reel_package.py` и golden reel contract.

### Реальные runtime requirements

- Python packages: `edge-tts`, `python-docx`, `PyMuPDF`, `Pillow`, `numpy`,
  `python-pptx`, `playwright`, `pdf2image`, `lxml`, `pyphen`;
- system tools: Poppler, LibreOffice, FFmpeg и Chromium;
- LLM host runtime и model credentials/configuration;
- network paths к arXiv, Wikimedia/Wikidata и Edge TTS;
- substantial desktop filesystem and subprocess orchestration.

Полный four-artifact bundle в paper breakdown занимает в среднем 89.2 минуты,
675 turns, около 2.568M distinct input tokens, 108.546M cached-context reads и
276K output tokens на paper (5-paper sample, `claude-opus-4-8`). Это не
подходящий on-device Android workload.

## Критическая оценка evidence

### Подтверждено

- Один shared extraction contract реально присутствует в paper и code.
- Stable ids, manifest, source checksum и QA receipts дают воспроизводимую
  lineage между artifacts.
- Poster benchmark содержит 100 papers и одинаковый rubric для baselines.
- Два VLM judges дают лучшую aggregate aesthetics оценку, чем авторские
  posters: 3.56 против 3.03; overall wins — 74/100 и 95/100 в двух judges.
- Hard package/layout gates реализованы отдельными scripts и non-zero exits.

### Не подтверждено

- Quantitative evaluation есть только для poster. Video и blog имеют
  capability tables, но не graded benchmark или human editing-effort study.
- VLM aesthetics и PaperQuiz — proxy metrics с конфликтующими incentives;
  measured-fill можно улучшить, не улучшив comprehension.
- Нет измерения factual precision всех generated claims, human revision time,
  section-navigation comprehension или production failure rate.
- Domain calibration ограничена ML/CV/NLP. Перенос в другие дисциплины не
  проверен.
- Только две зоны upstream имеют test files; существование gate scripts не
  равно полной regression coverage всего end-to-end pipeline.
- Логотипы, paper figures и Edge TTS имеют отдельные license/terms boundaries,
  которые нельзя считать покрытыми MIT license кода.

Следовательно, paper подтверждает полезные engineering patterns, но не даёт
основания импортировать весь runtime или автоматически публиковать материалы.

## Аудит текущей границы Soll

`soll_app` — Android review client. В репозитории есть:

- `SollSourceItem` с `rawFile`, `auditRef`, `evidenceRef`,
  `verificationArtifact` и status fields;
- Sources/Tasks/Chat/Insights surfaces;
- server-mediated `SollGateway`;
- существующий UX направления `Digest + article card`.

В репозитории нет server paper extractor, DOCX/PPTX/HTML renderer, headless
browser content worker или publication API. Поэтому добавление Python, Chromium,
LibreOffice и FFmpeg в APK нарушило бы текущую architecture boundary и не имело
бы реального execution owner.

## Интегрированный Soll pipeline contract

`ResearchContentPipelinePlanner.propose(SollSourceItem)` строит DAG:

```text
shared_evidence_bundle
   |-- digest_draft
   |-- article_card_draft
   `-- [digest + card] -> section_alignment
                         -> hard_release_gate
                         -> human review only
```

| Soll module | ResearchStudio pattern | Роль |
| --- | --- | --- |
| `SHARED_EVIDENCE_BUNDLE` | Paper2Assets | один grounded input с source refs для всех drafts |
| `DIGEST_DRAFT` | shared fan-out | текущий source digest без повторного extraction |
| `ARTICLE_CARD_DRAFT` | shared fan-out | текущая article card из того же bundle |
| `SECTION_ALIGNMENT` | Paper2Reel alignment | одинаковые sections/claims между digest и card |
| `HARD_RELEASE_GATE` | artifact QA gates | fail closed на missing/failed/ungrounded/mixed receipts |

`review(...)` принимает только receipts всех четырёх non-gate stages. Каждый
receipt обязан:

- иметь `passed=true`;
- ссылаться хотя бы на один durable evidence ref;
- использовать один и тот же непустой `bundleId`;
- иметь известный уникальный stage id.

Даже успешная проверка выставляет только `readyForHumanReview=true`.
`publicationAllowed` всегда `false`, `executableOnAndroid=false`, execution
boundary — `SERVER_PROPOSAL_ONLY`, а `requiresApproval=true`.

## Что сознательно не импортировано

| Upstream module | Решение | Причина |
| --- | --- | --- |
| Paper2Poster | deferred | нет approved poster use case, desktop renderer и human benchmark Soll |
| Paper2Video | deferred | FFmpeg/LibreOffice/TTS/network, высокий compute/cost, нет graded evidence |
| Paper2Blog DOCX | deferred | bilingual DOCX не совпадает с текущим digest/card contract |
| Paper2Reel | deferred | зависит от трёх отсутствующих artifact packages и browser hosting |
| upstream scripts/assets | not imported | нет execution owner; лишние dependencies и license surfaces |

## Promotion contract для server implementation

Отдельный server task можно открыть только для реального content use case и
named owner. Минимальные gates:

1. 100% inputs получают SHA-256, stable bundle id и source license label.
2. Digest и article card имеют evidence coverage 1.0 для чисел и factual claims.
3. Mixed-bundle, missing-evidence, failed-stage, duplicate и unknown receipts
   отклоняются в 100% negative fixtures.
4. Human factual-accuracy acceptance не ниже 95% на минимум 20 размеченных
   source items; 0 fabricated source links.
5. Generation time/cost измерены против текущего digest/card baseline, а
   duplicate extraction count равен 0.
6. Никакой publish/task/external action без approval exact revision; Android
   остаётся review/approve/reject client.

Если shared bundle не снижает duplicate work, section alignment не улучшает
factual consistency или нет server owner, production implementation остаётся
deferred. Текущая ценность задачи — проверенный Android proposal/gate contract,
а не неподтверждённое обещание полного media generation.
