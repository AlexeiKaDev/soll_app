package com.soll.domain.soll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SollProtocolSchemaTest {
    @Test
    fun `current server discovery schema is compatible with android contract`() {
        val schema = SollProtocolSchema(
            version = SollProtocolContract.VERSION,
            auth = compatibleAuth(),
            gadgetCommandRoutes = listOf(
                "GET /api/v1/gadgets/{device_id}/commands",
                "POST /api/v1/gadgets/{device_id}/commands",
                "GET /api/v1/gadgets/{device_id}/commands/pending",
                "POST /api/v1/gadgets/{device_id}/commands/{command_id}/result",
                "GET /api/v1/mesh/outbox",
                "GET /api/v1/mesh/outbox/next",
                "POST /api/v1/mesh/outbox/{outbound_id}/ack",
                "POST /api/v1/mesh/outbox/{outbound_id}/retry",
            ),
            androidTransport = compatibleAndroidTransport(),
            workerContracts = compatibleWorkerContracts(),
            gadgetDiscovery = SollGadgetDiscoverySchema(
                version = "soll-gadget-discovery-v1",
                primaryOrder = listOf("mdns", "ssdp", "wifi_ap", "qr", "manual"),
                mdnsServiceTypes = listOf("_soll-gadget._tcp", "_aquik._tcp", "_ws._tcp"),
                ssdpHeaderNames = listOf(
                    "LOCATION",
                    "X-DEVICE-ID / AQUIK-DEVICE-ID",
                    "X-WS-PORT / AQUIK-WS-PORT",
                    "X-CAPABILITIES / AQUIK-CAPABILITIES",
                ),
                wifiSsidPrefixes = listOf("AQUIK_", "AQUIK-", "SOLL_", "SOLL-", "Soll-"),
                defaultSetupHost = "192.168.4.1",
                deviceJsonEndpoint = "/device.json",
                deviceJsonRecommendedFields = listOf(
                    "deviceName",
                    "websocketUrl",
                    "websocketPort",
                    "path",
                    "capabilities",
                ),
            ),
        )

        assertTrue(schema.compatible)
        assertTrue(schema.warnings.isEmpty())
    }

    @Test
    fun `missing discovery fields are reported as incompatibility`() {
        val schema = SollProtocolSchema(
            version = SollProtocolContract.VERSION,
            auth = SollProtocolAuth(),
            gadgetCommandRoutes = emptyList(),
            androidTransport = SollProtocolTransport(),
            workerContracts = emptyMap(),
            gadgetDiscovery = SollGadgetDiscoverySchema(
                version = "old",
                primaryOrder = listOf("manual"),
                mdnsServiceTypes = emptyList(),
                ssdpHeaderNames = emptyList(),
                wifiSsidPrefixes = emptyList(),
                defaultSetupHost = "192.168.0.1",
                deviceJsonEndpoint = "/meta.json",
                deviceJsonRecommendedFields = emptyList(),
            ),
        )

        assertFalse(schema.compatible)
        assertTrue(schema.warnings.any { it.contains("gadget:commands") })
        assertTrue(schema.warnings.any { it.contains("Версия поиска гаджетов") })
        assertTrue(schema.warnings.any { it.contains("AQUIK-DEVICE-ID") })
        assertTrue(schema.warnings.any { it.contains("/device.json") })
    }

    @Test
    fun `android bootstrap validates token refresh and worker contracts`() {
        val bootstrap = SollProtocolBootstrap(
            version = SollProtocolContract.VERSION,
            auth = compatibleAuth(),
            transport = compatibleAndroidTransport(),
            workerContracts = compatibleWorkerContracts(),
        )

        assertTrue(bootstrap.compatible)
        assertTrue(bootstrap.warnings.isEmpty())
    }

    @Test
    fun `android bootstrap reports missing refresh and workers`() {
        val bootstrap = SollProtocolBootstrap(
            version = SollProtocolContract.VERSION,
            auth = SollProtocolAuth(tokenType = "device-bearer"),
            transport = SollProtocolTransport(recommendedAuth = "device bearer"),
            workerContracts = emptyMap(),
        )

        assertFalse(bootstrap.compatible)
        assertTrue(bootstrap.warnings.any { it.contains("token_refresh") })
        assertTrue(bootstrap.warnings.any { it.contains("android_mesh_outbox_worker") })
        assertTrue(bootstrap.warnings.any { it.contains("gadget_command_worker") })
    }

    @Test
    fun `device token signature matches server HMAC contract`() {
        val signature = buildSollDeviceTokenSignature(
            pairingSecret = "secret",
            deviceId = "device-1",
            challengeId = "challenge-1",
            challenge = "abc",
            nonce = "nonce1234",
        )

        assertEquals(
            "58c158a600648bbfd982e4fd0e165a418539d0a82230766fde898291ce57f529",
            signature,
        )
    }

    private fun compatibleAuth(): SollProtocolAuth =
        SollProtocolAuth(
            pairingEndpoint = "POST /api/v1/devices/pairing",
            challengeEndpoint = "POST /api/v1/devices/{device_id}/challenge",
            tokenEndpoint = "POST /api/v1/devices/token",
            tokenRefreshEndpoint = SollProtocolContract.DEVICE_TOKEN_REFRESH_ENDPOINT,
            tokenType = "device-bearer",
            refreshRule = "Device bearer can refresh itself before expiry; old bearer is invalidated immediately.",
        )

    private fun compatibleAndroidTransport(): SollProtocolTransport =
        SollProtocolTransport(
            recommendedAuth = "device bearer",
            poll = listOf(
                "GET /api/v1/android/sync-status",
                "GET /api/v1/mesh/outbox/next",
                "POST /api/v1/gadgets/{device_id}/commands/claim",
            ),
            push = listOf(
                "POST /api/v1/mesh/simulate",
                "POST /api/v1/gadgets/{device_id}/telemetry",
            ),
        )

    private fun compatibleWorkerContracts(): Map<String, SollProtocolWorkerContract> =
        mapOf(
            "android_mesh_outbox_worker" to SollProtocolWorkerContract(
                owner = "soll_app",
                auth = "device bearer",
                requiredScopes = listOf("gadget:commands", "status:read"),
                leaseSecondsDefault = 60,
                pollIntervalSeconds = 15,
                lifecycle = listOf("queued", "sent", "acked", "failed"),
            ),
            "gadget_command_worker" to SollProtocolWorkerContract(
                owner = "soll_app or ESP firmware",
                auth = "device bearer",
                requiredScopes = listOf("gadget:commands"),
                leaseSecondsDefault = 60,
                pollIntervalSeconds = 10,
                lifecycle = listOf("pending", "claimed", "acked", "done", "failed", "expired"),
            ),
        )
}
