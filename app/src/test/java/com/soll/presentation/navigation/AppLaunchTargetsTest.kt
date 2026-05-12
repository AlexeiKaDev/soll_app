package com.soll.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLaunchTargetsTest {

    @Test
    fun `notification extras open logs notifications tab`() {
        val command = AppLaunchTargets.fromExtras(
            section = AppLaunchTargets.SECTION_LOGS,
            logsTab = AppLaunchTargets.LOGS_TAB_NOTIFICATIONS,
        )

        assertEquals(AppLaunchTargets.SECTION_LOGS, command?.section)
        assertEquals(AppLaunchTargets.LOGS_TAB_NOTIFICATIONS, command?.logsTab)
    }

    @Test
    fun `unknown launch section is ignored`() {
        assertNull(AppLaunchTargets.fromExtras(section = "unknown", logsTab = null))
    }

    @Test
    fun `tool extras open requested local tools`() {
        listOf(
            AppLaunchTargets.SECTION_MUSIC,
            AppLaunchTargets.SECTION_BOOK_READER,
            AppLaunchTargets.SECTION_NOTES,
        ).forEach { section ->
            val command = AppLaunchTargets.fromExtras(section = section, logsTab = null)
            assertEquals(section, command?.section)
            assertNull(command?.logsTab)
        }
    }

    @Test
    fun `unknown logs tab still opens logs without tab override`() {
        val command = AppLaunchTargets.fromExtras(
            section = AppLaunchTargets.SECTION_LOGS,
            logsTab = "unknown",
        )

        assertEquals(AppLaunchTargets.SECTION_LOGS, command?.section)
        assertNull(command?.logsTab)
    }
}
