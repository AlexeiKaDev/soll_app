---
task_id: 41e79c89396e4b95987e22c3db56e2f7
project: soll_app
source_ref: source-item/9011e13c06d6/0a0137770111aebc
source_item: PACE - A Proxy for Agentic Capability Evaluation
source_processing_result: paper_dataset_parsed_safe_proxy_eval_designed_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-0a0137770111aebc-verification.md
source_value: "26-page arXiv PDF and 412 dataset rows parsed at a pinned revision; 1 safe proxy-eval design, 12 benign contract cases, 4 capability categories, and 8 validation metrics defined; 1/1 focused contract test passed; 0 agent/model runs, 0 autonomous actions, and 0 Android/runtime changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# PACE source processing and safe Soll proxy-eval verification

## Outcome

The acceptance source material was successfully parsed and converted into a
safe design-only Soll artifact. The 26-page arXiv PDF was checked as v2, and all
four JSONL files in `neulab/pace-bench` were parsed at pinned revision
`ce177cfe25bc8c8259cadecb56d4db8d9d36ab18`.

No upstream prompt, image, weight, benchmark runtime or dependency was imported
into Soll. The new contract defines only original synthetic tasks for
`reasoning`, `code_review`, `task_planning` and `source_triage`; every case is a
single static response with no tool call, network, shell, integration,
persistent write or real-system action.

Durable design:

- `docs/knowledge/pace-safe-proxy-eval-design.md`;
- `docs/knowledge/pace-safe-proxy-eval-v1.json`;
- focused validator:
  `app/src/test/java/com/soll/project/PaceSafeProxyEvalContractTest.kt`.

## Paper parse receipt

- source: <https://arxiv.org/pdf/2607.02032>;
- version/pages: `v2`, `26` pages;
- PDF SHA-256:
  `0af39b8c953f0a432735e00f1ea0cf9fa6eb631a643c421fa4d616f0d838fa7b`;
- parsed method: `14` calibration models, `19` candidate source benchmarks,
  `4` target benchmarks, `C=100`, `B=300`, Local Spearman relevance plus Global
  SVD leverage/relevance selection, absolute and pairwise regression, strict
  LOOCV;
- parsed reported average: MAE `3.80%`, Spearman `0.81`, pairwise accuracy
  `84.37%`;
- retained limitations: calibration representativeness, distribution shift,
  point estimates without error bars, and no substitution for full evaluation.

## Dataset parse receipt

- source: <https://huggingface.co/datasets/neulab/pace-bench>;
- revision: `ce177cfe25bc8c8259cadecb56d4db8d9d36ab18`;
- dataset license: `mixed-upstream`;
- files parsed: `4/4` JSONL files;
- rows parsed: `412/412`;
- content status: `405` ok, `7` unresolved;
- score columns: `400` per-target columns, `385` unique across targets using
  `(source_benchmark, subdir, instance_id)`;
- selected source benchmarks: `12`;
- image-bearing rows audited without importing images: `147`;
- null answers accounted for: `46`.

| File | Rows | Unique score columns | Content ok | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| `gaia.jsonl` | 100 | 100 | 99 | `80b92f739c5dbadd5a1689df95ebd6a6d8a519656618cf2fad3833cc14fc966e` |
| `swebench.jsonl` | 100 | 100 | 97 | `50c85e0d9af3bd601258f2e1f5ff75ca02cc2bb5cd1f1894f89aa7c8dd181559` |
| `swebench_multimodal.jsonl` | 105 | 100 | 103 | `8770deea286c155aaefa78b3b3cd77c61e183a7eb6cb3bf6d869b24c9ae4f9ec` |
| `swtbench.jsonl` | 107 | 100 | 106 | `9fbb5fb3f3ee358ed72237a06ee7b6848501cf1f08e86d9f2864b737e4e90135` |

Direct adoption was rejected because upstream licensing is per-source, seven
rows are unresolved, some prompts/checkers cover tool calls, web tasks or code
execution, and the fitted weights are specific to the upstream targets and
model distribution.

## Safe design validation

The contract provides `12` benign cases, exactly `3` in each required category.
It separates an always-run safety sentinel layer from PACE-style regression
selection, requires a separately approved candidate pool and full benign target
suite, validates by nested leave-one-snapshot-out against an equal-budget
baseline, and forbids any proxy claim before calibration.

Eight validation metrics are mandatory: macro MAE, Spearman, pairwise accuracy,
category coverage, safety-sentinel pass rate, unsafe-side-effect count,
abstention precision and schema-valid rate. A score can only shortlist a model
for human review; it cannot route a model, create a task, write state or approve
deployment.

Focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.PaceSafeProxyEvalContractTest" --console=plain
```

Observed results:

1. Initial run: the contract compiled, but `1/1` failed on a capitalization-only
   Markdown evidence assertion; no design, policy or production behavior failed.
2. Final run after aligning that assertion: `BUILD SUCCESSFUL`;
   `1/1 focused contract test passed` with `0` failures, `0` errors and `0`
   skipped tests.

External agent/model runs, benchmark evaluator calls, autonomous actions,
production writes and Android/runtime changes: `0`.

## Value metric update

- `source_processing_result`:
  `paper_dataset_parsed_safe_proxy_eval_designed_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-0a0137770111aebc-verification.md`;
- `source_value`: 26-page arXiv PDF and 412 dataset rows parsed at a pinned
  revision; 1 safe proxy-eval design, 12 benign contract cases, 4 capability
  categories and 8 validation metrics defined; 1/1 focused contract test
  passed; 0 agent/model runs, 0 autonomous actions, and 0 Android/runtime
  changes.
