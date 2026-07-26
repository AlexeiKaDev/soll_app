---
title: Hugging Face TRL v1.8.0 reference for Soll RL experiments
task_id: f0bbb7448fc0420ab8146f4567422870
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/314a49ff1073
source_trust: untrusted_external_content
release: v1.8.0
release_commit: 95809b9
review_status: knowledge_reference_added_experiments_deferred
reviewed_at: 2026-07-22 Europe/Chisinau
---

# Hugging Face TRL v1.8.0 reference for Soll RL experiments

## Decision

Retain TRL v1.8.0 as a versioned **desktop/server experiment reference**, not
as an Android dependency or evidence that Soll has an RL training capability.
The release is relevant to a future, separately approved post-training pilot
because it improves agent-environment rewards, multi-environment routing,
GRPO exploration controls, KTO stability and memory/throughput configuration.

This task does not install TRL, PyTorch, PEFT, vLLM or model weights. It does
not run training, download a dataset, call a model/provider, change a reward or
source-ranking policy, or modify Android production code. A later experiment
must name a Soll-owned workload and pass the gates in this note before any
runtime integration is considered.

## Source trace and trust boundary

- monitored source identifier:
  `monitored/hugging-face-trl-releases/20260709-233804-v1-8-0-7556880b.md`;
- task source reference: `insight/314a49ff1073`;
- primary release: <https://github.com/huggingface/trl/releases/tag/v1.8.0>;
- release tag commit observed: `95809b9`;
- release date observed on the primary release page: 9 July 2026.

The monitored snapshot is not vendored in this isolated worktree. Its text was
therefore treated only as an untrusted source identifier. The release identity,
API changes and feature boundaries below were checked against the official TRL
v1.8.0 release page. Upstream examples and performance claims are not Soll
measurements and do not authorize an import or experiment run.

## Release/API reference

The following eight changes are the parts worth retaining for a future Soll
experiment review:

1. **Stable KTO API.** `KTOConfig` and `KTOTrainer` move to top-level `trl`.
   The old `trl.experimental.kto` import remains temporarily with a
   `FutureWarning` and is scheduled for removal in v2.0.0.
2. **Environment-owned reward.** An environment factory may expose reserved
   `get_reward()` with no arguments, called once for a completed rollout.
   `reward_funcs` is then optional.
3. **Multiple environments.** `environment_factory` accepts a mapping of
   environment names to factories; each dataset row selects one through its
   `environment` column, and only that environment's tools should be exposed.
4. **GRPO entropy regularization.** `GRPOConfig` adds static entropy bonuses and
   an adaptive mode with target, delta and coefficient bounds. Adaptive state
   is checkpointed and is not compatible with the Liger kernel.
5. **Direct quantization configuration.** `quantization_config` is accepted by
   SFT, DPO, GRPO, RLOO, Reward and KTO trainers beside `peft_config`; supplying
   it again through `model_init_kwargs` is an error.
6. **MoE auxiliary loss.** Router load-balancing auxiliary loss support extends
   to DPO and KTO in addition to the earlier GRPO/RLOO/AsyncGRPO paths.
7. **Packing-aware AsyncGRPO.** Dynamic batching can balance attention work and
   use an optional token budget, making throughput and peak memory explicit
   experiment variables rather than release-level assumptions.
8. **Compatibility changes.** Shared tokenization/truncation paths were
   refactored, some trainers/scripts were removed, and vLLM 0.14/0.15 support
   was dropped. Any pilot must pin a complete environment and run migration
   checks instead of assuming a drop-in upgrade.

## Current Soll fit

Four repository seams were audited:

