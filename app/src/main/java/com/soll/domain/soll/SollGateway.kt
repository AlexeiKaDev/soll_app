package com.soll.domain.soll

import android.net.Uri
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudHistory
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.metacoordinator.MetaCoordinatorRequest
import com.soll.domain.metacoordinator.MetaCoordinatorResponse
import com.soll.domain.modelchat.ModelChatRequest
import com.soll.domain.modelchat.ModelChatResponse

data class SollHealth(
    val status: String,
    val schedulerRunning: Boolean,
    val vaultAccessible: Boolean,
    val jobsCount: Int,
    val androidPush: SollAndroidPushHealth = SollAndroidPushHealth(),
    val checkedAt: String? = null,
)

data class SollAndroidPushHealth(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val tokenCount: Int = 0,
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

data class SollDeviceToken(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: String,
    val expiresIn: Int,
)

data class SollAndroidSyncStatus(
    val serverTime: String,
    val health: SollHealth,
    val tasks: SollTaskBoard,
    val device: SollDevice?,
    val node: SollNodeIdentity = SollNodeIdentity(),
    val activeNodes: List<SollNodeIdentity> = emptyList(),
    val briefing: SollBriefing?,
    val chat: SollAndroidChatSync = SollAndroidChatSync(),
    val protocol: SollProtocolBootstrap?,
    val insights: List<SollLearningItem> = emptyList(),
    val sources: List<SollMonitoredSource> = emptyList(),
    val sourceItemsBySource: Map<String, List<SollSourceItem>> = emptyMap(),
    val warnings: List<String>,
    val fromCache: Boolean = false,
    val cachedAtMillis: Long? = null,
)

data class SollNodeIdentity(
    val nodeId: String = "",
    val nodeName: String = "",
    val nodeRole: String = "",
    val isPrimary: Boolean = false,
    val priority: Int = 0,
    val capabilities: List<String> = emptyList(),
    val active: Boolean = false,
    val lastSeenAt: String? = null,
)

data class SollAndroidChatSync(
    val primarySessionId: String = "soll-main",
    val recentSessions: List<SollChatSession> = emptyList(),
    val recentMessages: List<SollChatMessage> = emptyList(),
    val lastMessageId: Long? = null,
    val unreadCount: Int = 0,
    val pendingActionsCount: Int = 0,
    val encryptionRequired: Boolean = false,
    val streamEndpoint: String = "",
    val endpoints: Map<String, String> = emptyMap(),
)

data class SollAndroidPushRegistration(
    val success: Boolean,
    val provider: String,
    val enabled: Boolean,
    val tokenCount: Int,
    val reason: String?,
)

data class SollAndroidLocationStatus(
    val available: Boolean,
    val needsAndroidLocation: Boolean,
    val stale: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val provider: String,
    val label: String,
    val city: String,
    val country: String,
    val capturedAt: String?,
    val receivedAt: String?,
)

data class SollDailyTask(
    val id: String,
    val text: String,
    val done: Boolean,
    val line: Int,
    val attachments: List<SollDailyTaskAttachment> = emptyList(),
)

data class SollDailyTaskAttachment(
    val id: String,
    val taskId: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val path: String,
    val analysisStatus: String,
    val analysisSummary: String,
    val ocrText: String,
    val searchTerms: List<String>,
    val createdAt: String,
)

data class SollDailyTaskList(
    val date: String,
    val sourcePath: String,
    val tasks: List<SollDailyTask>,
    val createdTaskId: String? = null,
)

data class SollDailyTaskGeo(
    val locationLabel: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val capturedAt: String? = null,
)

data class SollDailyTaskResearch(
    val taskId: String,
    val query: String,
    val summary: String,
    val localResults: List<Map<String, Any?>> = emptyList(),
    val sourceResults: List<Map<String, Any?>> = emptyList(),
    val webResults: List<Map<String, Any?>> = emptyList(),
    val createdAt: String = "",
)

data class SollDailyTaskDetail(
    val date: String,
    val sourcePath: String,
    val task: SollDailyTask,
    val geo: SollDailyTaskGeo = SollDailyTaskGeo(),
    val sourceMatches: List<Map<String, Any?>> = emptyList(),
    val research: SollDailyTaskResearch? = null,
)

data class SollChatSession(
    val sessionId: String,
    val title: String,
    val updatedAt: String,
    val messageCount: Int,
)

data class SollChatMessage(
    val id: Long,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: String,
    val metadata: Map<String, Any?> = emptyMap(),
) {
    val isFromUser: Boolean
        get() = role == "user"
}

data class SollChatTurnError(
    val code: String = "",
    val message: String = "",
)

data class SollChatTurnResult(
    val sessionId: String,
    val message: SollChatMessage,
    val assistant: SollChatMessage? = null,
    val turnId: String = "",
    val clientTurnId: String = "",
    val status: String = "",
    val final: Boolean = false,
    val error: SollChatTurnError? = null,
) {
    val normalizedStatus: String
        get() = status.trim().lowercase()

    val isQueued: Boolean
        get() = normalizedStatus == "queued" && !final

    val isFailed: Boolean
        get() = normalizedStatus == "failed"

    fun permitsAssistantPayload(): Boolean =
        normalizedStatus.isBlank() || (normalizedStatus == "answered" && final)
}

data class SollTaskGraph(
    val nodes: List<SollTaskGraphNode> = emptyList(),
    val edges: List<SollTaskGraphEdge> = emptyList(),
    val totalTasks: Int = 0,
    val truncated: Boolean = false,
)

data class SollTaskGraphNode(
    val id: String,
    val kind: String,
    val label: String,
    val status: String = "",
    val priority: String = "",
    val projectId: String? = null,
    val taskId: String? = null,
    val sourceRef: String = "",
    val count: Int = 0,
)

data class SollTaskGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val kind: String,
    val label: String = "",
)

