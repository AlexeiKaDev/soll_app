package com.soll.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SollAgentPrototypeTest {
    @Test
    fun `run context keeps the three capabilities explicit`() {
        val context = assembler().assemble(
            SollAgentRunRequest(objective = "Проверить проект")
        )

        assertEquals(
            setOf(
                AgentRuntimeCapability.SHELL,
                AgentRuntimeCapability.SKILLS,
                AgentRuntimeCapability.COMPACTION,
            ),
            context.capabilities,
        )
        assertNull(context.checkpoint)
    }

    @Test
    fun `shell exposes only selected allowlisted tool ids`() {
        val context = assembler().assemble(
            SollAgentRunRequest(
                objective = "Проверить проект",
                requestedShellToolIds = setOf("repo_status"),
            )
        )

        assertEquals(listOf("repo_status"), context.shellTools.map { it.id })
        assertTrue(context.shellTools.single().requiresConfirmation)

        expectIllegalArgument("unknown_tool") {
            assembler().assemble(
                SollAgentRunRequest(
                    objective = "Проверить проект",
                    requestedShellToolIds = setOf("unknown_tool"),
                )
            )
        }
    }

    @Test
    fun `skill index is complete while instructions load progressively`() {
        val context = assembler().assemble(
            SollAgentRunRequest(
                objective = "Проверить проект",
                requestedSkillIds = setOf("focused-verification"),
            )
        )

        assertEquals(
            listOf("repo-navigation", "focused-verification"),
            context.skillIndex.map { it.id },
        )
        assertEquals(listOf("focused-verification"), context.loadedSkills.map { it.id })
        assertTrue(context.loadedSkills.single().instructions.contains("focused test"))
    }

    @Test
    fun `compaction preserves objective state evidence and recent tail`() {
        val events = listOf(
            AgentContextEvent(AgentContextEventType.OBSERVATION, "Clean base"),
            AgentContextEvent(
                AgentContextEventType.COMPLETED,
                "Mapped architecture",
                evidenceRefs = listOf("artifact:architecture.md"),
            ),
            AgentContextEvent(AgentContextEventType.PENDING, "Run focused tests"),
            AgentContextEvent(
                AgentContextEventType.COMPLETED,
                "Implemented context assembler",
                evidenceRefs = listOf("artifact:implementation.kt", "artifact:architecture.md"),
            ),
            AgentContextEvent(AgentContextEventType.OBSERVATION, "Build started"),
            AgentContextEvent(AgentContextEventType.PENDING, "Attach audit"),
        )

        val context = assembler().assemble(
            SollAgentRunRequest(
                objective = "Применить Shell + Skills + Compaction",
                events = events,
            )
        )

        val checkpoint = requireNotNull(context.checkpoint)
        assertEquals("Применить Shell + Skills + Compaction", checkpoint.objective)
        assertEquals(4, checkpoint.compactedEventCount)
        assertEquals(
            listOf("Mapped architecture", "Implemented context assembler"),
            checkpoint.completedSteps,
        )
        assertEquals(listOf("Run focused tests"), checkpoint.pendingSteps)
        assertEquals(
            listOf("artifact:architecture.md", "artifact:implementation.kt"),
            checkpoint.evidenceRefs,
        )
        assertEquals(events.takeLast(2), context.recentEvents)
    }

    private fun assembler(): SollAgentContextAssembler =
        SollAgentContextAssembler(
            SollAgentPrototypeConfig(
                capabilities = linkedSetOf(
                    AgentRuntimeCapability.SHELL,
                    AgentRuntimeCapability.SKILLS,
                    AgentRuntimeCapability.COMPACTION,
                ),
                shellTools = listOf(
                    AgentShellTool(
                        id = "repo_status",
                        summary = "Read repository status in the server sandbox",
                    ),
                    AgentShellTool(
                        id = "focused_test",
                        summary = "Run an approved focused test target",
                    ),
                ),
                skills = listOf(
                    AgentSkill(
                        id = "repo-navigation",
                        summary = "Find the smallest relevant code surface",
                        instructions = "Inspect the repository before changing files.",
                    ),
                    AgentSkill(
                        id = "focused-verification",
                        summary = "Verify only the changed behavior",
                        instructions = "Run the smallest focused test that proves the change.",
                    ),
                ),
                compactionPolicy = AgentCompactionPolicy(
                    maxEventCount = 4,
                    retainedRecentEventCount = 2,
                ),
            )
        )

    private fun expectIllegalArgument(messagePart: String, block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains(messagePart))
        }
    }
}
