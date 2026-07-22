---
task_id: 49b3763d37674cc39779d1e7f3e3581e
project: soll_app
source_ref: source-item/9011e13c06d6/4dd7eedded0ca608
source_item: "Are Performance-Optimization Benchmarks Reliably Measuring Coding Agents?"
source_processing_result: full_paper_v2_downloaded_metrics_audited_soll_reliability_contract_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-4dd7eedded0ca608-verification.md
source_value: "1 current 12-page PDF plus complete TeX source downloaded and SHA-256 verified; 740-task/12-replay methodology and 3 metric layers audited; 1 proposal-only Soll scorecard with 8 task metrics, 14 aggregate metrics and 6 resource guards; 4 synthetic cases validated; 1/1 focused contract test passed; 0 agent/model runs, cloud replays, external data imports or runtime changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# Performance-optimization benchmark reliability verification

## Outcome

The complete current paper was downloaded and analyzed as
`arxiv:2607.01211v2`. The durable detailed review is
`docs/knowledge/performance-optimization-benchmark-reliability.md`; the bounded
machine-readable Soll adaptation is
`docs/knowledge/performance-optimization-benchmark-reliability-v1.json`.

The source has measurable Soll value, but not as a new production leaderboard.
It exposes three separate measurement layers that an internal coding-agent
evaluation must keep apart: reference-signal validity, submission score/rank
sensitivity, and any-of-N task-frontier coverage. A deterministic synthetic
contract smoke proves the proposed calculations without running an agent or a
real performance workload.

## Complete-download receipt

| Artifact | Observed result |
| --- | --- |
| Canonical version | `arxiv:2607.01211v2`, revised 2026-07-16 |
| PDF | 12 pages; 415,035 bytes; SHA-256 `158e3baf87b42faa481ce5b53f82c94618c61f014ef37c9b63b858cd997f942a` |
| TeX source | 238,382 bytes; SHA-256 `f3fcc2ebab992c6c5041581a8ee0759286c78c11b47407cf94c18342f0131860` |
| Archive safety | PASS: 24/24 entries had relative, traversal-free paths |
| Source review | PASS: main TeX plus RQ1–RQ3, discussion, threats, conclusion and data-availability sections reviewed |
| Local location | ignored `build/source-processing/perf-bench-2607.01211v2/`; not vendored into Android/Git |
| Task raw path | supplied monitored raw file absent from isolated worktree; canonical primary source used |

The current v2 was selected instead of silently freezing the monitored v1
snapshot. Paper code/data were not cloned, imported or executed.

## Focused metric audit

| Layer | Paper evidence retained |
| --- | --- |
| Reference validity | 740 tasks, 4 machines, 3 rounds; only 39/102 GSO, 11/140 SWE-Perf and 411/498 SWE-fficiency tasks preserve the original rule across all 12 replays |
| Signal/noise | faster-than-base, original-rule validity, runtime change, within-task standard deviation and std/signal separated |
| Scoring | OPT@1, SpeedUp Ratio, official harmonic mean floor `0.001`, per-task denominator weight and bounded floor `0.5` analyzed |
| Rank sensitivity | official ranks disagree on 9/28 pairs; bounded penalty moves 6/8 ranks and flips 8/28 pairs |
| Task frontier | 450/450 any-of-10 passing, 449/450 faster than base, 384/450 reference-level; explicitly not single-agent capability |
| Limits | three benchmarks, fixed snapshots, strict replay rule, unavailable SWE-Perf public outputs, model-assisted strategy annotation and runtime-heavy scope retained |

## Soll adaptation

The proposal-only contract defines:

- 4 reference/correctness checks before a task enters the performance cohort;
- 8 task metrics, 14 aggregate metrics and 6 resource guards;
- 5 reporting rules and 7 promotion gates;
- separate correctness, faster-than-base and reference-level rates;
- official-floor and bounded-floor harmonic diagnostics plus tail weight;
- separate rank-sensitivity and fleet any-of-N fields;
- zero network, credential, arbitrary-input command, agent-run or production
  behavior in the contract itself.

The four synthetic cases cover a reference-beating candidate, a partial
optimization, a fast but incorrect candidate and an unstable reference task.
The computed cohort is 3 eligible / 1 excluded; correctness and
faster-than-base are 2/3, reference coverage is 1/3, official-floor HM is
`0.0029927`, bounded HM is `0.6760563`, and official worst-1 weight is
`0.9975684`. This pins both the correctness gate and the tail-dominance warning.

No real replay was fabricated: measured internal coding-agent performance
remains `0` pending a separately approved trusted benchmark workload and
multi-machine measurement plan.

## Focused smoke/audit artifact

Test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.PerformanceOptimizationBenchmarkReliabilityTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1 focused contract test passed` with
`0` failures, `0` errors and `0` skipped tests.

The test pins task/source provenance, both download checksums, paper findings,
the complete metric inventory, proposal-only safety boundary, cohort exclusion,
correctness gating, speedup ratios, both harmonic diagnostics, tail weight and
all required value-metric keys.

Agent/model runs, cloud replays, external data imports, arbitrary commands from
source content, Android/server behavior changes and production writes: `0`.

## Value metric update

- `source_processing_result`:
  `full_paper_v2_downloaded_metrics_audited_soll_reliability_contract_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-4dd7eedded0ca608-verification.md`;
- `source_value`: one current 12-page PDF plus complete TeX source downloaded
  and SHA-256 verified; the 740-task/12-replay methodology and three metric
  layers audited; one proposal-only Soll scorecard with 8 task metrics, 14
  aggregate metrics and 6 resource guards; four synthetic cases validated;
  `1/1 focused contract test passed`; agent/model runs, cloud replays, external
  data imports and runtime changes: `0`.