data class SollLearningItem(
    val id: String,
    val title: String,
    val status: String,
    val nextAction: String,
    val sourceRef: String,
    val seenCount: Int,
    val tags: List<String> = emptyList(),
    val scope: String = "",
    val origin: String = "",
)

data class SollRoadmap(
    val currentStage: String,
    val stages: List<SollRoadmapStage> = emptyList(),
    val readiness: List<SollRoadmapReadiness> = emptyList(),
    val updated: String? = null,
)

data class SollRoadmapStage(
    val id: String,
    val label: String,
    val status: String,
    val lines: List<SollRoadmapLine> = emptyList(),
)

data class SollRoadmapLine(
    val line: String,
    val text: String,
)

data class SollRoadmapReadiness(
    val area: String,
    val percent: Int,
    val gap: String,
)

enum class SollSourceScope(val apiValue: String) {
    PROJECT_SOLL("project_soll"),
    DAILY_TODO("daily_todo"),
}

data class SollMonitoredSource(
    val id: String,
    val name: String,
    val sourceType: String,
    val scope: SollSourceScope,
    val target: String,
    val description: String,
    val tags: List<String>,
    val enabled: Boolean,
    val lastResult: String,
    val itemsSeen: Int,
    val newItemsLastCheck: Int,
)

data class SollSourceItem(
    val itemId: String,
    val title: String,
    val sourceUrl: String,
    val contentPreview: String,
    val summary: String,
    val usefulness: String,
    val reasoning: String,
    val evidenceLevel: String,
    val projectFit: String,
    val actionability: String,
    val dualUseRisk: String,
    val dualUseAction: String,
    val safeNextStep: String,
    val needsDeepDive: Boolean,
    val rawFile: String,
    val notifiedAt: String,
    val lastStatus: String,
    val auditRef: String,
    val evidenceRef: String,
    val verificationArtifact: String,
    val statusReason: String,
    val deliveryStatus: String,
    val linkPreview: Map<String, Any?> = emptyMap(),
) {
    val isTerminal: Boolean
        get() = lastStatus.trim().lowercase() in TERMINAL_SOURCE_ITEM_STATUSES

    val canCreateTask: Boolean
        get() = !isTerminal && dualUseAction.trim().lowercase() == "allow"

    val visibleReason: String
        get() = statusReason.ifBlank { reasoning }
}

