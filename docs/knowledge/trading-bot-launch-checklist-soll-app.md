# Trading-bot launch checklist adapted for `soll_app`

Status: knowledge-base guidance; no trading feature is approved or implemented.

Source signal: Habr post **"Чеклист перед запуском торгового бота"** by
`tripolskypetr`, 2026-06-23, <https://habr.com/ru/posts/1049882/>.

Source reference: `source-item/854a3820824d/17b09ac75ba609e6`.

## Decision and scope

The source checklist is useful as a compact review of simulation causality,
state durability and external-side-effect verification. It is **not** evidence
that `soll_app` should become a trading bot.

The current repository contains no order, position, exchange, OHLC, PnL,
commission, slippage or leverage domain. The old Android Telegram
`BotService` is an archived placeholder that stops immediately; the active
mobile integration is Soll server/chat sync, persistent tool jobs and device
command delivery. Consequently:

- trading-specific requirements remain not applicable until a separately
  approved trading project and contract exist;
- generally useful controls are adapted below to current Soll operations;
- this review changes no API, scheduler, tool, notification or device behavior.

The task-referenced raw snapshot is not present in this isolated worktree. The
analysis covers the eight checks recoverable from the monitored-source record
and the public post: path-aware exits, look-ahead bias, execution economics,
sample size, crash recovery, adapter acknowledgement, aggregate exposure and
external-data clock discipline.

## Current Soll execution model audited

```text
Android UI / worker
  -> SollGateway / Retrofit response
  -> Room-backed sync queue, task cache and notifications

Local tool request
  -> ToolJobRunner (QUEUED -> RUNNING -> terminal state)
  -> handler side effect
  -> persisted output/log + optional notification

Server device command
  -> claim with lease
  -> execute on the intended gadget
  -> ack/result contract

Research event forecast
  -> AssistantEvent history strictly before forecastStartMillis
  -> deterministic forecast/evaluation (not production-wired)
```

## Adapted checklist

### 1. Path-aware exits -> verify the real effect path

**Trading failure:** a backtest closes at candle `close` even though the
intra-candle path would have crossed stop-loss or take-profit first.

**Soll analogue:** do not call an action successful merely because a handler
returned or an HTTP request completed. Success must describe the real terminal
effect at the boundary that owns it.

Current controls:

- `ToolJobRunner` records `SUCCESS` only after `ToolHandler.execute()` returns.
- Retrofit mutations parse the returned task/raw response instead of treating
  request dispatch alone as success.
- gadget commands have separate claim, acknowledgement and result operations.

Remaining gate:

- each side-effecting handler must return an effect receipt appropriate to the
  action: remote object/version, device command result, file size/hash, or
  observed postcondition;
- a queued/accepted response is not the same as effect completion;
- tests must cover intermediate ordering and failure, not only the happy final
  state. For device actions this means `claimed -> executed -> result`, never
  `claimed -> success`.

### 2. Look-ahead bias -> enforce an explicit as-of boundary

**Trading failure:** an indicator reads the full dataset while pretending to
evaluate an earlier tick.

**Soll analogue:** an offline evaluation, digest or forecast must not read
events, task states or source data newer than its declared decision time.

Current control:

- `DetectionStyleEventForecaster` filters both its multi-class forecast and
  baseline to `event.createdAt < forecastStartMillis`.

Required gate for any future replay/evaluator:

- accept an explicit `asOf`/forecast origin and an immutable input snapshot;
- inject a clock instead of calling `System.currentTimeMillis()` inside domain
  decisions under evaluation;
- split chronologically, fit all thresholds only on the training prefix and
  keep validation/test labels unavailable until prediction is frozen;
- record source event time, ingestion time and decision time separately so a
  late-arriving item cannot silently appear in an earlier replay.

### 3. Fees, slippage and leverage -> count real execution economics

**Trading failure:** gross mid-price PnL hides fees, spread, slippage and the
cost of leveraged holding.

**Soll analogue:** a feature can look useful while hiding network retries,
model/provider spend, battery, foreground-service time, storage, latency and
duplicate side effects.

Adapted gate:

- report both gross output quality and net operational cost;
- measure attempts, transferred bytes, p50/p95 latency, battery/CPU where
  relevant, provider cost and duplicate/failed effects per completed action;
- include retry amplification and time spent waiting for an external system;
- reject an optimization whose local metric improves while end-to-end latency,
  reliability, cost or battery breaches its budget.

This is a measurement requirement for future Soll automation. It does not map
commission or leverage into fake mobile metrics and does not justify a trading
dependency.

### 4. Sample size -> show `N/A`, uncertainty and promotion gates

**Trading failure:** fewer than 30 trades can produce a spectacular but
tail-driven Sharpe ratio.

**Soll analogue:** a tiny synthetic fixture or a handful of successful tool
runs cannot establish production value.

Current state:

- the event forecaster defaults to three occurrences and has only a synthetic
  holdout; its existing roadmap record correctly remains research-only.

Adapted gate:

- return `N/A` for promotion metrics when the predeclared minimum is not met;
- for recurring-event research, require at least 10 eligible signals spanning
  at least 14 days per evaluated class before reporting a promotion metric;
- use chronological/rolling-origin evaluation, multiple seeds where learning
  is stochastic, per-class results and confidence intervals;
- keep the denominator visible: eligible attempts, successes, failures,
  abstentions and excluded samples;
- compare against a named simple baseline and require improvement on unseen
  data, not only the fixture used during development.

The `10 signals / 14 days` rule is a conservative adoption gate from the
source, not a claim that it is statistically sufficient for every workload.
Each later experiment must justify its own power and risk threshold.

