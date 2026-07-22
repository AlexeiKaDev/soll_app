package com.soll.project

import java.io.File
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NopaOnline3dSceneGraphEvaluationTest {
    @Test
    fun `NoPA paper produces a reproducible comparison and deferred integration decision`() {
        val fixture = JSONObject(
            projectFile("docs/knowledge/nopa-particle-merge-synthetic-v1.json").readText(),
        )
        val analysis = projectFile(
            "docs/knowledge/nopa-online-3d-scene-graph-soll-evaluation.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-9011e13c06d6-cf371816a49777af-verification.md",
        ).readText().normalizeWhitespace()

        assertEquals("nopa-particle-merge-synthetic-v1", fixture.getString("experiment_id"))
        assertEquals("arxiv:2607.00529v1", fixture.getString("source_version"))
        assertEquals(
            "source-item/9011e13c06d6/cf371816a49777af",
            fixture.getString("source_ref"),
        )
        val scope = fixture.getJSONObject("scope")
        assertTrue(scope.getBoolean("synthetic_non_sensitive"))
        assertFalse(scope.getBoolean("network_required"))
        assertFalse(scope.getBoolean("production_writes"))

        val calibration = fixture.getJSONArray("calibration_cases").toCases()
        val holdout = fixture.getJSONArray("holdout_cases").toCases()
        assertEquals(6, calibration.size)
        assertEquals(6, holdout.size)
        assertEquals(calibration.size, calibration.map { it.id }.toSet().size)
        assertEquals(holdout.size, holdout.map { it.id }.toSet().size)

        val calibrationScores = calibration.associate { it.id to particleMmd(it.a, it.b) }
        val largestMergeScore = calibration
            .filter { it.expected == MERGE }
            .maxOf { calibrationScores.getValue(it.id) }
        val smallestSpawnScore = calibration
            .filter { it.expected == SPAWN }
            .minOf { calibrationScores.getValue(it.id) }
        assertTrue("Calibration classes must be separable", largestMergeScore < smallestSpawnScore)
        val calibratedThreshold = (largestMergeScore + smallestSpawnScore) / 2.0
        assertEquals(
            fixture.getJSONObject("expected_results").getDouble("calibrated_mmd_threshold"),
            calibratedThreshold,
            1e-9,
        )

        val expectedHoldout = fixture
            .getJSONObject("expected_results")
            .getJSONArray("holdout")
            .toObjectMap()
        var momentCorrect = 0
        var mmdCorrect = 0
        holdout.forEach { case ->
            val expected = expectedHoldout.getValue(case.id)
            val momentDecision = if (momentProxyMerge(case.a, case.b)) MERGE else SPAWN
            val mmdScore = particleMmd(case.a, case.b)
            val mmdDecision = if (mmdScore <= calibratedThreshold) MERGE else SPAWN

            assertEquals(expected.getString("moment_proxy"), momentDecision)
            assertEquals(expected.getString("particle_mmd"), mmdDecision)
            assertEquals(expected.getDouble("mmd_score"), mmdScore, 1e-6)
            if (momentDecision == case.expected) momentCorrect += 1
            if (mmdDecision == case.expected) mmdCorrect += 1
        }

        val aggregate = fixture.getJSONObject("expected_results").getJSONObject("aggregate")
        assertEquals(aggregate.getInt("moment_proxy_correct"), momentCorrect)
        assertEquals(aggregate.getInt("particle_mmd_correct"), mmdCorrect)
        assertEquals(3, momentCorrect)
        assertEquals(6, mmdCorrect)
        assertEquals(0.5, momentCorrect.toDouble() / holdout.size, 0.0)
        assertEquals(1.0, mmdCorrect.toDouble() / holdout.size, 0.0)
        assertEquals(0, aggregate.getInt("unsafe_side_effects"))

        val currentSoll = fixture.getJSONObject("current_soll_baseline")
        assertEquals(0, currentSoll.getInt("holdout_association_cases_runnable"))
        assertEquals(6, currentSoll.getInt("holdout_association_cases_total"))
        listOf(
            "rgb_d_depth_map",
            "world_camera_pose",
            "per_frame_2d_scene_graph",
            "particle_or_point_cloud_representation",
            "global_3d_scene_graph",
        ).forEach { missingCapability ->
            assertFalse("Unexpected Soll 3D capability: $missingCapability", currentSoll.getBoolean(missingCapability))
        }

        val scanner = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tools/scanner/ScannerScreen.kt",
        ).readText()
        val scannerRepository = projectFile(
            "app/src/main/java/com/soll/data/repository/ScannerRepository.kt",
        ).readText()
        val appBuild = projectFile("app/build.gradle.kts").readText()
        listOf(
            "ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST",
            "MlKitBarcodeAnalyzer(",
            "BarcodeScanning.getClient(",
            "onBarcodeDetected: (rawValue: String, format: String) -> Unit",
        ).forEach { currentContract ->
            assertTrue("Missing current Soll scanner contract: $currentContract", scanner.contains(currentContract))
        }
        assertTrue(scannerRepository.contains("suspend fun addScan("))
        assertTrue(scannerRepository.contains("rawValue: String"))
        assertTrue(appBuild.contains("implementation(libs.camera.core)"))
        assertTrue(appBuild.contains("implementation(libs.mlkit.barcode.scanning)"))
        assertFalse(appBuild.contains("arcore", ignoreCase = true))

        listOf(
            "task_id: 9f64e52d3f9d4380a823d56cf5aff30e",
            "source_ref: source-item/9011e13c06d6/cf371816a49777af",
            "source_version: arxiv:2607.00529v1",
            "28 страниц",
            "6,513,698 bytes",
            "79b0cc49e139a20617f6c03d81f278202bf619c0871e41ea4a56e24469244c43",
            "5,629,628 bytes",
            "bb9d95a54811b125894cd2c625f957d1df02e183268f0232e0cce82fd4139b7a",
            "## Воспроизводимый локальный эксперимент",
            "calibration `6` + holdout `6`",
            "moment-only proxy: `3/6`",
            "particle MMD: `6/6`",
            "текущий Soll path: `0/6 runnable`",
            "интеграцию отложить",
            "0` production/runtime",
        ).forEach { evidence ->
            assertTrue("Missing NoPA analysis evidence: $evidence", analysis.contains(evidence))
        }

        listOf(
            "source_processing_result: full_paper_downloaded_synthetic_merge_comparison_completed_integration_deferred",
            "verification_artifact: Soll/outputs/source-processing/source-item-9011e13c06d6-cf371816a49777af-verification.md",
            "1 full 28-page PDF plus TeX source downloaded and SHA-256 verified",
            "12-case deterministic particle comparison",
            "MMD 6/6 versus moment proxy 3/6 on holdout",
            "current Soll 3D association coverage 0/6",
            "NopaOnline3dSceneGraphEvaluationTest",
            "1/1 focused contract test passed",
        ).forEach { evidence ->
            assertTrue("Missing NoPA verification evidence: $evidence", verification.contains(evidence))
        }
    }

    private fun momentProxyMerge(a: List<Point3>, b: List<Point3>): Boolean =
        euclidean(mean(a), mean(b)) <= 0.1 && covarianceDistance(a, b) <= 0.1

    private fun particleMmd(a: List<Point3>, b: List<Point3>): Double {
        val all = a + b
        val nonZeroSquaredDistances = buildList {
            for (leftIndex in all.indices) {
                for (rightIndex in (leftIndex + 1)..<all.size) {
                    val distance = squaredDistance(all[leftIndex], all[rightIndex])
                    if (distance > 1e-12) add(distance)
                }
            }
        }.sorted()
        require(nonZeroSquaredDistances.isNotEmpty())
        val middle = nonZeroSquaredDistances.size / 2
        val sigmaSquared = if (nonZeroSquaredDistances.size % 2 == 1) {
            nonZeroSquaredDistances[middle]
        } else {
            (nonZeroSquaredDistances[middle - 1] + nonZeroSquaredDistances[middle]) / 2.0
        }
        val squaredMmd = averageKernel(a, a, sigmaSquared) +
            averageKernel(b, b, sigmaSquared) -
            2.0 * averageKernel(a, b, sigmaSquared)
        return sqrt(max(0.0, squaredMmd))
    }

    private fun averageKernel(
        left: List<Point3>,
        right: List<Point3>,
        sigmaSquared: Double,
    ): Double {
        var total = 0.0
        left.forEach { a ->
            right.forEach { b ->
                total += exp(-squaredDistance(a, b) / (2.0 * sigmaSquared))
            }
        }
        return total / (left.size * right.size)
    }

    private fun covarianceDistance(a: List<Point3>, b: List<Point3>): Double {
        val left = covariance(a)
        val right = covariance(b)
        var squared = 0.0
        for (row in 0..2) {
            for (column in 0..2) {
                val delta = left[row][column] - right[row][column]
                squared += delta * delta
            }
        }
        return sqrt(squared)
    }

    private fun covariance(points: List<Point3>): Array<DoubleArray> {
        val center = mean(points)
        return Array(3) { row ->
            DoubleArray(3) { column ->
                points.sumOf { point ->
                    (point[row] - center[row]) * (point[column] - center[column])
                } / points.size
            }
        }
    }

    private fun mean(points: List<Point3>): Point3 = Point3(
        points.sumOf { it.x } / points.size,
        points.sumOf { it.y } / points.size,
        points.sumOf { it.z } / points.size,
    )

    private fun euclidean(a: Point3, b: Point3): Double = sqrt(squaredDistance(a, b))

    private fun squaredDistance(a: Point3, b: Point3): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return dx * dx + dy * dy + dz * dz
    }

    private operator fun Point3.get(index: Int): Double = when (index) {
        0 -> x
        1 -> y
        else -> z
    }

    private fun JSONArray.toCases(): List<FixtureCase> = List(length()) { index ->
        val value = getJSONObject(index)
        FixtureCase(
            id = value.getString("id"),
            expected = value.getString("expected"),
            a = value.getJSONArray("a").toPoints(),
            b = value.getJSONArray("b").toPoints(),
        )
    }

    private fun JSONArray.toPoints(): List<Point3> = List(length()) { index ->
        val coordinates = getJSONArray(index)
        Point3(
            x = coordinates.getDouble(0),
            y = coordinates.getDouble(1),
            z = coordinates.getDouble(2),
        )
    }

    private fun JSONArray.toObjectMap(): Map<String, JSONObject> =
        List(length()) { index -> getJSONObject(index) }.associateBy { it.getString("id") }

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

    private data class FixtureCase(
        val id: String,
        val expected: String,
        val a: List<Point3>,
        val b: List<Point3>,
    )

    private data class Point3(
        val x: Double,
        val y: Double,
        val z: Double,
    )

    private companion object {
        const val MERGE = "merge"
        const val SPAWN = "spawn"
    }
}
