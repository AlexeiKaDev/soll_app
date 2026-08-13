package com.soll.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import com.squareup.moshi.Moshi
import com.soll.BuildConfig
import com.soll.data.api.AndroidLocationStatusResponse
import com.soll.data.api.AndroidLocationUpdateRequest
import com.soll.data.api.AndroidProtocolBootstrapResponse
import com.soll.data.api.AndroidPushTokenRequest
import com.soll.data.api.AndroidPushTokenResponse
import com.soll.data.api.AndroidSyncStatusResponse
import com.soll.data.api.AssistantAskRequest
import com.soll.data.api.AssistantAskResponse
import com.soll.data.api.AssistantFeedbackRequest
import com.soll.data.api.BookActionResponse
import com.soll.data.api.BookAlternativeResponse
import com.soll.data.api.BookBatchDownloadItemResponse
import com.soll.data.api.BookBatchDownloadResponse
import com.soll.data.api.BookCurrentResultsResponse
import com.soll.data.api.BookDownloadRequest
import com.soll.data.api.BookDownloadResponse
import com.soll.data.api.BookDownloadSelectedRequest
import com.soll.data.api.BookDownloadedFileResponse
import com.soll.data.api.BookFormatResponse
import com.soll.data.api.BookIdentityResponse
import com.soll.data.api.BookProcessDownloadedRequest
import com.soll.data.api.BookProcessResponse
import com.soll.data.api.BookResultResponse
import com.soll.data.api.BookSelectRequest
import com.soll.data.api.BookSelectResponse
import com.soll.data.api.BookStatusSessionResponse
import com.soll.data.api.ChatActionExecuteRequest
import com.soll.data.api.ChatActionExecuteResponse
import com.soll.data.api.ChatMessageResponse
import com.soll.data.api.ChatSessionCreateRequest
import com.soll.data.api.ChatSessionCreateResponse
import com.soll.data.api.ChatSessionSummaryResponse
import com.soll.data.api.ChatTurnRequest
import com.soll.data.api.CreateRawFileRequest
import com.soll.data.api.DailyTaskAttachmentResponse
import com.soll.data.api.DailyTaskCreateRequest
import com.soll.data.api.DailyTaskDetailResponse
import com.soll.data.api.DailyTaskGeoResponse
import com.soll.data.api.DailyTaskItemResponse
import com.soll.data.api.DailyTaskListResponse
import com.soll.data.api.DailyTaskResearchResponse
import com.soll.data.api.DailyTaskUpdateRequest
import com.soll.data.api.DeviceTokenRequest
import com.soll.data.api.GadgetCommandAckRequest
import com.soll.data.api.GadgetDiscoverySchemaResponse
import com.soll.data.api.GadgetCommandClaimRequest
import com.soll.data.api.GadgetCommandCreateRequest
import com.soll.data.api.GadgetCommandResponse
import com.soll.data.api.GadgetCommandResultRequest
import com.soll.data.api.GadgetEventResponse
import com.soll.data.api.GadgetHistoryPointResponse
import com.soll.data.api.GadgetHistoryResponse
import com.soll.data.api.GadgetSnapshotResponse
import com.soll.data.api.LearningItemResponse
import com.soll.data.api.LearningItemStatusRequest
import com.soll.data.api.LearningItemTaskRequest
import com.soll.data.api.MeshOutboxAttemptRequest
import com.soll.data.api.MeshOutboxAckRequest
import com.soll.data.api.MeshOutboxItemResponse
import com.soll.data.api.MeshStatusResponse
import com.soll.data.api.MonitoredSourceCreateRequest
import com.soll.data.api.MonitoredSourceResponse
import com.soll.data.api.MonitoredSourceUpdateRequest
import com.soll.data.api.RawFileResponse
import com.soll.data.api.RawUploadResponse
import com.soll.data.api.RoadmapLineRequest
import com.soll.data.api.RoadmapLineTaskRequest
import com.soll.data.api.RoadmapLineUpdateRequest
import com.soll.data.api.RoadmapResponse
import com.soll.data.api.RoadmapStageResponse
import com.soll.data.api.RoadmapLineResponse
import com.soll.data.api.RoadmapReadinessResponse
import com.soll.data.api.SollApiService
import com.soll.data.api.CalendarEventRequest
import com.soll.data.api.CalendarSnapshotRequest
import com.soll.data.api.FeedFeedbackRequest
import com.soll.data.api.FeedImportLinkRequest
import com.soll.data.api.NotificationReceiptRequest
import com.soll.data.api.toDomain
import com.soll.data.api.SollBookStatusResponse
import com.soll.data.api.SollBriefingResponse
import com.soll.data.api.SollDeviceResponse
import com.soll.data.api.SollHealthResponse
import com.soll.data.api.SollProtocolSchemaResponse
import com.soll.data.api.SollProtocolAuthResponse
import com.soll.data.api.SollProtocolTransportResponse
import com.soll.data.api.SollProtocolWorkerContractResponse
import com.soll.data.api.SollTaskBoardResponse
import com.soll.data.api.SollTaskBoardCountsResponse
import com.soll.data.api.SollTaskResponse
import com.soll.data.api.SecurePayloadEnvelopeRequest
import com.soll.data.api.SourceItemResponse
import com.soll.data.api.SourceItemTaskRequest
import com.soll.data.api.TaskUpdateRequest
import com.soll.data.api.TaskGraphEdgeResponse
import com.soll.data.api.TaskGraphNodeResponse
import com.soll.data.api.TaskGraphResponse
import com.soll.data.api.VoiceSynthesisRequest
import com.soll.data.local.dao.TaskGraphCacheDao
import com.soll.domain.metacoordinator.MetaCoordinatorFallback
import com.soll.domain.metacoordinator.MetaCoordinatorRequest
import com.soll.domain.metacoordinator.MetaCoordinatorResponse
import com.soll.domain.metacoordinator.MetaCoordinatorServerBridge
import com.soll.domain.modelchat.ModelChatFallback
import com.soll.domain.modelchat.ModelChatRequest
import com.soll.domain.modelchat.ModelChatResponse
import com.soll.domain.modelchat.ModelChatServerBridge
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudHistory
import com.soll.domain.device.GadgetCloudHistoryPoint
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH
import com.soll.domain.soll.SOLL_DURABLE_CLIENT_ID_MAX_LENGTH
import com.soll.domain.soll.SollAndroidSyncStatus
import com.soll.domain.soll.SollAndroidChatSync
import com.soll.domain.soll.SollAndroidLocationStatus
import com.soll.domain.soll.SollAndroidPushHealth
import com.soll.domain.soll.SollAndroidPushRegistration
import com.soll.domain.soll.SollBookActionResult
import com.soll.domain.soll.SollBookAlternative
import com.soll.domain.soll.SollBookBatchDownload
import com.soll.domain.soll.SollBookBatchItem
import com.soll.domain.soll.SollBookCurrentResults
import com.soll.domain.soll.SollBookDownload
import com.soll.domain.soll.SollBookDownloadedFile
import com.soll.domain.soll.SollBookFormat
import com.soll.domain.soll.SollBookIdentity
import com.soll.domain.soll.SollBookProcessResult
import com.soll.domain.soll.SollBookResult
import com.soll.domain.soll.SollBookSelection
import com.soll.domain.soll.SollBookSession
import com.soll.domain.soll.SollBookStatus
import com.soll.domain.soll.SollBriefing
import com.soll.domain.soll.SollCalendarEvent
import com.soll.domain.soll.SollCalendarSnapshot
import com.soll.domain.soll.SollChatActionResult
import com.soll.domain.soll.SollChatActionPolicyRegistry
import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollChatSession
import com.soll.domain.soll.SollDailyTask
import com.soll.domain.soll.SollDailyTaskAttachment
import com.soll.domain.soll.SollDailyTaskDetail
import com.soll.domain.soll.SollDailyTaskGeo
import com.soll.domain.soll.SollDailyTaskList
import com.soll.domain.soll.SollDailyTaskResearch
import com.soll.domain.soll.SollDevice
import com.soll.domain.soll.SollDeviceToken
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollFeedPage
import com.soll.domain.soll.SollFeedImportResult
import com.soll.domain.soll.SollFeedbackCommandResult
import com.soll.domain.soll.SollLearningItem
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollNodeIdentity
import com.soll.domain.soll.SollRoadmap
import com.soll.domain.soll.SollRoadmapLine
import com.soll.domain.soll.SollRoadmapReadiness
import com.soll.domain.soll.SollRoadmapStage
import com.soll.domain.soll.SollSourceItem
import com.soll.domain.soll.SollSourceItemsPage
import com.soll.domain.soll.SollSourceScope
import com.soll.domain.soll.SollGadgetDiscoverySchema
import com.soll.domain.soll.SollMeshOutboxItem
import com.soll.domain.soll.SollMeshStatus
import com.soll.domain.soll.SollProtocolAuth
import com.soll.domain.soll.SollProtocolBootstrap
import com.soll.domain.soll.SollProtocolSchema
import com.soll.domain.soll.SollProtocolSecurity
import com.soll.domain.soll.SollProtocolTransport
import com.soll.domain.soll.SollProtocolWorkerContract
import com.soll.domain.soll.SollRawNote
import com.soll.domain.soll.SollRawUpload
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import com.soll.domain.soll.SollTaskBoardCounts
import com.soll.domain.soll.SollTaskGraph
import com.soll.domain.soll.SollTaskGraphEdge
import com.soll.domain.soll.SollTaskGraphNode
import com.soll.domain.soll.SollTodaySnapshot
import com.soll.domain.soll.buildSollDeviceTokenSignature
import com.soll.domain.soll.isSollVoiceWav
import com.soll.domain.soll.withoutDailyTodoTasks
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber

