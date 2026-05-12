package com.soll.domain.deviceqa

enum class DeviceQaCategory(val title: String) {
    NOTIFICATIONS("Уведомления"),
    BACKGROUND("Фон"),
    MUSIC("Музыка"),
    WIDGETS("Виджеты"),
    THEME("Тема"),
    NFC("NFC"),
}

enum class DeviceQaCheckId(val storageKey: String) {
    NOTIFICATION_PERMISSION("notification_permission"),
    NOTIFICATION_CHANNELS("notification_channels"),
    NOTIFICATION_ANDROID13_FLOW("notification_android13_flow"),
    NOTIFICATION_TAP_ROUTING("notification_tap_routing"),
    NOTIFICATION_MEDIA_SESSION("notification_media_session"),
    BATTERY_OPTIMIZATION("battery_optimization"),
    MUSIC_SCREEN_OFF("music_screen_off"),
    MUSIC_LOCKSCREEN_CONTROLS("music_lockscreen_controls"),
    MUSIC_AUDIO_FOCUS("music_audio_focus"),
    WIDGET_LAUNCHER_COLD("widget_launcher_cold"),
    WIDGET_MEDIA_CONTROLS("widget_media_controls"),
    THEME_VISUAL_PASS("theme_visual_pass"),
    NFC_OWNED_TAGS("nfc_owned_tags"),
    NFC_ACCESS_FOB_DIAGNOSTIC("nfc_access_fob_diagnostic");

    companion object {
        fun fromStorage(value: String): DeviceQaCheckId? =
            entries.firstOrNull { it.storageKey == value }
    }
}

enum class DeviceQaStatus(val label: String) {
    OK("ОК"),
    WARNING("Внимание"),
    PROBLEM("Проблема"),
    NEEDS_MANUAL_TEST("Нужна проверка"),
    MANUAL_OK("Проверено"),
    MANUAL_PROBLEM("Есть проблема"),
}

data class DeviceQaManualResult(
    val status: DeviceQaStatus,
    val checkedAt: Long,
    val deviceSummary: String? = null,
) {
    init {
        require(status == DeviceQaStatus.MANUAL_OK || status == DeviceQaStatus.MANUAL_PROBLEM) {
            "Ручной результат может быть только MANUAL_OK или MANUAL_PROBLEM"
        }
    }
}

data class DeviceQaCheck(
    val id: DeviceQaCheckId,
    val category: DeviceQaCategory,
    val title: String,
    val detail: String,
    val status: DeviceQaStatus,
    val manual: Boolean,
    val expectedResult: String? = null,
    val roadmapRef: String? = null,
    val actionLabel: String? = null,
    val lastManualResult: DeviceQaManualResult? = null,
) {
    val effectiveStatus: DeviceQaStatus
        get() = lastManualResult?.status ?: status
}

object DeviceQaSummary {
    fun headline(checks: List<DeviceQaCheck>): String {
        val effectiveStatuses = checks.map { it.effectiveStatus }
        val problems = effectiveStatuses.count { it == DeviceQaStatus.PROBLEM || it == DeviceQaStatus.MANUAL_PROBLEM }
        val warnings = effectiveStatuses.count { it == DeviceQaStatus.WARNING || it == DeviceQaStatus.NEEDS_MANUAL_TEST }
        return when {
            problems > 0 -> "Есть проблемы: $problems"
            warnings > 0 -> "Нужна проверка: $warnings"
            checks.isNotEmpty() -> "Все доступные проверки в норме"
            else -> "Проверки еще не загружены"
        }
    }
}
