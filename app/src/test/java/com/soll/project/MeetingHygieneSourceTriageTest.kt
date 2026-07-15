package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingHygieneSourceTriageTest {
    @Test
    fun `meeting hygiene signal stays deferred with a confirmed workflow placement`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "meeting-hygiene-46bb283d",
            "deferred process/UX candidate",
            "desktop/server KB note",
            "user-provided notes or a consented transcript",
            "Raw Note or Chat intake",
            "`title`, `description`, `sourceRef`, `dueDate`",
            "do not reinterpret the bounded `/record` Telegram command as a meeting recorder",
            "require the user to confirm every decision and action item",
            "leave the item deferred with no production code or UI change",
        ).forEach { decision ->
            assertTrue("Missing meeting-hygiene triage decision: $decision", roadmap.contains(decision))
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
