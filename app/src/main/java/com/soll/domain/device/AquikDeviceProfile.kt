package com.soll.domain.device

object AquikDeviceProfile {
    const val ID = "aquik-v2"
    const val COMMAND_AUTH = "auth"
    const val COMMAND_GET_INFO = "getSystemInfo"
    const val COMMAND_GET_INFO_LEGACY = "getInfo"
    const val COMMAND_GET_CONFIG = "getConfig"
    const val COMMAND_GET_SETTINGS = "getSettings"
    const val COMMAND_SET_SETTINGS = "setSettings"
    const val COMMAND_GET_SENSORS = "getSensors"
    const val COMMAND_GET_ACTUATORS = "getActuators"
    const val COMMAND_SET_PUMP = "setPump"
    const val COMMAND_SET_FAN = "setFan"
    const val COMMAND_SET_LED = "setLED"
    const val COMMAND_GET_SCHEDULES = "getSchedules"
    const val COMMAND_ADD_SCHEDULE = "addSchedule"
    const val COMMAND_UPDATE_SCHEDULE = "updateSchedule"
    const val COMMAND_DELETE_SCHEDULE = "deleteSchedule"
    const val COMMAND_SCAN_I2C = "scanI2C"

    val profile = DeviceProfile(
        id = ID,
        name = "Aquik v2",
        transport = DeviceTransport.WEBSOCKET,
        authMode = DeviceAuthMode.TOKEN,
        commandSchemaVersion = "2.0",
        capabilities = listOf(
            COMMAND_AUTH,
            COMMAND_GET_INFO,
            COMMAND_GET_INFO_LEGACY,
            COMMAND_GET_CONFIG,
            COMMAND_GET_SETTINGS,
            COMMAND_SET_SETTINGS,
            COMMAND_GET_SENSORS,
            COMMAND_GET_ACTUATORS,
            COMMAND_SET_PUMP,
            COMMAND_SET_FAN,
            COMMAND_SET_LED,
            COMMAND_GET_SCHEDULES,
            COMMAND_ADD_SCHEDULE,
            COMMAND_UPDATE_SCHEDULE,
            COMMAND_DELETE_SCHEDULE,
            COMMAND_SCAN_I2C,
        ),
    )
}
