---
task_id: cd32673e2db04d698b3a222fe3eaa15b
project: soll_app
source_ref: source-item/d0cd9479f2a2/d4aa83030fe52bee
source_item: llama-cpp-releases-b9933
source_processing_result: android_adreno_q6_k_smoke_check_documented_runtime_unchanged
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-d4aa83030fe52bee-verification.md
source_value: "1 Soll_app Q6_K regression note added; 6 provenance/result fields, 6 focused smoke steps and 6 runtime promotion gates documented; 1/1 focused contract test passed; 0 production/runtime changes and 0 device/model inference runs"
verified_at: 2026-07-24 Europe/Chisinau
---

# llama.cpp b9933 Android/Adreno Q6_K smoke verification

## Outcome

The required Soll_app check is recorded in
`docs/knowledge/llama-cpp-b9933-android-adreno-q6-k-smoke.md`.
Every future local llama.cpp test on Android/Adreno must record the exact
llama.cpp version, prove Q6_K model provenance, include a real vocab/tensor
shape with `dimension % 128 != 0`, compare the Adreno/OpenCL result with a CPU
reference, and keep runtime promotion behind the normal regression/smoke
process.

This task does not update the Android or standalone runtime. The active policy
still uses `soll-backend-route`, does not package llama.cpp into the APK, and
does not select b9933 as an active default.

The task-referenced raw artifact is not present in the isolated worktree. The
note therefore scopes the release claim to the supplied source item and does
not invent an upstream commit, PR, benchmark or changed-file list.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release signal | b9933 Android/Adreno OpenCL Q6_K non-128 tail correctness check recorded |
| Required provenance | exact release/commit, binary hash, device/driver, GGUF hash, Q6_K and non-zero modulo dimension |
| Correctness oracle | fixed prompt/seed plus same-version CPU reference |
| Focused execution gate | cold load, prefill and generation, at least 3 repeats |
| Pass condition | 0 crashes, 0 load/kernel errors, 0 NaN/Inf, 0 unexpected fallback and 0 correctness mismatches |
| Regression control | ordinary multiple-of-128 model retained but cannot replace non-128 Q6_K fixture |
| Promotion policy | active defaults may change only in a separate review after normal regression/smoke |
| Product change | documentation, audit artifact and focused contract test only |
| Runtime proof | 0 device/model inference runs; no physical Adreno result claimed |

## Focused smoke/audit artifact

`LlamaCppB9933AdrenoQ6KSmokeContractTest` guards:

- the exact task, source reference, release URL and missing-raw boundary;
- exact llama.cpp version, Q6_K and `dimension % 128 != 0` requirements;
- CPU reference, three-run correctness criteria and zero-failure gate;
- the normal regression/smoke promotion and separate-review rule;
- unchanged `soll-backend-route` and no llama.cpp packaging in Android;
- the quantified source value and explicit absence of a device inference claim.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9933AdrenoQ6KSmokeContractTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `android_adreno_q6_k_smoke_check_documented_runtime_unchanged`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-d4aa83030fe52bee-verification.md`
- `source_value`: `1` Soll_app Q6_K regression note; `6` provenance/result
  fields; `6` focused smoke steps; `6` runtime promotion gates; `1/1` focused
  contract test; `0` production/runtime changes and `0` device/model inference
  runs.
