package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LilLogAgentArchitectureSourceTriageTest {
    @Test
    fun `LilLog agent blocks receive a bounded server first integration assessment`() {
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-5d8b23e3c9e6-19472db6d6f483d5-verification.md",
        ).readText()

        listOf(
            "source_ref: source-item/5d8b23e3c9e6/19472db6d6f483d5",
            "source_processing_result: architecture_audit_completed_server_first_agent_integration_feasible",
            "6 architecture blocks mapped",
            "3 requested integrations assessed",
            "0 runtime or external side effects",
            "raw/monitored\\lillog\\20260702-195135-building-agents-with-llm-large-language-model-as-297f7b2c.md",
            "is absent from this isolated worktree",
            "## Six-block architecture mapping",
            "### 1. Controller and orchestration",
            "### 2. Planning and reflection",
            "### 3. Short-term and long-term memory",
            "### 4. Tools and external APIs",
            "### 5. Scheduling and execution lifecycle",
            "### 6. Observation, safety and evaluation",
            "Current state: answer-only",
            "MetaCoordinatorRequest.safeForServer()",
            "ToolJobRunner",
            "Android must never receive API",
            "credentials or execute an arbitrary URL",
            "Never use a periodic Android",
            "worker to wake an autonomous reasoning loop",
            "## Feasibility assessment for the requested integrations",
            "High through typed server adapters; rejected as arbitrary Android calls",
            "## Proposed additive contract",
            "## Smallest safe delivery sequence",
            "## Promotion and rejection gates",
            "unknown plan schema, capability and tool IDs are blocked in 100% of cases",
            "external/device effects without the required approval remain exactly `0`",
            "LilLogAgentArchitectureSourceTriageTest",
        ).forEach { evidence ->
            assertTrue("Missing LilLog architecture evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path from ${System.getProperty("user.dir")}")
    }
}
