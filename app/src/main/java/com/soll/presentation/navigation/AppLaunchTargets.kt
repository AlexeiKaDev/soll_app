package com.soll.presentation.navigation

data class AppLaunchCommand(
    val section: String,
    val logsTab: String? = null,
    val nonce: Long = System.nanoTime(),
)

object AppLaunchTargets {
    const val EXTRA_OPEN_SECTION = "soll_open_section"
    const val EXTRA_OPEN_LOGS_TAB = "soll_open_logs_tab"
    const val SECTION_LOGS = "logs"
    const val SECTION_BOOK_READER = "book_reader"
    const val SECTION_MUSIC = "music"
    const val SECTION_NOTES = "notes"
    const val LOGS_TAB_NOTIFICATIONS = "notifications"

    fun fromExtras(section: String?, logsTab: String?): AppLaunchCommand? =
        when (section) {
            SECTION_LOGS -> AppLaunchCommand(
                section = SECTION_LOGS,
                logsTab = logsTab.takeIf { it == LOGS_TAB_NOTIFICATIONS },
            )
            SECTION_BOOK_READER,
            SECTION_MUSIC,
            SECTION_NOTES -> AppLaunchCommand(section = section)
            else -> null
        }
}