| Soll seam | Observed boundary | Decision |
| --- | --- | --- |
| `SollGateway.askModelChat(...)` | Android sends backend-mediated inference requests; it is not a trainer or rollout environment | keep all training behind a separately owned desktop/server boundary |
| `docs/knowledge/soll-source-monitoring-kb-eval-v1.json` | synthetic no-side-effect evaluation fixtures already exist | reuse its evidence and safety style, not its cases as training data without review |
| `docs/knowledge/transfer-aware-curriculum-soll-ranking-hypothesis.md` | GRPO/TAC is already documented as replay-only research with zero training runs | link future results without turning source ranking into an online reward loop |
| Gradle/Android runtime | no TRL, PyTorch, PEFT or vLLM dependency is declared | preserve zero direct Android training dependencies |

Android may later display a reviewed experiment summary or approval task through
existing server-mediated surfaces. It must not host the trainer, reward code,
dataset, credentials, model weights or environment tools.

## Five bounded experiment cards

These are references, not approved executions. Every card uses synthetic or
explicitly approved non-sensitive data and an isolated desktop/server runtime.

### 1. KTO v1 migration rehearsal

Pin TRL v1.8.0 and the full Python/model stack, then compare the top-level KTO
import and configuration against the last approved baseline. Measure import and
configuration success, warning count, one-step loss/metric parity and resume
behavior. Do not promote while an experimental import remains in Soll-owned
code or while output drift lacks an explained tolerance.

### 2. Environment-owned reward contract

Use two deterministic toy environments with disjoint tool schemas. Compare an
external reward function with `get_reward()` for identical completed rollouts.
Measure `reward_call_count / completed_rollout_count` (target `1.0`), reward
agreement (target `1.0`), environment routing accuracy (target `1.0`) and
cross-environment tool-schema leaks (target `0`). Reward code must be pure,
versioned and unable to perform network, filesystem or device side effects.

### 3. GRPO entropy ablation

Using identical model, data split, seed set and compute budget, compare no
entropy bonus, a static coefficient and adaptive entropy. Record held-out task
reward, entropy trajectory, collapse indicators, KL behavior, tokens/second,
peak memory and resume parity. A higher training reward alone is insufficient;
promotion requires held-out improvement without worse safety or instability.

### 4. QLoRA configuration contract

For one small approved model, compare the previous explicit model-loading path
with the direct `quantization_config` trainer argument. Measure initialization
success, trainable parameter count, peak memory, first-step loss and checkpoint
reload parity. A duplicate configuration through `model_init_kwargs` must fail
closed. No model or adapter artifact may be promoted from this smoke alone.

### 5. AsyncGRPO packing benchmark

Compare fixed micro-batches with packing-aware balancing and at least two token
budgets under the same prompts, output limits and gradient accumulation. Record
tokens/second, model FLOPs utilization when available, peak memory, cross-rank
idle time, timeout/error rate and held-out reward. Treat upstream throughput
figures only as hypotheses; require a reproducible local gain and identical
quality/safety acceptance before adoption.

## Six promotion gates

1. **Owned hypothesis.** Name the Soll workload, baseline, intended user value,
   owner and a falsifiable success threshold before choosing a trainer.
2. **Pinned provenance.** Pin TRL, Transformers, Accelerate, PEFT, vLLM, model,
   tokenizer, dataset revision, environment code, reward code and licenses.
3. **Data and isolation.** Start with synthetic or approved non-sensitive data;
   disable ambient credentials, external writes and device/tool side effects.
4. **Reward validity.** Version reward inputs/outputs, test determinism and
   adversarial reward gaming, and keep an independently scored held-out set.
5. **Reproducible comparison.** Keep data, seeds, model, hardware and budget
   equal; report quality, safety, latency/throughput, memory, errors and cost
   against the current baseline with retained receipts.
6. **Approval and rollback.** Require manual review before model promotion,
   preserve the prior artifact and routing path, and demonstrate rollback.
   Android/API changes need their own task and compatibility review.

## Value decision

The release signal produced one durable KB reference, a catalog of eight
release/API changes, five falsifiable experiment cards, an audit of four current
Soll seams and six promotion gates. The value is experiment readiness and a
clear non-Android boundary. Actual TRL experiments completed: **0**. Measured
model-quality, reward, throughput or memory improvement for Soll: **0**.
