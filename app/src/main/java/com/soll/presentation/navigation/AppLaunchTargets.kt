package com.soll.presentation.navigation

data class AppLaunchCommand(
    val section: String,
    val logsTab: String? = null,
    val sharedLink: SharedLinkPayload? = null,
    val nonce: Long = System.nanoTime(),
)

object AppLaunchTargets {
    const val EXTRA_OPEN_SECTION = "soll_open_section"
    const val EXTRA_OPEN_LOGS_TAB = "soll_open_logs_tab"
    const val EXTRA_NOTIFICATION_EVENT_ID = "soll_notification_event_id"
    const val SECTION_LOGS = "logs"
    const val SECTION_TODAY = "today"
    const val SECTION_CHAT = "chat"
    const val SECTION_TASKS = "tasks"
    const val SECTION_BOOK_READER = "book_reader"
    const val SECTION_MUSIC = "music"
    const val SECTION_NOTES = "notes"
    const val SECTION_PORTABLE_SSD = "portable_ssd"
    const val SECTION_SETTINGS = "settings"
    const val SECTION_SHARE_IMPORT = "share_import"
    const val LOGS_TAB_NOTIFICATIONS = "notifications"

    fun fromExtras(section: String?, logsTab: String?): AppLaunchCommand? =
        when (section) {
            "/$SECTION_TODAY", SECTION_TODAY, "assistant/today" -> AppLaunchCommand(section = SECTION_TODAY)
            "/$SECTION_TASKS" -> AppLaunchCommand(section = SECTION_TASKS)
            SECTION_CHAT -> AppLaunchCommand(section = SECTION_CHAT)
            SECTION_TASKS -> AppLaunchCommand(section = SECTION_TASKS)
            SECTION_LOGS -> AppLaunchCommand(
                section = SECTION_LOGS,
                logsTab = logsTab.takeIf { it == LOGS_TAB_NOTIFICATIONS },
            )
            SECTION_PORTABLE_SSD -> AppLaunchCommand(section = section)
            SECTION_MUSIC -> AppLaunchCommand(section = SECTION_MUSIC)
            SECTION_BOOK_READER -> AppLaunchCommand(section = SECTION_BOOK_READER)
            SECTION_SETTINGS -> AppLaunchCommand(section = SECTION_SETTINGS)
            else -> null
        }
}
