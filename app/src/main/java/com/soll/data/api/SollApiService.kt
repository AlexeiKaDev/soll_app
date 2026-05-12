package com.soll.data.api

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import okhttp3.MultipartBody

interface SollApiService {
    @GET("api/v1/health")
    suspend fun getHealth(
        @Header("Authorization") authorization: String? = null,
    ): SollHealthResponse

    @GET("api/v1/tasks/board")
    suspend fun getTaskBoard(
        @Header("Authorization") authorization: String? = null,
        @Query("import_daily") importDaily: Boolean = true,
        @Query("import_project_opportunities") importProjectOpportunities: Boolean = true,
    ): SollTaskBoardResponse

    @GET("api/v1/android/sync-status")
    suspend fun getAndroidSyncStatus(
        @Header("Authorization") authorization: String? = null,
    ): AndroidSyncStatusResponse

    @GET("api/v1/briefing/latest")
    suspend fun getLatestBriefing(
        @Header("Authorization") authorization: String? = null,
    ): SollBriefingResponse

    @POST("api/v1/raw/create")
    suspend fun createRawFile(
        @Header("Authorization") authorization: String? = null,
        @Body request: CreateRawFileRequest,
    ): RawFileResponse

    @Multipart
    @POST("api/v1/raw/upload")
    suspend fun uploadRawFile(
        @Header("Authorization") authorization: String? = null,
        @Part file: MultipartBody.Part,
    ): RawUploadResponse

    @POST("api/v1/tasks/{task_id}/status/{status}")
    suspend fun setTaskStatus(
        @Header("Authorization") authorization: String? = null,
        @Path("task_id") taskId: String,
        @Path("status") status: String,
    ): SollTaskResponse

    @POST("api/v1/tasks/{task_id}/today")
    suspend fun moveTaskToToday(
        @Header("Authorization") authorization: String? = null,
        @Path("task_id") taskId: String,
    ): SollTaskResponse

    @POST("api/v1/tasks/{task_id}/done")
    suspend fun completeTask(
        @Header("Authorization") authorization: String? = null,
        @Path("task_id") taskId: String,
    ): SollTaskResponse

    @POST("api/v1/tasks/{task_id}/defer")
    suspend fun deferTask(
        @Header("Authorization") authorization: String? = null,
        @Path("task_id") taskId: String,
    ): SollTaskResponse

    @POST("api/v1/tasks/{task_id}/reject")
    suspend fun rejectTask(
        @Header("Authorization") authorization: String? = null,
        @Path("task_id") taskId: String,
    ): SollTaskResponse

    @GET("api/v1/books/status")
    suspend fun getBookStatus(
        @Header("Authorization") authorization: String? = null,
    ): SollBookStatusResponse

    @POST("api/v1/books/select")
    suspend fun selectBook(
        @Header("Authorization") authorization: String? = null,
        @Body request: BookSelectRequest,
    ): BookSelectResponse

    @POST("api/v1/books/download")
    suspend fun downloadBook(
        @Header("Authorization") authorization: String? = null,
        @Body request: BookDownloadRequest,
    ): BookDownloadResponse

    @POST("api/v1/books/download-preferred")
    suspend fun downloadPreferredBook(
        @Header("Authorization") authorization: String? = null,
    ): BookDownloadResponse

    @POST("api/v1/books/download-selected")
    suspend fun downloadSelectedBooks(
        @Header("Authorization") authorization: String? = null,
        @Body request: BookDownloadSelectedRequest,
    ): BookBatchDownloadResponse

    @POST("api/v1/books/process-downloaded")
    suspend fun processDownloadedBook(
        @Header("Authorization") authorization: String? = null,
        @Body request: BookProcessDownloadedRequest,
    ): BookProcessResponse

    @POST("api/v1/books/cancel")
    suspend fun cancelBookSession(
        @Header("Authorization") authorization: String? = null,
    ): BookActionResponse

    @GET("api/v1/books/results")
    suspend fun getCurrentBookResults(
        @Header("Authorization") authorization: String? = null,
    ): BookCurrentResultsResponse

    @POST("api/v1/assistant/ask")
    suspend fun askAssistant(
        @Header("Authorization") authorization: String? = null,
        @Body request: AssistantAskRequest,
    ): AssistantAskResponse

    @GET("api/v1/gadgets")
    suspend fun getGadgets(
        @Header("Authorization") authorization: String? = null,
    ): List<GadgetSnapshotResponse>

    @GET("api/v1/gadgets/{gadget_id}/latest")
    suspend fun getGadgetLatest(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
    ): GadgetSnapshotResponse

    @GET("api/v1/gadgets/{gadget_id}/history")
    suspend fun getGadgetHistory(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Query("metric") metric: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("limit") limit: Int = 200,
    ): GadgetHistoryResponse

    @GET("api/v1/gadgets/{gadget_id}/events")
    suspend fun getGadgetEvents(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Query("limit") limit: Int = 50,
    ): List<GadgetEventResponse>

    @POST("api/v1/gadgets/{gadget_id}/commands")
    suspend fun createGadgetCommand(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Body request: GadgetCommandCreateRequest,
    ): GadgetCommandResponse
}

