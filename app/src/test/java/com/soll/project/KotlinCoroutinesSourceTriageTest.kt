package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinCoroutinesSourceTriageTest {
    @Test
    fun `coroutines article has a cancellation-focused Android placement`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "`kotlin-coroutines`",
            "engineering guidance, not a new runtime dependency",
            "`PhotoHandler` and `LocationHandler`",
            "`ActivityTrackingService` and `FieldMapRepository`",
            "single-resume safety",
            "prompt cancellation",
            "propagation of `CancellationException`",
            "must not hand-write continuations or compiler state machines",
        ).forEach { decision ->
            assertTrue("Missing Kotlin Coroutines triage decision: $decision", roadmap.contains(decision))
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
