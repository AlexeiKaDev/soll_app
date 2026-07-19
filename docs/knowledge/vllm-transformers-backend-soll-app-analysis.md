# vLLM Transformers backend: требования и применимость для Soll app

Дата анализа: 2026-07-19 (Europe/Chisinau).

Task: `aa8dfba5f72342bcb30624ed9b529173`,
`source_ref=insight/2b0ac2f1734b`.

## Краткий вывод

Новый Transformers modeling backend в vLLM **применим как отдельный
server-side A/B candidate**, но статья не доказывает ускорение текущего
локального inference Soll. Для уже поддерживаемой Qwen-модели native vLLM
остаётся базовой линией; полезность Transformers backend — прежде всего единая
реализация модели, более быстрый доступ к новым архитектурам и меньшая цена
отдельного vLLM-порта.

Нельзя переносить опубликованное равенство скорости напрямую на
`qwen3-coder:30b`: это локальный alias без доступного в этом worktree точного
model id, revision, architecture, quantization, launch plan, версии vLLM и
характеристик GPU. Статья измеряла Qwen3 4B dense на одном GPU, Qwen3 32B dense
с TP=2 и Qwen3 235B FP8 MoE с DP=8+EP на узле 8xH100. H100 не является общим
требованием запуска backend, но является границей опубликованного performance
evidence.

Решение по текущей задаче: **production/runtime не менять**. Сохранить Android
как клиент существующего серверного контракта и вынести проверку в отдельный,
approval-gated benchmark точной текущей модели. Продвигать Transformers backend
можно только после измеренного паритета и появления конкретной maintenance или
model-availability выгоды.

## Исправление source identity

В задаче смешаны два разных сигнала:

- путь `monitored/hugging-face-blog/20260711-001519-native-speed-vllm-transformers-modeling-backend-5ae7e5e5.md`, objective и safe next action указывают на
  **Native-speed vLLM transformers modeling backend**, опубликованный
  2026-07-08;
- evidence title **Fine-tune video and image models at scale with NVIDIA NeMo
  Automodel and Diffusers** относится к отдельному посту от 2026-07-17 про
  обучение diffusion-моделей и не задаёт требования vLLM inference.

Monitored-файл не vendored в этом изолированном worktree. Полный пост открыт по
каноническому URL, совпадающему с source filename и целью задачи. Анализ ниже не
приписывает NeMo/Diffusers статье требования vLLM.

## Что именно изменилось

Оптимизация вошла в vLLM `0.25.0` через PR `#47187`; актуальный patch release на
дату анализа — `0.25.1`. Поэтому команда из поста `uv pip install --upgrade
vllm --torch-backend auto` недостаточно воспроизводима: пилот должен явно
зафиксировать `vllm==0.25.1`, resolved Transformers/PyTorch/CUDA stack и
rollback image/lock.

Backend загружает Transformers model implementation, затем:

1. через `torch.fx` находит известные graph patterns;
2. через Python AST переписывает instance для inference-specific fusions;
3. заменяет совместимые проекции и блоки на оптимизированные vLLM kernels,
   включая `QKVParallelLinear`, `MergedColumnParallelLinear`, RMSNorm и
   `FusedMoE`/`MoERunner` paths;
4. оставляет результат совместимым с `torch.compile` и CUDA Graphs;
5. выводит `Fused:` records в startup log, что даёт проверяемое доказательство
   фактически применённых оптимизаций.

Это не запуск обычного `transformers.pipeline`. vLLM по-прежнему владеет
serving engine, continuous batching, KV cache, attention kernels,
parallelization и OpenAI-compatible API; Transformers предоставляет model
definition.

## Требования

### 1. Package и execution environment

| Требование | Практический gate для Soll |
| --- | --- |
| vLLM с PR `#47187` | использовать отдельное окружение с `vllm==0.25.1`; `0.24.0` выпущен до merge и не подходит для этой проверки |
| Python | package metadata `0.25.1`: `>=3.10,<3.15`; для пилота зафиксировать одну поддерживаемую minor version |
| Transformers | использовать совместимую Transformers v5 dependency, разрешённую и зафиксированную вместе с vLLM; не обновлять Transformers независимо поверх работающего runtime |
| PyTorch/accelerator | ставить согласованный vLLM wheel через `--torch-backend auto` или явно выбранный поддерживаемый backend; сохранить версии torch, driver, CUDA/ROCm и GPU identity |
| Isolation | отдельный WSL/Linux venv/container и отдельный test port; не обновлять живой `127.0.0.1:17200` in-place |
| Reproducibility | lock/freeze, model revision, tokenizer revision, command line, environment and rollback reference обязательны до A/B |

### 2. Model implementation

Для стандартной модели предпочтительна реализация, уже включённая в
Transformers. Для custom implementation нужны как минимум:

- корректный `config.json` и `auto_map.AutoModel`;
- изменения в base model, а не только в `*ForCausalLM` wrapper;
- передача `**kwargs` от base model до attention layer;
- вызов attention через `ALL_ATTENTION_FUNCTIONS` и
  `_supports_attention_backend = True`;
- для MoE — доступный `experts` block с поддерживаемой структурой и контрактом
  `hidden_states`, `top_k_index`, `top_k_weights`;
- full и/или sliding attention. Пост отдельно предупреждает, что linear
  attention на момент публикации не поддержан;
- для нестандартных TP/PP layouts — явные `base_model_tp_plan` и
  `base_model_pp_plan`. Стандартные Q/K/V/O и gated-MLP/expert patterns могут
  быть выведены fusion pass; PP может быть выведен только при однозначном
  единственном decoder `nn.ModuleList`.

