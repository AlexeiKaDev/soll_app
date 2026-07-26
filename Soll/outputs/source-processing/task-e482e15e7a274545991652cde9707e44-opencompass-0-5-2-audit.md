---
task_id: e482e15e7a274545991652cde9707e44
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/6cd00d7bc573
source_item: opencompass-releases-0-5-2
source_processing_result: compatibility_validated_server_eval_shortlist_defined
verification_artifact: Soll/outputs/source-processing/task-e482e15e7a274545991652cde9707e44-opencompass-0-5-2-audit.md
value_metric: "14 release benchmark directions and 3 cross-cutting metrics audited; 3 Soll pilot candidates shortlisted, 2 retained as conditional signals, and 9 deferred with no current Soll scenario; 1/1 focused contract test passed; 0 Android dependencies, 0 external model runs, and 0 production changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# OpenCompass 0.5.2 compatibility and Soll applicability audit

## Outcome

OpenCompass 0.5.2 is compatible with Soll only as an isolated desktop/server
evaluation tool, not as an Android runtime dependency. Its Python evaluation
environment, model or API adapters, dataset acquisition and optional GPU
backends belong outside `soll_app`. The Android app should continue to consume
reviewed results through its existing Soll API surfaces.

The release signal still produces measurable design value. All 14 benchmark
additions named by the 0.5.2 release were classified in
`docs/knowledge/opencompass-0-5-2-soll-applicability-v1.json`: 3 are suitable
for a bounded Soll-shaped pilot, 2 are conditional model-selection signals, and
9 have no current Soll workload or validated gold set. No package, model,
dataset or API credential was imported or executed.

The monitored source
`monitored/opencompass-releases/20260710-000042-0-5-2-824ab587.md` is not
vendored in this isolated worktree. It was treated only as an untrusted source
identifier. Release identity and scope were checked against the official
OpenCompass release and project documentation:

- <https://github.com/open-compass/opencompass/releases/tag/0.5.2>
- <https://github.com/open-compass/opencompass/commit/974179240a1a4e3c0ff14c60621cf1f6c95b287a>
- <https://opencompass.readthedocs.io/en/stable/get_started/installation.html>
- <https://opencompass.readthedocs.io/en/stable/get_started/quick_start.html>

The official release page now lists 0.5.3 after 0.5.2. Therefore even a future
pilot must review and pin its then-current tool version separately; this task
preserves the 0.5.2 benchmark signal, not an old production dependency.

## Compatibility audit

| Boundary | Observed result | Decision |
| --- | --- | --- |
| Runtime | OpenCompass is a Python evaluation platform with separate model/API and optional acceleration environments | keep outside the Android/Gradle runtime |
| Android transport | Soll uses `POST /api/v1/chat/turn` and `GET /api/v1/android/sync-status` | preserve the public contract; return reviewed summaries only |
| Assistant memory | `AssistantMemory` stores preferences, commands, tools and device/system records | adapt PI-LLM latest-value interference cases offline |
| Existing eval seam | `docs/knowledge/soll-source-monitoring-kb-eval-v1.json` already defines synthetic gold outputs and zero-side-effect scoring | extend a server/desktop harness, not the APK |
| Release telemetry | 0.5.2 adds output length, logprobs and finish reasons | collect server-side when available; do not expose chain-of-thought |
| Current DTO | `ChatTurnResponse` has message, assistant and task intake, but none of those 3 telemetry fields | no Android contract change for an eval-only signal |
| Current integration | 0 OpenCompass Gradle dependencies and 0 in-app Python/model runner surfaces | retain zero direct dependencies |

## Applicability to Soll scenarios

### Pilot shortlist

1. `IFBench` is the strongest direct fit. Adapt at least five synthetic chat,
   task-intake and structured-output prompts with objectively verifiable format,
   content and no-side-effect constraints. Measure `constraint_pass_rate` and
   require `1.0` for critical safety/approval constraints.
2. `PI-LLM` directly targets stale-value interference. Adapt at least five
   synthetic sequences where task status, source verdict, preference or device
   state is updated repeatedly, then query only the latest value. Measure
   `latest_value_exact_match` and `stale_value_recall_count`.
3. `LCB_pro` is a secondary fit for choosing a model behind an isolated Soll
   implementation worker. Use only non-sensitive standalone problems and
   retain compile/test evidence; its `pass@1` must not substitute for repository
   acceptance tests or human review.

### Conditional only

- `ARC_AGI_2` may become useful if a named abstract-reasoning failure blocks a
  Soll workflow. A general score currently has no task-level acceptance mapping.
- `ProcessBench` evaluates the location of errors in annotated mathematical
  reasoning. It must not be presented as validation of general agent plans,
  hidden chain-of-thought or tool trajectories; reopen only for a math-critic
  workload with matching gold annotations.

### No current scenario

`HMMT2025`, `AMO-Bench`, `IMO-Bench`, `ATLAS`, `OpenSWI`, `CMPhysBench`,
`Biology Instructions`, `Mol Instructions` and `SciReasoner` are deferred.
Soll currently owns no corresponding competition-math, scientific expert,
geophysical inversion, multi-omics, molecular or condensed-matter workload and
no validated domain gold set. Importing them would measure unrelated capability
without observable product value.

## Safe pilot contract

A separately approved pilot may run only in an isolated desktop/server
environment with synthetic or non-sensitive fixtures. Scoring must disable
network access and persistent writes, read no provider credentials, compare a
pinned candidate against a current baseline, retain per-case outputs and require
manual review. Required metrics are declared in the JSON matrix, including task
success, constraint compliance, latest-value retrieval, stale recall, pass@1,
output length, finish-reason distribution and unsafe side-effect count.

Promotion requires all safety assertions to pass and
`unsafe_side_effect_count = 0`. The pilot may not change the Android contract.
Logprobs are diagnostic-only because provider support is not universal; hidden
reasoning text must not be collected or surfaced.

## Focused smoke/audit artifact

`OpenCompass052SollApplicabilityTest` guards:

- exact task, source, release version and full release commit;
- a complete, unique 14-benchmark matrix and the `3 / 2 / 9` classification;
- the three shortlisted Soll scenarios and eight required pilot metrics;
- server-only placement, synthetic/no-side-effect controls and unchanged
  Android contracts/dependencies;
- absence of output-length, logprob and finish-reason fields from the current
  `ChatTurnResponse` DTO;
- the quantified `value_metric` and zero external model/production runs.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.OpenCompass052SollApplicabilityTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `compatibility_validated_server_eval_shortlist_defined`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-e482e15e7a274545991652cde9707e44-opencompass-0-5-2-audit.md`
- `value_metric`: `14` release benchmark directions and `3` cross-cutting
  metrics audited; `3` Soll pilot candidates shortlisted, `2` retained as
  conditional signals, and `9` deferred with no current Soll scenario; `1/1`
  focused contract test passed; `0` Android dependencies, `0` external model
  runs, and `0` production changes.
