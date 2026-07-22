# SeKV: full-text, implementation audit and Soll prototype target

Дата проверки: 2026-07-22 (Europe/Chisinau).

Task: `3726a86c0edd415d8f36d3112b7d5d4f`, project: `soll_app`,
`source_ref=source-item/9011e13c06d6/14bcf766ab5a4439`.

## Решение

Полный текст SeKV получен и разобран, а публичная реализация проверена на
зафиксированном commit. Идея подходит только как **отдельный server-side
research prototype** для длинноконтекстного GPU inference. Она не является
изменением Android-клиента, drop-in модулем для текущего `llama.cpp` CPU smoke
и пока не готова к подключению к рабочему Soll runtime.

Причина осторожного решения не только в цене обучения. Публичный код отражает
основные элементы статьи, но его default evaluation не воспроизводит paper
protocol, upstream не содержит тестов, а текущий prefetch/cache path не
доказывает заявленную ограниченную GPU residency. Поэтому результат задачи —
проверенный алгоритмический разбор и измеримый prototype contract, а не импорт
upstream package или обещание ускорения.

## Full-text receipt

Исходный monitored-файл
`raw/monitored/hugging-face-daily-papers/20260707-213045-sekv-resolution-adaptive-kv-cache-with-hierarchi-a9c95ac0.md`
отсутствует в изолированном worktree. Он не использован как доказательство.
Проверены первичные источники:

- Hugging Face paper page: <https://huggingface.co/papers/2606.31145>;
- arXiv record and full HTML: <https://arxiv.org/abs/2606.31145> и
  <https://arxiv.org/html/2606.31145>;
- arXiv `2606.31145v1`, submitted 2026-06-30, license **CC BY 4.0**;
- полный PDF: `18` page objects, `1,041,615` bytes, SHA-256
  `931835c45ac1ac579732ea0d11b14e01845d3d545e8b71fe6790f801c9ba0302`;
- полный TeX/e-print archive: `637,994` bytes, SHA-256
  `aff648c2ae94828e07e979040adfa8a5a48c0b26b26341ea8ea37a086d52088f`;
  проверены основной файл и sections для method, experiments, training,
  benchmarks, ablations, complexity, limitations и zoom-in analysis;
- официальный код: <https://github.com/AmirAbaskohi/SeKV>, commit
  `6569d111d3ace5c7c1ad596bf36962a99cd7e94b`, MIT, `32` blobs и `0` upstream
  test files на проверенной ревизии.

Архивы, модельные веса, датасеты и upstream package в Soll не добавлялись.

## Алгоритм статьи

### 1. Entropy-guided segmentation

На prefill для реализованного токена вычисляется surprisal
`H_t = -log p(x_t | x_<t)`. Граница span возникает при
`H_t > mean(H) + alpha * std(H)`; paper default `alpha=1.0`. Граничный токен
остаётся full-resolution anchor на GPU, а промежутки образуют semantic spans.
Span короче paper `L_min=16` не раскладывается через SVD и остаётся на GPU.

Плюс подхода: boundary signal уже доступен из logits prefill и не требует
отдельного сегментатора. Риск: качество границ зависит от калибровки backbone;
таблицы, код и фрагментированный текст отдельно отмечены авторами как сложные
режимы.

### 2. Dual-resolution memory

Для каждого layer/head span получает:

- нормированные surprisal weights и GPU-resident weighted mean key/value;
- per-layer/per-query-head projection в routing space `d'=32`;
- отдельные truncated SVD для key и value на CPU;
- soft rank gates от общего `g_phi`, с paper cap `R_max=32`.

Anchor KV, routing summaries, coarse means и local window остаются на GPU.
CPU хранит `U`, singular values и right singular vectors. Это не lossless cache:
статья прямо говорит об approximate low-rank reconstruction. Кроме того,
left factor всё ещё линейно зависит от длины span, поэтому экономия CPU и H2D
не является константной по контексту.

### 3. Query-adaptive zoom-in

