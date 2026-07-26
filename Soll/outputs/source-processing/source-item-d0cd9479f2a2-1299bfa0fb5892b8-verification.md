---
task_id: f677a485a77841808898a65f53258cd9
source_ref: source-item/d0cd9479f2a2/1299bfa0fb5892b8
source_processing_result: llama_cpp_b9917_security_baseline_enforced
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-1299bfa0fb5892b8-verification.md
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b9917+ GGUF security verification

## Result

The repository had no production llama.cpp/JNI integration and no GGUF files,
but its standalone binary smoke still pinned b9895. The active verification
manifest now pins official b10068 commit
`571d0d540df04f25298d0e159e520d9fc62ed121`, which is newer than the b9917
UGM tokenizer security fix. The Android app continues to use
`soll-backend-route`; no native binary or model is packaged into the APK.

Official evidence:

- <https://github.com/ggml-org/llama.cpp/releases/tag/b9917>
- <https://github.com/ggml-org/llama.cpp/pull/18750>
- <https://github.com/ggml-org/llama.cpp/releases/tag/b10068>

The two b9917 advisories cover out-of-bounds reads in UGM tokenizer
`precompiled_charsmap` handling. A malicious T5/UGM GGUF could trigger a
heap-buffer-overflow, crash or information disclosure. b10068 is the selected
active baseline because it includes the fix and was already separately audited
for its DFlash change.

## Controls and evidence

- `llama_cpp_active_defaults.json`: b10068, minimum b9917, two official release
  assets with exact byte counts and SHA-256, no Android packaging.
- `approved_models.json`: `deny_unlisted`, `0` approved models.
- `Test-LlamaCppModelProvenance.ps1`: exact file name + SHA-256, HTTPS source and
  immutable revision required; wrong extension, unlisted hash or external
  allowlist path fails closed.
- `Invoke-LlamaCppVerifiedModel.ps1`: repository-sanctioned launcher calls the
  provenance gate before passing `-m` to the pinned CLI.
- Local recursive audit: `0` `.gguf` files in `D:/AI/Models`, Soll and soll_app.
- Production audit: `0` CMake/JNI/libllama integration points in the app.

## Verification contract

`LlamaCppB9917SecurityBaselineTest` validates the active release/commit,
security floor, official package hashes, deny-all initial allowlist, gated
launcher, primary-source links, zero-GGUF evidence and the unchanged backend
route. A direct PowerShell negative smoke must reject an unlisted synthetic
`.gguf`; the release smoke must execute the b10068 Windows CLI/server versions
and validate the Android archive ABI.

## Executed verification

- `Test-LlamaCppActiveRelease.ps1`: PASS for `2/2` targets. Both downloaded
  archives matched the official byte count and SHA-256.
- Windows x64 CPU: `51` archive entries; `llama-cli.exe` and
  `llama-server.exe` both exited `0` and reported `10068 (571d0d540)`.
- Android arm64 CPU: `46` archive entries; `44/44` non-license files passed
  ELF64 little-endian AArch64 validation, including `llama-cli`,
  `llama-server` and `libllama.so`.
- Negative provenance smoke: an unlisted synthetic `.gguf` was rejected before
  any CLI invocation with `not approved by exact file name and SHA-256`.
- `LlamaCppB9917SecurityBaselineTest`: PASS (`1/1`).
- Full debug JVM suite and APK build: PASS, `381/381` tests with `0` failures,
  `0` errors and `0` skipped.
- Debug APK audit: `869` entries and `0` names matching b10068 binaries,
  libllama, the model allowlist or `.gguf` files.

## Value

- vulnerable standalone default superseded: `b9895 -> b10068`;
- minimum safe release: `b9917`;
- checksummed active archives: `2`;
- approved GGUF models: `0`;
- unverified model-load paths added: `0`;
- APK/native runtime changes: `0`.
