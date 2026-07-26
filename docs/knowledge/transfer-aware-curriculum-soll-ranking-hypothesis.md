---
title: Transfer-Aware Curriculum and a safe Soll ranking hypothesis
source: Hugging Face Daily Papers
source_url: https://huggingface.co/papers/2606.25178
arxiv: 2606.25178v2
code: https://github.com/YangYongJin/transfer-aware-curriculum
source_ref: source-item/9011e13c06d6/0efa7e920bd2c89b
task_id: 040135ed3f2a488ba79e18061e5b0007
status: research_note_added_replay_hypothesis_only
reviewed_at: 2026-07-22 Europe/Chisinau
---

# TAC: transfer-aware curriculum and a safe Soll ranking hypothesis

## Decision

Transfer-Aware Curriculum (TAC) is retained as a research pattern, not as a
training or production dependency. The useful idea is to rank a candidate by
both its immediate local signal and its observed benefit outside its own
domain. TAC itself is an online multi-domain RLVR training algorithm; its
gradients, GRPO loop and upstream training stack do not fit the current Soll
Android repository.

The bounded Soll hypothesis below borrows only the two-signal ranking shape. It
uses historical, non-sensitive outcome metadata in an offline replay. It does
not fine-tune a model, calculate model gradients, generate security code, call
the upstream implementation, or change any source/task priority.

## Source trace and verified links

- Hugging Face item: <https://huggingface.co/papers/2606.25178>;
- primary paper: <https://arxiv.org/abs/2606.25178>;
- paper version reviewed: `arXiv:2606.25178v2`;
- official code linked by the paper:
  <https://github.com/YangYongJin/transfer-aware-curriculum>;
- task source ref: `source-item/9011e13c06d6/0efa7e920bd2c89b`.

The task-referenced raw snapshot
`raw/monitored\hugging-face-daily-papers\20260705-203016-transferability-for-general-reasoning-an-automat-6dc17485.md`
is not vendored in this isolated worktree. The algorithm, metrics and result
claims were therefore checked against the primary arXiv record and the
paper-linked official GitHub repository. A read-only link smoke on 22 July
2026 returned HTTP `200` for both the arXiv and GitHub URLs.

## TAC algorithm

TAC treats each reasoning domain as an arm of a multi-armed bandit. After a
round-robin warmup, every training step selects one domain, draws a
single-domain minibatch, performs the normal GRPO update, observes the two
signals below, and updates the arm value. UCB-style exploration and a softmax
over arm values prevent the sampler from becoming purely greedy.

The upstream procedure has five important parts:

1. compute local learnability from the selected batch's GRPO advantages;
2. project the already-computed training gradient into a fixed small sketch
   and unit-normalize it;
3. maintain a per-domain exponential moving average (EMA) of projected
   gradient directions;
4. periodically compare every initialized domain with the others, smooth and
   normalize the resulting transferability scores;
5. combine learnability and transferability, then refresh both the selected
   arm and, at comparison steps, unselected arms that have cached learnability.

The two-phase update matters: transferability can move sampling mass away from
an arm even while that arm is not selected. Per-domain warmup, cached state,
EMA smoothing, relative normalization and UCB exploration are all guards
against sparse or noisy early observations; they are not optional detail.

### Learnability metric

For domain `i` and a minibatch of `B` rollouts, the paper uses mean absolute
GRPO advantage as the local proxy:

`L_i = (1 / B) * sum_b |A_b|`.

With binary rewards, `L_i` is high when a rollout group mixes successes and
failures, which indicates usable local learning signal. It approaches zero
when all rewards agree. That zero is ambiguous: the domain may already be
saturated or may still be beyond the policy's capacity. TAC maintains running
EMA mean and deviation per domain and normally supplies a z-scored
`L_i_normalized` to the bandit; early observations use the raw value until the
normalizer is ready.

Learnability therefore means **current within-domain optimization signal**. It
is not accuracy, intrinsic difficulty, source quality or proof of general
value.

### Transferability metric

Let `g_i` be the GRPO gradient for a designated parameter subset and `R` a
fixed random projection. TAC first forms a unit direction sketch:

`p_i = R g_i / ||R g_i||`.

Only the sampled domain updates its gradient-state EMA `h_i`. At each comparison
interval, raw transferability for an initialized domain is the mean cosine
alignment with the gradient-state EMAs of the other initialized domains:

`T_i_raw = mean_{j != i} cosine(h_i, h_j)`.

