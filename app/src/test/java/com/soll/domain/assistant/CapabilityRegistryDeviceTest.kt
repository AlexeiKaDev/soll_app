package com.soll.domain.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryDeviceTest {
    @Test
    fun `devices capability is dual use and disabled by default`() {
        val capability = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .single { it.id == "devices" }

        assertEquals(RiskTier.DUAL_USE_HARDWARE, capability.riskTier)
        assertFalse(capability.enabledByDefault)
    }

    @Test
    fun `devices capability must be enabled in settings`() {
        val disabled = CapabilityRegistry(FakeSettings(enabled = false)).checkCommand("devices")
        val enabled = CapabilityRegistry(FakeSettings(enabled = true)).checkCommand("devices")

        assertFalse(disabled.allowed)
        assertEquals(CapabilityBlockReason.CAPABILITY_DISABLED, disabled.reason)
        assertTrue(enabled.allowed)
    }

    @Test
    fun `nfc capability is dual use and disabled by default`() {
        val capability = CapabilityRegistry.CURRENT_COMMAND_CAPABILITIES
            .single { it.id == "nfc" }

        assertEquals(RiskTier.DUAL_USE_HARDWARE, capability.riskTier)
        assertFalse(capability.enabledByDefault)
    }

    private class FakeSettings(
        private val enabled: Boolean,
    ) : CapabilitySettings {
        override fun isRiskyCapabilitiesEnabled(): Boolean = true

        override fun isCapabilityEnabled(capability: Capability): Boolean =
            if (capability.id == "devices" || capability.id == "nfc") enabled else capability.enabledByDefault
    }
}
