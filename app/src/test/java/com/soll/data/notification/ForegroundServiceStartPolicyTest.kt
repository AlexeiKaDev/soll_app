package com.soll.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceStartPolicyTest {

    @Test
    fun `playback command uses foreground start until service accepts direct commands`() {
        assertEquals(
            ForegroundServiceStartMode.START_FOREGROUND_SERVICE,
            ForegroundServiceStartPolicy.forPlaybackCommand(
                acceptsDirectCommands = false,
                hasOngoingPlayback = false,
            ),
        )
    }

    @Test
    fun `playback command still uses foreground start for paused command-started service`() {
        assertEquals(
            ForegroundServiceStartMode.START_FOREGROUND_SERVICE,
            ForegroundServiceStartPolicy.forPlaybackCommand(
                acceptsDirectCommands = true,
                hasOngoingPlayback = false,
            ),
        )
    }

    @Test
    fun `playback command uses direct start for command-started service with ongoing playback`() {
        assertEquals(
            ForegroundServiceStartMode.START_SERVICE,
            ForegroundServiceStartPolicy.forPlaybackCommand(
                acceptsDirectCommands = true,
                hasOngoingPlayback = true,
            ),
        )
    }

    @Test
    fun `direct controls are blocked before command service start`() {
        assertFalse(ForegroundServiceStartPolicy.canSendDirectControlCommand(acceptsDirectCommands = false))
        assertTrue(ForegroundServiceStartPolicy.canSendDirectControlCommand(acceptsDirectCommands = true))
    }
}
