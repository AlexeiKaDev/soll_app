---
title: lm-evaluation-harness v0.4.11 result comparability note
task_id: 747be3f0cb7e4a079ab504cdb4de20a4
project: soll_app
source_ref: source-item/47425428d2cf/361bfe3d58165f4f
source_trust: untrusted_external_content
release: v0.4.11
review_status: knowledge_note_added_execution_deferred
reviewed_at: 2026-07-23 Europe/Chisinau
---

# lm-evaluation-harness v0.4.11 result comparability

## Decision

Treat lm-evaluation-harness v0.4.11 as a result-comparability boundary. Every
Soll evaluation receipt produced with this release must record both
`harness_version: 0.4.11` and the effective `task_version` for each task.

Do not compare directly against v0.4.10 results for these task families:

- `afrobench_belebele`;
- `evalita_llm`;
- `include`;
- `mgsm_direct`.

The monitored source signal reports task-version changes in those families.
Consequently, a cross-version score delta can mix model behavior with task,
configuration or data changes. Mark an existing v0.4.10 result as
`historical_not_comparable` until the same baseline workload is rerun with
v0.4.11 and the provenance fields below match. Do not publish a percentage
improvement or regression from an unmatched pair.

## Minimum result receipt

Record these eight provenance groups beside every v0.4.11 score:

1. harness package version and immutable environment/lock-file digest;
2. task name, effective `task_version` and task configuration digest;
3. dataset identifier, revision, subset and split;
4. prompt/template revision, few-shot count and few-shot source;
5. model and tokenizer identifiers and immutable revisions;
6. inference backend and its version;
7. generation arguments, batch settings, seeds and relevant limits;
8. operating system, accelerator, driver/runtime and scoring command.

A comparison is valid only when the intended experimental variable is named
and all other relevant receipt fields match. If a task does not expose a stable
version identifier, preserve the resolved task configuration and dataset
revision, label the receipt `task_version_unresolved`, and do not use it for a
cross-release claim.

## v0.4.10 to v0.4.11 upgrade check

For each of the four affected task families:

1. preserve the old receipt and raw aggregate output;
2. resolve and record the v0.4.11 task version before scoring;
3. rerun the baseline workload with v0.4.11 under the same model, prompt,
   generation and seed controls;
4. compare candidate and baseline only inside that pinned v0.4.11 environment;
5. retain per-sample identifiers or hashes so unexpected score movement can be
   attributed without retaining sensitive prompt content;
6. report unmatched historical scores separately, without a direct delta.

This note does not claim that old scores are wrong. It prevents them from being
presented as equivalent measurements after the evaluation definition changed.

## Optional local Windows ML smoke

The source signal also reports native Windows ML backend support. If Soll later
has a concrete desktop/server evaluation need, test it in a separate approved
task on a local Windows environment only. The smoke must:

- use a pinned v0.4.11 environment and a tiny local, non-sensitive fixture;
- start with backend discovery and one deterministic inference/scoring case;
- disable network access and perform no model or dataset downloads;
- read no credentials and write only to a task-owned temporary directory;
- perform no security or penetration testing;
- record backend/runtime versions, command, exit status, score and retained
  local logs;
- compare the local result only with a matching CPU/reference receipt, if one
  is available.

Stop on missing local runtime components instead of installing or downloading
them implicitly. Windows ML support is a desktop/server concern and must not be
added to the Android/Gradle runtime.

## Source trace and limits

- release pointer:
  <https://github.com/EleutherAI/lm-evaluation-harness/releases/tag/v0.4.11>;
- monitored capture identifier:
  `raw/monitored\lm-evaluation-harness-releases\20260709-235808-v0-4-11-daca52a1.md`;
- task source reference: `source-item/47425428d2cf/361bfe3d58165f4f`.

The raw monitored capture is not present in this isolated worktree. Its
task-supplied claims were treated as untrusted discovery metadata; this task
does not independently certify the release implementation or benchmark fixes.
No network request, harness installation, model evaluation, Windows ML backend
run, security test, Android runtime change or production write was performed.

## Value

The release signal produced **1** durable knowledge note, a hard comparability
guard for **4** task families, **8** required provenance groups and **1**
bounded offline Windows ML smoke contract. Actual evaluation runs: **0**.
Actual Windows ML backend runs: **0**. Measured model-quality improvement for
Soll: **0**.
