---
task_id: 0c7839e80ae54776831aadcfc7e3c172
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/d1d74e584f16
source_item: llama-cpp-releases-b9960
priority: low
task_board_translation: reject
source_processing_result: rejected_low_priority_no_current_soll_execution_seam
verification_artifact: Soll/outputs/source-processing/task-0c7839e80ae54776831aadcfc7e3c172-llama-cpp-b9960-audit.md
value_metric: "1 low-priority reject decision recorded; 3 official upstream surfaces and 4 current Soll seams audited; active b10068 verified 108 commits ahead; 0 applicable Soll production paths, 0 production/runtime changes and 0 measured runtime value"
verified_at: 2026-07-26 Europe/Chisinau
---

# llama.cpp b9960 low-priority decision audit

## Decision

**Reject for current implementation; priority `low`.** b9960 only changes the
embedded `llama-server` web UI while a model is loading. Soll app does not
serve or embed that UI: Android chat stays behind `POST api/v1/chat/turn`,
the standalone policy keeps `soll-backend-route` as the runtime default, and
llama.cpp binaries are not packaged into the APK. The source signal therefore
has no current product execution seam or measurable runtime value.

The only safe follow-up is conditional monitoring: reopen this decision if Soll
later owns an operator-facing embedded llama-server web UI and needs its
pre-readiness `503` experience. This is not an open implementation action.

## Source boundary

The requested `wiki/b9960.md` and `daily/2026-07-25.md` are absent from the
isolated Base SHA. No unavailable article content is inferred. The release
classification was reconstructed from three official upstream surfaces:

- [release b9960](https://github.com/ggml-org/llama.cpp/releases/tag/b9960);
- [commit `a935fbffe1a3d31509c325c116454ab5d56b2eb8`](https://github.com/ggml-org/llama.cpp/commit/a935fbffe1a3d31509c325c116454ab5d56b2eb8);
- [PR #25500](https://github.com/ggml-org/llama.cpp/pull/25500).

The release was published on 2026-07-11. Its patch changes 9 server/UI files
(`99` additions, `60` deletions), deletes `tools/ui/static/loading.html`, lets
frontend assets load while the server is not ready, and moves the loading
experience into the cached Svelte UI. The UI detects HTTP `503`, displays
`Loading model`, and retries server properties every `1000 ms`. It does not
change model inference, quantization, tokenizer behavior, the completion API,
or an Android application contract.

## Focused repository audit

| Check | Observed result |
| --- | --- |
| Android transport | `SollApiService` owns `POST api/v1/chat/turn`; it does not expose llama-server UI routes |
| Android production source | `0` exact `loading.html` or `llama_ui_get_assets` matches under `app/src/main` |
| Runtime policy | `packageIntoAndroidApp=false`; `androidRuntimeDefault=soll-backend-route` |
| Active standalone baseline | b10068 commit `571d0d540df04f25298d0e159e520d9fc62ed121` is `108` commits ahead of b9960 and `0` behind |
| Applicable production paths | `0` |
| Runtime proof required | none: the affected web UI is not a Soll-owned runtime surface |

## Task Board translation

- status: `reject`
- priority: `low`
- brief justification: embedded llama-server loading UI is outside the current
  Soll Android/backend contract, and the active standalone baseline already
  contains b9960;
- next step: none; reopen only when an embedded llama-server web UI becomes an
  owned and user-visible Soll surface.

## Focused smoke/audit

`LlamaCppB9960LowPriorityDecisionTest` guards the task/source identity,
low-priority reject, upstream scope, current API and runtime-policy seams,
missing-source boundary, and quantified value metric.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9960LowPriorityDecisionTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; JUnit reports `1/1` focused test passed
with `0` failures, `0` errors and `0` skipped tests.

## Value metric update

- low-priority reject decisions recorded: `1`;
- official upstream surfaces audited: `3`;
- current Soll seams audited: `4`;
- active baseline distance from b9960: `108` commits ahead, `0` behind;
- applicable Soll production paths: `0`;
- production/runtime files changed: `0`;
- measured b9960 runtime value for Soll app: `0`.
