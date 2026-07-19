# AI Race Coach: safe edge-coaching patterns for Soll app

## Task trace

| Field | Value |
| --- | --- |
| Task | `3689d0cc6ff347b5a79ca0196da72ab9` |
| Project | `fdf52463-9152-453a-b186-68e7d76c3edb` |
| Source reference | `insight/231da40935d9` |
| Monitored source | `monitored/google-developers-blog/20260709-204007-bridging-the-domain-gap-ai-race-coach-built-with-3a06c56a.md` |
| Application | AI Race Coach pattern |

## Research result

The Google Developers Blog case study, [Bridging the Domain Gap: AI Race Coach
built with Antigravity and
Gemini](https://developers.googleblog.com/bridging-the-domain-gap-ai-race-coach-built-with-antigravity-and-gemini/),
describes a hybrid edge/cloud telemetry and coaching pipeline. The named
monitored Markdown snapshot is not present in this isolated worktree, so this
note keeps the task-provided trace and uses the public primary article only to
bound the signal.

This is a **case-study signal, not a production specification**. The useful
result for Soll is a set of architecture and validation patterns. It is not an
approval to add Google Antigravity, ADK, Gemini, Gemma, a vehicle interface, an
on-device model, cloud telemetry upload or a new driver-facing product surface.

## Six safe patterns

**1. Split the latency paths.** Keep freshness checks, deterministic safety
rules and time-sensitive alerts on the device. Move optional summaries and
trend analysis to an asynchronous path. Loss of connectivity must not move a
deadline-sensitive decision to a slower cloud route.

**2. Make degraded operation explicit.** Treat `LOCAL_READY`, `CLOUD_READY`,
`DEGRADED_OFFLINE` and `STALE_INPUT` as observable states. In an offline state,
only previously validated local rules may continue; cloud work waits, the UI
shows the degraded state and stale input suppresses coaching.

**3. Ground advice before delivery.** Normalize units, retain source and sample
time, reject impossible or stale values, and attach the triggering evidence to
every recommendation. Generative text is advisory. It never reaches a device
actuator, bypasses a confirmation gate or overrides a deterministic limit.

**4. Bound local alerts.** Prefer a short notification or TTS message that
contains one action and one reason. Alerts need severity, deduplication,
cooldown, acknowledgement/cancel behavior and a visual evidence trail. A queue
must drop superseded low-priority advice rather than speaking an unsafe backlog.

**5. Measure the telemetry path without copying sensitive payloads.** Record
sample age, parse/quality failures, dropped and reordered samples, processing
latency, selected local/cloud route, alert latency and delivery outcome. Use
counts and coarse timings by default; raw sensor/user content requires an
explicit retention and consent contract.

**6. Promote from replay, not from a demo.** Start with synthetic or explicitly
consented recorded traces, domain-reviewed expected outcomes and a deterministic
baseline. Exercise normal, threshold-crossing, missing, stale, reordered and
offline cases before any live alert. Promote one capability at a time only when
measured accuracy, latency and resource budgets pass.

## Fit with the current Soll repository

The audit found four reusable seams. They are useful placement points, not proof
that an AI Race Coach already exists.

| Existing seam | Safe reuse | Missing contract before a pilot |
| --- | --- | --- |
| `GadgetCloudSnapshot`, `GadgetPayloadParser` and `GadgetSensorCatalog` | typed presentation of current gadget telemetry and deterministic sensor status | sample provenance/units, ordered buffering, quality flags and an enforced freshness budget |
| `SollNotificationChannel.ALERTS`, `SollNotificationRequest.dedupeKey` and `SystemNotificationDisplayPolicy` | user-controlled local alert delivery | telemetry-specific severity, cooldown, acknowledgement and supersession rules |
| `TextToSpeechManager` | existing local speech delivery | a short alert queue with priority, interruption, rate limits and fallback evidence |
| `SollServerSyncWorker` and the existing repository caches | network constraints, retry/backoff and visible cached server state | a bounded telemetry outbox, retention/consent policy and explicit degraded-mode state |

No production AI Race Coach, telemetry-to-alert binding or model route is added
by this task. Existing cache/retry and TTS behavior must not be relabelled as a
complete offline coaching fallback.

## Six adoption claims deliberately excluded

1. No Antigravity, ADK, Gemini, Gemma or other source-named dependency/API is
   selected from this case study.
2. The article's `10 Hz` input and `40 tokens per second` observations are not
   Soll performance targets or benchmarks.
3. Pixel 10, its TPU and the custom vehicle USB interface are not Soll hardware
   requirements.
4. No automatic cloud sync or raw telemetry upload is enabled.
5. No generated recommendation may directly actuate a gadget or make a
   high-stakes decision.
6. The reported track result is not counted as measured Soll value.

## Bounded follow-up experiment

If a concrete gadget-health or activity-coaching need is approved, the first
experiment should remain read-only and replay-only:

1. prepare consented or synthetic normal, threshold, stale and offline traces;
2. run a deterministic threshold/freshness baseline locally;
3. produce at most one deduplicated local alert with its source evidence;
4. keep any optional summary asynchronous and sanitized;
5. compare false positive/negative counts, missed/duplicate alerts, sample-age
   rejection, alert latency p50/p95, UI jank, battery cost and network payload
   count against the baseline;
6. require `0` actuator commands and `0` raw payload uploads for the replay.

Promotion requires named domain ownership, explicit consent/retention rules,
fixed freshness and resource budgets, offline and stale-input tests, a kill
switch, and real-device evidence. Until then, measured runtime improvement,
live alerts and on-device inference runs are all `0`.
