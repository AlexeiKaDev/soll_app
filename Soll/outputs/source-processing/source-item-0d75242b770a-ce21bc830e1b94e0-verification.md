---
task_id: 50e5cf3183d143a9b3368930443a2888
source_ref: source-item/0d75242b770a/ce21bc830e1b94e0
source_item: ios-media-feed-yandex
source_processing_result: best_practices_documented_and_adapted
verification_artifact: Soll/outputs/source-processing/source-item-0d75242b770a-ce21bc830e1b94e0-verification.md
source_value: "10 implementation practices extracted; 6 existing Soll seams audited; 8 promotion metric groups specified; 0 measured media-feed runtime value because no player feature was implemented"
verified_at: 2026-07-15 Europe/Chisinau
---

# Yandex media-feed best-practices audit for `soll_app`

## Outcome

The source's media-feed lessons were converted into the durable Android
technical contract at
`docs/knowledge/media-feed-implementation-soll-app.md`. The contract records
ten implementation practices, maps six current repository seams and defines
eight measurement groups plus promotion/rollback gates.

This completes the documentation objective without mistaking research for
feature approval. No autoplay UI, player pool, prefetcher, cache, network
client, API field, dependency or production behavior was added.

## Evidence reviewed

- Public source article:
  `https://habr.com/ru/companies/yandex/articles/1048718/`, "Это уже тысячу раз
  делали: как мы добавили медиаленту в Яндекс Еду для iOS. А потом переделали".
- Task source reference: `source-item/0d75242b770a/ce21bc830e1b94e0`.
- The task-referenced raw snapshot
  `raw/monitored\habr-yandex-company\20260702-194200-ios-04deb760.md` is not
  present in this isolated worktree. The public article and the existing
  repository were used; no missing raw content was invented.
- `TaskBoardScreen.kt`: the current `Источники` lane is one `LazyColumn` of
  source and text/image item cards.
- `SollGateway.kt`: `SollSourceItem` has text fields and an untyped
  `linkPreview`, with no playable media descriptor.
- `RemoteLinkPreviewImage.kt`: preview loading has a 48-entry in-memory LRU,
  2 MiB response cap and 192 px sampling, but uses a direct connection path
  and is not a video cache.
- `AppModule.kt`: API traffic has a shared Retrofit/OkHttp client.
- `MusicPlaybackService.kt`: Media3 currently serves the independent audio
  queue/session and is not a feed player.
- `ChatScreen.kt`: link previews remain digest/article cards without autoplay.

## Extracted and adapted practices

| # | Source lesson | Soll technical rule |
| ---: | --- | --- |
| 1 | a media feed is several coupled subsystems | declare a typed media contract and resource/UX budgets first |
| 2 | deep nested lists/layout scale poorly | keep one flat virtualized list and move coordination outside cards |
| 3 | parallel preloads compete with focused playback | Current visible media outranks prefetch; bound and cancel speculative work |
| 4 | one heavy player per cell grows resources | lease a small lifecycle-owned Media3 pool and detach off-screen surfaces |
| 5 | premature caches can become multi-gigabyte leaks | bound cache and buffering by bytes/lifetime and verify a post-scroll plateau |
| 6 | simultaneous videos may be a content-policy defect | mix server-ranked content and avoid autoplaying every visible item |
| 7 | users perceive blank/black startup as failure | model poster/loading/ready/failure/network states explicitly |
| 8 | network, battery and heat change the safe policy | degrade page size, prefetch, quality, autoplay and effects deliberately |
| 9 | local profiling misses intermittent real-device defects | design privacy-safe first-frame, rebuffer, jank, stall, memory and resource telemetry up front |
| 10 | tiny ideal fixtures hide pagination/lifecycle leaks | gate on representative 100-item real-device long-feed scenarios |

The source's pool size of five and iOS frame-layout implementation are not
copied as Android constants. The contract preserves the underlying principles
and requires Android/Compose/Media3 measurements to choose concrete limits.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Technical document exists | durable media-feed guidance under `docs/knowledge` | PASS |
| Best practices are source-backed | network, layout, lifecycle, memory, UX, degradation and observability covered | PASS |
| Guidance is Soll-specific | six real Android seams and the current no-video contract are recorded | PASS |
| Resource policy is bounded | player, prefetch, cache, pagination and lifecycle ownership are explicit | PASS |
| Promotion is measurable | eight metric groups and a 100-item real-device smoke are required | PASS |
| Existing behavior is preserved | Chat/source cards and music playback remain unchanged | PASS |
| Value metrics are attached | result, artifact path and quantified documentation value are present | PASS |

`YandexMediaFeedSourceTriageTest` is the focused repository audit. It verifies
the roadmap pointer, all ten durable practice anchors, the eight metric groups
and this artifact's value fields.

## Product decision

Keep implementation **not approved** until a concrete `Источники` media use
case and typed server contract exist. Chat remains `Digest + article card`.
When a feature task is approved, start with one flat sources feed, one focused
player, at most one justified warm neighbour, bounded cancellation-aware
prefetch, explicit fallback states and a kill switch. Do not share the music
session or extend the direct preview-image loader into a media subsystem.

## Value metric update

- `source_processing_result`: `best_practices_documented_and_adapted`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-0d75242b770a-ce21bc830e1b94e0-verification.md`
- `source_value`: 10 implementation practices were extracted, 6 existing Soll
  seams were audited and 8 promotion metric groups were specified. This is
  measurable technical-documentation and architecture-audit value; measured
  media-feed runtime value remains `0` because the task did not implement or
  run a player feature.
