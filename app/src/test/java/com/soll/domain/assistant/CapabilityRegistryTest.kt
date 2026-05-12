package com.soll.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {
    @Test
    fun `registry covers all current telegram commands`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings())

        assertEquals(
            setOf(
                "start",
                "help",
                "ping",
                "status",
                "info",
                "logs",
                "jobs",
                "sync",
                "storage",
                "ask_soll",
                "raw",
                "scanner",
                "devices",
                "nfc",
                "music",
                "field_map",
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

        val decision = registry.checkCommand("photo")

        assertFalse(decision.allowed)
        assertEquals(CapabilityBlockReason.RISKY_CAPABILITIES_DISABLED, decision.reason)
        assertEquals(RiskTier.FILE_MEDIA, decision.capability?.riskTier)
    }

    @Test
    fun `individual disabled command is blocked`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings(disabledCapabilities = setOf("ping")))

        val decision = registry.checkCommand("ping")

        assertFalse(decision.allowed)
        assertEquals(CapabilityBlockReason.CAPABILITY_DISABLED, decision.reason)
    }

    @Test
    fun `communication and media commands require confirmation metadata`() {
        val registry = CapabilityRegistry(FakeCapabilitySettings())

        assertTrue(registry.get("sms_send")!!.requiresConfirmation)
        assertTrue(registry.get("download")!!.auditRequired)
    }

    @Test
    fun `ask soll sends personal request to server and is audited`() {
        val capability = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .single { it.id == "ask_soll" }

        assertEquals(RiskTier.PERSONAL_DATA, capability.riskTier)
        assertTrue(capability.auditRequired)
        assertFalse(capability.requiresConfirmation)
    }

    @Test
    fun `scanner capability uses camera permission and personal data tier`() {
        val capability = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .single { it.id == "scanner" }

        assertEquals(RiskTier.PERSONAL_DATA, capability.riskTier)
        assertEquals(listOf(android.Manifest.permission.CAMERA), capability.requiredAndroidPermissions)
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
