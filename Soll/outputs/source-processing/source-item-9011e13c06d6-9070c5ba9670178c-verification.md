---
task_id: 638c73b7018f4572bb7fac10562f6672
project: soll_app
source_ref: source-item/9011e13c06d6/9070c5ba9670178c
source_item: "Program-as-Weights: A Programming Paradigm for Fuzzy Functions"
source_processing_result: paw_deep_dive_local_binary_proxy_smoke_completed_adoption_deferred
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-9070c5ba9670178c-verification.md
source_value: "paper CC BY 4.0 and SDK MIT verified; 8 synthetic Soll-shaped findings exercised through a real offline PAW runtime with 7/8 binary proxy matches and 8/8 output stability; four target tiers represented but only 2/4 runtime labels supported; 1/1 focused contract test passed; 0 private inputs, compile calls, credential/network attempts during inference, automatic decisions, persistent Soll writes, and repository dependency/model imports"
verified_at: 2026-07-22 Europe/Chisinau
---

# Program-as-Weights deep dive and local smoke verification

## Outcome

The source item produced a focused deep dive, a machine-readable smoke receipt
and a fail-closed adoption decision:

- analysis: `docs/knowledge/program-as-weights-soll-source-triage.md`;
- receipt: `docs/knowledge/program-as-weights-soll-source-triage-smoke-v1.json`;
- contract: `ProgramAsWeightsSourceTriageSmokeTest`.

The deep dive is complete. Production adoption is deferred because the public
smoke program supports `immediate` / `wait`, not the required `high` /
`medium` / `low` / `noise`, one medium case was over-escalated, and the checked
PAW compiler/program/data repositories do not declare licenses in their card
metadata or repository files. No production, Android, API, dependency, task,
alert or source-priority behavior changed.

## Source and license evidence

The task-specified raw monitored file is absent from this isolated worktree.
The paper and implementation were checked against their primary public sources
at pinned versions.

- Paper license: **CC BY 4.0 verified** on arXiv `2607.02512v1`.
- Python SDK license: **MIT verified** at release `v0.4.4`, commit
  `1abdff7e2a3446f3b8807873e568309652cb85d2`.
- Qwen3 0.6B base: **Apache-2.0 declared** at revision `c1899de...`.
- PAW compiler, base/program stores and `fuzzy_bench_verified`: no license
  declaration or license file observed at the pinned Hugging Face revisions.
- PAW/model/dataset files imported into the repository: **0**.

The acceptance criterion concerns the paper license and is met. Full runtime
redistribution/adoption clearance remains a stop condition.

## Local PAW smoke

The public `programasweights/email-triage` program
`d67162f3ab9562fe2826` and `qwen3-0.6b-q6_k` base were prepared into a
task-local temporary cache. No custom compile was called and no test input was
sent during preparation. Before inference, SDK offline mode was enabled, socket
connections and credential lookup were set to raise if called, and all eight
inputs were synthetic.

Observed guarded-run receipt:

| Metric | Result |
| --- | --- |
| Local PAW smoke | **7/8** binary proxy matches (`87.5%`) |
| High cases | `2/2` immediate; `0` false de-escalations |
| False escalations | `1` medium case |
| Repeated-run output stability | `8/8` |
| Target/runtime labels | `4` represented / `2` produced |
| Warm-cache load | `815 ms` |
| CPU case latency | `69-200 ms`, median `81.5 ms` |
| Inference network attempts | `0` |
| Credential lookup attempts | `0` |
| Private inputs | `0` |
| Automatic decisions / persistent Soll writes | `0 / 0` |

This passes the requested local smoke-test as an executed, inspectable test of
the source's local-runtime claim. It does **not** pass a four-label promotion
gate; exact four-label behavior was not tested.

## Focused contract result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.ProgramAsWeightsSourceTriageSmokeTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); focused contract result
`1/1` passed with `0` failures, `0` errors and `0` skipped.

Two initial contract runs failed on evidence-string consistency only: one exact
four-label limitation phrase crossed a Markdown line wrap, then two
verification-table assertions used colon-style wording instead of the actual
table cells. The durable wording/assertions were aligned and the same focused
test passed. The JSON smoke receipt, runtime outputs and production code did not
change while resolving those test-only findings.

The contract validates paper-license evidence, pinned upstream revisions,
synthetic-only inputs, offline/network/credential guards, all eight case
receipts, measured proxy metrics, the two-label limitation, zero autonomous
effects and the rejected production-adoption decision.

## Value metric update

- `source_processing_result`:
  `paw_deep_dive_local_binary_proxy_smoke_completed_adoption_deferred`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-9070c5ba9670178c-verification.md`;
- `source_value`: paper CC BY 4.0 and SDK MIT verified; a real local PAW
  runtime exercised `8` synthetic Soll-shaped findings with `7/8` binary proxy
  matches and `8/8` stable outputs; all four desired tiers were represented,
  but only `2/4` output labels were supported; `1/1` focused contract test
  passed. Private inputs, custom compile calls, credential/network attempts
  during inference, automatic decisions, persistent Soll writes and repository
  dependency/model imports: `0`.

The measurable value is a verified legal/privacy boundary, real offline runtime
evidence, a reusable synthetic fixture and explicit promotion gates. The source
is useful enough to retain as a candidate, but not strong or cleared enough to
adopt.