@Singleton
class SollRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val taskGraphCacheDao: TaskGraphCacheDao,
) : SollGateway {
    private val androidSyncStatusJsonAdapter by lazy {
        moshi.adapter(AndroidSyncStatusResponse::class.java)
    }

    override suspend fun getHealth(): Result<SollHealth> = runSuspendCatching {
        service().getHealth(readAuthorizationHeader()).toDomain()
    }

    override suspend fun getTodayIntelligence(): Result<SollTodaySnapshot> = runSuspendCatching {
        service().getTodayIntelligence(refreshAwareReadAuthorizationHeader()).toDomain()
    }

    override suspend fun getPersonalFeed(
        limit: Int,
        cursor: String,
        category: String,
    ): Result<SollFeedPage> = runSuspendCatching {
        service().getPersonalFeed(
            authorization = refreshAwareReadAuthorizationHeader(),
            limit = limit.coerceIn(1, 50),
            cursor = cursor,
            category = category,
        ).toDomain()
    }

    override suspend fun importFeedLink(
        url: String,
        title: String,
        sharedText: String,
        clientId: String?,
    ): Result<SollFeedImportResult> = runSuspendCatching {
        val cleanUrl = url.trim()
        require(cleanUrl.isNotBlank()) { "Ссылка не задана" }
        require(cleanUrl.length <= 2_048) { "Ссылка слишком длинная" }
        val parsedUrl = requireNotNull(cleanUrl.toHttpUrlOrNull()) {
            "Допустимы только корректные HTTP(S)-ссылки"
        }
        require(parsedUrl.scheme == "http" || parsedUrl.scheme == "https") {
            "Допустимы только HTTP(S)-ссылки"
        }
        require(parsedUrl.username.isBlank() && parsedUrl.password.isBlank()) {
            "Ссылки со встроенными учётными данными не поддерживаются"
        }
        val cleanClientId = clientId
            ?.trim()
            ?.take(SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH)
            ?.takeIf(String::isNotBlank)
        service().importFeedLink(
            authorization = writeAuthorizationHeader(),
            request = FeedImportLinkRequest(
                url = parsedUrl.toString(),
                title = title.trim().take(240),
                sharedText = sharedText.trim().take(16_000),
                source = "android_share",
                clientId = cleanClientId,
                idempotencyKey = cleanClientId,
            ),
        ).toDomain()
    }

    override suspend fun sendFeedFeedback(
        entityId: String,
        decision: String,
        topic: String,
        source: String,
        note: String,
        clientId: String,
    ): Result<SollFeedbackCommandResult> = runSuspendCatching {
        require(entityId.isNotBlank()) { "ID материала не задан" }
        val feedbackEventId = clientId.trim().take(SOLL_DURABLE_CLIENT_ID_MAX_LENGTH)
        require(feedbackEventId.isNotBlank()) { "ID события обратной связи не задан" }
        service().sendFeedFeedback(
            authorization = writeAuthorizationHeader(),
            entityId = entityId,
            request = FeedFeedbackRequest(
                decision = decision,
                topic = topic,
                source = source,
                note = note,
                clientId = feedbackEventId,
                idempotencyKey = feedbackEventId,
            ),
        ).toDomain()
    }

    override suspend fun sendAssistantFeedback(
        entityType: String,
        entityId: String,
        decision: String,
        note: String,
        clientId: String,
    ): Result<SollFeedbackCommandResult> = runSuspendCatching {
        val cleanEntityType = entityType.trim().lowercase()
        require(cleanEntityType in setOf("initiative", "notification")) { "Неизвестный тип обратной связи" }
        val cleanEntityId = entityId.trim()
        require(cleanEntityId.isNotBlank()) { "ID объекта обратной связи не задан" }
        val cleanClientId = clientId.trim().take(SOLL_DURABLE_CLIENT_ID_MAX_LENGTH)
        require(cleanClientId.isNotBlank()) { "ID события обратной связи не задан" }
        service().sendAssistantFeedback(
            authorization = writeAuthorizationHeader(),
            request = AssistantFeedbackRequest(
                entityType = cleanEntityType,
                entityId = cleanEntityId,
                decision = decision.trim(),
                note = note.trim().take(2_000),
                clientId = cleanClientId,
                idempotencyKey = cleanClientId,
            ),
        ).toDomain()
    }

    override suspend fun sendNotificationReceipt(
        eventId: String,
        state: String,
        occurredAt: String,
        clientId: String,
    ): Result<SollFeedbackCommandResult> = runSuspendCatching {
        val cleanState = state.trim().lowercase()
        require(cleanState in setOf("received", "opened")) { "Неизвестное состояние уведомления" }
        val cleanEventId = eventId.trim().take(200)
        require(cleanEventId.isNotBlank()) { "event_id уведомления не задан" }
        val cleanClientId = clientId.trim().take(SOLL_DURABLE_CLIENT_ID_MAX_LENGTH)
        require(cleanClientId.isNotBlank()) { "ID квитанции уведомления не задан" }
        service().sendNotificationReceipt(
            authorization = writeAuthorizationHeader(),
            request = NotificationReceiptRequest(
                eventId = cleanEventId,
                state = cleanState,
                occurredAt = occurredAt.trim(),
                clientId = cleanClientId,
                idempotencyKey = cleanClientId,
            ),
        ).toDomain()
    }

    override suspend fun syncCalendarSnapshot(
        timezone: String,
        events: List<SollCalendarEvent>,
    ): Result<SollCalendarSnapshot> = runSuspendCatching {
        service().syncCalendarSnapshot(
            authorization = writeAuthorizationHeader(),
            request = CalendarSnapshotRequest(
                timezone = timezone,
                events = events.take(200).map { event ->
                    CalendarEventRequest(
                        eventId = event.eventId,
                        title = event.title,
                        startAt = event.startAt,
                        endAt = event.endAt,
                        allDay = event.allDay,
                        location = event.location,
                    )
                },
            ),
        ).toDomain()
    }

    override suspend fun getTaskBoard(limitPerSection: Int?): Result<SollTaskBoard> {
        val sectionLimit = limitPerSection
            ?.coerceIn(TASK_BOARD_MIN_SECTION_LIMIT, TASK_BOARD_MAX_SECTION_LIMIT)
            ?: TASK_BOARD_SECTION_LIMIT
        val boardResult = runSuspendCatching {
            service().getTaskBoard(
                authorization = readAuthorizationHeader(),
                importDaily = false,
                limitPerSection = sectionLimit,
                includeCounts = true,
            ).toDomain().withoutDailyTodoTasks()
        }
        if (boardResult.isSuccess) {
            val board = boardResult.getOrThrow()
            if (board.openCount > 0 || board.doneRecent.isNotEmpty()) {
                return boardResult
            }
        }

        val syncStatusResult = runSuspendCatching {
            val response = service().getAndroidSyncStatus(ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader())
            cacheAndroidSyncStatus(response)
            response.tasks.toDomain().withoutDailyTodoTasks()
        }
        if (syncStatusResult.isSuccess) return syncStatusResult

        return boardResult
    }

    override suspend fun getTodayDailyTasks(): Result<SollDailyTaskList> {
        return runSuspendCatching {
            service().getTodayDailyTasks(readAuthorizationHeader()).toDomain()
        }
    }

    override suspend fun addTodayDailyTask(text: String, locationLabel: String): Result<SollDailyTaskList> =
        runSuspendCatching {
            val cleanText = text.trim()
            require(cleanText.isNotBlank()) { "Текст дела не задан" }
            service().addTodayDailyTask(
                authorization = writeAuthorizationHeader(),
                request = DailyTaskCreateRequest(
                    text = cleanText,
                    locationLabel = locationLabel.trim(),
                ),
            ).toDomain()
        }.recoverCatching { error ->
            throw error
        }

    override suspend fun updateTodayDailyTask(taskId: String, done: Boolean): Result<SollDailyTaskList> =
        runSuspendCatching {
            val cleanTaskId = taskId.trim()
            require(cleanTaskId.isNotBlank()) { "ID дела не задан" }
            service().updateTodayDailyTask(
                authorization = writeAuthorizationHeader(),
                taskId = cleanTaskId.encodedSollPathSegment(fieldName = "task_id"),
                request = DailyTaskUpdateRequest(done = done),
            ).toDomain()
        }.recoverCatching { error ->
            throw error
        }

    override suspend fun deleteTodayDailyTask(taskId: String): Result<SollDailyTaskList> =
        runSuspendCatching {
            val cleanTaskId = taskId.trim()
            require(cleanTaskId.isNotBlank()) { "ID дела не задан" }
            service().deleteTodayDailyTask(
                authorization = writeAuthorizationHeader(),
                taskId = cleanTaskId.encodedSollPathSegment(fieldName = "task_id"),
            ).toDomain()
        }

    override suspend fun getTodayDailyTaskDetail(taskId: String): Result<SollDailyTaskDetail> =
        runSuspendCatching {
            val cleanTaskId = taskId.trim()
            require(cleanTaskId.isNotBlank()) { "ID дела не задан" }
            service().getTodayDailyTaskDetail(
                authorization = readAuthorizationHeader(),
                taskId = cleanTaskId.encodedSollPathSegment(fieldName = "task_id"),
            ).toDomain()
        }

    override suspend fun researchTodayDailyTask(taskId: String): Result<SollDailyTaskDetail> =
        runSuspendCatching {
            val cleanTaskId = taskId.trim()
            require(cleanTaskId.isNotBlank()) { "ID дела не задан" }
            service().researchTodayDailyTask(
                authorization = writeAuthorizationHeader(),
                taskId = cleanTaskId.encodedSollPathSegment(fieldName = "task_id"),
            ).toDomain()
        }

    override suspend fun uploadTodayDailyTaskAttachment(taskId: String, uri: Uri): Result<SollDailyTaskAttachment> =
        runSuspendCatching {
            val cleanTaskId = taskId.trim()
            require(cleanTaskId.isNotBlank()) { "ID дела не задан" }
            val metadata = resolveRawUploadMetadata(uri)
            val filename = buildRawUploadFilename(metadata.displayName)
            val requestBody = uriRequestBody(
                uri = uri,
                contentType = metadata.mimeType,
                contentLength = metadata.size,
            )
            val part = MultipartBody.Part.createFormData("file", filename, requestBody)
            service().uploadTodayDailyTaskAttachment(
                authorization = writeAuthorizationHeader(),
                taskId = cleanTaskId.encodedSollPathSegment(fieldName = "task_id"),
                file = part,
            ).toDomain()
        }

    override suspend fun getAndroidSyncStatus(): Result<SollAndroidSyncStatus> {
        val liveResult = runSuspendCatching {
            val response = service().getAndroidSyncStatus(ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader())
            cacheAndroidSyncStatus(response)
            response.toDomain()
        }
        if (liveResult.isSuccess) return liveResult

        val cached = cachedAndroidSyncStatusOrNull(liveResult.exceptionOrNull())
        return cached?.let { Result.success(it) } ?: liveResult
    }

    override suspend fun listChatSessions(limit: Int): Result<List<SollChatSession>> = runSuspendCatching {
        service().getChatSessions(
            authorization = readAuthorizationHeader(),
            limit = limit.coerceIn(1, 100),
        ).sessions.map { it.toDomain() }
    }

    override suspend fun createChatSession(title: String, sessionId: String?): Result<SollChatSession> =
        runSuspendCatching {
            val cleanTitle = title.trim().ifBlank { "Soll Android" }
            service().createChatSession(
                authorization = readAuthorizationHeader(),
                request = ChatSessionCreateRequest(
                    title = cleanTitle,
                    sessionId = sessionId?.trim()?.takeIf { it.isNotBlank() },
                ),
            ).toDomain()
        }

    override suspend fun getChatSession(
        sessionId: String,
        limit: Int?,
        beforeId: Long?,
        afterId: Long?,
    ): Result<List<SollChatMessage>> = runSuspendCatching {
        val cleanSessionId = sessionId.trim()
        require(cleanSessionId.isNotBlank()) { "ID чата не задан" }
        service().getChatSession(
            authorization = readAuthorizationHeader(),
            sessionId = cleanSessionId,
            limit = limit?.coerceIn(1, 500),
            beforeId = beforeId,
            afterId = afterId,
        ).messages.map { it.toDomain() }
    }

    override suspend fun sendChatTurn(
        content: String,
        sessionId: String?,
        runAssistant: Boolean,
        taskIntake: Boolean,
        allowActions: Boolean,
        metadata: Map<String, Any?>,
    ): Result<Pair<SollChatMessage, SollChatMessage?>> = runSuspendCatching {
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank()) { "Сообщение пустое" }
        val requestMetadata = mapOf("source" to "android_app") + metadata
        val encrypted = encryptedEnvelopeOrNull(
            content = cleanContent,
            metadata = requestMetadata,
            aad = "POST /api/v1/chat/turn",
        )
        val response = service().sendChatTurn(
            authorization = readAuthorizationHeader(),
            request = ChatTurnRequest(
                sessionId = sessionId?.trim()?.takeIf { it.isNotBlank() },
                content = if (encrypted == null) cleanContent else null,
                metadata = if (encrypted == null) requestMetadata else null,
                encrypted = encrypted,
                runAssistant = runAssistant,
                taskIntake = taskIntake,
                allowActions = allowActions,
            ),
        )
        response.message.toDomain() to response.assistant?.toDomain()
    }

    override suspend fun synthesizeVoice(text: String): Result<ByteArray> = runSuspendCatching {
        val cleanText = text.trim()
        require(cleanText.isNotBlank()) { "Текст для озвучивания пуст" }
        require(cleanText.length <= MAX_CHAT_VOICE_TEXT_CHARS) { "Ответ слишком длинный для озвучивания" }
        service().synthesizeVoice(
            authorization = ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader(),
            request = VoiceSynthesisRequest(text = cleanText),
        ).use { response ->
            val declaredLength = response.contentLength()
            require(declaredLength in -1..MAX_CHAT_VOICE_AUDIO_BYTES) { "Голосовой ответ слишком большой" }
            val audio = withContext(Dispatchers.IO) { response.bytes() }
            require(audio.size <= MAX_CHAT_VOICE_AUDIO_BYTES) { "Голосовой ответ слишком большой" }
            require(audio.isSollVoiceWav()) { "Сервер вернул поврежденный голосовой ответ" }
            audio
        }
    }

    override suspend fun executeChatAction(
        actionId: String,
        action: String,
        taskId: String?,
        sessionId: String?,
    ): Result<SollChatActionResult> = runSuspendCatching {
        val cleanActionId = actionId.trim()
        val cleanAction = action.trim()
        require(cleanActionId.isNotBlank()) { "ID действия не задан" }
        require(cleanAction.isNotBlank()) { "Тип действия не задан" }
        val policy = requireNotNull(SollChatActionPolicyRegistry.resolve(cleanAction)) {
            "Действие не разрешено локальной политикой Android: $cleanAction"
        }
        service().executeChatAction(
            authorization = readAuthorizationHeader(),
            actionId = cleanActionId.encodedSollPathSegment(fieldName = "action_id"),
            request = ChatActionExecuteRequest(
                action = policy.type,
                taskId = taskId?.trim()?.takeIf { it.isNotBlank() },
                sessionId = sessionId?.trim()?.takeIf { it.isNotBlank() },
            ),
        ).toDomain()
    }

    override suspend fun issueDeviceToken(deviceId: String, pairingSecret: String): Result<SollDeviceToken> =
        runSuspendCatching {
            val cleanDeviceId = deviceId.trim()
            val cleanSecret = pairingSecret.trim()
            require(cleanDeviceId.isNotBlank()) { "Device ID не задан" }
            require(cleanSecret.isNotBlank()) { "Pairing secret не задан" }

            val challenge = service().createDeviceChallenge(cleanDeviceId)
            val nonce = UUID.randomUUID().toString().replace("-", "")
            val signature = buildSollDeviceTokenSignature(
                pairingSecret = cleanSecret,
                deviceId = challenge.deviceId,
                challengeId = challenge.challengeId,
                challenge = challenge.challenge,
                nonce = nonce,
            )
            service().issueDeviceToken(
                DeviceTokenRequest(
                    deviceId = challenge.deviceId,
                    challengeId = challenge.challengeId,
                    nonce = nonce,
                    signature = signature,
                )
            ).toDomain()
        }

    override suspend fun refreshDeviceToken(): Result<SollDeviceToken> = runSuspendCatching {
        val authorization = deviceAuthorizationHeader()
        require(authorization != null) { "Device bearer не настроен" }
        service().refreshDeviceToken(authorization).toDomain()
    }

    override suspend fun registerAndroidPushToken(
        token: String,
        provider: String,
    ): Result<SollAndroidPushRegistration> = runSuspendCatching {
        val cleanToken = token.trim()
        require(cleanToken.isNotBlank()) { "Push token не задан" }
        val authorization = ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader()
        service().registerAndroidPushToken(
            authorization = authorization,
            request = AndroidPushTokenRequest(
                token = cleanToken,
                provider = provider.trim().ifBlank { "fcm" },
                deviceId = settingsRepository.sollDeviceId.takeIf { it.isNotBlank() },
                appId = context.packageName,
                appVersion = BuildConfig.VERSION_NAME,
            ),
        ).toDomain()
    }

    override suspend fun publishAndroidLocation(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        altitudeMeters: Double?,
        provider: String,
        capturedAtMillis: Long,
        label: String,
        city: String,
        country: String,
        reason: String,
    ): Result<SollAndroidLocationStatus> = runSuspendCatching {
        val authorization = ensureDeviceAuthorizationHeader() ?: readAuthorizationHeader()
        service().updateAndroidLocation(
            authorization = authorization,
            request = AndroidLocationUpdateRequest(
                permissionGranted = true,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                altitudeMeters = altitudeMeters,
                provider = provider.trim().ifBlank { "android" },
                capturedAt = Instant.ofEpochMilli(capturedAtMillis).toString(),
                label = label.trim(),
                city = city.trim(),
                country = country.trim(),
                locale = Locale.getDefault().toLanguageTag(),
                reason = reason.trim().ifBlank { "android_user_approved_location" },
            ),
        ).toDomain()
    }

    override suspend fun createRawNote(
        title: String,
        content: String,
        tags: List<String>,
    ): Result<SollRawNote> = runSuspendCatching {
        val cleanTitle = title.trim()
        val cleanContent = content.trim()
        require(cleanTitle.isNotBlank()) { "Название заметки не задано" }
        require(cleanContent.isNotBlank()) { "Текст заметки не задан" }

        val request = CreateRawFileRequest(
            filename = buildRawNoteFilename(cleanTitle),
            content = buildRawNoteContent(
                title = cleanTitle,
                content = cleanContent,
                tags = tags,
            ),
        )
        service().createRawFile(authorizationHeader(), request).toDomain()
    }

    override suspend fun uploadRawFile(uri: Uri): Result<SollRawUpload> = runSuspendCatching {
        val metadata = resolveRawUploadMetadata(uri)
        val filename = buildRawUploadFilename(metadata.displayName)
        val requestBody = uriRequestBody(
            uri = uri,
            contentType = metadata.mimeType,
            contentLength = metadata.size,
        )
        val part = MultipartBody.Part.createFormData("file", filename, requestBody)
        service().uploadRawFile(authorizationHeader(), part).toDomain()
    }

    override suspend fun setTaskStatus(taskId: String, status: String): Result<SollTask> = runSuspendCatching {
        val cleanStatus = status.trim()
        require(cleanStatus.isNotBlank()) { "Статус задачи не задан" }
        service().setTaskStatus(
            authorizationHeader(),
            taskId.encodedSollPathSegment(fieldName = "task_id"),
            cleanStatus,
        ).taskResponse().toDomain()
    }

    override suspend fun updateTask(
        taskId: String,
        title: String,
        description: String,
    ): Result<SollTask> = runSuspendCatching {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotBlank()) { "Название задачи не задано" }
        service().updateTask(
            authorization = writeAuthorizationHeader(),
            taskId = taskId.encodedSollPathSegment(fieldName = "task_id"),
            request = TaskUpdateRequest(
                title = cleanTitle,
                description = description.trim(),
            ),
        ).taskResponse().toDomain()
    }

    override suspend fun moveTaskToToday(taskId: String): Result<SollTask> = runSuspendCatching {
        service().moveTaskToToday(
            authorizationHeader(),
            taskId.encodedSollPathSegment(fieldName = "task_id"),
        ).taskResponse().toDomain()
    }

    override suspend fun completeTask(taskId: String): Result<SollTask> = runSuspendCatching {
        service().completeTask(
            authorizationHeader(),
            taskId.encodedSollPathSegment(fieldName = "task_id"),
        ).taskResponse().toDomain()
    }

    override suspend fun deferTask(taskId: String): Result<SollTask> = runSuspendCatching {
        service().deferTask(
            authorizationHeader(),
            taskId.encodedSollPathSegment(fieldName = "task_id"),
        ).taskResponse().toDomain()
    }

    override suspend fun rejectTask(taskId: String): Result<SollTask> = runSuspendCatching {
        service().rejectTask(
            authorizationHeader(),
            taskId.encodedSollPathSegment(fieldName = "task_id"),
        ).taskResponse().toDomain()
    }

    override suspend fun getTaskGraph(includeDone: Boolean): Result<SollTaskGraph> {
        val liveResult = runSuspendCatching {
            service().getTaskGraph(
                authorization = readAuthorizationHeader(),
                includeDone = includeDone,
                maxNodes = 700,
            ).toDomain()
        }
        if (liveResult.isSuccess) {
            val graph = liveResult.getOrThrow()
            cacheTaskGraphBestEffort(graph, includeDone)
            return liveResult
        }

        var terminalResult = liveResult
        if (liveResult.exceptionOrNull()?.isHttpStatus(404) == true) {
            terminalResult = runSuspendCatching {
                val syncStatus = getAndroidSyncStatus().getOrThrow()
                buildTaskGraphFromBoard(syncStatus.tasks, includeDone = includeDone)
            }
            if (terminalResult.isSuccess) {
                val graph = terminalResult.getOrThrow()
                cacheTaskGraphBestEffort(graph, includeDone)
                return terminalResult
            }
        }

        return cachedTaskGraphOrNull(includeDone)?.let(Result.Companion::success) ?: terminalResult
    }

    private suspend fun cacheTaskGraphBestEffort(graph: SollTaskGraph, includeDone: Boolean) {
        try {
            val scope = taskGraphCacheScope(includeDone)
            val cached = taskGraphCacheDao.readGraph(scope)
            if (cached?.hasSameTaskGraphContent(graph) == true) return
            taskGraphCacheDao.replaceGraph(
                scope = scope,
                includeDone = includeDone,
                graph = graph,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Task graph cache update failed")
        }
    }

    private suspend fun cachedTaskGraphOrNull(includeDone: Boolean): SollTaskGraph? =
        try {
            taskGraphCacheDao.readGraph(taskGraphCacheScope(includeDone))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Task graph cache read failed")
            null
        }

    override suspend fun getTaskGraphDescendants(
        ancestorId: String,
        includeDone: Boolean,
        kind: String?,
        limit: Int,
    ): Result<List<SollTaskGraphNode>> = runSuspendCatching {
        require(ancestorId.isNotBlank()) { "Task graph ancestor ID must not be blank" }
        require(limit in 1..TASK_GRAPH_DESCENDANT_LIMIT) {
            "Task graph descendant limit must be between 1 and $TASK_GRAPH_DESCENDANT_LIMIT"
        }
        taskGraphCacheDao.readReachableNodes(
            scope = taskGraphCacheScope(includeDone),
            ancestorId = ancestorId,
            kind = kind,
            limit = limit,
        )?.map { it.toDomain() }
            ?: error("Task graph cache is not available")
    }

    override suspend fun getLearningItems(status: String?, limit: Int): Result<List<SollLearningItem>> =
        runSuspendCatching {
            service().getLearningItems(
                authorization = readAuthorizationHeader(),
                status = status?.takeIf { it.isNotBlank() },
                limit = limit.coerceIn(1, 200),
            ).items
                .map { it.toDomain() }
                .filterNot { it.isDailyTodoOrigin() }
        }.recoverCatching { error ->
            if (!error.isWorkspaceSnapshotFallbackStatus()) throw error
            getAndroidSyncStatus().getOrThrow().insights
                .filter { status.isNullOrBlank() || it.status == status }
                .filterNot { it.isDailyTodoOrigin() }
                .take(limit.coerceIn(1, 200))
        }

    override suspend fun updateLearningItemStatus(itemId: String, status: String): Result<SollLearningItem?> =
        runSuspendCatching {
            service().updateLearningItemStatus(
                authorization = authorizationHeader(),
                itemId = itemId.trim(),
                request = LearningItemStatusRequest(status = status.trim()),
            ).item?.toDomain()
        }

    override suspend fun createTaskFromLearningItem(itemId: String): Result<SollTask?> = runSuspendCatching {
        service().createTaskFromLearningItem(
            authorization = authorizationHeader(),
            itemId = itemId.trim(),
            request = LearningItemTaskRequest(priority = "B", markDone = true),
        ).task?.toDomain()
    }

    override suspend fun getRoadmap(): Result<SollRoadmap> =
        runSuspendCatching {
            service().getRoadmap(readAuthorizationHeader()).toDomain()
        }.recoverCatching { error ->
            if (!error.isHttpStatus(404)) throw error
            SollRoadmap(currentStage = "Relay без roadmap")
        }

    override suspend fun addRoadmapLine(stageId: String, line: String, text: String): Result<SollRoadmap> =
        runSuspendCatching {
            service().addRoadmapLine(
                authorization = authorizationHeader(),
                stageId = stageId.trim(),
                request = RoadmapLineRequest(line = line.trim(), text = text.trim()),
            ).toDomain()
        }

    override suspend fun updateRoadmapLine(
        stageId: String,
        line: String,
        newLine: String,
        text: String,
    ): Result<SollRoadmap> = runSuspendCatching {
        val cleanStageId = stageId.trim()
        val cleanLine = line.trim()
        val cleanNewLine = newLine.trim().ifBlank { cleanLine }
        val cleanText = text.trim()
        require(cleanStageId.isNotBlank()) { "ID этапа roadmap не задан" }
        require(cleanLine.isNotBlank()) { "Строка roadmap не задана" }
        require(cleanText.isNotBlank()) { "Текст roadmap не задан" }
        service().updateRoadmapLine(
            authorization = authorizationHeader(),
            stageId = cleanStageId,
            line = cleanLine,
            request = RoadmapLineUpdateRequest(line = cleanNewLine, text = cleanText),
        ).toDomain()
    }

    override suspend fun deleteRoadmapLine(stageId: String, line: String): Result<SollRoadmap> =
        runSuspendCatching {
            service().deleteRoadmapLine(
                authorization = authorizationHeader(),
                stageId = stageId.trim(),
                line = line.trim(),
            ).toDomain()
        }

    override suspend fun createTaskFromRoadmapLine(stageId: String, line: String): Result<SollTask?> =
        runSuspendCatching {
            val cleanStageId = stageId.trim()
            val cleanLine = line.trim()
            require(cleanStageId.isNotBlank()) { "ID этапа roadmap не задан" }
            require(cleanLine.isNotBlank()) { "Строка roadmap не задана" }
            service().createTaskFromRoadmapLine(
                authorization = authorizationHeader(),
                stageId = cleanStageId,
                line = cleanLine,
                request = RoadmapLineTaskRequest(priority = "B"),
            ).task?.toDomain()
        }

    override suspend fun listSources(scope: SollSourceScope): Result<List<SollMonitoredSource>> =
        runSuspendCatching {
            service().listSources(readAuthorizationHeader(), scope = scope.apiValue)
                .mapNotNull { it.toDomainOrNull() }
                .filterForSourceScope(scope)
        }.recoverCatching { error ->
            if (!error.isWorkspaceSnapshotFallbackStatus()) throw error
            getAndroidSyncStatus().getOrThrow().sources.filterForSourceScope(scope)
        }

    override suspend fun listSourceItems(sourceId: String, limit: Int): Result<List<SollSourceItem>> =
        runSuspendCatching {
            service().listSourceItems(
                authorization = readAuthorizationHeader(),
                sourceId = sourceId.trim(),
                limit = limit.coerceIn(1, 100),
            ).map { it.toDomain() }
        }.recoverCatching { error ->
            if (!error.isWorkspaceSnapshotFallbackStatus()) throw error
            getAndroidSyncStatus().getOrThrow()
                .sourceItemsBySource[sourceId.trim()]
                .orEmpty()
                .take(limit.coerceIn(1, 100))
        }

    override suspend fun listSourceItemsPage(
        sourceId: String,
        cursor: String,
        limit: Int,
    ): Result<SollSourceItemsPage> = runSuspendCatching {
        val cleanSourceId = sourceId.trim()
        require(cleanSourceId.isNotBlank()) { "ID источника не задан" }
        service().listSourceItemsPage(
            authorization = readAuthorizationHeader(),
            sourceId = cleanSourceId,
            cursor = cursor.trim().takeIf { it.isNotBlank() },
            limit = limit.coerceIn(1, 200),
        ).let { response ->
            SollSourceItemsPage(
                items = response.items.map { it.toDomain() },
                nextCursor = response.nextCursor,
                hasMore = response.hasMore,
                total = response.total,
                sourceEnabled = response.sourceEnabled,
                disabledReason = response.disabledReason,
            )
        }
    }.recoverCatching { error ->
        if (!error.isWorkspaceSnapshotFallbackStatus()) throw error
        val snapshotItems = getAndroidSyncStatus().getOrThrow()
            .sourceItemsBySource[sourceId.trim()]
            .orEmpty()
        SollSourceItemsPage(
            items = if (cursor.isBlank()) snapshotItems else emptyList(),
            nextCursor = "",
            hasMore = false,
            total = snapshotItems.size,
            sourceEnabled = true,
            disabledReason = "",
        )
    }

    override suspend fun createSource(
        name: String,
        target: String,
        scope: SollSourceScope,
        sourceType: String,
    ): Result<SollMonitoredSource> = runSuspendCatching {
        val cleanTarget = target.trim()
        val cleanSourceType = sourceType.trim().lowercase().takeIf { it in SOURCE_TYPES }
            ?: SOURCE_TYPE_WEB
        require(cleanTarget.isNotBlank()) { "URL источника не задан" }
        service().createSource(
            authorization = writeAuthorizationHeader(),
            request = MonitoredSourceCreateRequest(
                name = name.trim().takeIf { it.isNotBlank() },
                sourceType = cleanSourceType,
                scope = scope.apiValue,
                target = cleanTarget,
                tags = listOf("android", scope.apiValue),
            ),
        ).toDomain()
    }

    override suspend fun updateSource(
        sourceId: String,
        name: String,
        description: String,
        tags: List<String>,
        enabled: Boolean,
    ): Result<SollMonitoredSource> = runSuspendCatching {
        val cleanSourceId = sourceId.trim()
        require(cleanSourceId.isNotBlank()) { "ID источника не задан" }
        service().updateSource(
            authorization = writeAuthorizationHeader(),
            sourceId = cleanSourceId,
            request = MonitoredSourceUpdateRequest(
                name = name.trim().takeIf { it.isNotBlank() },
                description = description.trim(),
                tags = tags
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct(),
                enabled = enabled,
            ),
        ).toDomain()
    }

    override suspend fun deleteSource(sourceId: String): Result<Boolean> = runSuspendCatching {
        service().deleteSource(writeAuthorizationHeader(), sourceId.trim())
        true
    }

    override suspend fun checkSource(sourceId: String): Result<Boolean> = runSuspendCatching {
        service().checkSource(writeAuthorizationHeader(), sourceId.trim()).changed
    }

    override suspend fun createTaskFromSourceItem(sourceId: String, itemId: String): Result<SollTask?> =
        runSuspendCatching {
            val cleanSourceId = sourceId.trim()
            val cleanItemId = itemId.trim()
            require(cleanSourceId.isNotBlank()) { "ID источника не задан" }
            require(cleanItemId.isNotBlank()) { "ID материала не задан" }
            service().createTaskFromSourceItem(
                authorization = writeAuthorizationHeader(),
                sourceId = cleanSourceId,
                itemId = cleanItemId,
                request = SourceItemTaskRequest(priority = "B"),
            ).task?.toDomain()
        }

    override suspend fun getBookStatus(): Result<SollBookStatus> = runSuspendCatching {
        service().getBookStatus(authorizationHeader()).toDomain()
    }

    override suspend fun getCurrentBookResults(): Result<SollBookCurrentResults> = runSuspendCatching {
        service().getCurrentBookResults(authorizationHeader()).toDomain()
    }

    override suspend fun selectBook(number: Int): Result<SollBookSelection> = runSuspendCatching {
        require(number > 0) { "Номер книги должен быть положительным" }
        service().selectBook(authorizationHeader(), BookSelectRequest(number)).toDomain()
    }

    override suspend fun downloadBook(format: String): Result<SollBookDownload> = runSuspendCatching {
        val cleanFormat = format.trim()
        require(cleanFormat.isNotBlank()) { "Формат книги не задан" }
        service().downloadBook(authorizationHeader(), BookDownloadRequest(cleanFormat)).toDomain()
    }

    override suspend fun downloadPreferredBook(): Result<SollBookDownload> = runSuspendCatching {
        service().downloadPreferredBook(authorizationHeader()).toDomain()
    }

    override suspend fun downloadSelectedBooks(
        numbers: List<Int>,
        processAfter: Boolean,
    ): Result<SollBookBatchDownload> = runSuspendCatching {
        val cleanNumbers = numbers.distinct().filter { it > 0 }.take(50)
        require(cleanNumbers.isNotEmpty()) { "Выберите книги для скачивания" }
        service().downloadSelectedBooks(
            authorization = authorizationHeader(),
            request = BookDownloadSelectedRequest(
                numbers = cleanNumbers,
                processAfter = processAfter,
            ),
        ).toDomain()
    }

    override suspend fun processDownloadedBook(filePath: String?): Result<SollBookProcessResult> = runSuspendCatching {
        service().processDownloadedBook(
            authorization = authorizationHeader(),
            request = BookProcessDownloadedRequest(filePath = filePath?.trim()?.takeIf { it.isNotBlank() }),
        ).toDomain()
    }

    override suspend fun cancelBookSession(): Result<SollBookActionResult> = runSuspendCatching {
        service().cancelBookSession(authorizationHeader()).toDomain()
    }

    override suspend fun askMetaCoordinator(
        request: MetaCoordinatorRequest,
    ): Result<MetaCoordinatorResponse> {
        val safeRequest = request.safeForServer()
        return runSuspendCatching {
            service().askAssistant(
                authorization = authorizationHeader(),
                request = AssistantAskRequest(
                    question = MetaCoordinatorServerBridge.toAssistantQuestion(safeRequest),
                    allowWikiUpdates = false,
                ),
            ).toDomain(safeRequest)
        }.recover { error ->
            MetaCoordinatorFallback.unavailable(
                request = safeRequest,
                reason = error.message ?: "ошибка подключения",
            )
        }
    }

    override suspend fun askModelChat(
        request: ModelChatRequest,
    ): Result<ModelChatResponse> {
        val safeRequest = request.safeForServer()
        return runSuspendCatching {
            service().askAssistant(
                authorization = authorizationHeader(),
                request = AssistantAskRequest(
                    question = ModelChatServerBridge.toAssistantQuestion(safeRequest),
                    allowWikiUpdates = false,
                ),
            ).toDomain(safeRequest)
        }.recover { error ->
            ModelChatFallback.unavailable(
                request = safeRequest,
                reason = error.message ?: "ошибка подключения",
            )
        }
    }

    override suspend fun getProtocolSchema(): Result<SollProtocolSchema> = runSuspendCatching {
        service().getProtocolSchema(readAuthorizationHeader()).toDomain()
    }

    override suspend fun getMeshStatus(): Result<SollMeshStatus> = runSuspendCatching {
        service().getMeshStatus(readAuthorizationHeader()).toDomain()
    }

    override suspend fun getMeshOutbox(limit: Int): Result<List<SollMeshOutboxItem>> = runSuspendCatching {
        service().getMeshOutbox(
            authorization = readAuthorizationHeader(),
            limit = limit.coerceIn(1, 100),
        ).outbox.map { it.toDomain() }
    }

    override suspend fun claimNextMeshOutbox(toPeer: String?): Result<SollMeshOutboxItem?> = runSuspendCatching {
        service().claimNextMeshOutbox(
            authorization = readAuthorizationHeader(),
            toPeer = toPeer?.trim()?.takeIf { it.isNotBlank() },
        ).outbox?.toDomain()
    }

    override suspend fun ackMeshOutbox(
        outboundId: String,
        claimToken: String?,
    ): Result<SollMeshOutboxItem> = runSuspendCatching {
        val cleanId = outboundId.trim()
        require(cleanId.isNotBlank()) { "ID outbox-сообщения не задан" }
        val cleanClaimToken = claimToken?.trim()?.takeIf { it.isNotBlank() }
        service().ackMeshOutbox(
            authorization = readAuthorizationHeader(),
            outboundId = cleanId,
            request = MeshOutboxAckRequest(claimToken = cleanClaimToken),
        ).toDomain()
    }

    override suspend fun markMeshOutboxAttempt(
        outboundId: String,
        success: Boolean,
        error: String?,
        claimToken: String?,
    ): Result<SollMeshOutboxItem> = runSuspendCatching {
        val cleanId = outboundId.trim()
        require(cleanId.isNotBlank()) { "ID outbox-сообщения не задан" }
        val cleanClaimToken = claimToken?.trim()?.takeIf { it.isNotBlank() }
        service().markMeshOutboxAttempt(
            authorization = readAuthorizationHeader(),
            outboundId = cleanId,
            request = MeshOutboxAttemptRequest(
                success = success,
                error = error?.trim()?.takeIf { it.isNotBlank() },
                claimToken = cleanClaimToken,
            ),
        ).toDomain()
    }

    override suspend fun retryMeshOutbox(outboundId: String): Result<SollMeshOutboxItem> = runSuspendCatching {
        val cleanId = outboundId.trim()
        require(cleanId.isNotBlank()) { "ID outbox-сообщения не задан" }
        service().retryMeshOutbox(
            authorization = readAuthorizationHeader(),
            outboundId = cleanId,
        ).toDomain()
    }

    override suspend fun getGadgetSnapshots(): Result<List<GadgetCloudSnapshot>> = runSuspendCatching {
        service().getGadgets(readAuthorizationHeader()).map { it.toDomain() }
    }

    override suspend fun getGadgetLatest(gadgetId: String): Result<GadgetCloudSnapshot> = runSuspendCatching {
        val cleanId = gadgetId.trim()
        require(cleanId.isNotBlank()) { "ID гаджета не задан" }
        service().getGadgetLatest(
            authorization = readAuthorizationHeader(),
            gadgetId = cleanId,
        ).toDomain()
    }

    override suspend fun createGadgetCommand(
        gadgetId: String,
        command: String,
        params: Map<String, Any?>,
        ttlSeconds: Int,
    ): Result<GadgetCloudCommand> = runSuspendCatching {
        val cleanId = gadgetId.trim()
        val cleanCommand = command.trim()
        require(cleanId.isNotBlank()) { "ID гаджета не задан" }
        require(cleanCommand.isNotBlank()) { "Команда гаджета не задана" }
        service().createGadgetCommand(
            authorization = readAuthorizationHeader(),
            gadgetId = cleanId,
            request = GadgetCommandCreateRequest(
                command = cleanCommand,
                params = params,
                ttlSeconds = ttlSeconds.coerceIn(1, 3600),
            ),
        ).toDomain()
    }

    override suspend fun getGadgetCommands(gadgetId: String, limit: Int): Result<List<GadgetCloudCommand>> =
        runSuspendCatching {
            val cleanId = gadgetId.trim()
            require(cleanId.isNotBlank()) { "ID гаджета не задан" }
            service().getGadgetCommands(
                authorization = readAuthorizationHeader(),
                gadgetId = cleanId,
                limit = limit.coerceIn(1, 200),
            ).map { it.toDomain() }
        }

    override suspend fun claimGadgetCommand(
        gadgetId: String,
        workerId: String,
        leaseSeconds: Int,
    ): Result<GadgetCloudCommand?> = runSuspendCatching {
        val cleanId = gadgetId.trim()
        require(cleanId.isNotBlank()) { "ID гаджета не задан" }
        service().claimGadgetCommand(
            authorization = readAuthorizationHeader(),
            gadgetId = cleanId,
            request = GadgetCommandClaimRequest(
                workerId = workerId.trim(),
                leaseSeconds = leaseSeconds.coerceIn(5, 3600),
            ),
        )?.toDomain()
    }

    override suspend fun ackGadgetCommand(
        gadgetId: String,
        commandId: String,
        workerId: String,
    ): Result<GadgetCloudCommand> = runSuspendCatching {
        val cleanGadgetId = gadgetId.trim()
        val cleanCommandId = commandId.trim()
        require(cleanGadgetId.isNotBlank()) { "ID гаджета не задан" }
        require(cleanCommandId.isNotBlank()) { "ID команды не задан" }
        service().ackGadgetCommand(
            authorization = readAuthorizationHeader(),
            gadgetId = cleanGadgetId,
            commandId = cleanCommandId,
            request = GadgetCommandAckRequest(workerId = workerId.trim()),
        ).toDomain()
    }

    override suspend fun postGadgetCommandResult(
        gadgetId: String,
        commandId: String,
        success: Boolean,
        workerId: String,
        payload: Map<String, Any?>,
        error: String,
    ): Result<GadgetCloudCommand> = runSuspendCatching {
        val cleanGadgetId = gadgetId.trim()
        val cleanCommandId = commandId.trim()
        require(cleanGadgetId.isNotBlank()) { "ID гаджета не задан" }
        require(cleanCommandId.isNotBlank()) { "ID команды не задан" }
        service().postGadgetCommandResult(
            authorization = readAuthorizationHeader(),
            gadgetId = cleanGadgetId,
            commandId = cleanCommandId,
            request = GadgetCommandResultRequest(
                success = success,
                payload = payload,
                error = error.trim(),
                workerId = workerId.trim(),
            ),
        ).toDomain()
    }

    override suspend fun postManualGadgetCommandResult(
        gadgetId: String,
        commandId: String,
        success: Boolean,
        payload: Map<String, Any?>,
        error: String,
    ): Result<GadgetCloudCommand> = runSuspendCatching {
        val cleanGadgetId = gadgetId.trim()
        val cleanCommandId = commandId.trim()
        require(cleanGadgetId.isNotBlank()) { "ID гаджета не задан" }
        require(cleanCommandId.isNotBlank()) { "ID команды не задан" }
        service().postManualGadgetCommandResult(
            authorization = authorizationHeader(),
            gadgetId = cleanGadgetId,
            commandId = cleanCommandId,
            request = GadgetCommandResultRequest(
                success = success,
                payload = payload,
                error = error.trim(),
            ),
        ).toDomain()
    }

    override suspend fun getGadgetHistory(
        gadgetId: String,
        metric: String?,
        from: String?,
        to: String?,
        limit: Int,
    ): Result<GadgetCloudHistory> = runSuspendCatching {
        val cleanId = gadgetId.trim()
            require(cleanId.isNotBlank()) { "ID гаджета не задан" }
            service().getGadgetHistory(
                authorization = readAuthorizationHeader(),
                gadgetId = cleanId,
                metric = metric?.trim()?.takeIf { it.isNotBlank() },
            from = from?.trim()?.takeIf { it.isNotBlank() },
            to = to?.trim()?.takeIf { it.isNotBlank() },
            limit = limit.coerceIn(1, 1000),
        ).toDomain()
    }

    override suspend fun getGadgetEvents(gadgetId: String, limit: Int): Result<List<GadgetCloudEvent>> =
        runSuspendCatching {
            val cleanId = gadgetId.trim()
            require(cleanId.isNotBlank()) { "ID гаджета не задан" }
            service().getGadgetEvents(
                authorization = readAuthorizationHeader(),
                gadgetId = cleanId,
                limit = limit.coerceIn(1, 200),
            ).map { it.toDomain(fallbackGadgetId = cleanId) }
        }

    private fun service(): SollApiService {
        val baseUrl = normalizeSollBaseUrl(settingsRepository.sollServerUrl)
        require(baseUrl.isNotBlank()) { "URL сервера Soll не задан" }
        val apiPrefix = normalizeSollApiPathPrefix(settingsRepository.sollApiPathPrefix)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient.withSollApiPrefix(apiPrefix))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SollApiService::class.java)
    }

    private fun readAuthorizationHeader(): String? {
        return deviceAuthorizationHeader() ?: authorizationHeader()
    }

    private suspend fun refreshAwareReadAuthorizationHeader(): String? {
        val deviceAuthorization = ensureDeviceAuthorizationHeader()
        return selectRefreshAwareAuthorizationHeader(
            deviceAuthorization = deviceAuthorization,
            deviceTokenNeedsRefresh = deviceTokenNeedsRefresh(),
            fallbackAuthorization = authorizationHeader(),
        )
    }

    private suspend fun writeAuthorizationHeader(): String? {
        return ensureDeviceAuthorizationHeader() ?: authorizationHeader()
    }

    private suspend fun ensureDeviceAuthorizationHeader(): String? {
        val current = deviceAuthorizationHeader()
        if (current != null && !deviceTokenNeedsRefresh()) {
            return current
        }

        val deviceId = settingsRepository.sollDeviceId.trim()
        val pairingSecret = settingsRepository.sollDevicePairingSecret.trim()
        if (deviceId.isBlank() || pairingSecret.isBlank()) {
            return current
        }

        val refreshed = if (current != null && settingsRepository.sollDeviceTokenExpiresAt.isNotBlank()) {
            runSuspendCatching {
                val response = service().refreshDeviceToken(current)
                persistDeviceToken(response.toDomain())
                true
            }.getOrDefault(false)
        } else {
            false
        }

        if (!refreshed) {
            val token = issueDeviceToken(deviceId, pairingSecret).getOrNull()
            if (token != null) {
                persistDeviceToken(token)
            }
        }

        return deviceAuthorizationHeader()
    }

    private fun deviceAuthorizationHeader(): String? {
        val deviceToken = settingsRepository.sollDeviceAccessToken.trim()
        return deviceToken.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private fun authorizationHeader(): String? {
        val token = settingsRepository.sollAccessToken.trim()
        return token.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
    }

    private fun persistDeviceToken(token: SollDeviceToken) {
        settingsRepository.sollDeviceAccessToken = token.accessToken
        settingsRepository.sollDeviceTokenExpiresAt = token.expiresAt
    }

    private fun deviceTokenNeedsRefresh(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = settingsRepository.sollDeviceTokenExpiresAt.trim()
        if (expiresAt.isBlank()) return true
        val expiresAtMillis = parseIsoInstantMillis(expiresAt) ?: return true
        return expiresAtMillis - nowMillis <= DEVICE_TOKEN_REFRESH_SAFETY_MS
    }

    private fun encryptedEnvelopeOrNull(
        content: String,
        metadata: Map<String, Any?>,
        aad: String,
        extra: Map<String, Any?> = emptyMap(),
    ): SecurePayloadEnvelopeRequest? {
        val pairingSecret = settingsRepository.sollDevicePairingSecret.trim()
        if (pairingSecret.isBlank()) return null
        val key = MessageDigest.getInstance("SHA-256").digest(pairingSecret.toByteArray(Charsets.UTF_8))
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        val plaintext = org.json.JSONObject().apply {
            if (content.isNotBlank()) put("content", content)
            if (metadata.isNotEmpty()) put("metadata", org.json.JSONObject(metadata))
            extra.forEach { (key, value) ->
                if (value != null && value.toString().isNotBlank()) put(key, value)
            }
        }.toString().toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, nonce),
        )
        val aadBytes = aad.toByteArray(Charsets.UTF_8)
        cipher.updateAAD(aadBytes)
        val ciphertext = cipher.doFinal(plaintext)
        return SecurePayloadEnvelopeRequest(
            nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            aad = aad,
            keyId = settingsRepository.sollDeviceId.trim(),
        )
    }

    private fun cacheAndroidSyncStatus(response: AndroidSyncStatusResponse) {
        try {
            context.getSharedPreferences(SOLL_CACHE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ANDROID_SYNC_STATUS_JSON, androidSyncStatusJsonAdapter.toJson(response))
                .putLong(KEY_ANDROID_SYNC_STATUS_CACHED_AT, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
            // Cache failures must not break live server responses.
        }
    }

    private fun cachedAndroidSyncStatusOrNull(error: Throwable?): SollAndroidSyncStatus? {
        return try {
            val prefs = context.getSharedPreferences(SOLL_CACHE_PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_ANDROID_SYNC_STATUS_JSON, null) ?: return null
            val response = androidSyncStatusJsonAdapter.fromJson(json) ?: return null
            val warning = "Сервер недоступен, показан локальный кэш: ${error?.message ?: "ошибка подключения"}"
            response.toDomain(
                fromCache = true,
                cachedAtMillis = prefs.getLong(KEY_ANDROID_SYNC_STATUS_CACHED_AT, 0L).takeIf { it > 0L },
                extraWarnings = listOf(warning),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun SollHealthResponse.toDomain(): SollHealth =
        SollHealth(
            status = status,
            schedulerRunning = schedulerRunning,
            vaultAccessible = vaultAccessible,
            jobsCount = jobsCount,
            androidPush = SollAndroidPushHealth(
                enabled = androidPush.enabled,
                configured = androidPush.configured,
                tokenCount = androidPush.tokenCount,
            ),
            checkedAt = checkedAt,
        )

    private fun AndroidSyncStatusResponse.toDomain(
        fromCache: Boolean = false,
        cachedAtMillis: Long? = null,
        extraWarnings: List<String> = emptyList(),
    ): SollAndroidSyncStatus =
        SollAndroidSyncStatus(
            serverTime = serverTime,
            health = health.toDomain(),
            tasks = tasks.toDomain().withoutDailyTodoTasks(),
            device = device?.toDomain(),
            node = node.toDomain(),
            activeNodes = activeNodes.values
                .map { it.toDomain() }
                .sortedWith(compareByDescending<SollNodeIdentity> { it.active }
                    .thenByDescending { it.isPrimary }
                    .thenByDescending { it.priority }
                    .thenBy { it.nodeId }),
            briefing = briefing?.toDomain(),
            chat = chat.toDomain(),
            protocol = protocol?.toDomain(),
            insights = workspace.insights.items.map { it.toDomain() },
            sources = workspace.sources.mapNotNull { it.toDomainOrNull() },
            sourceItemsBySource = workspace.sourceItemsBySource.mapValues { (_, items) ->
                items.map { it.toDomain() }
            },
            warnings = (warnings + extraWarnings).distinct(),
            fromCache = fromCache,
            cachedAtMillis = cachedAtMillis,
        )

    private fun com.soll.data.api.SollNodeIdentityResponse.toDomain(): SollNodeIdentity =
        SollNodeIdentity(
            nodeId = nodeId,
            nodeName = nodeName,
            nodeRole = nodeRole,
            isPrimary = isPrimary,
            priority = priority,
            capabilities = capabilities,
            active = active,
            lastSeenAt = lastSeenAt,
        )

    private fun com.soll.data.api.AndroidChatSyncResponse.toDomain(): SollAndroidChatSync =
        SollAndroidChatSync(
            primarySessionId = primarySessionId,
            recentSessions = recentSessions.map { it.toDomain() },
            recentMessages = recentMessages.map { it.toDomain() },
            lastMessageId = lastMessageId,
            unreadCount = unreadCount,
            pendingActionsCount = pendingActionsCount,
            encryptionRequired = encryptionRequired,
            streamEndpoint = streamEndpoint,
            endpoints = endpoints,
        )

    private fun ChatSessionSummaryResponse.toDomain(): SollChatSession =
        SollChatSession(
            sessionId = sessionId,
            title = title,
            updatedAt = updatedAt,
            messageCount = messageCount,
        )

    private fun ChatSessionCreateResponse.toDomain(): SollChatSession =
        SollChatSession(
            sessionId = sessionId,
            title = title,
            updatedAt = "",
            messageCount = 0,
        )

    private fun ChatMessageResponse.toDomain(): SollChatMessage =
        SollChatMessage(
            id = id,
            sessionId = sessionId,
            role = role,
            content = content,
            createdAt = createdAt,
            metadata = metadata,
        )

    private fun ChatActionExecuteResponse.toDomain(): SollChatActionResult =
        SollChatActionResult(
            actionId = actionId,
            action = action,
            taskId = taskId,
            status = status,
            task = task?.toDomain(),
        )

    private fun TaskGraphResponse.toDomain(): SollTaskGraph =
        SollTaskGraph(
            nodes = nodes.map { it.toDomain() },
            edges = edges.map { it.toDomain() },
            totalTasks = totalTasks,
            truncated = truncated,
        )

    private fun TaskGraphNodeResponse.toDomain(): SollTaskGraphNode =
        SollTaskGraphNode(
            id = id,
            kind = kind,
            label = label,
            status = status,
            priority = priority,
            projectId = projectId,
            taskId = taskId,
            sourceRef = sourceRef,
            count = count,
        )

    private fun TaskGraphEdgeResponse.toDomain(): SollTaskGraphEdge =
        SollTaskGraphEdge(
            id = id,
            source = source,
            target = target,
            kind = kind,
            label = label,
        )

    private fun LearningItemResponse.toDomain(): SollLearningItem =
        SollLearningItem(
            id = id,
            title = title,
            status = status,
            nextAction = nextAction,
            sourceRef = sourceRef,
            seenCount = seenCount,
            tags = tags,
            scope = scope,
            origin = origin,
        )

    private fun RoadmapResponse.toDomain(): SollRoadmap =
        SollRoadmap(
            currentStage = currentStage,
            stages = stages.map { it.toDomain() },
            readiness = readiness.map { it.toDomain() },
            updated = updated,
        )

    private fun RoadmapStageResponse.toDomain(): SollRoadmapStage =
        SollRoadmapStage(
            id = id,
            label = label,
            status = status,
            lines = lines.map { it.toDomain() },
        )

    private fun RoadmapLineResponse.toDomain(): SollRoadmapLine =
        SollRoadmapLine(line = line, text = text)

    private fun RoadmapReadinessResponse.toDomain(): SollRoadmapReadiness =
        SollRoadmapReadiness(area = area, percent = percent, gap = gap)

    private fun MonitoredSourceResponse.toDomain(): SollMonitoredSource =
        toDomainOrNull()
            ?: SollMonitoredSource(
                id = id,
                name = name,
                sourceType = sourceType,
                scope = SollSourceScope.PROJECT_SOLL,
                target = target,
                description = description,
                tags = tags,
                enabled = enabled,
                lastResult = lastResult,
                itemsSeen = itemsSeen,
                newItemsLastCheck = newItemsLastCheck,
            )

    private fun MonitoredSourceResponse.toDomainOrNull(): SollMonitoredSource? {
        val sourceScope = SollSourceScope.entries.firstOrNull { it.apiValue == scope } ?: return null
        return toDomain(sourceScope)
    }

    private fun MonitoredSourceResponse.toDomain(sourceScope: SollSourceScope): SollMonitoredSource =
        SollMonitoredSource(
            id = id,
            name = name,
            sourceType = sourceType,
            scope = sourceScope,
            target = target,
            description = description,
            tags = tags,
            enabled = enabled,
            lastResult = lastResult,
            itemsSeen = itemsSeen,
            newItemsLastCheck = newItemsLastCheck,
        )

    private fun SourceItemResponse.toDomain(): SollSourceItem =
        SollSourceItem(
            itemId = itemId,
            title = title,
            sourceUrl = sourceUrl,
            contentPreview = contentPreview,
            summary = summary,
            usefulness = usefulness,
            reasoning = reasoning,
            evidenceLevel = evidenceLevel,
            projectFit = projectFit,
            actionability = actionability,
            dualUseRisk = dualUseRisk,
            dualUseAction = dualUseAction,
            safeNextStep = safeNextStep,
            needsDeepDive = needsDeepDive,
            rawFile = rawFile.orEmpty(),
            notifiedAt = notifiedAt.orEmpty(),
            lastStatus = lastStatus,
            auditRef = auditRef,
            evidenceRef = evidenceRef,
            verificationArtifact = verificationArtifact,
            statusReason = statusReason,
            deliveryStatus = deliveryStatus,
            linkPreview = linkPreview.orEmpty(),
        )

    private fun SollDeviceResponse.toDomain(): SollDevice =
        SollDevice(
            id = id,
            name = name,
            enabled = enabled,
            scopes = scopes,
            lastSeenAt = lastSeenAt,
        )

    private fun com.soll.data.api.DeviceTokenResponse.toDomain(): SollDeviceToken =
        SollDeviceToken(
            accessToken = accessToken,
            tokenType = tokenType,
            expiresAt = expiresAt,
            expiresIn = expiresIn,
        )

    private fun SollBriefingResponse.toDomain(): SollBriefing =
        SollBriefing(
            filename = filename,
            path = path,
            content = content,
            createdAt = createdAt,
        )

    private fun SollTaskBoardResponse.toDomain(): SollTaskBoard =
        SollTaskBoard(
            today = today.map { it.toDomain() },
            blocked = blocked.map { it.toDomain() },
            inbox = inbox.map { it.toDomain() },
            stale = stale.map { it.toDomain() },
            deferred = deferred.map { it.toDomain() },
            doneRecent = doneRecent.map { it.toDomain() },
            counts = counts?.toDomain(),
            limitPerSection = limitPerSection,
        )

    private fun DailyTaskListResponse.toDomain(): SollDailyTaskList =
        SollDailyTaskList(
            date = date,
            sourcePath = sourcePath,
            tasks = tasks.map { it.toDomain() },
            createdTaskId = createdTaskId,
        )

    private fun DailyTaskDetailResponse.toDomain(): SollDailyTaskDetail =
        SollDailyTaskDetail(
            date = date,
            sourcePath = sourcePath,
            task = task.toDomain(),
            geo = geo.toDomain(),
            sourceMatches = sourceMatches,
            research = research?.toDomain(),
        )

    private fun DailyTaskItemResponse.toDomain(): SollDailyTask =
        SollDailyTask(
            id = id,
            text = text,
            done = done,
            line = line,
            attachments = attachments.map { it.toDomain() },
        )

    private fun DailyTaskAttachmentResponse.toDomain(): SollDailyTaskAttachment =
        SollDailyTaskAttachment(
            id = id,
            taskId = taskId,
            filename = filename,
            contentType = contentType,
            size = size,
            path = path,
            analysisStatus = analysisStatus,
            analysisSummary = analysisSummary,
            ocrText = ocrText,
            searchTerms = searchTerms,
            createdAt = createdAt,
        )

    private fun DailyTaskGeoResponse.toDomain(): SollDailyTaskGeo =
        SollDailyTaskGeo(
            locationLabel = locationLabel,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            capturedAt = capturedAt,
        )

    private fun DailyTaskResearchResponse.toDomain(): SollDailyTaskResearch =
        SollDailyTaskResearch(
            taskId = taskId,
            query = query,
            summary = summary,
            localResults = localResults,
            sourceResults = sourceResults,
            webResults = webResults,
            createdAt = createdAt,
        )

    private fun SollTaskBoardCountsResponse.toDomain(): SollTaskBoardCounts =
        SollTaskBoardCounts(
            today = today,
            blocked = blocked,
            inbox = inbox,
            stale = stale,
            deferred = deferred,
            doneRecent = doneRecent,
        )

    private fun SollTaskResponse.toDomain(): SollTask =
        SollTask(
            id = id,
            title = title,
            description = description,
            sourceRef = sourceRef,
            projectName = projectName,
            status = status,
            priority = priority,
            dueDate = dueDate,
            tags = tags,
            approvalId = approvalId,
            toolJobId = toolJobId,
            executionState = executionState,
            outcomeArtifacts = outcomeArtifacts,
            completionKind = completionKind,
            completionResult = completionResult,
            completionEvidence = completionEvidence,
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

    private fun RawFileResponse.toDomain(): SollRawNote =
        SollRawNote(
            filename = filename,
            path = path,
            message = message,
        )

    private fun RawUploadResponse.toDomain(): SollRawUpload =
        SollRawUpload(
            filename = filename,
            path = path,
            size = size,
            message = message,
        )

    private fun SollBookStatusResponse.toDomain(): SollBookStatus =
        SollBookStatus(
            userbotRunning = userbotRunning,
            session = session.toDomain(),
        )

    private fun BookStatusSessionResponse.toDomain(): SollBookSession =
        SollBookSession(
            active = active,
            query = query,
            state = state,
            resultsCount = resultsCount,
            totalResultsCount = totalResultsCount,
            duplicatesCount = duplicatesCount,
            maxResults = maxResults,
            requestMode = requestMode,
            selectedBook = selectedBook,
            formatsCount = formatsCount,
            downloadedFiles = downloadedFiles.map { it.toDomain() },
            createdAt = createdAt,
        )

    private fun BookCurrentResultsResponse.toDomain(): SollBookCurrentResults =
        SollBookCurrentResults(
            active = active,
            query = query,
            state = state,
            results = results.map { it.toDomain() },
            selectedBook = selectedBook?.toDomain(),
            formats = formats.map { it.toDomain() },
            downloadedFiles = downloadedFiles.map { it.toDomain() },
        )

    private fun BookSelectResponse.toDomain(): SollBookSelection =
        SollBookSelection(
            book = book?.toDomain(),
            formats = formats.map { it.toDomain() },
            preferredFormats = preferredFormats.map { it.toDomain() },
            rawResponse = rawResponse,
        )

    private fun BookDownloadResponse.toDomain(): SollBookDownload =
        SollBookDownload(
            duplicateSkipped = duplicateSkipped,
            message = message,
            error = error,
            book = book?.toDomain(),
            format = format,
            filePath = filePath,
            metadataPath = metadataPath,
            dedupeKey = dedupeKey,
            rawResponse = rawResponse,
        )

    private fun BookBatchDownloadResponse.toDomain(): SollBookBatchDownload =
        SollBookBatchDownload(
            requested = requested,
            downloaded = downloaded,
            failed = failed,
            results = results.map { it.toDomain() },
        )

    private fun BookBatchDownloadItemResponse.toDomain(): SollBookBatchItem =
        SollBookBatchItem(
            number = number,
            success = success,
            error = error,
            duplicateSkipped = duplicateSkipped,
            message = message,
            book = book?.toDomain(),
            format = format,
            filePath = filePath,
            metadataPath = metadataPath,
            dedupeKey = dedupeKey,
            processResult = processResult?.toDomain(),
        )

    private fun BookProcessResponse.toDomain(): SollBookProcessResult =
        SollBookProcessResult(
            success = success,
            message = message,
            error = error,
            file = file,
            processedFile = processedFile,
            wikiEntry = wikiEntry,
            wikiUpdated = wikiUpdated,
            alreadyProcessed = alreadyProcessed,
            fallbackUsed = fallbackUsed,
            fallbackReason = fallbackReason,
            archiveSignature = archiveSignature,
        )

    private fun BookActionResponse.toDomain(): SollBookActionResult =
        SollBookActionResult(
            success = success,
            message = message,
        )

    private fun AssistantAskResponse.toDomain(request: MetaCoordinatorRequest): MetaCoordinatorResponse =
        MetaCoordinatorServerBridge.fromAssistantAnswer(
            request = request,
            answer = answer,
            usedTopics = usedTopics,
            confidence = confidence,
            gaps = gaps,
            contradictions = contradictions,
        )

    private fun AssistantAskResponse.toDomain(request: ModelChatRequest): ModelChatResponse =
        ModelChatServerBridge.fromAssistantAnswer(
            request = request,
            answer = answer,
        )

    private fun SollProtocolSchemaResponse.toDomain(): SollProtocolSchema =
        SollProtocolSchema(
            version = version,
            auth = auth.toDomain(),
            security = SollProtocolSecurity(
                pqcStatus = security.postQuantum.status,
                pqcProtectionActive = security.postQuantum.protectionActive,
                pqcTarget = security.postQuantum.target,
                pqcMigrationPhases = security.postQuantum.migrationPhases,
            ),
            gadgetCommandRoutes = scopes["gadget:commands"].orEmpty(),
            androidTransport = transports["android"]?.toDomain() ?: SollProtocolTransport(),
            workerContracts = workerContracts.mapValues { (_, contract) -> contract.toDomain() },
            gadgetDiscovery = gadgetDiscovery?.toDomain(),
        )

    private fun AndroidProtocolBootstrapResponse.toDomain(): SollProtocolBootstrap =
        SollProtocolBootstrap(
            version = version,
            auth = auth.toDomain(),
            transport = transport.toDomain(),
            workerContracts = workerContracts.mapValues { (_, contract) -> contract.toDomain() },
        )

    private fun AndroidPushTokenResponse.toDomain(): SollAndroidPushRegistration =
        SollAndroidPushRegistration(
            success = success,
            provider = provider,
            enabled = enabled,
            tokenCount = tokenCount,
            reason = reason,
        )

    private fun AndroidLocationStatusResponse.toDomain(): SollAndroidLocationStatus =
        SollAndroidLocationStatus(
            available = available,
            needsAndroidLocation = needsAndroidLocation,
            stale = stale,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            provider = provider,
            label = label,
            city = city,
            country = country,
            capturedAt = capturedAt,
            receivedAt = receivedAt,
        )

    private fun SollProtocolAuthResponse.toDomain(): SollProtocolAuth =
        SollProtocolAuth(
            pairingEndpoint = pairing,
            challengeEndpoint = challenge,
            tokenEndpoint = token,
            tokenRefreshEndpoint = tokenRefresh,
            tokenType = tokenType,
            refreshRule = refreshRule,
        )

    private fun SollProtocolTransportResponse.toDomain(): SollProtocolTransport =
        SollProtocolTransport(
            recommendedAuth = recommendedAuth,
            poll = poll,
            push = push,
        )

    private fun SollProtocolWorkerContractResponse.toDomain(): SollProtocolWorkerContract =
        SollProtocolWorkerContract(
            owner = owner,
            auth = auth,
            requiredScopes = requiredScopes,
            leaseSecondsDefault = leaseSecondsDefault,
            pollIntervalSeconds = pollIntervalSeconds,
            lifecycle = lifecycle,
        )

    private fun MeshStatusResponse.toDomain(): SollMeshStatus =
        SollMeshStatus(
            enabled = enabled,
            simulatedMode = simulatedMode,
            meshtasticAvailable = meshtasticAvailable,
            maxPayloadBytes = maxPayloadBytes,
            queuedOutboxCount = queuedOutboxCount,
            sentOutboxCount = sentOutboxCount,
            ackedOutboxCount = ackedOutboxCount,
            failedOutboxCount = failedOutboxCount,
        )

    private fun MeshOutboxItemResponse.toDomain(): SollMeshOutboxItem =
        SollMeshOutboxItem(
            outboundId = outboundId,
            toPeer = toPeer,
            text = text,
            claimToken = claimToken,
            securePayload = securePayload,
            status = status,
            retryCount = retryCount,
            maxRetries = maxRetries,
            lastError = lastError,
            createdAt = createdAt,
            lastAttemptAt = lastAttemptAt,
            ackedAt = ackedAt,
        )

    private fun GadgetDiscoverySchemaResponse.toDomain(): SollGadgetDiscoverySchema =
        SollGadgetDiscoverySchema(
            version = version,
            primaryOrder = primaryOrder,
            mdnsServiceTypes = mdns.serviceTypes,
            ssdpHeaderNames = ssdp.headers.keys.toList(),
            wifiSsidPrefixes = wifiAp.ssidPrefixes,
            defaultSetupHost = wifiAp.defaultSetupHost,
            deviceJsonEndpoint = deviceJson.endpoint,
            deviceJsonRecommendedFields = deviceJson.recommended,
        )

    private fun GadgetSnapshotResponse.toDomain(): GadgetCloudSnapshot =
        GadgetCloudSnapshot(
            id = id,
            name = name.ifBlank { id },
            profileId = profileId,
            enabled = enabled,
            firmwareVersion = firmwareVersion,
            localIp = localIp,
            uptimeMs = uptimeMs,
            capabilities = capabilities,
            heartbeatPayload = heartbeatPayload,
            lastHeartbeatAt = lastHeartbeatAt,
            lastTelemetryAt = lastTelemetryAt,
            latestTelemetry = latestTelemetry,
            latestEventType = latestEventType,
            latestEventSummary = latestEventSummary,
            stale = stale,
            updatedAt = updatedAt,
        )

    private fun GadgetEventResponse.toDomain(fallbackGadgetId: String): GadgetCloudEvent =
        GadgetCloudEvent(
            id = id.ifBlank { "$fallbackGadgetId:$createdAt:$type" },
            gadgetId = gadgetId.ifBlank { fallbackGadgetId },
            type = type,
            summary = summary,
            payload = payload,
            createdAt = createdAt,
        )

    private fun GadgetCommandResponse.toDomain(): GadgetCloudCommand =
        GadgetCloudCommand(
            id = id,
            gadgetId = gadgetId,
            command = command,
            params = params,
            status = status,
            reason = reason,
            result = result,
            riskLevel = riskLevel,
            approvalId = approvalId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            completedAt = completedAt,
        )

    private fun GadgetHistoryResponse.toDomain(): GadgetCloudHistory =
        GadgetCloudHistory(
            gadgetId = gadgetId,
            metric = metric,
            points = points.map { it.toDomain() },
        )

    private fun GadgetHistoryPointResponse.toDomain(): GadgetCloudHistoryPoint =
        GadgetCloudHistoryPoint(
            metric = metric,
            value = value,
            createdAt = createdAt,
        )

    private fun BookResultResponse.toDomain(): SollBookResult =
        SollBookResult(
            number = number,
            title = title,
            author = author,
            dedupeKey = dedupeKey,
            alternatives = alternatives.map { it.toDomain() },
        )

    private fun BookAlternativeResponse.toDomain(): SollBookAlternative =
        SollBookAlternative(
            number = number,
            title = title,
            author = author,
        )

    private fun BookIdentityResponse.toDomain(): SollBookIdentity =
        SollBookIdentity(
            title = title,
            author = author,
        )

    private fun BookFormatResponse.toDomain(): SollBookFormat =
        SollBookFormat(
            type = type,
            size = size,
        )

    private fun BookDownloadedFileResponse.toDomain(): SollBookDownloadedFile =
        SollBookDownloadedFile(
            filePath = filePath,
            metadataPath = metadataPath,
            dedupeKey = dedupeKey,
            book = book?.toDomain(),
            format = format,
            duplicateSkipped = duplicateSkipped,
            downloadedAt = downloadedAt,
        )

    private fun resolveRawUploadMetadata(uri: Uri): RawUploadMetadata {
        val resolver = context.contentResolver
        var displayName: String? = null
        var size: Long? = null

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                    size = cursor.longOrNull(OpenableColumns.SIZE)
                }
            }

        val fallbackName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: "upload.bin"

        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName
        val inferredMimeType = resolvedName
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?.let { extension ->
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    ?: when (extension) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        else -> null
                    }
            }

        return RawUploadMetadata(
            displayName = resolvedName,
            size = size,
            mimeType = resolver.getType(uri) ?: inferredMimeType,
        )
    }

    private fun uriRequestBody(
        uri: Uri,
        contentType: String?,
        contentLength: Long?,
    ): RequestBody = object : RequestBody() {
        override fun contentType() = contentType?.toMediaTypeOrNull()

        override fun contentLength(): Long = contentLength ?: -1L

        override fun writeTo(sink: BufferedSink) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: error("Не удалось открыть выбранный файл")
            inputStream.use { input ->
                sink.writeAll(input.source())
            }
        }
    }
}

