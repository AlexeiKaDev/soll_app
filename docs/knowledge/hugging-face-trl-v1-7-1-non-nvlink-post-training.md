---
title: "TRL v1.7.1: post-training on non-NVLink GPU"
task_id: 14ce4b268f384af6ab4d5b19ddb40a46
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/6931f077418d
source_trust: untrusted_external_content
section: LLM/post-training
release: v1.7.1
review_status: knowledge_note_added_runtime_change_deferred
reviewed_at: 2026-07-23 Europe/Chisinau
---

# TRL v1.7.1: post-training on non-NVLink GPU

## Краткая заметка

Сигнал о TRL v1.7.1 стоит сохранить для будущих server/desktop
post-training экспериментов Soll: релиз заявлен как bugfix-точка для связки
**GRPO + vLLM + PEFT** на multi-GPU системах без NVLink. Это актуально для
PCIe-связанных GPU, где нельзя переносить выводы с NVLink-топологии без
отдельной проверки коммуникаций, памяти и синхронизации весов/адаптеров.

Заметка не добавляет TRL, vLLM, PEFT, модель или training runtime в Android и
не доказывает прирост качества или скорости. Она фиксирует версию-кандидат и
минимальный контракт проверки перед отдельным, явно одобренным экспериментом.

## Источник и граница доверия

- monitored source:
  `monitored/hugging-face-trl-releases/20260709-233804-v1-7-1-7dfd65ac.md`;
- task source reference: `insight/6931f077418d`;
- release signal: `v1.7.1`;
- раздел KB: `LLM/post-training`.

Файл monitored source отсутствует в изолированном worktree. Поэтому здесь
сохранён только переданный задачей сигнал о трёх областях совместимости:
`GRPO`, `vLLM` и `PEFT` на non-NVLink GPU. Конкретные upstream PR, причины
ошибок и численные улучшения не приписываются релизу без первичного источника
и воспроизводимого локального запуска.

## Применимость к Soll

Использовать v1.7.1 следует только как pinned-кандидат для будущего
backend/desktop smoke, если у Soll появится одобренный post-training workload
на multi-GPU стенде без NVLink. Android остаётся клиентом существующего
backend-mediated model route и не должен содержать trainer, dataset, GPU
runtime, веса или PEFT adapters.

До принятия релиза нужно пройти пять ворот:

1. **Pinned environment.** Зафиксировать TRL `v1.7.1`, Transformers,
   Accelerate, vLLM, PEFT, модель, tokenizer, CUDA/driver и hardware topology.
2. **Topology proof.** Сохранить доказательство, что тестовый стенд именно
   non-NVLink, а также baseline на той же PCIe-топологии.
3. **Three-area smoke.** Отдельно проверить минимальный GRPO step, vLLM
   rollout path и загрузку/сохранение/повторную загрузку PEFT adapter.
4. **Measured comparison.** Сравнить успешность шагов, ошибки/OOM, peak VRAM,
   tokens/s и resume parity с текущей pinned-версией при одинаковых seed,
   данных и budget.
5. **Promotion and rollback.** Отклонить обновление без измеримой пользы,
   сохранить предыдущий environment lock и проверить возврат на него.

## Наблюдаемая ценность

Добавлена **1** краткая заметка в `LLM/post-training`, зафиксированы **3**
области совместимости и **5** проверяемых ворот эксперимента.
Выполнено **0** training/inference runs; измеренный выигрыш Soll остаётся **0** до отдельного
эксперимента. Production/runtime files и Android dependencies не менялись.
