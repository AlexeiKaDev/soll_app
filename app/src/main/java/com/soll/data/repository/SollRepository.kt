package com.soll.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.squareup.moshi.Moshi
import com.soll.BuildConfig
import com.soll.data.api.AndroidProtocolBootstrapResponse
import com.soll.data.api.AndroidPushTokenRequest
import com.soll.data.api.AndroidPushTokenResponse
import com.soll.data.api.AndroidSyncStatusResponse
import com.soll.data.api.AssistantAskRequest
import com.soll.data.api.AssistantAskResponse
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
import com.soll.data.api.TaskGraphEdgeResponse
import com.soll.data.api.TaskGraphNodeResponse
import com.soll.data.api.TaskGraphResponse
import com.soll.domain.metacoordinator.MetaCoordinatorFallback
import com.soll.domain.metacoordinator.MetaCoordinatorRequest
import com.soll.domain.metacoordinator.MetaCoordinatorResponse
import com.soll.domain.metacoordinator.MetaCoordinatorServerBridge
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudHistory
import com.soll.domain.device.GadgetCloudHistoryPoint
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollAndroidSyncStatus
import com.soll.domain.soll.SollAndroidChatSync
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
import com.soll.domain.soll.SollChatActionResult
import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollChatSession
import com.soll.domain.soll.SollDevice
import com.soll.domain.soll.SollDeviceToken
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollLearningItem
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollNodeIdentity
import com.soll.domain.soll.SollRoadmap
import com.soll.domain.soll.SollRoadmapLine
import com.soll.domain.soll.SollRoadmapReadiness
import com.soll.domain.soll.SollRoadmapStage
import com.soll.domain.soll.SollSourceItem
import com.soll.domain.soll.SollGadgetDiscoverySchema
import com.soll.domain.soll.SollMeshOutboxItem
import com.soll.domain.soll.SollMeshStatus
import com.soll.domain.soll.SollProtocolAuth
import com.soll.domain.soll.SollProtocolBootstrap
import com.soll.domain.soll.SollProtocolSchema
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
import com.soll.domain.soll.buildSollDeviceTokenSignature
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
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
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

@Singleton
class SollRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) : SollGateway {
    private val androidSyncStatusJsonAdapter by lazy {
        moshi.adapter(AndroidSyncStatusResponse::class.java)
    }

    override suspend fun getHealth(): Result<SollHealth> = runSuspendCatching {
        service().getHealth(readAuthorizationHeader()).toDomain()
    }

