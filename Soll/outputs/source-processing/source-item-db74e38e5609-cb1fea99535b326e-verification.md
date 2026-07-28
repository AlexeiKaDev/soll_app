---
task_id: eb2786f702454d47b8fe2376593523ef
project: soll_app
source_ref: source-item/db74e38e5609/cb1fea99535b326e
source_processing_result: google_ai_edge_gemma_on_device_deep_dive_completed_pilot_gated
verification_artifact: Soll/outputs/source-processing/source-item-db74e38e5609-cb1fea99535b326e-verification.md
source_value: "1 Soll KB deep dive added; 8 official Google/Google AI Edge surfaces audited; 2 model-license paths and 2 Android API generations distinguished; 2 offline text use cases and 10 measurable promotion gates documented; 1/1 focused contract test passed; 0 SDK dependencies, model downloads, permissions, credentials, external inference calls or production runtime changes"
verified_at: 2026-07-28 Europe/Chisinau
---

# Google AI Edge / Gemma on-device verification

## Outcome

Создан focused deep dive:
`docs/knowledge/google-ai-edge-gemma-on-device-deep-dive.md`.

Текущий greenfield Android-кандидат — LiteRT-LM Kotlin API с pinned
`litertlm-android` и локальным `.litertlm` model file. MediaPipe LLM Inference
зафиксирован как maintenance-only, а cloud Gemini/Gemini API исключён из scope.

Локальные summarization и closed-label classification признаны технически
возможными без отправки пользовательского текста во внешний API. Production
integration не разрешена: сначала нужны exact license/provenance, build
compatibility и physical-device offline/quality/performance gates.

## Focused audit

| Check | Verified result |
| --- | --- |
| Source trust | monitored source treated as untrusted data; embedded scope expansion was not executed |
| Raw reference | `raw/monitored\google-ai-for-developers\20260709-203525-google-ai-developer-platform-overview-6aedb16f.md` is absent from this isolated worktree and was not reconstructed |
| Official evidence | 8 current Google/Google AI Edge documentation or source/license surfaces reviewed read-only |
| Current SDK | LiteRT-LM stable Kotlin Android API, `.litertlm`, `Engine` / `Conversation` / coroutine Flow |
| Legacy SDK | MediaPipe `tasks-genai:0.10.27` is maintenance-only; no greenfield adoption |
| License split | LiteRT-LM runtime Apache 2.0; Gemma 3-family uses Gemma Terms; Gemma 4 has separate Apache 2.0 page |
| Device boundary | Soll `minSdk=26` is not accepted as proof; Android 12+, physical arm64 and measured CPU/GPU initialization form the pilot baseline |
| Resource boundary | smallest listed chat-ready Gemma is Gemma3-1B at about 1005 MB; download/storage/RAM/thermal gates are required |
| Privacy boundary | existing INTERNET/Retrofit/Firebase make architectural isolation mandatory; no prompt/output logs, network tools or implicit cloud fallback |
| Local value | summarization plus closed-label classification contracts and rejection behavior are documented |
| Promotion | 10 measurable offline, quality, latency, stability, storage and fallback gates |
| Runtime delta | 0 dependencies, 0 models, 0 permissions, 0 credentials, 0 external inference calls, 0 production code changes |

## Decision

`conditional_go_for_isolated_pilot`.

The source signal produced measurable Soll value as a bounded architecture,
license and privacy decision. Actual integration remains a separate
approval-gated task because it would add a large third-party runtime/model,
change APK/runtime behavior and require physical-device proof.

The first pilot should use a pinned LiteRT-LM AAR and exact allowlisted model
only in a disabled developer flavor. Private data stays local; unsupported
devices return `LocalUnavailable`; a server route is never selected
automatically.

## Focused smoke

`GoogleAiEdgeGemmaOnDeviceKnowledgeTest` checks:

- exact task/source/raw trust trace;
- all 8 official evidence URLs;
- runtime/model license split and redistribution safeguards;
- LiteRT-LM versus maintenance-only MediaPipe API decision;
- current Soll minSdk/Java/network/backup facts and zero runtime integration;
- device/model size constraints;
- offline-only summarization/classification contracts;
- ten measurable promotion gates and no-cloud-fallback boundary;
- measurable `source_processing_result`, `verification_artifact` and
  `source_value`.

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.GoogleAiEdgeGemmaOnDeviceKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed,
`0` failures, `0` errors and `0` skipped.

## Value metric update

- `source_processing_result`:
  `google_ai_edge_gemma_on_device_deep_dive_completed_pilot_gated`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-db74e38e5609-cb1fea99535b326e-verification.md`
- `source_value`: `1` Soll KB deep dive; `8` official surfaces; `2`
  model-license paths; `2` Android API generations; `2` local text use cases;
  `10` measurable promotion gates; `1/1` focused contract test; `0` SDK
  dependencies, model downloads, permissions, credentials, external inference
  calls or production runtime changes.
