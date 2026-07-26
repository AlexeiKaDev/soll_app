---
task_id: c5f1f18e21e8489eb4b55e6dce49fbaf
project: soll_app
source_ref: source-item/d0cd9479f2a2/2e41768d5606f508
source_processing_result: llama_cpp_b9945_chat_template_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-2e41768d5606f508-verification.md
source_value: accepted
value_metric: "1 approved 26,671,328-byte test-only GGUF; 1 non-standard Jinja template applied; 1/1 model load passed on pinned b10068 with exit code 0; 0 APK/runtime-route changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# llama.cpp b9945 chat-template verification

## Result

Soll pins the standalone verification baseline to llama.cpp `b10068`, commit
`571d0d540df04f25298d0e159e520d9fc62ed121`. This is newer than `b9945`, so no
release update is needed. The b9945 fix commit
`82fce65d8be40ba55048e06f2e14a01deb363d41` moves the chat-template thinking
probe into the guarded initialization path so an apply-time parser-generation
error returns cleanly instead of escaping as `SIGABRT`.
The official compare reports b10068 as `123` commits ahead and `0` behind
b9945, with the b9945 fix itself as the merge base.

The monitored raw path
`raw/monitored\llama-cpp-releases\20260711-001152-b9945-e5e7e491.md` was not
vendored in this isolated worktree. Release identity and behavior were checked
against the official release, exact commit and merged PR:

- <https://github.com/ggml-org/llama.cpp/releases/tag/b9945>
- <https://github.com/ggml-org/llama.cpp/commit/82fce65d8be40ba55048e06f2e14a01deb363d41>
- <https://github.com/ggml-org/llama.cpp/pull/24093>

## Focused implementation

- `minimumChatTemplateFixRelease: 9945` makes the required fix floor explicit.
- `approved_models.json` remains deny-by-default and approves exactly one
  `ggml-org/tiny-llamas` Q8_0 fixture by immutable revision, exact byte count
  and SHA-256 for this smoke only.
- `soll-nonstandard-chat-template.jinja` uses unique `<|soll_*|>` markers, so
  it is not one of llama.cpp's named built-in templates.
- `Test-LlamaCppB9945ChatTemplate.ps1` checks the pinned archive, model
  provenance and template before a one-token single-turn run through the pinned
  CLI at diagnostic verbosity.
- The fixture and release archive remain in the ignored `build/` cache. No
  GGUF or llama.cpp binary is packaged into the Android app, and
  `soll-backend-route` remains the runtime default.

## Executed verification

The focused PowerShell smoke reported:

- release: `b10068` / `571d0d540df04f25298d0e159e520d9fc62ed121`;
- model: `stories15M-q8_0.gguf`, `26,671,328` bytes,
  SHA-256 `2eda49203f2f044f3dddf29a7dd7cc861ef5a0340f518a19613d73ba6d9c06b6`;
- model revision: `def3e2dd70df35ecbf6403ea347de4c5977220c1`;
- template SHA-256:
  `2240bfec23b1d633ee5212fbd1f3546621d3b878930641dd03c37179488771d2`;
- `modelLoaded: true`;
- `nonStandardTemplateApplied: true`;
- `exitCode: 0`.

The new `LlamaCppB9945ChatTemplateSmokeTest` and four directly affected JVM
contract tests passed. The full `:app:testDebugUnitTest` suite also passed:
`419/419` tests passed, `0` failures, `0` errors and `0` skipped.
`git diff --check` reported no whitespace errors.

## Value metric update

- `source_processing_result`:
  `llama_cpp_b9945_chat_template_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-2e41768d5606f508-verification.md`;
- `source_value`: `accepted`;
- `value_metric`: `1` approved `26,671,328`-byte test-only GGUF; `1`
  non-standard Jinja template applied; `1/1` model load passed on pinned
  b10068 with exit code `0`; `0` APK/runtime-route changes.
