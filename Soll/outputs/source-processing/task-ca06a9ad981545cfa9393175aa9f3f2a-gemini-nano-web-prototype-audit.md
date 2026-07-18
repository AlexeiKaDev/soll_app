---
task_id: ca06a9ad981545cfa9393175aa9f3f2a
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/e99fdc8af909
source_signal: "AI features for web apps"
source_processing_result: soll_app_web_ai_routing_prototype_prepared
verification_artifact: Soll/outputs/source-processing/task-ca06a9ad981545cfa9393175aa9f3f2a-gemini-nano-web-prototype-audit.md
value_metric: "1 dependency-free routing prototype added; 3 capability probes and 5 route outcomes defined; 7/7 focused scenarios passed; 0 new dependencies, permissions, model downloads or external calls"
verified_at: 2026-07-19 Europe/Chisinau
---

# Gemini Nano / built-in web AI prototype audit

## Outcome

A bounded Soll app prototype is implemented at
`app/src/main/java/com/soll/domain/modelchat/GeminiNanoWebPrototype.kt`. It
turns a web host's observed built-in AI capability state into a deterministic
local/download-consent/server/blocked routing decision.

This is the smallest integration slice supported by the current repository.
Soll app is a native Android/Compose app with no web host, so the prototype does
not claim that an Android `WebView` exposes a browser built-in AI API. It adds no
SDK or UI, starts no model download, contacts no external service and preserves
the existing backend-mediated `SollGateway.askModelChat(...)` contract.

## Focused audit

| Check | Observed result |
| --- | --- |
| Required base | task supplied `365b6166507f3755b5982e1839c2b9e2ffc629d9` |
| Initial worktree | clean before the slice |
| Capability surface | `PROMPT`, `SUMMARIZE`, `REWRITE` |
| Availability states | `READY`, `DOWNLOADABLE`, `UNAVAILABLE`, `UNKNOWN` |
| Route outcomes | on-device, on-device after consented download, consent required, sanitized server fallback, private fallback blocked |
| Existing architecture | reuses `ModelChatRequest.safeForServer()`; no public gateway/repository contract changed |
| Privacy gate | unavailable or unknown local capability never emits a server payload for a request containing private messages |
| Download gate | `DOWNLOADABLE` cannot choose a download route unless `allowModelDownload=true` |
| Structural guard | non-server routes reject an attached `serverRequest` |
| Dependency/permission delta | `0` / `0` |
| Model downloads/external calls | `0` / `0` |

The host integration contract and promotion boundary are documented in
`docs/knowledge/gemini-nano-web-ai-prototype.md`.

## Focused smoke result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.domain.modelchat.GeminiNanoWebPrototypeTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`). The JUnit result reports
`tests=7`, `failures=0`, `errors=0`, `skipped=0`.

Covered scenarios:

1. ready prompt keeps private content on-device;
2. the requested capability uses its own availability probe;
3. downloadable model requires explicit consent;
4. explicit consent selects the post-download local route;
5. unavailable capability sanitizes a public server fallback;
6. unknown capability blocks a private server fallback;
7. local decisions reject any attached server payload.

The build emitted only pre-existing deprecation warnings in unrelated device
and push-token code.

Compatibility command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.domain.modelchat.*" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); the package result reports
`tests=10`, `failures=0`, `errors=0`, `skipped=0`, including the three existing
`ModelChatModelsTest` scenarios.

## Value metric update

- routing prototypes prepared: `1`;
- capability probes represented: `3`;
- deterministic route outcomes represented: `5`;
- focused scenarios passed: `7/7`;
- new runtime dependencies: `0`;
- new Android permissions: `0`;
- model downloads and external calls performed: `0`;
- measured Android Gemini Nano inference runs: `0`.

The observed value is an executable routing/privacy/consent contract that a
real Soll web host can implement and smoke-test. Runtime inference value remains
unmeasured until such a host exists and proves local processing without a
network request carrying user text.
