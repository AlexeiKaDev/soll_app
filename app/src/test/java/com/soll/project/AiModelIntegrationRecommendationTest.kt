package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelIntegrationRecommendationTest {
    @Test
    fun `model roundup produces a server side evaluation recommendation`() {
        val recommendation = projectFile(
            "docs/knowledge/ai-model-integration-recommendation-2026-07.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-37d75cbacc7c-741cfe0e55f8bad5-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Task: `e4bc6efce56249899f1d74a8ebae5788`",
            "`source_ref=source-item/37d75cbacc7c/741cfe0e55f8bad5`",
            "is not vendored in this isolated worktree",
            "OpenAI describes GPT-5 as a previous model",
            "retired Claude Sonnet 4",
            "`gemini-2.5-pro` as a stable model",
            "Meta Llama 4 Scout",
            "Mistral Small 4",
            "DeepSeek V4 Flash",
            "Qwen3-30B-A3B",
            "Parameter counts marked `undisclosed`",
            "USD per successful task",
            "`unsafe_side_effect_count = 0`",
            "Server-owned capability catalog",
            "`fast`",
            "`balanced`",
            "`deep`",
            "`vision`",
            "`local_private`",
            "MCP is a tool interoperability layer, not a model benchmark",
            "`ModelChatRequest.safeForServer()`",
            "`SollGateway.askModelChat(...)`",
            "`POST /api/v1/chat/turn`",
            "7 model-family representatives",
            "6 workload groups",
            "17 recorded metrics",
            "6 promotion gates",
            "0 external model quality",
            "https://developers.openai.com/api/docs/models/gpt-5",
            "https://platform.claude.com/docs/en/about-claude/models/overview",
            "https://ai.google.dev/gemini-api/docs/models/gemini-2.5-pro",
            "https://huggingface.co/meta-llama/Llama-4-Scout-17B-16E-Instruct",
            "https://docs.mistral.ai/models/model-cards/mistral-small-4-0-26-03",
            "https://api-docs.deepseek.com/quick_start/pricing/",
            "https://huggingface.co/Qwen/Qwen3-30B-A3B",
        ).forEach { control ->
            assertTrue("Missing model-integration control: $control", recommendation.contains(control))
        }

        listOf(
            "task_id: e4bc6efce56249899f1d74a8ebae5788",
            "project: soll_app",
            "source_ref: source-item/37d75cbacc7c/741cfe0e55f8bad5",
            "source_processing_result: recommendation_prepared_server_eval_required",
            "verification_artifact: Soll/outputs/source-processing/source-item-37d75cbacc7c-741cfe0e55f8bad5-verification.md",
            "7 model families compared",
            "5 routing profiles, 6 workload groups, 17 metrics and 6 promotion gates defined",
            "1/1 focused contract test passed",
            "0 provider API calls, credentials, Android/provider dependencies, production contract changes, or measured external model quality",
            "AiModelIntegrationRecommendationTest",
            "Android public contract remains unchanged",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing model-integration audit evidence: $evidence", audit.contains(evidence))
        }

        val modelContract = projectFile(
            "app/src/main/java/com/soll/domain/modelchat/ModelChatModels.kt",
        ).readText()
        assertTrue(modelContract.contains("AUTO"))
        assertTrue(modelContract.contains("LLAMA"))
        listOf("OPENAI", "ANTHROPIC", "GEMINI", "MISTRAL", "DEEPSEEK", "QWEN").forEach { vendor ->
            assertFalse("Research must not add Android provider enum $vendor", modelContract.contains(vendor))
        }

        val gradleInputs = listOf(
            projectFile("build.gradle.kts"),
            projectFile("settings.gradle.kts"),
            projectFile("app/build.gradle.kts"),
            projectFile("gradle/libs.versions.toml"),
        ).joinToString("\n") { it.readText() }
        listOf("com.openai", "anthropic", "generativeai", "mistral", "deepseek", "qwen").forEach { dependency ->
            assertFalse(
                "Research must not add provider dependency $dependency",
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
