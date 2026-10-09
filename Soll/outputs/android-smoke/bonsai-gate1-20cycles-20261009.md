# Bonsai 27B Android P0 spike — gate 1 result (2026-10-09)

Automated instrumented test (`BonsaiCompatibilitySpikeTest`), disposable
harness built from official upstream `examples/llama.android`
(`ggml-org/llama.cpp` commit `e60eff95fda7b6e46bf402a8e9f1e86d88ab29c8`),
run directly via `am instrument` on the named target device: DOOGEE S200
Plus, Android 15, arm64-v8a, MT6878/Mali, 15.9GB RAM.

Model: `prism-ml/Bonsai-27B-gguf` revision `f10afb355fda7b6e46bf402a8e9f1e86d88ab29c8`,
file `Bonsai-27B-Q1_0.gguf`, 3,803,452,480 bytes, SHA-256
`17ef842e47450caeb8eaa3ebfbbab5d2f2278b62b79be107985fb69a2f819aa0` (verified
against the model card before and after transfer to device).

20 cold `loadModel -> sendUserPrompt(take 5 tokens) -> cleanUp` cycles, no
UI involved (direct `InferenceEngine` calls). Total run time 547.1s.

## Gate 1 — compatibility: PASS

20/20 cycles completed with `ok=true`, zero exceptions, zero native
crashes, zero ANR. Raw per-cycle data: `bonsai-gate1-20cycles-20261009.json`.

## Memory: clean, no leak across 20 cycles

| Metric | Value |
| --- | --- |
| PSS after unload (baseline), cycles 2-20 | 85.9-103.1 MB, no drift |
| PSS after load, steady state | 7.76-7.86 GB |
| Native heap after unload, cycle 1 -> 20 | 9,301 KB -> 9,452 KB (+1.6%, negligible) |

Caveat: this test did not explicitly cap context length (the `InferenceEngine`
API has no context-length parameter), so the model loaded at whatever
context the engine/GGUF defaults resolve to -- not the 4K cap the
feasibility doc's safety policy calls for. The measured ~7.8GB steady-state
footprint is therefore higher than the ~5.2GB predicted for a properly
capped 4K-context load, and is not directly comparable to that budget.

## Gate 3 — speed: likely FAIL

| Metric | Value | Required (gate 3) |
| --- | --- | --- |
| Model load time | 19.3-20.5s (avg ~19.5s), consistent | not specified, but adds to perceived latency |
| Time for first 5 generated tokens | 5.57-12.34s (avg 6.78s) | -- |
| Implied average decode rate | **~0.74 tokens/sec** | p50 >= 8 tok/s |

Average decode throughput is roughly **10x slower** than the gate 3 bar.
This matches the feasibility doc's own stated risk ("Android-путь не
воспроизводит iPhone tok/s — высокая вероятность"). No Vulkan backend was
tested in this pass (CPU/NEON only, per the P0 scope); Vulkan is listed as
a second profile to try, but is unlikely to close a 10x gap on its own for
a 27B 1-bit model on this SoC.

## Not yet attempted

Gate 2's full 30-minute 20-turn soak, gate 4 (thermal/battery), gate 5
(quality, 60-prompt corpus), gate 6 (tool safety, 50+ adversarial prompts),
gate 7 (privacy/offline), gate 8 (delivery/rollback) were not run. Given
the gate 3 result, further gate work should wait for an owner decision on
whether the speed gap is acceptable for any realistic use case before
investing more time.

## Known issue found and fixed in the disposable harness (not upstream)

- `app/build.gradle.kts` had `isMinifyEnabled = true` for the debug build
  type, which caused R8 to strip Kotlin stdlib/coroutines `$default`
  synthetic bridge methods not used by the original app, breaking any new
  test code that calls stdlib functions with default arguments
  (`NoSuchMethodError` for `runBlocking$default`, `endsWith$default`).
  Fixed locally by disabling debug minification; this is a harness-only
  change in `D:/AI/bonsai-android-spike`, not upstream or soll_app.
- `connectedDebugAndroidTest` installs and uninstalls both APKs around each
  run, which wipes the model file from app-private storage every time;
  worked around by installing once, manually staging the model into app
  storage via `adb push` to `/data/local/tmp` + `run-as cp` (direct
  `run-as cp` from `/sdcard` is blocked by scoped storage even for the
  owning app), then invoking the test directly via
  `adb shell am instrument` (which does not uninstall afterward).
- A first failed test attempt (interrupted mid-load by the above R8 bug)
  left an orphaned `llama-server` native process holding ~4.5GB RAM after
  the Java-side test process exited, causing real, user-visible device lag
  on the owner's actively-used phone. Killed manually
  (`adb shell kill -9 <pid>`); going forward, any interrupted/crashed test
  run must be followed by an explicit check for and kill of leftover
  `llama-server` processes before the device is handed back to normal use.
