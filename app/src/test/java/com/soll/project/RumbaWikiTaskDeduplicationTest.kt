package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RumbaWikiTaskDeduplicationTest {
    @Test
    fun `RUMBA wiki work has one canonical task and one closed linked duplicate`() {
        val canonicalTaskId = "092df8f4d66143d0a402c29aa74155cc"
        val duplicateTaskId = "87c44d38824e4d4b8f3678683128a943"
        val wiki = projectFile("wiki/rumba-russkoyazychnyy.md").readText()
        val canonicalAudit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-092df8f4d66143d0a402c29aa74155cc-rumba-integration-audit.md",
        ).readText()
        val deduplicationAudit = projectFile(
            "Soll/outputs/source-processing/" +
                "task-87c44d38824e4d4b8f3678683128a943-rumba-task-deduplication-audit.md",
        ).readText()

        val wikiTaskIds = Regex("(?m)^task_id: ([0-9a-f]{32})$")
            .findAll(wiki)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(listOf(canonicalTaskId), wikiTaskIds)
        assertFalse(wiki.lines().any { it == "task_id: $duplicateTaskId" })

        listOf(
            "canonical_task_id: $canonicalTaskId",
            "canonical_task_status: validated",
            "linked_duplicate_task_id: $duplicateTaskId",
            "linked_duplicate_source_ref: insight/e202a3afd00a",
            "linked_duplicate_status: closed_linked",
            "active_task_count: 1",
            "2 task IDs matched; 1 canonical active task retained; " +
                "1 duplicate linked and closed",
        ).forEach { control ->
            assertTrue("Missing wiki deduplication control: $control", wiki.contains(control))
        }

        listOf(
            "task_id: $canonicalTaskId",
            "source_ref: insight/e348746d9311",
            "status: validated",
            "source_processing_result: " +
                "validated_relevant_offline_eval_blueprint_runtime_integration_deferred",
        ).forEach { canonicalControl ->
            assertTrue(
                "Canonical RUMBA record drifted: $canonicalControl",
                canonicalAudit.contains(canonicalControl),
            )
        }

        listOf(
            "task_id: $duplicateTaskId",
            "source_ref: insight/e202a3afd00a",
            "status: resolved_duplicate",
            "canonical_task_id: $canonicalTaskId",
            "duplicate_task_id: $duplicateTaskId",
            "duplicate_task_status: closed_linked",
            "active_task_count: 1",
            "| Active canonical records | `1` |",
            "| Analysis copies added | `0` |",
            "| Production/runtime files changed | `0` |",
        ).forEach { auditControl ->
            assertTrue(
                "Missing deduplication audit control: $auditControl",
                deduplicationAudit.contains(auditControl),
            )
        }

        listOf(
            "validated_relevant_offline_eval_blueprint_runtime_integration_deferred",
            "conditional offline evaluation candidate",
        ).forEach { preservedDecision ->
            assertTrue(wiki.contains(preservedDecision))
            assertTrue(deduplicationAudit.contains(preservedDecision))
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
