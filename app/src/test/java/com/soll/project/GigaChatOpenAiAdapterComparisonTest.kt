package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GigaChatOpenAiAdapterComparisonTest {
    @Test
    fun `GigaChat remains a normalized server provider instead of an Android drop in`() {
        val comparison = projectFile(
            "docs/knowledge/gigachat-openai-soll-adapter-comparison.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-9ca83e527e5d450dac232b7f16eadc3a-gigachat-openai-adapter-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Task: `9ca83e527e5d450dac232b7f16eadc3a`",
            "`source_ref=insight/d420d61dac77`",
            "is not vendored in this isolated worktree",
            "partial OpenAI compatibility",
            "three OpenAI SDK operation families",
            "`POST /v1/responses`",
            "`POST /v1/chat/completions`",
            "token lasts 30 minutes",
            "`text.format`",
            "`model_options.response_format`",
            "`functions_state_id`",
            "`response.output_text.delta`",
            "`data: [DONE]`",
            "Embedding isolation is mandatory",
            "`ModelChatRequest.safeForServer()`",
            "`SollGateway.askModelChat(...)`",
            "six translation boundaries",
            "all six gates",
            "0 GigaChat inference or embedding quality",
            "https://developers.sber.ru/docs/ru/gigachat/guides/compatible-openai",
            "https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/gigachat-api",
            "https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat",
            "https://developers.sber.ru/docs/ru/gigachat/guides/structured-output",
            "https://developers.sber.ru/docs/ru/gigachat/guides/response-token-streaming",
            "https://developers.sber.ru/docs/ru/gigachat/guides/functions/generating-arguments-for-custom-functions",
            "https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-embeddings",
            "https://developers.openai.com/api/docs/guides/migrate-to-responses",
            "https://developers.openai.com/api/docs/guides/structured-outputs",
            "https://developers.openai.com/api/docs/guides/streaming-responses",
            "https://developers.openai.com/api/docs/guides/embeddings",
            "https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create",
            "https://developers.openai.com/api/reference/resources/embeddings/methods/create",
            "https://developers.openai.com/api/reference/resources/responses/methods/create",
        ).forEach { control ->
            assertTrue("Missing GigaChat/OpenAI comparison control: $control", comparison.contains(control))
        }

        listOf(
            "task_id: 9ca83e527e5d450dac232b7f16eadc3a",
            "source_ref: insight/d420d61dac77",
            "source_processing_result: official_api_comparison_validated_adapter_boundary_ready",
            "14 official documentation/API surfaces reviewed",
            "8 interface areas compared",
            "3 partial OpenAI SDK operation families confirmed",
            "6 provider-specific translation boundaries and 6 live-canary gates defined",
            "1/1 focused contract test passed",
            "0 provider API calls, credentials, Android/runtime changes, or measured GigaChat inference value",
            "GigaChatOpenAiAdapterComparisonTest",
            "Android public contract remains unchanged",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing GigaChat/OpenAI audit evidence: $evidence", audit.contains(evidence))
        }

        val productionModelContract = projectFile(
            "app/src/main/java/com/soll/domain/modelchat/ModelChatModels.kt",
        ).readText()
        assertFalse(
            "Research task must not expose GigaChat as an Android provider before the server adapter exists",
            productionModelContract.contains("GIGACHAT"),
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
