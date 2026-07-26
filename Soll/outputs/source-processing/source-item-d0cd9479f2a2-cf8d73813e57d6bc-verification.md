---
task_id: bfafbcef4fff4702b3ce03ba57352158
project: soll_app
source_ref: source-item/d0cd9479f2a2/cf8d73813e57d6bc
source_item: llama-cpp-releases-b9923
source_processing_result: llama_cpp_b9923_sse_audit_completed_no_runtime_seam
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-cf8d73813e57d6bc-verification.md
source_value: audit_only_no_current_llama_server_sse_regression_surface
value_metric: "301 Android production source/config files scanned; 8 direct llama-server/SSE markers produced 0 matches; 6 upstream PR files and 5 local execution seams audited; active b10068 verified 145 commits ahead of b9923 with 0 behind; 1/1 focused contract test passed; 0 production/runtime changes, 0 local SSE requests, and 0 public listeners"
verified_at: 2026-07-26 Europe/Chisinau
---

# llama.cpp b9923 server streaming/SSE applicability audit

## Outcome

Soll_app does not currently use the llama.cpp server streaming/SSE replay
surface. The Android model-chat provider hint `LLAMA` is data sent through the
ordinary Soll backend request `POST api/v1/assistant/ask`; it does not select a
direct `llama-server` transport in the app. The standalone llama.cpp tooling
downloads checksummed release archives for explicit verification, but its active
release check invokes `llama-server` only with `--version`.

Because the objective makes the regression smoke conditional on actual
llama.cpp server streaming/SSE use, no executable SSE client, server launcher,
runtime dependency, or public listener was added. A loopback-only smoke would
exercise an upstream surface that has no current Soll execution seam and would
not protect a Soll product contract.

## Source and upstream verification

The requested raw item
`raw/monitored/llama-cpp-releases/20260708-223009-b9923-674220a4.md` is not
vendored in this isolated worktree. The release and PR were therefore checked
against the official upstream GitHub release and API metadata:

- release: <https://github.com/ggml-org/llama.cpp/releases/tag/b9923>;
- release/merge commit:
  `bbebeec4a87355896e3faac0c2baca8130c91b6a`, published 2026-07-08;
- PR: <https://github.com/ggml-org/llama.cpp/pull/25047>;
- PR head: `c6c84644213fa8e9eab84fcb9e2251963988af51`;
- scope: 6 files, 180 additions and 193 deletions.

PR #25047 describes itself as a follow-up to the SSE Replay Buffer in #23226
with "No functional change to the resumable stream behavior." Its relevant
changes are:

1. `stream_session`, `stream_session_manager`, the consumer type, and
   `stream_read_status` move behind the `server-stream.cpp` implementation
   boundary.
2. Public free functions receive the `server_stream_` prefix; the process-wide
   manager becomes a file-static singleton controlled through
   `server_stream_session_manager_start/stop`.
3. Session `done`/`completed_ts` and manager `running` state become plain
   members guarded by their mutexes; condition-variable predicates are changed
   while holding the matching lock. Only cancellation stays atomic for the
   lock-free stop poll.
4. Bring-up logging for drain, attach, DELETE and router resume is lowered from
   default-info visibility to debug/trace visibility.
5. Comments and developer documentation are aligned with the actual lifetime,
   locking and route surface.

The touched files contain no upstream regression test. The change is still
relevant to streaming response, reconnect/resume, cancel and session cleanup
because it refactors their shared buffer/session lifecycle. The currently pinned
standalone Soll baseline `b10068` contains the b9923 merge commit: GitHub compare
reports it 145 commits ahead and 0 behind.

## Soll/Soll_app usage proof

| Seam | Repository fact | SSE conclusion |
| --- | --- | --- |
| Android model chat | `ModelChatProviderHint.LLAMA` is serialized by `ModelChatServerBridge`; `SollRepository.askModelChat()` calls `service().askAssistant()` | Provider selection is backend-mediated, not a direct llama.cpp HTTP client |
| Android API | `SollApiService.askAssistant()` is Retrofit `POST api/v1/assistant/ask` and returns one `AssistantAskResponse` | No SSE response body or resumable-stream contract |
| Production scan | 301 Kotlin/KTS/XML/Java/C/C++/header files under `app/src/main` were checked | 0 matches for `text/event-stream`, `EventSource`, `Last-Event-ID`, `X-Conversation-Id`, `/v1/stream`, `/v1/streams`, `/v1/chat/completions`, or `llama-server` |
| Native packaging | 0 llama/ggml-named app files, 0 app `CMakeLists.txt`, and 0 files under `app/src/main/jniLibs` | No packaged llama.cpp server/runtime |
| Standalone policy | `llama_cpp_active_defaults.json` pins b10068, keeps `packageIntoAndroidApp: false`, and sets `androidRuntimeDefault: soll-backend-route` | Standalone binaries remain verification-only |
| Standalone server execution | `Test-LlamaCppActiveRelease.ps1` runs `llama-server --version` once and contains 0 `--host` flags | The current verifier never starts an HTTP/SSE listener |

## Regression-smoke safety assessment

An executable smoke is not justified until a Soll-owned client or service starts
using the llama.cpp resumable stream contract. If that seam is introduced, the
minimum safe smoke should run only in an isolated test process and cover:

1. **streaming response** — start the exact checksummed approved build/model,
   bind only to `127.0.0.1` on a test-reserved port (`0.0.0.0` is prohibited),
   send `stream=true` with a unique `X-Conversation-Id`, and require ordered SSE
   data plus the terminal event;
2. **reconnect/resume** — disconnect after a recorded byte offset, reconnect to
   `GET /v1/stream/<conversation_id>?from=<offset>`, and prove the reconstructed
   stream has neither a gap nor duplicate bytes;
3. **cancel** — issue idempotent
   `DELETE /v1/stream/<conversation_id>`, require HTTP 204, and prove generation
   stops instead of continuing in the background;
4. **session cleanup** — use a fresh conversation id per case, verify lookup no
   longer returns an explicitly deleted session, delete in `finally`, stop and
   await the child server, confirm the loopback port closes, and retain no model
   or response artifact outside the ignored build cache.

Promotion gates are: a real Soll execution seam, exact release/model
provenance, loopback-only binding, deterministic timeouts, guaranteed
process/session cleanup, and a focused test that fails against a deliberately
broken replay/cancel lifecycle. Until those gates exist, adding a network smoke
would increase maintenance and download/runtime cost without measurable Soll
regression coverage.

## Focused verification

`LlamaCppB9923SseApplicabilityAuditTest` guards the release/PR identity, the
five local seams, all eight absent runtime markers, the no-public-listener
decision, and the four-part future smoke contract.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9923SseApplicabilityAuditTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `llama_cpp_b9923_sse_audit_completed_no_runtime_seam`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-cf8d73813e57d6bc-verification.md`;
- `source_value`: `audit_only_no_current_llama_server_sse_regression_surface`;
- `value_metric`: 301 Android production source/config files scanned; 8 direct
  SSE/server markers produced 0 matches; 6 upstream files and 5 local seams
  audited; active b10068 verified 145 commits ahead of b9923 with 0 behind; 1/1
  focused test passed; 0 production/runtime changes, 0 local SSE requests and 0
  public listeners.