internal fun selectRefreshAwareAuthorizationHeader(
    deviceAuthorization: String?,
    deviceTokenNeedsRefresh: Boolean,
    fallbackAuthorization: String?,
): String? = deviceAuthorization
    ?.takeUnless { deviceTokenNeedsRefresh }
    ?: fallbackAuthorization

private data class RawUploadMetadata(
    val displayName: String,
    val size: Long?,
    val mimeType: String?,
)

internal fun List<SollMonitoredSource>.filterForSourceScope(scope: SollSourceScope): List<SollMonitoredSource> =
    filter { source ->
        when (scope) {
            SollSourceScope.DAILY_TODO -> source.scope == SollSourceScope.DAILY_TODO || source.hasDailyTodoMarker()
            SollSourceScope.PROJECT_SOLL -> source.scope == SollSourceScope.PROJECT_SOLL && !source.hasDailyTodoMarker()
        }
    }

internal fun SollLearningItem.isDailyTodoOrigin(): Boolean =
    id.hasDailyTodoMarker() ||
        sourceRef.hasDailyTodoMarker() ||
        tags.any { it.hasDailyTodoMarker() }

private fun SollMonitoredSource.hasDailyTodoMarker(): Boolean =
    id.hasDailyTodoMarker() ||
        target.hasDailyTodoMarker() ||
        tags.any { it.hasDailyTodoMarker() }

