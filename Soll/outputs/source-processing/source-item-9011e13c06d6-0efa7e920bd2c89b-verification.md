---
task_id: 040135ed3f2a488ba79e18061e5b0007
project: soll_app
source_ref: source-item/9011e13c06d6/0efa7e920bd2c89b
source_item: Transferability for General Reasoning - An Automated Curriculum for Multi-Domain RLVR
source_processing_result: tac_research_note_added_replay_pilot_deferred
verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-0efa7e920bd2c89b-verification.md
source_value: "1 TAC research note; 5 algorithm stages and 2 required signals documented; 1 replay-only Soll hypothesis with 5 evaluation metrics and 6 promotion gates; 2/2 primary links returned HTTP 200; 1/1 focused contract test passed; 0 fine-tuning, security-code generation, or production/runtime changes"
verified_at: 2026-07-22 Europe/Chisinau
---

# TAC research-note verification

## Outcome

The Hugging Face Daily Papers signal produced a bounded research note at
`docs/knowledge/transfer-aware-curriculum-soll-ranking-hypothesis.md`.
It documents TAC as a bandit curriculum, the mean-absolute-advantage
learnability signal, the projected-gradient/cosine transferability signal,
smoothing/normalization, the composite score and the two-phase update.

The note proposes one separate, safe Soll hypothesis: compare a transparent
local-yield plus cross-area-reuse score with chronological and local-yield-only
rankings in an offline historical replay. The proposal is advisory-only. No
model, upstream code, training dependency, runtime ranking, source priority,
task or Android behavior was added.

## Source and link audit

The task-referenced raw path
`raw/monitored\hugging-face-daily-papers\20260705-203016-transferability-for-general-reasoning-an-automat-6dc17485.md`
is absent from this isolated worktree. Claims were checked against primary
sources linked from the supplied Hugging Face paper page.

Read-only URL smoke executed on 22 July 2026:

| Link | Observed result |
| --- | --- |
| <https://arxiv.org/abs/2606.25178> | HTTP `200`; arXiv record title and `v2` available |
| <https://github.com/YangYongJin/transfer-aware-curriculum> | HTTP `200`; public official TAC repository and README available |

Functional links: `2/2`. No login, credential, API write, repository clone or
external side effect was used.

## Focused knowledge audit

| Check | Observed result |
| --- | --- |
| Traceability | exact task id, source ref, raw path, Hugging Face, arXiv and official GitHub retained |
| TAC algorithm | 5 stages plus bandit selection, warmup, UCB exploration and two-phase value update documented |
| Learnability | mean absolute GRPO advantage, running normalization and zero-signal ambiguity documented |
| Transferability | projected unit gradients, per-domain EMA, mean cross-domain cosine, smoothing and relative normalization documented |
| Upstream results | six domains, 14 benchmarks and reported macro claims separated from Soll value |
| Safe Soll hypothesis | 1 offline, replay-only advisory ranking proposal |
| Evaluation | 5 replay metrics and 6 explicit promotion/rejection gates |
| Safety boundary | no fine-tuning, model gradients, security-code generation, automatic decisions or external writes |
| Production delta | 0 production/runtime/API/UI/dependency/source-priority/task-board changes |

## Focused smoke result

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.TransferAwareCurriculumKnowledgeTest" --console=plain
```

Observed result: exit code `0` (`BUILD SUCCESSFUL`); focused contract result
`1/1` passed with `0` failures, `0` errors and `0` skipped tests.

Two initial contract-only runs failed before the successful final run. The
first exposed an assertion spanning a Markdown line wrap; the test was changed
to normalize whitespace. The second exposed an overly narrow expected audit
phrase; it was aligned with the complete zero-effect statement. Neither run
executed production behavior, and both documentation/test consistency issues
were resolved before the final receipt above.

`TransferAwareCurriculumKnowledgeTest` pins the task/source trace, primary
links, five TAC stages, both metric definitions, composite score, safe Soll
hypothesis, replay metrics, promotion threshold, zero-effect boundary and the
three required value-metric keys.

## Value metric update

- `source_processing_result`:
  `tac_research_note_added_replay_pilot_deferred`;
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-9011e13c06d6-0efa7e920bd2c89b-verification.md`;
- `source_value`: 1 TAC research note; 5 algorithm stages and 2 required
  signals documented; 1 replay-only Soll hypothesis with 5 evaluation metrics
  and 6 promotion gates; 2/2 primary links returned HTTP 200; 1/1 focused
  contract test passed; 0 fine-tuning, security-code generation, or
  production/runtime changes.

The delivered value is durable research guidance plus a falsifiable replay
contract. Ranking quality remains unmeasured, so runtime adoption is deferred
unless a later approved replay passes every promotion gate.
