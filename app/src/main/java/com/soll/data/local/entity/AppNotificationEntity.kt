package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationStatus

@Entity(
    tableName = "app_notifications",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["status"]),
        Index(value = ["channel_id"]),
        Index(value = ["source"]),
        Index(value = ["dedupe_key"], unique = true),
    ],
)
data class AppNotificationEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "channel_id")
    val channelId: String,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String?,

    @ColumnInfo(name = "priority")
    val priority: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "shown_at")
    val shownAt: Long?,

    @ColumnInfo(name = "read_at")
    val readAt: Long?,

    @ColumnInfo(name = "dismissed_at")
    val dismissedAt: Long?,

    @ColumnInfo(name = "system_notification_id")
    val systemNotificationId: Int?,

    @ColumnInfo(name = "dedupe_key")
    val dedupeKey: String?,
) {
    fun toDomain(): SollNotification = SollNotification(
        id = id,
        channel = runCatching { SollNotificationChannel.valueOf(channelId) }
            .getOrDefault(SollNotificationChannel.EVENTS),
        type = type,
        source = source,
        title = title,
        message = message,
        payloadJson = payloadJson,
        priority = runCatching { SollNotificationPriority.valueOf(priority) }
            .getOrDefault(SollNotificationPriority.DEFAULT),
        status = runCatching { SollNotificationStatus.valueOf(status) }
            .getOrDefault(SollNotificationStatus.UNREAD),
        createdAt = createdAt,
        shownAt = shownAt,
        readAt = readAt,
        dismissedAt = dismissedAt,
        systemNotificationId = systemNotificationId,
        dedupeKey = dedupeKey,
    )

    companion object {
        fun fromDomain(notification: SollNotification): AppNotificationEntity = AppNotificationEntity(
            id = notification.id,
            channelId = notification.channel.name,
            type = notification.type,
            source = notification.source,
            title = notification.title,
            message = notification.message,
            payloadJson = notification.payloadJson,
            priority = notification.priority.name,
            status = notification.status.name,
            createdAt = notification.createdAt,
            shownAt = notification.shownAt,
            readAt = notification.readAt,
            dismissedAt = notification.dismissedAt,
            systemNotificationId = notification.systemNotificationId,
            dedupeKey = notification.dedupeKey,
        )
    }
}
