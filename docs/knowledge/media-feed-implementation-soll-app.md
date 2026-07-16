# Media-feed implementation contract for `soll_app`

Status: technical guidance and promotion gates; no autoplay media feed is
approved or implemented by this document.

Source signal: Habr/Yandex article
**"Это уже тысячу раз делали: как мы добавили медиаленту в Яндекс Еду для
iOS. А потом переделали"**, 2026-06-19,
<https://habr.com/ru/companies/yandex/articles/1048718/>.

Source reference: `source-item/0d75242b770a/ce21bc830e1b94e0`.

## Decision and current scope

Treat a media feed as a resource-coordination system, not as a video player in
every list item. It combines pagination, visibility, network scheduling,
decoding, rendering, memory/cache ownership, lifecycle, loading UX and runtime
diagnostics. Those concerns need one explicit contract before a player appears
in production UI.

The current app does not yet have that contract and should not acquire an
autoplay subsystem from this source alone:

| Existing Soll seam | Current state | Consequence for a future media feed |
| --- | --- | --- |
| `TaskBoardScreen.SourcesMode` | one `LazyColumn` with source cards and text/image `SourceItemCard`s | keep one flat feed; extract a dedicated sources/media-feed presentation module before the card becomes stateful |
| `SollSourceItem` | title, URL, text previews, usefulness and an untyped `linkPreview` map | add a typed, versioned media descriptor server-side before playback; do not infer video behavior from arbitrary map keys |
| `RemoteLinkPreviewImage` | custom 48-entry in-memory LRU, a 2 MiB response cap and 192 px downsampling | useful bounded-image precedent, but entry count is not a byte budget and this loader must not grow into a video cache |
| `AppModule.provideOkHttpClient` | one shared Retrofit/OkHttp client for API work | future media transport needs one centrally configured connection/cache policy; do not add per-card clients or extend the direct `HttpURLConnection` preview path |
| `MusicPlaybackService` | Media3/ExoPlayer audio session with explicit service lifecycle | confirms Media3 availability, not permission to share the audio player/session with feed video |
| Chat link previews | digest text plus optional article image/link card | keep Chat as `Digest + article card`; autoplay belongs only in a separately approved `Источники` experience |

## Ten implementation practices adapted to Android

### 1. Define the system budget and typed contract first

Record functional behavior and non-functional budgets before implementation:
content types, focus/autoplay policy, page size, simultaneous decoders, player
slots, memory/cache bytes, network concurrency, time to first frame, frame
quality and lifecycle behavior.

The server-owned item contract should provide at least a stable item ID, media
kind, poster/first-frame URL, playable URL, dimensions, duration, estimated
bytes, accessibility label and page cursor. Optional or unsupported fields must
degrade to the existing article card. Do not silently treat `linkPreview` as a
video API.

### 2. Keep the list flat and coordination outside composables

Preserve one virtualized `LazyColumn`; do not place a media feed inside another
independently scrolling/re-measuring feed. Stable keys and content types must
survive pagination and filtering. Expensive parsing, image decoding and media
preparation stay off the main thread.

Before adding playback, move the source-item UI out of the already broad
`TaskBoardScreen` into a dedicated presentation module. A screen-level
coordinator owns focus, player leases, prefetch and metrics. Cards render
immutable state and emit intents; they must not coordinate one another or own
long-lived players.

### 3. Current visible media outranks prefetch

More parallel downloads do not create more bandwidth. Give the focused item
the highest priority, keep adjacent prefetch bounded, cancel work that leaves
the relevance window and suspend prefetch while the focused stream or other
Soll API traffic is constrained. Reduce both prefetch and page size on weak or
metered networks.

Use one centrally configured media transport/connection pool, validate HTTP
cache headers and compressed metadata, and record transferred bytes. Proxy
tools are suitable for finding N+1 requests, missing cache/compression and
oversized responses, but proxy timing must not be accepted as device-network
latency because interception can change HTTP/TLS behavior.

### 4. Lease a bounded player pool to visible items

Never create one ExoPlayer per item. The first prototype should allow one
playing item and at most one explicitly justified warm neighbour; any larger
pool must be configuration-backed and supported by measurements on target
devices. If no slot is available, the next card waits or shows its poster.

When an item loses focus, pause it, clear its media item, detach its rendering
surface and return the clean player to the pool. Release all players when the
owning screen/lifecycle is disposed, and stop autoplay when the app is not
foregrounded. The existing music session remains independent so feed video
cannot steal or corrupt its queue, audio focus or notification state.

### 5. Bound caches and buffering by bytes and lifetime

