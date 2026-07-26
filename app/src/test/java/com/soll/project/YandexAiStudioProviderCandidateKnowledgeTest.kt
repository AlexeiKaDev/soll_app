package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexAiStudioProviderCandidateKnowledgeTest {
    @Test
    fun `Yandex AI Studio remains a reviewed candidate without runtime integration`() {
        val knowledge = projectFile(
            "docs/knowledge/yandex-ai-studio-provider-candidate.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-4246339b7f08-8374e97d5f4aee48-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 2038f7e420124883a1c6a8dfd1553985",
            "source_ref: source-item/4246339b7f08/8374e97d5f4aee48",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "status: candidate_only_no_integration",
            "AI Studio / Model Gallery",
            "Yandex Vision OCR",
            "AI Search и Yandex Search API",
            "Само наличие этих заявлений и ссылок не доказывает соответствие",
            "условия одного сервиса не переносятся на другой автоматически",
            "### 1. SLA и условия сервиса",
            "### 2. Обработка персональных данных",
            "### 3. Хранение и передача пользовательских данных",
            "применимость 152-ФЗ, локализацию и трансграничные потоки",
            "использование данных для улучшения или обучения",
            "сроки хранения, удаление, резервные копии",
            "До завершения всех трёх review запрещены provider credentials, API-вызовы",
            "синтетических неперсональных данных",
            "Фактические интеграции, provider API calls и передачи данных",
            "https://yandex.cloud/ru/docs/ai-studio/",
            "https://aistudio.yandex.ru/docs/ru/",
        ).forEach { control ->
            assertTrue("Missing Yandex provider-candidate control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: 2038f7e420124883a1c6a8dfd1553985",
            "source_ref: source-item/4246339b7f08/8374e97d5f4aee48",
            "source_processing_result: provider_candidate_note_added_preintegration_reviews_required",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-4246339b7f08-8374e97d5f4aee48-verification.md",
            "1 Soll KB note added",
            "3 provider surfaces shortlisted",
            "3 mandatory review areas with 12 controls recorded",
            "1/1 focused contract test passed",
            "0 credentials, provider API calls, user-data transfers",
            "YandexAiStudioProviderCandidateKnowledgeTest",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing Yandex provider-candidate evidence: $evidence", verification.contains(evidence))
        }

        val runtimeInputs = listOf(
            projectFile("app/src/main/java/com/soll/domain/modelchat/ModelChatModels.kt"),
            projectFile("app/build.gradle.kts"),
        ).joinToString("\n") { it.readText() }
        listOf("YANDEX_AI_STUDIO", "aistudio.yandex.ru").forEach { runtimeToken ->
            assertFalse(
                "Knowledge-only task must not add Yandex AI Studio runtime token $runtimeToken",
                runtimeInputs.contains(runtimeToken, ignoreCase = true),
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
