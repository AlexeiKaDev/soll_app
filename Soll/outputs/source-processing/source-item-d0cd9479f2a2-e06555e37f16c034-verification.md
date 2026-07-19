---
task_id: fc4ed0d15e83406abd702e47040d3a99
source_ref: source-item/d0cd9479f2a2/e06555e37f16c034
source_item: llama-cpp-releases-b9898
source_processing_result: b9898_binaries_verified_ci_updated
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-e06555e37f16c034-verification.md
source_value: "2 release archives downloaded and checksummed; 2 Windows executables launched; 44/44 Android binary files passed ELF64 AArch64 validation; 24/24 binary release packages pinned across 22 selectable targets; 0 binaries packaged into the APK and 0 model inference runs"
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b9898 binary release smoke

## Outcome

The official `ggml-org/llama.cpp` release `b9898` at commit
`3d4cbdf18a72c97648c15e2dc060013c186cd36c` is pinned by
`tools/llama-cpp/llama_cpp_b9898_defaults.json`. The Windows x64 CPU build
matching this worktree and the Android arm64 CPU build matching the Soll app
were downloaded into the ignored repository cache `build/llama-b9898`, matched
against the release byte counts and SHA-256 digests, and passed the focused
binary smoke.

The task-referenced
`raw/monitored\llama-cpp-releases\20260707-220057-b9898-5b0a8c1c.md` file is
not vendored in this isolated worktree. The official release metadata at
`https://github.com/ggml-org/llama.cpp/releases/tag/b9898` was checked without
crossing the repository write boundary.

The reusable manifest records 22 selectable CPU/accelerator targets backed by
24 checksummed binary/framework packages. All 24 manifest entries match the
official release asset name, byte count and digest. The remaining 25th release
asset is the web UI bundle and is intentionally outside the binary target
matrix. Nine portable CPU/framework choices remain platform defaults;
accelerator builds stay explicit opt-ins.

The checked-in CI-compatible smoke runner is repeatable with:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppB9898Release.ps1
```

## Upstream regression focus

The b9898 release contains the SYCL change from upstream PR `#25081`, using the
SYCL function path to fix the AOT double-type issue. The downloaded Windows and
Android CPU archives do not exercise a SYCL device, so this slice proves the
published b9898 artifact identities and portable runtime surfaces, not the
hardware-specific SYCL regression itself. The exact Linux FP32/FP16 and Windows
SYCL assets are nevertheless pinned in the manifest for a future oneAPI runner.

## Source correction and deployment boundary

The release exposes macOS, iOS, Ubuntu, Android and Windows binary families with
CPU, Metal/XCFramework, Vulkan, CUDA, ROCm, OpenVINO, SYCL, OpenCL/Adreno and HIP
variants. The release page lists openEuler jobs as disabled, and macOS KleidiAI
also has no release asset; neither is represented as a downloadable target.

The Android runtime default remains `soll-backend-route`. The upstream Android
tarball is a standalone executable/shared-library distribution, not an AAR or
an existing Soll JNI contract. The task rules prohibit deploys and external
side effects, so the verified binaries remain only in the ignored local cache.
No production service, device, APK dependency or external environment changed.

## Download and smoke evidence

Host: Microsoft Windows `10.0.26200`, x64 OS and x64 PowerShell process.

| Target | Release bytes | Published and local SHA-256 | Test | Result |
| --- | ---: | --- | --- | --- |
| `windows-x64-cpu` | 17,482,595 | `36ceb9234fd2e7d8589e6152760125ff03bd7599a1239360a46aeefe97aa20fe` | ZIP integrity; execute `llama-cli.exe --version` and `llama-server.exe --version` | PASS |
| `android-arm64-cpu` | 78,731,027 | `dbe4e5680eda982c88087bda1d286d75833c1248aceba818eee404fbefb29cf9` | gzip/tar integrity; inspect every non-license file as ELF64 little-endian AArch64; require CLI, server and `libllama.so` | PASS |

Both Windows commands exited `0` and reported:

```text
version: 9898 (3d4cbdf18)
built with Clang 20.1.8 for Windows x86_64
```

The Windows archive contains 51 entries. The Android archive contains 46
entries, including the expected CLI, server and shared libraries; 44/44 Android
binary files passed the ELF64 little-endian AArch64 header check. The host
Android SDK provides `adb`, but `adb devices -l` reported zero connected
devices. The Android evidence is therefore a package/ABI smoke, not a device
inference claim.

## Focused repository verification

`LlamaCppB9898ReleaseAuditTest` validates the pinned release/commit, all 22
target IDs, 24 unique checksummed packages, nine platform defaults, unavailable
release signals, safe cache boundary, executable/ABI smoke controls, Android
non-packaging policy and this artifact's value fields.

Checks completed before coordinator handoff:

- `pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppB9898Release.ps1`: PASS,
  2/2 default targets;
- official GitHub release metadata audit: PASS, 24/24 manifest packages matched
  release asset names, byte counts and SHA-256 digests;
- `./gradlew.bat :app:testDebugUnitTest --tests com.soll.project.LlamaCppB9898ReleaseAuditTest`:
  PASS;
- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace`: PASS,
  383/383 JVM tests with 0 failures, 0 errors and 0 skipped; debug APK built;
- debug APK content audit: PASS, 869 entries with 0 names matching `b9898`,
  `llama-cli`, `llama-server` or `libllama`;
- `git diff --check` plus trailing-whitespace checks for new files: PASS.

## Value metric update

- `source_processing_result`: `b9898_binaries_verified_ci_updated`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-e06555e37f16c034-verification.md`
- `source_value`: 2 release archives downloaded and checksummed; 2 Windows
  executables launched successfully; 44/44 Android binary files passed static
  ABI validation; 24/24 binary packages were pinned across 22 selectable
  targets. 0 binaries were added to the APK and 0 model inference runs were
  claimed.
