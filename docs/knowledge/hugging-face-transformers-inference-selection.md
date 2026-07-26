---
title: "Transformers Pipeline/generate или inference server: короткий выбор для Soll"
task_id: d9b29c020651421dae4ea982ec865a87
project: soll_app
source_ref: source-item/2ac7c9adc8f0/ec1d24e8b04b4de0
source_trust: untrusted_external_content
verified_at: 2026-07-23 Europe/Chisinau
raw_ref: raw/monitored\hugging-face-transformers-docs\20260709-235037-transformers-library-overview-84124871.md
raw_status: absent_in_isolated_worktree
---

# Transformers Pipeline/generate или inference server

## Короткий ответ

`Pipeline` и `model.generate()` — хороший **in-process** слой для прототипа,
offline job, проверки совместимости и эталонного вывода одной модели. Они не
заменяют production serving engine с очередью конкурентных запросов,
continuous batching, управлением KV cache, параллелизмом, метриками и стабильным
сетевым API.

| Выбор | Когда использовать | Когда не выбирать |
| --- | --- | --- |
| Transformers `Pipeline` | Нужен самый короткий путь `preprocess -> model -> postprocess`; задача уже представлена task-specific pipeline; локальный прототип, notebook, последовательная или измеренная batch/offline обработка | Нужен тонкий контроль generation loop или конкурентный сетевой endpoint |
| Transformers `generate()` | Нужен прямой контроль токенизации, `GenerationConfig`, decoding/stopping, logits/cache и streamer; compatibility/correctness baseline; один доверенный процесс | Нужны multi-tenant scheduling, continuous batching, autoscaling или готовый OpenAI-compatible API |
| vLLM | Нужен общий high-throughput LLM/VLM endpoint, continuous batching, эффективный KV cache, multi-GPU и OpenAI-compatible contract; это базовый кандидат для нового Soll serving benchmark | Точная архитектура, modality, quantization или требуемая API-функция не подтверждена в текущей support matrix |
| SGLang | Нужны production chat/agent workloads, prefix reuse/RadixAttention, structured outputs либо его конкретная model/hardware комбинация выигрывает A/B у vLLM | Наличие только Transformers fallback ошибочно принимается за native feature/performance parity |
| TGI | Уже есть работающий TGI deployment, совместимая модель и измеренная причина не мигрировать | Новый default: TGI находится в maintenance mode; Hugging Face рекомендует vLLM/SGLang как развиваемые альтернативы |

Для Soll Android остаётся клиентом существующего server API. Эта заметка не
добавляет Python/Transformers или serving dependencies в приложение. Новый
engine выбирается только на server contour по точному checkpoint и одинаковому
workload; API boundary Android менять из-за engine choice не нужно.

## Быстрое правило выбора

1. Для classification/ASR/vision и другой готовой task abstraction начать с
   `Pipeline`.
2. Для генеративной модели, когда важны exact inputs, chat template, decoding,
   stopping или локальный streaming, использовать `generate()`.
3. Если модель должна обслуживать конкурентные запросы через сеть, сравнивать
   vLLM и SGLang. TGI оставлять для существующего проверенного deployment.
4. Не выбирать engine по названию или наличию fallback. Сначала пройти
   compatibility checklist, затем измерить correctness, TTFT/TPOT, throughput,
   peak memory, ошибки и rollback на representative Soll prompts.

`Pipeline` умеет batch inference, но официальная документация отдельно
предупреждает, что ускорение не гарантировано и зависит от модели, данных,
sequence length и hardware. Для latency-sensitive запроса, CPU и сильно
неравномерных длин batching не следует включать без измерения и OOM handling.

## Отдельная проверка `generate()` streaming

Проверено по Transformers `main` 2026-07-23; страница в этот день указывала
`v5.14.0` как latest stable. В актуальном контракте `generate(...,
streamer=...)` передаёт generated token ids объекту с методами `put()` и
`end()`.

- `TextStreamer` декодирует и печатает текст в stdout, когда сформированы целые
  слова. Это CLI/debug UX, не ответ приложения.