private fun String.hasDailyTodoMarker(): Boolean {
    val normalized = trim().lowercase()
    return normalized.startsWith("task:daily:") ||
        normalized.contains("daily_todo") ||
        normalized.contains("android_daily_todo")
}

private fun Throwable.isHttpStatus(statusCode: Int): Boolean =
    this is HttpException && code() == statusCode

private fun Throwable.isWorkspaceSnapshotFallbackStatus(): Boolean =
    this is HttpException && code() in setOf(401, 403, 404)

private fun buildTaskGraphFromBoard(
    board: SollTaskBoard,
    includeDone: Boolean,
): SollTaskGraph {
    val tasks = buildList {
        addAll(board.today)
        addAll(board.blocked)
        addAll(board.inbox)
        addAll(board.stale)
        addAll(board.deferred)
        if (includeDone) addAll(board.doneRecent)
    }.distinctBy { it.id }

    val nodes = linkedMapOf<String, SollTaskGraphNode>()
    val edges = linkedMapOf<String, SollTaskGraphEdge>()
    val projectCounts = tasks.groupingBy { it.projectLabel() }.eachCount()
    val subprojectCounts = tasks.groupingBy { "${it.projectLabel()}/${it.subprojectLabel()}" }.eachCount()

    tasks.forEach { task ->
        val projectLabel = task.projectLabel()
        val projectNodeId = graphNodeId("project", projectLabel)
        nodes.putIfAbsent(
            projectNodeId,
            SollTaskGraphNode(
                id = projectNodeId,
                kind = "project",
                label = projectLabel,
                count = projectCounts[projectLabel] ?: 0,
            ),
        )

        val parentNodeId = task.subprojectLabel().takeIf { it.isNotBlank() }?.let { subprojectLabel ->
            val subprojectNodeId = graphNodeId("subproject", "$projectLabel/$subprojectLabel")
            nodes.putIfAbsent(
                subprojectNodeId,
                SollTaskGraphNode(
                    id = subprojectNodeId,
                    kind = "subproject",
                    label = subprojectLabel,
                    projectId = projectNodeId,
                    count = subprojectCounts["$projectLabel/$subprojectLabel"] ?: 0,
                ),
            )
            edges.putIfAbsent(
                "$projectNodeId->$subprojectNodeId",
                SollTaskGraphEdge(
                    id = "$projectNodeId->$subprojectNodeId",
                    source = projectNodeId,
                    target = subprojectNodeId,
                    kind = "project_subproject",
                ),
            )
            subprojectNodeId
        } ?: projectNodeId

        val taskNodeId = "task:${task.id}"
        nodes[taskNodeId] = SollTaskGraphNode(
            id = taskNodeId,
            kind = "task",
            label = task.title,
            status = task.status,
            priority = task.priority,
            projectId = projectNodeId,
            taskId = task.id,
            sourceRef = task.sourceRef,
        )
        edges["$parentNodeId->$taskNodeId"] = SollTaskGraphEdge(
            id = "$parentNodeId->$taskNodeId",
            source = parentNodeId,
            target = taskNodeId,
            kind = "contains",
        )

        task.sourceRef.takeIf { it.isNotBlank() }?.let { sourceRef ->
            val sourceNodeId = graphNodeId("source", sourceRef)
            nodes.putIfAbsent(
                sourceNodeId,
                SollTaskGraphNode(
                    id = sourceNodeId,
                    kind = "source",
                    label = sourceRef,
                    sourceRef = sourceRef,
                ),
            )
            edges.putIfAbsent(
                "$sourceNodeId->$taskNodeId",
                SollTaskGraphEdge(
                    id = "$sourceNodeId->$taskNodeId",
                    source = sourceNodeId,
                    target = taskNodeId,
                    kind = "source_task",
                ),
            )
        }
    }

    return SollTaskGraph(
        nodes = nodes.values.toList(),
        edges = edges.values.toList(),
        totalTasks = tasks.size,
        truncated = false,
    )
}

