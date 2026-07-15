---
task_id: 8b69a9886d6644b3a3acb2a5b9bc02d1
source_ref: source-item/0d75242b770a/e916238e51d2cfd0
source_item: gpu-render-ytsaurus-9d291617
source_processing_result: audited_and_deferred_no_test_cluster
verification_artifact: Soll/outputs/source-processing/source-item-0d75242b770a-e916238e51d2cfd0-verification.md
source_value: "trial contract captured; 0 YTsaurus operations, 0 rendered frames, and 0 measured Soll_app runtime value because no approved GPU test cluster or render workload is available"
verified_at: 2026-07-15 Europe/Chisinau
---

# YTsaurus GPU-render trial audit for `soll_app`

## Outcome

The source was reviewed and converted into a bounded, reproducible test-cluster
trial contract. The requested GPU render was **not** run and the acceptance
criterion is not met: this isolated Android worktree has no YTsaurus endpoint,
approved GPU pool, render workload or cluster client. No credential or
environment file was inspected, and no external operation was submitted.

This is a deliberate defer result rather than a synthetic success. A local
configuration simulation, a CPU-generated image or a host `nvidia-smi` result
would not prove that Vulkan, X11 and the NVIDIA graphics devices work together
inside an isolated YTsaurus job.

## Evidence reviewed

- Source article: `https://habr.com/ru/companies/yandex/articles/1049126/`,
  "GPU-render in the cloud: passing the graphics stack into isolated
  containers" (Habr, Yandex, 2026-06-26).
- Official YTsaurus operation options:
  `https://ytsaurus.tech/docs/en/user-guide/data-processing/operations/operations-options`.
- Official YTsaurus NVIDIA GPU setup:
  `https://ytsaurus.tech/docs/en/admin-guide/gpu.html`.
- Official YTsaurus job statistics:
  `https://ytsaurus.tech/docs/en/user-guide/problems/jobstatistics`.

The task-referenced raw file
`raw/monitored\habr-yandex-company\20260702-194200-gpu-9d291617.md` is not
present in this isolated worktree. The public article and official platform
documentation were therefore used for the audit; no article-specific code or
container layer was available to reproduce.

## What the source implementation actually requires

The article's useful result is not simply "request one GPU". A desktop graphics
engine needs the following chain inside the same isolated job:

```text
YTsaurus GPU allocation and platform driver overlay
  -> NVIDIA compute, modeset and DRM devices
  -> NVIDIA Vulkan ICD and Xorg DDX driver
  -> Xorg on a virtual display
  -> Vulkan WSI surface and swapchain
  -> bounded vkcube smoke
  -> real engine render
  -> software ffmpeg encoding when the selected GPU has no NVENC
```

For the article's A100/Xorg path, the container/device boundary includes all of
these nodes, not only the standard compute devices:

- `/dev/nvidiactl`
- `/dev/nvidia0` (or the GPU assigned to the job)
- `/dev/nvidia-uvm`
- `/dev/nvidia-modeset`
- `/dev/dri/card0` (or the assigned DRM card)
- `/dev/dri/renderD128` (or the assigned render node)

`/dev/dri/renderD*` is used by the Vulkan ICD for buffer allocation, while Xorg
must become DRM master on the corresponding `/dev/dri/card*`. Both processes
must use the same physical GPU so the Xorg NVIDIA driver can import the rendered
dma-buf objects. The application layer must not pin conflicting NVIDIA driver
libraries when the cluster supplies its own compatible driver overlay.

The article reduced risk in five stages: privileged Docker `vkcube`, an
unprivileged container with the exact libraries/devices, the same rootfs as a
Porto layer, a normal YTsaurus operation with the platform driver overlay, and
only then Unity/3DGS plus `ffmpeg`. Platform support for Xorg on GPU hosts was a
prerequisite; it was not solved by application code alone.

Public YTsaurus documentation provides a smaller compute-only admission smoke:
one task with `gpu_limit=1`, scheduled through `pool_trees=[gpu]`, running
`nvidia-smi`. That check is necessary but is not sufficient for graphics.

## Repository and local capability audit

