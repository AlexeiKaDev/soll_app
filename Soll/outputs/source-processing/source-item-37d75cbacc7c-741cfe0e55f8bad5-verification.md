---
task_id: e4bc6efce56249899f1d74a8ebae5788
project: soll_app
source_ref: source-item/37d75cbacc7c/741cfe0e55f8bad5
source_item: ai-model-releases-benchmarks
source_processing_result: recommendation_prepared_server_eval_required
verification_artifact: Soll/outputs/source-processing/source-item-37d75cbacc7c-741cfe0e55f8bad5-verification.md
value_metric: "7 model families compared against 7 current Soll seams; 5 routing profiles, 6 workload groups, 17 metrics and 6 promotion gates defined; 1/1 focused contract test passed; 0 provider API calls, credentials, Android/provider dependencies, production contract changes, or measured external model quality"
verified_at: 2026-07-23 Europe/Chisinau
---

# AI model release and Soll integration audit

## Outcome

The requested integration recommendation is complete in
`docs/knowledge/ai-model-integration-recommendation-2026-07.md`.

Soll should not add direct GPT, Claude, Gemini, Llama, Mistral, DeepSeek, Qwen,
or MCP integrations to Android. Model choice, exact model IDs, lifecycle,
credentials, tool/MCP policy, context and cost belong in a provider-neutral
server router. Android public contract remains unchanged on the existing
`ModelChatRequest.safeForServer()` -> `SollGateway.askModelChat(...)` path.

The monitored raw artifact is not vendored in this isolated worktree, so its
summary was treated only as untrusted discovery metadata. Current official
sources show material lifecycle drift: GPT-5 is now described as a previous
OpenAI model, Claude Sonnet 4 is retired from Anthropic's first-party API, and
Gemini 2.5 Pro remains a stable Google model. No source-roundup leaderboard was
therefore accepted as a production routing decision.

## Focused audit

| Check | Observed result |
| --- | --- |
| Model scope | 7 model families compared: GPT, Claude, Gemini, Llama, Mistral, DeepSeek and Qwen |
| Parameter/context boundary | vendor-undisclosed counts are not guessed; dense/MoE total and active counts remain distinct; native and extended context remain distinct |
| Lifecycle boundary | exact pinned server IDs required; source-era aliases are not compiled into Android |
| Soll architecture | 7 current seams audited; Android already sanitizes and forwards model chat through the backend |
| Routing recommendation | 5 workload profiles: fast, balanced, deep, vision and local_private |
| Evaluation contract | 6 workload groups, 17 metrics and 6 promotion gates defined |
| MCP | server-side allowlisted tool transport, not a benchmark or Android execution path |
| Runtime changes | 0 provider calls, 0 credentials, 0 new dependencies, 0 public-contract changes |

## Recommendation

Create a separately approved server-only evaluation task. Freeze the current
backend route as baseline, resolve then-current stable model IDs, and compare
one balanced cloud candidate, one low-cost cloud candidate and one local/private
open-weight candidate on the same synthetic or non-sensitive Soll fixtures.
Promote only a pinned profile that passes all safety/schema gates and improves
quality or USD per successful task by the declared threshold. Do not perform an
Android-native Llama/Mistral/Qwen trial and do not expose provider credentials
or arbitrary MCP tools to the app.

## Focused smoke/audit artifact

`AiModelIntegrationRecommendationTest` guards:

- exact task/source trace and the missing-raw source boundary;
- all seven model families and current lifecycle caveats;
- the server-only capability catalog and five routing profiles;
- six Soll-shaped workload groups, seventeen recorded metrics and six promotion
  gates, including zero unsafe side effects;
- unchanged `AUTO`/`LLAMA` Android model contract and absence of provider SDKs;
- the quantified value metric and zero external model execution claims.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.soll.project.AiModelIntegrationRecommendationTest" --console=plain
```

Observed result: `BUILD SUCCESSFUL`; `1/1` focused contract test passed with
`0` failures, `0` errors and `0` skipped tests.

## Value metric update

- `source_processing_result`:
  `recommendation_prepared_server_eval_required`
- `verification_artifact`:
  `Soll/outputs/source-processing/source-item-37d75cbacc7c-741cfe0e55f8bad5-verification.md`
- `value_metric`: `7` model families compared against `7` current Soll seams;
  `5` routing profiles, `6` workload groups, `17` metrics and `6` promotion
  gates defined; `1/1` focused contract test passed; `0` provider API calls,
  credentials, Android/provider dependencies, production contract changes, or
  measured external model quality.