Cache posters and media with separate LRU byte budgets, TTLs and memory-pressure
eviction. A page leaving the retained window must not leave decoded bitmaps,
player surfaces, media items or coroutine work reachable. Never cache a player
inside a list item.

Tune buffering for short dwell time instead of downloading most of a clip a
user may skip after seconds. Prefer an efficient video representation over GIF
for moving content. Cache limits must be tested with long, mixed feeds; a cache
that only grows is a delayed leak, even if each individual entry is capped.

### 6. Use content policy as a resource control

Do not autoplay every visible video. The server should mix video with text and
image items and avoid clusters that require simultaneous decoders. Ranking,
pagination and filtering must preserve stable IDs and deterministic focus when
pages are inserted. Changing the content mix is often safer and more useful
than tuning ever more concurrent players.

### 7. Model perceived performance as explicit UI states

Every media card needs deterministic `poster`, `loading`, `ready`, `playing`,
`paused`, `failed`, `unsupported` and `network-restricted` states. Keep a poster
or validated first-frame replacement visible until a decoded frame is ready;
never present an unexplained black or empty rectangle. Retry is explicit and
must not start a duplicate hidden request.

Autoplay defaults and sound behavior require an approved product/accessibility
decision. Reduced-motion, data-saver and user autoplay preferences override
ranking intent.

### 8. Degrade deliberately for network, battery and thermal pressure

Define one policy function from lifecycle state, connectivity/metering, data
saver, battery and thermal status to page size, prefetch distance, quality,
autoplay and visual-effect limits. Under pressure, prefer the poster/article
card, reduce quality/page size, stop prefetch and remove expensive blur,
transparency or shadow effects before sacrificing UI responsiveness.

Degradation must be visible in diagnostics and reversible when conditions
recover. It must not create a second hidden behavior path without tests.

### 9. Make observability part of the architecture

Emit privacy-safe events for item focus, player acquire/release, prepare,
first frame, pause reason, playback error, rebuffer, prefetch start/cancel,
page load and degradation-policy changes. Correlate them with an opaque item
ID, page/session ID, app version, device class, network class and experiment
variant; do not log signed media URLs or content secrets.

Collect time to first frame, rebuffering, frame/jank data, main-thread stalls,
player/decoder counts, heap/native memory, cache bytes, network bytes and
battery/thermal state on real devices. Provide an opt-in debug panel that can
export a bounded trace. Local profiling alone is insufficient for intermittent
device/content combinations.

### 10. Test the long-feed failure modes before promotion

Use representative mixed fixtures, not three ideal clips. The focused smoke
must cover a 100-item feed, repeated pagination/filtering, fast forward/back
scroll, weak and metered networks, background/foreground, navigation away,
configuration change, playback failure and memory/thermal pressure on at least
one low-tier and one mid-tier real device.

Budgets and the comparison baseline are declared before the run. A simulator
or a successful short happy path cannot promote the feature.

## Required measurement record

| Metric group | Evidence required for a media-feed trial |
| --- | --- |
| Correctness/lifecycle | one focused item, deterministic transitions, no playback after stop/dispose, music session unaffected |
| Startup | time to poster and time to first frame at p50/p95, separated by network/device class |
| Playback | starts, failures, rebuffer count/duration and pause/cancel reasons |
| Scroll/render | frame-time/jank distribution during steady scroll and page insertion |
| Responsiveness | main-thread stall count/duration and trace for actionable outliers |
| Memory/resources | heap/native/cache bytes plus active player/decoder/surface counts; a plateau after repeated pages and return scroll |
| Network | bytes per viewed item, cancelled/wasted prefetch bytes, concurrent requests, cache hits and page-response bytes |
| Energy/thermal | battery/thermal observations and the exact degradation decisions they triggered |

## Promotion and rollback gates

A later implementation may ship only when all of the following are attached to
its task:

1. a separately approved user scenario and typed server media contract;
2. declared device/network matrix, baseline and budgets for all eight metric
   groups above;
3. focused unit tests for focus arbitration, player lease cleanup, prefetch
   cancellation, cache eviction and degradation policy;
4. a real-device long-feed trace showing bounded players, memory, cache and
   network work across repeated pages;
5. no black/empty loading state, autoplay after lifecycle stop, interference
   with music playback or regression in the existing text/image fallback;
6. production-safe telemetry plus a remote/feature-flag kill switch that
   returns `Источники` to article cards without changing the API contract.

Until those gates exist, the source provides measurable design/audit value but
zero measured media-feed runtime value. The correct current action is to keep
the practices in technical documentation and leave production behavior
unchanged.
