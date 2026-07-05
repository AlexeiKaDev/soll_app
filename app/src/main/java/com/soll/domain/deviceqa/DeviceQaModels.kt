package com.soll.domain.deviceqa

enum class DeviceQaCategory(val title: String) {
    NOTIFICATIONS("Уведомления"),
    BACKGROUND("Фон"),
    THEME("Тема"),
    GADGETS("Гаджеты"),
}

enum class DeviceQaCheckId(val storageKey: String) {
    NOTIFICATION_PERMISSION("notification_permission"),
    NOTIFICATION_CHANNELS("notification_channels"),
    NOTIFICATION_ANDROID13_FLOW("notification_android13_flow"),
    NOTIFICATION_TAP_ROUTING("notification_tap_routing"),
    BATTERY_OPTIMIZATION("battery_optimization"),
    THEME_VISUAL_PASS("theme_visual_pass"),
    GADGET_PROTOCOL_SCHEMA("gadget_protocol_schema"),
    GADGET_SERVER_LOCAL_BINDING("gadget_server_local_binding"),
    GADGET_MESH_OUTBOX_WORKER("gadget_mesh_outbox_worker"),
    GADGET_READ_ONLY_COMMAND_WORKER("gadget_read_only_command_worker"),
    GADGET_MANUAL_WRITE_FLOW("gadget_manual_write_flow");

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
