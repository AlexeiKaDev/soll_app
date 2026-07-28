# llama.cpp b9946: условия теста Qualcomm Hexagon unary ops

Дата проверки: 2026-07-28.

## Решение

Soll_app рассматривает on-device inference только как ограниченный
исследовательский путь. Текущий standalone baseline `b10068` разрешает
Android arm64 CPU binary лишь для upstream harness/ADB smoke, не упаковывает
llama.cpp в APK и оставляет `soll-backend-route` product runtime по умолчанию.

[PR #25474](https://github.com/ggml-org/llama.cpp/pull/25474) merged 2026-07-09
как 10 commits и 9 изменённых Hexagon файлов: wide-row tiling против VTCM
overflow, fastdiv, host-computed kernel parameters, specialized HVX functions,
tracing и build fixes. Merge commit
`fb30ba9a6c5b4674174d06aed14794832ab33278` одновременно является commit
[release b9946](https://github.com/ggml-org/llama.cpp/releases/tag/b9946).
Текущий b10068 на `122` commits ahead и `0` behind b9946, поэтому b9946 не
нужно продвигать как отдельный runtime.

## Android arm64 asset

GitHub release API перечисляет 25 uploaded assets; единственный Android asset:

- `llama-b9946-bin-android-arm64.tar.gz`;
- label: `Android arm64 (CPU)`;
- size: `74337414` bytes;
- SHA-256:
  `c54732403dc88c9a05edfef5b0ec31d63d720a52ec54154fce6b781ad2535712`.

Архив скачан в ignored repository cache и совпал по размеру и SHA-256. В нём
46 entries, включая `llama-cli`, `llama-server` и `libllama.so`; `44/44`
binary files прошли проверку ELF64 little-endian AArch64. Отдельных
Hexagon/HVX/HTP assets или entries по имени нет. Это валидный CPU
package/ABI smoke, но не доказательство эффекта PR #25474.

## Bounded test contract

Реальный тест разрешён только при выполнении всех условий:

1. **Устройство.** Один Qualcomm Snapdragon Android device с поддерживаемым
   HTP/HVX и совместимым Hexagon SDK 6.6; зафиксировать device model, SoC,
   Android build, ABI, firmware/DSP version, battery и thermal state.
2. **Модель.** Upstream-aligned candidate
   `Llama-3.2-1B-Instruct-Q4_0.gguf`; до запуска отдельно закрепить immutable
   source revision, bytes и SHA-256 и добавить ровно этот use case в
   deny-by-default `tools/llama-cpp/approved_models.json`. Сейчас модель не
   одобрена, и allowlist не меняется.
3. **Две сборки.** Собрать parent
   `82fce65d8be40ba55048e06f2e14a01deb363d41` и b9946 commit
   `fb30ba9a6c5b4674174d06aed14794832ab33278` одним toolchain. Использовать
   upstream preset `arm64-android-snapdragon-release` с
   `ANDROID_ABI=arm64-v8a`, `ANDROID_PLATFORM=android-31`,
   `GGML_HEXAGON=ON`, `GGML_OPENCL=ON`, `GGML_OPENMP=OFF`,
   `GGML_LLAMAFILE=OFF`, `LLAMA_OPENSSL=OFF` и одинаковыми
   `HEXAGON_SDK_ROOT`/`HEXAGON_TOOLS_ROOT`. Запускать `D=HTP0`, `NDEV=1`,
   `GGML_HEXAGON_PROFILE=1`; сохранить build logs и binary SHA-256.
4. **Fixture.** Один фиксированный benign offline prompt суммирует три
   синтетические локальные заметки. Sampling, context, prompt, seed, threads и
   token limit одинаковы; `temperature=0`. Выполнить `1 warm-up + 5 measured
   repeats` каждой сборки.
5. **Метрики.** Для каждого repeat сохранить model-load latency, time to first
   token, total latency, prompt tokens/sec, generation tokens/sec, peak
   RSS/PSS, HTP profile/VTCM allocation, battery/thermal state, exact output,
   exit status и backend/fallback trace.
6. **Стабильность и gate.** Требуются `5/5` успешных repeat, `0` crashes,
   timeouts, VTCM overflows, NaN/Inf outputs и unexpected CPU/OpenCL fallback.
   Отдельно публикуются median/p95 latency, median tokens/sec и peak memory для
   parent и b9946; только их delta может быть приписана PR #25474.

Тест полностью offline: airplane mode, direct `llama-cli`, без server,
network, browser, agents, tools/actions, реальных пользовательских данных,
offensive или security scenarios. Он не включает сканирование, fuzzing,
эксплуатацию, auth testing или внешние интеграции.

## Текущее состояние

В worktree нет `adb`, подключённого Qualcomm device, одобренной модели или
Hexagon toolchain. Выполнено `0` device/model inference runs и не заявлены
latency, tokens/sec, memory или stability results. Изменено `0`
production/runtime файлов; b10068, APK policy и `soll-backend-route` остаются
без изменений.

Task-provided raw path
`raw/monitored/llama-cpp-releases/20260711-001152-b9946-3469ed9c.md` отсутствует
в изолированном worktree; все upstream факты выше проверены через официальные
read-only GitHub release, PR, commit, compare и Snapdragon preset surfaces.
