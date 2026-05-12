package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProvisioningPlanTest {
    @Test
    fun `aquik setup plan documents AP defaults and configure endpoint`() {
        val steps = DeviceProvisioningPlan.aquikSetupSteps()
        val text = steps.joinToString("\n") { "${it.title} ${it.description}" }

        assertEquals("AQUIK-Setup", AquikProvisioningDefaults.setupApSsid)
        assertEquals("192.168.4.1", AquikProvisioningDefaults.setupApHost)
        assertTrue(text.contains("AQUIK-Setup"))
        assertTrue(text.contains("/api/wifi/configure"))
        assertTrue(text.contains("Вернитесь в домашнюю сеть"))
    }
}
