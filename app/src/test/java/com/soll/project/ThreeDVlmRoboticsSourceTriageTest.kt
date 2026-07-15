package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeDVlmRoboticsSourceTriageTest {
    @Test
    fun `3d vlm robotics signal stays deferred with a safe implementation boundary`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()

        listOf(
            "`3d-vlm-robotics` is a deferred embodied-robotics research candidate",
            "architectural synthesis, not a drop-in SDK",
            "no calibrated multi-view/depth input, robot kinematics, gripper or motion planner",
            "isolated desktop/server robotics sandbox",
            "one synthetic or simulated pick/place scenario",
            "deterministic tools for grasp lookup, reachability, collision and stability checks",
            "no command may reach hardware during the spike",
            "against a deterministic non-VLM baseline",
            "it must never send VLM-produced coordinates directly to actuators",
            "leave the candidate deferred with no production code, dependency or UI change",
        ).forEach { decision ->
            assertTrue("Missing 3D VLM robotics triage decision: $decision", roadmap.contains(decision))
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
