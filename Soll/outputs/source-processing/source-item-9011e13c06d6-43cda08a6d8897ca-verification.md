---
task_id: 85529c3c86464d0c9be2d6bb774de4b1
project: soll_app
source_ref: source-item/9011e13c06d6/43cda08a6d8897ca
source_processing_result: downloaded_and_audited_adoption_deferred_no_current_soll_workload
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-43cda08a6d8897ca-verification.md
source_value: full_pinned_dataset_receipt_plus_quality_baseline_and_soll_promotion_gate
verified_at: 2026-07-22
---

# SciIR source processing verification

## Outcome

The acceptance criterion **Dataset downloaded and preliminary quality analysis
completed** is met. The complete `MAIR-Lab-HUST/SciIR-82k` repository snapshot
at revision `51f7e778c690c9f738051bb9141cb80da488fffc` was downloaded to an ignored
repository-local cache and audited offline. Adoption remains deferred because
the released annotations need repair and `soll_app` has no current
scientific-image generation or evaluation workload.

The monitored task path
`raw/monitored\hugging-face-daily-papers\20260702-190417-sciir-a-large-scale-training-dataset-and-benchma-00b72caa.md`
is absent from this isolated worktree. The task title and paper ID were instead
matched independently to the official paper, Hugging Face dataset, and code
repository. The external source was treated as untrusted data; it did not
authorize a runtime, dependency, integration, credential, or deployment change.

## Full download receipt

| Metric | Result |
|---|---:|
| Pinned repository files | `89/89` |
| Pinned repository bytes | `24,961,674,409 / 24,961,674,409` |
| Upstream size/hash mismatches | `0` |
| Image shards | `83/83` |
| Caption file | `caption.jsonl`, `321,286,682` bytes |
| Metadata file | `metadata.json`, `2,760,286,452` bytes |
| Snapshot cache | `build/source-processing/sciir-82k-51f7e778c690` (ignored) |
| Machine audit | `docs/knowledge/sciir-82k-quality-audit-v1.json` |

The machine audit carries one receipt per repository file and validates Git
blob SHA-1 for regular files or SHA-256 for Hugging Face LFS objects against the
pinned repository API response.

## Preliminary quality evidence

| Check | Result |
|---|---:|
| Caption rows / unique filenames | `82,189 / 82,189` |
| Blank prompts / blank Sci-RCoT | `0 / 0` |
| Exact duplicate prompt / Sci-RCoT rows | `0 / 0` |
| Rows without a non-empty reasoning dimension | `454` |
| Metadata rows / unique IDs | `47,709 / 47,709` |
| Metadata segment links | `82,189` |
| Caption/metadata/segment relationship gaps | `0` |
| Metadata rows labelled `CC BY 4.0` | `47,709` |
| Missing `figure_caption` fields | `2,967` |
| Noncanonical subject rows | `1,175` |
| Law/entity/process pair-length mismatch rows | `3,637 / 4,934 / 1,633` |
| Tar image members / valid PNG structures | `82,189 / 82,189` |
| Exact duplicate image rows / unique image hashes | `3,412 / 78,777` |
| Image dimensions | all `1024 × 1024` |
| Image link gaps | `0` |

The pair-length mismatch is a release-contract defect: the README says
`terms[i]` corresponds to `visualization[i]`, yet thousands of rows have unequal
list lengths. Subject anomalies include empty values, literal `None`, and five
verbose model-answer leaks. There are also `454` rows without any non-empty
reasoning dimension and `3,412` excess exact-copy images. Six deterministic images were manually viewed;
several had cropped edge labels, large whitespace, or reduced text readability.
The small visual sample is qualitative and is not presented as a corpus rate.

## Soll_app comparison

- `Источники` and Chat can display a bounded server-produced research summary;
  they are not dataset distribution or image-generation surfaces.
- Scanner Tool is CameraX + ML Kit barcode/QR recognition, not scientific-image
  OCR, reasoning, or generation.
- Daily/task attachments may later consume a validated server analysis result,
  but SciIR does not itself define an attachment-analysis product contract.
- The PACE proxy suite is a safe synthetic agent regression. Its upstream
  multimodal cases do not make SciIR labels interchangeable with agent metrics.
- Deferred 3D VLM and Bonsai work remains deferred. No Android model path is
  reopened by this source.

No file under `app/src/main`, Gradle configuration, model weight, runtime, API
contract, external integration, or deployment was changed. A future use must be
a separately approved desktop/server evaluation for one named scientific-figure
workflow, with article-grouped leakage-safe splits, repaired annotations,
expert-reviewed gold data, a deterministic baseline, licensing review, and
measured factual-quality/cost/latency gates.

## Value metric

- `source_processing_result`:
  `downloaded_and_audited_adoption_deferred_no_current_soll_workload`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-43cda08a6d8897ca-verification.md`
- `source_value`: `1` complete pinned repository receipt, `1` machine-readable
  whole-corpus structural audit, `1` manual visual warning sample, `1` current
  Soll contour comparison, `8` promotion-gate groups, and `0` Android
  production/dependency changes.

## Focused verification

```powershell
python -m py_compile tools\source_processing\audit_sciir_dataset.py
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.SciIrDatasetQualityAuditTest" --console=plain
git diff --check
```
