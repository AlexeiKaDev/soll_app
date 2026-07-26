package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TeeConfidentialInferenceKnowledgeTest {
    @Test
    fun `TEE signal becomes a bounded confidential inference knowledge note`() {
        val knowledge = projectFile(
            "docs/knowledge/tee-confidential-llm-inference.md",
        ).readText()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-810413622d7845debcea6bf265340ac4-tee-knowledge-audit.md",
        ).readText()

        listOf(
            "task_id: 810413622d7845debcea6bf265340ac4",
            "source_ref: insight/61caa564515a",
            "monitored/nvidia-technical-blog/20260706-080044-hardware-rooted-ai-security-that-won-t-slow-you--c2f1af06.md",
            "confidential computing for server-side LLM inference",
            "A TEE protects data in use",
            "A TEE does not replace TLS",
            "does not protect against prompt injection",
            "`SollGateway.askModelChat(...)`",
            "`SecurePayloadEnvelopeRequest`",
            "`EncryptedSharedPreferences`",
            "No production TEE or attestation integration",
            "silent non-TEE fallback is forbidden",
            "TTFT p50/p95",
            "## Eight promotion gates",
            "**Threat model.**",
            "**Measured workload.**",
            "**Attestation verification.**",
            "**Key and data lifecycle.**",
            "**Boundary audit.**",
            "**Output and action safety.**",
            "**Performance and reliability.**",
            "**Evidence, fallback and rollback.**",
            "Actual TEE inference runs completed by this task: **0**",
        ).forEach { control ->
            assertTrue("Missing TEE knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "source_processing_result: technical_note_added_tee_pilot_deferred",
            "verification_artifact: Soll/outputs/source-processing/" +
                "task-810413622d7845debcea6bf265340ac4-tee-knowledge-audit.md",
            "1 TEE technical note added",
            "4 current Soll security/inference seams audited",
            "8 promotion gates defined",
            "1/1 focused contract test passed",
            "0 TEE inference runs",
            "0 production files changed",
            "TeeConfidentialInferenceKnowledgeTest",
        ).forEach { evidence ->
            assertTrue("Missing TEE verification evidence: $evidence", verification.contains(evidence))
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