`--trust-remote-code` не является безопасным shortcut для Soll. Его можно
рассматривать только после review и pin конкретного commit/revision в
изолированном окружении; по умолчанию gate требует upstream Transformers code
без удалённого исполняемого model code.

### 3. Runtime proof

Сам флаг запуска:

```text
vllm serve <EXACT_CURRENT_MODEL_OR_LOCAL_PATH> --model-impl transformers <SAME_EXISTING_FLAGS>
```

не доказывает ни совместимость, ни скорость. Успешный canary обязан приложить:

- `vllm`, `transformers`, `torch`, accelerator/driver versions и exact model +
  tokenizer revisions;
- startup log, где выбран `Transformers...` implementation и нет fallback,
  compile или weight-load error;
- список фактически применённых `Fused:` operations; отсутствие ожидаемой
  fusion — отдельный результат, а не скрытый успех;
- успешные `/health`, `/v1/models` и один deterministic completion через тот же
  OpenAI-compatible contract;
- одинаковые model, dtype/quantization, context limit, batching, TP/PP/DP/EP,
  prompt set и generation parameters для native и Transformers runs.

## Проверенные seams текущего worktree

Ниже зафиксированы пять проверенных seams текущего worktree.

1. `soll_status.md` хранит последнее доступное доказательство от 2026-07-13:
   `qwen3-coder:30b` на `127.0.0.1:17200` и `bge-base-en` на `17201`. Это
   историческая запись, не live proof на 2026-07-19.
2. Та же запись сообщает blocked launch-plan doctor: endpoints отвечали, но
   проверка WSL/Docker launch backend была нездорова. Поэтому in-place upgrade
   сейчас особенно неуместен.
3. В isolated Android worktree нет vLLM requirements/lock, launch command,
   `RuntimeServiceManager` или server runtime implementation. Нельзя определить
   установленную версию и точный checkpoint без выхода за scope.
4. Android chat обращается к серверу через `SollGateway.sendChatTurn(...)` и
   `SollGateway.askModelChat(...)`; vLLM/Transformers dependencies в Android
   Gradle graph отсутствуют. Этот контракт не должен меняться от выбора
   server-side model implementation.
5. `tools/export_utrobin_onnx.py` импортирует Transformers только как offline
   VITS/ONNX export tool. Он не связан с LLM serving и не является местом
   внедрения нового backend.

## Approval-gated A/B план

1. Получить из владельца runtime точный launch plan без секретов: model path/id,
   revision, tokenizer, quantization/dtype, context, parallel flags, vLLM,
   Transformers, torch, WSL distro, driver/CUDA и GPU/VRAM.
2. Снять native baseline в текущем неизменённом runtime. Сохранить health,
   output contract, errors, request throughput, output token throughput, TTFT
   p50/p95, TPOT p50/p95, peak GPU memory и power/thermal state, если доступны.
3. Создать отдельное `vllm==0.25.1` окружение и test port. Не скачивать новую
   модель и не включать remote code без отдельного approval.
4. Запустить exact checkpoint с `--model-impl transformers` и всеми остальными
   flags без изменений. Stop-on-failure: model incompatibility, missing expected
   fusion, output/usage schema drift, OOM, compile error или non-finite output.
5. Выполнить warm-up и минимум три измеряемых прогона каждого backend на одном
   hardware state: контролируемый 1024-in/128-out throughput workload плюс
   representative Soll prompts, включая русский текст, code task и
   structured/tool-shaped response.
6. Сохранить native как rollback/default. Android и server API route не менять
   во время эксперимента.

### Promotion gates

- correctness: одинаковый deterministic fixture contract, отсутствие новых
  ошибок/NaN и успешные Soll structured-output/tool-shape checks;
- performance parity: Transformers throughput не ниже `95%` native, TTFT/TPOT
  p95 не хуже native более чем на `5%`;
- resource parity: peak GPU memory не хуже native более чем на `5%`, нет OOM и
  ухудшения устойчивости в 30-minute soak;
- product value: кроме паритета должна существовать конкретная выгода — нужная
  Soll модель доступна только/раньше через Transformers backend либо устраняется
  подтверждённая стоимость отдельного model port;
- rollback: переключение назад на зафиксированный native runtime проверено до
  promotion.

Если backend достигает лишь паритета на уже поддерживаемом native Qwen и не даёт
конкретной maintenance/model-availability выгоды, переключение не создаёт
измеримой продуктовой ценности и native остаётся default.

Выполнено `0` Soll inference benchmark runs; измеренное runtime-значение этого
backend для текущей модели остаётся `0` до отдельного approval-gated A/B.

## Итоговая оценка

| Вопрос | Ответ |
| --- | --- |
| Можно ли технически проверить? | Да, на server/WSL contour, в отдельном окружении и test port |
| Нужны ли изменения Android? | Нет |
| Достаточно ли `pip upgrade` и одного флага? | Нет; нужны pin, exact model compatibility и A/B evidence |
| Доказано ли ускорение `qwen3-coder:30b`? | Нет, выполнено `0` Soll inference benchmark runs |
| Safe next action | approval-gated native vs Transformers A/B после восстановления/фиксации launch-plan evidence |

## Primary sources

- <https://huggingface.co/blog/native-speed-vllm-transformers-backend>
- <https://github.com/vllm-project/vllm/pull/47187>
- <https://github.com/vllm-project/vllm/releases/tag/v0.25.0>
- <https://pypi.org/project/vllm/0.25.1/>
- <https://docs.vllm.ai/en/latest/models/supported_models/>
- <https://huggingface.co/docs/transformers/main/transformers_as_backend>
