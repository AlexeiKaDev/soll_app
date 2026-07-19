package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAndroidIntegrationPlanTest {
    @Test
    fun `Gemini Android plan separates local and cloud routes behind measured gates`() {
        val plan = projectFile(
            "docs/knowledge/gemini-android-integration-plan-2026-07.md",
        ).readText().normalizeWhitespace()
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-6fa387876bad40e6a170bf164ea8cd08-gemini-android-integration-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "Gemini Nano выполняется на совместимом Android-устройстве",
            "Gemini Flash/Pro выполняется в облаке",
            "ML Kit GenAI разрешает инференс только когда приложение находится на переднем плане",
            "`com.google.mlkit:genai-prompt:1.0.0-beta2`",
            "Private request разрешён только для готового on-device адаптера",
            "`SollGateway.askModelChat(...)`",
            "App Check с Play Integrity",
            "Private payload sent to cloud | `0`",
            "До этого canary измеренное значение Android Gemini Nano inference равно `0`",
        ).forEach { control ->
            assertTrue("Missing Gemini Android control: $control", plan.contains(control))
        }

        listOf(
            "task_id: 6fa387876bad40e6a170bf164ea8cd08",
            "source_processing_result: official_docs_reviewed_integration_plan_ready",
            "7 primary Google documentation surfaces reviewed",
            "2 distinct Android AI routes defined",
            "0 dependencies, permissions, secrets or external model calls added",
            "measured Nano inference runs `0`",
            "The current task is complete as research/planning",
        ).forEach { evidence ->
            assertTrue("Missing Gemini integration evidence: $evidence", audit.contains(evidence))
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
