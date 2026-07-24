---
title: SAO deep dive and a safe Soll agent-task offline evaluation
source: Hugging Face Daily Papers
source_url: https://huggingface.co/papers/2607.07508
arxiv: 2607.07508v1
source_ref: source-item/9011e13c06d6/e9091c296290ee7e
task_id: a0ea55d17b354cdeaf385ab485645435
status: offline_synthetic_eval_only
reviewed_at: 2026-07-24 Europe/Chisinau
---

# SAO: algorithm, value-model requirements, and a safe Soll offline eval

## Decision

Single-Rollout Asynchronous Optimization (SAO) is retained as a research and
evaluation pattern. It is not added as a Soll training or runtime dependency.
The paper studies asynchronous reinforcement learning of large agentic models;
this repository is an Android client and does not contain the required policy,
critic, rollout-log-probability, or distributed trainer infrastructure.

The only application in this task is
`SaoOfflineEvalPrototypeTest`: a deterministic, test-scope audit of a synthetic
fixture. It checks the paper's one-rollout-per-prompt shape, Direct
double-sided Importance Sampling (DIS) mask, observation exclusion, and two
critic diagnostics. It does not calculate gradients, update weights, execute
an agent, call a model or tool, rank live tasks, or write to an external
system.

## Source receipt

- Hugging Face item: <https://huggingface.co/papers/2607.07508>;
- primary record: <https://arxiv.org/abs/2607.07508>;
- full text reviewed: <https://arxiv.org/html/2607.07508>;
- PDF reviewed: <https://arxiv.org/pdf/2607.07508>;
- paper version: `arXiv:2607.07508v1`, submitted 8 July 2026;
- license shown by arXiv: CC BY 4.0;
- PDF receipt: `%PDF-1.7`, 664,828 bytes, SHA-256
  `44c695be0428c666d06c914ba76c037e3ac77eeb5db0a81bbe239719c21bda48`.

The task-referenced raw snapshot
`raw/monitored\hugging-face-daily-papers\20260709-230009-single-rollout-asynchronous-optimization-for-age-d251fcc8.md`
is absent from this isolated worktree. It was not treated as evidence. The
paper pages and PDF were read without login or external writes. No paper
archive, upstream code, model, dataset, training stack, or credential was
copied into the repository.

## SAO algorithm

The paper does not present one standalone pseudocode listing. The following
ordered algorithm reconstructs Sections 3.1 and 3.2 and keeps the components
that materially affect correctness:

1. Dispatch a prompt to an asynchronous rollout worker and sample exactly one
   trajectory. Preserve the behavior-policy log probability for every
   model-generated action token and distinguish action tokens from external
   observation tokens.
2. Make the completed trajectory available to training immediately. Do not
   wait for a same-prompt response group as GRPO does.
3. Recompute each action token's log probability under the current policy and
   form a direct behavior ratio from the current and rollout policies. SAO
   deliberately drops the separately tracked "latest old policy".
4. Apply the strict double-sided DIS calibration to each action token. A ratio
   strictly inside the trust interval keeps its ratio weight; every token on
   or outside either boundary receives weight zero and contributes no
   gradient.
5. Predict token-level values with the critic. For a multi-turn trace
   `[action, observation, action, ...]`, exclude observation tokens and bridge
   the last token of the current action directly to the first token of the
   next action when calculating the TD residual and GAE.
6. Update the critic more often than the actor so its value baseline can track
   the changing policy. The reported experiments use two critic updates for
   each actor update.
7. Regularize critic optimization. In the paper's Qwen3 MoE setting, attention
   parameters are frozen and the MoE projections are optimized; the critic is
   also initialized with substantially scaled value-pretraining data.
8. Apply the actor update using only retained action-token contributions, then
   continue consuming newly completed trajectories as rollout workers and the
   trainer advance asynchronously.

In compact form, one trajectory per prompt replaces the GRPO group baseline,
the learned critic supplies the single-trajectory baseline, skip-observation
GAE supplies action-only advantages, and DIS gates stale action tokens before
an actor update.

## Direct double-sided Importance Sampling and clipping

For an action token `a_t` in state `s_t`, SAO uses the rollout engine's stored
log probability directly:

`r_t(theta) = exp(log pi_theta(a_t | s_t) - log pi_rollout(a_t | s_t))`.

Its calibration function is:

`f(r; epsilon_low, epsilon_high) = r`
when
`1 - epsilon_low < r < 1 + epsilon_high`, and `0` otherwise.

The paper's actor expression is:

`L(theta) = E_t[f(r_t; epsilon_low, epsilon_high) * A_t * log pi_theta(a_t | s_t)]`.

Four details are easy to lose in an implementation:

- this is a strict open interval: tokens exactly on either boundary are
  masked;
- an out-of-range token is zeroed, not numerically clamped to the boundary;
- both sides are gated regardless of the sign of the advantage, unlike
  standard PPO's sign-conditioned clipping;
- the denominator is the token's actual rollout-policy probability, not one
  convenient stale checkpoint for the whole trajectory.

The paper reports `epsilon_low=0.3`, `epsilon_high=5.0` for tool-integrated
reasoning, producing the interval `(0.7, 6.0)`. For coding it reports
`epsilon_low=0.8`, `epsilon_high=3.0`, producing `(0.2, 4.0)`. These are
experiment-specific settings, not safe Soll defaults. A real audit would also
need identical tokenization, immutable rollout/current model identities,
finite aligned token log probabilities, an action/observation mask, policy-lag
metadata, and reporting of ratio quantiles and masked-token rate. Missing or
mismatched provenance invalidates the comparison; it must not be imputed.

