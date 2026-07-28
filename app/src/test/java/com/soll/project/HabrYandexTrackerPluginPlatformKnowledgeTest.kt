package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrYandexTrackerPluginPlatformKnowledgeTest {
    @Test
    fun `Yandex plugin patterns remain server first and measurable for Soll`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md")
            .readText()
            .normalizeWhitespace()
        val knowledge = projectFile(
            "docs/knowledge/yandex-tracker-plugin-platform-soll-boundary.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-7968f28912d24cbca2164067de27810e-habr-yandex-tracker-plugins-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "insight/220db783f7ba",
            "docs/knowledge/yandex-tracker-plugin-platform-soll-boundary.md",
            "server-first extension security contract",
            "treat LLM-generated code as an untrusted proposal",
            "Do not add dynamic JavaScript, a WebView plugin host",
            "3-5 frozen synthetic fixtures",
            "all seven documented promotion gates",
        ).forEach { decision ->
            assertTrue("Missing plugin-platform roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "task_id: 7968f28912d24cbca2164067de27810e",
            "source_ref: insight/220db783f7ba",
            "wiki/habr-yandex-company-1.md",
            "20260727-230008-600-000-c611fa02.md",
            "public_primary_verified_local_snapshots_absent",
            "https://habr.com/ru/companies/yandex/articles/1062416/",
            "Six source patterns were confirmed",
            "These six seams are useful integration boundaries",
            "Seven controls are mandatory",
            "Seven promotion gates apply",
            "Effective access is the intersection of manifest, worker, user",
            "Code receives a broker capability, never a raw token",
            "LLM-generated code is an untrusted proposal",
            "one read-only server extension",
            "three to five frozen fixtures",
            "unauthorized external, filesystem and device effects remain exactly `0`",
            "measurable runtime value is `0`",
            "Android public contract remains unchanged",
        ).forEach { control ->
            assertTrue("Missing Soll extension boundary control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: bounded_server_first_extension_contract_documented",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-7968f28912d24cbca2164067de27810e-habr-yandex-tracker-plugins-audit.md",
            "6 public-source patterns verified",
            "6 existing seams audited",
            "7 server-first controls and 7 promotion gates documented",
            "1/1 focused contract test passed",
            "0 production/runtime files or dependencies changed",
            "HabrYandexTrackerPluginPlatformKnowledgeTest",
            "Observed result: `BUILD SUCCESSFUL`",
            "Runtime value remains `0`",
        ).forEach { evidence ->
            assertTrue("Missing plugin-platform verification evidence: $evidence", verification.contains(evidence))
        }

        val runtimeInputs = listOf(
            projectFile("app/src/main/java"),
            projectFile("app/build.gradle.kts"),
        ).joinToString("\n") { file ->
            if (file.isDirectory) {
                file.walkTopDown()
                    .filter(File::isFile)
                    .filter { it.extension in setOf("kt", "java") }
                    .joinToString("\n") { it.readText() }
            } else {
                file.readText()
            }
        }
        listOf(
            "android.webkit.WebView",
            "addJavascriptInterface",
            "habr.com/ru/companies/yandex/articles/1062416",
            "SollExtensionManifest",
        ).forEach { runtimeToken ->
            assertFalse(
                "Knowledge-only task must not add runtime plugin token: $runtimeToken",
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
