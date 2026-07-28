package com.soll.data.api

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.Path
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Query
import okhttp3.MultipartBody
import okhttp3.ResponseBody

interface SollApiService {
    @GET("api/v1/health")
    suspend fun getHealth(
        @Header("Authorization") authorization: String? = null,
    ): SollHealthResponse

    @GET("api/v1/tasks/board")
    suspend fun getTaskBoard(
        @Header("Authorization") authorization: String? = null,
        @Query("import_daily") importDaily: Boolean = false,
        @Query("import_project_opportunities") importProjectOpportunities: Boolean = true,
        @Query("limit_per_section") limitPerSection: Int? = null,
        @Query("include_counts") includeCounts: Boolean = false,
    ): SollTaskBoardResponse

    @GET("api/v1/daily/tasks/today")
    suspend fun getTodayDailyTasks(
        @Header("Authorization") authorization: String? = null,
    ): DailyTaskListResponse

    @POST("api/v1/daily/tasks/today")
    suspend fun addTodayDailyTask(
        @Header("Authorization") authorization: String? = null,
        @Body request: DailyTaskCreateRequest,
    ): DailyTaskListResponse

    @PATCH("api/v1/daily/tasks/today/{task_id}")
    suspend fun updateTodayDailyTask(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
        @Body request: DailyTaskUpdateRequest,
    ): DailyTaskListResponse

    @DELETE("api/v1/daily/tasks/today/{task_id}")
    suspend fun deleteTodayDailyTask(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): DailyTaskListResponse

    @GET("api/v1/daily/tasks/today/{task_id}/detail")
    suspend fun getTodayDailyTaskDetail(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): DailyTaskDetailResponse

    @POST("api/v1/daily/tasks/today/{task_id}/research")
    suspend fun researchTodayDailyTask(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): DailyTaskDetailResponse

    @Multipart
    @POST("api/v1/daily/tasks/today/{task_id}/attachments")
    suspend fun uploadTodayDailyTaskAttachment(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
        @Part file: MultipartBody.Part,
    ): DailyTaskAttachmentResponse

    @GET("api/v1/android/sync-status")
    suspend fun getAndroidSyncStatus(
        @Header("Authorization") authorization: String? = null,
    ): AndroidSyncStatusResponse

    @POST("api/v1/android/push-token")
    suspend fun registerAndroidPushToken(
        @Header("Authorization") authorization: String? = null,
        @Body request: AndroidPushTokenRequest,
    ): AndroidPushTokenResponse

    @GET("api/v1/android/location")
    suspend fun getAndroidLocation(
        @Header("Authorization") authorization: String? = null,
    ): AndroidLocationStatusResponse

    @POST("api/v1/android/location")
    suspend fun updateAndroidLocation(
        @Header("Authorization") authorization: String? = null,
        @Body request: AndroidLocationUpdateRequest,
    ): AndroidLocationStatusResponse

    @GET("api/v1/chat/sessions")
    suspend fun getChatSessions(
        @Header("Authorization") authorization: String? = null,
        @Query("limit") limit: Int = 50,
    ): ChatSessionsResponse

    @POST("api/v1/chat/sessions")
    suspend fun createChatSession(
        @Header("Authorization") authorization: String? = null,
        @Body request: ChatSessionCreateRequest,
    ): ChatSessionCreateResponse

    @GET("api/v1/chat/sessions/{session_id}")
    suspend fun getChatSession(
        @Header("Authorization") authorization: String? = null,
        @Path("session_id") sessionId: String,
        @Query("limit") limit: Int? = null,
        @Query("before_id") beforeId: Long? = null,
        @Query("after_id") afterId: Long? = null,
    ): ChatSessionMessagesResponse

    @POST("api/v1/chat/messages")
    suspend fun sendChatMessage(
        @Header("Authorization") authorization: String? = null,
        @Body request: ChatMessageCreateRequest,
    ): ChatMessageCreateResponse

    @POST("api/v1/chat/turn")
    suspend fun sendChatTurn(
        @Header("Authorization") authorization: String? = null,
        @Body request: ChatTurnRequest,
    ): ChatTurnResponse

    @POST("api/v1/voice/synthesize")
    suspend fun synthesizeVoice(
        @Header("Authorization") authorization: String? = null,
        @Body request: VoiceSynthesisRequest,
    ): ResponseBody

