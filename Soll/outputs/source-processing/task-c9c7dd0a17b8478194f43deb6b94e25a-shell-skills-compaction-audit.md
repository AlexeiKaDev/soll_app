# Soll agent Shell + Skills + Compaction smoke/audit

- task_id: `c9c7dd0a17b8478194f43deb6b94e25a`
- source_ref: `insight/af83aa26b648`
- source_processing_result: `prototype_applied_and_focused_smoke_passed`
- value_metric: `3/3 named patterns implemented; 4/4 focused tests passed; 0 raw shell command fields; 0 runtime or external side effects`

## Source and implementation boundary

The monitored source path from the task record is absent from this isolated worktree. The implementation was checked against the current official OpenAI Sandbox Agents capability contract:

- <https://developers.openai.com/api/docs/guides/agents/sandboxes#give-the-agent-capabilities>

That contract describes Shell, Skills and Compaction as explicit agent capabilities, recommends selective skill materialization, and uses compaction for context trimming in long-running flows. The Soll prototype applies those mechanics without importing an agent SDK, changing a public API or allowing Android to execute arbitrary commands.

## Applied pattern mapping

### Shell

- `SollAgentPrototypeConfig.capabilities` declares `SHELL` explicitly instead of relying on implicit defaults.
- A run requests registry IDs and receives only matching `AgentShellTool` records.
- Unknown IDs fail closed.
- The mobile model contains no executable, raw command, argument or credential field; `requiresConfirmation` is explicit on every shell tool descriptor.

### Skills

- The run context first exposes `AgentSkillSummary` entries for the complete configured index.
- Full `AgentSkill.instructions` are included only for requested, known skill IDs.
- Unknown skill IDs fail closed, so unregistered instructions cannot silently enter the run context.

### Compaction

- `AgentCompactionPolicy` defines an event budget and a recent-tail budget.
- Once the event budget is exceeded, older events become an inspectable checkpoint containing the original objective, compacted-event count, completed steps, pending steps and deduplicated evidence references.
- The most recent events stay verbatim, preserving the immediate execution state for continuation.

## Safety audit

- Android-side shell execution added: `0`
- Raw executable/command fields added: `0`
- External integration or provider credential paths added: `0`
- Database, network, UI or public API contracts changed: `0`
- Autonomous background loops added: `0`

The new classes are transport-neutral domain models. A future server worker may map its approved sandbox tools and skills into this context, while Android remains the approval and observability client.

## Focused smoke

Command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.soll.domain.agent.SollAgentPrototypeTest
```

Observed on 2026-07-19 Europe/Chisinau:

- Gradle result: `BUILD SUCCESSFUL`
- Test suite: `com.soll.domain.agent.SollAgentPrototypeTest`
- Tests: `4`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

The smoke proves:

1. The prototype run context preserves the explicit Shell + Skills + Compaction capability set.
2. Shell exposure returns only selected allowlisted tool IDs and rejects an unknown ID.
3. The full skill index remains discoverable while only the requested skill instructions are loaded.
4. Compaction preserves objective, completed/pending state, deduplicated evidence and the exact recent event tail.

## Files

- `app/src/main/java/com/soll/domain/agent/SollAgentPrototype.kt`
- `app/src/test/java/com/soll/domain/agent/SollAgentPrototypeTest.kt`
- `docs/soll_app-superassistant-roadmap-2026-05-06.md`
- `soll_status.md`
- `Soll/outputs/source-processing/task-c9c7dd0a17b8478194f43deb6b94e25a-shell-skills-compaction-audit.md`
