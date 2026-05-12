package com.soll.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.soll.domain.notification.SollNotificationChannel

object SollNotificationChannels {
    const val BOT_SERVICE_NOTIFICATION_ID = 1001
    const val TTS_NOTIFICATION_ID = 1002
    const val MUSIC_NOTIFICATION_ID = 1003
    const val TELEGRAM_COMMAND_NOTIFICATION_ID = 2001

    fun ensureAll(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    SollNotificationChannel.BOT_SERVICE.channelId,
                    "Фоновый бот Soll",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Статус фонового Telegram-бота"
                    setShowBadge(false)
                },
                NotificationChannel(
                    SollNotificationChannel.TTS_PLAYBACK.channelId,
                    "Чтение книг",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Управление озвучиванием книг"
                    setShowBadge(false)
                },
                NotificationChannel(
                    SollNotificationChannel.MUSIC_PLAYBACK.channelId,
                    "Музыка Soll",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Фоновое воспроизведение музыки"
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
