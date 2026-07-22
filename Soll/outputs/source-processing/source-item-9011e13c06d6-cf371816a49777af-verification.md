---
task_id: 9f64e52d3f9d4380a823d56cf5aff30e
project: soll_app
source_ref: source-item/9011e13c06d6/cf371816a49777af
source_item: "NoPA: Non-Parametric Online 3D Scene Graph Generation"
source_processing_result: full_paper_downloaded_synthetic_merge_comparison_completed_integration_deferred
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-cf371816a49777af-verification.md
source_value: "1 full 28-page PDF plus TeX source downloaded and SHA-256 verified; 12-case deterministic particle comparison completed with MMD 6/6 versus moment proxy 3/6 on holdout; current Soll 3D association coverage 0/6; 1/1 focused contract test passed; 0 model/dataset imports and 0 production/runtime changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# NoPA full-paper, experiment and Soll integration verification

## Outcome

The acceptance criterion is satisfied. The complete arXiv v1 PDF and TeX source
were downloaded, hashed, safely extracted and reviewed. A focused local
experiment was executed against an explicit lightweight moment baseline, and
the current Soll camera/scanner path was audited as the actual integration
baseline.

Durable evidence:

- detailed evaluation:
  `docs/knowledge/nopa-online-3d-scene-graph-soll-evaluation.md`;
- machine-readable calibration/holdout fixture and results:
  `docs/knowledge/nopa-particle-merge-synthetic-v1.json`;
- executable audit:
  `app/src/test/java/com/soll/project/NopaOnline3dSceneGraphEvaluationTest.kt`.

The result is **defer integration**, not defer task completion. The paper and
component hypothesis produced measurable research value, but NoPA cannot
replace or augment Soll's present barcode scanner without RGB-D depth, world
poses, a 2D object/predicate graph and a named 3D workflow.

## Full-download receipt

| Check | Observed result |
| --- | --- |
| Canonical source | `arxiv:2607.00529v1`, ECCV 2026, 28 pages |
| PDF | 6,513,698 bytes; SHA-256 `79b0cc49e139a20617f6c03d81f278202bf619c0871e41ea4a56e24469244c43` |
| TeX source | 5,629,628 bytes; SHA-256 `bb9d95a54811b125894cd2c625f957d1df02e183268f0232e0cce82fd4139b7a` |
| Archive safety | PASS: 19/19 entries relative and traversal-free |
| Source extraction | PASS: `main.tex`, `supple.tex`, bibliography, styles and 10 assets |
| Paper license | CC BY-SA 4.0 from the arXiv record |
| Local cache | ignored `build/source-processing/nopa-2607.00529v1/`; not vendored into the Android app |
| Raw task path | absent from isolated worktree; canonical primary source used |

No NoPA implementation repository, pinned code revision, model weights or
dataset bundle was linked in the v1 source. No upstream code, models, data or
runtime dependencies were imported or executed.

## Focused paper audit

| Check | Observed result |
| --- | --- |
| Input contract | streaming RGB plus depth map, world pose and predicted per-frame 2D scene graph |
| Representation | fixed `n=256` world-frame particles per object with RBF KDE |
| Association | fitted-Gaussian Hellinger pre-filter plus MMD only in the ambiguity band |
| Bounded update | KDE resampling keeps a fixed particle count after merge |
| Relation recovery | MMD affinity clusters plus majority-vote propagation |
| 3DSSG result | relationship recall `25.7% -> 53.2%`; latency `22 -> 27 ms`; VRAM `1,204 -> 1,206 MB` versus reproduced FROSS |
| ReplicaSSG result | relationship recall `22.3% -> 36.9%`; latency `17 -> 23 ms` versus reproduced FROSS |
| Critical ablation | particles alone reduced relationship recall to `17.6%`; tailored merge and propagation are required |
| Limits | learned 2D SSG quality caps results; no released pinned NoPA code; paper GPU metrics are not Android metrics |

## Experimental comparison

The deterministic experiment contains 12 synthetic, non-sensitive particle-set
pairs: 6 calibration and 6 untouched holdout cases. Its MMD threshold is derived
only from calibration. The holdout deliberately includes different supports
with the same centroid and covariance so that the paper's distribution-level
motivation is falsifiable.

| Path | Runnable holdout | Correct | Accuracy | Interpretation |
| --- | ---: | ---: | ---: | --- |
| Current Soll CameraX + ML Kit barcode path | 0/6 | n/a | n/a | emits code strings, not 3D object candidates |
| Moment-only centroid/covariance proxy | 6/6 | 3/6 | 0.50 | merged all same-moment negative cases |
| NoPA-inspired RBF-MMD component | 6/6 | 6/6 | 1.00 | separated all fixture supports |

This is a component microexperiment, not a NoPA, 3DSSG, ReplicaSSG, sensor or
Android performance reproduction. It establishes one narrow algorithmic signal
and one concrete product gap. Measured end-to-end Soll 3D value remains `0`.

## Integration decision

Direct integration is rejected for the current app. The only acceptable future
path is an approved offline desktop/server pilot after a named user workflow,
calibrated RGB-D source, stable pose, pinned implementation/license receipt and
fixed real-scene baseline exist. Android may review immutable graph evidence but
must not receive autonomous coordinates or control hardware.

No production code, runtime dependency, database/API contract, source priority,
UI, notification, task mutation or actuator path changed.

## Focused smoke result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.NopaOnline3dSceneGraphEvaluationTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1 focused contract test passed` with
`0` failures, `0` errors and `0` skipped tests.

The test parses all 12 particle fixtures, recalibrates the threshold, recomputes
every holdout MMD score and both method decisions, verifies aggregate metrics,
audits the current CameraX/ML Kit/string-storage contract and pins the download,
scope, integration and value-metric receipts.

## Value metric update

- `source_processing_result`:
  `full_paper_downloaded_synthetic_merge_comparison_completed_integration_deferred`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-cf371816a49777af-verification.md`;
- `source_value`: 1 full 28-page PDF plus TeX source downloaded and SHA-256
  verified; 12-case deterministic particle comparison completed with MMD 6/6
  versus moment proxy 3/6 on holdout; current Soll 3D association coverage 0/6;
  `1/1` focused contract test passed; model/dataset imports and
  production/runtime changes: `0`.
