---
task_id: 16a254d3e2ef4721bc9ca139ca2fa520
source_ref: source-item/d0cd9479f2a2/fd72a4b7d45cc93b
source_item: llama-cpp-releases-b9892
source_processing_result: b9892_binaries_verified_defaults_updated
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-fd72a4b7d45cc93b-verification.md
source_value: "2 release archives downloaded and checksummed; 2 Windows executables launched; 44/44 Android binary files passed ELF64 AArch64 validation; 22 selectable targets and 9 platform defaults configured; 0 binaries packaged into the APK and 0 model inference runs"
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b9892 binary release smoke

## Outcome

The official `ggml-org/llama.cpp` release `b9892` at commit
`ee445f93d8a0a5033a46d1960e901ef5caec9a41` was checked against its GitHub
release metadata. The two builds corresponding to this Windows x64 worktree and
the Android arm64 Soll application were downloaded into the ignored repository
cache `build/llama-b9892`, verified against the release SHA-256 digests, and
tested.

The task-referenced
`raw/monitored\llama-cpp-releases\20260707-220057-b9892-8441b45b.md` file is
not vendored in this isolated worktree. The task record and the canonical
release/tag metadata were used without crossing the repository boundary.

The reusable release configuration is
`tools/llama-cpp/llama_cpp_b9892_defaults.json`. It records every platform and
accelerator binary in the release as 22 selectable targets backed by 24
checksummed packages, including the separate CUDA runtime archives. Nine
portable CPU/framework choices are platform defaults; accelerator builds remain
explicit opt-ins. The focused smoke can be repeated with:

```powershell
pwsh -File tools/llama-cpp/Test-LlamaCppB9892Release.ps1
```

The default Android runtime remains the Soll backend route. The upstream
Android tarball is a standalone executable/shared-library distribution, not an
AAR or a Soll JNI contract, so 0 binaries were packaged into the APK and no
production Android dependency or behavior changed.

## Source corrections

The release contains CPU, CUDA, Vulkan, ROCm, OpenVINO, SYCL, OpenCL/Adreno and
HIP packages across Android, iOS, macOS, Ubuntu and Windows. The source signal
needs two corrections:

- PowerPC received an optimal default thread-count source change, but b9892 has
  no PowerPC release asset.
- The openEuler 310p/910b jobs and the macOS arm64 KleidiAI job are displayed as
  disabled and have no downloadable b9892 assets. They must not be configured
  as supported binaries.

The JSON therefore records these as unavailable signals rather than inventing
download URLs.

## Download and smoke evidence

Host: Microsoft Windows `10.0.26200`, x64 OS and x64 PowerShell process.

| Target | Release bytes | Published and local SHA-256 | Test | Result |
| --- | ---: | --- | --- | --- |
| `windows-x64-cpu` | 17,482,680 | `882307c5ace8dc17d0dade33d28efdd324caabf306e443e440df6bc735151cb2` | ZIP integrity; execute `llama-cli.exe --version` and `llama-server.exe --version` | PASS |
| `android-arm64-cpu` | 78,731,996 | `6752e8b33c3e8f9eaa991f8e8e572c2c88b0a28cbc70fced7db75c129efd5bbe` | gzip/tar integrity; inspect every non-license file as ELF64 little-endian AArch64; require CLI, server and `libllama.so` | PASS |

Both Windows commands exited `0` and reported:

```text
version: 9892 (ee445f93d)
built with Clang 20.1.8 for Windows x86_64
```

The Android archive contained the expected CLI, server and shared libraries;
44/44 Android binary files passed the ELF64 little-endian AArch64 header check.
No ADB executable or attached Android target was available, so this is a
package/ABI compatibility smoke, not a claim of device inference. A GGUF model
was not part of the release and was not downloaded; model inference runs: `0`.

The normal Soll Android build also passed `:app:assembleDebug`. Its resulting
APK had 869 archive entries and 0 entries matching b9892, `llama-cli`,
`llama-server` or `libllama`, confirming that the audit binaries did not leak
into application packaging.

## Default-selection contract

CPU/framework defaults now exist for these release platforms:

- Android arm64;
- iOS XCFramework;
- macOS arm64 and x64;
- Ubuntu arm64, s390x and x64;
- Windows arm64 and x64.

The same manifest exposes opt-in Ubuntu arm64/x64 Vulkan, Ubuntu x64 ROCm 7.2,
OpenVINO 2026.2.1 and SYCL FP16/FP32, plus Windows arm64 OpenCL/Adreno and
Windows x64 CUDA 12.4/13.3, Vulkan, OpenVINO 2026.2.1, SYCL and HIP/Radeon.
Every package has an exact byte count and SHA-256; the smoke script rejects
unknown targets, mismatches, empty archives and cache paths outside this
repository.

## Focused repository verification

`LlamaCppB9892ReleaseAuditTest` validates the pinned release/commit, all 22
target IDs, 24 unique checksummed packages, nine platform defaults, disabled
release signals, safe cache boundary, executable/ABI smoke controls, Android
non-packaging policy and this artifact's value fields.

Focused checks completed:

- `pwsh -File tools/llama-cpp/Test-LlamaCppB9892Release.ps1`: PASS, 2/2
  default targets;
- `:app:testDebugUnitTest --tests com.soll.project.LlamaCppB9892ReleaseAuditTest`:
  PASS;
- `:app:assembleDebug`: PASS;
- `git diff --check`: PASS.

## Value metric update

- `source_processing_result`: `b9892_binaries_verified_defaults_updated`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-fd72a4b7d45cc93b-verification.md`
- `source_value`: 2 release archives downloaded and checksummed; 2 Windows
  executables launched successfully; 44/44 Android binary files passed static
  ABI validation; 22 selectable targets, 24 packages and 9 safe platform
  defaults configured. 0 binaries were added to the APK and 0 model inference
  runs were claimed.