На decode query проецируется в то же routing space. Sigmoid gate использует
similarity и `log(|S|)` size prior. Отдельный trainable threshold задан для
каждого layer/query head. Non-expanded span участвует одной coarse KV entry с
`log(|S|)` pre-softmax correction; expanded span восстанавливается до token
resolution. Anchor, coarse и reconstructed entries участвуют в одном softmax.

При превышении бюджета spans сортируются по routing score. Paper определяет
budget как peak GPU-resident KV во время шага; основные quality таблицы
сопоставляют методы при `10%` от FullKV.

### 4. Обучение

Backbone и SVD constants заморожены. Обучаются только projections, thresholds
и rank predictor: около `4.3M` параметров, примерно `0.05%` Llama-3-8B.
Objective объединяет full-KV distillation, teacher-attention zoom BCE,
SVD reconstruction и expansion/rank budget loss; binary zoom использует STE.

Paper recipe: RedPajama arXiv/books/code, около `0.5B` tokens, примерно `3K`
steps, bf16, AdamW `lr=1e-3`, weight decay `0.01`, `10%` warmup, curriculum
`8K -> 32K`, effective batch `8`, `rho=0.9`, positive weight `10`, local window
`512`, permissive `tau=0.05`. Один backbone обучался `2-6` часов на узле
`8xA100 80GB`; routing parameters отдельны для каждого backbone.

## Что именно заявлено экспериментом

Это author-reported результаты, а не измерения Soll:

- LongBench, RULER, InfiniteBench и NIAH, плюс 50-shot GSM8K;
- пять backbones: Llama-3.2-3B, Llama-3-8B, Llama-3.1-8B, Mistral-7B v0.3 и
  Qwen2.5-14B;
- SeKV выиграл `20/20` compressed benchmark/model cells при matched `10%` KV
  budget и в среднем дал `+5.9%` к SentenceKV;
- при 128K авторы сообщают `53.3%` меньше GPU memory, чем FullKV;
- на Qwen2.5-14B reported memory меняется `31.2GB -> 34.9GB` от 8K до 128K,
  FullKV — `36.0GB -> 74.8GB`;
- на 8K input / 4K output reported latency: SeKV `166.95s`, FullKV `183.42s`,
  StreamingLLM `150.86s`; это batch-size-1 A100 evidence, а не общий serving
  throughput result;
- самые большие ablation-потери на Qwen2.5-14B/NIAH: без SVD reconstruction
  `91.17 -> 83.47`, без trained zoom-in `91.17 -> 85.96`.

Статья не изолирует prefill randomized-SVD cost в целевом Soll workload, не
показывает multi-request continuous batching и предупреждает о зависимости от
CPU-GPU bandwidth и adversarial queries, активирующих много distant spans.

## Pinned implementation audit

Проверены `config`, `backbone`, `segmentation`, `modules`, `memory`, `attention`,
`teacher`, `losses`, `train`, `generate` и `eval` на commit
`6569d111d3ace5c7c1ad596bf36962a99cd7e94b`. Код действительно содержит
surprisal segmentation, per-head routing, rank gates, separate K/V low-rank
factors, mixed-resolution attention, STE training и CUDA stream prefetch.

До runtime prototype нужно закрыть следующие расхождения.

1. **Segmentation default:** paper и appendix используют `L_min=16`, repository
   `default.yaml` — `l_min: 4`.
2. **Model identity:** paper указывает Mistral-7B-Instruct-v0.3, registry —
   v0.2. Более того, `FrozenBackbone` отклоняет выбранный v0.2, если его
   `sliding_window` включён, поэтому README supported-model row не является
   готовым запуском.
3. **Context protocol:** defaults RULER/NIAH заканчиваются на `32768`, paper
   оценивает до `128K`. Встроенный RULER — один самодельный key/value retrieval
   fixture, а не полный официальный RULER suite.
4. **GSM8K protocol:** default — zero-shot; опционально доступны только четыре
   hard-coded examples. Paper result получен в `50-shot` setting.
5. **Budget protocol:** default budgets — absolute token caps
   `none/512/1024/2048`, а paper tables используют matched `5/10/15/20%` FullKV.
   Без normalizer нельзя сравнивать scores напрямую.
