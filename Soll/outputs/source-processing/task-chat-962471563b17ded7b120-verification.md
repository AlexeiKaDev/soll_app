---
task_id: task:chat:962471563b17ded7b120
source_ref: android_app
source_item: habr-bonsai-qwen36-27b-android-feasibility
source_processing_result: deep_analysis_completed_android_pilot_deferred
verification_artifact: Soll/outputs/source-processing/task-chat-962471563b17ded7b120-verification.md
source_value: "1 deep feasibility note added; 8 source claims and 7 current Soll seams audited; 8 promotion gates defined; 0 Android Bonsai runs and 0 measured runtime value"
verified_at: 2026-07-18 Europe/Chisinau
---

# Bonsai 27B Android feasibility verification

## Outcome

The attached Habr article was read at its resolved URL
`https://habr.com/ru/articles/1059572/` and checked against PrismML's official
announcement, binary and ternary Hugging Face model cards, whitepaper/demo
repository, upstream backend matrix, `llama.cpp` Android guide and Android
memory/background-work documentation.

The deep Soll-specific analysis is stored at
`docs/knowledge/bonsai-27b-on-device-android-feasibility.md`.

The decision is deliberately two-part:

- a bounded opt-in text-only `arm64-v8a` Android compatibility spike is
  technically justified;
- Android production integration is not approved until a named device passes
  all eight measurable promotion gates.

No model, binary, NDK/CMake contour, dependency, UI, service, permission or
production behavior was added. No 3.8 GB model download or unapproved external
runtime execution was performed.

## Source fact checks

| Habr/article signal | Primary-source result | Audit decision |
| --- | --- | --- |
| binary 3.9 GB / 1.125 bpw | confirmed as roughly 3.79 GB Q1_0 language weights | weight size is not peak app memory |
| ternary 5.9 GB | 5.9 GB is ideal; current deployed Q2_0 is about 7.2 GB | discrepancy recorded |
| 90% intelligence | 76.11 vs 85.07 aggregate, or 89.5% | not a per-capability SLA |
| agent/tool capability | category falls from 80.0 to 66.03 | direct tool execution rejected |
| 262K mobile context | vendor peak is about 9.4 GB with 4-bit KV, language-only | first spike capped at 4K |
| 11 tok/s on phone | iPhone 17 Pro Max via MLX Swift | not treated as Android evidence |
| Android viability | upstream `llama.cpp` has Android binding and Q1_0 CPU/Vulkan | compatibility spike is possible |
| ready Android distribution | PrismML matrix lists iOS XCFramework, no Android artifact | production integration deferred |

## Repository audit evidence

- `app/build.gradle.kts` defines `minSdk=26`, `targetSdk=34` and existing
  ONNX/Sherpa JNI dependencies, but no `externalNativeBuild` or LLM runtime.
- The repository contains no `CMakeLists.txt` and no owned C/C++ inference
  binding.
- `ModelChatRequest.safeForServer()` strips private messages;
  `SollGateway.askModelChat(...)` and `SollRepository.askModelChat(...)` keep
  the current model path backend-mediated.
- `askModelChat` is not currently called by the presentation layer;
  `ChatViewModel` uses `sendChatTurn`, so a local model must not create a
  duplicate chat product.
- `SollChatActionPolicyRegistry` is the local allowlist for model-proposed
  actions and must remain outside model control.
- The app already owns memory-intensive ONNX TTS/STT and media paths, so LLM
  residency requires explicit arbitration and real-device measurement.
- The roadmap still states `No heavy local LLM on Android in early phases`;
  this analysis reopens only a removable experiment, not general rollout.

## Focused audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Attached article is resolved and read | canonical Habr URL recorded | PASS |
| Primary sources are distinguished from vendor claims | official model/runtime docs and caveat | PASS |
| Headline numbers are not repeated blindly | ternary and aggregate-quality corrections | PASS |
| Current Soll architecture is audited | build, JNI, chat, model bridge and tool policy | PASS |
| Android recommendation is actionable | P0-P3 plan and exact first-spike scope | PASS |
| Privacy and tool execution remain gated | no silent cloud fallback or direct actions | PASS |
| Promotion is measurable | 8 promotion gates | PASS |
| Production remains unchanged | documentation, roadmap, test and artifact only | PASS |

`QwenBonsaiAndroidFeasibilityTest` is the focused repository contract. It
verifies the roadmap decision, source corrections, Soll-specific architecture,
first-spike limits, all eight gate families and this artifact's value fields.

## Product decision

Keep the server route as default. Open a separate implementation task only
after choosing one exact Android target device. Start with the 1-bit Q1_0
language model, `arm64-v8a`, 4K context, 512 output tokens, no vision, no
drafter and no tool execution. Build/run it first in the upstream Android
harness, then integrate behind a `ModelEngine` adapter only if load, memory,
speed, thermal, quality, safety, offline and delivery/rollback gates pass.

## Value metric update

- `source_processing_result`:
  `deep_analysis_completed_android_pilot_deferred`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-chat-962471563b17ded7b120-verification.md`
- `source_value`: 1 deep feasibility note was added, 8 source claims and 7
  current Soll seams were audited, and 8 measurable promotion gates were
  defined. Actual Android Bonsai runs: `0`; measured Android runtime value: `0`
  because no target device/model execution was in the approved task.