    @POST("api/v1/chat/actions/{action_id}/execute")
    suspend fun executeChatAction(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "action_id", encoded = true) actionId: String,
        @Body request: ChatActionExecuteRequest,
    ): ChatActionExecuteResponse

    @POST("api/v1/devices/{device_id}/challenge")
    suspend fun createDeviceChallenge(
        @Path("device_id") deviceId: String,
    ): DeviceChallengeResponse

    @POST("api/v1/devices/token")
    suspend fun issueDeviceToken(
        @Body request: DeviceTokenRequest,
    ): DeviceTokenResponse

    @POST("api/v1/devices/token/refresh")
    suspend fun refreshDeviceToken(
        @Header("Authorization") authorization: String? = null,
    ): DeviceTokenResponse

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
        @Path(value = "task_id", encoded = true) taskId: String,
        @Path("status") status: String,
    ): SollTaskMutationResponse

    @PATCH("api/v1/tasks/{task_id}")
    suspend fun updateTask(
        @Header("Authorization") authorization: String?,
        @Path("task_id", encoded = true) taskId: String,
        @Body request: TaskUpdateRequest,
    ): SollTaskMutationResponse

    @POST("api/v1/tasks/{task_id}/today")
    suspend fun moveTaskToToday(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): SollTaskMutationResponse

    @POST("api/v1/tasks/{task_id}/done")
    suspend fun completeTask(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): SollTaskMutationResponse

    @POST("api/v1/tasks/{task_id}/defer")
    suspend fun deferTask(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): SollTaskMutationResponse

    @POST("api/v1/tasks/{task_id}/reject")
    suspend fun rejectTask(
        @Header("Authorization") authorization: String? = null,
        @Path(value = "task_id", encoded = true) taskId: String,
    ): SollTaskMutationResponse

    @GET("api/v1/tasks/graph")
    suspend fun getTaskGraph(
        @Header("Authorization") authorization: String? = null,
        @Query("include_done") includeDone: Boolean = false,
        @Query("max_nodes") maxNodes: Int = 500,
    ): TaskGraphResponse

    @GET("api/v1/insights/learning")
    suspend fun getLearningItems(
        @Header("Authorization") authorization: String? = null,
        @Query("status") status: String? = "pending",
        @Query("limit") limit: Int = 80,
    ): LearningItemsResponse

    @PATCH("api/v1/insights/learning/{item_id}")
    suspend fun updateLearningItemStatus(
        @Header("Authorization") authorization: String? = null,
        @Path("item_id") itemId: String,
        @Body request: LearningItemStatusRequest,
    ): LearningItemUpdateResponse

    @POST("api/v1/insights/learning/{item_id}/task")
    suspend fun createTaskFromLearningItem(
        @Header("Authorization") authorization: String? = null,
        @Path("item_id") itemId: String,
        @Body request: LearningItemTaskRequest = LearningItemTaskRequest(),
    ): LearningItemTaskResponse

    @GET("api/v1/roadmap/")
    suspend fun getRoadmap(
        @Header("Authorization") authorization: String? = null,
    ): RoadmapResponse

    @POST("api/v1/roadmap/stages/{stage_id}/lines")
    suspend fun addRoadmapLine(
        @Header("Authorization") authorization: String? = null,
        @Path("stage_id") stageId: String,
        @Body request: RoadmapLineRequest,
    ): RoadmapResponse

    @PATCH("api/v1/roadmap/stages/{stage_id}/lines/{line}")
    suspend fun updateRoadmapLine(
        @Header("Authorization") authorization: String? = null,
        @Path("stage_id") stageId: String,
        @Path("line") line: String,
        @Body request: RoadmapLineUpdateRequest,
    ): RoadmapResponse

    @DELETE("api/v1/roadmap/stages/{stage_id}/lines/{line}")
    suspend fun deleteRoadmapLine(
        @Header("Authorization") authorization: String? = null,
        @Path("stage_id") stageId: String,
        @Path("line") line: String,
    ): RoadmapResponse

    @POST("api/v1/roadmap/stages/{stage_id}/lines/{line}/task")
    suspend fun createTaskFromRoadmapLine(
        @Header("Authorization") authorization: String? = null,
        @Path("stage_id") stageId: String,
        @Path("line") line: String,
        @Body request: RoadmapLineTaskRequest = RoadmapLineTaskRequest(),
    ): RoadmapLineTaskResponse

    @GET("api/v1/sources/")
    suspend fun listSources(
        @Header("Authorization") authorization: String? = null,
        @Query("scope") scope: String = "project_soll",
    ): List<MonitoredSourceResponse>

    @GET("api/v1/sources/{source_id}/items")
    suspend fun listSourceItems(
        @Header("Authorization") authorization: String? = null,
        @Path("source_id") sourceId: String,
        @Query("limit") limit: Int = 20,
    ): List<SourceItemResponse>

    @GET("api/v1/sources/{source_id}/items/page")
    suspend fun listSourceItemsPage(
        @Header("Authorization") authorization: String? = null,
        @Path("source_id") sourceId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 50,
    ): SourceItemsPageResponse

    @POST("api/v1/sources/{source_id}/items/{item_id}/task")
    suspend fun createTaskFromSourceItem(
        @Header("Authorization") authorization: String? = null,
        @Path("source_id") sourceId: String,
        @Path("item_id") itemId: String,
        @Body request: SourceItemTaskRequest = SourceItemTaskRequest(),
    ): SourceItemTaskResponse

    @POST("api/v1/sources/")
    suspend fun createSource(
        @Header("Authorization") authorization: String? = null,
        @Body request: MonitoredSourceCreateRequest,
    ): MonitoredSourceResponse

    @PUT("api/v1/sources/{source_id}")
    suspend fun updateSource(
        @Header("Authorization") authorization: String? = null,
        @Path("source_id") sourceId: String,
        @Body request: MonitoredSourceUpdateRequest,
    ): MonitoredSourceResponse

    @DELETE("api/v1/sources/{source_id}")
    suspend fun deleteSource(
        @Header("Authorization") authorization: String? = null,
        @Path("source_id") sourceId: String,
    ): Map<String, Any?>

    @POST("api/v1/sources/{source_id}/check")
    suspend fun checkSource(
        @Header("Authorization") authorization: String? = null,
        @Path("source_id") sourceId: String,
        @Query("force") force: Boolean = true,
    ): SourceCheckResponse

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

    @GET("api/v1/protocol/schema")
    suspend fun getProtocolSchema(
        @Header("Authorization") authorization: String? = null,
    ): SollProtocolSchemaResponse

    @GET("api/v1/mesh/status")
    suspend fun getMeshStatus(
        @Header("Authorization") authorization: String? = null,
    ): MeshStatusResponse

    @GET("api/v1/mesh/outbox")
    suspend fun getMeshOutbox(
        @Header("Authorization") authorization: String? = null,
        @Query("limit") limit: Int = 20,
    ): MeshOutboxListResponse

    @GET("api/v1/mesh/outbox/next")
    suspend fun claimNextMeshOutbox(
        @Header("Authorization") authorization: String? = null,
        @Query("to_peer") toPeer: String? = null,
    ): MeshOutboxClaimResponse

    @POST("api/v1/mesh/outbox/{outbound_id}/ack")
    suspend fun ackMeshOutbox(
        @Header("Authorization") authorization: String? = null,
        @Path("outbound_id") outboundId: String,
    ): MeshOutboxItemResponse

    @POST("api/v1/mesh/outbox/{outbound_id}/attempt")
    suspend fun markMeshOutboxAttempt(
        @Header("Authorization") authorization: String? = null,
        @Path("outbound_id") outboundId: String,
        @Body request: MeshOutboxAttemptRequest,
    ): MeshOutboxItemResponse

    @POST("api/v1/mesh/outbox/{outbound_id}/retry")
    suspend fun retryMeshOutbox(
        @Header("Authorization") authorization: String? = null,
        @Path("outbound_id") outboundId: String,
    ): MeshOutboxItemResponse

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

    @GET("api/v1/gadgets/{gadget_id}/commands")
    suspend fun getGadgetCommands(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Query("limit") limit: Int = 20,
    ): List<GadgetCommandResponse>

    @POST("api/v1/gadgets/{gadget_id}/commands")
    suspend fun createGadgetCommand(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Body request: GadgetCommandCreateRequest,
    ): GadgetCommandResponse

    @POST("api/v1/gadgets/{gadget_id}/commands/claim")
    suspend fun claimGadgetCommand(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Body request: GadgetCommandClaimRequest,
    ): GadgetCommandResponse?

    @POST("api/v1/gadgets/{gadget_id}/commands/{command_id}/ack")
    suspend fun ackGadgetCommand(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Path("command_id") commandId: String,
        @Body request: GadgetCommandAckRequest,
    ): GadgetCommandResponse

    @POST("api/v1/gadgets/{gadget_id}/commands/{command_id}/result")
    suspend fun postGadgetCommandResult(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Path("command_id") commandId: String,
        @Body request: GadgetCommandResultRequest,
    ): GadgetCommandResponse

    @POST("api/v1/gadgets/{gadget_id}/commands/{command_id}/manual-result")
    suspend fun postManualGadgetCommandResult(
        @Header("Authorization") authorization: String? = null,
        @Path("gadget_id") gadgetId: String,
        @Path("command_id") commandId: String,
        @Body request: GadgetCommandResultRequest,
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
    @Json(name = "android_push")
    val androidPush: AndroidPushHealthResponse = AndroidPushHealthResponse(),
    @Json(name = "checked_at")
    val checkedAt: String? = null,
)