### 5. Crash recovery -> durable state plus deterministic reconciliation

**Trading failure:** a non-atomic state file or restart-from-zero loses the
open position and diverges from the exchange.

**Soll analogue:** Room persistence alone is insufficient when the process can
die between a remote side effect and the local terminal-state update.

Current controls:

- sync-queue and tool-job rows persist pending/running/terminal states in Room;
- WorkManager retries pending/failed sync operations;
- tool progress and terminal output are persisted as the handler runs.

Audit findings:

- `SyncQueueDao.getReadyItems()` selects only `PENDING` and `FAILED`; an item
  left `RUNNING` by process death has no stale-lease reconciliation path;
- generic tool jobs also have no startup rule that turns an orphaned `RUNNING`
  row into a reviewable failed/retryable state;
- raw-file queue copies write directly to the final path, with no temporary
  file + fsync/close + atomic rename or stored length/hash validation;
- the remote side effect and the local `DONE`/`SUCCESS` transition cannot form
  one transaction, so idempotency and reconciliation are required.

Required gate:

1. Give running work a lease/heartbeat and reconcile stale leases on startup.
2. Use stable operation IDs/idempotency keys for retryable mutations.
3. Write files to a sibling temporary path, close and validate length/hash,
   then atomically rename before enqueueing.
4. On restart, query the authoritative side when possible before retrying an
   ambiguous operation.
5. Test process death before dispatch, after dispatch/before acknowledgement,
   and after acknowledgement/before the local terminal write.

### 6. Exchange adapter -> distinguish accepted, executed and persisted

**Trading failure:** local state says a position closed even though no buyer or
seller filled the order.

**Soll analogue:** transport acceptance must not be promoted to completed
device/server state.

Current controls and gaps:

- gadget commands already model claim, ack and posted result, which is the
  strongest current Soll analogue;
- sync-queue entries are marked done after a successful gateway response, but
  raw-note creation and queued mutations do not carry the queue row ID as an
  explicit end-to-end idempotency key;
- a timeout after the server commits but before Android receives the response
  is therefore ambiguous and can cause a duplicate retry where the endpoint is
  not naturally idempotent.

Required contract for any new side-effect adapter:

```text
requested -> accepted/claimed -> executed or rejected -> result persisted
```

Persist the authoritative external ID and result. Never update the local model
to the intended final state merely because dispatch succeeded.

### 7. Ten strategies, one account -> enforce aggregate exposure budgets

**Trading failure:** ten strategies that each risk 10% can expose one account
to 100% risk.

**Soll analogue:** individually safe agents/jobs can collectively exhaust the
same phone, device, provider, network or notification budget.

Adapted gate for future parallel automation:

- centralize concurrency and rate limits per shared resource;
- calculate aggregate in-flight work before starting another job;
- reserve capacity for cancellation, health checks and user actions;
- cap provider spend, device-command concurrency, notification volume,
  foreground runtime and storage at the coordinator level;
- expose the aggregate budget and rejection reason in audit logs.

There is no capital or strategy portfolio in the current app. This is a future
multi-agent/resource-safety rule, not a trading implementation requirement.

### 8. External data and cron -> replay on the simulation clock

**Trading failure:** a fast backtest calls a cron-fed database governed by wall
clock time and sees data that was unavailable at the simulated instant.

**Soll analogue:** a historical evaluation must not call live sync, current
source state or a wall-clock scheduler during replay.

Current control:

- the research forecaster accepts `forecastStartMillis` and filters its input;
  WorkManager/alarm scheduling remains production infrastructure, not a replay
  clock.

Required gate:

- domain replay consumes a frozen event log through an injected monotonic
  simulation clock;
- external fetches are disabled or served from timestamped fixtures;
- each fixture records both availability time and event time;
- incomplete/live records are explicitly marked and excluded according to a
  declared policy;
- the same deterministic fixture yields the same decisions regardless of wall
  clock, network and scheduler state.

## Promotion checklist for a new Soll automation

Before a new autonomous or side-effecting flow is promoted, its review must
answer all of the following:

- What is the authoritative completion event and effect receipt?
- What exact `asOf` boundary prevents future data from entering evaluation?
- What is the net cost/error budget, including retries and external latency?
- What minimum sample and time span make metrics reportable rather than `N/A`?
- What state survives process death, and how are stale running rows reconciled?
- Is retry idempotent after an ambiguous timeout?
- Which shared resource budget covers all concurrent jobs together?
- Can the workflow be deterministically replayed without live data or wall
  clock access?

If any answer is missing, keep the flow proposal/research-only and do not allow
it to mutate tasks, devices or external systems automatically.

## Prioritized Soll follow-up candidates

This knowledge review identifies bounded follow-ups but does not implement them
under this source-processing task:

1. Reconcile stale `RUNNING` sync-queue/tool-job rows using leases and explicit
   restart outcomes.
2. Add end-to-end operation IDs and effect receipts to ambiguous retryable
   writes, starting with raw-note/file upload and device actions.
3. Add an atomic validated file-staging helper for queued uploads.
4. Reuse the explicit as-of/sample-size gates if the research event forecaster
   is ever evaluated on real, consented history.
5. Define aggregate resource budgets before enabling parallel autonomous jobs.

## Value decision

The source produces measurable **knowledge/audit value**: eight launch risks
are mapped to current Soll components, and three concrete durability gaps are
identified (orphaned running rows, non-atomic upload staging, and missing
end-to-end idempotency for ambiguous writes). It produces **zero measured
trading runtime value** because `soll_app` has no trading implementation. The
correct outcome is checklist integration plus deferred, separately scoped
reliability work—not exchange or strategy code.
