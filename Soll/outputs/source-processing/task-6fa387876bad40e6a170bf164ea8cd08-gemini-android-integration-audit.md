---
task_id: 6fa387876bad40e6a170bf164ea8cd08
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/5e49e233280a
source_processing_result: official_docs_reviewed_integration_plan_ready
verification_artifact: Soll/outputs/source-processing/task-6fa387876bad40e6a170bf164ea8cd08-gemini-android-integration-audit.md
value_metric: "7 primary Google documentation surfaces reviewed; 2 distinct Android AI routes defined; 3 implementation phases and 8 measurable promotion gates documented; 0 dependencies, permissions, secrets or external model calls added"
verified_at: 2026-07-19 Europe/Chisinau
---

# Gemini Android integration plan audit

## Outcome

The task requested a review of Gemini API documentation for Soll app. The review found that the input
claim combined cloud Gemini Flash with on-device Gemini Nano. The corrected plan is documented in
`docs/knowledge/gemini-android-integration-plan-2026-07.md` and keeps those runtimes independent.

No provider SDK, permission, secret, model download or external inference call was added. This is
intentional: the acceptance criterion asks for a documented integration plan, while current runtime
evidence cannot prove native Gemini Nano support because ADB reports no connected devices.

## Repository facts

| Check | Observed result |
| --- | --- |
| Android minimum | `minSdk=26`, compatible with the documented ML Kit Prompt API minimum |
| Existing chat route | `ModelChatRequest.safeForServer()` -> `SollGateway.askModelChat(...)` |
| Existing local prototype | browser/web-host routing contract only; not an ML Kit adapter |
| Firebase baseline | BoM `34.15.0` plus Messaging; no `firebase-ai` or App Check dependency |
| Firebase configs | debug config present; release config absent |
| Device proof | ADB is available from the installed SDK, but reports no devices; measured Nano inference runs `0` |

## Documentation findings

1. Gemini Nano on Android is exposed through ML Kit GenAI/AICore, not the cloud Gemini model ID.
2. Prompt API currently requires API 26+, runtime capability checks and explicit handling of model download.
3. ML Kit GenAI is foreground-only and returns a dedicated error for background use.
4. Device/model support varies, so build success cannot replace a real `checkStatus()` canary.
5. Cloud Gemini in a mobile app requires abuse protection; Firebase recommends App Check, authenticated
   users, quotas, monitoring and Remote Config.
6. The Gemini 3 guide is already superseded; a model ID must be stable, configurable and canary-tested.
7. Private Soll messages must remain local or be blocked; a cloud fallback receives only `safeForServer()`.

## Promotion decision

- `P0`: native foreground adapter behind a disabled feature flag.
- `P1`: real compatible-device canary with privacy, latency, memory, thermal and quality evidence.
- `P2`: optional cloud adapter, preferably server-mediated; Firebase client access only after App Check and
  release configuration are ready.

The current task is complete as research/planning. Runtime implementation remains a separate task because
it changes dependencies and needs device/security evidence.

## Verification

Focused guard:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.GeminiAndroidIntegrationPlanTest" --console=plain
```

Expected contract:

- knowledge note and audit artifact both exist;
- on-device and cloud routes are explicitly separated;
- private cloud fallback, hidden downloads and background ML Kit calls remain forbidden;
- implementation remains gated by device and App Check evidence.

Observed result: `BUILD SUCCESSFUL`; the focused plan test and the existing `domain.modelchat`
compatibility package completed with 11 tests, 0 failures, 0 errors and 0 skipped.