private fun SollTask.projectLabel(): String =
    projectName?.trim()?.takeIf { it.isNotBlank() } ?: "Без проекта"

private fun SollTask.subprojectLabel(): String =
    branch.trim().takeIf { it.isNotBlank() && it != "innovation" } ?: ""

private fun graphNodeId(kind: String, label: String): String =
    "$kind:${label.hashCode() and Int.MAX_VALUE}"

private fun taskGraphCacheScope(includeDone: Boolean): String =
    if (includeDone) TASK_GRAPH_SCOPE_ALL else TASK_GRAPH_SCOPE_OPEN

private fun SollTaskGraph.hasSameTaskGraphContent(other: SollTaskGraph): Boolean =
    totalTasks == other.totalTasks &&
        truncated == other.truncated &&
        nodes.sortedBy { it.id } == other.nodes.sortedBy { it.id } &&
        edges.sortedBy { it.id } == other.edges.sortedBy { it.id }

private const val SOLL_CACHE_PREFS = "soll_server_cache"
private const val KEY_ANDROID_SYNC_STATUS_JSON = "android_sync_status_json"
private const val KEY_ANDROID_SYNC_STATUS_CACHED_AT = "android_sync_status_cached_at"
private const val TASK_BOARD_SECTION_LIMIT = 80
private const val TASK_BOARD_MIN_SECTION_LIMIT = 20
private const val TASK_BOARD_MAX_SECTION_LIMIT = 500
private const val TASK_GRAPH_SCOPE_OPEN = "open"
private const val TASK_GRAPH_SCOPE_ALL = "all"
private const val TASK_GRAPH_DESCENDANT_LIMIT = 700
private const val DEVICE_TOKEN_REFRESH_SAFETY_MS = 2 * 60_000L
private const val SOURCE_TYPE_WEB = "web"
private const val MAX_CHAT_VOICE_TEXT_CHARS = 1_200
private const val MAX_CHAT_VOICE_AUDIO_BYTES = 25L * 1024L * 1024L
private val SOURCE_TYPES = setOf(SOURCE_TYPE_WEB, "rss", "telegram_chat")