## Value-model requirements

Single-rollout optimization removes GRPO's within-prompt group mean and raises
gradient variance. SAO therefore depends on a trained critic rather than
treating value modeling as an optional auxiliary:

1. **Cold-start quality.** The policy and value model in the reasoning setup
   are initialized from the same TIR-finetuned Qwen3-30B-A3B model. The paper
   says scaling the value-pretraining corpus is essential; its size and data
   recipe are not disclosed sufficiently to reproduce from this paper alone.
2. **Faster critic schedule.** The critic must update more frequently than the
   actor (`K > 1`); the reported setting is `K=2`. The ablation with one critic
   update has worse explained variance and benchmark performance.
3. **Stable parameter subset.** For the tested MoE backbone, full-attention
   critic gradients were unstable. SAO freezes the critic's attention modules
   and updates MoE projections. This is an architecture-specific empirical
   intervention, not a general requirement that can be copied to a dense or
   differently partitioned model without a new ablation.
4. **Token-level action values.** The critic must provide values at
   model-generated token granularity. Observation tokens are not generated by
   the policy and must not become value or advantage targets.
5. **Skip-observation GAE.** At an action-to-observation-to-action boundary,
   SAO uses:
   `delta = r_t + gamma * V(a_(i+1,0)) - V(a_(i,N))` and
   `A(a_(i,N)) = delta + gamma * lambda * A(a_(i+1,0))`.
   The external observation span is bypassed. The appendix reports worse
   results for step-average and last-token step-level alternatives.
6. **Diagnostics before actor use.** Explained variance between values and
   returns, critic gradient norm, missing/invalid value count, and performance
   by task family must be monitored. The paper's reported critic settings
   (`5e-6` learning rate, `lambda_critic=1`, ten-step warmup) are experiment
   details, not universal readiness thresholds.

A critic is not ready merely because it returns a number. Its pretraining
provenance, architecture-specific trainable subset, update ratio, token/action
alignment, and held-out calibration evidence must all be recorded. The paper
also requires rollout token log probabilities; without them DIS cannot be
evaluated even if the critic is accurate.

## Reported evidence and limits

The paper evaluates Qwen3-30B-A3B on tool-integrated math and OpenHands coding.
It reports SAO ahead of standard GRPO and GRPO with DIS on AIME2025,
BeyondAIME, HMMT, IMOAnswerBench, and SWE-Bench Verified. Standard GRPO is
reported to collapse around step 160; the distinction between SAO and GRPO
with DIS becomes clear after roughly 400 steps. Ablations report lower
reasoning accuracy with one critic update, full-parameter critic training,
VAPO without DIS, and a running-mean reward baseline.

These are upstream training results, not Soll measurements. The study uses one
large MoE backbone, long sparse-reward agent tasks, and a controlled simulated
online preference shift. The paper itself warns that results may not transfer
to smaller models, dense rewards, short traces, or real user-facing online
adaptation. It does not establish a safe production threshold for clip rate,
explained variance, or policy lag, and it does not provide enough
value-pretraining detail for reproduction.

## Offline Soll prototype

The machine-readable fixture
`docs/knowledge/sao-soll-agent-offline-eval-v1.json` contains three synthetic,
credential-free task traces. Every prompt has exactly one rollout. Across the
fixture there are six action tokens and three external observation tokens.
The test-only evaluator:

- validates the one-rollout-per-prompt invariant;
- calculates `r_t` from stored synthetic log probabilities;
- applies the strict reasoning interval `(0.7, 6.0)`;
- proves that exact boundary values are masked;
- excludes observations and counts action-to-action bridges;
- reports critic mean absolute error and explained variance;
- reads no production task history and performs no model or agent execution.

The deterministic smoke retains four of six action tokens and masks two. It
reports value MAE `0.3` and explained variance `0.555`. Those values only
prove that the evaluator and fixture agree; they say nothing about a real
critic or about Soll model quality.

### Protocol for any later real replay

A later experiment would require separate approval and an immutable sanitized
export. It must remain disconnected from live task selection and external
tools. Before evaluation, require:

1. one archived rollout per stable prompt id, with no prompt duplicated;
2. exact rollout/current model and tokenizer revisions plus aligned finite
   per-action-token log probabilities;
3. explicit action and external-observation spans;
4. immutable outcome/return labels and separately versioned critic predictions;
5. task-family labels for stratified reporting and no secrets, personal data,
   credentials, raw environment payloads, or generated external commands;
6. metrics for missing data, ratio quantiles, lower/upper mask rates, value
   MAE, explained variance, family slices, and result sensitivity to epsilon;
7. a read-only result artifact with automatic task mutation, model updates,
   agent/tool execution, alerts, and external writes all disabled.

Reject the replay if any token provenance is missing, a prompt has multiple
rollouts, observation tokens leak into critic metrics, value diagnostics are
undefined, one family dominates the aggregate, or any side effect is required.
Passing an offline report would still not authorize training or production use.

## Measured result

- SAO algorithm stages documented: `8`;
- value-model requirement groups documented: `6`;
- synthetic offline suites added: `1`;
- prompts / rollouts: `3 / 3`, exactly one rollout per prompt;
- action / observation tokens audited: `6 / 3`;
- DIS result: `4/6` action tokens retained, `2/6` masked;
- model downloads, training or weight updates: `0`;
- production task reads, agent/tool executions and external actions: `0`;
- production/runtime/API/UI/dependency changes: `0`.

The measurable value is a source-traceable algorithm note and an executable
offline audit contract. No training, runtime, benchmark, or user-facing
improvement is claimed.
