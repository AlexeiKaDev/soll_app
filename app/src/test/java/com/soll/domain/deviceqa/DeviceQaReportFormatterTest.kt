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
                id = DeviceQaCheckId.MUSIC_SCREEN_OFF,
                category = DeviceQaCategory.MUSIC,
                title = "Музыка с выключенным экраном",
                detail = "Проверить 10+ минут.",
                status = DeviceQaStatus.NEEDS_MANUAL_TEST,
                manual = true,
                expectedResult = "Музыка не останавливается после выключения экрана.",
                roadmapRef = "Music Player / Device QA",
                lastManualResult = DeviceQaManualResult(
                    status = DeviceQaStatus.MANUAL_OK,
                    checkedAt = 1_700_000_000_000L,
                    deviceSummary = "Doogee S200, Android 15 (API 35)",
                ),
            ),
            DeviceQaCheck(
                id = DeviceQaCheckId.NFC_OWNED_TAGS,
                category = DeviceQaCategory.NFC,
                title = "Свои NFC-метки",
                detail = "Проверить чтение/запись.",
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
        assertTrue(report.contains("## Музыка"))
        assertTrue(report.contains("## NFC"))
        assertTrue(report.contains("Разрешение уведомлений: ОК"))
        assertTrue(report.contains("Музыка с выключенным экраном: Проверено"))
        assertTrue(report.contains("Ожидание: Музыка не останавливается после выключения экрана."))
        assertTrue(report.contains("План: Music Player / Device QA"))
        assertTrue(report.contains("Устройство проверки: Doogee S200, Android 15 (API 35)"))
        assertTrue(report.contains("Свои NFC-метки: Нужна проверка"))
    }
}
