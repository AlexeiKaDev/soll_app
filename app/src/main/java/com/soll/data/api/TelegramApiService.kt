package com.soll.data.api

import com.soll.data.api.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

/**
 * Telegram Bot API Service
 * Base URL: https://api.telegram.org/
 */
interface TelegramApiService {

    /**
     * Get bot info
     */
    @GET("bot{token}/getMe")
    suspend fun getMe(
        @Path(value = "token", encoded = true) token: String
    ): TelegramResponse<BotInfo>

    /**
     * Get updates using long polling
     * @param timeout Long polling timeout in seconds (recommended 25-30)
     * @param offset Identifier of the first update to be returned
     */
    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path(value = "token", encoded = true) token: String,
        @Query("offset") offset: Long? = null,
        @Query("timeout") timeout: Int = 30,
        @Query("allowed_updates") allowedUpdates: String? = null
    ): TelegramResponse<List<Update>>

    /**
     * Remove webhook so [getUpdates] long polling works. Required if bot was used with a webhook elsewhere.
     */
    @GET("bot{token}/deleteWebhook")
    suspend fun deleteWebhook(
        @Path(value = "token", encoded = true) token: String,
        @Query("drop_pending_updates") dropPendingUpdates: Boolean = false,
    ): TelegramResponse<Boolean>

    /**
     * Send text message
     */
    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path(value = "token", encoded = true) token: String,
        @Body request: SendMessageRequest
    ): TelegramResponse<Message>

    /**
     * Send document (file)
     */
    @Multipart
    @POST("bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part document: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null,
        @Part("parse_mode") parseMode: RequestBody? = null
    ): TelegramResponse<Message>

    /**
     * Send photo
     */
    @Multipart
    @POST("bot{token}/sendPhoto")
    suspend fun sendPhoto(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null,
        @Part("parse_mode") parseMode: RequestBody? = null
    ): TelegramResponse<Message>

    /**
     * Send audio file
     */
    @Multipart
    @POST("bot{token}/sendAudio")
    suspend fun sendAudio(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null,
        @Part("duration") duration: RequestBody? = null
    ): TelegramResponse<Message>

    /**
     * Send voice message
     */
    @Multipart
    @POST("bot{token}/sendVoice")
    suspend fun sendVoice(
        @Path(value = "token", encoded = true) token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part voice: MultipartBody.Part,
        @Part("caption") caption: RequestBody? = null,
        @Part("duration") duration: RequestBody? = null
    ): TelegramResponse<Message>

    /**
     * Send location
     */
    @POST("bot{token}/sendLocation")
    suspend fun sendLocation(
        @Path(value = "token", encoded = true) token: String,
        @Body request: SendLocationRequest
    ): TelegramResponse<Message>

    /**
     * Get file info for downloading
     */
    @GET("bot{token}/getFile")
    suspend fun getFile(
        @Path(value = "token", encoded = true) token: String,
        @Query("file_id") fileId: String
    ): TelegramResponse<TelegramFile>

    /**
     * Answer callback query
     */
    @POST("bot{token}/answerCallbackQuery")
    suspend fun answerCallbackQuery(
        @Path(value = "token", encoded = true) token: String,
        @Body request: AnswerCallbackQueryRequest
    ): TelegramResponse<Boolean>

    /**
     * Delete message
     */
    @GET("bot{token}/deleteMessage")
    suspend fun deleteMessage(
        @Path(value = "token", encoded = true) token: String,
        @Query("chat_id") chatId: Long,
        @Query("message_id") messageId: Long
    ): TelegramResponse<Boolean>

    companion object {
        /**
         * Get file download URL
         */
        fun getFileUrl(token: String, filePath: String): String {
            val t = token.replace(":", "%3A")
            return "https://api.telegram.org/file/bot$t/$filePath"
        }
    }
}
