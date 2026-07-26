---
task_id: 798292d19f554cc6b64f0ecc2c9eaeb4
project: soll_app
source_ref: source-item/9011e13c06d6/43bc9d100c528d57
source_item: "When LLMs Read Tables Carelessly: Measuring and Reducing Data Referencing Errors"
source_processing_result: table_dre_analysis_assigned_integration_contract_smoke_passed
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-43bc9d100c528d57-verification.md
source_value: "1 assigned analyst role; full arXiv v1 paper and TeX source verified; 2 DRE classes, 3 paper metrics and 2 mitigation methods analyzed; 1 proposal-only 5-stage server-side integration contract with 12 evaluation metrics and 10 promotion gates; 4 synthetic outcomes, 4 filtering candidates and 3 rejection traces validated; 1/1 focused contract test passed; 0 model/critic runs, upstream imports, external integrations or runtime changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# Table data-referencing error analysis verification

## Outcome

The **Soll Table Reliability Analyst** role was assigned to the analytical part
of the future Server/AI table pipeline. The assignment produced a detailed
paper review and a machine-readable integration contract:

- `docs/knowledge/table-data-referencing-error-reduction.md`;
- `docs/knowledge/table-data-referencing-error-reduction-v1.json`;
- `TableDataReferencingErrorReductionTest` as the focused smoke.

The repository has no current table-processing runtime. The existing Android
model-chat path is backend-mediated by `SollGateway.askModelChat(...)`.
Accordingly, the result integrates the error-reduction methods as a bounded
server-side contract and promotion plan, without fabricating an Android critic
or a live backend change.

## Source receipt

| Check | Result |
| --- | --- |
| Canonical version | PASS: `arxiv:2606.32029v1`, 2026-06-30, ACL 2026 Oral |
| PDF | PASS: 558,235 bytes; 19 page objects; SHA-256 `9fd9f94b6b21136f44fe529e5eab5715970338e35277392de6c0db7d2787d551` |
| TeX source | PASS: 264,898 bytes; 9/9 traversal-free entries; SHA-256 `e04af27216e44f3609afb7c2e62226b49a4ea207f3769872971902c5f4742c10` |
| Full-text review | PASS: method, experiments, appendices, critic prompt, synthetic rules, training details and limitations reviewed |
| Public code | READ-ONLY audit at `7e17ba3ec2d8f0238df8f2a1094491162ae10946`; no `LICENSE` file observed; not imported or executed |
| Task raw path | Supplied monitored raw file absent from this isolated worktree; not claimed as evidence |

## Focused analysis and integration audit

| Check | Result |
| --- | --- |
| Analyst assignment | PASS: 1 assigned analyst role, `Soll Table Reliability Analyst` |
| Taxonomy | PASS: 2/2 classes: incorrect citation and omitted information |
| Paper metrics | PASS: DRE rate, Correct-in-DRE and DRE-in-Incorrect |
| Mitigation | PASS: critic filtering retains the minimum-DRE subset; rejection sampling is segment-level and bounded |
| Paper evidence | PASS: judge accuracy `92.67%`; Critic-4B overall F1 `78.16%`; maximum reported full-set RS gain `+11.96pp` |
| Soll stages | PASS: normalize, generate, deterministic checks, critic and policy |
| Evaluation inventory | PASS: 12 quality/runtime metrics plus 5 cost metrics |
| Promotion boundary | PASS: 10 gates, synthetic-only OOD evidence rejected, rollback required |
| Runtime boundary | PASS: proposal-only; 0 Android/server runtime behavior changes |

The Soll adaptation deliberately does not use hidden chain-of-thought or the
paper's literal `Wait` delimiter as an API contract. It checks an explicit
answer/evidence segment or the complete public answer and fails closed to
`needs_human_review` or `abstain` at the retry limit.

## Focused smoke/audit artifact

The deterministic fixture validates 4 synthetic outcomes, 4 filtering
candidates and 3 rejection traces. Observed metrics:

- DRE rate `0.5`;
- incorrect-citation and omission rates `0.25` each;
- Correct-in-DRE `0.5` and DRE-in-Incorrect `0.5`;
- final answer accuracy `0.5`;
- filtering retains both zero-DRE candidates, including one logically wrong
  answer, proving that the critic is not a final-answer oracle;
- rejection accepts attempts `1` and `2`, then routes an exhausted trace to
  `needs_human_review`.

Focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.TableDataReferencingErrorReductionTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1 focused contract test passed` with
`0` failures, `0` errors and `0` skipped tests.

Model/critic executions, upstream imports, external integrations, credentials,
production table requests and Android/server runtime changes: `0`.

## Value metric update

- `source_processing_result`:
  `table_dre_analysis_assigned_integration_contract_smoke_passed`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-43bc9d100c528d57-verification.md`;
- `source_value`: 1 assigned analyst role; full arXiv v1 paper and source
  verified; 2 DRE classes, 3 paper metrics and 2 mitigation methods analyzed;
  one 5-stage server-side integration contract with 12 evaluation metrics and
  10 promotion gates; 4 synthetic outcomes, 4 filtering candidates and 3
  rejection traces validated; `1/1 focused contract test passed`; model/critic
  runs, upstream imports, external integrations and runtime changes: `0`.
