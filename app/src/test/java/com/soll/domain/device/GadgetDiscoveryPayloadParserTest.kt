package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GadgetDiscoveryPayloadParserTest {
    @Test
    fun `android discovery contract mirrors soll protocol schema`() {
        assertEquals("soll-gadget-discovery-v1", GadgetDiscoveryContract.VERSION)
        assertEquals(GadgetDiscoveryMethod.LAN_MDNS, GadgetDiscoveryContract.primaryOrder.first())
        assertTrue(GadgetDiscoveryContract.mdnsServiceTypes.contains("_aquik._tcp"))
        assertTrue(GadgetDiscoveryContract.setupSsidPrefixes.contains("AQUIK_"))
        assertEquals("/device.json", GadgetDiscoveryContract.deviceJsonPath)
    }

    @Test
    fun `device json maps websocket endpoint and metadata`() {
        val candidate = GadgetDiscoveryPayloadParser.candidateFromDeviceJson(
            jsonText = """
                {
                  "deviceId": "aquik-main",
                  "deviceName": "Основной аквариум",
                  "boardType": "ESP32",
                  "firmwareVersion": "2.1.0",
                  "websocketUrl": "ws://192.168.1.50:81/ws",
                  "websocketPort": 81,
                  "macAddress": "AA:BB:CC:DD:EE:FF",
                  "capabilities": ["WIFI", "WEBSOCKET", "MDNS"]
                }
            """.trimIndent(),
            fallbackHost = "192.168.1.50",
            method = GadgetDiscoveryMethod.LAN_MDNS,
        )

        assertEquals("Основной аквариум", candidate.displayName)
        assertEquals(AquikDeviceProfile.ID, candidate.profileId)
        assertEquals("192.168.1.50", candidate.host)
        assertEquals(81, candidate.port)
        assertEquals("ws", candidate.path)
        assertEquals("ESP32", candidate.chip)
        assertEquals("2.1.0", candidate.firmware)
        assertTrue(candidate.capabilities.contains("WEBSOCKET"))
    }

    @Test
    fun `qr pairing text becomes candidate`() {
        val candidate = GadgetDiscoveryPayloadParser.candidateFromPairingText(
            "soll-device://pair?profile=aquik-v2&host=192.168.1.77&port=81&path=ws&token=secret"
        )

        assertNotNull(candidate)
        val parsed = candidate!!
        assertEquals(GadgetDiscoveryMethod.QR, parsed.method)
        assertEquals("192.168.1.77", parsed.host)
        assertEquals("secret", parsed.token)
    }

    @Test
    fun `aquik setup qr becomes provisioning candidate`() {
        val candidate = GadgetDiscoveryPayloadParser.candidateFromPairingText(
            "aquik://setup?ssid=AQUIK_ABC123&key=12345678&id=ESP32_C3-ABC123&chip=ESP32_C3&fw=1.0.0"
        )

        assertNotNull(candidate)
        val parsed = candidate!!
        assertEquals(GadgetDiscoveryMethod.QR, parsed.method)
        assertEquals("AQUIK_ABC123", parsed.apSsid)
        assertEquals("ESP32_C3", parsed.chip)
        assertTrue(parsed.capabilities.contains("PROVISIONING"))
    }

    @Test
    fun `ssdp headers are parsed case insensitively`() {
        val headers = GadgetDiscoveryPayloadParser.parseSsdpHeaders(
            """
                HTTP/1.1 200 OK
                LOCATION: http://192.168.1.88/device.json
                X-DEVICE-ID: greenhouse
                X-FIRMWARE-VERSION: 2.0
            """.trimIndent()
        )

        val candidate = GadgetDiscoveryPayloadParser.candidateFromSsdpHeaders(headers, "192.168.1.88")

        assertEquals("greenhouse", candidate.displayName)
        assertEquals("192.168.1.88", candidate.host)
        assertEquals("2.0", candidate.firmware)
        assertEquals(GadgetDiscoveryMethod.LAN_SSDP, candidate.method)
    }

    @Test
    fun `aquik ssdp headers are accepted`() {
        val headers = GadgetDiscoveryPayloadParser.parseSsdpHeaders(
            """
                HTTP/1.1 200 OK
                ST: urn:aquik:device:controller:1
                LOCATION: http://192.168.1.99:80/device-desc.xml
                AQUIK-DEVICE-ID: AQUIK_ABC123
                AQUIK-CHIP-TYPE: ESP32_C3
                AQUIK-FW-VERSION: 1.0.0
                AQUIK-MAC: AA:BB:CC:DD:EE:FF
                AQUIK-CAPABILITIES: WIFI,WEBSOCKET,SSDP
            """.trimIndent()
        )

        val candidate = GadgetDiscoveryPayloadParser.candidateFromSsdpHeaders(headers, "192.168.1.99")

        assertEquals("AQUIK_ABC123", candidate.displayName)
        assertEquals("ESP32_C3", candidate.chip)
        assertEquals("1.0.0", candidate.firmware)
        assertTrue(candidate.capabilities.contains("SSDP"))
        assertEquals("http://192.168.1.99/device.json", GadgetDiscoveryPayloadParser.deviceJsonUrlFromSsdpLocation(headers.getValue("LOCATION")))
    }
}