internal fun ByteArray.isSollVoiceWav(): Boolean =
    size > 44 &&
        this[0] == 'R'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte() &&
        this[3] == 'F'.code.toByte() &&
        this[8] == 'W'.code.toByte() &&
        this[9] == 'A'.code.toByte() &&
        this[10] == 'V'.code.toByte() &&
        this[11] == 'E'.code.toByte()

data class SollSourceItemsPage(
    val items: List<SollSourceItem>,
    val nextCursor: String,
    val hasMore: Boolean,
    val total: Int,
    val sourceEnabled: Boolean,
    val disabledReason: String,
)

private val TERMINAL_SOURCE_ITEM_STATUSES = setOf(
    "blocked",
    "deferred",
    "done",
    "duplicate",
    "ignored",
    "implemented",
    "rejected",
    "source_processing_deferred",
    "source_review_deferred",
    "suppressed",
    "task_created",
)

data class SollChatActionResult(
    val actionId: String,
    val action: String,
    val taskId: String?,
    val status: String,
    val task: SollTask?,
)

data class SollMeshStatus(
    val enabled: Boolean,
    val simulatedMode: Boolean,
    val meshtasticAvailable: Boolean,
    val maxPayloadBytes: Int,
    val queuedOutboxCount: Int,
    val sentOutboxCount: Int,
    val ackedOutboxCount: Int,
    val failedOutboxCount: Int,
)

data class SollMeshOutboxItem(
    val outboundId: String,
    val toPeer: String,
    val text: String,
    val claimToken: String = "",
    val securePayload: String = "",
    val status: String,
    val retryCount: Int,
    val maxRetries: Int,
    val lastError: String?,
    val createdAt: String,
    val lastAttemptAt: String?,
    val ackedAt: String?,
)

data class SollTaskBoard(
    val today: List<SollTask>,
    val blocked: List<SollTask> = emptyList(),
    val inbox: List<SollTask>,
    val stale: List<SollTask>,
    val deferred: List<SollTask> = emptyList(),
    val doneRecent: List<SollTask>,
    val counts: SollTaskBoardCounts? = null,
    val limitPerSection: Int? = null,
) {
    val openCount: Int
        get() = counts?.openCount ?: displayedOpenCount

    val displayedOpenCount: Int
        get() = today.size + blocked.size + inbox.size + stale.size + deferred.size

    val doneCount: Int
        get() = counts?.doneRecent ?: displayedDoneCount

    val displayedDoneCount: Int
        get() = doneRecent.size

    val totalCount: Int
        get() = openCount + doneCount

    val displayedTotalCount: Int
        get() = displayedOpenCount + displayedDoneCount

    val hasLimitedOpenSections: Boolean
        get() = counts?.let { displayedOpenCount < it.openCount } == true

    val hasLimitedDoneSection: Boolean
        get() = counts?.let { displayedDoneCount < it.doneRecent } == true

    val hasLimitedSections: Boolean
        get() = hasLimitedOpenSections || hasLimitedDoneSection
}

