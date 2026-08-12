package com.soll.domain.soll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SollPairingVerificationTest {
    @Test
    fun `verification exposes endpoint but never URL credentials or query`() {
        val verification = sollPairingVerification(
            serverUrl = "http://owner:secret@127.0.0.1:49237/private?token=hidden#fragment",
            apiPathPrefix = "/api/v1/",
            userAccessToken = "bearer-secret",
            deviceId = "",
            pairingSecret = "",
            deviceAccessToken = "",
        )

        assertEquals("http://127.0.0.1:49237/api/v1", verification.endpointLabel)
        assertEquals(SollPairingAuthMode.BEARER, verification.authMode)
        assertTrue(verification.isReady)
        assertFalse(verification.endpointLabel.contains("secret"))
        assertFalse(verification.endpointLabel.contains("hidden"))
    }

    @Test
    fun `device pairing wins over retained legacy bearer without exposing auth material`() {
        val verification = sollPairingVerification(
            serverUrl = "http://127.0.0.1:49237",
            apiPathPrefix = "api/v1",
            userAccessToken = "legacy-bearer",
            deviceId = "android-phone",
            pairingSecret = "pairing-secret",
            deviceAccessToken = "",
        )

        assertEquals(SollPairingAuthMode.DEVICE, verification.authMode)
        assertEquals("http://127.0.0.1:49237/api/v1", verification.endpointLabel)
        assertFalse(verification.endpointLabel.contains("android-phone"))
        assertFalse(verification.endpointLabel.contains("pairing-secret"))
    }

    @Test
    fun `endpoint alone is not reported as ready pairing`() {
        val verification = sollPairingVerification(
            serverUrl = "https://sales.monolith-ost.com/",
            apiPathPrefix = "api/v1/soll",
            userAccessToken = "",
            deviceId = "",
            pairingSecret = "",
            deviceAccessToken = "",
        )

        assertEquals(SollPairingAuthMode.MISSING, verification.authMode)
        assertFalse(verification.isReady)
    }
}
