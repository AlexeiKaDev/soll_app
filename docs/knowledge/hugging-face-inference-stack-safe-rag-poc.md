---
title: "Hugging Face inference stack: TEI, TGI, Inference Providers и safetensors"
task_id: beb1f28015b14e1a80af6cb6eccf06ce
project: soll_app
source_ref: source-item/2ac7c9adc8f0/92b3fb6e7bafb440
source_trust: untrusted_external_content
verified_at: 2026-07-23 Europe/Chisinau
raw_ref: raw/monitored\hugging-face-transformers-docs\20260709-235037-inference-deployment-and-training-stack-be32e866.md
raw_status: absent_in_isolated_worktree
---

# Hugging Face inference stack и безопасный embedding PoC

## Короткое сравнение

Transformers задаёт и переиспользует определения моделей, но сам по себе не
является одним универсальным production-сервером. Выбор слоя зависит от
операции и границы данных.

| Компонент | Для чего | Граница для Soll |
| --- | --- | --- |
| **TEI (Text Embeddings Inference)** | Локально или самостоятельно обслуживать embedding-модели; `/embed` и OpenAI-compatible embeddings API, token-based dynamic batching, safetensors loading, tracing и Prometheus metrics | Подходит для изолированного embedding/RAG контура. Не генерирует ответ и не должен получать право выполнять tools |
| **TGI (Text Generation Inference)** | Обслуживать LLM text generation: streaming SSE, continuous batching, tensor parallelism и generation controls | Не нужен embedding-only PoC. Upstream помечен как maintenance mode и рекомендует для новых LLM-serving задач оценивать развиваемые engines |
| **Inference Providers** | Делать managed/serverless calls к моделям разных провайдеров через единый SDK/API; доступна в том числе Feature Extraction | Это внешний data/credential/billing boundary, а не локальный runtime. Выбирать только после отдельного privacy, provider, retention, cost и token review |
| **safetensors** | Хранить tensor weights без pickle-десериализации, с быстрым zero-copy чтением | Предпочтительный формат весов, но не знак доверия модели: всё ещё нужны provenance, pinned revision/hash, license review, allowlist и запрет непроверенного remote code |

TEI и TGI — serving runtimes для разных типов вычисления; Inference Providers
— managed access plane; safetensors — формат файлов. Они дополняют друг друга,
но не являются взаимозаменяемыми вариантами одного уровня.

## Выбранный PoC: локальный TEI embedding-only retrieval

Выбран **контрактный PoC TEI на loopback с заранее одобренной локальной
embedding-моделью**. В этой задаче модель не выбирается, не скачивается и не
загружается: репозиторий не содержит подтверждённого allowlist entry для
TEI-модели. TGI и Inference Providers в PoC не используются.

До запуска оператор должен отдельно одобрить:

1. локальный model directory из allowlist с точной revision, SHA-256 manifest,
   provenance и проверенной лицензией;
2. наличие только ожидаемых config/tokenizer и `.safetensors` weights, без
   pickle weights и без `trust_remote_code`;
3. поддерживаемость архитектуры текущей TEI support matrix;
4. pinned и проверенный TEI image/binary, read-only model mount, отключённый
   egress, loopback bind и ограниченные CPU/RAM;
5. временный тестовый индекс, не смешиваемый с production vectors.

### Фиксированный smoke

- Вход: три локальные несекретные заметки и один фиксированный query.
- Действие: получить embeddings через loopback `/embed`, проверить одинаковую
  ненулевую dimension, конечные числа и повторяемость в заданном tolerance;
  затем вычислить cosine similarity и вернуть top-1 reference.
- Выход: только `document_id`, score и metadata
  `model_revision/vector_dimension/normalization/index_revision`.
- Запрещено: LLM generation, prompt-driven code, tools, shell, agent loop,
  remote calls, model download и запись в production index.
- Pass: `3` document embeddings + `1` query embedding, ожидаемый top-1 на
  синтетическом fixture, `0` outbound calls, `0` downloads, `0` tool calls и
  `0` persistent runtime changes.

Это проверяет полезную для локального RAG часть — embedding и retrieval — без
agentic execution. Если approved model или изоляция не подтверждены, PoC
останавливается до model load; переход к генерации является отдельной задачей.

## Почему safetensors недостаточно

Safetensors устраняет риск выполнения pickle-кода при чтении tensor-файла, но
не подтверждает происхождение, лицензию, качество или безопасное поведение
весов. Также он не делает безопасными tokenizer/config, custom modeling code,
container image или сетевой endpoint. Поэтому правило Soll:
`safetensors + pinned hash + allowlist + no remote code + isolation`, а не
«расширение файла означает доверенную модель».

## Источники

- <https://huggingface.co/docs/transformers/index>
- <https://huggingface.co/docs/text-embeddings-inference/index>
- <https://huggingface.co/docs/text-embeddings-inference/quick_tour>
- <https://huggingface.co/docs/text-generation-inference/index>
- <https://huggingface.co/docs/inference-providers/index>
- <https://huggingface.co/docs/inference-providers/tasks/feature-extraction>
- <https://huggingface.co/docs/safetensors/index>
