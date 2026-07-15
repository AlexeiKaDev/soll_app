---
task_id: 7c9f67f94d8140899ea66c5384a0d9eb
source_ref: source-item/0d75242b770a/84a6a99a52dd1e61
source_item: yaff-zero-copy-protobuf-cefec43f
source_processing_result: benchmarked_and_deferred
verification_artifact: Soll/outputs/source-processing/source-item-0d75242b770a-84a6a99a52dd1e61-verification.md
source_value: "validated research value; 0 measured Soll_app runtime value because the current mobile sync path is JSON/Moshi, not Protobuf/C++"
verified_at: 2026-07-15 Europe/Chisinau
---

# YaFF evaluation for `soll_app`

## Outcome

YaFF was built from the public upstream repository and its official access and
space benchmarks were run locally. The result is **defer production adoption**:
YaFF shows a real zero-copy advantage over Protobuf on its own C++ workloads,
but there is no Protobuf parsing or C++ data plane in the current Android sync
path, so this task did not demonstrate a measurable `soll_app` saving. No YaFF,
Protobuf, NDK, CMake, wire-format, API, or UI dependency was added.

This is a useful negative result rather than an implementation failure: it
prevents a native subsystem from being added to optimize work that this app does
not perform.

## Evidence reviewed

- Source article: `https://habr.com/ru/companies/yandex/articles/1047638/`.
- Upstream: `https://github.com/yandex/yaff`, Apache-2.0, version `0.1.0`.
- Pinned benchmark revision: `d6f74675374b587ce24112c284abd54a92090221`
  (`Add NOTICES`, 2026-06-24).
- The task-referenced raw file
  `raw/monitored\habr-yandex-company\20260702-194200-yaff-zero-copy-protobuf-cefec43f.md`
  is not vendored in this isolated `soll_app` worktree. The task record, public
  article, upstream source, documentation, benchmark code, and generated local
  binaries were used instead.

Upstream facts relevant to the decision:

- YaFF is a C++20 serialization library and code generator that reuses `.proto`
  schemas and offers Flat, Sparse, and Dynamic zero-copy layouts.
- Its integration is CMake/Conan based and its generated runtime API is C++.
- Multi-language bindings and a columnar layout are roadmap items, not current
  integration surfaces.
- `yaff::ReadMessage` does not validate offsets or lengths. Upstream explicitly
  treats malformed or hostile input as undefined behavior and currently has no
  equivalent of the FlatBuffers verifier.

## Current Soll app data path

The audited mobile path is:

```text
GET /api/v1/android/sync-status (JSON)
  -> Retrofit + Moshi -> AndroidSyncStatusResponse
  -> SollRepository.toDomain()
  -> task cache / notifications / UI
  -> Moshi JSON copy in SharedPreferences for offline fallback
```

Evidence in this repository:

- `SollApiService.kt` declares the JSON Retrofit endpoint and the
  `AndroidSyncStatusResponse` DTO.
- `SollRepository.kt` creates a Moshi adapter, maps the DTO to domain models,
  and serializes the same DTO back to JSON for its offline cache.
- `SollServerSyncWorker.kt` consumes the mapped task/chat data.
- `app/build.gradle.kts` uses Retrofit, Moshi, and OkHttp and has no Protobuf,
  YaFF, CMake, NDK, JNI, or `externalNativeBuild` dependency.

Therefore the local YaFF-versus-Protobuf benchmark is a capability probe, not a
benchmark of the current Soll app pipeline.

## Local benchmark

### Setup

- Host: Intel Core i9-14900KF, 32 logical CPUs, 36 MiB L3.
- Runtime: WSL2 Ubuntu 24.04, Linux x86-64.
- Compiler: GCC 13.3.0, C++20, Release build.
- YaFF: `0.1.0` at `d6f74675374b587ce24112c284abd54a92090221`.
- Dependencies selected by the upstream Conan recipe: Protobuf `7.35.0`,
  Google Benchmark `1.9.5`, FlatBuffers `24.12.23`.
- Access results are CPU-time means over five repetitions with a `0.2s`
  minimum run time. Space results are the deterministic one-iteration size
  probes provided by upstream.
- All downloaded source, packages, tools, binaries, and raw benchmark output
  stayed under the ignored repository-local `build/` directory. No credentials
  or global Conan state were used.

Build recipe from the worktree root:

```bash
git clone --depth 1 https://github.com/yandex/yaff.git build/yaff-upstream
python3 -m venv build/yaff-tooling
. build/yaff-tooling/bin/activate
python -m pip install cmake ninja conan
export CONAN_HOME="$PWD/build/yaff-conan-home"
conan profile detect --force
conan install build/yaff-upstream --output-folder=build/yaff-build \
  --build=missing -s build_type=Release -o '&:build_benchmarks=True'
conan build build/yaff-upstream --output-folder=build/yaff-build \
  -s build_type=Release -o '&:build_benchmarks=True'
```

### Flat read access

