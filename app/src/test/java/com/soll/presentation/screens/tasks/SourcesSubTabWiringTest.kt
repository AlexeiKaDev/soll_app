package com.soll.presentation.screens.tasks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesSubTabWiringTest {
    @Test
    fun `selecting a source jumps straight to the materials sub-tab`() {
        val viewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardViewModel.kt"
        ).readText()

        // Опening a source from the list must switch straight to the
        // materials sub-tab -- otherwise the user still has to find the
        // tab themselves after tapping "Открыть", defeating the fix for
        // having to scroll past every source card to see its materials.
        val selectSourceBody = Regex(
            "fun selectSource\\(source: SollMonitoredSource\\) \\{.*?\\n    \\}",
            RegexOption.DOT_MATCHES_ALL,
        ).find(viewModel)?.value
        requireNotNull(selectSourceBody) { "selectSource() not found in TaskBoardViewModel.kt" }
        assertTrue(selectSourceBody.contains("selectedSourcesSubTab = SourcesSubTab.MATERIALS"))
    }

    @Test
    fun `sources screen renders a sub-tab per selected sub-tab instead of one combined list`() {
        val screen = projectFile(
            "app/src/main/java/com/soll/presentation/screens/tasks/TaskBoardScreen.kt"
        ).readText()

        // The old layout rendered the materials list as trailing items in
        // the SAME LazyColumn as every source card, forcing a scroll past
        // all of them to reach the selected source's materials. The fix
        // splits list and materials into separate sub-tab composables
        // driven by uiState.selectedSourcesSubTab.
        assertTrue(screen.contains("SourcesSubTab.LIST -> SourcesListTab"))
        assertTrue(screen.contains("SourcesSubTab.MATERIALS -> SourceMaterialsTab"))
        assertFalse(
            "SourcesListTab must not also render sourceItems inline (that was the bug)",
            Regex(
                "private fun SourcesListTab\\(.*?\\n\\}",
                RegexOption.DOT_MATCHES_ALL,
            ).find(screen)?.value?.contains("uiState.sourceItems") ?: false,
        )
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path")
    }
}
