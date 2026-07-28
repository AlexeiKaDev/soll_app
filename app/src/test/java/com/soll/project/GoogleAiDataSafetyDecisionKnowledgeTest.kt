package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiDataSafetyDecisionKnowledgeTest {
    @Test
    fun `Google AI decision separates local and cloud data with explicit safety controls`() {
        val note = projectFile(
            "docs/knowledge/google-ai-data-boundary-and-safety-decision.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-db74e38e5609-d6c4aab5eaf02a1b-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: 053acb56309a4990a1a25d3a4db19d55",
            "source_ref: source-item/db74e38e5609/d6c4aab5eaf02a1b",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "official_sources_only: true",
            "Google AI Edge | на устройстве",
            "Gemini Nano on Android | на совместимом Android-устройстве",
            "Gemini API | в облаке Google",
            "system-managed Android path",
            "`private=true` сообщения",
            "локальную заметку, черновик или короткий текст",
            "выбранное пользователем изображение",
            "выбранный короткий audio fragment",
            "`On-device` не означает полное отсутствие служебного network traffic",
            "Private Compute Services",
            "не должен получать Soll prompt или output",
            "Contacts, полный SMS/call history, location history",
            "Credentials, API keys, pairing tokens",
            "большие документы или context",
            "актуальная внешняя информация и Google Search/Maps grounding",
            "cloud-only tools, function calling, code execution",
            "`ModelChatRequest.safeForServer()` удаляет `private=true` turns",
            "`SollGateway.askModelChat(...)`",
            "до 55 дней для abuse monitoring",
            "`HARM_CATEGORY_HARASSMENT`",
            "`HARM_CATEGORY_HATE_SPEECH`",
            "`HARM_CATEGORY_SEXUALLY_EXPLICIT`",
            "`HARM_CATEGORY_DANGEROUS_CONTENT`",
            "`BLOCK_MEDIUM_AND_ABOVE`",
            "`OFF` по умолчанию для Gemini 2.5 и 3",
            "`promptFeedback.blockReason`",
            "`Candidate.finishReason=SAFETY`",
            "`private_cloud_attempt_count` с invariant `== 0`",
            "`safety_signal_handled_rate == 1.0`",
            "`raw_prompt_or_output_log_count == 0`",
            "https://developers.google.com/edge",
            "https://developer.android.com/ai/overview",
            "https://developer.android.com/ai/gemini-nano",
            "https://developers.google.com/ml-kit/genai",
            "https://developers.google.com/ml-kit/genai/prompt/android/get-started",
            "https://developers.google.com/ml-kit/genai/prompt/android/evaluate-prompt",
            "https://developers.google.com/ml-kit/terms",
            "https://ai.google.dev/gemini-api/docs/safety-settings",
            "https://ai.google.dev/gemini-api/docs/safety-guidance",
            "https://ai.google.dev/gemini-api/docs/usage-policies",
            "https://ai.google.dev/gemini-api/docs/logs-policy",
            "https://ai.google.dev/gemini-api/docs/zdr",
        ).forEach { control ->
            assertTrue("Missing Google AI data/safety control: $control", note.contains(control))
        }

        listOf(
            "task_id: 053acb56309a4990a1a25d3a4db19d55",
            "project: soll_app",
            "source_ref: source-item/db74e38e5609/d6c4aab5eaf02a1b",
            "source_processing_result: " +
                "official_google_ai_data_boundary_and_safety_decision_completed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-db74e38e5609-d6c4aab5eaf02a1b-verification.md",
            "12 official Google documentation surfaces reviewed",
            "3 Google AI execution contours",
            "11 safety controls and 8 privacy-safe monitoring signals documented",
            "`documentation_complete_runtime_integration_not_authorized`",
            "GoogleAiDataSafetyDecisionKnowledgeTest",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing Google AI data/safety evidence: $evidence", verification.contains(evidence))
        }

        val models = projectFile(
            "app/src/main/java/com/soll/domain/modelchat/ModelChatModels.kt",
        ).readText()
        val build = projectFile("app/build.gradle.kts").readText()
        val versions = projectFile("gradle/libs.versions.toml").readText()

        assertTrue(
            "The documented server gate must keep filtering private messages",
            models.contains(".filterNot { it.private }"),
        )
        assertTrue(
            "The documented server route must continue to sanitize requests",
            models.contains("val safeRequest = request.safeForServer()"),
        )
        listOf(
            "com.google.mlkit:genai",
            "com.google.firebase:firebase-ai",
            "com.google.ai.edge",
        ).forEach { dependency ->
            assertFalse(
                "Decision-note task must not add runtime dependency $dependency",
                build.contains(dependency, ignoreCase = true) ||
                    versions.contains(dependency, ignoreCase = true),
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
