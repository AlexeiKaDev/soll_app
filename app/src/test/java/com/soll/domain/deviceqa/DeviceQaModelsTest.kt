package com.soll.domain.deviceqa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceQaModelsTest {
    @Test
    fun `summary reports problems first`() {
        val checks = listOf(
            check(DeviceQaStatus.OK),
            check(DeviceQaStatus.MANUAL_PROBLEM),
            check(DeviceQaStatus.NEEDS_MANUAL_TEST),
        )

        assertEquals("Есть проблемы: 1", DeviceQaSummary.headline(checks))
    }

    @Test
    fun `summary reports manual checks when no problems`() {
        val checks = listOf(
            check(DeviceQaStatus.OK),
            check(DeviceQaStatus.NEEDS_MANUAL_TEST),
        )

        assertEquals("Нужна проверка: 1", DeviceQaSummary.headline(checks))
    }

    @Test
    fun `manual result only accepts manual statuses`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceQaManualResult(DeviceQaStatus.OK, checkedAt = 1L)
        }
    }

    @Test
    fun `manual result can keep tested device context`() {
        val result = DeviceQaManualResult(
            status = DeviceQaStatus.MANUAL_OK,
            checkedAt = 1L,
            deviceSummary = "Doogee S200, Android 15 (API 35)",
        )

        assertEquals("Doogee S200, Android 15 (API 35)", result.deviceSummary)
    }

    @Test
    fun `check keeps expected result and roadmap context`() {
        val check = check(DeviceQaStatus.NEEDS_MANUAL_TEST).copy(
            expectedResult = "Музыка играет 10+ минут с выключенным экраном.",
            roadmapRef = "Music Player / Device QA",
        )

        assertEquals("Музыка играет 10+ минут с выключенным экраном.", check.expectedResult)
        assertEquals("Music Player / Device QA", check.roadmapRef)
    }

    private fun check(status: DeviceQaStatus): DeviceQaCheck =
        DeviceQaCheck(
            id = DeviceQaCheckId.MUSIC_SCREEN_OFF,
            category = DeviceQaCategory.MUSIC,
            title = "Проверка",
            detail = "Детали",
            status = DeviceQaStatus.OK,
            manual = true,
            lastManualResult = if (status == DeviceQaStatus.MANUAL_OK || status == DeviceQaStatus.MANUAL_PROBLEM) {
                DeviceQaManualResult(status, checkedAt = 1L)
            } else {
                null
            },
        ).copy(status = status)
}
