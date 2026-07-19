---
task_id: c91d8e4aacb74e12b82c16b9b79c5358
source_ref: source-item/d0cd9479f2a2/e94cf86e6b4a008b
source_processing_result: documented_not_applicable_no_apple_metal_target
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-e94cf86e6b4a008b-verification.md
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b9891 Metal applicability verification

The official b9891 release and merged PR #25176 were checked against the
current Soll app build/runtime. The change adds Metal `COL2IM_1D` for
f32/f16/bf16 and tightens its `supports_op` predicate. Soll app has no Apple
target, Metal backend, CMake, externalNativeBuild or JNI/libllama integration;
its user-facing AI runtime remains `soll-backend-route`.

No Android dependency or production build update is justified. The standalone
llama.cpp verification baseline is already b10068, newer than b9891, but its
Windows/Android smoke does not claim Metal coverage.

Primary evidence:

- <https://github.com/ggml-org/llama.cpp/releases/tag/b9891>
- <https://github.com/ggml-org/llama.cpp/pull/25176>
- release commit `f36e5c348bc8795c34f9a038e58876e7a8423d4d`
- upstream Apple M2 `test-backend-ops -o COL2IM_1D` passed for f32/f16/bf16

Repository evidence:

- `0` Apple/Metal production targets;
- `0` CMake/externalNativeBuild/JNI/libllama app seams;
- `0` Android dependency changes;
- `0` local Metal builds or benchmarks claimed;
- `1` knowledge entry added with an explicit reopen condition.

This task is complete as `documentation/documented_not_applicable`, not as an
implementation. A future Apple llama.cpp target must open a new measured task
with device, workload, CPU-reference correctness, latency and memory evidence.
