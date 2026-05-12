package com.soll.data.notification

enum class ForegroundServiceStartMode {
    START_SERVICE,
    START_FOREGROUND_SERVICE,
}

object ForegroundServiceStartPolicy {
    fun forPlaybackCommand(
        acceptsDirectCommands: Boolean,
        hasOngoingPlayback: Boolean,
    ): ForegroundServiceStartMode =
        if (acceptsDirectCommands && hasOngoingPlayback) {
            ForegroundServiceStartMode.START_SERVICE
        } else {
            ForegroundServiceStartMode.START_FOREGROUND_SERVICE
        }

    fun canSendDirectControlCommand(acceptsDirectCommands: Boolean): Boolean =
        acceptsDirectCommands
}
