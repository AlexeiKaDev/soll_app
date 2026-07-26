---
title: Program-as-Weights deep dive for advisory Soll source triage
source: Hugging Face Daily Papers
source_url: https://huggingface.co/papers/2607.02512
arxiv: 2607.02512v1
source_ref: source-item/9011e13c06d6/9070c5ba9670178c
status: local_binary_proxy_smoke_completed_four_label_adoption_deferred
reviewed_at: 2026-07-22 Europe/Chisinau
---

# Program-as-Weights deep dive for advisory Soll source triage

## Decision

Program-as-Weights (PAW) is technically relevant to local, repeatable source
triage, but this review does **not** add it to Soll production or Android. A
real local PAW runtime was exercised on eight synthetic Soll-shaped monitoring
findings. The available public program was a two-label urgency proxy, not the
required `high` / `medium` / `low` / `noise` function: it matched the proxy
oracle on `7/8` cases and over-escalated one `medium` case. The output was
stable across two offline runs.

That is measurable source value, but it is not evidence for four-label
adoption. PAW remains an advisory-only candidate with human review mandatory.
No task, alert, source priority or other Soll state may be changed from its
output.

## Source trace

- monitored item: `Program-as-Weights: A Programming Paradigm for Fuzzy Functions`;
- task source: <https://huggingface.co/papers/2607.02512>;
- primary paper: <https://arxiv.org/abs/2607.02512>;
- paper version reviewed: `arXiv:2607.02512v1`, submitted 2 July 2026;
- Python SDK: <https://github.com/programasweights/programasweights-python>;
- SDK release reviewed and exercised: `v0.4.4`, commit
  `1abdff7e2a3446f3b8807873e568309652cb85d2`;
- source ref: `source-item/9011e13c06d6/9070c5ba9670178c`.

The task-referenced raw file
`raw/monitored\hugging-face-daily-papers\20260705-203016-program-as-weights-a-programming-paradigm-for-fu-b8d3c4f6.md`
is not vendored in this isolated worktree. The claims below were therefore
checked against the primary arXiv paper, the linked GitHub repository and the
public Hugging Face repository metadata at the pinned revisions recorded in
the smoke fixture.

## What PAW actually provides

PAW compiles a natural-language function specification once into a hybrid
artifact: a readable pseudo-program plus an opaque parameter-efficient adapter.
A frozen small interpreter then executes the artifact locally. The paper's
current text path uses a Qwen3 4B compiler and a Qwen3 0.6B interpreter. The
reported main FuzzyBench result is `73.78%` exact match versus `68.70%` for
direct Qwen3-32B prompting, with a shared local base and a per-program adapter.
Those are upstream claims, not Soll results.

The concept fits source-monitor triage because:

1. classification is single-input/single-output and has a small label space;
2. the paper includes an event-driven log-monitoring case study;
3. a versioned local artifact is more reproducible than a per-item cloud API;
4. local inference can keep real finding text on the Soll host after assets are
   prepared.

The paper also gives explicit reasons not to trust it as an autonomous decision
maker: the continuous adapter is opaque, all reported evaluations are
single-step, FuzzyBench is synthetically generated, and broader external
validation is still in progress. Its own qualitative analysis identifies
classification as a relative strength but does not validate Soll's four-tier
policy or cost of false de-escalation.

## GitHub and privacy boundary

The reviewed SDK separates remote preparation from local execution:

- `paw.compile(spec, ...)` sends the specification to
  `/api/v1/compile`; the SDK default is `public=True`;
- `public=False` changes listing semantics but does not make compilation local;
- `paw.prepare_program(...)` downloads a public program bundle and its base
  runtime;
- `paw.function(..., offline=True)` rejects missing cached assets and runs the
  input through local `llama.cpp`;
- the SDK validates bundle paths before extraction and supports a task-local
  cache through `PAW_CACHE_DIR`.

Therefore no real Soll finding, repository content, user text or private policy
may be used in `compile`. A later custom four-label compilation may use only a
reviewed synthetic specification and examples, must be separately approved as
an external write, must set non-public/ephemeral behavior explicitly, and must
still be treated as data disclosure to the compiler operator. Runtime inputs
may be real only after the exact bundle and base are cached and a network-deny
guard proves offline execution.

## License audit

Checked on 22 July 2026:

