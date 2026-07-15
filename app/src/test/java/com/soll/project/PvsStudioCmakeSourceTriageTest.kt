package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PvsStudioCmakeSourceTriageTest {
    @Test
    fun `PVS Studio signal has a firmware-only gated placement plan`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "pvs-studio-cmake-4db4899a",
            "Gradle/Kotlin Android project",
            "must not add CMake, NDK or PVS-Studio",
            "separate CMake-based C/C++ firmware repository such as Aquik/ESP",
            "CMAKE_<LANG>_PVS_STUDIO",
            "target-level `<LANG>_PVS_STUDIO`",
            "Makefile or Ninja generator",
            "non-blocking build-log mode",
            "license material outside version control",
            "standalone full report artifact",
        ).forEach { decision ->
            assertTrue("Missing PVS-Studio/CMake triage decision: $decision", roadmap.contains(decision))
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
