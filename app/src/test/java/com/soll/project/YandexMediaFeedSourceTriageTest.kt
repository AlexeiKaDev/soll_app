package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexMediaFeedSourceTriageTest {
    @Test
    fun `Yandex media feed practices are adapted to Soll technical documentation`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val knowledge = projectFile(
            "docs/knowledge/media-feed-implementation-soll-app.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-ce21bc830e1b94e0-verification.md",
        ).readText()

        listOf(
            "source-item/0d75242b770a/ce21bc830e1b94e0",
            "docs/knowledge/media-feed-implementation-soll-app.md",
            "typed server media contract",
            "lifecycle-owned bounded Media3 player pool",
            "Chat and `Источники` surfaces remain text/image digest + article cards",
            "100-item long-feed smoke",
        ).forEach { decision ->
            assertTrue("Missing media-feed roadmap decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "https://habr.com/ru/companies/yandex/articles/1048718/",
            "## Ten implementation practices adapted to Android",
            "Define the system budget and typed contract first",
            "Keep the list flat and coordination outside composables",
            "Current visible media outranks prefetch",
            "Lease a bounded player pool to visible items",
            "Bound caches and buffering by bytes and lifetime",
            "Use content policy as a resource control",
            "Model perceived performance as explicit UI states",
            "Degrade deliberately for network, battery and thermal pressure",
            "Make observability part of the architecture",
            "Test the long-feed failure modes before promotion",
            "Correctness/lifecycle",
            "Energy/thermal",
            "Digest + article card",
            "zero measured media-feed runtime value",
        ).forEach { practice ->
            assertTrue("Missing adapted media-feed practice: $practice", knowledge.contains(practice))
        }

        listOf(
            "source_processing_result: best_practices_documented_and_adapted",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-ce21bc830e1b94e0-verification.md",
            "10 implementation practices extracted",
            "6 existing Soll seams audited",
            "8 promotion metric groups specified",
            "0 measured media-feed runtime value",
            "YandexMediaFeedSourceTriageTest",
        ).forEach { evidence ->
            assertTrue("Missing media-feed verification evidence: $evidence", verification.contains(evidence))
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
