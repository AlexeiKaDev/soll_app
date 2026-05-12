package com.soll.domain.deviceqa

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceQaReportFormatter {
    fun buildReport(
        checks: List<DeviceQaCheck>,
        generatedAt: Long,
        deviceSummary: String,
        appSummary: String? = null,
    ): String = buildString {
        val statusCounts = DeviceQaStatusCounts.from(checks)
        appendLine("# Отчет Device QA Soll App")
        appendLine()
        appendLine("- Устройство: $deviceSummary")
        appSummary?.takeIf { it.isNotBlank() }?.let { app ->
            appendLine("- Версия приложения: $app")
        }
        appendLine("- Создано: ${formatReportTime(generatedAt)}")
        appendLine("- Итог: ${DeviceQaSummary.headline(checks)}")
        appendLine("- Статусы: ОК/проверено ${statusCounts.ok}, внимание/проверить ${statusCounts.warning}, проблемы ${statusCounts.problem}")

        DeviceQaCategory.entries.forEach { category ->
            val group = checks.filter { it.category == category }
            if (group.isEmpty()) return@forEach

            appendLine()
            appendLine("## ${category.title}")
            group.forEach { check ->
                appendLine("- ${check.title}: ${check.effectiveStatus.label}")
                appendLine("  - Детали: ${check.detail}")
                check.expectedResult?.takeIf { it.isNotBlank() }?.let { expected ->
                    appendLine("  - Ожидание: $expected")
                }
                check.roadmapRef?.takeIf { it.isNotBlank() }?.let { ref ->
                    appendLine("  - План: $ref")
                }
                check.lastManualResult?.let { result ->
                    appendLine("  - Ручная проверка: ${result.status.label}, ${formatReportTime(result.checkedAt)}")
                    result.deviceSummary?.let { device ->
                        appendLine("  - Устройство проверки: $device")
                    }
                }
            }
        }
    }.trimEnd()

    private fun formatReportTime(timestamp: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru")).format(Date(timestamp))

    private data class DeviceQaStatusCounts(
        val ok: Int,
        val warning: Int,
        val problem: Int,
    ) {
        companion object {
            fun from(checks: List<DeviceQaCheck>): DeviceQaStatusCounts {
                val statuses = checks.map { it.effectiveStatus }
                return DeviceQaStatusCounts(
                    ok = statuses.count { it == DeviceQaStatus.OK || it == DeviceQaStatus.MANUAL_OK },
                    warning = statuses.count { it == DeviceQaStatus.WARNING || it == DeviceQaStatus.NEEDS_MANUAL_TEST },
                    problem = statuses.count { it == DeviceQaStatus.PROBLEM || it == DeviceQaStatus.MANUAL_PROBLEM },
                )
            }
        }
    }
}
