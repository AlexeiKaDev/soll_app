package com.soll.domain.device

object GenericEspWebSocketProfile {
    const val ID = "generic-esp-websocket"

    val profile = DeviceProfile(
        id = ID,
        name = "ESP WebSocket",
        transport = DeviceTransport.WEBSOCKET,
        authMode = DeviceAuthMode.NONE,
        commandSchemaVersion = "generic-json-1",
        capabilities = listOf(
            AquikDeviceProfile.COMMAND_GET_INFO_LEGACY,
            AquikDeviceProfile.COMMAND_GET_SENSORS,
        ),
    )
}

object BuiltInDeviceProfiles {
    val all: List<DeviceProfile> = listOf(
        AquikDeviceProfile.profile,
        GenericEspWebSocketProfile.profile,
    )

    fun byId(id: String): DeviceProfile? =
        all.firstOrNull { it.id == id }
}
