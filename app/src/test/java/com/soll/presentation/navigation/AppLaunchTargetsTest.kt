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
    fun `media notification extras open restored tools`() {
        assertEquals(
            AppLaunchTargets.SECTION_MUSIC,
            AppLaunchTargets.fromExtras(section = AppLaunchTargets.SECTION_MUSIC, logsTab = null)?.section,
        )
        assertEquals(
            AppLaunchTargets.SECTION_BOOK_READER,
            AppLaunchTargets.fromExtras(section = AppLaunchTargets.SECTION_BOOK_READER, logsTab = null)?.section,
        )
    }

    @Test
    fun `task notification extras open task board`() {
        assertEquals(
            AppLaunchTargets.SECTION_TASKS,
            AppLaunchTargets.fromExtras(section = AppLaunchTargets.SECTION_TASKS, logsTab = null)?.section,
        )
    }

    @Test
    fun `slash tasks path opens exact task board destination`() {
        assertEquals(
            Routes.TASKS,
            AppLaunchTargets.fromExtras(section = "/tasks", logsTab = null)?.section,
        )
    }

    @Test
    fun `today extras open exact today destination`() {
        listOf("today", "/today", "assistant/today").forEach { section ->
            assertEquals(
                AppLaunchTargets.SECTION_TODAY,
                AppLaunchTargets.fromExtras(section = section, logsTab = null)?.section,
            )
        }
    }

    @Test
    fun `pairing launch extras open settings`() {
        assertEquals(
            AppLaunchTargets.SECTION_SETTINGS,
            AppLaunchTargets.fromExtras(section = AppLaunchTargets.SECTION_SETTINGS, logsTab = null)?.section,
        )
    }

    @Test
    fun `archived notes extra is ignored`() {
        assertNull(AppLaunchTargets.fromExtras(section = AppLaunchTargets.SECTION_NOTES, logsTab = null))
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
