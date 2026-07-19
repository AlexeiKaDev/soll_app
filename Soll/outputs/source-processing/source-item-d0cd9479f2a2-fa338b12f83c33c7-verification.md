# Source verification: llama.cpp b9897 SYCL environment migration

Date: 2026-07-19
Task id: `0b6087e2f99c43a8b4956a25e07d9d47`
Source ref: `source-item/d0cd9479f2a2/fa338b12f83c33c7`
Knowledge artifact: `docs/knowledge/llama-cpp-b9897-sycl-env-migration.md`

## Result

`source_processing_result: sycl_migration_documented_runtime_not_applicable`

`verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-fa338b12f83c33c7-verification.md`

`source_value: knowledge_only`

The official b9897 release, commit `26145b3`, merged PR #25042 and its file
patch were reviewed. The source signal is valid, but it does not apply to the
active Soll runtime.

## Evidence

- `GGML_SYCL_DISABLE_OPT`, `GGML_SYCL_DISABLE_GRAPH` and
  `GGML_SYCL_DISABLE_DNN` became the corresponding `ENABLE_*` variables.
- Existing explicit values require boolean inversion; upstream defaults still
  mean optimization enabled, graph disabled and oneDNN enabled.
- The compile-time macro `GGML_SYCL_USE_VMM` became
  `GGML_SYCL_SUPPORT_VMM`; it is not an environment variable.
- Main Soll uses WSL vLLM with safetensors and NVIDIA/CUDA, not llama.cpp SYCL.
- Android uses `soll-backend-route`; production app has no native llama.cpp
  build or JNI seam.
- `D:/AI/Models` contains 0 GGUF files at audit time.
- The release asset matrix was not interpreted as newly added platform support
  or as a list of runtimes installed in Soll.

## Validation

- 1 knowledge artifact added.
- 3 upstream SYCL environment renames and 6 explicit value mappings recorded.
- 3 current Soll execution surfaces audited; 0 applicable SYCL seams found.
- 0 production/runtime files changed.
- 0 local b9897 build or inference runs claimed without a target device and
  workload.
- `LlamaCppB9897SyclEnvMigrationTest`: focused Android JVM contract test.

## Reopen gate

Reopen implementation only after an Intel SYCL device, oneAPI toolchain,
checksummed GGUF model and measurable workload are explicitly selected. Keep
the current backend route as rollback until that separate benchmark passes.
