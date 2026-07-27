---
task_id: 6b3d4519fd40430ba1627de15dee3d40
project: soll_app
source_ref: source-item/d0cd9479f2a2/04ee2c4d8c554c7f
source_item: llama-cpp-releases-b9924
source_processing_result: android_arm64_package_verified_runtime_update_rejected_as_downgrade
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-04ee2c4d8c554c7f-verification.md
source_value: "1 focused audit; 25/25 PR checks and 5/5 Android/release jobs verified; 44/44 Android binaries passed ELF64 AArch64 validation; 0/869 APK entries matched b9924; active b10068 verified 144 commits ahead; 1/1 focused contract test passed; 0 production/runtime changes and 0 device/model inference runs"
verified_at: 2026-07-27 Europe/Chisinau
---

# llama.cpp b9924 safe local-inference verification

## Outcome

The official b9924 Android arm64 CPU archive is package/ABI compatible:
published size and SHA-256 match locally, tar integrity passed, the required
CLI/server/library files are present, and all `44/44` binary files are ELF64
little-endian AArch64.

Do not update the isolated Soll/Soll_app runtime to b9924. Active standalone
baseline b10068 is already `144` commits ahead and contains b9924. A b9924 pin
would also fall below the repository's b9945 chat-template-fix gate. Android
keeps `soll-backend-route`; no llama.cpp binary is packaged into the APK.

The durable analysis is
`docs/knowledge/llama-cpp-b9924-safe-local-inference-audit.md`.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b9924` -> `90e0f5cfcb6cdb4b7b60a4f81b0a26e542149ad5`, published 2026-07-08 |
| Changelog | one item: `llama: refactor fused ops (#24646)` |
| PR scope | 4 commits; 6 source files; `+122/-131`; no Android build, ARM CPU kernel or `ggml/src` change |
| Performance evidence | 0 Android benchmark, tokens/s, TTFT, memory, thermal or power results |
| PR CI | head commit: `25/25` checks successful |
| Android CI | `arm64`, `default`, `ndk`: `3/3` successful |
| Release CI | `android-arm64` and release creation/upload: `2/2` successful |
| Broader CI caveat | Ubuntu x64 CPU Test and Server sanitize Python setup failed; Ubuntu arm64 CPU was cancelled |
| Android asset | 78812406 bytes; published/local SHA-256 match |
| ABI smoke | 46 tar entries; 44 binaries; 21 shared libraries; `44/44` ELF64 little-endian AArch64 |
| Required files | `llama-cli`, `llama-server`, `libllama.so` present |
| Soll APK | `assembleDebug` passed; `0/869` entries matched b9924/CLI/server/libllama |
| Regression note | open #25644 covers mismatched multi-RPC placement and says `Not a regression`; not the single-device Android CPU path |
| Current Soll policy | b10068, `packageIntoAndroidApp=false`, `soll-backend-route`, deny-by-default models |
| Runtime proof | adb unavailable; `0` device/model inference runs and no performance claim |
| Product change | documentation, verification artifact and contract test only |

The task-referenced monitored source is not vendored in this isolated worktree.
Release, PR, issue, compare and Actions metadata were read from official
upstream surfaces without attributing unavailable raw content.

## Safety decision

The archive was downloaded only to ignored cache and inspected. `llama-server`
was not executed; HTTP, RPC, remote model loading, network-capable tools and
autonomous agent features were not enabled. The active manifest, model
allowlist, dependencies, Android API and APK contents remain unchanged.

Package/ABI compatibility does not prove runtime compatibility on a specific
phone. A future release newer than b10068 needs a checksummed, CPU-only,
direct-CLI device comparison with one warm-up and five measured repeats,
correctness/resource/thermal gates, no network or tools, and retained
b10068/backend rollback.

## Focused contract test

`LlamaCppB9924SafeLocalInferenceAuditTest` guards:

- release, PR, asset digest, CI and b10068 ancestry evidence;
- the exact `44/44` ELF64 AArch64 package/ABI result;
- absence of upstream performance proof and the RPC regression caveat;
- unchanged backend-route, no-APK, deny-by-default local-inference policy;
- the rejection of a b9924 downgrade and the quantified value metric.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9924SafeLocalInferenceAuditTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `android_arm64_package_verified_runtime_update_rejected_as_downgrade`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-04ee2c4d8c554c7f-verification.md`
- `source_value`: `1` focused audit; `25/25` PR checks and `5/5`
  Android/release jobs verified; `44/44` Android binaries passed ELF64
  AArch64 validation; `0/869` APK entries matched b9924; active b10068
  verified `144` commits ahead; `1/1` focused contract test; `0`
  production/runtime changes and `0` device/model inference runs.