data class AndroidPushHealthResponse(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    @Json(name = "token_count")
    val tokenCount: Int = 0,
)

data class AndroidSyncStatusResponse(
    @Json(name = "server_time")
    val serverTime: String = "",
    val health: SollHealthResponse = SollHealthResponse(status = "unknown"),
    val tasks: SollTaskBoardResponse = SollTaskBoardResponse(),
    val device: SollDeviceResponse? = null,
    val node: SollNodeIdentityResponse = SollNodeIdentityResponse(),
    @Json(name = "active_nodes")
    val activeNodes: Map<String, SollNodeIdentityResponse> = emptyMap(),
    val briefing: SollBriefingResponse? = null,
    val chat: AndroidChatSyncResponse = AndroidChatSyncResponse(),
    val protocol: AndroidProtocolBootstrapResponse? = null,
    val workspace: AndroidWorkspaceSyncResponse = AndroidWorkspaceSyncResponse(),
    val warnings: List<String> = emptyList(),
)

data class AndroidWorkspaceSyncResponse(
    val insights: LearningItemsResponse = LearningItemsResponse(),
    val sources: List<MonitoredSourceResponse> = emptyList(),
    @Json(name = "source_items_by_source")
    val sourceItemsBySource: Map<String, List<SourceItemResponse>> = emptyMap(),
)

data class SollNodeIdentityResponse(
    @Json(name = "node_id")
    val nodeId: String = "",
    @Json(name = "node_name")
    val nodeName: String = "",
    @Json(name = "node_role")
    val nodeRole: String = "",
    @Json(name = "is_primary")
    val isPrimary: Boolean = false,
    val priority: Int = 0,
    val capabilities: List<String> = emptyList(),
    val active: Boolean = false,
    @Json(name = "last_seen_at")
    val lastSeenAt: String? = null,
)

data class AndroidChatSyncResponse(
    @Json(name = "primary_session_id")
    val primarySessionId: String = "soll-main",
    @Json(name = "recent_sessions")
    val recentSessions: List<ChatSessionSummaryResponse> = emptyList(),
    @Json(name = "recent_messages")
    val recentMessages: List<ChatMessageResponse> = emptyList(),
    @Json(name = "last_message_id")
    val lastMessageId: Long? = null,
    @Json(name = "unread_count")
    val unreadCount: Int = 0,
    @Json(name = "pending_actions_count")
    val pendingActionsCount: Int = 0,
    @Json(name = "encryption_required")
    val encryptionRequired: Boolean = false,
    @Json(name = "stream_endpoint")
    val streamEndpoint: String = "",
    val endpoints: Map<String, String> = emptyMap(),
)

data class AndroidPushTokenRequest(
    val token: String,
    val provider: String = "fcm",
    val platform: String = "android",
    @Json(name = "device_id")
    val deviceId: String? = null,
    @Json(name = "app_id")
    val appId: String? = null,
    @Json(name = "app_version")
    val appVersion: String? = null,
    @Json(name = "installation_id")
    val installationId: String? = null,
)

