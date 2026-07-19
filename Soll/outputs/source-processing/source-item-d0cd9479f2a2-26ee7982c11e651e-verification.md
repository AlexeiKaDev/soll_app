---
task_id: c923fe6aa418475dab3f7f5b8e45b702
source_ref: source-item/d0cd9479f2a2/26ee7982c11e651e
source_item: llama-cpp-releases-b9895
source_processing_result: b9895_binaries_verified_defaults_updated
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-26ee7982c11e651e-verification.md
source_value: "2 release archives downloaded and checksummed; 2 Windows executables launched; 44/44 Android binary files passed ELF64 AArch64 validation; 22 selectable targets and 9 platform defaults configured; 0 binaries packaged into the APK and 0 model inference runs"
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b9895 binary release smoke

## Outcome

The official `ggml-org/llama.cpp` release `b9895` at commit
`defa95c306598bff66640c64dc8788adf90b72ea` is now pinned by
`tools/llama-cpp/llama_cpp_b9895_defaults.json`. The two builds matching this
Windows x64 worktree and the Android arm64 Soll application were downloaded
into the ignored repository cache `build/llama-b9895`, matched against the
release byte counts and SHA-256 digests, and passed the focused binary smoke.

The task-referenced
`raw/monitored\llama-cpp-releases\20260707-220057-b9895-3a6d7d41.md` file is
not vendored in this isolated worktree. Official GitHub release/tag metadata
and the `b9892...b9895` comparison were used without crossing the repository
write boundary.

The reusable manifest records 22 selectable CPU/accelerator targets backed by
24 checksummed packages, including separate CUDA runtime archives. Nine
portable CPU/framework choices remain platform defaults; accelerator builds
stay explicit opt-ins. The release smoke is repeatable with:

```powershell
pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppB9895Release.ps1
```

## Upstream regression focus

The `b9892...b9895` upstream range contains three commits:

- `6f8895feec96773574c7e10fcf7b56965d23550a` — OpenCL flash-attention decode
  optimizations;
- `a8cfdbb9e4c42e4cc6c1578c71f0202fd4a42b06` — Vulkan `GGML_OP_SET_ROWS`
  source-type validation;
- `defa95c306598bff66640c64dc8788adf90b72ea` — prompt-shrink cleanup for
  ngram-map, preventing the reported out-of-bounds read.

The executed Windows binaries identify themselves as the exact final commit,
`9895 (defa95c30)`. This proves the staged binaries contain the upstream fix;
it is not a dynamic ngram prompt-shrink reproduction. No GGUF model or
upstream source-test harness is part of the release assets, so model inference
and a direct ngram-map reproduction remain `0`.

## Source correction and deployment boundary

The b9895 downloadable target matrix exposes Apple/macOS, Android, iOS,
Ubuntu and Windows builds with CPU, Metal/XCFramework, Vulkan, CUDA, ROCm,
OpenVINO, SYCL, OpenCL/Adreno and HIP variants. These platform families were
already present in the immediately preceding b9892 release; the measurable
upgrade value here is the three-commit runtime delta, especially the ngram-map
bounds fix, rather than a new Soll platform family.

The Android runtime default remains `soll-backend-route`. The upstream Android
tarball is a standalone executable/shared-library distribution, not an AAR or
an existing Soll JNI contract. The task rules prohibit deploys and external
side effects, so the verified b9895 binaries were staged only in the ignored
repository cache. No production service, device, APK dependency or external
environment was changed.

## Download and smoke evidence

Host: Microsoft Windows `10.0.26200`, x64 OS and x64 PowerShell process.

| Target | Release bytes | Published and local SHA-256 | Test | Result |
| --- | ---: | --- | --- | --- |
| `windows-x64-cpu` | 17,482,688 | `a69d92ae6a3e352c5c389f3798b6c287d73100d84612753506dc55b10f517c05` | ZIP integrity; execute `llama-cli.exe --version` and `llama-server.exe --version` | PASS |
| `android-arm64-cpu` | 78,735,572 | `362f72212ea6bcc779f977ced45e172bc59a5d9c084939e6ebbec1bf24035963` | gzip/tar integrity; inspect every non-license file as ELF64 little-endian AArch64; require CLI, server and `libllama.so` | PASS |

Both Windows commands exited `0` and reported:

```text
version: 9895 (defa95c30)
built with Clang 20.1.8 for Windows x86_64
```

The Windows archive contained 51 entries. The Android archive contained 46
entries, including the expected CLI, server and shared libraries; 44/44
Android binary files passed the ELF64 little-endian AArch64 header check.
The host Android SDK provides `adb`, but `adb devices -l` reported zero
connected devices. The Android evidence is therefore a package/ABI smoke,
not a device inference claim.

## Focused repository verification

`LlamaCppB9895ReleaseAuditTest` validates the pinned release/commit, all 22
target IDs, 24 unique checksummed packages, nine platform defaults, unavailable
release signals, safe cache boundary, executable/ABI smoke controls, Android
non-packaging policy and this artifact's value fields.

Checks completed before coordinator handoff:

- `pwsh -NoProfile -File tools/llama-cpp/Test-LlamaCppB9895Release.ps1`: PASS,
  2/2 default targets;
- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace`: PASS,
  373/373 JVM tests with 0 failures, 0 errors and 0 skipped; debug APK built;
- `./gradlew.bat :app:testDebugUnitTest --tests com.soll.project.LlamaCppB9895ReleaseAuditTest`:
  PASS;
- debug APK content audit: PASS, 869 entries with 0 names matching `b9895`,
  `llama-cli`, `llama-server` or `libllama`;
- `git diff --check` plus trailing-whitespace checks for new files: PASS.

## Value metric update

- `source_processing_result`: `b9895_binaries_verified_defaults_updated`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-26ee7982c11e651e-verification.md`
- `source_value`: 2 release archives downloaded and checksummed; 2 Windows
  executables launched successfully; 44/44 Android binary files passed static
  ABI validation; 22 selectable targets, 24 packages and 9 safe platform
  defaults configured. 0 binaries were added to the APK and 0 model inference
  runs were claimed.
