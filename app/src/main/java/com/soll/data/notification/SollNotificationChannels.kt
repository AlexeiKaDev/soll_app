package com.soll.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.soll.domain.notification.SollNotificationChannel

object SollNotificationChannels {
    const val BOT_SERVICE_NOTIFICATION_ID = 1001
    const val TTS_NOTIFICATION_ID = 1002
    const val MUSIC_NOTIFICATION_ID = 1003
    const val ACTIVITY_TRACKING_NOTIFICATION_ID = 1004
    const val SERVER_SYNC_NOTIFICATION_ID = 1005
    const val CHAT_NOTIFICATION_ID = 2001
    const val PORTABLE_SSD_NOTIFICATION_ID = 2002

    fun ensureAll(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    SollNotificationChannel.CHAT.channelId,
                    "Чат Soll",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Сообщения, действия и ответы сервера Soll"
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    SollNotificationChannel.ACTIVITY_TRACKING.channelId,
                    "Активность Soll",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Экономный фоновый шагомер и геоистория"
                    setShowBadge(false)
                },
                NotificationChannel(
                    SollNotificationChannel.TTS_PLAYBACK.channelId,
                    "Читалка Soll",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Озвучивание книг и управление TTS"
                    setShowBadge(false)
                },
                NotificationChannel(
                    SollNotificationChannel.MUSIC_PLAYBACK.channelId,
                    "Музыка Soll",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Фоновое воспроизведение локальной музыки"
                    setShowBadge(false)
                },
                NotificationChannel(
                    SollNotificationChannel.SERVER_SYNC.channelId,
                    "Фоновый sync Soll",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Поддерживает проверку чата и задач, когда приложение свернуто"
                    setShowBadge(false)
                },
                NotificationChannel(
                    SollNotificationChannel.EVENTS.channelId,
                    "События Soll",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Обычные события приложения"
                    setShowBadge(true)
                },
                NotificationChannel(
                    SollNotificationChannel.ALERTS.channelId,
                    "Важные события Soll",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Ошибки, блокировки и действия, требующие внимания"
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    SollNotificationChannel.TOOL_JOBS.channelId,
                    "Задачи Soll",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Результаты фоновых задач и инструментов"
                    setShowBadge(true)
                },
            )
        )
    }
}
