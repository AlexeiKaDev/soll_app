---
title: TurnOPD formulas and safe Soll turn-budget audit
source: Hugging Face Daily Papers
source_url: https://huggingface.co/papers/2607.05804
arxiv: 2607.05804v1
source_ref: source-item/9011e13c06d6/f94e66941d30b4cb
task_id: 28b03fff52b24ac8902b9f0c2e1673a1
status: offline_existing_synthetic_traces_only
reviewed_at: 2026-07-27 Europe/Chisinau
---

# TurnOPD: formulas, controllers, and a safe Soll offline audit

## Decision

TurnOPD is retained as a research and evaluation pattern, not as a Soll
training or runtime controller. The paper addresses on-policy distillation with
a student, a frozen teacher, full-depth probe rollouts, token-level reverse-KL
and optimizer updates. This repository contains an Android client and
versioned synthetic evaluation fixtures, not that training stack.

This task therefore applies only the paper's turn accounting to two already
versioned, non-production fixtures:

- `docs/knowledge/sao-soll-agent-offline-eval-v1.json`;
- `docs/knowledge/agentlens-soll-ci-harness-v1.json`.

The audit counts turns, token allocation, completion depth, survivor coverage
and missing controller inputs. It does not train a model, query a teacher,
calculate gradients, execute an agent or tool, read production/user traces,
change task priority, or deploy anything.

## Source receipt

- Hugging Face item: <https://huggingface.co/papers/2607.05804>;
- primary paper: <https://arxiv.org/abs/2607.05804>;
- full PDF reviewed: <https://arxiv.org/pdf/2607.05804>;
- paper identity: `arXiv:2607.05804v1`, submitted 7 July 2026;
- method sections reviewed: diagnosis, both budget controllers, algorithm and
  complete reference hyperparameters.

The task-referenced raw snapshot
`raw/monitored\hugging-face-daily-papers\20260708-220900-turnopd-making-on-policy-distillation-turn-aware-ba3fa721.md`
is absent from this isolated worktree and is not used as evidence. No paper
archive, upstream repository, model, dataset or training dependency is copied
into Soll.

## Baseline objective and turn notation

At turn `t`, the student observes `o_t`, conditions on history
`h_t = (x, o_1, r_1, ..., o_t)` and generates response
`r_t ~ pi_theta(. | h_t)`. A trajectory is:

`tau = (x, o_1, r_1, o_2, r_2, ..., o_T, r_T)`.

For response-token mask `m_i`, vanilla on-policy distillation minimizes a
trajectory-normalized reverse-KL:

`L_OPD(theta) = E[(1 / sum_i m_i) * sum_i m_i * D_KL(pi_theta(.|s_i) || pi_T(.|s_i))]`.

The token denominator is important: long or shallow responses receive more
aggregate weight. TurnOPD separates two budgets:

1. **External budget:** how many interaction turns are collected.
2. **Internal budget:** how loss mass is allocated over the collected turns.

## Controller 1: adaptive rollout depth

### Survivor-weighted supervision mass

Only uncensored full-depth probe rollouts update the controller. For zero-based
turn index `t`, probe mean reverse-KL `K_t`, survivor count `n_t` and initial
count `n_0`:

`m_t = max(K_t, 0) * (n_t / n_0)`.

`q_t = m_t / (sum_j m_j + epsilon)`.

The effective-signal centroid and its discrete projection are:

`H_eff_bar = sum_t t * q_t`.

`H_eff = round(H_eff_bar)`.

`K_t` is a full teacher/student distribution reverse-KL mean. A sampled-token
log-probability difference, reward, success rate or tool-call failure is not a
drop-in substitute.

### Success-coverage floor

For successful completion depth `L_succ` and coverage quantile `p`:

`H_cov = Q_hat_p(L_succ) = min{H : F_succ(H) >= p}`.

The paper's reference run uses `p = 0.80` and requires at least `8` successful
probe trajectories before refreshing this arm. It uses successful rollouts,
not failed maximum-horizon tails, for the reference coverage CDF.

### Combined, smoothed controller

`H_ctrl = max(H_eff, H_cov)`.

After probe snapshot `k`, the zero-based controller state is smoothed:

`H_bar_k = (1 - alpha_ema) * H_bar_(k-1) + alpha_ema * H_ctrl,k`.

The applied turn count is:

`H_hat_(k+1) = clip(round(H_bar_k) + 1, H_min, H_max)`.

The `+1` converts the zero-based index into a number of turns. The paper's
ALFWorld reference uses `alpha_ema=0.30`, `H_min/H_max=2/50`, three warm-up
steps and a full-depth probe every eight optimizer steps. These are reported
experiment settings, not safe Soll defaults.

Truncated routine rollouts must never update `K_t`, survivor statistics or the
completion CDF: their unobserved tail would bias the next cap and can create
cascading shrinkage.

## Controller 2: progressive turn-normalized loss

For `n_t` supervised tokens at turn `t` and `T` observed turns:

`q_traj_t = n_t / sum_j n_j`.

`q_turn_t = 1 / T`.

With training step `k` of `K` and blend window `(s, e)`:

`progress = k / K`.

`alpha = clip((progress - s) / (e - s), 0, 1)`.

`q_blend_t = (1 - alpha) * q_traj_t + alpha * q_turn_t`.

