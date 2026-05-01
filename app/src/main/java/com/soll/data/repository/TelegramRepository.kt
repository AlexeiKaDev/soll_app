package com.soll.data.repository

import com.soll.data.api.TelegramApiService
import com.soll.data.api.model.*
import com.soll.data.local.dao.CommandLogDao
import com.soll.data.local.dao.MessageLogDao
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.local.entity.MessageLogEntity
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramRepository @Inject constructor(
    private val apiService: TelegramApiService,
    private val settingsRepository: SettingsRepository,
    private val messageLogDao: MessageLogDao,
    private val commandLogDao: CommandLogDao
) {
    private val token: String
        get() = settingsRepository.botToken ?: throw IllegalStateException("Bot token not set")

    /**
     * Get bot info
     */
    suspend fun getMe(): Result<BotInfo> = runCatching {
        val response = apiService.getMe(token)
        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Unknown error")
        }
    }

    /**
     * Get updates using long polling
     */
    suspend fun getUpdates(offset: Long? = null, timeout: Int = 30): Result<List<Update>> = runCatching {
        val response = apiService.getUpdates(
            token = token,
            offset = offset,
            timeout = timeout
        )
        if (response.ok && response.result != null) {
            // Log received messages
            response.result.forEach { update ->
                update.message?.let { message ->
                    logMessage(update, message)
                }
            }
            response.result
        } else {
            throw Exception(response.description ?: "Failed to get updates")
        }
    }

    /**
     * Send text message
     */
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: String? = "HTML",
        replyToMessageId: Long? = null
    ): Result<Message> = runCatching {
        val request = SendMessageRequest(
            chatId = chatId,
            text = text,
            parseMode = parseMode,
            replyToMessageId = replyToMessageId
        )
        val response = apiService.sendMessage(token, request)
        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Failed to send message")
        }
    }

    /**
     * Send document (file)
     */
    suspend fun sendDocument(
        chatId: Long,
        file: File,
        caption: String? = null
    ): Result<Message> = runCatching {
        val chatIdBody = chatId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = caption?.toRequestBody("text/plain".toMediaTypeOrNull())
        val parseModeBody = "HTML".toRequestBody("text/plain".toMediaTypeOrNull())

        val mimeType = getMimeType(file.name)
        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val documentPart = MultipartBody.Part.createFormData("document", file.name, requestFile)

        val response = apiService.sendDocument(
            token = token,
            chatId = chatIdBody,
            document = documentPart,
            caption = captionBody,
            parseMode = parseModeBody
        )

        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Failed to send document")
        }
    }

    /**
     * Send photo
     */
    suspend fun sendPhoto(
        chatId: Long,
        file: File,
        caption: String? = null
    ): Result<Message> = runCatching {
        val chatIdBody = chatId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = caption?.toRequestBody("text/plain".toMediaTypeOrNull())
        val parseModeBody = "HTML".toRequestBody("text/plain".toMediaTypeOrNull())

        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val photoPart = MultipartBody.Part.createFormData("photo", file.name, requestFile)

        val response = apiService.sendPhoto(
            token = token,
            chatId = chatIdBody,
            photo = photoPart,
            caption = captionBody,
            parseMode = parseModeBody
        )

        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Failed to send photo")
        }
    }

    /**
     * Send location
     */
    suspend fun sendLocation(
        chatId: Long,
        latitude: Double,
        longitude: Double,
        accuracy: Double? = null
    ): Result<Message> = runCatching {
        val request = SendLocationRequest(
            chatId = chatId,
            latitude = latitude,
            longitude = longitude,
            horizontalAccuracy = accuracy
        )
        val response = apiService.sendLocation(token, request)
        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Failed to send location")
        }
    }

    /**
     * Send voice message
     */
    suspend fun sendVoice(
        chatId: Long,
        file: File,
        caption: String? = null,
        duration: Int? = null
    ): Result<Message> = runCatching {
        val chatIdBody = chatId.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val captionBody = caption?.toRequestBody("text/plain".toMediaTypeOrNull())
        val durationBody = duration?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

        val requestFile = file.asRequestBody("audio/ogg".toMediaTypeOrNull())
        val voicePart = MultipartBody.Part.createFormData("voice", file.name, requestFile)

        val response = apiService.sendVoice(
            token = token,
            chatId = chatIdBody,
            voice = voicePart,
            caption = captionBody,
            duration = durationBody
        )

        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Failed to send voice")
        }
    }

    /**
     * Get file info for downloading
     */
    suspend fun getFile(fileId: String): Result<TelegramFile> = runCatching {
        val response = apiService.getFile(token, fileId)
        if (response.ok && response.result != null) {
            response.result
        } else {
            throw Exception(response.description ?: "Failed to get file")
        }
    }

    /**
     * Get file download URL
     */
    fun getFileUrl(filePath: String): String {
        return TelegramApiService.getFileUrl(token, filePath)
    }

    // Logging

    private suspend fun logMessage(update: Update, message: Message) {
        try {
            val logEntity = MessageLogEntity(
                updateId = update.updateId,
                messageId = message.messageId,
                chatId = message.chat.id,
                chatType = message.chat.type,
                chatTitle = message.chat.displayName,
                userId = message.from?.id,
                username = message.from?.username,
                userFullName = message.from?.fullName,
                text = message.text,
                hasDocument = message.document != null,
                hasPhoto = !message.photo.isNullOrEmpty(),
                hasLocation = message.location != null,
                messageDate = message.date * 1000 // Convert to milliseconds
            )
            messageLogDao.insert(logEntity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to log message")
        }
    }

    suspend fun logCommand(
        command: String,
        args: String?,
        chatId: Long,
        userId: Long?,
        username: String?,
        status: String,
        errorMessage: String? = null,
        responseText: String? = null,
        executionTimeMs: Long? = null
    ) {
        try {
            val logEntity = CommandLogEntity(
                command = command,
                args = args,
                chatId = chatId,
                userId = userId,
                username = username,
                status = status,
                errorMessage = errorMessage,
                responseText = responseText?.take(1000), // Limit response text
                executionTimeMs = executionTimeMs
            )
            commandLogDao.insert(logEntity)
        } catch (e: Exception) {
            Timber.e(e, "Failed to log command")
        }
    }

    fun getMessageLogs(limit: Int = 100): Flow<List<MessageLogEntity>> =
        messageLogDao.getRecentLogs(limit)

    fun getCommandLogs(limit: Int = 100): Flow<List<CommandLogEntity>> =
        commandLogDao.getRecentLogs(limit)

    suspend fun getMessageCount(): Int = messageLogDao.getCount()

    fun getMessageCountFlow(): Flow<Int> = messageLogDao.getCountFlow()

    suspend fun clearLogs() {
        messageLogDao.deleteAll()
        commandLogDao.deleteAll()
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
