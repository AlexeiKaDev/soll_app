package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenPencilSourceTriageTest {
    @Test
    fun `OpenPencil stays a deferred evidence-gated desktop design candidate`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "openpencil-cbbbe156",
            "desktop-local, design-time spike",
            "canonical upstream project and release",
            "offline claims and least-privilege Claude Code/Cursor access",
            "disposable `.fig` copy",
            "current manual Compose workflow",
            "reimplemented and tested as ordinary Jetpack Compose code",
            "leave this item deferred",
        ).forEach { decision ->
            assertTrue("Missing OpenPencil triage decision: $decision", roadmap.contains(decision))
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
