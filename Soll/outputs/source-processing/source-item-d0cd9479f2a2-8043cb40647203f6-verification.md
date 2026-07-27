---
task_id: 9c6ce128efae4e1b96a766b9b4e6e4f5
project: soll_app
source_ref: source-item/d0cd9479f2a2/8043cb40647203f6
source_item: llama-cpp-releases-b9922
source_processing_result: standalone_b10068_confirmed_b9922_included_no_current_recurrent_workload
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-8043cb40647203f6-verification.md
source_value: "1 Soll_app recurrent-tail KB note added; 4 official upstream surfaces and 6 current Soll seams audited; standalone b10068 confirmed 146 commits ahead of b9922; 6 future benchmark steps and 4 parallelism levels defined; 1/1 focused contract test passed; 0 production/runtime changes and 0 local model inference runs"
verified_at: 2026-07-27 Europe/Chisinau
---

# llama.cpp b9922 recurrent-tail applicability audit

## Outcome

llama.cpp/GGUF usage is confirmed in this repository as a standalone
verification contour, not as the Android production runtime. Its active
version is b10068, commit
`571d0d540df04f25298d0e159e520d9fc62ed121`. Checksummed Windows and Android
CPU archives are kept outside the APK, model loading is deny-by-default, and
Android chat remains backend-mediated through `POST api/v1/chat/turn` and
`soll-backend-route`.

PR #25278 and its exact b9922 merge commit are evaluated in
`docs/knowledge/llama-cpp-b9922-recurrent-tail-splitting.md`. b10068 is 146
commits ahead of b9922, 0 behind, with the exact b9922 commit as merge base, so
the active standalone release already includes `n_keep_tail`. A separate
b9922 pin or Android/runtime change would add no value.

There is no approved recurrent GGUF or local summarization/RAG adapter in this
worktree. The current tiny model is limited to three historical smoke uses, and
the active release smoke is version-only. Consequently the current measured
runtime value of n_keep_tail for Soll_app is `0`; no model inference or
performance claim is made.

The task-referenced raw monitored artifact is absent from this isolated
worktree. Its contents were not inferred. Release identity, PR metadata, diff
and b10068 ancestry were checked against official upstream surfaces.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | b9922 -> `230ea9d214320c5e79cc8166ed708ac60514c71e`, published 2026-07-08 |
| PR identity | #25278 merged 2026-07-08; 1 commit; head `ddaaa14341ed7ed4bf6f3465a260da7e2168a5db` |
| Upstream diff | 10 files, +102/-38; main allocator diff `src/llama-batch.cpp` +70/-4 |
| Functional delta | recurrent partial-rollback paths use equal splits while preserving the last `n_rs_seq + 1` tokens in one ubatch |
| Safety precondition | `n_ubatch > n_keep_tail`; ordinary KV-cache callers pass `0` |
| Android production seam | no direct llama.cpp/GGUF/JNI/CMake runtime marker; chat uses `POST api/v1/chat/turn` |
| Standalone seam | active b10068 commit `571d0d540df04f25298d0e159e520d9fc62ed121`; not packaged into Android |
| Active ancestry | b10068 is 146 commits ahead of b9922, 0 behind, with exact b9922 merge base |
| Model prerequisite | deny-by-default allowlist has no recurrent summarization/RAG approved use |
| Expected impact | possible parallel throughput/p95 latency improvement; no expected retrieval or output-quality change |
| Product change | KB note, audit artifact and focused repository contract test only |
| Runtime proof | 0 local model inference runs; no measured performance gain claimed |

## Summarization/RAG impact decision

The change is relevant only when a recurrent model uses partial rollback with
multiple parallel sequences. It can improve batching for parallel chunk
summaries or RAG branches without changing retrieval, prompts or sampling, but
the rollback-tail invariant must be verified structurally.

The KB note defines a six-step future model-backed benchmark with exact
pre/post builds, model provenance, ubatch-tail evidence, fixed summarization and
RAG fixtures, parallelism 1/2/4/8, five cold repeats, correctness/state checks,
latency/throughput/memory metrics and explicit promotion thresholds. It is a
future contract, not an unexecuted smoke claim.

## Focused smoke/audit artifact

`LlamaCppB9922RecurrentTailApplicabilityTest` guards:

- exact task, source, missing-raw boundary, release, PR, merge commit and parent;
- the 10-file +102/-38 diff and n_keep_tail/partial-rollback semantics;
- standalone b10068 identity, ancestry and non-packaging policy;
- the backend-mediated Android chat contract and absence of direct production
  llama.cpp/GGUF/native markers;
- deny-by-default model provenance and absence of a recurrent workload;
- the six-step future benchmark, quantified `source_value` and explicit `0`
  local model inference runs.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9922RecurrentTailApplicabilityTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `standalone_b10068_confirmed_b9922_included_no_current_recurrent_workload`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-8043cb40647203f6-verification.md`
- `source_value`: `1` Soll_app recurrent-tail KB note; `4` official upstream
  surfaces and `6` current Soll seams; standalone b10068 `146` commits ahead
  of b9922; `6` future benchmark steps and `4` parallelism levels; `1/1`
  focused contract test; `0` production/runtime changes and `0` local model
  inference runs.
