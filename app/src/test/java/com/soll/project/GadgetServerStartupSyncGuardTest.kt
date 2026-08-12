package com.soll.project

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GadgetServerStartupSyncGuardTest {
    @Test
    fun applicationStartupSchedulesOneImmediateGadgetSyncAlongsidePeriodicFallback() {
        val source = File("src/main/java/com/soll/SollApplication.kt").readText()

        assertTrue(source.contains("GadgetServerSyncScheduler.schedule(this, settingsRepository)"))
        assertEquals(
            1,
            Regex(Regex.escape("GadgetServerSyncScheduler.runNow(this, settingsRepository)"))
                .findAll(source)
                .count(),
        )
        assertTrue(source.contains("SollServerSyncAlarmScheduler.cancel(this)"))
    }
}
