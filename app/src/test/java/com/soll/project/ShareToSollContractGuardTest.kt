package com.soll.project

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareToSollContractGuardTest {
    @Test
    fun `manifest exposes only a text plain send target`() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val sendFilter = manifest.substringAfter("android.intent.action.SEND").substringBefore("</intent-filter>")

        assertTrue(sendFilter.contains("android.intent.category.DEFAULT"))
        assertTrue(sendFilter.contains("android:mimeType=\"text/plain\""))
        assertFalse(sendFilter.contains("android.intent.category.BROWSABLE"))
    }

    @Test
    fun `share entry routes to dedicated screen and exact api endpoint`() {
        val activity = projectFile("app/src/main/java/com/soll/presentation/MainActivity.kt").readText()
        val navigation = projectFile(
            "app/src/main/java/com/soll/presentation/navigation/AppNavigation.kt"
        ).readText()
        val api = projectFile("app/src/main/java/com/soll/data/api/SollApiService.kt").readText()

        assertTrue(activity.contains("Intent.ACTION_SEND"))
        assertTrue(activity.contains("Intent.EXTRA_TEXT"))
        assertTrue(activity.contains("SECTION_SHARE_IMPORT"))
        assertTrue(navigation.contains("composable(Routes.SHARE_IMPORT)"))
        assertTrue(api.contains("@POST(\"api/v1/feed/import-link\")"))
    }

    @Test
    fun `share import is persisted before network delivery and can retry`() {
        val entity = projectFile(
            "app/src/main/java/com/soll/data/local/entity/SyncQueueEntity.kt"
        ).readText()
        val repository = projectFile(
            "app/src/main/java/com/soll/data/repository/SollSyncQueueRepository.kt"
        ).readText()
        val viewModel = projectFile(
            "app/src/main/java/com/soll/presentation/screens/share/ShareImportViewModel.kt"
        ).readText()

        assertTrue(entity.contains("KIND_FEED_IMPORT"))
        assertTrue(repository.contains("suspend fun enqueueFeedImport"))
        assertTrue(repository.contains("syncQueueDao.insert("))
        assertTrue(repository.contains("SyncQueueEntity.KIND_FEED_IMPORT -> retryFeedImport"))
        assertTrue(repository.contains("suspend fun retryNow"))
        assertTrue(viewModel.contains("SollSyncQueueRepository"))
        assertTrue(viewModel.contains("enqueueFeedImport("))
        assertTrue(viewModel.contains("observeItem(queueId)"))
        assertFalse(viewModel.contains("SollGateway"))
        assertFalse(viewModel.contains("gateway.importFeedLink"))
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
