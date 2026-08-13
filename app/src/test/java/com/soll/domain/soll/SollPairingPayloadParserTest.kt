package com.soll.domain.soll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SollPairingPayloadParserTest {
    @Test
    fun `parses desktop deep link pairing payload`() {
        val payload = SollPairingPayloadParser.parse(
            "soll://pair?type=soll_android_pairing&v=1" +
                "&server_url=https%3A%2F%2Fsales.monolith-ost.com%2F" +
                "&api_path_prefix=%2Fapi%2Fv1%2Fsoll%2F" +
                "&access_token=abcdef123456" +
                "&client_id=desktop-main" +
                "&session_id=soll-main",
        )

        assertNotNull(payload)
        assertEquals("https://sales.monolith-ost.com/", payload?.serverUrl)
        assertEquals("api/v1/soll", payload?.apiPathPrefix)
        assertEquals("abcdef123456", payload?.accessToken)
        assertEquals("desktop-main", payload?.clientId)
        assertEquals("soll-main", payload?.sessionId)
        assertEquals(true, payload?.usesRelayBearerAuth)
    }

    @Test
    fun `parses ordinary remote v1 bearer without new parser semantics`() {
        val payload = SollPairingPayloadParser.parse(
            "soll://pair?type=soll_android_pairing&v=1" +
                "&auth_mode=relay_bearer" +
                "&server_url=https%3A%2F%2Fsales.monolith-ost.com%2F" +
                "&api_path_prefix=api%2Fv1%2Fsoll" +
                "&access_token=ordinary-android-token" +
                "&client_id=android-main" +
                "&session_id=soll-main",
        )

        assertNotNull(payload)
        assertEquals("ordinary-android-token", payload?.accessToken)
        assertEquals("android-main", payload?.clientId)
        assertEquals("soll-main", payload?.sessionId)
        assertEquals(true, payload?.usesRelayBearerAuth)
    }

    @Test
    fun `parses json pairing payload with device auth material`() {
        val payload = SollPairingPayloadParser.parse(
            """
            {
              "type": "soll_android_pairing",
              "server_url": "https://sales.monolith-ost.com/",
              "api_path_prefix": "api/v1/soll",
              "device_id": "phone-1",
              "pairing_secret": "secret-1"
            }
            """.trimIndent(),
        )

        assertNotNull(payload)
        assertEquals("phone-1", payload?.deviceId)
        assertEquals("secret-1", payload?.pairingSecret)
        assertEquals("", payload?.accessToken)
        assertEquals(false, payload?.usesRelayBearerAuth)
    }

    @Test
    fun `rejects unknown or incomplete pairing payloads`() {
        assertNull(SollPairingPayloadParser.parse("https://sales.monolith-ost.com/"))
        assertNull(SollPairingPayloadParser.parse("soll://pair?type=other&server_url=https%3A%2F%2Fx&api_path_prefix=api&access_token=t"))
        assertNull(SollPairingPayloadParser.parse("soll://pair?server_url=https%3A%2F%2Fx&api_path_prefix=api"))
    }
}
