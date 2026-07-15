package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrYandexGpuRenderSourceTriageTest {
    @Test
    fun `GPU render signal records the real cluster gate without claiming a synthetic pass`() {
        val roadmap = projectFile("docs/soll_app-superassistant-roadmap-2026-05-06.md").readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-e916238e51d2cfd0-verification.md",
        ).readText()

        listOf(
            "source-item/0d75242b770a/e916238e51d2cfd0",
            "YTsaurus GPU-rendering source is deferred",
            "`gpu_limit=1`",
            "`pool_trees=[gpu]`",
            "three bounded `vkcube` runs",
            "must not submit render operations or hold cluster credentials",
        ).forEach { decision ->
            assertTrue("Missing YTsaurus GPU-render decision: $decision", roadmap.contains(decision))
        }

        listOf(
            "source_processing_result: audited_and_deferred_no_test_cluster",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-0d75242b770a-e916238e51d2cfd0-verification.md",
            "criterion is not met",
            "Actual YTsaurus operations completed: `0`",
            "test-cluster trials: `0`",
            "`/dev/nvidia-modeset`",
            "`/dev/dri/renderD128`",
            "Do not use `--privileged`",
            "measured runtime value remains `0`",
        ).forEach { evidence ->
            assertTrue("Missing YTsaurus GPU-render evidence: $evidence", verification.contains(evidence))
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