Equivalently at token level:

`w_blend = (1 - alpha) * w_traj + alpha * w_turn`.

`L_k = sum_i w_blend,i * ell_i`.

Early training follows token mass; later training moves toward equal turn
mass. A hard uniform-turn switch can overweight poorly supported deep turns,
which is why the paper uses progressive blending and a minimum survivor floor.

## Existing non-production Soll traces

| Fixture | Existing trace shape | Safe provenance | Turn-budget fields |
| --- | --- | --- | --- |
| SAO offline eval | 3 synthetic rollouts; each has action, observation, action | personal data false; credentials false; production history forbidden; agent/model execution forbidden | action-turn boundaries, one action token per turn, terminal return and sampled-token log probabilities |
| AgentLens CI smoke | 1 embedded synthetic recovery trace; 6 ordered events, including 3 tool calls | explicit fixture repository; network, secrets, external writes and repository mutation all zero | event order and outcome evidence only |

Neither fixture contains a frozen-teacher distribution, exact per-turn
reverse-KL, full-depth probe marker, controller step, training progress or
uncensored survivor snapshot. AgentLens also has no explicit `turn_id` or
per-turn token counts. Therefore neither trace is eligible to drive `H_eff`,
EMA, loss updates or a runtime rollout cap.

## Offline audit on existing budget data

### Exact turn/token accounting

The SAO fixture contains `3` rollouts and `6` action turns (`2` per rollout).
All three rollouts reach both action turns, so survivor counts are `n_0=3` and
`n_1=3`. Each turn contains one supervised action token:

| Metric | Turn 0 | Turn 1 |
| --- | ---: | ---: |
| Surviving traces | 3 | 3 |
| Action tokens | 3 | 3 |
| `q_traj` | 0.50 | 0.50 |
| `q_turn` | 0.50 | 0.50 |
| `q_blend`, any `alpha` | 0.50 | 0.50 |

The existing fixture therefore has no token-vs-turn reallocation headroom:
progressive normalization leaves its `50% / 50%` loss budget unchanged. This
is a measurable negative result, not evidence against TurnOPD on realistic
variable-length responses.

### Coverage diagnostic

Two of the three SAO rollouts have terminal return `1.0`; both complete at
depth `2`. The diagnostic 80th-percentile completion depth is therefore
`2 turns` (zero-based `H_cov=1`, followed by `+1` when expressed as applied
turn count).

This value is not controller-ready. Only `2` successful traces are present,
below the paper reference guard of `8`, and the fixture has no full-depth probe
provenance. A production or training controller must keep the previous
coverage arm rather than update from this sample.

### Sampled-token drift diagnostic

As a readiness check only, the test computes the mean absolute sampled-token
log-probability difference:

`d_t = mean |log p_candidate(a_t) - log p_rollout(a_t)|`.

It obtains `d_0=0.7067545121` and `d_1=0.9293643566`; normalized proxy mass is
`43.197% / 56.803%`, with a zero-based centroid that rounds to turn `1`.
This diagnostic also suggests retaining both turns, but it is deliberately not
named `K_t` or `H_eff`: one sampled action probability cannot reconstruct the
reverse-KL between full student and teacher distributions.

### AgentLens readiness audit

The AgentLens fixture contributes one six-event recovery trace: three
assistant/final events and three tool-call events. It verifies ordering,
recovery and zero unsafe effects, but supplies no explicit action-turn
boundary, per-turn token count, completion-depth sample or KL. It is retained
in the inventory and rejected from all TurnOPD calculations.

## Promotion decision and future replay gate

The source produced measurable Soll value as a formula extraction and
executable readiness audit. TurnOPD adoption is rejected for the current data:

- exact reverse-KL traces eligible for `H_eff`: `0`;
- successful full-depth probes eligible for the reference coverage update:
  `2/8`;
- fixtures with an uncensored full-depth probe marker: `0/2`;
- fixtures where progressive normalization changes allocation: `0/2`;
- model training, gradient or weight updates: `0`;
- production/user trace reads: `0`;
- agent/tool executions, task mutations, external side effects and deploys:
  `0`.

A later real replay requires separate approval and an immutable sanitized
non-production export. It must include explicit turns, supervised-token counts,
full teacher/student distributions or precomputed exact reverse-KL with model
revisions, full-depth probe markers, survivor counts, terminal success and
completion depth. It must report missingness, per-turn support, `H_eff`,
`H_cov`, EMA sensitivity, token-vs-turn loss shares and whether any proposed
cap would truncate successful completions. No result may authorize training,
task mutation or deployment automatically.

## Measured result

- budget controllers documented: `2`;
- baseline, depth-controller and loss-allocation formulas documented as one
  source-traceable formula set;
- existing synthetic non-production fixtures audited: `2`;
- SAO rollouts/action turns: `3/6`;
- exact turn allocation: `50% / 50%`, unchanged by blending;
- diagnostic 80% success completion depth: `2 turns`, rejected for refresh at
  `2/8` successful probes;
- exact reverse-KL controller inputs: `0`;
- production data, model training, model updates, agent runs, runtime changes
  and deploys: `0`.

The measurable value is a source-traceable KB note, deterministic turn-budget
audit and explicit non-promotion boundary. No model-quality, training-time or
user-facing improvement is claimed.