data class AndroidPushTokenResponse(
    val success: Boolean = false,
    val provider: String = "fcm",
    val enabled: Boolean = false,
    @Json(name = "token_count")
    val tokenCount: Int = 0,
    val reason: String? = null,
)

data class AndroidLocationUpdateRequest(
    @Json(name = "permission_granted")
    val permissionGranted: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "accuracy_meters")
    val accuracyMeters: Float? = null,
    @Json(name = "altitude_meters")
    val altitudeMeters: Double? = null,
    val provider: String = "android",
    @Json(name = "captured_at")
    val capturedAt: String? = null,
    val label: String = "",
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val locale: String = "",
    val reason: String = "",
)

data class AndroidLocationStatusResponse(
    val source: String = "android_app",
    val available: Boolean = false,
    @Json(name = "permission_granted")
    val permissionGranted: Boolean = false,
    @Json(name = "needs_android_location")
    val needsAndroidLocation: Boolean = true,
    val stale: Boolean = true,
    @Json(name = "age_seconds")
    val ageSeconds: Int? = null,
    @Json(name = "max_age_seconds")
    val maxAgeSeconds: Int = 1800,
    @Json(name = "device_id")
    val deviceId: String = "",
    @Json(name = "device_name")
    val deviceName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "accuracy_meters")
    val accuracyMeters: Float? = null,
    @Json(name = "altitude_meters")
    val altitudeMeters: Double? = null,
    val provider: String = "",
    val label: String = "",
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val locale: String = "",
    @Json(name = "captured_at")
    val capturedAt: String? = null,
    @Json(name = "received_at")
    val receivedAt: String? = null,
    val reason: String = "",
    val request: Map<String, Any?> = emptyMap(),
)

data class ChatSessionSummaryResponse(
    @Json(name = "session_id")
    val sessionId: String = "",
    val title: String = "",
    @Json(name = "updated_at")
    val updatedAt: String = "",
    @Json(name = "message_count")
    val messageCount: Int = 0,
)

data class ChatMessageResponse(
    val id: Long = 0,
    @Json(name = "session_id")
    val sessionId: String = "",
    val role: String = "",
    val content: String = "",
    @Json(name = "created_at")
    val createdAt: String = "",
    val metadata: Map<String, Any?> = emptyMap(),
)

data class ChatSessionsResponse(
    val sessions: List<ChatSessionSummaryResponse> = emptyList(),
)

data class ChatSessionCreateRequest(
    val title: String,
    @Json(name = "session_id")
    val sessionId: String? = null,
)

data class ChatSessionCreateResponse(
    @Json(name = "session_id")
    val sessionId: String = "",
    val title: String = "",
)

data class ChatSessionMessagesResponse(
    @Json(name = "session_id")
    val sessionId: String = "",
    val messages: List<ChatMessageResponse> = emptyList(),
)

data class SecurePayloadEnvelopeRequest(
    val algorithm: String = "AES-256-GCM",
    val nonce: String,
    val ciphertext: String,
    val aad: String = "",
    @Json(name = "key_id")
    val keyId: String = "",
)

data class ChatMessageCreateRequest(
    @Json(name = "session_id")
    val sessionId: String? = null,
    val role: String = "user",
    val content: String? = null,
    val metadata: Map<String, Any?>? = null,
    val encrypted: SecurePayloadEnvelopeRequest? = null,
)

data class ChatMessageCreateResponse(
    @Json(name = "session_id")
    val sessionId: String = "",
    val message: ChatMessageResponse = ChatMessageResponse(),
)

data class ChatTurnRequest(
    @Json(name = "session_id")
    val sessionId: String? = null,
    val content: String? = null,
    val metadata: Map<String, Any?>? = null,
    val encrypted: SecurePayloadEnvelopeRequest? = null,
    @Json(name = "run_assistant")
    val runAssistant: Boolean = true,
    @Json(name = "task_intake")
    val taskIntake: Boolean = true,
)

data class ChatTurnResponse(
    @Json(name = "session_id")
    val sessionId: String = "",
    val message: ChatMessageResponse = ChatMessageResponse(),
    val assistant: ChatMessageResponse? = null,
    @Json(name = "task_intake")
    val taskIntake: ChatTaskIntakeResponse? = null,
)

data class ChatTaskIntakeResponse(
    val acted: Boolean = false,
    val reason: String = "",
    val items: List<ChatTaskIntakeItemResponse> = emptyList(),
)

data class ChatTaskIntakeItemResponse(
    val action: String = "",
    @Json(name = "task_id")
    val taskId: String? = null,
    val title: String = "",
    @Json(name = "project_name")
    val projectName: String? = null,
)

data class ChatActionExecuteRequest(
    val action: String? = null,
    @Json(name = "task_id")
    val taskId: String? = null,
    @Json(name = "session_id")
    val sessionId: String? = null,
    val note: String = "",
    val metadata: Map<String, Any?>? = null,
    val encrypted: SecurePayloadEnvelopeRequest? = null,
)

data class ChatActionExecuteResponse(
    @Json(name = "action_id")
    val actionId: String = "",
    val action: String = "",
    @Json(name = "task_id")
    val taskId: String? = null,
    val status: String = "",
    val task: SollTaskResponse? = null,
)

data class SollDeviceResponse(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val scopes: List<String> = emptyList(),
    @Json(name = "last_seen_at")
    val lastSeenAt: String? = null,
)

