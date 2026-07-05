package com.soll.domain.notification

import kotlinx.coroutines.flow.Flow
import java.util.UUID

enum class SollNotificationChannel(val channelId: String) {
    CHAT("soll_chat"),
    EVENTS("soll_events"),
    ALERTS("soll_alerts"),
    TOOL_JOBS("soll_tool_jobs"),
    SERVER_SYNC("soll_server_sync"),
    BOT_SERVICE("soll_bot_service"),
    TTS_PLAYBACK("soll_tts_service"),
    MUSIC_PLAYBACK("soll_music_playback"),
    ACTIVITY_TRACKING("soll_activity_tracking"),
}

enum class SollNotificationPriority {
    LOW,
    DEFAULT,
    HIGH,
}

enum class SollNotificationStatus {
    UNREAD,
    READ,
    DISMISSED,
}

data class SollNotification(
    val id: String = UUID.randomUUID().toString(),
    val channel: SollNotificationChannel,
    val type: String,
    val source: String,
    val title: String,
    val message: String,
    val payloadJson: String? = null,
    val priority: SollNotificationPriority = SollNotificationPriority.DEFAULT,
    val status: SollNotificationStatus = SollNotificationStatus.UNREAD,
    val createdAt: Long = System.currentTimeMillis(),
    val shownAt: Long? = null,
    val readAt: Long? = null,
    val dismissedAt: Long? = null,
    val systemNotificationId: Int? = null,
    val dedupeKey: String? = null,
)

data class SollNotificationRequest(
    val channel: SollNotificationChannel,
    val type: String,
    val source: String,
    val title: String,
    val message: String,
    val payloadJson: String? = null,
    val priority: SollNotificationPriority = SollNotificationPriority.DEFAULT,
    val showSystem: Boolean = true,
    val autoCancel: Boolean = true,
    val onlyAlertOnce: Boolean = false,
    val systemNotificationId: Int? = null,
    val launchSection: String? = null,
    val launchLogsTab: String? = null,
    val systemGroupKey: String? = null,
    val systemGroupTitle: String? = null,
    val dedupeKey: String? = null,
)

interface SollNotificationCenter {
    fun observeRecent(limit: Int = 100): Flow<List<SollNotification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun post(request: SollNotificationRequest): SollNotification
    suspend fun markRead(id: String)
    suspend fun markAllRead()
    suspend fun dismiss(id: String)
    suspend fun deleteAll()
    fun ensureChannels()
    fun canPostSystemNotifications(): Boolean
}
