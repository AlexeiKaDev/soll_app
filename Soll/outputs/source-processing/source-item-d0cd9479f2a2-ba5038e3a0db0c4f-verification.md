---
task_id: ea94bdf1f8a143e9b8b271f6dbf7dcac
project: soll_app
source_ref: source-item/d0cd9479f2a2/ba5038e3a0db0c4f
source_item: llama-cpp-releases-b9946
source_processing_result: pr_and_android_cpu_asset_verified_hexagon_benchmark_gated
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-ba5038e3a0db0c4f-verification.md
source_value: "1 short test note added; PR #25474's 10 commits/9 Hexagon files and 25 uploaded b9946 assets audited; 1 Android arm64 CPU archive checksummed; 44/44 binaries passed ELF64 AArch64 smoke; 2 matched custom-build arms and 6 measurement/stability gates defined; 1/1 focused contract test passed; 0 production/runtime changes and 0 device/model inference runs"
verified_at: 2026-07-28 Europe/Chisinau
---

# llama.cpp b9946 Qualcomm/Android verification

## Outcome

PR #25474 and the b9946 Android arm64 release surface are confirmed. The PR
merged into the exact b9946 release commit and is a Qualcomm Hexagon/HVX unary
kernel change. The only published Android asset is a CPU archive; its presence
does not demonstrate that the Hexagon path builds, runs or improves a model.

Soll_app already has a conditional on-device research boundary, so the short
test note was added at
`docs/knowledge/llama-cpp-b9946-qualcomm-hexagon-unary-test-note.md`. It defines
an exact Qualcomm device prerequisite, model provenance gate, parent-vs-b9946
Snapdragon build flags, latency/tokens-per-second/memory/stability measurements
and an offline benign-only safety boundary.

## Focused upstream audit

| Check | Observed result |
| --- | --- |
| PR | [#25474](https://github.com/ggml-org/llama.cpp/pull/25474), merged 2026-07-09; 10 commits, 9 files, +1088/-682 |
| PR scope | Hexagon unary tiling, VTCM overflow avoidance, fastdiv, host kernel params, specialized HVX functions, tracing and build fixes |
| Release identity | [b9946](https://github.com/ggml-org/llama.cpp/releases/tag/b9946) -> `fb30ba9a6c5b4674174d06aed14794832ab33278`, the PR merge commit |
| Release assets | 25 uploaded assets in GitHub API; exactly 1 name contains `android`; 0 names contain Hexagon/HVX/HTP |
| Android asset | `llama-b9946-bin-android-arm64.tar.gz`, labeled CPU, 74337414 bytes |
| Published SHA-256 | `c54732403dc88c9a05edfef5b0ec31d63d720a52ec54154fce6b781ad2535712` |
| Download smoke | local ignored-cache size/hash matched; gzip/tar integrity passed; 46 entries; required CLI/server/libllama present |
| ABI smoke | 44/44 non-license files are ELF64 little-endian AArch64 |
| Hexagon boundary | CPU archive has no separately named Hexagon/HVX/HTP payload; accelerator attribution requires a custom Snapdragon build |
| Active baseline | b10068 is 122 commits ahead and 0 behind b9946; Android product default remains `soll-backend-route` |
| Runtime proof | `adb` unavailable; 0 device/model inference runs and 0 performance claims |

The task-referenced
`raw/monitored/llama-cpp-releases/20260711-001152-b9946-3469ed9c.md` is not
vendored in this isolated worktree. The audit used official read-only GitHub
release, PR, commit, compare, Snapdragon README and CMake preset surfaces.

## Test contract

The note requires two checksummed custom builds—parent
`82fce65d8be40ba55048e06f2e14a01deb363d41` and b9946—using the same
`arm64-android-snapdragon-release` toolchain with `GGML_HEXAGON=ON`. It pins
the remaining build flags, `D=HTP0`, `NDEV=1` and
`GGML_HEXAGON_PROFILE=1`, while requiring separate immutable approval for the
candidate GGUF.

Six measurable gates cover exact identity/backend, 5/5 completed repeats,
correct fixed-fixture output, latency and tokens/sec, peak RSS/PSS and HTP/VTCM
profiling, plus zero crashes/timeouts/VTCM overflows/NaN/fallbacks. The test is
direct offline inference only: no network, server, agents, tools/actions,
offensive/security scenarios, scans, fuzzing, exploitation or auth testing.

## Focused contract test

`LlamaCppB9946QualcommHexagonTestNoteTest` guards:

- exact PR/release/parent identity and Android asset checksum evidence;
- CPU-asset versus custom-Hexagon-build separation;
- Qualcomm device, model provenance, build flags and six measurement gates;
- the benign offline-only scenario and explicit prohibited scenarios;
- unchanged b10068/backend-route/no-APK/deny-by-default model policy;
- the quantified `source_value` and explicit zero runtime claim.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9946QualcommHexagonTestNoteTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `pr_and_android_cpu_asset_verified_hexagon_benchmark_gated`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-ba5038e3a0db0c4f-verification.md`
- `source_value`: `1` short test note; PR #25474's `10` commits/`9` Hexagon
  files and `25` uploaded release assets audited; `1` Android arm64 CPU archive
  checksummed; `44/44` binaries passed ELF64 AArch64 smoke; `2` matched custom
  build arms and `6` measurement/stability gates defined; `1/1` focused
  contract test passed; `0` production/runtime changes and `0` device/model
  inference runs.