data class DeviceChallengeResponse(
    @Json(name = "device_id")
    val deviceId: String = "",
    @Json(name = "challenge_id")
    val challengeId: String = "",
    val challenge: String = "",
    @Json(name = "expires_at")
    val expiresAt: String = "",
)

data class DeviceTokenRequest(
    @Json(name = "device_id")
    val deviceId: String,
    @Json(name = "challenge_id")
    val challengeId: String,
    val nonce: String,
    val signature: String,
)

data class DeviceTokenResponse(
    @Json(name = "access_token")
    val accessToken: String = "",
    @Json(name = "token_type")
    val tokenType: String = "",
    @Json(name = "expires_at")
    val expiresAt: String = "",
    @Json(name = "expires_in")
    val expiresIn: Int = 0,
)

data class AndroidProtocolBootstrapResponse(
    val version: String = "",
    val auth: SollProtocolAuthResponse = SollProtocolAuthResponse(),
    val transport: SollProtocolTransportResponse = SollProtocolTransportResponse(),
    @Json(name = "worker_contracts")
    val workerContracts: Map<String, SollProtocolWorkerContractResponse> = emptyMap(),
)

data class SollProtocolAuthResponse(
    val pairing: String = "",
    val challenge: String = "",
    val token: String = "",
    @Json(name = "token_refresh")
    val tokenRefresh: String = "",
    @Json(name = "token_type")
    val tokenType: String = "",
    @Json(name = "refresh_rule")
    val refreshRule: String = "",
)

data class SollProtocolTransportResponse(
    @Json(name = "recommended_auth")
    val recommendedAuth: String = "",
    val poll: List<String> = emptyList(),
    val push: List<String> = emptyList(),
)

data class SollProtocolWorkerContractResponse(
    val owner: String = "",
    val auth: String = "",
    @Json(name = "required_scopes")
    val requiredScopes: List<String> = emptyList(),
    @Json(name = "lease_seconds_default")
    val leaseSecondsDefault: Int = 0,
    @Json(name = "poll_interval_seconds")
    val pollIntervalSeconds: Int = 0,
    val lifecycle: List<String> = emptyList(),
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
    val blocked: List<SollTaskResponse> = emptyList(),
    val inbox: List<SollTaskResponse> = emptyList(),
    val stale: List<SollTaskResponse> = emptyList(),
    val deferred: List<SollTaskResponse> = emptyList(),
    @Json(name = "done_recent")
    val doneRecent: List<SollTaskResponse> = emptyList(),
    val counts: SollTaskBoardCountsResponse? = null,
    @Json(name = "limit_per_section")
    val limitPerSection: Int? = null,
)

data class DailyTaskListResponse(
    val date: String = "",
    @Json(name = "source_path")
    val sourcePath: String = "",
    val tasks: List<DailyTaskItemResponse> = emptyList(),
    @Json(name = "created_task_id")
    val createdTaskId: String? = null,
)

data class DailyTaskItemResponse(
    val id: String = "",
    val text: String = "",
    val done: Boolean = false,
    val line: Int = 0,
    val attachments: List<DailyTaskAttachmentResponse> = emptyList(),
)

data class DailyTaskAttachmentResponse(
    val id: String = "",
    @Json(name = "task_id")
    val taskId: String = "",
    val filename: String = "",
    @Json(name = "content_type")
    val contentType: String = "",
    val size: Long = 0,
    val path: String = "",
    @Json(name = "analysis_status")
    val analysisStatus: String = "",
    @Json(name = "analysis_summary")
    val analysisSummary: String = "",
    @Json(name = "ocr_text")
    val ocrText: String = "",
    @Json(name = "search_terms")
    val searchTerms: List<String> = emptyList(),
    @Json(name = "created_at")
    val createdAt: String = "",
)

data class DailyTaskGeoResponse(
    @Json(name = "location_label")
    val locationLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "accuracy_meters")
    val accuracyMeters: Float? = null,
    @Json(name = "captured_at")
    val capturedAt: String? = null,
)

data class DailyTaskResearchResponse(
    @Json(name = "task_id")
    val taskId: String = "",
    val query: String = "",
    val summary: String = "",
    @Json(name = "local_results")
    val localResults: List<Map<String, Any?>> = emptyList(),
    @Json(name = "source_results")
    val sourceResults: List<Map<String, Any?>> = emptyList(),
    @Json(name = "web_results")
    val webResults: List<Map<String, Any?>> = emptyList(),
    @Json(name = "created_at")
    val createdAt: String = "",
)

data class DailyTaskDetailResponse(
    val date: String = "",
    @Json(name = "source_path")
    val sourcePath: String = "",
    val task: DailyTaskItemResponse = DailyTaskItemResponse(),
    val geo: DailyTaskGeoResponse = DailyTaskGeoResponse(),
    @Json(name = "source_matches")
    val sourceMatches: List<Map<String, Any?>> = emptyList(),
    val research: DailyTaskResearchResponse? = null,
)

data class DailyTaskCreateRequest(
    val text: String,
    @Json(name = "location_label")
    val locationLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "accuracy_meters")
    val accuracyMeters: Float? = null,
    @Json(name = "captured_at")
    val capturedAt: String? = null,
)

data class DailyTaskUpdateRequest(
    val done: Boolean,
)

data class VoiceSynthesisRequest(
    val text: String,
)

data class TaskUpdateRequest(
    val title: String,
    val description: String,
)