fun normalizeSollBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""

    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }

    val parsed = withScheme.toHttpUrlOrNull()
    val normalized = if (parsed != null) {
        val pathSegments = parsed.pathSegments.filter { it.isNotBlank() }
        val withoutQuery = parsed.newBuilder()
            .query(null)
            .fragment(null)
        if (pathSegments == listOf("api", "v1") || pathSegments == listOf("api", "v1", "soll")) {
            withoutQuery.encodedPath("/")
        }
        withoutQuery.build().toString()
    } else {
        withScheme
    }
    return if (normalized.endsWith("/")) normalized else "$normalized/"
}

fun normalizeSollApiPathPrefix(rawPrefix: String): String {
    val normalized = rawPrefix
        .trim()
        .trim('/')
        .replace(Regex("/+"), "/")
    return normalized
}

internal fun rewriteSollApiUrl(url: HttpUrl, apiPathPrefix: String): HttpUrl {
    val prefixSegments = normalizeSollApiPathPrefix(apiPathPrefix)
        .split("/")
        .filter { it.isNotBlank() }
    if (prefixSegments.isEmpty()) {
        return url
    }
    val currentSegments = url.encodedPathSegments
    if (currentSegments.take(prefixSegments.size) == prefixSegments) {
        return url
    }
    val legacyPrefix = listOf("api", "v1")
    if (currentSegments.take(legacyPrefix.size) != legacyPrefix) {
        return url
    }
    val nextSegments = prefixSegments + currentSegments.drop(legacyPrefix.size)
    return url.newBuilder()
        .encodedPath("/${nextSegments.joinToString("/")}")
        .build()
}

