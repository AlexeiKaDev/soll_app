---
title: SciIR-82k quality audit and Soll applicability
task_id: 85529c3c86464d0c9be2d6bb774de4b1
source_ref: source-item/9011e13c06d6/43cda08a6d8897ca
dataset_revision: 51f7e778c690c9f738051bb9141cb80da488fffc
reviewed_at: 2026-07-22
---

# SciIR-82k: preliminary quality audit and Soll applicability

## Decision

The requested full public SciIR-82k snapshot was downloaded at the pinned
revision and audited offline. It is a useful research corpus with strong
image-to-article traceability, but it is **not ready for a Soll production or
training import**. The released labels contain measurable structural defects,
the release does not provide an explicit leakage-safe benchmark split, and
`soll_app` has no current scientific-image generation or evaluation workload.

The measurable Soll value of this source is the reproducible dataset receipt,
quality baseline, and promotion gate below. The 24.96 GB snapshot was held only
in the ignored cache of the isolated audit worktree; that worktree was removed
after integration, so the dataset is not currently retained locally. The
retained `tools/source_processing/audit_sciir_dataset.py` tool can reproduce the
download and audit from the pinned revision. Do not package the dataset in the
APK, add model weights or training dependencies, or expose generated
chain-of-thought text in Android.

## Source and download receipt

The task's monitored raw path is not present in this isolated worktree. Its
title and paper identifier were independently matched to the primary paper,
official dataset repository, and official code repository before any data was
interpreted.

| Item | Verified value |
|---|---|
| Paper | `SciIR: A Large-scale Training Dataset and Benchmark for Scientific Image Reasoning Generation`, arXiv `2606.30124` v1, accepted at ECCV 2026 |
| Dataset | `MAIR-Lab-HUST/SciIR-82k` |
| Pinned revision | `51f7e778c690c9f738051bb9141cb80da488fffc` |
| Repository receipt | `89/89` files, `24,961,674,409` bytes, every file size and upstream Git/LFS digest matched |
| Image payload | `83` uncompressed tar shards, `82,189` PNG members |
| Caption payload | `caption.jsonl`, `82,189` rows |
| Metadata payload | `metadata.json`, `47,709` source-image rows and `82,189` segment links |
| Local cache | Not currently retained; former task-worktree path `build/source-processing/sciir-82k-51f7e778c690` (ignored, not an application asset) |
| Machine-readable audit | `docs/knowledge/sciir-82k-quality-audit-v1.json` |

Primary references:

- <https://arxiv.org/abs/2606.30124>
- <https://huggingface.co/datasets/MAIR-Lab-HUST/SciIR-82k>
- <https://github.com/MAIR-Lab-HUST/SciIR>

## Preliminary quality results

The audit parses all captions and metadata, streams every tar member, validates
the complete PNG chunk structure and CRCs, and compares every repository file
with the pinned Hugging Face receipt. It does not treat a successful archive
download as evidence that the semantic labels are correct.

| Check | Result | Interpretation |
|---|---:|---|
| Caption rows / unique filenames | `82,189 / 82,189` | no missing or duplicate caption filename |
| Blank abstract prompts / Sci-RCoT | `0 / 0` | generated text fields are populated |
| Exact duplicate prompt / Sci-RCoT rows | `0 / 0` | no exact text-row duplication found |
| Rows without a non-empty reasoning dimension | `454` | populated prompt/Sci-RCoT does not guarantee usable structured reasoning |
| Metadata rows / unique IDs | `47,709 / 47,709` | source records are uniquely keyed |
| Caption-to-metadata ID gaps | `0` | every caption base ID maps to metadata and back |
| Caption-to-segment gaps | `0` | every caption filename maps to one metadata segment and back |
| `CC BY 4.0` metadata labels | `47,709` | release labels every metadata row consistently; this is not a substitute for legal review |
| Missing `figure_caption` fields | `2,967` | material source-text gap for prompt/evaluation use |
| Noncanonical subject rows | `1,175` | includes `1,164` empty, `6` literal `None`, and `5` verbose model-answer leaks |
| `terms` / `visualization` length mismatches | law `3,637`; entity `4,934`; process `1,633` | violates the README's positional-pair contract and requires repair before use |
| Images / valid PNG structures | `82,189 / 82,189` | all released image bytes were fully parsed, not sampled |
| Exact duplicate image rows | `3,412` (`78,777` unique hashes) | about 4.15% excess exact copies require group-aware deduplication before splitting |
| Image dimensions | all `1024 × 1024` | release normalization is directly verified |
| Filename/image/segment gaps | `0` | cross-file referential integrity is complete |