data class SollTaskBoardCountsResponse(
    val today: Int = 0,
    val blocked: Int = 0,
    val inbox: Int = 0,
    val stale: Int = 0,
    val deferred: Int = 0,
    @Json(name = "done_recent")
    val doneRecent: Int = 0,
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
    @Json(name = "approval_id")
    val approvalId: String? = null,
    @Json(name = "tool_job_id")
    val toolJobId: String? = null,
    @Json(name = "execution_state")
    val executionState: String = "",
    @Json(name = "outcome_artifacts")
    val outcomeArtifacts: List<String> = emptyList(),
    @Json(name = "value_metric")
    val valueMetric: String = "",
    val branch: String = "innovation",
    @Json(name = "pair_id")
    val pairId: String? = null,
    @Json(name = "assigned_node_id")
    val assignedNodeId: String? = null,
    @Json(name = "required_capabilities")
    val requiredCapabilities: List<String> = emptyList(),
    @Json(name = "routing_state")
    val routingState: String = "",
    @Json(name = "execution_run_id")
    val executionRunId: String = "",
    @Json(name = "execution_phase")
    val executionPhase: String = "",
    @Json(name = "execution_reason")
    val executionReason: String = "",
    @Json(name = "risk_class")
    val riskClass: String = "",
    @Json(name = "acceptance_criteria")
    val acceptanceCriteria: List<String> = emptyList(),
    @Json(name = "test_plan")
    val testPlan: List<String> = emptyList(),
    @Json(name = "base_sha")
    val baseSha: String = "",
    @Json(name = "commit_sha")
    val commitSha: String = "",
    @Json(name = "rollback_sha")
    val rollbackSha: String = "",
    @Json(name = "execution_attempts")
    val executionAttempts: Int = 0,
    @Json(name = "execution_updated_at")
    val executionUpdatedAt: String? = null,
)

data class SollTaskMutationResponse(
    val id: String? = null,
    val title: String? = null,
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
    @Json(name = "approval_id")
    val approvalId: String? = null,
    @Json(name = "tool_job_id")
    val toolJobId: String? = null,
    @Json(name = "execution_state")
    val executionState: String = "",
    @Json(name = "outcome_artifacts")
    val outcomeArtifacts: List<String> = emptyList(),
    @Json(name = "value_metric")
    val valueMetric: String = "",
    val branch: String = "innovation",
    @Json(name = "pair_id")
    val pairId: String? = null,
    @Json(name = "assigned_node_id")
    val assignedNodeId: String? = null,
    @Json(name = "required_capabilities")
    val requiredCapabilities: List<String> = emptyList(),
    @Json(name = "routing_state")
    val routingState: String = "",
    @Json(name = "execution_run_id")
    val executionRunId: String = "",
    @Json(name = "execution_phase")
    val executionPhase: String = "",
    @Json(name = "execution_reason")
    val executionReason: String = "",
    @Json(name = "risk_class")
    val riskClass: String = "",
    @Json(name = "acceptance_criteria")
    val acceptanceCriteria: List<String> = emptyList(),
    @Json(name = "test_plan")
    val testPlan: List<String> = emptyList(),
    @Json(name = "base_sha")
    val baseSha: String = "",
    @Json(name = "commit_sha")
    val commitSha: String = "",
    @Json(name = "rollback_sha")
    val rollbackSha: String = "",
    @Json(name = "execution_attempts")
    val executionAttempts: Int = 0,
    @Json(name = "execution_updated_at")
    val executionUpdatedAt: String? = null,
    val task: SollTaskResponse? = null,
) {
    fun taskResponse(): SollTaskResponse {
        task?.let { return it }
        val cleanId = id?.trim().orEmpty()
        val cleanTitle = title?.trim().orEmpty()
        require(cleanId.isNotBlank()) { "Task response id is required" }
        require(cleanTitle.isNotBlank()) { "Task response title is required" }
        return SollTaskResponse(
            id = cleanId,
            title = cleanTitle,
            description = description,
            sourceRef = sourceRef,
            projectId = projectId,
            projectName = projectName,
            status = status,
            priority = priority,
            createdAt = createdAt,
            updatedAt = updatedAt,
            dueDate = dueDate,
            tags = tags,
            approvalId = approvalId,
            toolJobId = toolJobId,
            executionState = executionState,
            outcomeArtifacts = outcomeArtifacts,
            valueMetric = valueMetric,
            branch = branch,
            pairId = pairId,
            assignedNodeId = assignedNodeId,
            requiredCapabilities = requiredCapabilities,
            routingState = routingState,
            executionRunId = executionRunId,
            executionPhase = executionPhase,
            executionReason = executionReason,
            riskClass = riskClass,
            acceptanceCriteria = acceptanceCriteria,
            testPlan = testPlan,
            baseSha = baseSha,
            commitSha = commitSha,
            rollbackSha = rollbackSha,
            executionAttempts = executionAttempts,
            executionUpdatedAt = executionUpdatedAt,
        )
    }
}

data class TaskGraphResponse(
    val nodes: List<TaskGraphNodeResponse> = emptyList(),
    val edges: List<TaskGraphEdgeResponse> = emptyList(),
    @Json(name = "total_tasks")
    val totalTasks: Int = 0,
    val truncated: Boolean = false,
)

data class TaskGraphNodeResponse(
    val id: String = "",
    val kind: String = "",
    val label: String = "",
    val status: String = "",
    val priority: String = "",
    @Json(name = "project_id")
    val projectId: String? = null,
    @Json(name = "task_id")
    val taskId: String? = null,
    @Json(name = "source_ref")
    val sourceRef: String = "",
    val count: Int = 0,
)

data class TaskGraphEdgeResponse(
    val id: String = "",
    val source: String = "",
    val target: String = "",
    val kind: String = "",
    val label: String = "",
)

data class LearningItemsResponse(
    val items: List<LearningItemResponse> = emptyList(),
    val count: Int = 0,
)