| Component | Pinned evidence | Result | Consequence |
| --- | --- | --- | --- |
| Paper `2607.02512v1` | arXiv page and license link | **CC BY 4.0 verified** | acceptance criterion met; attribution is required for reuse |
| Python SDK `v0.4.4` | GitHub `LICENSE` at commit `1abdff7e...` | **MIT verified** | SDK code is permissively licensed |
| Qwen3 0.6B base | Hugging Face revision `c1899de...` | **Apache-2.0 declared** | base model has a declared permissive license |
| PAW compiler model | `programasweights/paw-4b-qwen3-0.6b` revision `ae0f52b...` | no license in card metadata and no license file observed | do not import or redistribute without clarification |
| PAW base/program stores | revisions `07c7590...` and `cc2766f...` | no license in card metadata and no license file observed | public availability is not license clearance |
| `fuzzy_bench_verified` | revision `9df57d8...` | no license in card metadata and no license file observed | do not vendor or train on it without clarification |

The acceptance criterion is specifically the paper license and is satisfied.
The wider runtime/data license chain is **not** fully cleared, so this slice
downloads artifacts only into a deleted task-local smoke cache and imports no
PAW dependency, model, program or dataset into the repository.

## Local synthetic smoke

The durable receipt is
`docs/knowledge/program-as-weights-soll-source-triage-smoke-v1.json`.

Preparation used Python SDK `0.4.4`, public precompiled program
`d67162f3ab9562fe2826` (`programasweights/email-triage`) and runtime
`qwen3-0.6b-q6_k`. Preparation downloaded only the public bundle and base;
custom compile calls and specification uploads were `0`. Before inference the
SDK was switched to `PAW_OFFLINE=1` and `offline=True`, socket connection
functions were replaced with a fail-closed guard, and credential lookup was
replaced with a fail-closed guard. Observed network and credential attempts
during inference were both `0`.

The target tiers were retained in the fixture while the public program's
`immediate` / `wait` output was evaluated only as a binary proxy:

| Target tier | Synthetic case | Expected proxy | Observed | Result |
| --- | --- | --- | --- | --- |
| high | source ingestion stopped, restore today | immediate | immediate | pass |
| high | license gate blocks today's release | immediate | immediate | pass |
| medium | weekly source-quality review | wait | wait | pass |
| medium | retry growth for planned maintenance | wait | immediate | **false escalation** |
| low | duplicate article, no action now | wait | wait | pass |
| low | optional topic tag missing | wait | wait | pass |
| noise | community newsletter | wait | wait | pass |
| noise | healthy heartbeat | wait | wait | pass |

Observed on the guarded second run:

- proxy matches: `7/8` (`87.5%`);
- high proxy recall: `2/2` (`100%`);
- false de-escalations: `0`; false escalations: `1`;
- output stability across two offline runs: `8/8`;
- warm-cache load: `815 ms`;
- per-case CPU latency: `69-200 ms`, median `81.5 ms`;
- synthetic inputs: `8`; private inputs: `0`;
- network attempts during inference: `0`; credential attempts: `0`;
- automatic decisions and persistent Soll writes: `0`.

The latency is a single-machine smoke observation, not a benchmark. More
importantly, a two-label program cannot distinguish `medium`, `low` and
`noise`; four target labels were represented in the fixture but exact four-label behavior was not tested.

## Promotion decision and next gate

Production adoption is rejected for this slice. A later proposal can be
reconsidered only when all of these are true:

1. an explicitly approved synthetic-only compile produces a pinned four-label
   artifact without exposing private Soll text;
2. the PAW artifact/model and any dataset used have explicit license clearance;
3. a held-out fixture has at least six cases per tier plus ambiguous boundary
   cases, `>= 90%` macro accuracy, `100%` high recall and no high-to-low/noise
   false de-escalations;
4. offline mode is fail-closed and network-deny verified in the actual server
   runtime;
5. outputs remain suggestions with provenance, confidence/abstention handling
   and mandatory human acceptance;
6. there is no Android, task-board, alerting or source-priority mutation until a
   separately reviewed integration task is approved.

For now the measurable value is the verified license boundary, a real local
runtime receipt, a reproducible synthetic fixture and a concrete stop condition
that prevents a promising paper from silently becoming an autonomous policy.
