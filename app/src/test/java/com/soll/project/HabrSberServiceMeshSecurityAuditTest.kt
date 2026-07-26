package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabrSberServiceMeshSecurityAuditTest {
    @Test
    fun `service mesh article becomes a bounded security audit with value evidence`() {
        val audit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-7e67fcf32f744b0d82fc777876284397-service-mesh-security-audit.md",
        ).readText()
        val appModule = projectFile("app/src/main/java/com/soll/di/AppModule.kt").readText()
        val appBuild = projectFile("app/build.gradle.kts").readText()
        val repository = projectFile(
            "app/src/main/java/com/soll/data/repository/SollRepository.kt",
        ).readText()
        val botConfig = projectFile(
            "app/src/main/java/com/soll/data/local/entity/BotConfigEntity.kt",
        ).readText()

        listOf(
            "task_id: 7e67fcf32f744b0d82fc777876284397",
            "source_ref: insight/63b2d2897866",
            "https://habr.com/ru/companies/sberbank/articles/1046634/",
            "## mTLS configuration review",
            "no application mTLS configuration is present",
            "## Egress policy implementation review",
            "no Service Mesh or host allowlist",
            "## Database connection security review",
            "the app has no direct remote database",
            "source context is lost",
            "ports (`5001`",
            "`5002`)",
            "Data at rest is a separate security gap",
            "`6` server promotion gates defined",
            "0 production changes",
            "source_processing_result: service_mesh_review_completed_android_boundary_confirmed",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-7e67fcf32f744b0d82fc777876284397-service-mesh-security-audit.md",
            "value_metric: \"3 security areas audited; 0 Istio manifests",
            "HabrSberServiceMeshSecurityAuditTest",
        ).forEach { evidence ->
            assertTrue("Missing service-mesh audit evidence: $evidence", audit.contains(evidence))
        }

        assertTrue(appBuild.contains("manifestPlaceholders[\"usesCleartextTraffic\"] = \"false\""))
        assertTrue(appBuild.contains("manifestPlaceholders[\"usesCleartextTraffic\"] = \"true\""))
        assertTrue(repository.contains("\"http://\$trimmed\""))
        assertTrue(appModule.contains("OkHttpClient.Builder()"))
        assertTrue(appModule.contains("Room.databaseBuilder("))
        assertTrue(botConfig.contains("val token: String"))
        assertFalse(appModule.contains("CertificatePinner("))
        assertFalse(appModule.contains("clientCertificate("))
        assertFalse(appModule.contains("fallbackToDestructiveMigration()"))
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
