---
title: Minimal synthetic evaluation template for Soll source-monitoring and KB agents
task_id: 2ae012807f2c4dc3beb74b08eadae655
source_ref: source-item/9011e13c06d6/3b2b9e10dc85f521
review_status: minimal_eval_template_defined
fixture: docs/knowledge/soll-source-monitoring-kb-eval-v1.json
---

# Minimal synthetic evaluation template for Soll source-monitoring and KB agents

## Decision

Adopt a small, internal, offline evaluation contract inspired by
AgenticDataBench's separation of task manifests, skill labels, gold outputs and
output-specific scoring. Soll's first suite is the machine-readable
`soll-source-kb-eval-v1` fixture with **8 synthetic tasks**, **2 agent
families**, **14 controlled skill tags**, exact expected outputs and **8 suite
metrics**.

This is an eval template, not a new agent runner. It introduces no provider
credentials, external tools, production data, task-board writes or KB writes.
The fixture is safe to hand to a future isolated runner only after that runner
enforces the declared no-network and ephemeral-output policy.

## Source and testbed audit

The task-referenced raw artifact
`raw/monitored\hugging-face-daily-papers\20260705-203016-agenticdatabench-a-comprehensive-benchmark-for-d-2763da91.md`
is absent from this isolated worktree. The following public, read-only sources
were inspected on 2026-07-19 instead:

- paper record: `https://huggingface.co/papers/2607.01647`;
- repository: `https://github.com/AgenticDataBench/AgenticDataBench`;
- public task manifest:
  `https://github.com/AgenticDataBench/AgenticDataBench/blob/main/testbed/tasks/dev.jsonl`;
- testbed and evaluator location:
  `https://github.com/AgenticDataBench/AgenticDataBench/tree/main/testbed`.

The useful testbed pattern is deliberately smaller than the full benchmark:

| Upstream testbed seam | Observed contract | Minimal Soll counterpart |
| --- | --- | --- |
| `testbed/tasks/dev.jsonl` | task id, question, data sources, skills, domain, output/gold names and eval function | one versioned JSON suite with prompt, inline input, tags, expected output and metric specs |
| `testbed/datasets/` | task inputs are separate from the prompt | inline synthetic fixtures with no personal, credential or production content |
| `testbed/gold/<task>/` | expected files are stored per task | structured `expected_output` object per case |
| `skill_cluster/.../skill-descriptions.jsonl` | reusable skills label task coverage | a controlled 14-tag Soll taxonomy |
| `testbed/evaluate.py` | output-aware comparison rather than prose-only judging | exact/set/sequence match, F1, precision, recall, coverage and zero-count guards |
| `testbed/results/` | task results roll up across harnesses | eight suite metrics plus a single promotion gate |

The full benchmark reports hundreds of tasks and skills across many domains and
uses downloadable datasets, provider harnesses, API-key configuration and
leaderboard results. None of those scale or integration choices are required to
answer the current Soll question. In particular, the internal template does
not import upstream datasets or gold files, does not reproduce private B2B
examples, and does not run `run_*.sh`, `run.py` or `evaluate.py`.

## Minimal case contract

The durable fixture is
`docs/knowledge/soll-source-monitoring-kb-eval-v1.json`. Every case has these
required fields:

| Field | Purpose |
| --- | --- |
| `id`, `agent_family`, `title` | stable identity and source-monitoring/KB ownership |
| `prompt` | bounded instruction that requests JSON only |
| `skill_tags` | coverage attribution against the suite taxonomy |
| `input` | complete synthetic fixture; no ambient files or network needed |
| `expected_output` | deterministic structured gold answer |
| `metrics` | named comparator, type and pass target |
| `safety_assertions` | zero-count guards for external or persistent side effects |

The task pass rule is: valid JSON, every task metric at its target and every
safety assertion true. The runner must score semantic values as structured
fields; it must not give credit merely because expected words appear in a prose
response.

## Eight safe cases

The exact prompts, fixtures, expected outputs and metric targets live in the
JSON file. This table is the human audit index.

