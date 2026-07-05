package com.soll.presentation.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationGuardTest {
    @Test
    fun `devices are opened from tools, not bottom navigation`() {
        val destinations = projectFile("app/src/main/java/com/soll/presentation/navigation/AppDestinations.kt").readText()

        assertTrue(destinations.contains("val bottomBar = listOf(Chat, Tasks, Tools, Settings)"))
        assertFalse(destinations.contains("val bottomBar = listOf(Home, Tasks, Devices"))
        assertFalse(destinations.contains("val bottomBar = listOf(Home, Chat, Tasks, Tools, Logs, Settings)"))
        assertTrue(destinations.contains("route = Devices.route"))
        assertTrue(destinations.contains("title = Devices.title"))
        assertFalse(destinations.contains("val Home"))
        assertTrue(destinations.contains("route = Routes.MUSIC"))
        assertTrue(destinations.contains("route = Routes.BOOK_READER"))
        assertTrue(destinations.contains("route = Routes.BREATHING"))
        assertFalse(destinations.contains("route = Routes.NFC"))
    }

    private fun projectFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            val candidate = File(current, path)
            if (candidate.exists()) return candidate
            current = current.parentFile ?: current
        }
        error("Project file not found: $path")
    }
}