| Check | Result | Consequence |
| --- | --- | --- |
| Referenced raw source | absent | article details cannot be reproduced from a vendored record |
| YTsaurus client (`yt`) | absent | no operation can be submitted or inspected |
| Container runtime (`docker`) | absent | the article's first isolation stage cannot run locally |
| Local NVIDIA probe | `Failed to initialize NVML: Unknown Error` | host GPU is not usable as substitute evidence |
| Approved cluster endpoint/pool/layer | not present in repository scope | no authorized trial target exists |
| Soll-owned 3D/render workload | none found | there is no product latency/throughput baseline to improve |
| Android render integration | not applicable | cluster driver, Xorg and engine code must not be added to the app |

Actual YTsaurus operations completed: `0`. Rendered frames: `0`. Successful
test-cluster trials: `0`. No production or test-cluster value is claimed.

## Approved test-cluster trial contract

When a cluster owner supplies a non-production target and explicitly approves a
run, execute the following progression rather than jumping directly to Unity:

1. Confirm a dedicated homogeneous GPU pool tree, compatible node/driver
   versions, one disposable output location, quota and a named cleanup owner.
2. Submit one vanilla admission job with `job_count=1`, `gpu_limit=1`, bounded
   CPU/memory/time limits and the GPU pool tree. Require successful
   `nvidia-smi`, record the allocated GPU identity and stop on mismatch.
3. Use a pinned application/Porto layer without bundled NVIDIA driver
   libraries. Verify the cluster overlay, Vulkan ICD, Xorg DDX and the six
   required device nodes from inside the job. Do not use `--privileged`.
4. Start a private Xorg virtual display on the allocated GPU. Require
   `vulkaninfo` to report that same hardware device and reject a software
   renderer or a cross-GPU DRM mapping.
5. Run a bounded `vkcube` smoke, store its exit status, Xorg/Vulkan logs and
   frame evidence, then repeat it three times to expose startup/cleanup races.
6. Only after three graphics smokes pass, run one tiny non-sensitive scene with
   fixed inputs and a fixed frame count. Encode with a declared software codec
   when the test GPU has no NVENC; record output duration, frame count, bytes and
   SHA-256.
7. Capture operation/job status plus YTsaurus GPU cumulative load, maximum GPU
   memory, wall time, CPU encoding time, failures and retries. Delete disposable
   cluster output after the evidence is attached according to the cluster's
   retention policy.

### Required evidence for a successful result

A future task may mark the acceptance criterion complete only when one artifact
contains all of the following from an actual YTsaurus test operation:

- immutable cluster/operation/job identifiers with secrets removed;
- pinned application layer and renderer revisions;
- allocated GPU, host driver, Vulkan device and Xorg display identity;
- exact non-privileged device allowlist and proof no software renderer was used;
- three successful bounded `vkcube` runs;
- one verified scene output with duration, frame count, size and SHA-256;
- YTsaurus GPU utilization/memory and end-to-end wall-time statistics;
- cleanup status and a comparison with a named current Soll rendering baseline.

## Product decision and promotion gates

Keep this source **deferred**. `soll_app` is the Android review/approval client,
not a GPU render worker or cluster administrator. Do not add YTsaurus clients,
Porto/Docker layers, NVIDIA/Vulkan/X11/Unity dependencies, cluster endpoints or
credentials to Android.

Reopen a desktop/server spike only after all of these gates hold:

1. Soll has a concrete non-sensitive 3D, synthetic-data or video workload with
   a measured current render time, queue delay, throughput and cost.
2. A cluster owner provides an approved non-production YTsaurus GPU pool and
   confirms platform-level Xorg/modeset/DRM support.
3. The layer, renderer and input assets have verified licenses and revisions.
4. The non-privileged `vkcube` gate passes three times and the real scene output
   is deterministic and independently inspected.
5. The trial shows a named throughput/latency/cost gain over the current Soll
   baseline and records cleanup, failure isolation and rollback behavior.

Android may later display server-produced operation status, metrics, artifacts
and explicit approve/reject actions through existing Tasks, Chat, `Источники`,
`Инсайты`, Roadmap and `Ask Soll` surfaces. It must not submit render operations
or hold cluster credentials.

## Value metric update

- `source_processing_result`: `audited_and_deferred_no_test_cluster`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-0d75242b770a-e916238e51d2cfd0-verification.md`
- `source_value`: the source produced a reproducible graphics-stack audit and
  an evidence-gated trial contract; measured runtime value remains `0` because
  no YTsaurus operation or Soll render workload was available and no rendered
  frame was produced.
