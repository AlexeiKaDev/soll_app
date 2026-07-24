---
title: Google Tunix agentic RL applicability to Soll
task_id: 810c7bbad21a46d0a3094cc767e22477
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/3223b4da61f2
source_trust: untrusted_external_content
section: LLM/post-training
review_status: applicable_to_bounded_server_experiment
reviewed_at: 2026-07-24 Europe/Chisinau
---

# Google Tunix agentic RL applicability to Soll

## Decision

**Applicable to Soll app workflow**, but only as a framework candidate for a
future, separately approved desktop/server agent-training experiment. The fit
is the existing Soll evaluation and post-training contour for multi-turn,
tool-using agents. It is not an Android runtime dependency, an inference
provider, or evidence of a current hardware-efficiency improvement.

Tunix should enter a framework-selection smoke only when Soll owns an approved
multi-turn training workload and has access to a pinned JAX/TPU environment.
Until those prerequisites exist, the current server-mediated inference route
and deterministic evaluation fixtures remain unchanged.

## Source and trust boundary

- monitored source:
  `monitored/google-developers-blog/20260723-223002-scaling-agentic-rl-high-throughput-agentic-train-28c30f53.md`;
- task source reference: `insight/3223b4da61f2`;
- official article:
  `https://developers.googleblog.com/scaling-agentic-rl-high-throughput-agentic-training-with-tunix/`;
- official project: `https://github.com/google/tunix`;
- article date observed on the official page: 21 July 2026.

The monitored snapshot is absent from this isolated worktree. Its task payload
was treated as untrusted metadata. The four capability groups below were
checked read-only against the official Google article on 24 July 2026. No
upstream code, recipe, dataset, model, environment, dependency or performance
claim was copied into Soll.

## Four relevant capability groups

1. **Asynchronous rollouts.** `RolloutOrchestrator` overlaps model sampling
   with host-side environment, tool and reward waits instead of making an
   accelerator wait for the slowest trajectory.
2. **Barrier-free training pipeline.** A producer/consumer queue decouples
   variable-length trajectory generation from `AgenticRLLearner`; completed
   groups can feed the trainer without waiting for a whole synchronous batch.
3. **Composable agent and environment boundary.** Agent classes and
   `TaskEnvironment`, `ToolEnvironment` or `BaseTaskEnv` separate multi-turn
   interaction logic from the trainer, which is useful for a disposable
   synthetic Soll environment.
4. **Continuous RL pipeline telemetry.** Lightweight stage metrics expose
   rollout, environment, training and weight-sync stalls so an experiment can
   test hardware utilization rather than inherit an upstream throughput claim.

These are candidate capabilities, not verified Soll behavior. In particular,
the article targets JAX/TPU training and mentions serving integrations that are
not present in this Android repository.

## Current Soll fit

Four current seams were audited:

| Soll seam | Observed boundary | Tunix decision |
| --- | --- | --- |
| `app/build.gradle.kts` | Android has no Tunix, JAX or Python trainer dependency | preserve zero direct Android training dependencies |
| `SollGateway.askModelChat(...)` | Android uses backend-mediated inference; it does not own rollouts, rewards or training | keep model training and credentials on an isolated desktop/server worker |
| `docs/knowledge/soll-source-monitoring-kb-eval-v1.json` | eight synthetic, no-side-effect evaluation cases already define task and safety scoring | use only as a held-out evaluation gate; do not silently convert cases into training data |
| `docs/knowledge/hugging-face-trl-v1-8-0-rl-experiment-reference.md` | five bounded RL experiment cards and six promotion gates already exist | compare Tunix with the pinned baseline only for a multi-turn workload; do not add a duplicate RL program |

The best initial workload, if separately approved, is one deterministic,
synthetic tool-use environment modeled on the source-monitoring/KB domain. Its
tools must be in-memory, allowlisted and unable to access the network,
credentials, device controls, the task board or persistent knowledge. Android
may later display the experiment summary and an approve/reject decision; it
must not execute the trainer or environment.

## One framework-selection smoke

Compare a pinned Tunix candidate with the current approved baseline on the same
small model, synthetic environment, prompt/trajectory budget, seed set and TPU
topology. Include fixed tool-wait and long-tail cases so asynchronous rollout
overlap is actually exercised. Run at least three repetitions per
configuration after a warm-up and retain the raw aggregate receipts.

Record these eight metrics:

1. completed trajectories per second;
2. generated tokens per second;
3. accelerator idle-time fraction;
4. trainer input-starvation fraction;
5. fixed-budget wall-clock duration;
6. peak accelerator memory;
7. held-out task success and reward agreement with the deterministic scorer;
8. unsafe side-effect and cross-environment tool-leak count.

The smoke has seven gates:

1. **Owned workload.** Name the Soll use case, owner, baseline and user value;
   reject the experiment when environment/tool waits are not a measured
   bottleneck.
2. **Pinned provenance.** Pin Tunix, JAX, Flax, Optax, serving engine, model,
   tokenizer, environment, reward code, dataset revision and hardware
   topology, including licenses.
3. **Isolation.** Use synthetic or explicitly approved non-sensitive data with
   no ambient credentials, network, persistent writes or device tools.
4. **Repeatable efficiency.** Require at least three comparable runs and a
   median trajectory-throughput gain of at least `15%` plus an accelerator
   idle-time reduction of at least `20%` relative to the pinned baseline.
5. **Quality and safety parity.** Held-out task success may not regress by more
   than `0.02` absolute; deterministic reward agreement must be `1.0`; unsafe
   side effects and cross-environment tool leaks must remain `0`.
6. **Reliability and resource bound.** No OOM, deadlock, lost trajectory or
   unrecovered trainer error is allowed, and peak accelerator memory may not
   increase by more than `10%` without an approved trade-off.
7. **Approval and rollback.** Retain the baseline environment and artifacts,
   review receipts manually, and require a separate task before model
   promotion or any Android/API/runtime change.

If the same fixed-budget workload does not pass every gate, record the result
and reject Tunix for that workload. Upstream TPU-utilization claims are not a
substitute for this comparison.

## Observed value and limits

This applicability check produced one durable knowledge note, captured four
upstream capability groups, audited four current Soll seams, and defined one
framework-selection smoke with eight metrics and seven gates. That is
measurable workflow-readiness value.

Actual Tunix/JAX installations: **0**. Training or inference runs: **0**.
Measured Soll throughput, accelerator utilization, quality or cost
improvement: **0**. Android production files and runtime contracts changed:
**0**. Hardware-efficiency value remains unproven until the bounded smoke has
an approved workload and TPU prerequisite.
