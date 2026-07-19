package com.soll.project

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppB9898ReleaseAuditTest {
    @Test
    fun `b9898 defaults stay checksummed portable and outside the Android APK`() {
        val configFile = projectFile("tools/llama-cpp/llama_cpp_b9898_defaults.json")
        val smokeScript = projectFile("tools/llama-cpp/Test-LlamaCppB9898Release.ps1").readText()
        val toolReadme = projectFile("tools/llama-cpp/README.md").readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-e06555e37f16c034-verification.md",
        ).readText()
        val status = projectFile("soll_status.md").readText()
        val config = JSONObject(configFile.readText())

        val release = config.getJSONObject("release")
        assertEquals("b9898", release.getString("tag"))
        assertEquals(
            "3d4cbdf18a72c97648c15e2dc060013c186cd36c",
            release.getString("commit"),
        )

        val policy = config.getJSONObject("policy")
        assertTrue(policy.getBoolean("verifySha256"))
        assertFalse(policy.getBoolean("packageIntoAndroidApp"))
        assertTrue(policy.getBoolean("historicalEvidenceOnly"))
        assertTrue(policy.getBoolean("notApprovedForModelLoading"))
        assertEquals("soll-backend-route", policy.getString("androidRuntimeDefault"))

        val targets = config.getJSONArray("targets")
        assertEquals(22, targets.length())
        val targetIds = mutableSetOf<String>()
        val packageNames = mutableSetOf<String>()
        repeat(targets.length()) { targetIndex ->
            val target = targets.getJSONObject(targetIndex)
            assertTrue("Duplicate target", targetIds.add(target.getString("id")))
            val packages = target.getJSONArray("packages")
            assertTrue("Target has no packages", packages.length() > 0)
            repeat(packages.length()) { packageIndex ->
                val releasePackage = packages.getJSONObject(packageIndex)
                assertTrue("Invalid package size", releasePackage.getLong("bytes") > 0)
                assertTrue(
                    "Invalid SHA-256",
                    releasePackage.getString("sha256").matches(Regex("[0-9a-f]{64}")),
                )
                assertTrue(
                    "Release package is configured twice",
                    packageNames.add(releasePackage.getString("asset")),
                )
            }
        }
        assertEquals(24, packageNames.size)

        val platformDefaults = config.getJSONObject("platformDefaults")
        assertEquals(9, platformDefaults.length())
        platformDefaults.keys().forEach { platform ->
            assertTrue("Unknown default for $platform", targetIds.contains(platformDefaults.getString(platform)))
        }
        listOf(
            "android-arm64-cpu",
            "windows-x64-cpu",
            "windows-arm64-opencl-adreno",
            "ubuntu-x64-rocm-7.2",
            "ubuntu-x64-openvino-2026.2.1",
            "ubuntu-x64-sycl-fp16",
            "windows-x64-cuda-13.3",
            "windows-x64-vulkan",
        ).forEach { target -> assertTrue("Missing release target $target", targetIds.contains(target)) }

        val unavailable = config.getJSONArray("unavailableReleaseSignals").toString()
        assertTrue(unavailable.contains("openeuler"))
        assertTrue(unavailable.contains("no-release-asset"))

        listOf(
            "Get-FileHash",
            "Invoke-WebRequest",
            "llama-cli.exe",
            "llama-server.exe",
            "libllama.so",
            "version: 9898",
            "ELF64 AArch64",
            "CacheDirectory must stay inside the repository",
        ).forEach { control -> assertTrue("Missing smoke control $control", smokeScript.contains(control)) }
        assertTrue(toolReadme.contains("`b9892`, `b9895` or `b9898`"))

        listOf(
            "source_processing_result: b9898_binaries_verified_ci_updated",
            "verification_artifact: Soll/outputs/source-processing/" +
                "source-item-d0cd9479f2a2-e06555e37f16c034-verification.md",
            "2 release archives downloaded",
            "44/44 Android binary files",
            "22 selectable targets",
            "0 binaries packaged into the APK",
            "LlamaCppB9898ReleaseAuditTest",
        ).forEach { evidence ->
            assertTrue("Missing verification evidence: $evidence", verification.contains(evidence))
        }
        assertTrue(status.contains("llama.cpp b9898 binary release smoke"))
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
