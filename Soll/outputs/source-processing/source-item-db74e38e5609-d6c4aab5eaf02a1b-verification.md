---
task_id: 053acb56309a4990a1a25d3a4db19d55
project: soll_app
source_ref: source-item/db74e38e5609/d6c4aab5eaf02a1b
source_processing_result: official_google_ai_data_boundary_and_safety_decision_completed
verification_artifact: Soll/outputs/source-processing/source-item-db74e38e5609-d6c4aab5eaf02a1b-verification.md
source_value: "1 Soll decision note added; 12 official Google documentation surfaces reviewed; 3 Google AI execution contours, 4 local data classes, 6 cloud-only capability classes, 11 safety controls and 8 privacy-safe monitoring signals documented; 1/1 focused contract test passed; 0 runtime dependencies, permissions, credentials, external inference calls or production behavior changes"
verified_at: 2026-07-28 Europe/Chisinau
---

# Google AI data boundary and safety verification

## Outcome

Создан короткий decision note:
`docs/knowledge/google-ai-data-boundary-and-safety-decision.md`.

Он явно разделяет:

1. Google AI Edge как on-device runtime/tooling ecosystem;
2. Gemini Nano через ML Kit GenAI и Android AICore;
3. cloud Gemini API с per-request safety configuration и внешней data boundary.

Private messages, короткие notes/drafts и явно выбранные image/audio inputs
назначены on-device route. Large context, remote/server state, grounding,
cloud-only tools, background processing и unsupported-device capability
остаются cloud capability. Это не разрешает отправку private data: cloud
получает только минимизированный non-private payload после
`ModelChatRequest.safeForServer()`, PII/attachment gate и consent.

## Focused audit

| Check | Verified result |
| --- | --- |
| Source trust | monitored item treated as untrusted data; no embedded scope expansion executed |
| Raw reference | `raw/monitored\google-ai-for-developers\20260709-203525-on-device-responsible-ai-and-security-8abb4f49.md` is absent from this isolated worktree and was not reconstructed |
| Official evidence | 12 current Google AI, Android Developers, Google AI Edge and ML Kit documentation surfaces reviewed read-only |
| Stack split | Google AI Edge, Gemini Nano/AICore and Gemini API have separate execution and ownership boundaries |
| On-device privacy | supported ML Kit input/inference/output stays local; AICore request isolation does not authorize Soll logging |
| On-device network nuance | model/update/metrics traffic is distinct from inference data and must never receive a Soll prompt/output |
| Local data classes | private selected chat, local note/draft/text, selected image and selected short audio |
| Never-model data | credentials, API keys, pairing/authentication tokens |
| Default-deny device data | bulk contacts, SMS/call history, location history and media library need separate review |
| Cloud-only capability | large context, remote state, grounding, tools/code/remote files, background work and unsupported-device coverage |
| Cloud payload | server-mediated, non-private, minimized, PII/attachment gated and consented |
| Safety settings | all 4 adjustable Gemini API categories explicit at initial `BLOCK_MEDIUM_AND_ABOVE`; no reliance on Gemini 2.5/3 `OFF` defaults |
| Safety feedback | prompt block, `finishReason=SAFETY` and ratings handled without threshold downgrade or bypass retry |
| Monitoring | 8 privacy-safe signal groups; raw prompt/output logging forbidden |
| Retention boundary | paid API abuse monitoring may retain content up to 55 days; private grounding/logging/caching/file routes forbidden without separate approval |
| Runtime delta | 0 dependencies, 0 permissions, 0 credentials, 0 external inference calls, 0 production behavior changes |

## Decision

`documentation_complete_runtime_integration_not_authorized`.

The source produced measurable Soll value: it turns a broad privacy/safety
signal into a concrete routing matrix, safety baseline and monitoring contract.
No runtime integration is justified by this documentation-only task.

## Focused smoke

`GoogleAiDataSafetyDecisionKnowledgeTest` checks:

- exact task/source/raw trust trace;
- explicit separation of all three Google AI contours;
- allowed local, cloud-only and never-model data boundaries;
- current `safeForServer()` private-message gate;
- all 12 official evidence URLs;
- explicit Gemini API safety categories and baseline threshold;
- safety feedback, retention and privacy-safe monitoring controls;
- absence of new ML Kit GenAI, Firebase AI Logic and Google AI Edge runtime
  dependencies.

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.GoogleAiDataSafetyDecisionKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed, `0`
failures, `0` errors and `0` skipped.

## Value metric update

- `source_processing_result`:
  `official_google_ai_data_boundary_and_safety_decision_completed`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-db74e38e5609-d6c4aab5eaf02a1b-verification.md`
- `source_value`: `1` decision note; `12` official surfaces; `3` execution
  contours; `4` local data classes; `6` cloud-only capability classes; `11`
  safety controls; `8` monitoring signals; `1/1` focused contract test; `0`
  runtime dependencies, permissions, credentials, external inference calls or
  production behavior changes.
