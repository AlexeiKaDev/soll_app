package com.soll.domain.deviceqa

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceQaReportFormatterTest {
    @Test
    fun `report contains headline categories checks and manual device context`() {
        val checks = listOf(
            DeviceQaCheck(
                id = DeviceQaCheckId.NOTIFICATION_PERMISSION,
                category = DeviceQaCategory.NOTIFICATIONS,
                title = "Разрешение уведомлений",
                detail = "Android разрешает системные уведомления Soll.",
                status = DeviceQaStatus.OK,
                manual = false,
            ),
            DeviceQaCheck(
                id = DeviceQaCheckId.GADGET_MANUAL_WRITE_FLOW,
                category = DeviceQaCategory.GADGETS,
                title = "Manual write flow",
                detail = "Проверить ручное подтверждение.",
                status = DeviceQaStatus.NEEDS_MANUAL_TEST,
                manual = true,
                expectedResult = "Write-команда не исполняется фоном без явного UI-подтверждения.",
                roadmapRef = "ESP Connector / Device QA",
                lastManualResult = DeviceQaManualResult(
                    status = DeviceQaStatus.MANUAL_OK,
                    checkedAt = 1_700_000_000_000L,
                    deviceSummary = "Doogee S200, Android 15 (API 35)",
                ),
            ),
            DeviceQaCheck(
                id = DeviceQaCheckId.GADGET_MESH_OUTBOX_WORKER,
                category = DeviceQaCategory.GADGETS,
                title = "Mesh/outbox worker",
                detail = "Проверить claim/ACK.",
                status = DeviceQaStatus.NEEDS_MANUAL_TEST,
                manual = true,
            ),
        )

        val report = DeviceQaReportFormatter.buildReport(
            checks = checks,
            generatedAt = 1_700_000_000_000L,
            deviceSummary = "Doogee S200, Android 15 (API 35)",
            appSummary = "com.soll.debug 1.0.0 (1)",
        )

        assertTrue(report.contains("# Отчет Device QA Soll App"))
        assertTrue(report.contains("Устройство: Doogee S200, Android 15 (API 35)"))
        assertTrue(report.contains("Версия приложения: com.soll.debug 1.0.0 (1)"))
        assertTrue(report.contains("Итог: Нужна проверка: 1"))
        assertTrue(report.contains("Статусы: ОК/проверено 2, внимание/проверить 1, проблемы 0"))
        assertTrue(report.contains("## Уведомления"))
        assertTrue(report.contains("## Гаджеты"))
        assertTrue(report.contains("Разрешение уведомлений: ОК"))
        assertTrue(report.contains("Manual write flow: Проверено"))
        assertTrue(report.contains("Ожидание: Write-команда не исполняется фоном без явного UI-подтверждения."))
        assertTrue(report.contains("План: ESP Connector / Device QA"))
        assertTrue(report.contains("Устройство проверки: Doogee S200, Android 15 (API 35)"))
        assertTrue(report.contains("Mesh/outbox worker: Нужна проверка"))
    }
}
