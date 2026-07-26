---
task_id: 2038f7e420124883a1c6a8dfd1553985
project: soll_app
source_ref: source-item/4246339b7f08/8374e97d5f4aee48
source_processing_result: provider_candidate_note_added_preintegration_reviews_required
verification_artifact: Soll/outputs/source-processing/source-item-4246339b7f08-8374e97d5f4aee48-verification.md
source_value: "1 Soll KB note added; 3 provider surfaces shortlisted; 3 mandatory review areas with 12 controls recorded; 1/1 focused contract test passed; 0 credentials, provider API calls, user-data transfers, SDK/runtime changes, or production integrations"
verified_at: 2026-07-26 Europe/Chisinau
---

# Yandex AI Studio provider-candidate audit

## Outcome

Добавлена короткая Soll KB-заметка
`docs/knowledge/yandex-ai-studio-provider-candidate.md`. Она сохраняет Yandex AI
Studio / Model Gallery, Yandex Vision OCR и AI Search / Yandex Search API как
три поверхности-кандидата, но запрещает интеграцию до отдельного разбора SLA,
персональных данных и хранения/передачи пользовательских данных.

Заметка не добавляет provider config, SDK, credential, API call или Android /
runtime behavior.

## Evidence and trust boundary

- Task source: `source-item/4246339b7f08/8374e97d5f4aee48`.
- Official page: <https://yandex.cloud/ru/docs/ai-studio/>, read-only checked
  2026-07-26; it redirected to
  <https://aistudio.yandex.ru/docs/ru/>.
- The current official page lists Model Gallery, AI Search, Search API and
  Vision OCR. It also links to service-specific service levels and terms and
  states that Yandex Cloud infrastructure is protected in accordance with
  152-ФЗ.

The task-plan capture
`raw/monitored\yandex-ai-studio-docs\20260709-205552-sla-4f82731c.md` is absent
from this isolated worktree. The task payload was treated only as untrusted
discovery metadata. Current official documentation was used to confirm the
shortlist signal, but no linked SLA or legal document was interpreted as part
of this bounded note.

The 152-ФЗ statement is not treated as proof that a concrete Soll data flow is
compliant. The note explicitly requires a later service-by-service legal,
privacy, retention and transfer review.

## Focused smoke/audit checks

| Check | Expected | Result |
| --- | --- | --- |
| Knowledge attachment | durable short note under `docs/knowledge` | PASS |
| Candidate scope | LLM, OCR and search are separated | PASS |
| Integration status | candidate only; no provider approval implied | PASS |
| SLA boundary | applicable version, availability, exclusions and remedies require review | PASS |
| Personal-data boundary | roles, minimization, subprocessors and localization require review | PASS |
| Storage/transfer boundary | logging, retention, regions/encryption and support access require review | PASS |
| Compliance wording | 152-ФЗ infrastructure statement is not promoted to Soll compliance | PASS |
| Measurable value | 3 surfaces, 3 review areas and 12 controls recorded | PASS |
| Focused contract test | `YandexAiStudioProviderCandidateKnowledgeTest` | PASS |
| Safe scope | 0 credentials, calls, transfers, SDK/runtime changes and integrations | PASS |

## Value metric update

- `source_processing_result`:
  `provider_candidate_note_added_preintegration_reviews_required`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-4246339b7f08-8374e97d5f4aee48-verification.md`
- `source_value`: `1` Soll KB note; `3` provider surfaces; `3` mandatory review
  areas with `12` controls; `1/1` focused contract test. Credentials, provider
  API calls, user-data transfers, SDK/runtime changes and production
  integrations remain `0`.

## Test evidence

- Command: `.\gradlew.bat :app:testDebugUnitTest --tests
  "com.soll.project.YandexAiStudioProviderCandidateKnowledgeTest"
  --console=plain`
- Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed.