private fun OkHttpClient.withSollApiPrefix(apiPathPrefix: String): OkHttpClient {
    val normalized = normalizeSollApiPathPrefix(apiPathPrefix)
    if (normalized.isBlank() || normalized == "api/v1") return this
    return newBuilder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val rewrittenUrl = rewriteSollApiUrl(request.url, normalized)
            val nextRequest = if (rewrittenUrl == request.url) {
                request
            } else {
                request.newBuilder().url(rewrittenUrl).build()
            }
            chain.proceed(nextRequest)
        })
        .build()
}

private fun String.encodedSollPathSegment(fieldName: String): String {
    val clean = trim()
    require(clean.isNotBlank()) { "$fieldName не задан" }
    return Uri.encode(clean)
}

private fun parseIsoInstantMillis(value: String): Long? {
    val trimmed = value.trim()
    return try {
        Instant.parse(trimmed).toEpochMilli()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(trimmed).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

fun buildRawNoteFilename(
    title: String,
    timestampMillis: Long = System.currentTimeMillis(),
): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timestampMillis))
    val slug = title
        .trim()
        .lowercase(Locale.ROOT)
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString(separator = "")
        .replace(Regex("-+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "note" }

    return "mobile-$stamp-$slug.md"
}

fun buildRawNoteContent(
    title: String,
    content: String,
    tags: List<String>,
    timestampMillis: Long = System.currentTimeMillis(),
): String {
    val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timestampMillis))
    val normalizedTags = (listOf("mobile", "soll_app") + tags)
        .map { it.trim().replace(Regex("\\s+"), "-") }
        .filter { it.isNotBlank() }
        .distinct()

    return buildString {
        append("---\n")
        append("source: soll_app_android\n")
        append("kind: mobile_raw_note\n")
        append("created_at: $createdAt\n")
        append("tags:\n")
        normalizedTags.forEach { tag ->
            append("  - $tag\n")
        }
        append("---\n\n")
        append("# ${title.trim()}\n\n")
        append(content.trim())
        append("\n")
    }
}

fun buildRawUploadFilename(
    displayName: String,
    timestampMillis: Long = System.currentTimeMillis(),
): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timestampMillis))
    val cleanName = displayName
        .trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .ifBlank { "file" }
    val dotIndex = cleanName.lastIndexOf('.')
    val rawBase = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
    val extension = if (dotIndex > 0) {
        cleanName.substring(dotIndex)
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it == '.' }
            .take(12)
    } else {
        ""
    }
    val slug = rawBase
        .lowercase(Locale.ROOT)
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString(separator = "")
        .replace(Regex("-+"), "-")
        .trim('-')
        .take(48)
        .ifBlank { "file" }

    return "mobile-$stamp-$slug$extension"
}

private fun Cursor.stringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.longOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}
