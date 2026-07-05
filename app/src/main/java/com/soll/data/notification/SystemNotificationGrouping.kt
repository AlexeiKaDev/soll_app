package com.soll.data.notification

import com.soll.domain.notification.SollNotificationChannel

internal fun systemNotificationGroupKey(channel: SollNotificationChannel, rawGroup: String? = null): String {
    val customGroup = rawGroup
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9_.-]+"), "_")
        ?.trim('_')
        ?.takeIf { it.isNotBlank() }
    return "soll.group.${customGroup ?: channel.channelId}"
}

internal fun systemNotificationSummaryId(
    channel: SollNotificationChannel,
    groupKey: String = systemNotificationGroupKey(channel),
): Int {
    if (groupKey != systemNotificationGroupKey(channel)) {
        return 4000 + (groupKey.hashCode() and 0x0fffffff)
    }
    return when (channel) {
        SollNotificationChannel.CHAT -> 3001
        SollNotificationChannel.ALERTS -> 3002
        SollNotificationChannel.TOOL_JOBS -> 3003
        SollNotificationChannel.EVENTS -> 3004
        SollNotificationChannel.SERVER_SYNC -> 3005
        SollNotificationChannel.BOT_SERVICE -> 3006
        SollNotificationChannel.TTS_PLAYBACK -> 3007
        SollNotificationChannel.MUSIC_PLAYBACK -> 3008
        SollNotificationChannel.ACTIVITY_TRACKING -> 3009
    }
}

internal fun systemNotificationSummaryTitle(channel: SollNotificationChannel): String =
    when (channel) {
        SollNotificationChannel.CHAT -> "Чат Soll"
        SollNotificationChannel.ALERTS -> "Важное Soll"
        SollNotificationChannel.TOOL_JOBS -> "Задачи Soll"
        SollNotificationChannel.EVENTS -> "События Soll"
        SollNotificationChannel.SERVER_SYNC -> "Синхронизация Soll"
        SollNotificationChannel.BOT_SERVICE -> "Архив Soll"
        SollNotificationChannel.TTS_PLAYBACK -> "Читалка Soll"
        SollNotificationChannel.MUSIC_PLAYBACK -> "Музыка Soll"
        SollNotificationChannel.ACTIVITY_TRACKING -> "Активность Soll"
    }

internal fun systemNotificationSummaryText(channel: SollNotificationChannel, unreadCount: Int): String {
    val count = unreadCount.coerceAtLeast(1)
    val noun = when {
        count % 100 in 11..14 -> "уведомлений"
        count % 10 == 1 -> "уведомление"
        count % 10 in 2..4 -> "уведомления"
        else -> "уведомлений"
    }
    return when (channel) {
        SollNotificationChannel.CHAT -> "$count $noun в чате"
        SollNotificationChannel.ALERTS -> "$count $noun требуют внимания"
        SollNotificationChannel.TOOL_JOBS -> "$count $noun по задачам"
        SollNotificationChannel.SERVER_SYNC -> "$count технических уведомлений"
        else -> "$count $noun"
    }
}
