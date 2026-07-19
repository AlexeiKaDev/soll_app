---
title: SWE-Review local own-diff pilot for Soll
task_id: b015b7e24b3d4e4aad07d61d4b89ceed
source_ref: source-item/9011e13c06d6/95d3fc37e0d98731
review_status: proposal_only_human_approval_required
---

# Локальный review-revise пилот для собственных diff Soll

## Короткая заметка

Источник «SWE-Review: Closing the Loop on Issue Resolution with Agentic Code Review»
предлагает замкнуть цикл AI-разработки: отдельный reviewer исследует
патч, возвращает решение и структурированные замечания, после чего автор
исправляет diff и повторяет проверку. Для Soll полезен не автоматический merge,
а дополнительный локальный `review -> revise -> test -> human approval` перед
передачей изменения координатору.

Заметка основана только на source signal из задачи. Указанный raw-файл
`raw/monitored\hugging-face-daily-papers\20260708-220900-swe-review-closing-the-loop-on-issue-resolution--40f68ab9.md`
отсутствует и в корне worktree, и под `Soll/raw`; внешний поиск и скачивание не выполнялись.
Поэтому здесь не приписываются статье детали, которых нет в
переданном описании. Source URL сохранен только для трассировки:
<https://huggingface.co/papers/2607.06065>.

## Контракт безопасного пилота

Пилот ограничен одной задачей, worktree `soll_app` и базой
`e9931cb9c1912b5217d835a15d13dec183c11420`. Reviewer получает только diff,
созданный implementation worker этой задачи, и квитанцию сфокусированного
теста. Он не сканирует другие репозитории, host profile, историю пользователя,
переменные окружения, credential stores или внешние сервисы.

Точный own-diff manifest пилота:

1. `docs/knowledge/swe-review-local-diff-pilot.md`;
2. `app/src/test/java/com/soll/project/SweReviewLocalDiffPilotTest.kt`;
3. `Soll/outputs/source-processing/source-item-9011e13c06d6-95d3fc37e0d98731-verification.md`.

Любой четвертый путь, изменение вне текущего worktree или расхождение с base
SHA означает `reject`. Файлы с именами `.env`, `*credential*`, `*secret*`,
`keystore`, `local.properties`, `google-services.json`, SSH/cloud/signing
материалы и их содержимое запрещено читать и запрещено передавать reviewer-у,
даже если такой путь случайно появится в diff.

### Обязательные gates

| Gate | Условие успеха | Нарушение |
| --- | --- | --- |
| Scope | base SHA совпадает; changed paths точно равны manifest | `reject` |
| Ownership | reviewer читает только diff этой задачи и test receipt | `reject` |
| Repository | только текущий `soll_app` worktree; foreign repositories = 0 | `reject` |
| Secrets | secret/config/profile reads = 0 | `reject` |
| External access | network, web, MCP/connectors и внешнее сканирование = 0 | `reject` |
| Effects | commit, push, deploy, PR, branch/tag и auto-merge = 0 | `reject` |
| Tests | сфокусированный тест завершился успешно после последней правки | `reject` |
| Decision | только `accept_for_human_review` или `reject` с evidence refs | `reject` |
| Approval | финальное решение принимает человек/координатор | promotion запрещен |

Reviewer не может расширять scope, исправлять файлы, запускать production-код,
считать собственное решение merge approval или маркировать непроверенный тест
как успешный. Он возвращает: `decision`, `scope_checked`, список находок с
severity/path/evidence, обязательные правки, проверенные test receipts,
остаточные риски и `human_approval_required: true`.

## Порядок review-revise

1. Implementation worker фиксирует base SHA и exact changed-path manifest.
2. Worker запускает только объявленный focused test и сохраняет exit code.
3. Локальный reviewer проверяет manifest, diff, требования и test receipt.
4. При `reject` автор меняет только manifest-файлы и повторяет тест и review.
5. При `accept_for_human_review` автоматические commit/merge/push остаются
   запрещены; diff и verification artifact передаются человеку/координатору.

Пилот считается полезным только если дает проверяемый review receipt при нулевых
scope violations, secret reads, external calls и автоматических repository
effects. Он не доказывает качество SWE-Review как системы и не разрешает
постоянный reviewer runtime; для этого нужна отдельная задача и human approval.