6. **Parallelism and performance:** путь, названный `tensor_parallel`, фактически
   выбирает `device_map=auto`; evaluator не измеряет latency/throughput/TTFT/TPOT.
   Он не воспроизводит paper TP или Table 3.
7. **GPU residency risk:** выбранные CPU factors присваиваются обратно в
   `SpanEntry.factors` как GPU tensors. LRU удаляет только собственную cache
   reference, а `SpanEntry` продолжает владеть tensors; budget accounting считает
   reconstructed KV rows, но не resident SVD factors. Это требует исправления и
   измерения, иначе bounded memory claim не доказан.
8. **Reproducibility:** `pyproject.toml` задаёт диапазоны зависимостей, model/data
   revisions не pin-нуты, lockfile и upstream tests отсутствуют (`0` test files).

Эти пункты не доказывают, что метод не работает. Они доказывают, что
`pip install -e .` и default evaluator недостаточны для принятия paper claims.

## Текущая граница Soll

Проверены четыре локальные точки:

1. Android model chat остаётся backend-mediated через
   `SollGateway.askModelChat(...)`; provider credentials не находятся в APK.
2. `tools/llama-cpp/llama_cpp_active_defaults.json` pin-ит standalone b10068,
   но policy сохраняет `androidRuntimeDefault: soll-backend-route` и
   `packageIntoAndroidApp: false`.
3. В `app/src/main` нет PyTorch/Transformers/SeKV/vLLM CUDA serving layer; Sherpa
   ONNX относится к speech, не к long-context LLM KV cache.
4. `soll_status.md` хранит историческую запись о `qwen3-coder:30b` на
   `127.0.0.1:17200`, но изолированный worktree не содержит launch plan,
   checkpoint revision, GPU identity или живой server implementation. Эта запись
   не считается current runtime proof.

Следовательно, место возможного внедрения — отдельный server-side inference
adapter/harness рядом с фактическим владельцем GPU runtime. Android API и
llama.cpp CPU release-smoke не должны меняться.

## Approval-gated prototype contract

### Phase A — static and tensor correctness

В отдельном Python environment pin exact commit, Python, PyTorch, Transformers,
CUDA, model/tokenizer/data revisions и hardware. Сначала исправить восемь
reproduction gaps и добавить upstream-shaped tests без скачивания production
данных. На synthetic K/V проверить exact partition, anchor retention, rank cap,
reconstruction error, single-softmax gate, H2D byte accounting и освобождение
SVD factors после LRU eviction.

### Phase B — one-model offline pilot

После отдельного model approval взять один доступный frozen backbone и
non-sensitive prompts. Сравнить FullKV, простой static compressed baseline и
SeKV на одинаковых tokens, dtype, decoding и hardware. Начать с 8K/32K; 128K
допускать только после bounded-residency proof. Не подключать pilot к
`127.0.0.1:17200` и не менять server API.

Обязательные metrics:

- output/logit agreement, NIAH retrieval и один long-document QA slice;
- peak GPU bytes, peak CPU bytes, SVD-factor residency и H2D bytes/token;
- prefill/SVD time, TTFT p50/p95, TPOT p50/p95, tokens/s and end-to-end latency;
- expansion rate, effective rank, expanded spans/step and cache hit/eviction;
- errors/OOM, determinism, 30-minute single-request soak and rollback proof.

### Promotion gates

- fixed revisions and one-command offline reproduction;
- upstream-style unit tests plus no leaked GPU factors after eviction;
- FullKV-compatible output/API contract and no quality regression outside an
  agreed benchmark tolerance;
- measured memory benefit after including SVD factors and temporary buffers;
- no p95 latency regression hidden by average throughput;
- explicit rollback to current backend and zero Android contract change.

Если code residency fix, exact protocol reproduction или measurable memory
benefit не проходят, источник остаётся knowledge-only. Текущей задачей
выполнено `0` model downloads, `0` SeKV training/inference/benchmark runs,
`0` dependencies imported и `0` production/runtime changes.