The raw value is temporally smoothed with another EMA. TAC then applies a
cross-domain min-max transformation with an EMA-smoothed, floored scale. This
creates a bounded **relative ranking** of which domain's recent update
direction aligns best with the rest and avoids spikes when all cosines briefly
cluster. It is a directional first-order proxy, not observed causal improvement
on every target domain.

The official repository summarizes the composite score as:

`S_i = beta * L_i_normalized + (1 - beta) * T_i_normalized`.

Thus one coefficient balances locally available signal against cross-domain
alignment. TAC updates arm values by EMA and samples with exploration rather
than always selecting the current maximum.

## Reported evaluation and limits

The paper evaluates six domains (`math`, `codegen`, `logic`, `simulation`,
`table`, `stem`) over 14 held-out benchmarks and reports macro-averaged Pass@1
accuracy for Qwen3-1.7B and Llama3.2-3B. It reports TAC ahead of proportional
random sampling, a hand-designed schedule and a learnability-only bandit, with
up to `2.8` macro points (`10%` relative) over the latter and less than `1%`
wall-clock overhead. These are upstream training claims, not Soll measurements.

The result does not establish that gradient cosine is causal, that the same
weights transfer to non-RL ranking, or that the method works without adequate
warmup. Mean absolute advantage also cannot distinguish saturation from a task
that is too hard. Relative min-max scores can be unstable when observations
are sparse, and the published domains, models and verification rewards differ
from Soll source processing. The GitHub repository is implementation evidence,
not approval to import its veRL/Reasoning360 training stack.

## Safe Soll hypothesis: transfer-aware advisory ranking

**Hypothesis.** In an offline replay, a source or learning-task family ranked
by both recent verified local yield and evidence-backed reuse across other Soll
areas will place more genuinely useful items near the top than a recency-only
or local-yield-only baseline.

This is an analogy, not TAC transplanted into Soll:

| TAC concept | Replay-only Soll proxy | Required evidence |
| --- | --- | --- |
| arm/domain | monitored source or approved learning-task family | stable non-secret source/family id |
| learnability | smoothed rate of reviewed items that pass verification and record non-zero `source_value` in the same family | historical review and verification receipt |
| transferability | smoothed mean number/rate of explicit successful reuses in a different Soll area | durable cross-area link plus accepted outcome; similarity alone does not count |
| composite score | normalized weighted sum shown only in a shadow ranking | versioned features, weights and reason codes |
| exploration | minimum coverage quota in replay | no live priority or task mutation |

The proxy deliberately avoids embeddings and generated labels. Missing reuse
evidence scores as unknown, not as success. Source volume is capped or
posterior-smoothed so one prolific feed cannot dominate, and every ranked item
must expose the local-yield and cross-area-reuse components separately. The
ranking is advisory: a human remains responsible for accepting an item or
creating a task.

### Bounded replay and promotion gate

A later, separately approved experiment may use a time-split set of at least
`50` already-reviewed source items from at least `3` source/family groups. It
must compare the composite ranking with both chronological and local-yield-only
baselines on the same held-out slice. Before looking at results, define useful
as an existing passed verification with non-zero `source_value`; define
cross-area reuse only from explicit durable links.

Report `Precision@5`, `nDCG@10`, useful-item recall, per-group exposure, score
stability under leave-one-group-out replay and the count of missing-evidence
items. Promotion requires all of the following:

1. `nDCG@10` improves by at least `5%` over both baselines;
2. `Precision@5` does not regress against either baseline;
3. no source/family with eligible useful items receives zero exposure;
4. every score is reproducible from versioned metadata and reason codes;
5. private content, model calls, fine-tuning runs and generated security code
   remain `0`;
6. automatic priority writes, task creation, alerts and external side effects
   remain `0`.

Reject the hypothesis if the held-out set is too small, cross-area links are
not reliable, improvement misses the threshold, exposure collapses, or ranking
cannot be explained from stored evidence. A later live shadow view would still
need a separate product task, explicit approval, an owner and a rollback path.

## Measured result of this task

- research notes added: `1`;
- TAC algorithm stages documented: `5`;
- required metrics defined: learnability and transferability (`2`);
- safe Soll hypotheses defined: `1` replay-only advisory ranking;
- replay metrics defined: `5`;
- primary arXiv/GitHub links returning HTTP `200`: `2/2`;
- fine-tuning/model-gradient/security-code generation runs: `0`;
- production, Android, source-priority and task-board behavior changes: `0`.

The measurable value is a source-traceable algorithm/metric note and a
falsifiable, bounded experiment with explicit stop conditions. No runtime
ranking improvement is claimed before that replay exists.
