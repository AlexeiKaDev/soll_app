package com.soll.data.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidPushTokenRegistrarTest {
    @Test
    fun `push registration auth accepts user bearer`() {
        assertEquals(
            true,
            hasSollPushRegistrationAuth(
                userAccessToken = "user-token",
                deviceAccessToken = "",
                deviceId = "",
                pairingSecret = "",
            ),
        )
    }

    @Test
    fun `push registration auth accepts device bearer`() {
        assertEquals(
            true,
            hasSollPushRegistrationAuth(
                userAccessToken = "",
                deviceAccessToken = "device-token",
                deviceId = "",
                pairingSecret = "",
            ),
        )
    }

    @Test
    fun `push registration auth accepts device pairing material`() {
        assertEquals(
            true,
            hasSollPushRegistrationAuth(
                userAccessToken = "",
                deviceAccessToken = "",
                deviceId = "android-phone",
                pairingSecret = "secret",
            ),
        )
    }

    @Test
    fun `push registration auth rejects missing credentials`() {
        assertEquals(
            false,
            hasSollPushRegistrationAuth(
                userAccessToken = "",
                deviceAccessToken = "",
                deviceId = "android-phone",
                pairingSecret = "",
            ),
        )
        assertEquals("Soll auth missing: issue device token or set bearer", PUSH_AUTH_MISSING_ERROR)
    }
}