data class CreateRawFileRequest(
    val filename: String,
    val content: String = "",
)

data class SollHealthResponse(
    val status: String,
    val version: String = "",
    @Json(name = "scheduler_running")
    val schedulerRunning: Boolean = false,
    @Json(name = "vault_accessible")
    val vaultAccessible: Boolean = false,
    @Json(name = "jobs_count")
    val jobsCount: Int = 0,
    @Json(name = "checked_at")
    val checkedAt: String? = null,
)

data class AndroidSyncStatusResponse(
    @Json(name = "server_time")
    val serverTime: String = "",
    val health: SollHealthResponse = SollHealthResponse(status = "unknown"),
    val tasks: SollTaskBoardResponse = SollTaskBoardResponse(),
    val device: SollDeviceResponse? = null,
    val briefing: SollBriefingResponse? = null,
    val warnings: List<String> = emptyList(),
)

data class SollDeviceResponse(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val scopes: List<String> = emptyList(),
    @Json(name = "last_seen_at")
    val lastSeenAt: String? = null,
)

data class SollBriefingResponse(
    val filename: String = "",
    val path: String = "",
    val content: String = "",
    @Json(name = "created_at")
    val createdAt: String = "",
)

data class SollTaskBoardResponse(
    val today: List<SollTaskResponse> = emptyList(),
    val inbox: List<SollTaskResponse> = emptyList(),
    val stale: List<SollTaskResponse> = emptyList(),
    @Json(name = "done_recent")
    val doneRecent: List<SollTaskResponse> = emptyList(),
)

data class SollTaskResponse(
    val id: String,
    val title: String,
    val description: String = "",
    @Json(name = "source_ref")
    val sourceRef: String = "",
    @Json(name = "project_id")
    val projectId: String? = null,
    @Json(name = "project_name")
    val projectName: String? = null,
    val status: String = "",
    val priority: String = "",
    @Json(name = "created_at")
    val createdAt: String = "",
    @Json(name = "updated_at")
    val updatedAt: String = "",
    @Json(name = "due_date")
    val dueDate: String? = null,
    val tags: List<String> = emptyList(),
)

data class RawFileResponse(
    val message: String = "",
    val filename: String,
    val path: String = "",
)

data class RawUploadResponse(
    val message: String = "",
    val filename: String,
    val path: String = "",
    val size: Long = 0L,
)

data class BookSelectRequest(
    val number: Int,
)

data class BookDownloadRequest(
    val format: String,
)

data class BookDownloadSelectedRequest(
    val numbers: List<Int>,
    @Json(name = "process_after")
    val processAfter: Boolean = false,
)

data class BookProcessDownloadedRequest(
    @Json(name = "file_path")
    val filePath: String? = null,
)

data class SollBookStatusResponse(
    @Json(name = "userbot_running")
    val userbotRunning: Boolean = false,
    val session: BookStatusSessionResponse = BookStatusSessionResponse(),
)

data class BookStatusSessionResponse(
    val active: Boolean = false,
    val query: String? = null,
    val state: String? = null,
    @Json(name = "results_count")
    val resultsCount: Int = 0,
    @Json(name = "total_results_count")
    val totalResultsCount: Int = 0,
    @Json(name = "duplicates_count")
    val duplicatesCount: Int = 0,
    @Json(name = "max_results")
    val maxResults: Int = 50,
    @Json(name = "request_mode")
    val requestMode: String? = null,
    @Json(name = "selected_book")
    val selectedBook: String? = null,
    @Json(name = "formats_count")
    val formatsCount: Int = 0,
    @Json(name = "downloaded_files")
    val downloadedFiles: List<BookDownloadedFileResponse> = emptyList(),
    @Json(name = "created_at")
    val createdAt: String? = null,
)

data class BookCurrentResultsResponse(
    val active: Boolean = false,
    val query: String? = null,
    val state: String? = null,
    val results: List<BookResultResponse> = emptyList(),
    @Json(name = "selected_book")
    val selectedBook: BookIdentityResponse? = null,
    val formats: List<BookFormatResponse> = emptyList(),
    @Json(name = "downloaded_files")
    val downloadedFiles: List<BookDownloadedFileResponse> = emptyList(),
)

data class BookSelectResponse(
    val success: Boolean = false,
    val book: BookIdentityResponse? = null,
    val formats: List<BookFormatResponse> = emptyList(),
    @Json(name = "preferred_formats")
    val preferredFormats: List<BookFormatResponse> = emptyList(),
    @Json(name = "raw_response")
    val rawResponse: String? = null,
)

data class BookDownloadResponse(
    val success: Boolean = false,
    @Json(name = "duplicate_skipped")
    val duplicateSkipped: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val book: BookIdentityResponse? = null,
    val format: String? = null,
    @Json(name = "file_path")
    val filePath: String? = null,
    @Json(name = "metadata_path")
    val metadataPath: String? = null,
    @Json(name = "dedupe_key")
    val dedupeKey: String? = null,
    @Json(name = "raw_response")
    val rawResponse: String? = null,
)

