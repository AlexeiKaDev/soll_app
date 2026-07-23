package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiEvalsPinningAuditTest {
    @Test
    fun `audit records immutable upstream pins and absent local pinning surfaces`() {
        val verification = projectFile(
            "Soll/outputs/source-processing/" +
                "source-item-2d59297d3e34-cfb13e207e7fa245-verification.md",
        ).readText().normalizeWhitespace()

        listOf(
            "task_id: b4a9366249e144fdbbf37e05aa2e8745",
            "project: soll_app",
            "source_ref: source-item/2d59297d3e34/cfb13e207e7fa245",
            "source_trust: untrusted_external_content",
            "source_processing_result: ci_pinning_audit_completed_no_local_refs",
            "8eac7a7de5215c907fbddc30efdaf316913eccdd",
            "dbb1a20192809f5004d0c274374963b1e3cb20bf",
            "5 pre-commit hook revisions",
            "4 GitHub Actions usages",
            "0 local hook/action references require pinning",
            "The hook IDs, arguments, excludes, ordering and repository URLs were unchanged",
            "Workflow triggers, permissions, inputs, Python version, LFS behavior and test commands were unchanged",
            "The comparison is intentionally limited to the current isolated worktree",
            "1/1 focused contract test passed",
            "0 application, build, dependency or runtime changes",
        ).forEach { evidence ->
            assertTrue("Missing pinning audit evidence: $evidence", verification.contains(evidence))
        }

        listOf(
            ".pre-commit-config.yaml",
            ".pre-commit-config.yml",
            ".github/workflows",
            "Soll/.pre-commit-config.yaml",
            "Soll/.pre-commit-config.yml",
            "Soll/.github/workflows",
        ).forEach { path ->
            assertFalse(
                "Audit baseline unexpectedly contains CI/pre-commit surface: $path",
                projectPath(path).exists(),
            )
        }
    }

    private fun projectFile(path: String): File =
        projectPath(path).also { file ->
            assertTrue("Project file not found: $path", file.isFile)
        }

    private fun projectPath(path: String): File = File(projectRoot(), path)

    private fun projectRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            if (
                File(current, "settings.gradle.kts").isFile &&
                File(current, "app").isDirectory
            ) {
                return current
            }
            current = current.parentFile ?: current
        }
        error("Project root not found from ${System.getProperty("user.dir")}")
    }

    private fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ")
}
