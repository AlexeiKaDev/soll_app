---
task_id: bee0b2b4e7fb4baa9be6a2496e05e34d
project: soll_app
source_ref: source-item/d0cd9479f2a2/01c5f16863b3ae97
source_processing_result: llama_cpp_b9947_output_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-01c5f16863b3ae97-verification.md
source_value: accepted
value_metric: "1 merged upstream PR reviewed; 1/1 safe local --output inference passed on pinned b10068; 81-byte transcript persisted without stdout parsing; 0 APK/runtime-route changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# llama.cpp b9947 `--output` verification

## Result

PR [#25484](https://github.com/ggml-org/llama.cpp/pull/25484) was merged on
2026-07-09 as the verified commit
[`3de7dd4c8f5d9806279249310b6c3db24a1a67ab`](https://github.com/ggml-org/llama.cpp/commit/3de7dd4c8f5d9806279249310b6c3db24a1a67ab).
It changed three files (`+60/-8`) and exposed the existing common `--output`
argument to `llama-cli`. The release
[`b9947`](https://github.com/ggml-org/llama.cpp/releases/tag/b9947) points to
that commit and publishes 27 assets, including Windows x64/arm64, Linux
x64/arm64/s390x, Android arm64, macOS arm64/x64 and iOS builds.

Soll already pins its standalone verification baseline to llama.cpp `b10068`,
commit `571d0d540df04f25298d0e159e520d9fc62ed121`. The official comparison reports
b10068 as `121` commits ahead and `0` behind b9947, so no release downgrade or
second binary manifest is needed.

The monitored raw path
`raw/monitored\llama-cpp-releases\20260711-001152-b9947-988e8956.md` was not
vendored in this isolated worktree. Release identity, merge state and the
implementation diff were checked against the official GitHub release, PR,
commit and compare surfaces.

## PR review

- Initialization opens the requested path before the CLI run and fails cleanly
  if the file cannot be opened.
- The implementation flushes each transcript write and closes the stream during
  shutdown.
- The file records `User:` and `Assistant:` blocks; reasoning, when present, is
  wrapped in human-readable `[Start thinking]` / `[End thinking]` markers.
- Upstream explicitly describes the format as human-readable and leaves future
  downstream formats TBD. Soll should therefore retain the result as an opaque
  inference artifact, not treat it as a stable machine-readable schema.
- The one review thread concerned whether reasoning and tool-call content
  belongs in conversation history; it does not invalidate file persistence, but
  supports keeping any future ingestion parser out of this task.

## Focused implementation

- `minimumOutputFileRelease: 9947` records the feature floor while keeping the
  active b10068 security baseline.
- The existing checksummed, immutable tiny GGUF fixture now explicitly permits
  the narrow `b9947-output-file-smoke` use. The allowlist remains
  `deny_unlisted`.
- `Test-LlamaCppB9947Output.ps1` verifies the pinned release archive and model
  provenance, runs exactly one deterministic token on an offline harmless
  prompt, and validates the requested file directly.
- The script deliberately leaves native stdout/stderr unredirected and reports
  `stdoutParsed: false`; success depends only on exit status and the file's
  `User:` / `Assistant:` structure.
- The output remains under ignored `build/llama-b10068/output-smoke/`. No GGUF
  or llama.cpp binary is packaged into the Android app, and
  `soll-backend-route` remains the runtime default.

## Executed verification

The focused PowerShell smoke reported:

- release: `b10068` / `571d0d540df04f25298d0e159e520d9fc62ed121`;
- model: `stories15M-q8_0.gguf`, `26,671,328` bytes,
  SHA-256 `2eda49203f2f044f3dddf29a7dd7cc861ef5a0340f518a19613d73ba6d9c06b6`;
- model revision: `def3e2dd70df35ecbf6403ea347de4c5977220c1`;
- safe prompt: `Soll output smoke: harmless local inference artifact.`;
- output: `build/llama-b10068/output-smoke/llama-cli-output.txt`, `81` bytes,
  SHA-256 `d186d2853012165bf04a1a385cbe795339f57dbb840b9d0925a4472c770a2c47`;
- `stdoutParsed: false`;
- `userPromptPersisted: true`;
- `assistantContentPersisted: true`;
- `exitCode: 0`.

The new `LlamaCppB9947OutputSmokeTest` and five directly affected llama.cpp JVM
contract tests passed. The full `:app:testDebugUnitTest` suite also passed:
`420/420` tests passed, `0` failures, `0` errors and `0` skipped.
`git diff --check` reported no whitespace errors.

## Value metric update

- `source_processing_result`: `llama_cpp_b9947_output_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-01c5f16863b3ae97-verification.md`;
- `source_value`: `accepted`;
- `value_metric`: `1` merged upstream PR reviewed; `1/1` safe local
  `--output` inference passed on pinned b10068; `81`-byte transcript persisted
  without stdout parsing; `0` APK/runtime-route changes.
