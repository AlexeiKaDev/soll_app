---
task_id: 7bb8d114c0194698b0cd3e570e25f2dd
source_ref: source-item/94b02ac6da81/f4c9532907757955
source_item: habr-sber-long-horizon-event-detection-180362b7
source_processing_result: prototype_validated_on_synthetic_soll_events
verification_artifact: Soll/outputs/source-processing/source-item-94b02ac6da81-f4c9532907757955-verification.md
source_value: "research adaptation validated: synthetic holdout F1 0.9412 versus 0.6154 baseline; production value remains unproven pending redacted chronological backtest"
verified_at: 2026-07-15 Europe/Chisinau
---

# Detector-style long-horizon event forecast prototype for `soll_app`

## Outcome

A focused research prototype now maps object detection's **what + where** idea
to Soll's **what + when** event sequence without changing an Android or server
contract:

- `AssistantEvent.type` + `AssistantEvent.source` are the event class (what).
- `AssistantEvent.createdAt` supplies the ordered event coordinate (when).
- Each forecast is a class, expected timestamp, bounded time window, confidence,
  support count and learned recurrence period.
- Matching requires the same class and sufficient temporal IoU. Greedy
  one-to-one assignment prevents several predictions from receiving credit for
  one actual event; temporal NMS removes overlapping same-class predictions.

The prototype is a deterministic pure Kotlin component. It reads no
`payloadJson`, has no network/model/dependency cost and performs no task,
notification, UI or background-work mutation.

## Scope and source limitation

The task-referenced raw file
`raw/monitored\habr-sber-company\20260702-194414-item-180362b7.md` is not
present in this isolated worktree. The implementation therefore adapts only the
method stated in the task record—detect **what** and **when** in a long event
horizon—and does not claim reproduction of unreviewed article-specific model
architecture or paper results.

No representative historical AssistantEvent export is checked into this
repository. The smoke evaluation uses a synthetic Soll-shaped holdout so the
algorithm, matching rules and metrics are reproducible without personal data.
No production forecast value is claimed.

## Prototype

`DetectionStyleEventForecaster` provides three research operations:

1. Group safe historical events by normalized `type/source` class.
2. Learn a robust recurrence period from the median inter-event gap and a
   window from median absolute deviation, then emit all class/time candidates
   inside the requested long horizon with bounded confidence and temporal NMS.
3. Evaluate candidates by one-to-one class + temporal-IoU matching, reporting
   TP/FP/FN, precision, recall, F1 and timing MAE.

The comparison baseline deliberately predicts only the most frequent event
class using the same recurrence estimator. It tests whether multi-event set
prediction contributes value beyond a simple dominant-class forecast.

## Focused holdout audit

### Data

- Training: four weekly `sync_success/server_sync` events, four weekly
  `source_digest/source_monitor` events and two sparse
  `backup_completed/backup` events.
- Horizon: 28 days.
- Holdout: four sync events, four digest events (one shifted by one day) and one
  unexpected `tool_failure/tool_runner` event.
- Match tolerance: +/- 1 day, minimum temporal IoU `0.3`.
- Sparse series gate: at least three historical occurrences.

### Result

| Method | TP | FP | FN | Precision | Recall | F1 | Timing MAE |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Detector-style multi-class prototype | 8 | 0 | 1 | 1.0000 | 0.8889 | 0.9412 | 0.125 day |
| Single-class frequency baseline | 4 | 0 | 5 | 1.0000 | 0.4444 | 0.6154 | not a promotion metric |

The prototype F1 `0.9412` exceeds the baseline F1 `0.6154` by `0.3258` on this
synthetic Soll-shaped holdout. This is a smoke result, not an estimate of future
performance: the fixture is small, regular and intentionally favorable to a
recurrence detector.

## Promotion decision

Keep the prototype research-only. Before any notification/UI/scheduler wiring:

1. Export only consented/redacted `type`, coarse `source` and `createdAt`; never
   export `payloadJson`, summaries, chat text or identifiers for this study.
2. Use chronological train/validation/test and rolling-origin backtests. Prevent
   events after each forecast origin from influencing class, period or window.
3. Compare against seasonal, last-interval and frequency baselines; report
   per-class and macro precision/recall/F1, timing MAE, calibration, coverage,
   false alerts per week and performance under drift/class imbalance.
4. Require repeated unseen-data improvement and define a named reviewable user
   action for each predicted class. A forecast must never mutate a task or
   trigger a tool automatically.
5. Add confidence thresholds, abstention, retention limits and monitoring before
   considering an opt-in read-only forecast surface.

## Value metric update

- `source_processing_result`: `prototype_validated_on_synthetic_soll_events`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-94b02ac6da81-f4c9532907757955-verification.md`
- `source_value`: the detector-style adaptation is executable and beats the
  focused baseline on the deterministic fixture; measurable production Soll
  value is still `unproven` until a privacy-safe chronological backtest passes.
