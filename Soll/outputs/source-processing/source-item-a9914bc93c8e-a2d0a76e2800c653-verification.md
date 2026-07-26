---
task_id: 581a85c9bfdf4d04b70186440346b819
project: soll_app
source_ref: source-item/a9914bc93c8e/a2d0a76e2800c653
source_trust: untrusted_external_content
raw_ref: raw/monitored\deeppavlov-releases\20260709-234334-release-1-6-0-2a7d642f.md
raw_status: absent_in_isolated_worktree
source_processing_result: release_and_pr_verified_offline_ner_runner_added_model_execution_deferred
verification_artifact: Soll/outputs/source-processing/source-item-a9914bc93c8e-a2d0a76e2800c653-verification.md
source_value: "1 offline-only runner and 2 synthetic notes added; 2 immutable commits and 2 PR files audited; 3 conflicting taxonomy counts recorded; 2/2 focused checks passed; 0 model/dependency downloads, external inference calls, user-note transfers, Android/runtime changes"
verified_at: 2026-07-24 Europe/Chisinau
---

# DeepPavlov 1.6.0 NER release and offline Soll smoke audit

## Decision

The release and PR are real, and a minimal offline-only local runner is now
available for a separately prepared DeepPavlov 1.6.0 environment. The source
signal does not justify adding DeepPavlov to the Android application or
downloading and executing its model in this task.

The advertised `37`-entity count cannot be converted into a verified 37-name
list from the release or PR. Neither surface enumerates the labels, the
repository config loads `tag.dict` only from a large mutable model archive, and
later first-party surfaces disagree with the release count. This audit
therefore records the discrepancy instead of fabricating or relabeling a list.

Actual NER inference remains deferred until a pinned local environment and a
reviewed model archive plus label dictionary are available. The checked-in
runner's `--self-check` mode still proves the offline boundary, two controlled
fixtures, batch-shape validation and BIO span decoding without installing or
importing DeepPavlov.

## Source and trust boundary

The task-supplied raw path
`raw/monitored\deeppavlov-releases\20260709-234334-release-1-6-0-2a7d642f.md`
is absent from this isolated worktree. Its untrusted description was used only
as a discovery pointer. Public upstream reads used no credentials and caused no
repository, account or service mutation.

The following upstream surfaces were inspected read-only on 2026-07-24:

