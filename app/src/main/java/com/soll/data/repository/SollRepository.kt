package com.soll.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.squareup.moshi.Moshi
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
import com.soll.data.api.CreateRawFileRequest
import com.soll.data.api.GadgetEventResponse
import com.soll.data.api.GadgetHistoryPointResponse
import com.soll.data.api.GadgetHistoryResponse
import com.soll.data.api.GadgetSnapshotResponse
import com.soll.data.api.RawFileResponse
import com.soll.data.api.RawUploadResponse
import com.soll.data.api.SollApiService
import com.soll.data.api.SollBookStatusResponse
import com.soll.data.api.SollBriefingResponse
import com.soll.data.api.SollDeviceResponse
import com.soll.data.api.SollHealthResponse
import com.soll.data.api.SollTaskBoardResponse
import com.soll.data.api.SollTaskResponse
import com.soll.domain.metacoordinator.MetaCoordinatorFallback
import com.soll.domain.metacoordinator.MetaCoordinatorRequest
import com.soll.domain.metacoordinator.MetaCoordinatorResponse
import com.soll.domain.metacoordinator.MetaCoordinatorServerBridge
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudHistory
import com.soll.domain.device.GadgetCloudHistoryPoint
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollAndroidSyncStatus
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
import com.soll.domain.soll.SollDevice
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollRawNote
import com.soll.domain.soll.SollRawUpload
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
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
        service().getHealth(authorizationHeader()).toDomain()
    }

    override suspend fun getTaskBoard(): Result<SollTaskBoard> = runSuspendCatching {
        service().getTaskBoard(authorizationHeader()).toDomain()
    }

    override suspend fun getAndroidSyncStatus(): Result<SollAndroidSyncStatus> {
        val liveResult = runSuspendCatching {
            val response = service().getAndroidSyncStatus(authorizationHeader())
            cacheAndroidSyncStatus(response)
            response.toDomain()
        }
        if (liveResult.isSuccess) return liveResult

        val cached = cachedAndroidSyncStatusOrNull(liveResult.exceptionOrNull())
        return cached?.let { Result.success(it) } ?: liveResult
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
        service().setTaskStatus(authorizationHeader(), taskId, status).toDomain()
    }

    override suspend fun moveTaskToToday(taskId: String): Result<SollTask> = runSuspendCatching {
        service().moveTaskToToday(authorizationHeader(), taskId).toDomain()
    }

    override suspend fun completeTask(taskId: String): Result<SollTask> = runSuspendCatching {
        service().completeTask(authorizationHeader(), taskId).toDomain()
    }

    override suspend fun deferTask(taskId: String): Result<SollTask> = runSuspendCatching {
        service().deferTask(authorizationHeader(), taskId).toDomain()
    }

    override suspend fun rejectTask(taskId: String): Result<SollTask> = runSuspendCatching {
        service().rejectTask(authorizationHeader(), taskId).toDomain()
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

    override suspend fun getGadgetSnapshots(): Result<List<GadgetCloudSnapshot>> = runSuspendCatching {
        service().getGadgets(authorizationHeader()).map { it.toDomain() }
    }

    override suspend fun getGadgetLatest(gadgetId: String): Result<GadgetCloudSnapshot> = runSuspendCatching {
        val cleanId = gadgetId.trim()
        require(cleanId.isNotBlank()) { "ID гаджета не задан" }
        service().getGadgetLatest(
            authorization = authorizationHeader(),
            gadgetId = cleanId,
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
            authorization = authorizationHeader(),
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
                authorization = authorizationHeader(),
                gadgetId = cleanId,
                limit = limit.coerceIn(1, 200),
            ).map { it.toDomain(fallbackGadgetId = cleanId) }
        }

    private fun service(): SollApiService {
        val baseUrl = normalizeSollBaseUrl(settingsRepository.sollServerUrl)
        require(baseUrl.isNotBlank()) { "URL сервера Soll не задан" }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SollApiService::class.java)
    }

    private fun authorizationHeader(): String? {
        val token = settingsRepository.sollAccessToken.trim()
        return token.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
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
            briefing = briefing?.toDomain(),
            warnings = (warnings + extraWarnings).distinct(),
            fromCache = fromCache,
            cachedAtMillis = cachedAtMillis,
        )

    private fun SollDeviceResponse.toDomain(): SollDevice =
        SollDevice(
            id = id,
            name = name,
            enabled = enabled,
            scopes = scopes,
            lastSeenAt = lastSeenAt,
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
            inbox = inbox.map { it.toDomain() },
            stale = stale.map { it.toDomain() },
            doneRecent = doneRecent.map { it.toDomain() },
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

    private fun GadgetSnapshotResponse.toDomain(): GadgetCloudSnapshot =
        GadgetCloudSnapshot(
            id = id,
            name = name.ifBlank { id },
            profileId = profileId,
            enabled = enabled,
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

private const val SOLL_CACHE_PREFS = "soll_server_cache"
private const val KEY_ANDROID_SYNC_STATUS_JSON = "android_sync_status_json"
private const val KEY_ANDROID_SYNC_STATUS_CACHED_AT = "android_sync_status_cached_at"

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
