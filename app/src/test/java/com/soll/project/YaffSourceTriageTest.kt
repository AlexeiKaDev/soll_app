package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class YaffSourceTriageTest {
    @Test
    fun `YaFF evaluation records local benchmarks and a safe integration gate`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-84a6a99a52dd1e61-verification.md",
        ).readText()

        listOf(
            "yaff-zero-copy-protobuf-cefec43f",
            "d6f74675374b587ce24112c284abd54a92090221",
            "Retrofit/Moshi JSON",
            "Do not add YaFF or change the Android wire contract",
            "ReadMessage` has no verifier for hostile buffers",
            "keep production adoption deferred",
        ).forEach { decision ->
            assertTrue("Missing YaFF triage decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "source_processing_result: benchmarked_and_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-84a6a99a52dd1e61-verification.md",
            "0 measured Soll_app runtime value",
            "YaFF Flat | 6.33 | 110.56",
            "Protobuf | 111 | 307",
            "YaFF Flat | 7.55 | 238",
            "50 fields, 5% populated | 31.77 | 108.33 | 294.58 | 76.73",
            "trusted internal C++ read-only snapshot/filter sidecar",
            "Never pass remote bytes directly to `ReadMessage`",
        ).forEach { evidence ->
            assertTrue("Missing YaFF verification evidence: $evidence", verification.contains(evidence))
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
