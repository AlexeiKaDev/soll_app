# llama.cpp b9891 Metal COL2IM_1D: применимость к Soll app

Дата проверки: 2026-07-19.

## Решение

Обновление Android dependencies или production build для b9891 не требуется.
Изменение добавляет `GGML_OP_COL2IM_1D` только в Apple Metal backend, а Soll
app является Android-приложением и использует `soll-backend-route`. В app нет
`CMakeLists.txt`, `externalNativeBuild`, собственного JNI/libllama или Apple
target. Поэтому у b9891 нет исполняемого пользовательского пути в Soll app.

Standalone verification tools уже закрепляют `b10068`, который новее b9891 и
содержит это изменение. Их активные smoke targets — Windows x64 CPU и Android
arm64 CPU; они не доказывают Metal correctness и не выдают его за локально
измеренную ценность.

## Что подтверждено upstream

- [официальный релиз b9891](https://github.com/ggml-org/llama.cpp/releases/tag/b9891)
  опубликован 6 июля 2026 года и указывает на commit
  `f36e5c348bc8795c34f9a038e58876e7a8423d4d`;
- [PR #25176](https://github.com/ggml-org/llama.cpp/pull/25176) добавляет Metal
  gather kernel для `COL2IM_1D` с `f32`, `f16` и `bf16`;
- каждый output element обрабатывается одним thread, accumulator остаётся F32,
  запись выполняется без atomics;
- `supports_op` требует contiguous destination и одинаковый type у destination
  и `src0`, как CPU/CUDA/Vulkan paths;
- upstream сообщил успешный `test-backend-ops -o COL2IM_1D` на Apple M2 для
  всех трёх типов. Это upstream evidence, не локальный Soll benchmark.

## Когда задача станет применимой

Новый implementation task нужен только при появлении отдельного Apple target
или macOS/iOS llama.cpp harness с workload, который реально строит
`GGML_OP_COL2IM_1D`. Тогда необходимо закрепить Apple device/OS, llama.cpp
commit, exact model/workload, сравнить Metal output с CPU reference для
f32/f16/bf16 и измерить correctness, latency и memory. До этого нельзя
добавлять Apple artifacts в Android APK или менять Gradle dependencies.

## Измеримый результат

- проверены `2` official upstream surface и `5` local runtime/build seam;
- найдено `0` Apple/Metal production targets и `0` llama.cpp JNI seam;
- изменено `0` Android dependencies и `0` production build files;
- добавлена `1` KB-запись с точным условием повторного открытия задачи.