data class LearningItemResponse(
    val id: String = "",
    val title: String = "",
    val status: String = "pending",
    val scope: String = "",
    val origin: String = "",
    @Json(name = "next_action")
    val nextAction: String = "",
    @Json(name = "source_ref")
    val sourceRef: String = "",
    @Json(name = "seen_count")
    val seenCount: Int = 0,
    val tags: List<String> = emptyList(),
)

data class LearningItemStatusRequest(
    val status: String,
)

data class LearningItemTaskRequest(
    val priority: String = "B",
    @Json(name = "mark_done")
    val markDone: Boolean = false,
)

data class LearningItemUpdateResponse(
    val success: Boolean = false,
    @Json(name = "item_id")
    val itemId: String = "",
    val status: String = "",
    val item: LearningItemResponse? = null,
)

data class LearningItemTaskResponse(
    val success: Boolean = false,
    val task: SollTaskResponse? = null,
    val item: LearningItemResponse? = null,
)

data class RoadmapResponse(
    val updated: String? = null,
    @Json(name = "current_stage")
    val currentStage: String = "",
    val stages: List<RoadmapStageResponse> = emptyList(),
    val readiness: List<RoadmapReadinessResponse> = emptyList(),
)

data class RoadmapStageResponse(
    val id: String = "",
    val label: String = "",
    val status: String = "",
    val lines: List<RoadmapLineResponse> = emptyList(),
)

data class RoadmapLineResponse(
    val line: String = "",
    val text: String = "",
)

data class RoadmapReadinessResponse(
    val area: String = "",
    val percent: Int = 0,
    val gap: String = "",
)

data class RoadmapLineRequest(
    val line: String,
    val text: String,
)

data class RoadmapLineUpdateRequest(
    val line: String? = null,
    val text: String? = null,
)

data class RoadmapLineTaskRequest(
    val priority: String = "B",
)

data class RoadmapLineTaskResponse(
    val success: Boolean = false,
    val task: SollTaskResponse? = null,
    val stage: RoadmapStageResponse? = null,
    val line: RoadmapLineResponse? = null,
)

data class MonitoredSourceResponse(
    val id: String = "",
    val name: String = "",
    @Json(name = "source_type")
    val sourceType: String = "web",
    val scope: String = "project_soll",
    val target: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true,
    @Json(name = "last_result")
    val lastResult: String = "never",
    @Json(name = "items_seen")
    val itemsSeen: Int = 0,
    @Json(name = "new_items_last_check")
    val newItemsLastCheck: Int = 0,
)

data class SourceItemResponse(
    @Json(name = "item_id")
    val itemId: String = "",
    val title: String = "",
    @Json(name = "source_url")
    val sourceUrl: String = "",
    @Json(name = "content_preview")
    val contentPreview: String = "",
    val summary: String = "",
    val usefulness: String = "medium",
    val reasoning: String = "",
    @Json(name = "evidence_level")
    val evidenceLevel: String = "unknown",
    @Json(name = "project_fit")
    val projectFit: String = "unknown",
    val actionability: String = "research_only",
    @Json(name = "dual_use_risk")
    val dualUseRisk: String = "none",
    @Json(name = "dual_use_action")
    val dualUseAction: String = "allow",
    @Json(name = "safe_next_step")
    val safeNextStep: String = "",
    @Json(name = "needs_deep_dive")
    val needsDeepDive: Boolean = false,
    @Json(name = "raw_file")
    val rawFile: String? = null,
    @Json(name = "notified_at")
    val notifiedAt: String? = null,
    @Json(name = "last_status")
    val lastStatus: String = "new",
    @Json(name = "audit_ref")
    val auditRef: String = "",
    @Json(name = "evidence_ref")
    val evidenceRef: String = "",
    @Json(name = "verification_artifact")
    val verificationArtifact: String = "",
    @Json(name = "status_reason")
    val statusReason: String = "",
    @Json(name = "delivery_status")
    val deliveryStatus: String = "unknown",
    @Json(name = "link_preview")
    val linkPreview: Map<String, Any?>? = null,
)

data class SourceItemsPageResponse(
    val items: List<SourceItemResponse> = emptyList(),
    @Json(name = "next_cursor")
    val nextCursor: String = "",
    @Json(name = "has_more")
    val hasMore: Boolean = false,
    val total: Int = 0,
    @Json(name = "source_enabled")
    val sourceEnabled: Boolean = true,
    @Json(name = "disabled_reason")
    val disabledReason: String = "",
)

data class SourceItemTaskRequest(
    val priority: String = "B",
)

data class SourceItemTaskResponse(
    val success: Boolean = false,
    val task: SollTaskResponse? = null,
    val source: MonitoredSourceResponse? = null,
    val item: SourceItemResponse? = null,
)

data class MonitoredSourceCreateRequest(
    val name: String? = null,
    @Json(name = "source_type")
    val sourceType: String = "web",
    val scope: String = "project_soll",
    val target: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val enabled: Boolean = true,
)

data class MonitoredSourceUpdateRequest(
    val name: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val enabled: Boolean? = null,
)