data class SollTaskBoardCounts(
    val today: Int = 0,
    val blocked: Int = 0,
    val inbox: Int = 0,
    val stale: Int = 0,
    val deferred: Int = 0,
    val doneRecent: Int = 0,
) {
    val openCount: Int
        get() = today + blocked + inbox + stale + deferred
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
    val approvalId: String? = null,
    val toolJobId: String? = null,
    val executionState: String = "",
    val outcomeArtifacts: List<String> = emptyList(),
    val completionKind: String = "",
    val completionResult: String = "",
    val completionEvidence: List<String> = emptyList(),
    val valueMetric: String = "",
    val branch: String = "innovation",
    val pairId: String? = null,
    val assignedNodeId: String? = null,
    val requiredCapabilities: List<String> = emptyList(),
    val routingState: String = "",
    val executionRunId: String = "",
    val executionPhase: String = "",
    val executionReason: String = "",
    val riskClass: String = "",
    val acceptanceCriteria: List<String> = emptyList(),
    val testPlan: List<String> = emptyList(),
    val baseSha: String = "",
    val commitSha: String = "",
    val rollbackSha: String = "",
    val executionAttempts: Int = 0,
    val executionUpdatedAt: String? = null,
)

internal fun SollTaskBoard.withoutDailyTodoTasks(): SollTaskBoard {
    val filteredToday = today.filterNot { it.isDailyTodoTask() }
    val filteredBlocked = blocked.filterNot { it.isDailyTodoTask() }
    val filteredInbox = inbox.filterNot { it.isDailyTodoTask() }
    val filteredStale = stale.filterNot { it.isDailyTodoTask() }
    val filteredDeferred = deferred.filterNot { it.isDailyTodoTask() }
    val filteredDoneRecent = doneRecent.filterNot { it.isDailyTodoTask() }
    val changed = filteredToday.size != today.size ||
        filteredBlocked.size != blocked.size ||
        filteredInbox.size != inbox.size ||
        filteredStale.size != stale.size ||
        filteredDeferred.size != deferred.size ||
        filteredDoneRecent.size != doneRecent.size
    return copy(
        today = filteredToday,
        blocked = filteredBlocked,
        inbox = filteredInbox,
        stale = filteredStale,
        deferred = filteredDeferred,
        doneRecent = filteredDoneRecent,
        counts = if (changed) {
            SollTaskBoardCounts(
                today = filteredToday.size,
                blocked = filteredBlocked.size,
                inbox = filteredInbox.size,
                stale = filteredStale.size,
                deferred = filteredDeferred.size,
                doneRecent = filteredDoneRecent.size,
            )
        } else {
            counts
        },
    )
}

internal fun SollTask.isDailyTodoTask(): Boolean =
    id.hasDailyTodoMarker() ||
        sourceRef.hasDailyTodoMarker() ||
        tags.any { it.hasDailyTodoMarker() }

