package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaModelApiCodingAgentProviderConfigKnowledgeTest {
    @Test
    fun `Meta Model API config remains reviewed credential free documentation`() {
        val knowledge = projectFile(
            "docs/knowledge/meta-model-api-coding-agent-provider-config.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-08c98d51dea1-b05c13c0e5686fcc-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: bc5e9fc1a57a4f4292a81f3deef06f51",
            "source_ref: source-item/08c98d51dea1/b05c13c0e5686fcc",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "https://api.meta.ai/v1",
            "https://api.meta.ai",
            "`MODEL_API_KEY`",
            "`ANTHROPIC_AUTH_TOKEN=\"${'$'}MODEL_API_KEY\"`",
            "`muse-spark-1.1`",
            "context `1048576`",
            "maximum output `131072`",
            "`/v1/responses`",
            "`/v1/chat/completions`",
            "`/v1/messages`",
            "`@ai-sdk/openai`",
            "\"baseURL\": \"https://api.meta.ai/v1\"",
            "\"include\": [\"reasoning.encrypted_content\"]",
            "`@ai-sdk/openai-compatible`",
            "Добавлять config нужно вручную после review",
            "file-write, shell, MCP и external-integration tools отключены deny-by-default",
            "если агент не умеет технически запретить эти tools, запуск отменяется",
            "`0` provider API calls",
            "https://ai.developer.meta.com/docs/guides/coding-agents/",
            "https://ai.developer.meta.com/docs/getting-started/overview/",
            "https://ai.meta.com/blog/introducing-muse-spark-meta-model-api/",
        ).forEach { control ->
            assertTrue("Missing Meta Model API config control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: bc5e9fc1a57a4f4292a81f3deef06f51",
            "source_ref: source-item/08c98d51dea1/b05c13c0e5686fcc",
            "source_processing_result: kb_note_added_official_provider_config_verified",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-08c98d51dea1-b05c13c0e5686fcc-verification.md",
            "1 Soll KB note added",
            "3 API surfaces separated",
            "2 exact token limits recorded",
            "1 manual OpenCode config and 6 sandbox/credential guards documented",
            "1/1 focused contract test passed",
            "0 credentials, provider API calls, autonomous write tools",
            "MetaModelApiCodingAgentProviderConfigKnowledgeTest",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing Meta Model API verification evidence: $evidence", verification.contains(evidence))
        }

        val productionModelContract = projectFile(
            "app/src/main/java/com/soll/domain/modelchat/ModelChatModels.kt",
        ).readText()
        assertFalse(
            "Knowledge-only task must not expose Muse Spark as an Android provider",
            productionModelContract.contains("MUSE_SPARK"),
        )
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