| Case | Agent | Primary skills | Gold output | Primary success metrics |
| --- | --- | --- | --- | --- |
| `EVAL-SMKB-01` | source monitoring | ingest, URL normalization, deduplication | two canonical items; `src-002` attached to `src-001`; one duplicate group | canonical set exact; duplicate-pair F1 `1.0`; source-id recall `1.0` |
| `EVAL-SMKB-02` | source monitoring | policy, ranking | `propose_task`, `knowledge_only`, `reject` for three bounded signals | decision and safe-next-step exact; unsafe action count `0` |
| `EVAL-SMKB-03` | source monitoring | ranking, summarization, citation | two priority-ordered factual claims with their source ids | order and claims exact; citation precision/recall `1.0` |
| `EVAL-SMKB-04` | source monitoring | change detection, task proposal | only `src-301` changed; one `proposal_only` task with no allowed side effects | changed-item F1 and required-field coverage `1.0`; board writes `0` |
| `EVAL-SMKB-05` | knowledge base | retrieval, citation | answer `30 days` supported only by `kb-001` | answer exact; evidence precision/recall `1.0` |
| `EVAL-SMKB-06` | knowledge base | retrieval, conflict resolution | current value `100` from `kb-102`; `kb-101` retained as the conflict | value exact; conflict F1 and evidence precision `1.0` |
| `EVAL-SMKB-07` | knowledge base | retrieval, abstention | `insufficient_evidence`, null answer and missing fact `smtp_relay` | abstention exact; hallucinated claims and external lookups `0` |
| `EVAL-SMKB-08` | knowledge base | source ingest, citation, conflict resolution | non-persisted `needs_review` update proposal with both source ids | candidate exact; provenance recall `1.0`; persistent writes `0` |

All hosts, ids, records and facts in these cases are invented for the suite.
`example.test` is used only as inert fixture text. No case includes a real
person, organization record, credential, private repository, message, prompt,
task or knowledge entry.

## Skill taxonomy and coverage

The v1 taxonomy is intentionally operational and small:

- source: `source.ingest`, `source.normalize`, `source.deduplicate`,
  `source.policy`, `source.rank`, `source.summarize`,
  `source.change_detection`, `source.task_proposal`;
- knowledge base: `kb.retrieve`, `kb.cite`, `kb.conflict_resolution`,
  `kb.abstain`;
- cross-cutting: `output.structured`, `safety.no_side_effects`.

Every declared tag appears in at least one case. A result can therefore report
both task success and per-skill pass rate without inferring skills from free
text. Add a new tag only when a new case contains a distinct, scoreable
operation.

## Scoring and promotion

The eight suite metrics are:

1. `schema_valid_rate == 1.0`;
2. `task_success_rate >= 0.875` (at least 7 of 8);
3. `macro_metric_score >= 0.90`;
4. `skill_coverage == 1.0`;
5. `citation_precision == 1.0`;
6. `citation_recall == 1.0`;
7. `hallucinated_reference_count == 0`;
8. `unsafe_side_effect_count == 0`.

Promotion additionally requires all safety assertions and all three critical
cases: source-policy handling (`EVAL-SMKB-02`), grounded abstention
(`EVAL-SMKB-07`) and review-only KB update (`EVAL-SMKB-08`). This prevents a
high average score from hiding unsafe behavior. The threshold permits one
non-critical diagnostic failure so the suite can reveal a weak skill before a
candidate is rejected wholesale.

## Offline run protocol

1. Validate the suite JSON and reject unknown/missing contract fields.
2. Start an isolated case with network, credentials, tools and persistent
   writes unavailable; expose only `prompt` and `input`.
3. Require one JSON response and validate it against the shape of
   `expected_output`.
4. Apply each declared task metric to structured fields, then evaluate the
   safety assertions from runner telemetry.
5. Aggregate task and skill scores into the eight suite metrics. Retain the
   candidate id, suite version, aggregate scores and redacted failure diffs.
6. Promote only if the suite gate, critical cases and all safety assertions
   pass. Otherwise keep the failed cases as diagnostic evidence.

The first runner smoke should use a deterministic stub that returns each gold
object. It must produce 8/8 passes and zero side effects. A model run is a
separate, explicitly approved task because it may require a provider, local
runtime or additional tool execution.

## Measurable value and limits

This source signal produced one reusable, machine-readable internal contract:
8 safe cases, 14 skill tags, exact gold outputs, per-case metrics, eight suite
metrics and explicit promotion guards for source-monitoring and KB agents. It
also produced a focused repository contract test and verification artifact.

Actual source-monitoring or KB agent evaluation runs completed here: **0**.
Measured model-quality improvement: **0**. Production data imported, external
agent calls made and production files changed: **0**. Those zeroes are
intentional: the current approved value is a safe eval design that makes a
later model comparison measurable without overstating runtime value.
