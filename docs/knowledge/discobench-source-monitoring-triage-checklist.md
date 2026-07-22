---
title: DiscoBench checklist for Soll source-monitoring triage
task_id: 5921307b9b854acaa30401b438124927
source_ref: source-item/9011e13c06d6/1a64df4ff985ceb0
review_status: checklist_added
source_url: https://huggingface.co/papers/2606.27669
---

# DiscoBench checklist for Soll source-monitoring triage

## Scope

DiscoBench evaluates clarification-aware deep search over 211 questions, 463
ambiguity instances and 11 domains. The paper treats ambiguity as a checkpoint
inside a multi-hop search trajectory: an unresolved early checkpoint can send
all later retrieval down a plausible but incorrect path.

This note adapts that evaluation vocabulary into a proposal-only Soll triage
checklist. It does not add a search agent, automatically question a user, write
to a task board, or change Android behavior. The task-referenced raw snapshot is
absent from this isolated worktree, so the taxonomy, metrics and reported
results were checked against the public
[arXiv v2 paper](https://arxiv.org/html/2606.27669) and
[Hugging Face paper record](https://huggingface.co/papers/2606.27669).

## Four ambiguity checks

At every source-processing checkpoint, mark all matching checks before
promoting a fact, source match, project fit or proposed next action.

### 1. Entity

- **Signal:** two or more entities satisfy the same description.
- **Ask for:** the smallest distinguishing attribute, relation, name or event.
- **Do not:** select the first plausible entity and propagate it downstream.

### 2. Version

- **Signal:** multiple dates, releases, editions or historical states fit.
- **Ask for:** the intended date, version, interval or "as of" boundary.
- **Do not:** silently substitute the newest or most prominent version.

### 3. Criteria

- **Signal:** the same wording maps to different ranking, inclusion, scoring or
  policy standards.
- **Ask for:** the intended population, scope, metric or decision rule.
- **Do not:** choose a convenient criterion after seeing search results.

### 4. Factual Inaccuracy

- **Signal:** the request conflicts with reliable evidence or a bounded search
  finds no factually consistent target.
- **Ask for:** the suspect name, relation, date or premise to be corrected.
- **Do not:** repair the premise silently or invent a match.

## SearchThenAsk rule

Use the transition **Search -> Detect -> Ask -> Search**:

1. Run a bounded evidence search for the current checkpoint and retain the
   candidate set or the evidence conflict.
2. Continue without asking only when the evidence identifies one consistent
   target under an explicit version and criterion.
3. Stop broadening retrieval when an Entity, Version, Criteria or Factual
   Inaccuracy signal remains. Record the ambiguity type and ask one targeted
   question for the smallest discriminative clue.
4. Add the clue to the checkpoint constraints, search again, and advance only
   when the target is evidence-backed. Re-check downstream conclusions that
   depended on the ambiguous checkpoint.
5. If no clarification arrives, or the clue is still insufficient, return
   `needs_clarification` with the candidates/conflict and evidence references.
   Never turn unresolved ambiguity into a guessed fact or an unbounded search
   loop.

This is a Soll operating rule derived from the paper's behavior analysis, not
a claim that every uncertain query should trigger an immediate question. The
paper defines SearchThenAsk as retrieval before clarification. On the common
146-checkpoint subset, its mean checkpoint pass rate was 93.4%, compared with
56.5% for DirectGuess and 51.9% for SearchHeavyGuess. The useful lesson is the
action transition: convert retrieval uncertainty into clarification instead of
searching repeatedly and then guessing.

## Evaluation card

Keep the paper's metrics separate so a high-quality question cannot hide poor
ambiguity detection, and frequent asking cannot hide failure to use the clue.

| Perspective | Metric | Soll triage reading |
| --- | --- | --- |
| Task utility | End-to-end accuracy | final triage decision/answer matches the reviewed result |
| Task utility | Checkpoint pass rate | proportion of source/reasoning checkpoints correctly advanced, normalized per item |
| Ambiguity detection | Detection accuracy | correct ask/no-ask decisions across reached ambiguous and non-ambiguous checkpoints |
| Ambiguity detection | Detection F1 | precision/recall balance for correctly targeted asks at ambiguous checkpoints |
| Interaction quality | CE-A, clarification-question accuracy | targeted clarification questions divided by all Ask checkpoints |
| Interaction quality | CE-B, clarification-to-advance rate | Ask checkpoints where the clue is used to advance divided by all Ask checkpoints |
| Cost efficiency | average Ask turns | clarification burden per triaged source item |
| Cost efficiency | tool-use turns | retrieval/tool burden per triaged source item |
| Cost efficiency | token consumption | input and output token cost for the evaluated batch |

For detection accounting: a targeted Ask on an ambiguous checkpoint is a true
positive; a missed or mistargeted Ask is a false negative; an unnecessary Ask
on a non-ambiguous checkpoint is a false positive; proceeding on a
non-ambiguous checkpoint is a true negative. CE-A and CE-B use all Ask
checkpoints as their denominator.

## Minimal audit record

For each triaged item retain:

- `source_ref` and checkpoint id;
- ambiguity type(s) or explicit `none`;
- candidate set, conflicting premise or missing criterion/version;
- evidence references and search/tool turns before Ask;
- exact clarification question and returned clue;
- post-clue evidence, checkpoint outcome and final status;
- Ask turns, tool-use turns and token counts.

The checklist passes only when the ambiguity decision is auditable, every
resolved checkpoint has evidence, and unresolved checkpoints remain
`needs_clarification`. Runtime model-quality improvement remains unmeasured
until this card is exercised on a separately approved, non-sensitive eval set.
