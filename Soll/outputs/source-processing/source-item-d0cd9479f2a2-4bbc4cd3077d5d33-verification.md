---
task_id: ee8517e9f913409b82e97cf0d9f8e0ce
project: soll_app
source_ref: source-item/d0cd9479f2a2/4bbc4cd3077d5d33
source_item: llama-cpp-releases-b9928
source_processing_result: research_plan_documented_cpu_control_cannot_validate_hexagon
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-4bbc4cd3077d5d33-verification.md
source_value: "1 research note; 3 comparison arms; 1 synthetic local RAG fixture; 5 measured repeats per arm specified; 1/1 focused contract test passed; 0 production/runtime changes and 0 device/model inference runs"
verified_at: 2026-07-27 Europe/Chisinau
---

# llama.cpp b9928 Android arm64 CPU research-plan verification

## Outcome

The repository already has a constrained local-inference plan: active
standalone release b10068 exposes an Android arm64 CPU ADB-smoke target, while
the Android product runtime remains `soll-backend-route` and
`packageIntoAndroidApp` remains `false`.

The required research note was created at
`docs/knowledge/llama-cpp-b9928-android-arm64-cpu-research-plan.md`. It defines
one benign synthetic summarization/RAG fixture and three comparison arms:
current product runtime, b9928 Android arm64 CPU, and active b10068 Android
arm64 CPU control. It prohibits network-capable agents, tools/actions, dynamic
retrieval and security automation.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release | b9928 -> `81ff7abe50b95fb81cc70a6cdba1eb1a02a48f62`, published 2026-07-08 |
| Upstream signal | PR #25425: Hexagon VTCM/HMX pipeline changes for MUL_MAT, MUL_MAT_ID and FLASH_ATTN_EXT |
| Published Android asset | `llama-b9928-bin-android-arm64.tar.gz`, CPU, 74325550 bytes, SHA-256 pinned |
| Existing Soll plan | Android arm64 CPU is standalone ADB-smoke only; no APK packaging or app JNI/CMake integration |
| Current runtime | `soll-backend-route`; active standalone control b10068 |
| Ancestry | b10068 is 140 commits ahead and 0 behind b9928 |
| Safe scenario | one synthetic three-chunk local summarization/RAG fixture |
| Measurement plan | 1 warm-up + 5 measured repeats for each of 3 arms |
| Safety boundary | 0 network-capable agents, 0 tools/actions, 0 security automation |
| Runtime proof | adb unavailable; 0 device/model inference runs and no performance claim |
| Product change | documentation, verification artifact and contract test only |

The published Android asset is explicitly CPU. The audit therefore does not
claim that an Android CPU comparison measures the release's Hexagon/HMX
changes. A real accelerator attribution test would require a separately
approved parent-vs-b9928 Snapdragon build with `GGML_HEXAGON=ON`.

## Focused contract test

`LlamaCppB9928AndroidCpuResearchPlanTest` guards:

- the release, PR, asset digest and b10068 ancestry evidence;
- the existing backend-route/no-APK runtime boundary;
- all three comparison arms and the synthetic fixed local fixture;
- five-repeat quality, performance, resource and safety measurements;
- explicit separation of CPU feasibility from Hexagon/HMX attribution;
- the quantified value metric and explicit `0` device/model inference runs.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9928AndroidCpuResearchPlanTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `research_plan_documented_cpu_control_cannot_validate_hexagon`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-4bbc4cd3077d5d33-verification.md`
- `source_value`: `1` research note; `3` comparison arms; `1` synthetic local
  RAG fixture; `5` measured repeats per arm specified; `1/1` focused contract
  test; `0` production/runtime changes and `0` device/model inference runs.