data class BookBatchDownloadResponse(
    val success: Boolean = false,
    val requested: Int = 0,
    val downloaded: Int = 0,
    val failed: Int = 0,
    val results: List<BookBatchDownloadItemResponse> = emptyList(),
)

data class BookBatchDownloadItemResponse(
    val number: Int = 0,
    val success: Boolean = false,
    val error: String? = null,
    @Json(name = "duplicate_skipped")
    val duplicateSkipped: Boolean = false,
    val message: String? = null,
    val book: BookIdentityResponse? = null,
    val format: String? = null,
    @Json(name = "file_path")
    val filePath: String? = null,
    @Json(name = "metadata_path")
    val metadataPath: String? = null,
    @Json(name = "dedupe_key")
    val dedupeKey: String? = null,
    @Json(name = "process_result")
    val processResult: BookProcessResponse? = null,
)

data class BookProcessResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val file: String? = null,
    @Json(name = "processed_file")
    val processedFile: String? = null,
    @Json(name = "wiki_entry")
    val wikiEntry: String? = null,
    @Json(name = "wiki_updated")
    val wikiUpdated: List<String> = emptyList(),
    @Json(name = "already_processed")
    val alreadyProcessed: Boolean = false,
    @Json(name = "fallback_used")
    val fallbackUsed: Boolean = false,
    @Json(name = "fallback_reason")
    val fallbackReason: String? = null,
    @Json(name = "archive_signature")
    val archiveSignature: String? = null,
)

data class BookActionResponse(
    val success: Boolean = false,
    val message: String? = null,
)

data class BookResultResponse(
    val number: Int = 0,
    val title: String = "",
    val author: String = "",
    @Json(name = "dedupe_key")
    val dedupeKey: String = "",
    val alternatives: List<BookAlternativeResponse> = emptyList(),
)

data class BookAlternativeResponse(
    val number: Int? = null,
    val title: String? = null,
    val author: String? = null,
)

data class BookIdentityResponse(
    val title: String? = null,
    val author: String? = null,
)

data class BookFormatResponse(
    val type: String = "",
    val size: String = "",
)

data class BookDownloadedFileResponse(
    @Json(name = "file_path")
    val filePath: String? = null,
    @Json(name = "metadata_path")
    val metadataPath: String? = null,
    @Json(name = "dedupe_key")
    val dedupeKey: String? = null,
    val book: BookIdentityResponse? = null,
    val format: String? = null,
    @Json(name = "duplicate_skipped")
    val duplicateSkipped: Boolean = false,
    @Json(name = "downloaded_at")
    val downloadedAt: String? = null,
)

data class AssistantAskRequest(
    val question: String,
    @Json(name = "allow_wiki_updates")
    val allowWikiUpdates: Boolean = false,
)

data class AssistantAskResponse(
    val question: String = "",
    val answer: String = "",
    val confidence: String = "",
    @Json(name = "used_topics")
    val usedTopics: List<String> = emptyList(),
    val contradictions: List<String> = emptyList(),
    val gaps: List<String> = emptyList(),
    @Json(name = "output_file")
    val outputFile: String? = null,
    val path: String? = null,
    @Json(name = "wiki_updates_applied")
    val wikiUpdatesApplied: List<String> = emptyList(),
)

data class GadgetSnapshotResponse(
    val id: String,
    val name: String = "",
    @Json(name = "profile_id")
    val profileId: String = "",
    val enabled: Boolean = true,
    @Json(name = "last_heartbeat_at")
    val lastHeartbeatAt: String? = null,
    @Json(name = "last_telemetry_at")
    val lastTelemetryAt: String? = null,
    @Json(name = "latest_telemetry")
    val latestTelemetry: Map<String, Any?> = emptyMap(),
    @Json(name = "latest_event_type")
    val latestEventType: String? = null,
    @Json(name = "latest_event_summary")
    val latestEventSummary: String? = null,
    val stale: Boolean = true,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
)

data class GadgetEventResponse(
    val id: String = "",
    @Json(name = "gadget_id")
    val gadgetId: String = "",
    val type: String = "",
    val summary: String = "",
    val payload: Map<String, Any?> = emptyMap(),
    @Json(name = "created_at")
    val createdAt: String = "",
)

data class GadgetHistoryResponse(
    @Json(name = "gadget_id")
    val gadgetId: String = "",
    val metric: String? = null,
    val points: List<GadgetHistoryPointResponse> = emptyList(),
)

data class GadgetHistoryPointResponse(
    val metric: String = "",
    val value: Any? = null,
    @Json(name = "created_at")
    val createdAt: String = "",
)

data class GadgetCommandCreateRequest(
    val command: String,
    val params: Map<String, Any?> = emptyMap(),
    @Json(name = "ttl_seconds")
    val ttlSeconds: Int = 60,
)

data class GadgetCommandResponse(
    val id: String = "",
    @Json(name = "gadget_id")
    val gadgetId: String = "",
    val command: String = "",
    val status: String = "",
    val reason: String = "",
    @Json(name = "created_at")
    val createdAt: String = "",
)
