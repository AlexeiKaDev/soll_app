package com.soll.data.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request to send a text message
 */
@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "text") val text: String,
    @Json(name = "parse_mode") val parseMode: String? = "HTML",
    @Json(name = "disable_web_page_preview") val disableWebPagePreview: Boolean? = null,
    @Json(name = "disable_notification") val disableNotification: Boolean? = null,
    @Json(name = "reply_to_message_id") val replyToMessageId: Long? = null
)

/**
 * Request to send a document
 */
@JsonClass(generateAdapter = true)
data class SendDocumentRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "parse_mode") val parseMode: String? = "HTML"
)

/**
 * Request to send a photo
 */
@JsonClass(generateAdapter = true)
data class SendPhotoRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "parse_mode") val parseMode: String? = "HTML"
)

/**
 * Request to send a location
 */
@JsonClass(generateAdapter = true)
data class SendLocationRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "horizontal_accuracy") val horizontalAccuracy: Double? = null,
    @Json(name = "live_period") val livePeriod: Int? = null
)

/**
 * Request to send an audio file
 */
@JsonClass(generateAdapter = true)
data class SendAudioRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "parse_mode") val parseMode: String? = "HTML"
)

/**
 * Request to send a voice message
 */
@JsonClass(generateAdapter = true)
data class SendVoiceRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "parse_mode") val parseMode: String? = "HTML"
)

/**
 * Request to answer callback query
 */
@JsonClass(generateAdapter = true)
data class AnswerCallbackQueryRequest(
    @Json(name = "callback_query_id") val callbackQueryId: String,
    @Json(name = "text") val text: String? = null,
    @Json(name = "show_alert") val showAlert: Boolean? = null
)

/**
 * Inline keyboard markup for messages
 */
@JsonClass(generateAdapter = true)
data class InlineKeyboardMarkup(
    @Json(name = "inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

/**
 * Inline keyboard button
 */
@JsonClass(generateAdapter = true)
data class InlineKeyboardButton(
    @Json(name = "text") val text: String,
    @Json(name = "callback_data") val callbackData: String? = null,
    @Json(name = "url") val url: String? = null
)
