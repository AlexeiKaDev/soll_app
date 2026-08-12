# llama.cpp b10343 applicability audit

Date: 2026-08-12  
Task: `6994eacb97fe459e82fab113ae15bb66`  
Decision: **reject current Soll app integration; retain as a future standalone-server baseline**

## Verified upstream change

- [llama.cpp b10343](https://github.com/ggml-org/llama.cpp/releases/tag/b10343)
  is commit `e23e9440eb0c625c30d6c40266e9335071a4debc` and updates the vendored
  `cpp-httplib` from 0.52.0 to 0.53.0.
- [cpp-httplib v0.53.0](https://github.com/yhirose/cpp-httplib/releases/tag/v0.53.0)
  changes WebSocket result/TLS diagnostics, fixes mount-point matching on a path
  boundary, and adds bounded graceful socket draining before close.
- The llama.cpp release publishes an Android arm64 archive, but this does not
  create an Android application integration by itself.

## Soll app execution-seam check

The bounded production scan covered `app/src/main`, the app/root Gradle files,
`settings.gradle.kts`, and `gradle/` for `cpp-httplib`, `httplib.h`, `llama.cpp`,
`libllama`, and `ggml`.

Result: `production_matches=0`.

Soll app has no llama.cpp JNI/CMake/NDK runtime and continues to use the
authenticated Soll backend route. The repository's `tools/llama-cpp` area is a
separate verification harness and is not packaged into the APK.

## Decision and future trigger

Do not add the library, binary, native ABI, or a new Android networking stack
for b10343. That would duplicate Retrofit/OkHttp and increase APK/native attack
surface without a reachable product benefit.

If a future approved portable or Android standalone `llama-server` is promoted
to production, its pinned llama.cpp revision must be b10343 or newer before any
HTTP file mount or WebSocket endpoint is exposed, followed by hash/provenance,
TLS hostname, unauthorized mount-prefix, graceful-close, ABI, memory and device
smoke gates. Until that trigger exists, this release is watch-only.

## Verification

- Official GitHub release and commit API checked on 2026-08-12.
- Production dependency scan: `0` matches.
- Focused existing no-embedded-runtime and active-release policy tests are the
  regression gate for this review.
