package com.soll.data.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Telegram Bot API response wrapper
 */
@JsonClass(generateAdapter = true)
data class TelegramResponse<T>(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "result") val result: T?,
    @Json(name = "description") val description: String? = null,
    @Json(name = "error_code") val errorCode: Int? = null
)

/**
 * Telegram Update object
 */
@JsonClass(generateAdapter = true)
data class Update(
    @Json(name = "update_id") val updateId: Long,
    @Json(name = "message") val message: Message? = null,
    @Json(name = "edited_message") val editedMessage: Message? = null,
    @Json(name = "callback_query") val callbackQuery: CallbackQuery? = null
)

/**
 * Telegram Message object
 */
@JsonClass(generateAdapter = true)
data class Message(
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "from") val from: User? = null,
    @Json(name = "chat") val chat: Chat,
    @Json(name = "date") val date: Long,
    @Json(name = "text") val text: String? = null,
    @Json(name = "document") val document: Document? = null,
    @Json(name = "photo") val photo: List<PhotoSize>? = null,
    @Json(name = "caption") val caption: String? = null,
    @Json(name = "reply_to_message") val replyToMessage: Message? = null,
    @Json(name = "location") val location: Location? = null
)

/**
 * Telegram User object
 */
@JsonClass(generateAdapter = true)
data class User(
    @Json(name = "id") val id: Long,
    @Json(name = "is_bot") val isBot: Boolean,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "language_code") val languageCode: String? = null
) {
    val fullName: String
        get() = if (lastName != null) "$firstName $lastName" else firstName

    val displayName: String
        get() = username?.let { "@$it" } ?: fullName
}

/**
 * Telegram Chat object
 */
@JsonClass(generateAdapter = true)
data class Chat(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String, // "private", "group", "supergroup", "channel"
    @Json(name = "title") val title: String? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null
) {
    val displayName: String
        get() = title ?: username ?: firstName ?: "Chat $id"
}

/**
 * Telegram Document object
 */
@JsonClass(generateAdapter = true)
data class Document(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "file_size") val fileSize: Long? = null
)

/**
 * Telegram PhotoSize object
 */
@JsonClass(generateAdapter = true)
data class PhotoSize(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "width") val width: Int,
    @Json(name = "height") val height: Int,
    @Json(name = "file_size") val fileSize: Long? = null
)

/**
 * Telegram Location object
 */
@JsonClass(generateAdapter = true)
data class Location(
    @Json(name = "longitude") val longitude: Double,
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "horizontal_accuracy") val horizontalAccuracy: Double? = null
)

/**
 * Telegram CallbackQuery object
 */
@JsonClass(generateAdapter = true)
data class CallbackQuery(
    @Json(name = "id") val id: String,
    @Json(name = "from") val from: User,
    @Json(name = "message") val message: Message? = null,
    @Json(name = "data") val data: String? = null
)

/**
 * Telegram File object (for getFile response)
 */
@JsonClass(generateAdapter = true)
data class TelegramFile(
    @Json(name = "file_id") val fileId: String,
    @Json(name = "file_unique_id") val fileUniqueId: String,
    @Json(name = "file_size") val fileSize: Long? = null,
    @Json(name = "file_path") val filePath: String? = null
)

/**
 * Telegram Bot info
 */
@JsonClass(generateAdapter = true)
data class BotInfo(
    @Json(name = "id") val id: Long,
    @Json(name = "is_bot") val isBot: Boolean,
    @Json(name = "first_name") val firstName: String,
    @Json(name = "username") val username: String,
    @Json(name = "can_join_groups") val canJoinGroups: Boolean? = null,
    @Json(name = "can_read_all_group_messages") val canReadAllGroupMessages: Boolean? = null,
    @Json(name = "supports_inline_queries") val supportsInlineQueries: Boolean? = null
)