data class SourceCheckResponse(
    @Json(name = "source_id")
    val sourceId: String = "",
    val changed: Boolean = false,
    val summary: String = "",
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

data class SollProtocolSchemaResponse(
    val version: String = "",
    val auth: SollProtocolAuthResponse = SollProtocolAuthResponse(),
    val security: SollProtocolSecurityResponse = SollProtocolSecurityResponse(),
    val scopes: Map<String, List<String>> = emptyMap(),
    val transports: Map<String, SollProtocolTransportResponse> = emptyMap(),
    @Json(name = "worker_contracts")
    val workerContracts: Map<String, SollProtocolWorkerContractResponse> = emptyMap(),
    @Json(name = "gadget_discovery")
    val gadgetDiscovery: GadgetDiscoverySchemaResponse? = null,
)

data class SollProtocolSecurityResponse(
    @Json(name = "post_quantum")
    val postQuantum: SollProtocolPqcResponse = SollProtocolPqcResponse(),
)

data class SollProtocolPqcResponse(
    val status: String = "unknown",
    @Json(name = "protection_active")
    val protectionActive: Boolean = false,
    val target: String = "",
    @Json(name = "migration_phases")
    val migrationPhases: List<String> = emptyList(),
)

data class MeshStatusResponse(
    val enabled: Boolean = false,
    @Json(name = "simulated_mode")
    val simulatedMode: Boolean = false,
    @Json(name = "meshtastic_available")
    val meshtasticAvailable: Boolean = false,
    @Json(name = "max_payload_bytes")
    val maxPayloadBytes: Int = 0,
    @Json(name = "queued_outbox_count")
    val queuedOutboxCount: Int = 0,
    @Json(name = "sent_outbox_count")
    val sentOutboxCount: Int = 0,
    @Json(name = "acked_outbox_count")
    val ackedOutboxCount: Int = 0,
    @Json(name = "failed_outbox_count")
    val failedOutboxCount: Int = 0,
)

data class MeshOutboxListResponse(
    val outbox: List<MeshOutboxItemResponse> = emptyList(),
)

data class MeshOutboxClaimResponse(
    val outbox: MeshOutboxItemResponse? = null,
)

data class MeshOutboxAttemptRequest(
    val success: Boolean,
    val error: String? = null,
)

data class MeshOutboxItemResponse(
    @Json(name = "outbound_id")
    val outboundId: String = "",
    @Json(name = "to_peer")
    val toPeer: String = "",
    val text: String = "",
    val status: String = "",
    @Json(name = "retry_count")
    val retryCount: Int = 0,
    @Json(name = "max_retries")
    val maxRetries: Int = 0,
    @Json(name = "last_error")
    val lastError: String? = null,
    @Json(name = "created_at")
    val createdAt: String = "",
    @Json(name = "last_attempt_at")
    val lastAttemptAt: String? = null,
    @Json(name = "acked_at")
    val ackedAt: String? = null,
)

data class GadgetDiscoverySchemaResponse(
    val version: String = "",
    @Json(name = "primary_order")
    val primaryOrder: List<String> = emptyList(),
    val mdns: GadgetDiscoveryMdnsSchemaResponse = GadgetDiscoveryMdnsSchemaResponse(),
    val ssdp: GadgetDiscoverySsdpSchemaResponse = GadgetDiscoverySsdpSchemaResponse(),
    @Json(name = "wifi_ap")
    val wifiAp: GadgetDiscoveryWifiApSchemaResponse = GadgetDiscoveryWifiApSchemaResponse(),
    @Json(name = "device_json")
    val deviceJson: GadgetDiscoveryDeviceJsonSchemaResponse = GadgetDiscoveryDeviceJsonSchemaResponse(),
)

data class GadgetDiscoveryMdnsSchemaResponse(
    @Json(name = "service_types")
    val serviceTypes: List<String> = emptyList(),
)

data class GadgetDiscoverySsdpSchemaResponse(
    val headers: Map<String, String> = emptyMap(),
)

data class GadgetDiscoveryWifiApSchemaResponse(
    @Json(name = "ssid_prefixes")
    val ssidPrefixes: List<String> = emptyList(),
    @Json(name = "default_setup_host")
    val defaultSetupHost: String = "",
)

data class GadgetDiscoveryDeviceJsonSchemaResponse(
    val endpoint: String = "",
    val recommended: List<String> = emptyList(),
)

data class GadgetSnapshotResponse(
    val id: String,
    val name: String = "",
    @Json(name = "profile_id")
    val profileId: String = "",
    val enabled: Boolean = true,
    @Json(name = "firmware_version")
    val firmwareVersion: String = "",
    @Json(name = "local_ip")
    val localIp: String? = null,
    @Json(name = "uptime_ms")
    val uptimeMs: Long? = null,
    val capabilities: List<String> = emptyList(),
    @Json(name = "heartbeat_payload")
    val heartbeatPayload: Map<String, Any?> = emptyMap(),
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

data class GadgetCommandClaimRequest(
    @Json(name = "worker_id")
    val workerId: String = "",
    @Json(name = "lease_seconds")
    val leaseSeconds: Int = 60,
)

data class GadgetCommandAckRequest(
    @Json(name = "worker_id")
    val workerId: String = "",
)

data class GadgetCommandResultRequest(
    val success: Boolean,
    val payload: Map<String, Any?> = emptyMap(),
    val error: String = "",
    @Json(name = "worker_id")
    val workerId: String = "",
)

data class GadgetCommandResponse(
    val id: String = "",
    @Json(name = "gadget_id")
    val gadgetId: String = "",
    val command: String = "",
    val params: Map<String, Any?> = emptyMap(),
    val status: String = "",
    val reason: String = "",
    val result: Map<String, Any?> = emptyMap(),
    @Json(name = "risk_level")
    val riskLevel: String = "read_only",
    @Json(name = "approval_id")
    val approvalId: String = "",
    @Json(name = "created_at")
    val createdAt: String = "",
    @Json(name = "expires_at")
    val expiresAt: String? = null,
    @Json(name = "completed_at")
    val completedAt: String? = null,
)
