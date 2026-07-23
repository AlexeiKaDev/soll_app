package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaModelApiSafeJsonAnalyzerKnowledgeTest {
    @Test
    fun `Llama API deep dive preserves a review only safe JSON contract`() {
        val knowledge = projectFile(
            "docs/knowledge/llama-model-api-safe-json-finding-analyzer.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-08c98d51dea1-819ca889dbfcf345-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 0c16238031584c2d9c6847e6cfa121e1",
            "source_ref: source-item/08c98d51dea1/819ca889dbfcf345",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "`POST /v1/responses`",
            "`POST /v1/chat/completions`",
            "`text.format`",
            "`response_format`",
            "`choices[0].message.content`",
            "`output_text` content block",
            "\"type\": \"json_schema\"",
            "\"name\": \"soll_source_findings_v1\"",
            "\"strict\": true",
            "максимум 64 символа",
            "10 уровней",
            "5,000 суммарно",
            "120,000 символов суммарно",
            "1,000 по умолчанию",
            "200,000 nodes",
            "Recursive `${'$'}ref` cycles",
            "каждый object задаёт `additionalProperties: false`",
            "`required` перечисляет каждое поле",
            "`^[a-zA-Z0-9_.-]+$`",
            "`tool_choice` поддерживает только `\"auto\"`",
            "`parallel_tool_calls` по умолчанию `true`",
            "`max_tool_calls` с минимумом `1`",
            "каждый `call_id` имеет длину 1–64 символа",
            "`function_call_output` с тем же `call_id`",
            "всегда передавать `strict: true`",
            "всегда выполнять локальную валидацию",
            "\"type\": \"web_search\"",
            "\"search_context_size\": \"low\"",
            "\"allowed_domains\": [\"example.com\"]",
            "\"include\": [\"web_search_call.results\"]",
            "\"max_tool_calls\": 1",
            "`type: \"web_search_call\"`",
            "`type`, `title`, `url`, `snippet`",
            "\"type\": \"url_citation\"",
            "`start_index` / `end_index`",
            "не гарантирует поиск",
            "`browser.search`, `browser.open`, `browser.find`",
            "`usage.input_tokens_details.cached_tokens`",
            "`usage.prompt_tokens_details.cached_tokens`",
            "`prompt_cache_key`",
            "`prompt_cache_retention: \"in_memory\"`",
            "`prompt_cache_retention: \"24h\"`",
            "`store: false` отключает response retrieval, но не prompt caching",
            "`summary`, `usefulness`, `reasoning`, `evidenceLevel`, `projectFit`",
            "`safeNextStep`",
            "`verificationArtifact`",
            "### Двенадцать deterministic gates",
            "`schema_valid_rate == 1.0`",
            "`citation_integrity_rate == 1.0`",
            "`unsafe_tool_execution_count == 0`",
            "`automatic_task_or_wiki_write_count == 0`",
            "`0` provider API calls",
            "https://llama.developer.meta.com/docs/features/structured-output",
            "https://llama.developer.meta.com/docs/features/tool-calling/",
            "https://llama.developer.meta.com/docs/features/search-grounding",
            "https://llama.developer.meta.com/docs/features/prompt-caching",
            "https://ai.developer.meta.com/docs/api-reference/responses/schemas",
        ).forEach { control ->
            assertTrue("Missing Llama safe analyzer control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: 0c16238031584c2d9c6847e6cfa121e1",
            "source_ref: source-item/08c98d51dea1/819ca889dbfcf345",
            "source_processing_result: official_llama_api_deep_dive_safe_json_analyzer_blueprint",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-08c98d51dea1-819ca889dbfcf345-verification.md",
            "1 Soll KB note added",
            "4 official Llama feature pages and 2 Responses API reference pages audited",
            "2 endpoint mappings, 6 schema-size limits, 4 strict-subset rules",
            "12 deterministic analyzer gates documented",
            "1/1 focused contract test passed",
            "0 provider calls, credentials, tool executions, task/wiki writes",
            "LlamaModelApiSafeJsonAnalyzerKnowledgeTest",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing Llama verification evidence: $evidence", verification.contains(evidence))
        }

        val productionApi = projectFile(
            "app/src/main/java/com/soll/data/api/SollApiService.kt",
        ).readText()
        val productionModels = projectFile(
            "app/src/main/java/com/soll/domain/soll/SollGateway.kt",
        ).readText()
        assertFalse(
            "Knowledge-only task must not add Meta Model API endpoints",
            productionApi.contains("api.meta.ai"),
        )
        assertFalse(
            "Knowledge-only task must not add a Meta provider to Android models",
            productionModels.contains("MUSE_SPARK"),
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
