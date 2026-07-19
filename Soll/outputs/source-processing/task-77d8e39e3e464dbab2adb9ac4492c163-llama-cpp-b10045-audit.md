---
task_id: 77d8e39e3e464dbab2adb9ac4492c163
project: fdf52463-9152-453a-b186-68e7d76c3edb
source_ref: insight/d1315e5d1789
source_item: llama-cpp-releases-b10045
source_processing_result: implementation_location_defined_runtime_deferred
verification_artifact: Soll/outputs/source-processing/task-77d8e39e3e464dbab2adb9ac4492c163-llama-cpp-b10045-audit.md
value_metric: "1 wiki implementation-location contract added; 3 official upstream surfaces and 5 current Soll seams audited; b10068 verified 23 commits ahead of b10045; 5 smoke gates defined; 0 production/runtime changes and 0 measured b10045 Soll inference value"
verified_at: 2026-07-19 Europe/Chisinau
---

# llama.cpp b10045 implementation-location audit

## Outcome

The implementation location is defined in `wiki/b10045.md`. The b10045 change
belongs in the Soll server inference adapter behind the existing Android
`POST /api/v1/chat/turn` contract, where chat sessions can be mapped to
llama-server slots and snapshot files. That server-side component is not part
of this isolated Android repository, so no production implementation was added.

The requested wiki and monitored source were not vendored at task start. The
release classification was therefore reconstructed from the official b10045
release, exact commit and PR without claiming unavailable source details.

## Focused audit

| Check | Observed result |
| --- | --- |
| Release identity | `b10045` -> `a8dc0e3269a5378d212e6daea953fbbaa7ac8e4b`, released 2026-07-16 |
| Upstream scope | 3 server/test files, `+145/-14` |
| Functional delta | text-only slot save/restore/erase allowed with `mmproj`; media slot remains rejected with HTTP `501` |
| Android seam | `SollApiService` exposes `POST api/v1/chat/turn`, not llama-server `/slots` |
| Standalone baseline | checksummed b10068 is `23` commits ahead and contains the b10045 merge base |
| Model prerequisite | deny-by-default allowlist has `0` approved models |
| Product change | none; wiki/test/audit only |
| Runtime proof | `0` b10045 inference or multimodal slot-persistence runs |

## Implementation decision

Keep Android `ChatViewModel`, `SollRepository`, `SollApiService`, Gradle and the
`soll-backend-route` default unchanged. If a local multimodal llama-server is
approved later, implement slot persistence in the server-side chat inference
adapter. The first in-repository execution artifact should then be
`tools/llama-cpp/Test-LlamaCppMultimodalSlotPersistence.ps1`, gated by pinned
GGUF/mmproj provenance and the five functional, isolation and TTFT checks in the
wiki.

## Focused smoke/audit artifact

`LlamaCppB10045ImplementationLocationTest` guards:

- exact task, source, release, commit and PR identity;
- the missing-source boundary and the b10045 functional scope;
- the exact server-side placement and unchanged Android contract;
- b10068 ancestry, the empty model allowlist and five future smoke gates;
- the quantified value metric and `0` production/runtime changes.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.LlamaCppB10045ImplementationLocationTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused test passed with `0`
failures, `0` errors and `0` skipped tests. The Kotlin daemon was unavailable,
so Gradle used its supported non-daemon compilation fallback before completing
the test successfully.

## Value metric update

- `source_processing_result`: `implementation_location_defined_runtime_deferred`
- `verification_artifact`:
  `Soll/outputs/source-processing/task-77d8e39e3e464dbab2adb9ac4492c163-llama-cpp-b10045-audit.md`
- `value_metric`: `1` wiki implementation-location contract added; `3`
  official upstream surfaces and `5` current Soll seams audited; b10068 verified
  `23` commits ahead of b10045; `5` smoke gates defined; `0` production/runtime
  changes and `0` measured b10045 Soll inference value.
