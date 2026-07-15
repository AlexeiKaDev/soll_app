---
task_id: a67b4eeefdc94eaaaad5a141a9976802
source_ref: source-item/e1174ebfd950/64af1d1a2fd48283
source_item: how-we-contain-claude-across-products-f328ce89
source_processing_result: security_review_completed_recommendations_ready
verification_artifact: Soll/outputs/source-processing/source-item-e1174ebfd950-64af1d1a2fd48283-verification.md
source_value: "9 containment recommendations and 7 measurable promotion gates prepared; 1 Android trust gap identified; 0 production containment changes because this task is review-only"
verified_at: 2026-07-15 Europe/Chisinau
---

# Anthropic agent-containment source audit for `soll_app`

## Outcome

The Soll security review is complete. The implementation recommendations are
recorded in
`docs/security/anthropic-agent-containment-recommendations.md` and assigned by
control to Security, Platform, Backend, Android, Product and AI Core owners.

The review preserves the current architecture: Android remains an approval,
observability and evidence client, while any tool-capable agent execution must
be contained in desktop/server workers. No sandbox, proxy, credential, external
integration or production behavior was changed by this task.

## Evidence reviewed

- Official Anthropic Engineering article:
  `https://www.anthropic.com/engineering/how-we-contain-claude`, published
  2026-05-25.
- `CapabilityRegistry` and `CommandSafetyGate` for registered Android command
  risk, permission and confirmation checks.
- `MetaCoordinatorActionGate` for capability matching and confirmation of
  server-suggested actions.
- `ChatViewModel.actionUis()` and `SollRepository.executeChatAction()` for the
  current chat action boundary.
- Android manifest/build policy for disabled backup and release cleartext
  denial.
- the Soll app roadmap's desktop/server reasoning boundary and Android
  approval/observability role.

The task-referenced raw file
`raw/monitored\anthropic-engineering\20260702-192923-how-we-contain-claude-across-products-f328ce89.md`
is absent from this isolated worktree. The official source URL supplied by the
task was therefore used as read-only evidence.

## Focused audit mapping

| Source lesson | Soll finding | Recommendation |
| --- | --- | --- |
| environment bounds must survive model mistakes | Android gates exist; no server worker isolation proof is in this repo | C1 disposable OS-isolated worker |
| config before trust is attacker input | no pre-trust worker/config contract is documented | C2 trust before parsing and canonical path checks |
| filesystem and egress controls must overlap | no default-deny worker egress proof is present | C3 capability-aware egress broker |
| credentials should not enter the sandbox | no per-job principal contract is present | C4 short-lived worker identity |
| trusted transport can return poisoned content | source/tool provenance is not an execution authority model | C5 provenance plus action separation |
| broad approvals create fatigue | command confirmations are action-oriented, not bounded worker leases | C6 explicit capability leases |
| isolation reduces host visibility | no out-of-sandbox containment event contract is present | C7 append-only security telemetry |
| memory and sub-agent output can escalate trust | no inherited trust label is documented | C8 provenance-preserving memory and delegation |
| custom glue is often the weakest layer | chat action metadata accepts any non-blank type locally | C9 adversarial boundary tests |

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Security review exists | durable document under `docs/security` | PASS |
| Article lessons are covered | environment, model and external-content layers | PASS |
| Recommendations fit Soll | desktop/server containment; Android remains client | PASS |
| Current controls cite real code | capability, command, coordinator and chat boundaries | PASS |
| Gaps are explicit | isolation, egress, identity, provenance and telemetry | PASS |
| Rollout is measurable | 9 recommendations and 7 promotion gates | PASS |
| Review stays safe | documentation/test/artifact only; 0 production changes | PASS |
| Value metrics are attached | result, artifact path and source value | PASS |

`AnthropicContainmentSourceTriageTest` verifies the durable review decision,
the full recommendation/gate count, the roadmap link and the value-metric
fields in this artifact.

## Promotion decision

Accept the source as actionable security-engineering input and create separate
implementation tasks in the documented delivery order. Do not claim production
containment until a synthetic non-production trial passes all seven gates. Do
not add an agent shell, general execution environment or provider credentials
to Android.

## Value metric update

- `source_processing_result`:
  `security_review_completed_recommendations_ready`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-e1174ebfd950-64af1d1a2fd48283-verification.md`
- `source_value`: `9` implementation recommendations and `7` measurable
  promotion gates were prepared, and `1` concrete Android trust gap was
  identified. Measured production containment change remains `0` because the
  approved scope is review and recommendation only.