Six deterministic images from five shards were also viewed manually. The
sample included clear plots and diagrams, but several panels had cropped edge
labels, large whitespace, or reduced text readability. This tiny visual review
is a warning signal, not an estimate of corpus-wide semantic accuracy.

## Paper claims versus release evidence

The paper describes an automated pipeline using figure cropping/filtering,
multimodal stratification and generated annotations. The construction appendix
states that a random 10% of the filter's `KEEP` partition is manually reviewed
against a 5% false-positive threshold, but the release does not contain those
review logs. Its separate annotation-quality checks use three random samples of
150 examples, with reported pass rates of 91.3% for reasoning extraction, 86.0%
for Sci-RCoT, and 89.3% for prompt distillation. That is useful spot evidence,
but the paper does not report an overlapping multi-rater sample or inter-rater
agreement, and the 450 annotation-reviewed rows are only about 0.55% of the
82,189 released segments.

The paper also says 800 SciIR-Bench rows were removed from training data. The
dataset repository snapshot has no explicit train/test split or released list
that lets this audit independently reproduce that exclusion. A future benchmark
must split by source article, not randomly by segment, because multiple figures
and segments can share one article and near-identical visual context.

## Mapping to current `soll_app`

| Current contour | Fit of SciIR | Decision |
|---|---|---|
| `Источники` and Chat | already show digest/text plus bounded article-image cards | may display a validated server-produced source summary; no dataset delivery to Android |
| Scanner Tool | CameraX + ML Kit barcode/QR capture, not scientific figure OCR or reasoning | no scanner/model expansion from this source |
| Daily/task attachments | uploads and displays server analysis summaries | a future named scientific-document workflow could consume a server result, but the corpus is not an attachment analyzer |
| PACE proxy eval | safe synthetic agent-capability regression, despite one upstream multimodal source file | do not mix image-generation labels into the agent proxy suite |
| 3D VLM / Bonsai research | already deferred behind concrete workloads, hardware and safety gates | SciIR does not reopen either Android model path |

There is therefore no honest current product metric for training or benchmarking
on SciIR. The safe candidate, only after a concrete scientific-document or
figure-authoring workflow is approved, is a desktop/server evaluation slice:

1. Create a cleaned, article-grouped subset; repair positional pairs, canonical
   subjects and missing-caption policy, and record every exclusion.
2. Define expert-reviewed gold outputs for one named Soll workflow. Do not score
   general image aesthetics as a proxy for user value.
3. Compare a deterministic non-generative baseline and one pinned model on
   factual correctness, relation coverage, hallucinations, readability,
   latency, compute/storage cost and failure behavior.
4. Keep data, weights, inference, licenses and provenance server-owned. Android
   receives only bounded result cards, evidence and explicit approval tasks.

## Promotion gates

No dataset-derived implementation should be promoted until all gates pass:

- full receipt and integrity audit remains reproducible at a pinned revision;
- `terms[i]` and `visualization[i]` are either repaired or fail closed;
- subjects are canonicalized and missing captions have an explicit policy;
- train/validation/test data are grouped by source article and checked for
  image/text leakage and exact or perceptual duplicates;
- source-article and downstream-use licensing are reviewed beyond trusting the
  metadata string;
- domain experts label an overlapping stratified sample and agreement is
  reported separately from model-generated annotations;
- a named Soll workflow beats its baseline with predefined acceptance metrics;
- storage, compute, privacy, provenance, rollback and human-approval ownership
  are defined outside Android.

Until then the correct result is:
`downloaded_and_audited_adoption_deferred_no_current_soll_workload`.