- [Release 1.6.0](https://github.com/deeppavlov/DeepPavlov/releases/tag/1.6.0),
  published 2024-03-13, and its immutable
  [tag commit](https://github.com/deeppavlov/DeepPavlov/commit/6e1036dbfcde1c293b50c742f0736a3965dd1e0d)
  `6e1036dbfcde1c293b50c742f0736a3965dd1e0d`;
- [PR #1682](https://github.com/deeppavlov/DeepPavlov/pull/1682),
  merged 2024-03-07 as
  [`2e2e994d220ff73e5f0c7aafb9aa17efc4955580`](https://github.com/deeppavlov/DeepPavlov/commit/2e2e994d220ff73e5f0c7aafb9aa17efc4955580);
- the immutable 1.6.0
  [`ner_bert_base` config](https://github.com/deeppavlov/DeepPavlov/blob/6e1036dbfcde1c293b50c742f0736a3965dd1e0d/deeppavlov/configs/ner/ner_bert_base.json)
  and
  [quick-start registry](https://github.com/deeppavlov/DeepPavlov/blob/6e1036dbfcde1c293b50c742f0736a3965dd1e0d/tests/test_quick_start.py);
- the authors' later
  [DeepPavlov 1.0 paper](https://aclanthology.org/2024.emnlp-demo.47.pdf);
- the current official
  [NER documentation](https://docs.deeppavlov.ai/en/master/features/models/NER.html)
  and [demo bundle](https://demo.deeppavlov.ai/static/js/bundle.js).

No POST request was made to an external inference endpoint. No model, weight,
dataset, Python package or dependency was downloaded or executed.

## Release notes and PR #1682 verification

The release notes contain exactly two improvements:

1. Python 3.11 support via PR #1681.
2. A NER model "with 37 entities" via PR #1682.

PR #1682 is titled `feat: ner_bert_base with 37 entities`, has the body
`New 37 entities NER`, and was merged. Its final diff contains exactly two
files with `+56/-0`:

| File | Verified change |
| --- | --- |
| `deeppavlov/configs/ner/ner_bert_base.json` | Adds a transformer NER pipeline and model download metadata. |
| `tests/test_quick_start.py` | Registers the config for the generic one-argument inference smoke. |

The config uses `bert-base-multilingual-cased`, a
`torch_transformers_ner_preprocessor`, a `simple_vocab` loaded from
`{MODEL_PATH}/tag.dict`, and a `torch_transformers_sequence_tagger`. Its public
output is aligned `x_tokens` plus `y_pred` BIO tags.

The model metadata points at
`http://files.deeppavlov.ai/v1/ner/ner_bert_base.tar.gz`, which currently
redirects to HTTPS. A read-only HEAD request reported:

- `Content-Length`: `1,393,701,190` bytes (about 1.393 GB / 1.298 GiB);
- `Last-Modified`: `Wed, 06 Mar 2024 12:28:28 GMT`;
- ETag: `"65e8616c-53123146"`.

A two-megabyte ranged read of the tar header showed that the first payload is a
`2,128,078,848`-byte `model.pth.tar`; `tag.dict` is not available before that
payload. The config publishes no SHA-256, model card, standalone label
dictionary or immutable archive URL. The ETag is not a cryptographic digest.

## The advertised 37-entity list is not verifiable

PR #1682 does not enumerate or test the 37 entity names. It only makes the same
count claim as the release. The immutable repository config likewise contains
no labels; it delegates them to the archive's `tag.dict`.

Two later first-party surfaces conflict with the release claim:

- the authors' November 2024 paper identifies `ner_bert_base` in footnote 13;
  the later official paper says `32` entity types for the primary/demo model;
- the current official demo UI enumerates `35` labels in its `newNer`
  taxonomy.
  The inspected 3,122,069-byte bundle had SHA-256
  `3d46ffe46ba8eb7cad4c8e4d8dc97ff4c4a343321b39ad603597d1ca5f559c91`
  and ETag `W/"2fa395-UygfPWVO/vVewRn9MRS1Phk5yRM"`.

For auditability, those 35 current UI labels are:

`BUSINESS_NAME`, `BUSINESS_TYPE`, `CARDINAL`, `COLOR_TYPE`, `DATETIME`,
`EMAIL_ADDRESS`, `EVENT_NAME`, `FOOD_TYPE`, `GENERAL_FREQUENCY`,
`HOUSE_PLACE`, `LANGUAGE`, `LAW`, `MEAL_TYPE`, `MEDIA_TYPE`, `MONEY`,
`MUSIC_GENRE`, `NORP`, `ORDINAL`, `PERCENT`, `PERSON`, `PLACE_NAME`,
`QUANTITY`, `RELATION`, `TIMEOFDAY`, `TRANSPORT_AGENCY`, `TRANSPORT_TYPE`,
`WEATHER_DESCRIPTOR`, `WORK_OF_ART`, `PHONE_NUMBER`, `STREET_NAME`,
`BUILDING_NUMBER`, `APARTMENT`, `CITY`, `REGION`, `STATE`.

That current 35-label list and the paper's 32-type statement must not be
substituted for a verified 1.6.0 37-type vocabulary. The release claim is
accepted only as a publisher claim; the requested names remain unverified until
the publisher supplies a versioned `tag.dict` or a corrected release/model
card. Downloading 1.393 GB merely to recover an unpinned dictionary would not
resolve artifact integrity or provenance and was not performed.

## Minimal local-only Soll NER example

`tools/source_processing/run_deeppavlov_160_ner_offline.py` is a bounded
evaluation runner, not Android or server production code. It contains exactly
two fixed synthetic Soll-shaped notes in English and Russian. No user note is
accepted from a file, argument, stdin or environment variable.

Before importing DeepPavlov it:

- sets `HF_HUB_OFFLINE=1`, `TRANSFORMERS_OFFLINE=1`,
  `HF_DATASETS_OFFLINE=1`, `PIP_NO_INDEX=1` and telemetry/offline flags;
- installs a Python audit hook that rejects `socket.connect` and
  `socket.getaddrinfo`;
- builds `ner_bert_base` with `download=False` and `install=False`;
- validates the returned batch and token/tag alignment, then emits local JSON
  containing tokens, BIO tags and decoded spans.

The dependency-free contract smoke is:

```powershell
python tools/source_processing/run_deeppavlov_160_ner_offline.py --self-check
```

Actual inference is deliberately a separate local prerequisite:

```powershell
python tools/source_processing/run_deeppavlov_160_ner_offline.py
```

That second command requires DeepPavlov 1.6.0 and the complete
`ner_bert_base` cache to have already been reviewed and installed in an
isolated environment. It performs no installation or download. This task did
not create that approximately 1.4 GB model environment, so no model accuracy,
latency or memory claim is made.

## Focused smoke/audit checks

| Check | Result |
| --- | --- |
| Task base SHA `9d752407fc7ebae7736cc8359cd5caeb6bf1c178` checked out before changes | PASS |
| Release, tag commit, PR, merge commit, two changed files and archive metadata inspected read-only | PASS |
| 37/32/35 taxonomy conflict recorded without fabricating a release label list | PASS |
| Offline self-check for two synthetic notes and six BIO spans | PASS |
| `DeepPavlov160NerOfflineAuditTest` | PASS |
| No model/dependency downloads, external inference calls, user-note transfers, Android/runtime changes | PASS |

## Value metric update

- `source_processing_result`:
  `release_and_pr_verified_offline_ner_runner_added_model_execution_deferred`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-a9914bc93c8e-a2d0a76e2800c653-verification.md`;
- `source_value`: `1` offline-only runner and `2` synthetic notes added; `2`
  immutable commits and `2` PR files audited; `3` conflicting taxonomy counts
  recorded; `2/2` focused checks passed; `0` model/dependency downloads,
  external inference calls, user-note transfers, Android/runtime changes.

## Test evidence

- Command:
  `python tools/source_processing/run_deeppavlov_160_ner_offline.py --self-check`
  — exit code `0`; emitted `2` synthetic note results and decoded `6` expected
  BIO spans without importing DeepPavlov.
- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.soll.project.DeepPavlov160NerOfflineAuditTest" --console=plain
  --rerun-tasks` — `BUILD SUCCESSFUL` in `1m 45s`; all `33/33` Gradle tasks
  executed and the focused `1/1` test passed with `0` failures, `0` errors and
  `0` skipped tests.
