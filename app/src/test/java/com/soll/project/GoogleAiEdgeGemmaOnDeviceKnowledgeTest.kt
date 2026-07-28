package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiEdgeGemmaOnDeviceKnowledgeTest {
    @Test
    fun `Google AI Edge signal becomes an offline only Gemma pilot decision`() {
        val knowledge = projectFile(
            "docs/knowledge/google-ai-edge-gemma-on-device-deep-dive.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-db74e38e5609-cb1fea99535b326e-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: eb2786f702454d47b8fe2376593523ef",
            "source_ref: source-item/db74e38e5609/cb1fea99535b326e",
            "source_trust: untrusted_external_content",
            "raw_status: absent_in_isolated_worktree",
            "raw/monitored\\google-ai-for-developers\\" +
                "20260709-203525-google-ai-developer-platform-overview-6aedb16f.md",
            "**LiteRT-LM Kotlin API**",
            "maintenance-only mode",
            "com.google.ai.edge.litertlm:litertlm-android:<pinned-version>",
            "`EngineConfig(modelPath, backend, cacheDir)`",
            "`sendMessageAsync(...): Flow<Message>`",
            "`libvndksupport.so`",
            "`libOpenCL.so`",
            "Gemma3-1B",
            "`1005 MB`",
            "Gemma4-E2B",
            "peak CPU memory `1733 MB`, GPU `676 MB`",
            "Gemma 3 / Gemma 3n / FunctionGemma / EmbeddingGemma",
            "Gemma Terms of Use",
            "обязательный Notice",
            "Gemma 4",
            "Apache License 2.0",
            "никакие скачанные `.so`, `.dex` или executable files не исполняются",
            "`compileSdk=36`, `minSdk=26`, `targetSdk=34`",
            "Kotlin `jvmTarget` равны `17`",
            "manifest уже содержит `android.permission.INTERNET`",
            "`android:allowBackup=\"false\"`",
            "Google AI Edge Gallery требует Android 12+",
            "physical arm64 device",
            "free app-private storage >= 2.5 x model bytes",
            "## Privacy boundary",
            "local text -> Retrofit / Firebase / SollGateway / Gemini API / telemetry",
            "Нет cloud/API-key fallback",
            "Automatic tool calling выключен",
            "## Возможность local summarization",
            "## Возможность local classification",
            "принимает только exact member allowlist",
            "не изобретает confidence score",
            "interface OnDeviceTextProcessor",
            "adapter не наследует и не вызывает `SollGateway`",
            "## Promotion plan и десять measurable gates",
            "`outbound_inference_request_count == 0`",
            "`prompt_or_output_log_leak_count == 0`",
            "`classification_schema_valid_rate == 1.0`",
            "`classification_macro_f1 >= 0.85`",
            "`summary_required_point_recall >= 0.80`",
            "`summary_unsupported_claim_rate <= 0.05`",
            "cold init `p95 <= 12 s`",
            "`crash + ANR + OOM == 0`",
            "model-corruption tests проходят `3/3`",
            "`automatic_server_fallback_count == 0`",
            "https://developers.google.com/edge/litert-lm/overview",
            "https://developers.google.com/edge/litert-lm/android",
            "https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android",
            "https://github.com/google-ai-edge/LiteRT-LM",
            "https://github.com/google-ai-edge/gallery",
            "https://ai.google.dev/gemma/terms",
            "https://ai.google.dev/gemma/prohibited_use_policy",
            "https://ai.google.dev/gemma/apache_2",
        ).forEach { control ->
            assertTrue("Missing Google AI Edge/Gemma control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: eb2786f702454d47b8fe2376593523ef",
            "project: soll_app",
            "source_ref: source-item/db74e38e5609/cb1fea99535b326e",
            "source_processing_result: " +
                "google_ai_edge_gemma_on_device_deep_dive_completed_pilot_gated",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-db74e38e5609-cb1fea99535b326e-verification.md",
            "1 Soll KB deep dive added",
            "8 official Google/Google AI Edge surfaces audited",
            "2 model-license paths and 2 Android API generations distinguished",
            "2 offline text use cases and 10 measurable promotion gates documented",
            "1/1 focused contract test passed",
            "0 SDK dependencies, model downloads, permissions, credentials",
            "`conditional_go_for_isolated_pilot`",
            "GoogleAiEdgeGemmaOnDeviceKnowledgeTest",
            "Observed result: `BUILD SUCCESSFUL`",
        ).forEach { evidence ->
            assertTrue("Missing Google AI Edge/Gemma evidence: $evidence", verification.contains(evidence))
        }

        val build = projectFile("app/build.gradle.kts").readText()
        val versions = projectFile("gradle/libs.versions.toml").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue("Soll minSdk baseline drifted", build.contains("minSdk = 26"))
        assertTrue("Soll Java target baseline drifted", build.contains("JavaVersion.VERSION_17"))
        assertTrue("Soll Kotlin target baseline drifted", build.contains("jvmTarget = \"17\""))
        assertTrue(
            "Privacy audit expects the existing INTERNET permission",
            manifest.contains("android.permission.INTERNET"),
        )
        assertTrue(
            "Privacy audit expects Android backup to remain disabled",
            manifest.contains("android:allowBackup=\"false\""),
        )
        assertFalse(
            "Deep-dive task must not add LiteRT-LM to production dependencies",
            build.contains("com.google.ai.edge.litertlm"),
        )
        assertFalse(
            "Deep-dive task must not add maintenance-only MediaPipe GenAI",
            build.contains("com.google.mediapipe:tasks-genai"),
        )
        assertFalse(
            "Deep-dive task must not add a Google AI Edge version catalog entry",
            versions.contains("litertlm", ignoreCase = true),
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
