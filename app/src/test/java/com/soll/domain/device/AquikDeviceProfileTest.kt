package com.soll.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AquikDeviceProfileTest {
    @Test
    fun `aquik profile exposes websocket commands`() {
        val profile = AquikDeviceProfile.profile

        assertEquals("aquik-v2", profile.id)
        assertEquals(DeviceTransport.WEBSOCKET, profile.transport)
        assertEquals(DeviceAuthMode.TOKEN, profile.authMode)
        assertTrue(profile.capabilities.contains("auth"))
        assertTrue(profile.capabilities.contains("getSensors"))
        assertTrue(profile.capabilities.contains("getSystemInfo"))
        assertTrue(profile.capabilities.contains("getSettings"))
        assertTrue(profile.capabilities.contains("getSchedules"))
    }

    @Test
    fun `manual websocket config builds endpoint url`() {
        val config = DeviceConnectionConfig(
            profile = AquikDeviceProfile.profile,
            host = "192.168.1.100",
            port = 81,
            path = "ws",
        )

        assertEquals("ws://192.168.1.100:81/ws", config.endpointUrl())
        assertEquals("aquik-v2:192.168.1.100:81", config.deviceId)
    }

    @Test
    fun `endpoint normalization preserves secure websocket scheme`() {
        val config = DeviceConnectionConfig(
            profile = AquikDeviceProfile.profile,
            host = "https://Aquik.local/gadget/ws",
        )

        assertEquals("wss://aquik.local:443/gadget/ws", config.endpointUrl())
        assertEquals("aquik-v2:wss://aquik.local:443", config.deviceId)
    }

    @Test
    fun `generic esp profile does not advertise aquik actuator commands`() {
        val commands = GenericEspWebSocketProfile.profile.capabilities

        assertTrue(commands.contains(AquikDeviceProfile.COMMAND_GET_SENSORS))
        assertTrue(commands.none { it == AquikDeviceProfile.COMMAND_SET_PUMP })
        assertTrue(commands.none { it == AquikDeviceProfile.COMMAND_SET_FAN })
        assertTrue(commands.none { it == AquikDeviceProfile.COMMAND_SET_LED })
    }
}
