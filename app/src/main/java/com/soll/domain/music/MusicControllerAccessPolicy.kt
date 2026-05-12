package com.soll.domain.music

object MusicControllerAccessPolicy {
    fun canConnect(
        appPackageName: String,
        controllerPackageName: String,
        isMediaNotificationController: Boolean,
        isTrusted: Boolean,
        headsetControlsEnabled: Boolean,
    ): Boolean {
        if (controllerPackageName == appPackageName) return true
        if (isMediaNotificationController) return true
        if (!headsetControlsEnabled) return false
        return isTrusted
    }
}
