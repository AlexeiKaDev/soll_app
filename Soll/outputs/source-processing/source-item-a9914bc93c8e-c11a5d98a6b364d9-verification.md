---
task_id: d9e6c8fcd2cd469e8ece838c973f508c
project: soll_app
source_ref: source-item/a9914bc93c8e/c11a5d98a6b364d9
source_trust: untrusted_external_content
raw_ref: raw/monitored\deeppavlov-releases\20260709-234334-release-1-7-0-be517aab.md
raw_status: absent_in_isolated_worktree
source_processing_result: deeppavlov_deberta_ner_audited_production_adoption_deferred
verification_artifact: Soll/outputs/source-processing/source-item-a9914bc93c8e-c11a5d98a6b364d9-verification.md
source_value: "2 NER configs and 6 adoption dimensions audited; 2 archive transfer sizes measured; 3 license layers separated; 3 inference surfaces documented; 7 safe-pilot gates defined; 1/1 focused contract test passed; 0 model downloads, inference calls, dependencies, Android contracts or runtime files changed"
verified_at: 2026-07-23 Europe/Chisinau
---

# DeepPavlov 1.7.0 DeBERTa NER audit

## Decision

The release signal is useful as a bounded server-side evaluation candidate,
but it does not justify production integration in Soll.

Both added checkpoints are English-only DeBERTa-v3-base + CRF models. The
OntoNotes config has the more useful entity vocabulary for monitored articles;
the CoNLL-2003 config only covers person, organization, location and
miscellaneous entities. Neither checkpoint has a dedicated model card or an
explicit checkpoint license, both use an old and tightly pinned Python stack,
and the supplied archives have no published cryptographic digest. Production
adoption is therefore deferred. No model, archive or dependency was downloaded
or executed by this audit.

If the licensing and artifact-integrity gaps are resolved, the only suitable
next step is an isolated server-side, read-only shadow evaluation on labeled
English monitored-source text. Android remains an approval and observability
client; NER output must not create tasks, call tools or trigger actions.

## Source and trust boundary

The task-supplied raw path
`raw/monitored\deeppavlov-releases\20260709-234334-release-1-7-0-be517aab.md`
is absent from this isolated worktree. Its untrusted description was used only
as a discovery pointer. Findings below were checked read-only against public
upstream surfaces on 2026-07-23:

- [Release 1.7.0](https://github.com/deeppavlov/DeepPavlov/releases/tag/1.7.0),
  published 2024-08-12 at commit
  `aff27489a3c87644eeb8f6009e4a824e83c66c05`;
- [PR #1691](https://github.com/deeppavlov/DeepPavlov/pull/1691),
  merged as `ab737eecb9ebbdf6ccc1a616560f17d1982460f6`;
- the two immutable release configs:
  [CoNLL-2003](https://github.com/deeppavlov/DeepPavlov/blob/aff27489a3c87644eeb8f6009e4a824e83c66c05/deeppavlov/configs/ner/ner_conll2003_deberta_crf.json)
  and
  [OntoNotes](https://github.com/deeppavlov/DeepPavlov/blob/aff27489a3c87644eeb8f6009e4a824e83c66c05/deeppavlov/configs/ner/ner_ontonotes_deberta_crf.json);
- the [DeBERTa-v3-base card](https://huggingface.co/microsoft/deberta-v3-base),
  [DeepPavlov NER guide](http://docs.deeppavlov.ai/en/master/features/models/NER.html)
  and [REST API guide](http://docs.deeppavlov.ai/en/master/integrations/rest_api.html);
- the [CoNLL-2003 dataset card](https://huggingface.co/datasets/eriktks/conll2003)
  and [OntoNotes 5.0 catalog record](https://catalog.ldc.upenn.edu/LDC2013T19).

The release has no attached binary assets: the model configs download separate
archives from `files.deeppavlov.ai`.

## PR and model-card audit

PR #1691 is titled `Update of conll2003 and ontonotes ner configs`. Its final
diff has 8 files, `+260/-63`, and adds exactly these two NER configs:

- `ner_conll2003_deberta_crf`;
- `ner_ontonotes_deberta_crf`.

Both configs set `TRANSFORMER` to `microsoft/deberta-v3-base`, use
`torch_transformers_ner_preprocessor`, use a
`torch_transformers_sequence_tagger` with `use_crf: true`, cap
`max_seq_length` at `512`, and expose `x_tokens` plus `y_pred`. The internal
tagger also produces `probas`, but the public chainer output does not expose
those probabilities.

A recursive inventory of the 1.7.0 source tree found only the two config files
whose path contains `deberta`; it found no release-specific model-card file.
The current official DeepPavlov NER model table also does not list either new
config, and an exact public Hugging Face search found no DeepPavlov-hosted
checkpoint repository for them. The only complete model card available is for
the unfine-tuned `microsoft/deberta-v3-base` base model. Its hosted inference
provider performs fill-mask inference, not the added DeepPavlov NER task.

This is a documentation and provenance gap, not evidence that the checkpoints
inherit the base model's license or hosted API.

## Six-dimension verification

### 1. Language and entity coverage

| Config | Verified language | Training/evaluation schema | Soll relevance |
| --- | --- | --- | --- |
| `ner_conll2003_deberta_crf` | English | CoNLL-2003: `PER`, `ORG`, `LOC`, `MISC` | Narrow candidate for high-level people/organization/location suggestions |
| `ner_ontonotes_deberta_crf` | English | OntoNotes English NER: 18 documented types including person, organization, GPE, date, money, product, event, law and language | Better candidate for diverse English monitored articles |

The OntoNotes corpus as a whole contains English, Chinese and Arabic, but this
config is not the existing explicitly multilingual
`ner_ontonotes_bert_mult`; it uses an English-tagged DeBERTa base model and is
treated as English-only. Neither checkpoint is suitable for Russian, German,
Romanian or mixed-language source text without a language gate and separate
evaluation.

### 2. License and provenance

Three different license layers must not be collapsed:

1. DeepPavlov 1.7.0 source code and configs declare Apache-2.0.
2. `microsoft/deberta-v3-base` declares MIT on its model card.
3. The two fine-tuned archive URLs have no dedicated model card or explicit
   checkpoint license. CoNLL-2003 is marked `other`; its English Reuters text
   requires the applicable Reuters/NIST agreements. OntoNotes 5.0 is
   distributed under an LDC User Agreement.

Apache-2.0 for framework code and MIT for the base model do not prove that the
fine-tuned checkpoint weights or their commercial redistribution are cleared.
Production use remains blocked until the checkpoint publisher identifies the
weight license, training-data provenance and allowed use/redistribution terms.

### 3. Size and artifact integrity

Read-only HTTP `HEAD` requests followed each config's `http://` URL to HTTPS:

| Config | Transfer archive | `Content-Length` | Binary size | Server metadata |
| --- | --- | ---: | ---: | --- |
| CoNLL-2003 | `ner_conll2003_deberta_crf.tar.gz` | `1,326,708,465` bytes | about `1.327 GB` / `1.236 GiB` | `Last-Modified: 2024-07-28`; ETag `"66a69c1c-4f13f6f1"` |
| OntoNotes | `ner_ontonotes_deberta_crf.tar.gz` | `1,421,717,858` bytes | about `1.422 GB` / `1.324 GiB` | `Last-Modified: 2024-07-28`; ETag `"66a6a2b5-54bdb162"` |

These are compressed transfer sizes, not extracted disk, RAM, VRAM or latency
measurements. ETags are not accepted as cryptographic integrity proofs. A
future pilot must obtain or create reviewed SHA-256 pins before extraction and
must reject path traversal, symlinks and unexpected archive entries.

### 4. Dependencies

The model configs require the DeepPavlov 1.7.0 runtime and the following
component pins:

- `torch>=1.6.0,<1.14.0`;
- `transformers==4.30.0` on Python 3.8+ (or `>=4.13.0,<4.25.0` below 3.8);
- `sentencepiece==0.2.0`;
- `protobuf<=3.20`;
- `pytorch-crf==0.7.*`;
- `microsoft/deberta-v3-base`.

DeepPavlov itself also pins an older FastAPI/Pydantic/Numpy/Pandas/Uvicorn
stack, including `fastapi<=0.89.1`, `pydantic<2` and `numpy<1.24`. The
combination belongs in a separately locked server container, not the Android
Gradle graph and not an existing modern Python service environment.

### 5. Inference API

The fine-tuned checkpoints have three documented local inference surfaces:

1. Python: `build_model("ner_conll2003_deberta_crf", ...)` or
   `build_model("ner_ontonotes_deberta_crf", ...)`, then call the model with a
   list of sentences; output is token lists plus aligned BIO tags.
2. CLI: `python -m deeppavlov interact <config> -d` or
   `python -m deeppavlov predict <config> -f <file>`.
3. Self-hosted REST: `python -m deeppavlov riseapi <config> -d`; send
   `POST /model` with `{"x":["bounded text"]}`, inspect argument/output names
   through `GET /api`, and use `POST /probe` only for service health.

There is no dedicated hosted inference endpoint or SLA for the fine-tuned
archives. The Hugging Face Inference API shown on the base card is fill-mask
inference for the base model and cannot substitute for these CRF checkpoints.

### 6. Applicability to safe Soll extraction

The models may generate non-authoritative entity suggestions for English
source indexing, deduplication and human review. They must not be trusted as a
security boundary, factual verifier, entity linker, PII classifier or action
policy. Their predictions are derived from untrusted monitored-source text and
remain untrusted data.

The current Android contract receives server-created `SollSourceItem` records
with title, URL, preview, summary, evidence and safe-next-step metadata. It has
no NER model runtime or entity field. That boundary remains unchanged. A
future server pilot must:

1. accept only stored text plus stable `source_item_id` and canonical source
   provenance; never let model text select files, URLs, tools or commands;
2. run only when a reviewed language detector returns English; preserve a
   no-model fallback for all other or uncertain languages;
3. normalize and allowlist labels, verify every returned span against the
   original text, cap input at 512 subwords with deterministic overlap, and
   bound batch, CPU, RAM, time and output counts;
4. retain model/config/archive digests with every result and keep extracted
   persons under the existing personal-data retention and visibility policy;
5. expose suggestions in shadow/review mode only; `unsafe_side_effect_count`
   must remain `0`, including task creation, notifications and external calls;
6. compare both candidates with the named current extraction baseline on at
   least 200 representative English articles and 500 manually labeled
   entities, reporting per-label precision/recall/F1, exact-span errors,
   false positives per article, p50/p95 latency and peak RSS;
7. promote only after checkpoint licensing is cleared, SHA-256 and safe
   extraction pass, overall exact-span F1 is at least `0.85`, precision for
   every enabled action-visible label is at least `0.90`, and a human reviews
   all error clusters. Rollback removes the shadow enricher without changing
   the source-item contract.

The OntoNotes model is the first comparison candidate because its label set is
more useful for general articles. CoNLL-2003 remains a narrower control. No
quality, latency, memory or monitored-source benefit is claimed until that
separately approved pilot is run.

## Focused smoke/audit checks

| Check | Result |
| --- | --- |
| Task base SHA `989fdddcbfa5710880834e17c38b31bb02f3cc23` checked out before changes | PASS |
| Release, PR, two immutable configs and base model card inspected read-only | PASS |
| Language, license, size, dependencies, inference API and safe applicability recorded | PASS |
| Missing raw snapshot and missing checkpoint-card/license boundaries recorded | PASS |
| No model archive, weight, dataset or dependency downloaded | PASS |
| No Android contract, application runtime or Gradle dependency changed | PASS |
| `DeepPavlov170NerApplicabilityAuditTest` | PASS |

## Value metric update

- `source_processing_result`:
  `deeppavlov_deberta_ner_audited_production_adoption_deferred`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-a9914bc93c8e-c11a5d98a6b364d9-verification.md`;
- `source_value`: `2` NER configs and `6` adoption dimensions audited; `2`
  archive transfer sizes measured; `3` license layers separated; `3`
  inference surfaces documented; `7` safe-pilot gates defined; `1/1` focused
  contract test passed; `0` model downloads, inference calls, dependencies,
  Android contracts or runtime files changed.

## Test evidence

- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.soll.project.DeepPavlov170NerApplicabilityAuditTest" --console=plain
  --rerun-tasks`
- Observed result: `BUILD SUCCESSFUL`; the final fresh run completed in `1m 48s`,
  all `33/33` Gradle tasks executed, and the `1/1` focused contract test passed
  with `0` failures, `0` errors and `0` skipped tests.