- `TextIteratorStreamer` кладёт готовый к печати текст в очередь. Для
  неблокирующего потребителя docs запускают `model.generate` в отдельном
  `Thread`; `timeout` нужен, чтобы не ждать очередь бесконечно при ошибке
  generation thread.
- `AsyncTextIteratorStreamer` предоставляет async iterator, поднимает
  `TimeoutError` по timeout и должен создаваться внутри coroutine; сама
  генерация в официальном примере всё равно идёт в отдельном `Thread`.
- Свой streamer допустим при реализации `put()` и `end()`.

Эти классы выдают декодированные текстовые фрагменты из одного Python-процесса.
Cancellation, backpressure, disconnect, error envelope и SSE/HTTP framing
должны задаваться слоем приложения. Streaming vLLM/SGLang/TGI — отдельный
server API contract; наличие Transformers streamer не превращает
`generate()` в production server.

## Отдельная проверка совместимости моделей

### Direct Transformers

1. Проверить task и соответствующий `AutoModelFor...`, а не только наличие
   весов. AutoClass выбирает реализацию по configuration class/`model_type`.
2. Для `generate()` нужна generative model class с подходящей language-model
   head; checkpoint, config, tokenizer/processor и revision должны быть
   согласованы.
3. Для chat использовать chat template именно модели. Неверные control tokens
   ухудшают результат; безопаснее `apply_chat_template(..., tokenize=True)`.
4. Если нужен `trust_remote_code=True`, это выполнение кода из model repo.
   Разрешать только после review и pin commit hash через `revision`; по
   умолчанию для Soll — отказ.

### Serving engines

- **vLLM:** проверить architecture и нужные колонки/ограничения в текущем
  Supported Models. Transformers modeling backend расширяет охват. Current docs
  заявляют одинаковую performance с dedicated vLLM implementation и
  совместимость с listed feature matrix только при выполнении всех backend
  requirements. Успешная загрузка сама по себе не доказывает выполнение этих
  условий или одинаковый вывод точной revision. При сравнении явно передавать
  одинаковые sampling parameters: Transformers `generate()` применяет
  `generation_config.json`, а vLLM использует переданные параметры.
- **SGLang:** проверить native support либо требования Transformers fallback.
  Официальный fallback ориентирован на большинство decoder-style LMs;
  compatible custom model должен корректно передавать attention kwargs и
  объявлять `_supports_attention_backend = True`. Transformers tag/успешный
  load — только кандидат на smoke, не доказательство production качества.
- **TGI:** сверить optimized supported list. Для non-core Transformers model
  возможен fallback, но docs предупреждают о потере отдельных оптимизаций,
  включая tensor-parallel sharding и Flash Attention. Для нового Soll serving
  не начинать с maintenance-only TGI без внешнего ограничения.

Минимальный smoke для любого пути: pin engine/Transformers/model/tokenizer
revisions; загрузка без remote code или с reviewed pin; корректный chat
template; один deterministic prompt; один streaming prompt; context boundary;
OOM/error path; одинаковые generation parameters; затем concurrency benchmark
для server engine.

## Источники

- <https://huggingface.co/docs/transformers/main/en/pipeline_tutorial>
- <https://huggingface.co/docs/transformers/main/en/generation_features>
- <https://huggingface.co/docs/transformers/main/en/internal/generation_utils>
- <https://huggingface.co/docs/transformers/main/en/model_doc/auto>
- <https://huggingface.co/docs/transformers/en/models>
- <https://huggingface.co/docs/transformers/chat_templating>
- <https://huggingface.co/docs/transformers/main/transformers_as_backend>
- <https://docs.vllm.ai/en/stable/models/supported_models/>
- <https://docs.vllm.ai/en/stable/serving/openai_compatible_server/>
- <https://docs.sglang.io/supported_models/transformers_fallback.html>
- <https://huggingface.co/docs/text-generation-inference/main/index>
- <https://huggingface.co/docs/text-generation-inference/en/basic_tutorials/non_core_models>
