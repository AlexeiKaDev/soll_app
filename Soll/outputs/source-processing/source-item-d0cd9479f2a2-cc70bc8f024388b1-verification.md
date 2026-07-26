---
task_id: 16b9095f66114292925ca44a66ccf142
project: soll_app
source_ref: source-item/d0cd9479f2a2/cc70bc8f024388b1
source_item: llama-cpp-releases-b9936
source_processing_result: min_step_prompt_batch_fix_verified_no_current_direct_server_execution_seam
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-cc70bc8f024388b1-verification.md
source_value: "1 Soll_app min-step note added; 4 official upstream surfaces and 5 current Soll seams audited; b10068 verified 132 commits ahead of b9936; 6 future regression steps defined; 1/1 focused contract test passed; 0 production/runtime changes and 0 local model inference runs"
verified_at: 2026-07-24 Europe/Chisinau
---

# llama.cpp b9936 min-step prompt-batch audit

## Outcome

The required PR and server diff are verified in
`docs/knowledge/llama-cpp-b9936-min-step-prompt-batch-splitting.md`.
PR #25420 changed one file, `tools/server/server-context.cpp`, so prompt
batches no longer break at every intermediate user-message boundary when the
resulting checkpoint would violate `checkpoint_min_step`.

The change is relevant to future local multi-turn `llama-server` completion
workloads with context checkpoints. It does not execute in the current
Soll_app Android path or release smoke: Android uses `POST api/v1/chat/turn`
through `soll-backend-route`, llama.cpp is not packaged into the APK, and the
standalone smoke invokes `llama-server --version`. Active b10068 already
contains exact b9936 commit and is 132 commits ahead, 0 behind.

The task-referenced raw monitored artifact is absent from this isolated
worktree. No unverified details were attributed to it; the release, PR, commit
diff and compare result were checked against official upstream surfaces.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | b9936 -> `64c8b7db72fbd871512b371b5c141c00fd0a8ba6`, published 2026-07-09 |
| PR identity | #25420 merged 2026-07-09; 1 commit; 25 checks passed |
| Server diff | `tools/server/server-context.cpp`, +8/-3 |
| Functional delta | intermediate user boundary breaks only when no checkpoint exists or `pos > last checkpoint + min-step`; last user still breaks |
| Default threshold | `--checkpoint-min-step 256`; `0` disables the minimum |
| Android seam | `SollApiService` uses `POST api/v1/chat/turn`, not direct llama-server HTTP |
| Standalone seam | b10068 CPU archives are not packaged into Android; server smoke is version-only |
| Active ancestry | b10068 is 132 commits ahead of b9936, 0 behind, with exact b9936 merge base |
| Model prerequisite | no allowlisted server/model regression fixture |
| Product change | knowledge note, audit artifact and focused contract test only |
| Runtime proof | 0 local model inference runs; no prefill-speed claim |

## Regression contract

The KB note defines a six-step future smoke using the b9936 parent and fixed
candidate, exact tokenized boundaries around min-step, active checkpoint debug
evidence, decode-batch/checkpoint inspection, five cold-prefill repeats and a
fixed-seed correctness comparison. The structural pass condition checks both
`pos <= checkpoint + 256` and `pos > checkpoint + 256`; timing alone cannot
turn a structurally wrong run into PASS.

No server/model run was attempted because the current deny-by-default allowlist
has no approved use for this server regression. This is a documented future
test, not an unexecuted test claim.

## Focused smoke/audit artifact

`LlamaCppB9936MinStepKnowledgeTest` guards:

- exact task, source, missing-raw boundary, release, PR, merge commit and parent;
- the one-file +8/-3 diff and all three new split conditions;
- the min-step boundary cases and six-step future regression contract;
- active b10068 ancestry and unchanged Android/standalone policies;
- version-only server smoke and absence of an approved server/model fixture;
- quantified `source_value` and explicit `0` local model inference runs.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9936MinStepKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `min_step_prompt_batch_fix_verified_no_current_direct_server_execution_seam`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-cc70bc8f024388b1-verification.md`
- `source_value`: `1` Soll_app min-step note; `4` official upstream surfaces;
  `5` current Soll seams; b10068 `132` commits ahead of b9936; `6` future
  regression steps; `1/1` focused contract test; `0` production/runtime
  changes and `0` local model inference runs.
