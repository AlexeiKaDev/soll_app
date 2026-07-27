# llama.cpp b9924: аудит безопасного локального inference

Дата проверки: 2026-07-27.

## Решение

Официальный `b9924` Android archive совместим с Android arm64 CPU на уровне
release package и ABI. Архив прошёл проверку размера, SHA-256, tar integrity и
ELF headers: все `44/44` binary files имеют формат ELF64 little-endian AArch64,
а `llama-cli`, `llama-server` и `libllama.so` присутствуют.

Обновлять изолированный Soll/Soll_app runtime до b9924 не нужно. Текущий
standalone baseline уже `b10068`: он на `144` commits ahead and `0` behind
b9924 и содержит commit b9924. Переход на b9924 был бы downgrade ниже
repository gate `minimumChatTemplateFixRelease = 9945`, а не полезным
обновлением. `soll-backend-route` остаётся Android product runtime,
`packageIntoAndroidApp` остаётся `false`, model allowlist и production code не
меняются.

## Проверенная граница source

Указанный задачей monitored artifact
`raw/monitored/llama-cpp-releases/20260708-223009-b9924-de42dc6d.md` не vendored
в изолированном worktree. Task record проверен через официальные read-only
upstream surfaces:

- [release b9924](https://github.com/ggml-org/llama.cpp/releases/tag/b9924),
  опубликованный 2026-07-08 с commit
  `90e0f5cfcb6cdb4b7b60a4f81b0a26e542149ad5`;
- [PR #24646](https://github.com/ggml-org/llama.cpp/pull/24646), merged с тем
  же commit;
- [issue #25644](https://github.com/ggml-org/llama.cpp/issues/25644), открытый
  post-merge placement report;
- [compare b9924...b10068](https://github.com/ggml-org/llama.cpp/compare/b9924...b10068);
- GitHub Actions metadata для PR head, Android CI и release workflow.

Release changelog содержит один change: `llama: refactor fused ops (#24646)`.
Из `27` отображаемых release artifacts два являются автоматически
сгенерированными source archives; опубликованный Android target один:
`Android arm64 (CPU)`.

## PR #24646: scope и влияние

PR состоит из `4` commits и меняет `6` source files (`122` additions, `131`
deletions):

- `src/llama-context.cpp` и `src/llama-context.h`;
- `src/llama-graph.cpp` и `src/llama-graph.h`;
- `src/llama-impl.h`;
- `src/models/delta-net-base.cpp`.

Он добавляет `llm_graph_fused_node` и `resolve_fused_ops`, затем заменяет
поиск Flash Attention и Gated Delta Net nodes по tensor names на явные
graph descriptors. Изменений Android build, ARM CPU kernels или файлов
`ggml/src` нет.

Это refactor корректности/расширяемости, а не доказанная performance feature:
в PR отсутствуют Android benchmark, tokens/s, TTFT, memory, thermal или power
results. Поэтому для Android arm64 CPU нельзя заявлять ускорение или
регрессию скорости только по changelog.

Чувствительная область — automatic fused-op probing при создании context.
В коде сохранён TODO: сравнение с `model.dev_layer()` поддерживает прежнее
поведение, но не является descriptor-specific и остаётся неверным для
некоторых случаев вроде `--no-kv-offload`.

Post-merge issue #25644 описывает crash в multi-node RPC CPU конфигурации при
несогласованных `-ot` и `-ts`. Автор issue явно классифицирует это как
`Not a regression`: PR #24646 только сделал placement mismatch видимым через
`resolve_fused_ops`. Этот кейс не совпадает с single-device Android CPU
standalone smoke, но является причиной не продвигать b9924 без
model-backed/device-specific regression run.

## CI audit

PR head `7219e8dfe1d82d44479d983364b1ded632f28c46` имел `25/25` successful
checks. Для merge commit:

- Android CI: `arm64`, `default` и `ndk` — `3/3` success;
- release `android-arm64` build/package/upload job — success;
- final release creation/upload job — success.

При этом весь post-merge CI не был полностью зелёным:

- `CI (cpu)` имел failed Ubuntu x64 `Test`, после чего Ubuntu arm64 job был
  cancelled;
- `Server (sanitize)` имел failed `Python setup`.

Публичный API не отдал logs этих двух jobs без authentication (`403`), поэтому
их нельзя честно объявить связанными или не связанными с PR. Успешные Android
и release jobs подтверждают сборку/публикацию Android asset, но не заменяют
runtime correctness или performance benchmark.

## Локальный Android package/ABI smoke

Проверен официальный asset:

| Поле | Наблюдение |
| --- | --- |
| Asset | `llama-b9924-bin-android-arm64.tar.gz` |
| Published/local size | `78812406` bytes |
| Published/local SHA-256 | `018f1db4fced30044b90f95b44ab6a18d439142e5d3a125b5b5ec5a0a06d4ad5` |
| Tar entries | `46` |
| Extracted files | `45`: `1` license + `44` binaries |
| Shared libraries | `21` `.so` files |
| Required files | `llama-cli`, `llama-server`, `libllama.so` |
| ABI check | `44/44` ELF64, little-endian, `e_machine = 0xB7` AArch64 |

Archive хранится только в ignored cache `build/llama-b9924-audit` и не является
изменением репозитория или APK. `adb` на worker unavailable, поэтому выполнено
`0` device runs и `0` model inference runs. Проверка не доказывает Bionic/API
level compatibility, runtime CPU-feature dispatch, качество, latency,
throughput, RSS или thermals на конкретном телефоне.

Обычная Soll_app debug-сборка прошла. В APK было `869` entries и `0/869`
совпадений с `b9924`, `llama-cli`, `llama-server` или `libllama`, что
подтверждает отсутствие утечки audit archive в приложение.

## Safety boundary и promotion gate

`llama-server` присутствует в архиве, но не запускался. Не включались HTTP,
RPC, model download, browser/tools, shell tools, сетевые или autonomous agent
capabilities. Model allowlist не расширялся; ни один GGUF не загружался.

Повторно открывать promotion имеет смысл только для release новее active
baseline и после отдельного approved device smoke:

1. зафиксировать Android device/SoC/build, binary и GGUF SHA-256;
2. использовать direct offline `llama-cli`, CPU-only и deny-by-default model;
3. выполнить `1` warm-up + `5` measured repeats с фиксированными prompt,
   sampler, seed, context, token limit и thread count;
4. проверить output equality/quality, `0` crashes/fallbacks/network/tool calls,
   latency, prompt/generation tokens/s, peak RSS, battery и thermals;
5. сравнить с текущим b10068 на том же устройстве и сохранить b10068/backend
   rollback.

Наблюдаемая ценность b9924 ограничена maintenance evidence: Android package и
ABI совместимы, refactor/regression boundaries понятны, но отдельной
измеримой runtime-пользы поверх b10068 нет.

Изменения: `0` production/runtime, dependency, API, APK и model-allowlist
changes; device/model inference runs: `0`.