private fun String.hasDailyTodoMarker(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("task:daily:") ||
        normalized.contains("daily_todo") ||
        normalized.contains("android_daily_todo")
}

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
    suspend fun getTodayIntelligence(): Result<SollTodaySnapshot>
    suspend fun getPersonalFeed(
        limit: Int = 30,
        cursor: String = "",
        category: String = "",
    ): Result<SollFeedPage>
    suspend fun importFeedLink(
        url: String,
        title: String = "",
        sharedText: String = "",
        clientId: String? = null,
    ): Result<SollFeedImportResult> = Result.failure(
        UnsupportedOperationException("Импорт ссылок не поддерживается этим шлюзом")
    )
    suspend fun sendFeedFeedback(
        entityId: String,
        decision: String,
        topic: String,
        source: String,
        note: String = "",
        clientId: String,
    ): Result<SollFeedbackCommandResult>
    suspend fun sendAssistantFeedback(
        entityType: String,
        entityId: String,
        decision: String,
        note: String = "",
        clientId: String,
    ): Result<SollFeedbackCommandResult>
    suspend fun sendNotificationReceipt(
        eventId: String,
        state: String,
        occurredAt: String,
        clientId: String,
    ): Result<SollFeedbackCommandResult>
    suspend fun syncCalendarSnapshot(
        timezone: String,
        events: List<SollCalendarEvent>,
    ): Result<SollCalendarSnapshot>
    suspend fun getTaskBoard(limitPerSection: Int? = null): Result<SollTaskBoard>
    suspend fun getTodayDailyTasks(): Result<SollDailyTaskList>
    suspend fun addTodayDailyTask(text: String, locationLabel: String = ""): Result<SollDailyTaskList>
    suspend fun updateTodayDailyTask(taskId: String, done: Boolean): Result<SollDailyTaskList>
    suspend fun deleteTodayDailyTask(taskId: String): Result<SollDailyTaskList>
    suspend fun getTodayDailyTaskDetail(taskId: String): Result<SollDailyTaskDetail>
    suspend fun researchTodayDailyTask(taskId: String): Result<SollDailyTaskDetail>
    suspend fun uploadTodayDailyTaskAttachment(taskId: String, uri: Uri): Result<SollDailyTaskAttachment>
    suspend fun getAndroidSyncStatus(): Result<SollAndroidSyncStatus>
    suspend fun listChatSessions(limit: Int = 50): Result<List<SollChatSession>>
    suspend fun createChatSession(title: String, sessionId: String? = null): Result<SollChatSession>
    suspend fun getChatSession(
        sessionId: String,
        limit: Int? = null,
        beforeId: Long? = null,
        afterId: Long? = null,
    ): Result<List<SollChatMessage>>
    suspend fun sendChatTurn(
        content: String,
        sessionId: String? = null,
        runAssistant: Boolean = true,
        taskIntake: Boolean = false,
        allowActions: Boolean = false,
        metadata: Map<String, Any?> = emptyMap(),
        encryptionNonceSeed: String? = null,
    ): Result<SollChatTurnResult>

    suspend fun getChatTurnStatus(turnId: String): Result<SollChatTurnResult>

    suspend fun synthesizeVoice(text: String): Result<ByteArray>

    suspend fun executeChatAction(
        actionId: String,
        action: String,
        taskId: String? = null,
        sessionId: String? = null,
    ): Result<SollChatActionResult>

    suspend fun issueDeviceToken(deviceId: String, pairingSecret: String): Result<SollDeviceToken>
    suspend fun refreshDeviceToken(): Result<SollDeviceToken>
    suspend fun registerAndroidPushToken(
        token: String,
        provider: String = "fcm",
    ): Result<SollAndroidPushRegistration>

    suspend fun publishAndroidLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float? = null,
        altitudeMeters: Double? = null,
        provider: String = "android",
        capturedAtMillis: Long = System.currentTimeMillis(),
        label: String = "",
        city: String = "",
        country: String = "",
        reason: String = "android_user_approved_location",
    ): Result<SollAndroidLocationStatus>

    suspend fun createRawNote(
        title: String,
        content: String,
        tags: List<String> = emptyList(),
    ): Result<SollRawNote>

    suspend fun uploadRawFile(uri: Uri): Result<SollRawUpload>
    suspend fun updateTask(taskId: String, title: String, description: String): Result<SollTask>
    suspend fun setTaskStatus(taskId: String, status: String): Result<SollTask>
    suspend fun moveTaskToToday(taskId: String): Result<SollTask>
    suspend fun completeTask(taskId: String): Result<SollTask>
    suspend fun deferTask(taskId: String): Result<SollTask>
    suspend fun rejectTask(taskId: String): Result<SollTask>
    suspend fun getTaskGraph(includeDone: Boolean = false): Result<SollTaskGraph>
    suspend fun getTaskGraphDescendants(
        ancestorId: String,
        includeDone: Boolean = false,
        kind: String? = null,
        limit: Int = 700,
    ): Result<List<SollTaskGraphNode>>
    suspend fun getLearningItems(status: String? = "pending", limit: Int = 80): Result<List<SollLearningItem>>
    suspend fun updateLearningItemStatus(itemId: String, status: String): Result<SollLearningItem?>
    suspend fun createTaskFromLearningItem(itemId: String): Result<SollTask?>
    suspend fun getRoadmap(): Result<SollRoadmap>
    suspend fun addRoadmapLine(stageId: String, line: String, text: String): Result<SollRoadmap>
    suspend fun updateRoadmapLine(stageId: String, line: String, newLine: String, text: String): Result<SollRoadmap>
    suspend fun deleteRoadmapLine(stageId: String, line: String): Result<SollRoadmap>
    suspend fun createTaskFromRoadmapLine(stageId: String, line: String): Result<SollTask?>
    suspend fun listSources(scope: SollSourceScope): Result<List<SollMonitoredSource>>
    suspend fun listSourceItems(sourceId: String, limit: Int = 20): Result<List<SollSourceItem>>
    suspend fun listSourceItemsPage(sourceId: String, cursor: String = "", limit: Int = 50): Result<SollSourceItemsPage>
    suspend fun createSource(
        name: String,
        target: String,
        scope: SollSourceScope,
        sourceType: String = "web",
    ): Result<SollMonitoredSource>
    suspend fun updateSource(
        sourceId: String,
        name: String,
        description: String,
        tags: List<String>,
        enabled: Boolean,
    ): Result<SollMonitoredSource>
    suspend fun deleteSource(sourceId: String): Result<Boolean>
    suspend fun checkSource(sourceId: String): Result<Boolean>
    suspend fun createTaskFromSourceItem(sourceId: String, itemId: String): Result<SollTask?>
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
    suspend fun askModelChat(request: ModelChatRequest): Result<ModelChatResponse>
    suspend fun getProtocolSchema(): Result<SollProtocolSchema>
    suspend fun getMeshStatus(): Result<SollMeshStatus>
    suspend fun getMeshOutbox(limit: Int = 20): Result<List<SollMeshOutboxItem>>
    suspend fun claimNextMeshOutbox(toPeer: String? = null): Result<SollMeshOutboxItem?>
    suspend fun ackMeshOutbox(
        outboundId: String,
        claimToken: String? = null,
    ): Result<SollMeshOutboxItem>
    suspend fun markMeshOutboxAttempt(
        outboundId: String,
        success: Boolean,
        error: String? = null,
        claimToken: String? = null,
    ): Result<SollMeshOutboxItem>
    suspend fun retryMeshOutbox(outboundId: String): Result<SollMeshOutboxItem>
    suspend fun getGadgetSnapshots(): Result<List<GadgetCloudSnapshot>>
    suspend fun getGadgetLatest(gadgetId: String): Result<GadgetCloudSnapshot>
    suspend fun createGadgetCommand(
        gadgetId: String,
        command: String,
        params: Map<String, Any?> = emptyMap(),
        ttlSeconds: Int = 60,
    ): Result<GadgetCloudCommand>

    suspend fun getGadgetCommands(gadgetId: String, limit: Int = 20): Result<List<GadgetCloudCommand>>

    suspend fun claimGadgetCommand(
        gadgetId: String,
        workerId: String,
        leaseSeconds: Int = 60,
    ): Result<GadgetCloudCommand?>

    suspend fun ackGadgetCommand(
        gadgetId: String,
        commandId: String,
        workerId: String,
    ): Result<GadgetCloudCommand>

    suspend fun postGadgetCommandResult(
        gadgetId: String,
        commandId: String,
        success: Boolean,
        workerId: String,
        payload: Map<String, Any?> = emptyMap(),
        error: String = "",
    ): Result<GadgetCloudCommand>

    suspend fun postManualGadgetCommandResult(
        gadgetId: String,
        commandId: String,
        success: Boolean,
        payload: Map<String, Any?> = emptyMap(),
        error: String = "",
    ): Result<GadgetCloudCommand>

    suspend fun getGadgetHistory(
        gadgetId: String,
        metric: String? = null,
        from: String? = null,
        to: String? = null,
        limit: Int = 200,
    ): Result<GadgetCloudHistory>

    suspend fun getGadgetEvents(gadgetId: String, limit: Int = 50): Result<List<GadgetCloudEvent>>
}
