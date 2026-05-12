package com.soll.domain.soll

import android.net.Uri
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudHistory
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.metacoordinator.MetaCoordinatorRequest
import com.soll.domain.metacoordinator.MetaCoordinatorResponse

data class SollHealth(
    val status: String,
    val schedulerRunning: Boolean,
    val vaultAccessible: Boolean,
    val jobsCount: Int,
    val checkedAt: String? = null,
)

data class SollBriefing(
    val filename: String,
    val path: String,
    val content: String,
    val createdAt: String,
)

data class SollDevice(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val scopes: List<String>,
    val lastSeenAt: String?,
)

data class SollAndroidSyncStatus(
    val serverTime: String,
    val health: SollHealth,
    val tasks: SollTaskBoard,
    val device: SollDevice?,
    val briefing: SollBriefing?,
    val warnings: List<String>,
    val fromCache: Boolean = false,
    val cachedAtMillis: Long? = null,
)

data class SollTaskBoard(
    val today: List<SollTask>,
    val inbox: List<SollTask>,
    val stale: List<SollTask>,
    val doneRecent: List<SollTask>,
) {
    val openCount: Int
        get() = today.size + inbox.size + stale.size
}

data class SollTask(
    val id: String,
    val title: String,
    val description: String,
    val sourceRef: String,
    val projectName: String?,
    val status: String,
    val priority: String,
    val dueDate: String?,
    val tags: List<String>,
)

data class SollRawNote(
    val filename: String,
    val path: String,
    val message: String,
)

data class SollRawUpload(
    val filename: String,
    val path: String,
    val size: Long,
    val message: String,
)

data class SollBookStatus(
    val userbotRunning: Boolean,
    val session: SollBookSession,
)

data class SollBookSession(
    val active: Boolean,
    val query: String?,
    val state: String?,
    val resultsCount: Int,
    val totalResultsCount: Int,
    val duplicatesCount: Int,
    val maxResults: Int,
    val requestMode: String?,
    val selectedBook: String?,
    val formatsCount: Int,
    val downloadedFiles: List<SollBookDownloadedFile>,
    val createdAt: String?,
)

data class SollBookCurrentResults(
    val active: Boolean,
    val query: String?,
    val state: String?,
    val results: List<SollBookResult>,
    val selectedBook: SollBookIdentity?,
    val formats: List<SollBookFormat>,
    val downloadedFiles: List<SollBookDownloadedFile>,
)

data class SollBookSelection(
    val book: SollBookIdentity?,
    val formats: List<SollBookFormat>,
    val preferredFormats: List<SollBookFormat>,
    val rawResponse: String?,
)

data class SollBookDownload(
    val duplicateSkipped: Boolean,
    val message: String?,
    val error: String?,
    val book: SollBookIdentity?,
    val format: String?,
    val filePath: String?,
    val metadataPath: String?,
    val dedupeKey: String?,
    val rawResponse: String?,
)

data class SollBookBatchDownload(
    val requested: Int,
    val downloaded: Int,
    val failed: Int,
    val results: List<SollBookBatchItem>,
)

data class SollBookBatchItem(
    val number: Int,
    val success: Boolean,
    val error: String?,
    val duplicateSkipped: Boolean,
    val message: String?,
    val book: SollBookIdentity?,
    val format: String?,
    val filePath: String?,
    val metadataPath: String?,
    val dedupeKey: String?,
    val processResult: SollBookProcessResult?,
)

data class SollBookProcessResult(
    val success: Boolean,
    val message: String?,
    val error: String?,
    val file: String?,
    val processedFile: String?,
    val wikiEntry: String?,
    val wikiUpdated: List<String>,
    val alreadyProcessed: Boolean,
    val fallbackUsed: Boolean,
    val fallbackReason: String?,
    val archiveSignature: String?,
)

data class SollBookActionResult(
    val success: Boolean,
    val message: String?,
)

data class SollBookResult(
    val number: Int,
    val title: String,
    val author: String,
    val dedupeKey: String,
    val alternatives: List<SollBookAlternative>,
)

data class SollBookAlternative(
    val number: Int?,
    val title: String?,
    val author: String?,
)

data class SollBookIdentity(
    val title: String?,
    val author: String?,
)

data class SollBookFormat(
    val type: String,
    val size: String,
)

data class SollBookDownloadedFile(
    val filePath: String?,
    val metadataPath: String?,
    val dedupeKey: String?,
    val book: SollBookIdentity?,
    val format: String?,
    val duplicateSkipped: Boolean,
    val downloadedAt: String?,
)

interface SollGateway {
    suspend fun getHealth(): Result<SollHealth>
    suspend fun getTaskBoard(): Result<SollTaskBoard>
    suspend fun getAndroidSyncStatus(): Result<SollAndroidSyncStatus>
    suspend fun createRawNote(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
    ): Result<SollRawNote>

    suspend fun uploadRawFile(uri: Uri): Result<SollRawUpload>
    suspend fun setTaskStatus(taskId: String, status: String): Result<SollTask>
    suspend fun moveTaskToToday(taskId: String): Result<SollTask>
    suspend fun completeTask(taskId: String): Result<SollTask>
    suspend fun deferTask(taskId: String): Result<SollTask>
    suspend fun rejectTask(taskId: String): Result<SollTask>
    suspend fun getBookStatus(): Result<SollBookStatus>
    suspend fun getCurrentBookResults(): Result<SollBookCurrentResults>
    suspend fun selectBook(number: Int): Result<SollBookSelection>
    suspend fun downloadBook(format: String): Result<SollBookDownload>
    suspend fun downloadPreferredBook(): Result<SollBookDownload>
    suspend fun downloadSelectedBooks(
        numbers: List<Int>,
        processAfter: Boolean,
    ): Result<SollBookBatchDownload>

    suspend fun processDownloadedBook(filePath: String? = null): Result<SollBookProcessResult>
    suspend fun cancelBookSession(): Result<SollBookActionResult>
    suspend fun askMetaCoordinator(request: MetaCoordinatorRequest): Result<MetaCoordinatorResponse>
    suspend fun getGadgetSnapshots(): Result<List<GadgetCloudSnapshot>>
    suspend fun getGadgetLatest(gadgetId: String): Result<GadgetCloudSnapshot>
    suspend fun getGadgetHistory(
        gadgetId: String,
        metric: String? = null,
        from: String? = null,
        to: String? = null,
        limit: Int = 200,
    ): Result<GadgetCloudHistory>

    suspend fun getGadgetEvents(gadgetId: String, limit: Int = 50): Result<List<GadgetCloudEvent>>
}
