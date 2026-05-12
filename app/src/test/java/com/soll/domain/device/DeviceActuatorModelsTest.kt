package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceActuatorModelsTest {
    @Test
    fun `aquik profile exposes safe actuator commands`() {
        val commands = AquikDeviceProfile.profile.capabilities

        assertTrue(commands.contains(AquikDeviceProfile.COMMAND_GET_ACTUATORS))
        assertTrue(commands.contains(AquikDeviceProfile.COMMAND_SET_PUMP))
        assertTrue(commands.contains(AquikDeviceProfile.COMMAND_SET_FAN))
        assertTrue(commands.contains(AquikDeviceProfile.COMMAND_SET_LED))
    }

    @Test
    fun `actuator wire names match aquik protocol`() {
        assertEquals("air", DevicePumpType.AIR.wireName)
        assertEquals("water", DevicePumpType.WATER.wireName)
        assertEquals("full", DeviceLedType.FULL.wireName)
        assertEquals("white", DeviceLedType.WHITE.wireName)
    }

    @Test
    fun `built in profiles include aquik and generic esp websocket`() {
        val ids = BuiltInDeviceProfiles.all.map { it.id }

        assertTrue(ids.contains(AquikDeviceProfile.ID))
        assertTrue(ids.contains(GenericEspWebSocketProfile.ID))
    }
}