Mean CPU time in nanoseconds to parse/read all fields in the upstream workload:

| Format | 10 fields | 100 fields |
| --- | ---: | ---: |
| Raw C++ | 0.89 | 9.01 |
| Protobuf | 32.64 | 661.00 |
| FlatBuffers | 2.49 | 19.41 |
| YaFF Flat | 6.33 | 110.56 |
| YaFF Sparse | 2.64 | 184.47 |

On this GCC/WSL host, YaFF was `5.2x` (10 fields) and `6.0x` (100 fields)
faster than the Protobuf case in Flat layout. Sparse was `12.4x` and `3.6x`
faster, respectively. YaFF did not beat FlatBuffers on this flat-field run, so
the upstream cross-compiler ratio must not be copied into a Soll business case.

### Hierarchical read access

Mean CPU time in nanoseconds, with uncached access chains and no intervening
modification:

| Format | Hot, ~2 KiB set | Cold, 512 MiB set |
| --- | ---: | ---: |
| Raw C++ pointer | 5.50 | 180 |
| Protobuf | 111 | 307 |
| FlatBuffers | 14.9 | 278 |
| YaFF Flat | 7.55 | 238 |
| YaFF Sparse | 15.4 | 236 |

YaFF Flat was `14.7x` faster than Protobuf for the hot hierarchy but only
`1.29x` faster for the cold workload where memory latency dominates. YaFF
Sparse was `7.2x` and `1.30x` faster. The two cold YaFF cases were run in
separate processes to keep each upstream 512 MiB fixture bounded.

### Serialized size

Average bytes reported by the upstream maximum-`uint64` workload:

| Case | Protobuf | FlatBuffers | YaFF Flat | YaFF Sparse |
| --- | ---: | ---: | ---: | ---: |
| 5 fields, 100% populated | 49.96 | 64.00 | 48.00 | 55.00 |
| 50 fields, 100% populated | 534.60 | 512.00 | 419.00 | 479.00 |
| 50 fields, 25% populated | 137.59 | 211.37 | 395.10 | 176.29 |
| 50 fields, 5% populated | 31.77 | 108.33 | 294.58 | 76.73 |

The dense 50-field YaFF Flat buffer was `21.6%` smaller than Protobuf, while
the 5%-populated Flat buffer was `9.3x` the Protobuf size. Sparse layout reduces
that penalty but was still `2.4x` the Protobuf size at 5% density. Layout and
real field distributions must therefore be measured, not assumed.

## Prototype integration design (proposal only)

The smallest safe prototype keeps the Android public contract unchanged:

```text
trusted internal producer
  -> immutable versioned .proto snapshot
  -> C++ YaFF writer -> checksum + schema id -> local mmap file
  -> C++ read-only query/filter sidecar
  -> existing JSON /api/v1/android/sync-status response
  -> unchanged Retrofit/Moshi Android client
```

This prototype is a trusted internal C++ read-only snapshot/filter sidecar. It
is relevant only if profiling later finds a C++ service that repeatedly parses a large,
immutable Protobuf snapshot before selecting the small task/chat/source subset
sent to Android. Converting YaFF straight back to JSON without doing substantial
server-side filtering would pay conversion complexity while preserving the
mobile JSON cost.

An Android/NDK YaFF decoder is rejected for the current stage because:

1. YaFF has no Kotlin/Java binding.
2. The current endpoint is JSON, not Protobuf.
3. A network response is untrusted input, while YaFF currently has no structural
   verifier and documents malformed reads as undefined behavior.
4. JNI, native packaging, schema negotiation, fallback, and crash handling would
   be a new subsystem with no measured bottleneck.

### Promotion gates

Before any implementation task is created:

1. Instrument representative syncs and record response bytes, Moshi decode
   CPU/wall time, allocation pressure, end-to-end sync time, and battery impact
   at p50/p95. A candidate needs at least a repeatable 5% CPU share or 5 ms p95
   decode/read hotspot in the component that YaFF can actually replace.
2. Identify a trusted C++ producer/consumer boundary with immutable data and a
   lifetime owner for every zero-copy view. If none exists, stop.
3. Benchmark the real schema and captured non-sensitive size/density histogram
   against the current format. Require at least 20% p95 CPU reduction, no more
   than 10% payload/storage regression, and no end-to-end latency regression.
4. Add schema-version, checksum, maximum-size, producer identity, corruption,
   compatibility, golden round-trip, fuzz, and fallback tests. Never pass remote bytes directly to `ReadMessage`.
5. Keep the JSON endpoint as the rollback path until one release of parity and
   operational evidence is complete.

## Value metric update

- `source_processing_result`: `benchmarked_and_deferred`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-0d75242b770a-84a6a99a52dd1e61-verification.md`
- `source_value`: YaFF is validated as a promising C++/Protobuf internal format,
  but current measured Soll app runtime value is `0` because its data pipeline
  is JSON/Moshi. Reopen only after a qualifying C++/Protobuf hotspot is measured.
