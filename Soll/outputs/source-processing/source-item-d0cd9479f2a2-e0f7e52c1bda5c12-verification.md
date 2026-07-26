---
task_id: d684628fc9304beebcaf4e55bc792097
project: soll_app
source_ref: source-item/d0cd9479f2a2/e0f7e52c1bda5c12
source_item: llama-cpp-releases-b9916
source_processing_result: llama_cpp_b9916_deterministic_inference_smoke_passed_pin_unchanged
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-e0f7e52c1bda5c12-verification.md
source_value: accepted
value_metric: "1 merged PR and verified commit audited; 4/4 fixed-input CPU inference runs passed; 2/2 runs deterministic on b9916 and 2/2 on active b10068; 26-byte output matched across releases; active baseline 152 commits ahead; 0 pin, APK or runtime-route changes"
verified_at: 2026-07-26 Europe/Chisinau
---

# llama.cpp b9916 scalar-tail inference verification

## Result

PR
[#25390](https://github.com/ggml-org/llama.cpp/pull/25390), commit
[`57b50e1f6b50e01eb14c43fc1253602af74c1870`](https://github.com/ggml-org/llama.cpp/commit/57b50e1f6b50e01eb14c43fc1253602af74c1870)
and release [b9916](https://github.com/ggml-org/llama.cpp/releases/tag/b9916)
were verified against official upstream surfaces. The deterministic local
inference smoke passed on b9916 and the current pinned b10068 baseline.

No pin update is warranted. The active b10068 commit
`571d0d540df04f25298d0e159e520d9fc62ed121` is `152` commits ahead and `0`
behind the b9916 fix. It already contains the correction and remains newer than
the configured b9917 GGUF security baseline. Android continues to use
`soll-backend-route`; no llama.cpp binary or GGUF is packaged into the APK.

The acceptance text abbreviates the candidate as `b991`; this verification
uses the task's exact source release, `b9916`.

## Upstream audit

| Check | Verified result |
| --- | --- |
| PR | #25390 was merged on 2026-07-08 with 1 changed file, +1/-1 |
| Commit | `57b50e1f6b50e01eb14c43fc1253602af74c1870`; GitHub signature verification `valid` |
| File | `ggml/src/ggml-cpu/simd-gemm.h` |
| Defect | scalar tail-column calculation used `A[i + kk]` after `A` had already advanced to the current full row block |
| Fix | replace `A[i + kk]` with row-major `A[i * K + kk]` |
| Release | b9916 targets the full fix commit and was published 2026-07-08 12:21:04 UTC |
| Active ancestry | b10068 is `152` commits ahead and `0` behind commit `57b50e1` |

This is a model-backed CPU regression smoke, not instruction-level branch
coverage of `simd_gemm`. The exact tail-column correction is established by the
one-line upstream patch audit; the runtime smoke establishes that both fixed
release points execute the same controlled Soll fixture successfully.

## Deterministic smoke

Command:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppB9916DeterministicInference.ps1
```

Controls:

- exact SHA-256 and byte count for both official Windows x64 CPU archives;
- deny-by-default, immutable `stories15M-q8_0.gguf` fixture provenance;
- `llama-completion.exe`, CPU only, 1 inference thread, 1 batch thread;
- fixed prompt, seed `424242`, temperature `0`, context `128`, 8-token budget;
- two isolated runs per release and a 120-second subprocess timeout;
- no model discovery, network model resolution, APK packaging or Android
  runtime change.

`llama-completion.exe` is intentional. b9916's `llama-cli` reports that
non-conversation mode is unsupported and directs batch inference callers to
`llama-completion`.

| Field | b9916 | active b10068 |
| --- | --- | --- |
| Version | `9916 (57b50e1f6)` | `10068 (571d0d540)` |
| Archive bytes | `17,498,364` | `18,007,324` |
| Archive SHA-256 | `b9421aa043ef9e93d518246e26a2c89aa073237ad9122a8b327792177cae7c8b` | `01d5f30876acfb4a0be59396710f450213495c7181d8fbcce2fad045835ceb89` |
| Successful runs | `2/2` | `2/2` |
| Within-release deterministic | `true` | `true` |
| Output bytes | `26` | `26` |
| Output SHA-256 | `193a9313cf55adbde15b7742e5e36fa69a328149c2db5d33cb82305c9c3329ff` | `193a9313cf55adbde15b7742e5e36fa69a328149c2db5d33cb82305c9c3329ff` |

Observed summary: `inferenceRuns: 4`, `allRunsExitCodeZero: true`,
`crossReleaseOutputMatch: true`.

## Decision and value

Keep b10068 pinned. The monitored signal was valid and the executable check
produced measurable regression evidence, but b9916 is not an upgrade candidate
for the current repository. Its manifest is explicitly
`historicalComparisonOnly` and `notApprovedAsActiveBaseline`.

Focused repository contract:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9916DeterministicInferenceSmokeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed.

## Value metric update

- `source_processing_result`:
  `llama_cpp_b9916_deterministic_inference_smoke_passed_pin_unchanged`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-e0f7e52c1bda5c12-verification.md`
- `source_value`: `accepted`
- `value_metric`: `1` merged PR and verified commit audited; `4/4`
  fixed-input CPU inference runs passed; `2/2` runs deterministic on b9916 and
  `2/2` on active b10068; `26`-byte output matched across releases; active
  baseline `152` commits ahead; `0` pin, APK or runtime-route changes.
