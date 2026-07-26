package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VsCodeGcpWorkbenchDevToolingKnowledgeTest {
    @Test
    fun `GCP recovery signal becomes a bounded dev tooling knowledge note`() {
        val knowledge = projectFile(
            "docs/knowledge/vscode-gcp-workbench-elastic-ml-dev-tooling.md",
        ).readText().normalizeWhitespace()
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "task-d4db390d37d048bc9f76d51071b42479-vscode-gcp-workbench-audit.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: d4db390d37d048bc9f76d51071b42479",
            "source_ref: insight/c311bd90fa93",
            "scope: VS Code + GCP Workbench integration",
            "monitored/google-developers-blog/20260709-204007-ml-development-in-vs-code-with-google-cloud-powe-b1594323.md",
            "unverified source claim",
            "not as a platform SLA",
            "No task-owned GCP project, billing account, quota, region",
            "`SollGateway.askModelChat(...)`",
            "Android should remain a status, artifact and approval surface",
            "## Ten GCP setup concerns",
            "### 1. Evidence and workload ownership",
            "### 2. Project, billing, APIs and quota",
            "### 3. Identity and least-privilege IAM",
            "### 4. VS Code connection and extension trust",
            "### 5. Network exposure and egress",
            "### 6. Data, storage and privacy",
            "### 7. Checkpoint and elasticity semantics",
            "### 8. Reproducible development environment",
            "### 9. Cost and resource lifecycle",
            "### 10. Observability, audit and failure handling",
            "Run no pilot until all seven gates are met",
            "**Checkpoint correctness.**",
            "**Bounded recovery trial.**",
            "**Measured comparison.**",
            "successful deterministic checkpoint restore",
            "Cloud training runs, controlled TPU interruptions and measured runtime improvement",
            "all **0**",
        ).forEach { control ->
            assertTrue("Missing VS Code/GCP knowledge control: $control", knowledge.contains(control))
        }

        listOf(
            "task_id: d4db390d37d048bc9f76d51071b42479",
            "project: fdf52463-9152-453a-b186-68e7d76c3edb",
            "source_ref: insight/c311bd90fa93",
            "source_processing_result: dev_tooling_note_added_gcp_pilot_deferred",
            "verification_artifact: Soll/outputs/source-processing/task-d4db390d37d048bc9f76d51071b42479-vscode-gcp-workbench-audit.md",
            "1 dev-tooling note added",
            "4 current Soll seams audited",
            "10 GCP setup-concern categories documented",
            "7 measurable pilot gates defined",
            "1/1 focused contract test passed",
            "0 cloud resources, 0 TPU training runs and 0 Android production changes",
            "VsCodeGcpWorkbenchDevToolingKnowledgeTest",
            "Runtime elasticity and cost value remain unmeasured",
        ).forEach { evidence ->
            assertTrue("Missing VS Code/GCP verification evidence: $evidence", verification.contains(evidence))
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

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
