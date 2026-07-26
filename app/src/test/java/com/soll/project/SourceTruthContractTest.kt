package com.soll.project

import com.soll.domain.soll.SollSourceItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceTruthContractTest {
    @Test
    fun `source contract uses cursor pages and carries truth metadata`() {
        val api = projectFile("app/src/main/java/com/soll/data/api/SollApiService.kt").readText()
        val repository = projectFile("app/src/main/java/com/soll/data/repository/SollRepository.kt").readText()
        val gateway = projectFile("app/src/main/java/com/soll/domain/soll/SollGateway.kt").readText()

        assertTrue(api.contains("@GET(\"api/v1/sources/{source_id}/items/page\")"))
        assertTrue(api.contains("data class SourceItemsPageResponse"))
        listOf(
            "lastStatus",
            "actionability",
            "safeNextStep",
            "auditRef",
            "evidenceRef",
            "statusReason",
            "deliveryStatus",
        ).forEach { field -> assertTrue("Missing source truth field: $field", api.contains("val $field")) }
        assertTrue(repository.contains("override suspend fun listSourceItemsPage"))
        assertTrue(gateway.contains("data class SollSourceItemsPage"))
    }

    @Test
    fun `source screens page forward instead of stopping after twenty items`() {
        val taskViewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardViewModel.kt",
        ).readText()
        val taskScreen = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardScreen.kt",
        ).readText()
        val todoViewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/todo/DailyTodoViewModel.kt",
        ).readText()

        assertTrue(taskViewModel.contains("fun loadMoreSourceItems()"))
        assertTrue(todoViewModel.contains("fun loadMoreSourceItems()"))
        assertFalse(taskViewModel.contains("listSourceItems(id, limit = 20)"))
        assertFalse(todoViewModel.contains("listSourceItems(id, limit = 20)"))
        assertTrue(taskScreen.contains("item.isTerminal -> PassiveChip"))
        assertTrue(taskScreen.contains("Text(\"Загрузить ещё\")"))
    }

    @Test
    fun `terminal and unsafe items cannot create another task`() {
        val implemented = sourceItem(lastStatus = "implemented")
        val manualReview = sourceItem(lastStatus = "new", dualUseAction = "manual_review")
        val actionable = sourceItem(lastStatus = "changed")

        assertTrue(implemented.isTerminal)
        assertFalse(implemented.canCreateTask)
        assertFalse(manualReview.canCreateTask)
        assertTrue(actionable.canCreateTask)
        assertEquals("Focused reason", actionable.visibleReason)
    }

    private fun sourceItem(
        lastStatus: String,
        dualUseAction: String = "allow",
    ) = SollSourceItem(
        itemId = "item-1",
        title = "Source item",
        sourceUrl = "https://example.com/item",
        contentPreview = "Preview",
        summary = "Summary",
        usefulness = "high",
        reasoning = "Fallback reason",
        evidenceLevel = "primary",
        projectFit = "soll_app",
        actionability = "implementation_ready",
        dualUseRisk = "none",
        dualUseAction = dualUseAction,
        safeNextStep = "Implement",
        needsDeepDive = false,
        rawFile = "raw/item.md",
        notifiedAt = "",
        lastStatus = lastStatus,
        auditRef = "raw/item.md",
        evidenceRef = "verification.md",
        verificationArtifact = "verification.md",
        statusReason = "Focused reason",
        deliveryStatus = "notified",
    )

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: return@repeat
        }
        error("Project file not found: $path")
    }
}
