---
task_id: 87869f06b7d046499386370410b6bff8
source_ref: source-item/854a3820824d/17b09ac75ba609e6
source_item: habr-trading-bot-launch-checklist-01021f23
source_processing_result: checklist_adapted_to_soll_app_knowledge_base
verification_artifact: Soll/outputs/source-processing/source-item-854a3820824d-17b09ac75ba609e6-verification.md
source_value: "8 checklist risks mapped; 3 concrete durability gaps identified; 0 measured trading runtime value because soll_app has no trading engine"
verified_at: 2026-07-15 Europe/Chisinau
---

# Habr trading-bot checklist integration audit

## Outcome

The checklist was integrated into the durable Soll app knowledge base at
`docs/knowledge/trading-bot-launch-checklist-soll-app.md`. No trading,
exchange, order, position, OHLC, PnL or financial dependency was added.

The current app is not a trading bot: its legacy Android Telegram `BotService`
is archived and immediately stops, while the active paths are Soll server/chat
sync, Room-backed tool/sync jobs, gadget command delivery and a research-only
event forecaster.

## Source coverage

The knowledge note analyzes and adapts all eight recovered source checks:

| Source check | Soll adaptation | Audit result |
| --- | --- | --- |
| Path-aware exits | verify terminal effect, not dispatch | partial control |
| Look-ahead bias | explicit `asOf` input boundary | present in research forecaster only |
| Fees/slippage/leverage | net latency/cost/battery/retry budget | future gate |
| Sample size | `N/A`, minimum span/count, unseen evaluation | research gate incomplete |
| Crash recovery | durable leases, reconciliation, atomic staging | three concrete gaps |
| Exchange adapter | accepted vs executed vs persisted | strong for gadget flow, weaker for queued writes |
| Aggregate exposure | coordinator-wide shared-resource budgets | future gate |
| Cron/external data | injected replay clock and frozen fixtures | future gate |

## Repository audit evidence

- `app/src/main/java/com/soll/data/service/BotService.kt` documents the Telegram
  bot as archived, calls `stopSelf(startId)` and returns `START_NOT_STICKY`.
- No production source under `app/src/main` defines trading orders, positions,
  OHLC candles, PnL, commissions, slippage or leverage.
- `DetectionStyleEventForecaster` excludes events at or after
  `forecastStartMillis`, providing a concrete current look-ahead guard.
- `ToolJobRunner` persists queued/running/terminal status and only marks success
  after its handler returns.
- `SollSyncQueueRepository` persists pending work and retries gateway failures.
- `SyncQueueDao.getReadyItems()` selects `PENDING`/`FAILED`, not stale
  `RUNNING`; there is no generic startup reconciliation for orphaned running
  sync items or tool jobs.
- queued raw files are copied directly to their final queue path without a
  temporary-file rename or persisted length/hash verification.
- queued writes use local UUID rows, but raw-note/file gateway requests do not
  carry that UUID as an explicit end-to-end idempotency key.
- gadget commands expose claim, ack and posted-result operations, demonstrating
  the desired accepted/executed/persisted separation.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Knowledge note exists | durable checklist under `docs/knowledge` | PASS |
| All eight checks are represented | eight named checklist sections | PASS |
| Current implementation is not mislabeled as trading | explicit no-trading scope | PASS |
| Existing controls cite real components | forecaster, tool runner, sync queue, gadget contract | PASS |
| Gaps are actionable and bounded | recovery, atomic staging, idempotency | PASS |
| Production behavior remains unchanged | documentation/test/artifact only | PASS |
| Source metrics are attached | result, artifact path and value | PASS |

The focused unit audit
`HabrTradingBotChecklistSourceTriageTest` verifies the knowledge-base decision,
all eight adapted controls and the value-metric fields in this artifact.

## Promotion decision

Keep trading adoption **rejected/out of scope**. Create separate reliability
tasks only if the coordinator accepts them, in this order:

1. stale-running lease/reconciliation;
2. idempotency plus authoritative effect receipts;
3. atomic validated upload staging;
4. chronological sample gates for any real event-forecast evaluation;
5. aggregate resource budgets for parallel automation.

## Value metric update

- `source_processing_result`:
  `checklist_adapted_to_soll_app_knowledge_base`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-854a3820824d-17b09ac75ba609e6-verification.md`
- `source_value`: all 8 source risks were converted into review gates for the
  actual Soll architecture and 3 concrete durability gaps were identified;
  measured trading runtime value remains `0` because no trading engine exists.