    override suspend fun getTaskBoard(limitPerSection: Int?): Result<SollTaskBoard> {
        val sectionLimit = limitPerSection
            ?.coerceIn(TASK_BOARD_MIN_SECTION_LIMIT, TASK_BOARD_MAX_SECTION_LIMIT)
            ?: TASK_BOARD_SECTION_LIMIT
        val boardResult = runSuspendCatching {
            service().getTaskBoard(
                authorization = readAuthorizationHeader(),
                limitPerSection = sectionLimit,
                includeCounts = true,
            ).toDomain()
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
            response.tasks.toDomain()
        }
        if (syncStatusResult.isSuccess) return syncStatusResult

        return boardResult
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
    ): Result<Pair<SollChatMessage, SollChatMessage?>> = runSuspendCatching {
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank()) { "Сообщение пустое" }
        val metadata = mapOf("source" to "android_app")
        val encrypted = encryptedEnvelopeOrNull(
            content = cleanContent,
            metadata = metadata,
            aad = "POST /api/v1/chat/turn",
        )
        val response = service().sendChatTurn(
            authorization = readAuthorizationHeader(),
            request = ChatTurnRequest(
                sessionId = sessionId?.trim()?.takeIf { it.isNotBlank() },
                content = if (encrypted == null) cleanContent else null,
                metadata = if (encrypted == null) metadata else null,
                encrypted = encrypted,
                runAssistant = runAssistant,
                taskIntake = true,
            ),
        )
        response.message.toDomain() to response.assistant?.toDomain()
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
        service().executeChatAction(
            authorization = readAuthorizationHeader(),
            actionId = cleanActionId.encodedSollPathSegment(fieldName = "action_id"),
            request = ChatActionExecuteRequest(
                action = cleanAction,
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

    override suspend fun getTaskGraph(includeDone: Boolean): Result<SollTaskGraph> = runSuspendCatching {
        service().getTaskGraph(
            authorization = readAuthorizationHeader(),
            includeDone = includeDone,
            maxNodes = 700,
        ).toDomain()
    }.recoverCatching { error ->
        if (!error.isHttpStatus(404)) throw error
        val syncStatus = getAndroidSyncStatus().getOrThrow()
        buildTaskGraphFromBoard(syncStatus.tasks, includeDone = includeDone)
    }

    override suspend fun getLearningItems(status: String?, limit: Int): Result<List<SollLearningItem>> =
        runSuspendCatching {
            service().getLearningItems(
                authorization = readAuthorizationHeader(),
                status = status?.takeIf { it.isNotBlank() },
                limit = limit.coerceIn(1, 200),
            ).items.map { it.toDomain() }
        }.recoverCatching { error ->
            if (!error.isWorkspaceSnapshotFallbackStatus()) throw error
            getAndroidSyncStatus().getOrThrow().insights
                .filter { status.isNullOrBlank() || it.status == status }
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

    override suspend fun listSources(): Result<List<SollMonitoredSource>> =
        runSuspendCatching {
            service().listSources(readAuthorizationHeader()).map { it.toDomain() }
        }.recoverCatching { error ->
            if (!error.isWorkspaceSnapshotFallbackStatus()) throw error
            getAndroidSyncStatus().getOrThrow().sources
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

    override suspend fun createSource(
        name: String,
        target: String,
        sourceType: String,
    ): Result<SollMonitoredSource> = runSuspendCatching {
        val cleanTarget = target.trim()
        val cleanSourceType = sourceType.trim().lowercase().takeIf { it in SOURCE_TYPES }
            ?: SOURCE_TYPE_WEB
        require(cleanTarget.isNotBlank()) { "URL источника не задан" }
        service().createSource(
            authorization = authorizationHeader(),
            request = MonitoredSourceCreateRequest(
                name = name.trim().takeIf { it.isNotBlank() },
                sourceType = cleanSourceType,
                target = cleanTarget,
                tags = listOf("android"),
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
            authorization = authorizationHeader(),
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
        service().deleteSource(authorizationHeader(), sourceId.trim())
        true
    }

    override suspend fun checkSource(sourceId: String): Result<Boolean> = runSuspendCatching {
        service().checkSource(authorizationHeader(), sourceId.trim()).changed
    }

    override suspend fun createTaskFromSourceItem(sourceId: String, itemId: String): Result<SollTask?> =
        runSuspendCatching {
            val cleanSourceId = sourceId.trim()
            val cleanItemId = itemId.trim()
            require(cleanSourceId.isNotBlank()) { "ID источника не задан" }
            require(cleanItemId.isNotBlank()) { "ID материала не задан" }
            service().createTaskFromSourceItem(
                authorization = authorizationHeader(),
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

    override suspend fun ackMeshOutbox(outboundId: String): Result<SollMeshOutboxItem> = runSuspendCatching {
        val cleanId = outboundId.trim()
        require(cleanId.isNotBlank()) { "ID outbox-сообщения не задан" }
        service().ackMeshOutbox(
            authorization = readAuthorizationHeader(),
            outboundId = cleanId,
        ).toDomain()
    }

    override suspend fun markMeshOutboxAttempt(
        outboundId: String,
        success: Boolean,
        error: String?,
    ): Result<SollMeshOutboxItem> = runSuspendCatching {
        val cleanId = outboundId.trim()
        require(cleanId.isNotBlank()) { "ID outbox-сообщения не задан" }
        service().markMeshOutboxAttempt(
            authorization = readAuthorizationHeader(),
            outboundId = cleanId,
            request = MeshOutboxAttemptRequest(
                success = success,
                error = error?.trim()?.takeIf { it.isNotBlank() },
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
            tasks = tasks.toDomain(),
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
            sources = workspace.sources.map { it.toDomain() },
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
        SollMonitoredSource(
            id = id,
            name = name,
            sourceType = sourceType,
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
            linkPreview = linkPreview,
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
            valueMetric = valueMetric,
            branch = branch,
            pairId = pairId,
            assignedNodeId = assignedNodeId,
            requiredCapabilities = requiredCapabilities,
            routingState = routingState,
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

    private fun SollProtocolSchemaResponse.toDomain(): SollProtocolSchema =
        SollProtocolSchema(
            version = version,
            auth = auth.toDomain(),
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

        return RawUploadMetadata(
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            size = size,
            mimeType = resolver.getType(uri),
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

private data class RawUploadMetadata(
    val displayName: String,
    val size: Long?,
    val mimeType: String?,
)

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

private const val SOLL_CACHE_PREFS = "soll_server_cache"
private const val KEY_ANDROID_SYNC_STATUS_JSON = "android_sync_status_json"
private const val KEY_ANDROID_SYNC_STATUS_CACHED_AT = "android_sync_status_cached_at"
private const val TASK_BOARD_SECTION_LIMIT = 80
private const val TASK_BOARD_MIN_SECTION_LIMIT = 20
private const val TASK_BOARD_MAX_SECTION_LIMIT = 500
private const val DEVICE_TOKEN_REFRESH_SAFETY_MS = 2 * 60_000L
private const val SOURCE_TYPE_WEB = "web"
private val SOURCE_TYPES = setOf(SOURCE_TYPE_WEB, "rss", "telegram_chat")

fun normalizeSollBaseUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""

    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }

    return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
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
    val currentSegments = url.pathSegments
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
