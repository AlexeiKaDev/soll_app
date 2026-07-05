package com.soll.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {
    @Test
    fun `registry covers current Soll core capabilities`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings())

        assertEquals(
            setOf(
                "chat",
                "start",
                "help",
                "ping",
                "status",
                "info",
                "logs",
                "storage",
                "files",
                "download",
                "sms",
                "sms_send",
                "calls",
                "call",
                "contacts",
                "location",
                "photo",
                "record",
                "tasks",
                "sync",
                "jobs",
                "raw",
                "server_action",
                "devices",
                "field_map",
                "portable_ssd",
                "notify",
                "vibrate",
                "flashlight",
                "volume",
                "alarm",
                "brightness",
                "bluetooth",
                "wifi",
            ),
            registry.capabilities.map { it.id }.toSet(),
        )
    }

    @Test
    fun `safe commands remain allowed when risky capabilities are disabled`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings(riskyCapabilitiesEnabled = false))

        val decision = registry.checkCommand("status")

        assertTrue(decision.allowed)
        assertEquals(RiskTier.SAFE_INFO, decision.capability?.riskTier)
    }

    @Test
    fun `risky commands are blocked by global risky switch`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings(riskyCapabilitiesEnabled = false))

        val decision = registry.checkCommand("server_action")

        assertFalse(decision.allowed)
        assertEquals(CapabilityBlockReason.RISKY_CAPABILITIES_DISABLED, decision.reason)
        assertEquals(RiskTier.MONEY_OR_EXTERNAL_ACTION, decision.capability?.riskTier)
    }

    @Test
    fun `individual disabled command is blocked`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings(disabledCapabilities = setOf("chat")))

        val decision = registry.checkCommand("chat")

        assertFalse(decision.allowed)
        assertEquals(CapabilityBlockReason.CAPABILITY_DISABLED, decision.reason)
    }

    @Test
    fun `communication and media commands require confirmation metadata`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings())

        assertTrue(registry.get("server_action")!!.requiresConfirmation)
        assertTrue(registry.get("sms_send")!!.requiresConfirmation)
        assertTrue(registry.get("photo")!!.requiresConfirmation)
        assertTrue(registry.get("server_action")!!.auditRequired)
    }

    @Test
    fun `chat is safe information channel`() {
        val capability = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .single { it.id == "chat" }

        assertEquals(RiskTier.SAFE_INFO, capability.riskTier)
        assertFalse(capability.auditRequired)
        assertFalse(capability.requiresConfirmation)
    }

    @Test
    fun `activity capability uses location permission and personal data tier`() {
        val capability = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .single { it.id == "field_map" }

        assertEquals(RiskTier.PERSONAL_DATA, capability.riskTier)
        assertEquals(
            listOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            capability.requiredAndroidPermissions,
        )
        assertTrue(capability.enabledByDefault)
        assertFalse(capability.requiresConfirmation)
    }

    private class FakeCapabilitySettings(
        private val riskyCapabilitiesEnabled: Boolean = true,
        private val disabledCapabilities: Set<String> = emptySet(),
    ) : CapabilitySettings {
        override fun isRiskyCapabilitiesEnabled(): Boolean = riskyCapabilitiesEnabled

        override fun isCapabilityEnabled(capability: Capability): Boolean =
            capability.enabledByDefault && capability.id !in disabledCapabilities
    }
}
