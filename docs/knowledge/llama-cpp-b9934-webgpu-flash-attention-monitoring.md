# llama.cpp b9934: WebGPU Flash Attention monitoring

Дата проверки: 2026-07-24.

## Решение

`b9934` стоит сохранить как monitoring-сигнал, но не внедрять отдельно в Soll
runtime. Основание для заметки есть: PR #25418 публикует числовой A/B benchmark
для WebGPU token generation на NVIDIA V100 и Apple M2 при context length 16K.
Это не общий GPU-релиз и не доказательство ускорения CUDA, Metal, Vulkan,
OpenCL, CPU или текущего Android runtime.

Текущий `llama_cpp_active_defaults.json` уже pin-ит более новый `b10068`.
Официальный upstream compare показывает, что b10068 на `134` commits впереди
b9934, на `0` позади, а merge base равен exact b9934 commit. Однако оба
активных standalone target используют CPU, llama.cpp не упаковывается в APK, а
Android продолжает отправлять chat turn через `POST api/v1/chat/turn` в
`soll-backend-route`. Поэтому production code, dependencies, runtime route и
release defaults не меняются.

## Граница исходника

Task-referenced raw artifact
`raw/monitored/llama-cpp-releases/20260709-233427-b9934-5e34e4da.md` отсутствует
в изолированном worktree. Заметка не приписывает ему дополнительных деталей и
основана на трех официальных upstream surface:

- [release b9934](https://github.com/ggml-org/llama.cpp/releases/tag/b9934);
- [commit `32e41fa5b48e15b93c7a40ce677226b2e773c351`](https://github.com/ggml-org/llama.cpp/commit/32e41fa5b48e15b93c7a40ce677226b2e773c351);
- [PR #25418](https://github.com/ggml-org/llama.cpp/pull/25418).

Релиз опубликован 9 июля 2026 года. Commit меняет только два WebGPU-файла:
`ggml-webgpu-shader-lib.hpp` и `flash_attn_vec_split.wgsl`, с `16`
добавлениями и `23` удалениями. Parent для точного A/B сравнения —
`92366df30d4eaa4b85139b5fd694360237731b19`.

## Какие GPU/WebGPU сценарии меняются

Изменение относится только к `ggml-webgpu` kernel `flash_attn_vec` при
включенном Flash Attention. Оно перераспределяет subgroup lanes между
накоплением `Q * K` и `P * V`.

1. Старый compile-time параметр `VEC_NE` заменен на `D_SPLIT`.
2. Default `D_SPLIT` равен `context.min_subgroup_size`. Для F16 K и V он
   вычисляется по `head_dim_qk | head_dim_v`, ограничивается диапазоном от `1`
   до `4` и больше не требует равенства двух head dimensions.
3. Старые специальные случаи `64/192/576 -> VEC_NE=2` и
   `96 -> VEC_NE=4` заменены одним размерно-зависимым правилом. Поэтому
   меняются как ранее специальные, так и другие F16 head-dimension формы,
   включая асимметричные QK/V dimensions.
4. Значение `D_SPLIT` добавлено в имя pipeline variant (`_dsplit...`), чтобы
   разные shader specialization не переиспользовали один cache key.
5. WGSL loop strides для QK и PV теперь выводятся из `D_SPLIT`; PV loop получил
   явную проверку `kv_idx >= KV_TILE` для неполного разбиения tile.

Следовательно, затронут WebGPU Flash Attention path на GPU с subgroup support,
а не физический GPU сам по себе. V100 в опубликованном опыте работает через
WebGPU, не через CUDA; M2 — через WebGPU, не через Metal backend.

## Подтвержденный upstream benchmark

PR сообщает token-generation результаты при `-fa 1`, `-p 0`, `-n 128`,
`-d 16384`, `-r 3`, `-dev WebGPU`. Автор описывает `-d 16384` как context
length 16K. Числа ниже — upstream A/B, не локальное измерение Soll.

| WebGPU device | Model / quantization | До, t/s | PR, t/s | Reported speedup |
| --- | --- | ---: | ---: | ---: |
| NVIDIA V100 | gemma4 E4B Q4_K_M | 26.02 ± 0.16 | 27.21 ± 0.15 | +4.6% |
| NVIDIA V100 | gpt-oss 20B MXFP4 MoE | 26.35 ± 0.37 | 27.78 ± 0.36 | +5.4% |
| NVIDIA V100 | llama 3B Q4_K_M | 25.55 ± 1.44 | 34.78 ± 2.57 | +36.1% |
| Apple M2 | gemma4 E4B Q4_K_M | 6.79 ± 1.53 | 6.83 ± 1.50 | +0.6% |
| Apple M2 | gpt-oss 20B MXFP4 MoE | 20.12 ± 0.17 | 22.22 ± 0.16 | +10.4% |
| Apple M2 | llama 3B Q4_K_M | 18.18 ± 0.05 | 20.18 ± 0.01 | +11.0% |

На head SHA PR официальные checks `gpu-webgpu-nvidia` и
`gpu-webgpu-apple` завершились с `success`. Это полезное correctness/build
свидетельство, но оно не расширяет benchmark на другие adapters, head
dimensions или workload.

Практически значимые подтвержденные сценарии — WebGPU token generation на
16K context для указанных device/model пар. M2 gemma4 `+0.6%` находится внутри
опубликованного разброса и не считается самостоятельным доказательством
ускорения. Prompt processing, короткий context, parallel serving, memory,
power, thermals и другие backends PR численно не измеряет.

## Риск регрессии и локальная применимость

Подтвержденной регрессии в PR нет. Но regression surface существует: новое
разбиение применяется шире старого списка специальных F16 dimensions и меняет
QK/PV loop partition. В benchmark покрыты только три модели, два устройства,
16K context и три повтора. Поэтому нельзя переносить максимальный `+36.1%` на
другие subgroup sizes, adapters, head dimensions или длины context без A/B.

| Soll seam | Проверенный факт | Решение |
| --- | --- | --- |
| Android chat | `SollApiService` вызывает `POST api/v1/chat/turn` | Не раскрывать WebGPU backend в Android contract |
| Runtime policy | `androidRuntimeDefault` — `soll-backend-route`, `packageIntoAndroidApp` — `false` | Не менять APK или runtime route |
| Active release | b10068 содержит b9934 и опережает его на 134 commits | Не делать отдельный rollout старого tag |
| Active targets | manifest содержит только Android arm64 CPU и Windows x64 CPU | Текущий smoke не исполняет WebGPU shader |
| Model gate | allowlist содержит только tiny CPU smoke fixture, не три benchmark-модели | Локальный model-backed WebGPU benchmark сейчас не разрешен |

Поиск в production Android source и `tools/llama-cpp` дал `0` текущих WebGPU
backend, `ggml-webgpu`, WGSL или `flash_attn_vec` execution seam.

## Ворота будущего WebGPU A/B

Повторно открыть runtime-задачу следует только при появлении одобренного
WebGPU inference host/workload или конкретного regression report:

1. Зафиксировать GPU, OS, WebGPU implementation/adapter, driver, browser или
   native Dawn version, subgroup size и exact llama.cpp commit.
2. Сравнить parent `92366df30d4eaa4b85139b5fd694360237731b19` с b9934
   либо доказать наличие commit b9934 в тестируемом b10068 binary.
3. Использовать checksummed approved GGUF и отдельно покрыть F16 K/V,
   equal/unequal QK/V head dimensions и representative `64`, `96`, `128`,
   `192`, `576` dimensions.
4. Минимум в пяти повторах измерить prompt и generation tokens/s, TTFT,
   latency p50/p95, peak memory, power и thermals на коротком и 16K context.
5. Проверить output parity/backend ops; crashes, shader validation errors,
   NaN, out-of-bounds и unexpected fallback должны быть равны `0`.
6. Продвигать WebGPU path только при устойчивом выигрыше целевой фазы не менее
   `10%` без регрессии correctness/resources; сохранить `soll-backend-route`
   и CPU baseline как rollback.

## Наблюдаемая ценность

- Добавлена `1` WebGPU monitoring note на основании `6` числовых benchmark rows.
- Проверены `3` официальные upstream surface, `2` WebGPU CI job и `5` текущих
  Soll seam.
- Классифицированы WebGPU-only scenario, непроверенные области и `6` ворот
  будущего A/B.
- Подтверждено, что b10068 на `134` commits впереди b9934 и уже содержит exact
  commit.
- Изменено `0` production/runtime файлов, dependencies, API contracts и
  release defaults.
- Выполнено `0` локальных WebGPU inference/benchmark runs; текущая измеренная
  runtime-ценность Soll app остается `0`.
