package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GadgetProfileCatalogTest {
    @Test
    fun `aquik is universal gadget profile for aquarium and greenhouse`() {
        val descriptor = GadgetProfileCatalog.byProfileId(AquikDeviceProfile.ID)

        requireNotNull(descriptor)
        assertEquals(GadgetDomain.AQUARIUM_GREENHOUSE, descriptor.domain)
        assertTrue(descriptor.primaryUseCases.contains("аквариум"))
        assertTrue(descriptor.primaryUseCases.contains("теплица"))
        assertTrue(descriptor.expectedSensors.contains("Температура воды"))
        assertTrue(descriptor.expectedActuators.contains("Водяной насос"))
        assertTrue(descriptor.plannedModules.contains("автоматизации"))
        assertEquals("Soll Gadget Protocol / Aquik v2", descriptor.protocolName)
        assertTrue(descriptor.communicationOptions.any { it.title == "Wi-Fi LAN" })
        assertTrue(descriptor.communicationOptions.any { it.title == "BLE" })
        assertTrue(descriptor.communicationOptions.any { it.title == "Bluetooth" })
        assertTrue(descriptor.communicationOptions.any { it.title == "Сервер Soll" && it.transport == "HTTPS/JSON" })
    }

    @Test
    fun `generic esp websocket stays available for future gadgets`() {
        val descriptor = GadgetProfileCatalog.byProfileId(GenericEspWebSocketProfile.ID)

        requireNotNull(descriptor)
        assertEquals(GadgetDomain.ESP_CONTROLLER, descriptor.domain)
        assertTrue(descriptor.summary.contains("будущих ESP-гаджетов"))
        assertTrue(descriptor.setupHint.contains("UI остается только в Android"))
        assertTrue(descriptor.communicationOptions.any { it.title == "Сервер Soll" })
    }

    @Test
    fun `sensor catalog evaluates aquik telemetry status`() {
        assertEquals(DeviceSensorStatus.NORMAL, GadgetSensorCatalog.statusFor("waterTemp", 25.0))
        assertEquals(DeviceSensorStatus.WARNING, GadgetSensorCatalog.statusFor("waterTemp", 30.0))
        assertEquals(DeviceSensorStatus.CRITICAL, GadgetSensorCatalog.statusFor("waterTemp", 36.0))
        assertEquals("Темп. воды", GadgetSensorCatalog.labelFor("waterTemp"))
    }
}
