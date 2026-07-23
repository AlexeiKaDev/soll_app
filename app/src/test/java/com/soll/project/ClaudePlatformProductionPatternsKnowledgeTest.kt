package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudePlatformProductionPatternsKnowledgeTest {
    @Test
    fun `Claude Platform source becomes a safe measurable production patterns card`() {
        val card = projectFile(
            "docs/knowledge/claude-platform-production-patterns.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-69ab93825377-8319dac620e06275-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 7338bb761a114c699c358a2a3081d923",
            "source_ref: source-item/69ab93825377/8319dac620e06275",
            "## KB card",
            "Messages API",
            "Managed Agents",
            "provider-neutral, server-only Messages adapter",
            "`input_schema`",
            "`additionalProperties: false`",
            "`strict: true`",
            "Schema conformance is not authorization",
            "at least 30 non-sensitive cases",
            "USD per successful task",
            "server-side compaction",
            "Context editing is a beta surface",
            "RPM, input tokens per minute (ITPM), and output tokens per minute (OTPM)",
            "`429` response includes `retry-after`",
            "token-bucket behavior",
            "Usage & Cost Admin API",
            "50%, 75%, and 90%",
            "0 unauthorized executions",
            "0 duplicate side effects",
            "0 provider keys or SDKs in Android",
            "autonomous shell or computer use",
            "isolated sandbox",
            "default-deny allowlist",
            "append-only audit",
            "https://platform.claude.com/docs/en/home",
            "https://platform.claude.com/docs/en/agents-and-tools/tool-use/strict-tool-use",
            "https://platform.claude.com/docs/en/test-and-evaluate/develop-tests",
            "https://platform.claude.com/docs/en/build-with-claude/context-editing",
            "https://platform.claude.com/docs/en/api/rate-limits",
            "https://platform.claude.com/docs/en/manage-claude/usage-cost-api",
        ).forEach { control ->
            assertTrue("Missing Claude Platform production control: $control", card.contains(control))
        }

        listOf(
            "task_id: 7338bb761a114c699c358a2a3081d923",
            "project: soll_app",
            "source_ref: source-item/69ab93825377/8319dac620e06275",
            "source_processing_result: claude_platform_kb_and_production_deep_dive_completed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-69ab93825377-8319dac620e06275-verification.md",
            "1 KB card",
            "5 production patterns",
            "6 promotion areas",
            "0 provider API calls",
            "ClaudePlatformProductionPatternsKnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing Claude Platform audit evidence: $evidence", verification.contains(evidence))
        }

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
            projectFile("gradle/libs.versions.toml"),
        ).joinToString("\n") { it.readText() }
        listOf("com.anthropic", "anthropic-sdk", "claude-agent-sdk").forEach { dependency ->
            assertFalse(
                "Knowledge-only task must not add provider dependency $dependency",
                gradleInputs.contains(dependency, ignoreCase = true),
            )
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
