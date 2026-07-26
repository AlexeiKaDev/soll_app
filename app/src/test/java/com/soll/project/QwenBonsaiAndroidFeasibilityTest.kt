package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenBonsaiAndroidFeasibilityTest {
    @Test
    fun `Bonsai 27B analysis keeps Android adoption behind measured gates`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val knowledge = projectFile(
            "docs/knowledge/bonsai-27b-on-device-android-feasibility.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-chat-962471563b17ded7b120-verification.md",
        ).readText()

        listOf(
            "task:chat:962471563b17ded7b120",
            "docs/knowledge/bonsai-27b-on-device-android-feasibility.md",
            "Android production integration is not approved",
            "text-only `arm64-v8a` compatibility spike",
            "8 promotion gates",
            "Actual Android Bonsai runs and measured runtime value remain 0",
        ).forEach { decision ->
            assertTrue("Missing Bonsai roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "https://habr.com/ru/articles/1059572/",
            "5,9 ГБ — идеальный размер",
            "около 7,2 ГБ",
            "89,47%",
            "80,0 до 66,03",
            "iPhone 17 Pro Max через MLX Swift",
            "нет опубликованного PrismML Android APK/AAR",
            "около 5,2 ГБ при контексте 4K",
            "`SollGateway.askModelChat(...)`",
            "нет `CMakeLists.txt`, `externalNativeBuild`",
            "No heavy local LLM on Android in early phases",
            "cloud fallback запрещен без нового согласия",
            "### P0 — воспроизводимый compatibility spike",
            "## Восемь ворот продвижения",
            "**Совместимость.**",
            "**Память.**",
            "**Скорость.**",
            "**Тепло и батарея.**",
            "**Качество.**",
            "**Tool safety.**",
            "**Privacy/offline.**",
            "**Доставка и откат.**",
        ).forEach { control ->
            assertTrue("Missing Bonsai Android control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: deep_analysis_completed_android_pilot_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-chat-962471563b17ded7b120-verification.md",
            "1 deep feasibility note added",
            "8 source claims and 7 current Soll seams audited",
            "8 promotion gates defined",
            "Actual Android Bonsai runs: `0`",
            "measured Android runtime value: `0`",
            "QwenBonsaiAndroidFeasibilityTest",
        ).forEach { evidence ->
            assertTrue("Missing Bonsai verification evidence: $evidence", verification.contains(evidence))
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
