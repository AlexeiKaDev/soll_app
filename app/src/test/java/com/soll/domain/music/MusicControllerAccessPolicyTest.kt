package com.soll.domain.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicControllerAccessPolicyTest {

    @Test
    fun `own app and media notification controller are always allowed`() {
        assertTrue(
            MusicControllerAccessPolicy.canConnect(
                appPackageName = APP_PACKAGE,
                controllerPackageName = APP_PACKAGE,
                isMediaNotificationController = false,
                isTrusted = false,
                headsetControlsEnabled = false,
            )
        )

        assertTrue(
            MusicControllerAccessPolicy.canConnect(
                appPackageName = APP_PACKAGE,
                controllerPackageName = "android",
                isMediaNotificationController = true,
                isTrusted = false,
                headsetControlsEnabled = false,
            )
        )
    }

    @Test
    fun `external controls are blocked when headset controls are disabled`() {
        assertFalse(
            MusicControllerAccessPolicy.canConnect(
                appPackageName = APP_PACKAGE,
                controllerPackageName = "com.android.bluetooth",
                isMediaNotificationController = false,
                isTrusted = true,
                headsetControlsEnabled = false,
            )
        )
    }

    @Test
    fun `untrusted external controller is blocked even when headset controls are enabled`() {
        assertFalse(
            MusicControllerAccessPolicy.canConnect(
                appPackageName = APP_PACKAGE,
                controllerPackageName = "com.example.remote",
                isMediaNotificationController = false,
                isTrusted = false,
                headsetControlsEnabled = true,
            )
        )
    }

    @Test
    fun `trusted external controller is allowed only when headset controls are enabled`() {
        assertTrue(
            MusicControllerAccessPolicy.canConnect(
                appPackageName = APP_PACKAGE,
                controllerPackageName = "com.android.bluetooth",
                isMediaNotificationController = false,
                isTrusted = true,
                headsetControlsEnabled = true,
            )
        )
    }

    private companion object {
        const val APP_PACKAGE = "com.soll.debug"
    }
}
