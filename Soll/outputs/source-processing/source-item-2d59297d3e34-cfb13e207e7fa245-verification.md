---
task_id: b4a9366249e144fdbbf37e05aa2e8745
project: soll_app
source_ref: source-item/2d59297d3e34/cfb13e207e7fa245
source_trust: untrusted_external_content
source_processing_result: ci_pinning_audit_completed_no_local_refs
verification_artifact: Soll/outputs/source-processing/source-item-2d59297d3e34-cfb13e207e7fa245-verification.md
source_value: "2 upstream hardening commits, 5 pre-commit hook revisions, 4 GitHub Actions usages and 2 local configuration scopes audited; 0 local hook/action references require pinning; 1/1 focused contract test passed; 0 application, build, dependency or runtime changes"
verified_at: 2026-07-23 Europe/Chisinau
---

# OpenAI Evals CI and pre-commit pinning audit

## Outcome

OpenAI Evals hardened two existing configuration surfaces without changing
their behavior: five pre-commit hook release refs became full 40-character
commit SHAs, while four occurrences of two GitHub Actions release refs became
full commit SHAs with the former major version retained as a comment.

At base SHA `f081f8070a55b250325f391f05c22f07d80bde27`, the current
Soll_app repository has no `.pre-commit-config.yaml`,
`.pre-commit-config.yml`, or `.github/workflows` files. Its in-repository
`Soll/` subtree also contains none of those configuration paths. Therefore
there are zero existing local hook or action references to pin, and copying
the upstream files would add a new CI/tooling architecture rather than harden
an existing one. This task makes no configuration or application-logic change.

The comparison is intentionally limited to the current isolated worktree. It
does not claim to describe a separate Soll server repository outside this
worktree.

## Trust boundary and evidence

- Task source reference:
  `source-item/2d59297d3e34/cfb13e207e7fa245`.
- Monitored capture identifier:
  `raw/monitored\openai-evals-commits\20260709-234628-commits-openai-evals-github-85f7deef.md`.
- Upstream pre-commit commit:
  <https://github.com/openai/evals/commit/8eac7a7de5215c907fbddc30efdaf316913eccdd>.
- Upstream GitHub Actions commit:
  <https://github.com/openai/evals/commit/dbb1a20192809f5004d0c274374963b1e3cb20bf>.

The monitored capture is absent from this isolated worktree. Its untrusted
description was treated only as a source pointer. The two public commit
patches were inspected read-only, and all local findings were derived from
tracked paths and a hidden-file-aware content scan at the recorded base SHA.

## Upstream diff analysis

Commit `8eac7a7de5215c907fbddc30efdaf316913eccdd` changed only five
`rev` values in `.pre-commit-config.yaml`:

| Hook repository | Before | After |
| --- | --- | --- |
| `pre-commit/mirrors-mypy` | `v1.3.0` | `bd424e49d4f0181d4c8b8909a8cd5ce9eb058044` |
| `psf/black` | `22.8.0` | `2018e667a6a36ee3fbfa8041cd36512f92f60d49` |
| `pycqa/isort` | `5.12.0` | `e44834b7b294701f596c9118d6c370f86671a50d` |
| `PyCQA/autoflake` | `v1.6.1` | `b567334b9fc699fc169af0ad1ea0ff0fc017fbeb` |
| `astral-sh/ruff-pre-commit` | `v0.0.277` | `95f113d6340ab4348ecc5d912cf6e6b3465bfb86` |

The hook IDs, arguments, excludes, ordering and repository URLs were
unchanged. The hardening freezes the exact code previously selected by the
human-readable release refs; it is not a tool upgrade.

Commit `dbb1a20192809f5004d0c274374963b1e3cb20bf` changed four
`uses:` occurrences across `.github/workflows/run_tests.yaml` and
`.github/workflows/test_eval.yaml`:

| Action | Before | After | Occurrences |
| --- | --- | --- | ---: |
| `actions/checkout` | `@v2` | `@ee0669bd1cc54295c223e0bb666b733df41de1c5 # v2` | 2 |
| `actions/setup-python` | `@v2` | `@e9aba2c848f5ebd159c070c61ea2c4e2b122355e # v2` | 2 |

Workflow triggers, permissions, inputs, Python version, LFS behavior and test
commands were unchanged. The inline `# v2` comments preserve update context
while execution resolves only the immutable SHA.

## Soll and Soll_app comparison

| Scope in this worktree | Pre-commit config | GitHub Actions workflows | Pinning result |
| --- | ---: | ---: | --- |
| OpenAI Evals upstream after the commits | 1 file, 5 full-SHA refs | 2 files, 4 full-SHA usages | Existing external executable refs are immutable |
| Soll_app repository root | 0 files, 0 refs | 0 files, 0 usages | No existing refs are exposed or require pinning |
| In-repository `Soll/` subtree | 0 files, 0 refs | 0 files, 0 usages | No separate pinning surface is present in this checkout |

Soll_app does have Gradle build inputs and a Gradle wrapper, but those are not
pre-commit hook or GitHub Actions references and were not changed. Adding
pre-commit or GitHub Actions solely to imitate Evals would expand scope and
could introduce new executable third-party dependencies without a defined
local CI requirement.

## Adoption rule for future configuration

If either audited local scope later adds a third-party pre-commit hook or
GitHub Action:

1. resolve the reviewed release to its full 40-character commit SHA;
2. use the SHA as the executable reference and retain the release tag in a
   comment for maintainability;
3. review the upstream diff before updating the SHA;
4. run the affected hook/workflow checks before merging.

This is a future review rule, not an implementation request or a claim that CI
should be added.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Base revision | task base SHA is checked out | PASS |
| Upstream pre-commit diff | 5 release refs become 5 full SHAs only | PASS |
| Upstream workflow diff | 4 action usages become full SHAs only | PASS |
| Soll_app config inventory | 0 pre-commit configs and 0 workflow files | PASS |
| In-repository `Soll/` inventory | 0 pre-commit configs and 0 workflow files | PASS |
| Local external refs | 0 `rev`/`uses` entries require pinning | PASS |
| Scope boundary | no app, build, dependency or runtime files changed | PASS |
| Focused contract test | `OpenAiEvalsPinningAuditTest` | PASS |

## Value metric update

- `source_processing_result`:
  `ci_pinning_audit_completed_no_local_refs`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-2d59297d3e34-cfb13e207e7fa245-verification.md`
- `source_value`: `2` upstream hardening commits, `5` pre-commit hook
  revisions, `4` GitHub Actions usages and `2` in-worktree local configuration
  scopes audited; `0` local hook/action references require pinning; `1/1`
  focused contract test passed; application, build, dependency and runtime
  changes remain `0`.

## Test evidence

- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.soll.project.OpenAiEvalsPinningAuditTest" --console=plain`
- Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed.
