---
task_id: 61be2475b60a4f888c1c3fdd0f409133
project: soll_app
source_ref: source-item/d0cd9479f2a2/cc8bb3edd290c856
source_item: llama-cpp-releases-b9935
source_processing_result: qualcomm_hexagon_vision_rope_local_only_boundary_documented
verification_artifact: Soll/outputs/source-processing/source-item-d0cd9479f2a2-cc8bb3edd290c856-verification.md
source_value: "1 short Soll_app KB note added; 2 official upstream surfaces, 5 commits and 2 changed Hexagon files audited; 4 privacy/safety prohibitions recorded; 1/1 focused contract test passed; 0 production/runtime changes and 0 on-device inference runs"
verified_at: 2026-07-28 Europe/Chisinau
---

# llama.cpp b9935 Qualcomm Hexagon VISION RoPE verification

## Outcome

The required note is recorded in
`docs/knowledge/llama-cpp-b9935-qualcomm-hexagon-vision-rope.md`.
PR #25216 is relevant only to local on-device inference or vision preprocessing
that actually executes llama.cpp on Qualcomm Hexagon. The note explicitly
rejects network scanning, device/source discovery, uploads, background capture
and hidden data collection.

No Soll_app runtime integration is justified. Current active targets are CPU,
llama.cpp is not packaged into the Android app, and Android chat continues to
use `soll-backend-route`.

The task-referenced raw artifact is absent from both possible repository
locations. The source claim was therefore checked against the official b9935
release, PR page and PR diff without inventing unavailable monitored content.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release / PR | b9935, PR #25216, merge commit `f2d1c2f`, 5 commits |
| Upstream scope | 2 changed files, both under `ggml/src/ggml-hexagon` |
| Functional scope | VISION RoPE plus strided src0 and non-contiguous dst DMA/SPAD handling |
| Model motivation | Qwen2-VL/Qwen3-VL vision encoder |
| Allowed use | explicit local input on Qualcomm Hexagon only |
| Prohibited use | network scanning, discovery, upload, background capture and hidden collection |
| Current Soll seam | b10068 CPU-only targets; `packageIntoAndroidApp: false`; `soll-backend-route` |
| Product change | KB, verification artifact and focused contract test only |
| Runtime proof | 0 device/model inference runs; no Qualcomm result claimed |

## Focused smoke/audit artifact

`LlamaCppB9935HexagonVisionRopeKnowledgeTest` guards:

- exact task, source reference, raw-artifact boundary and upstream identities;
- VISION RoPE, Qwen vision encoder, strided/non-contiguous and DMA/SPAD scope;
- local Qualcomm Hexagon applicability and the four privacy/safety prohibitions;
- unchanged active CPU targets, Android packaging and backend route;
- quantified source value and explicit absence of an on-device inference claim.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB9935HexagonVisionRopeKnowledgeTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `qualcomm_hexagon_vision_rope_local_only_boundary_documented`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-d0cd9479f2a2-cc8bb3edd290c856-verification.md`
- `source_value`: `1` short Soll_app KB note; `2` official upstream surfaces;
  `5` commits; `2` changed Hexagon files; `4` privacy/safety prohibitions;
  `1/1` focused contract test; `0` production/runtime changes and
  `0` on-device inference runs.
