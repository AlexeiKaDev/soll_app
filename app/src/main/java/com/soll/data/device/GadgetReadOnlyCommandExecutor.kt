package com.soll.data.device

import com.soll.data.repository.SettingsRepository
import com.soll.domain.device.AquikDeviceProfile
import com.soll.domain.device.BuiltInDeviceProfiles
import com.soll.domain.device.DeviceCommandResponse
import com.soll.domain.device.DeviceConnectionConfig
import com.soll.domain.device.DeviceLedType
import com.soll.domain.device.DevicePumpType
import com.soll.domain.device.KnownDevice
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.json.JSONObject

@Singleton
class GadgetReadOnlyCommandExecutor @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun execute(
        device: KnownDevice,
        command: String,
        params: Map<String, Any?>,
    ): Result<GadgetCommandExecutionResult> = runCatching {
        require(isReadOnlyGadgetCommand(command)) { "Unsupported write command for Android worker: $command" }
        val profile = BuiltInDeviceProfiles.byId(device.profileId)
            ?: error("Unknown local gadget profile: ${device.profileId}")
        require(command in profile.capabilities) { "Profile ${profile.id} does not support command $command" }

        val connector = WebSocketDeviceConnector(okHttpClient)
        val config = DeviceConnectionConfig(
            profile = profile,
            host = device.host,
            port = device.port,
            path = device.path,
            token = settingsRepository.getDeviceAuthToken(device.id),
        )
        connector.connect(config).getOrThrow()
        try {
            connector.executeReadOnlyCommand(command, params).getOrThrow().toExecutionResult()
        } finally {
            connector.disconnect()
        }
    }

    suspend fun executeManualApproved(
        device: KnownDevice,
        command: String,
        params: Map<String, Any?>,
    ): Result<GadgetCommandExecutionResult> = runCatching {
        val policy = gadgetCommandPolicy(command)
        require(policy.risk == GadgetCommandRisk.WRITE_REQUIRES_APPROVAL) { policy.reason }
        val profile = BuiltInDeviceProfiles.byId(device.profileId)
            ?: error("Unknown local gadget profile: ${device.profileId}")
        require(command in profile.capabilities) { "Profile ${profile.id} does not support command $command" }

        val connector = WebSocketDeviceConnector(okHttpClient)
        val config = DeviceConnectionConfig(
            profile = profile,
            host = device.host,
            port = device.port,
            path = device.path,
            token = settingsRepository.getDeviceAuthToken(device.id),
        )
        connector.connect(config).getOrThrow()
        try {
            connector.executeManualCommand(command, params).getOrThrow().toExecutionResult()
        } finally {
            connector.disconnect()
        }
    }
}

data class GadgetCommandExecutionResult(
    val payload: Map<String, Any?>,
)

internal enum class GadgetCommandRisk {
    READ_ONLY,
    WRITE_REQUIRES_APPROVAL,
    UNSUPPORTED,
}

internal data class GadgetCommandPolicy(
    val risk: GadgetCommandRisk,
    val reason: String,
)

internal fun gadgetCommandPolicy(command: String): GadgetCommandPolicy =
    when (command) {
        in READ_ONLY_GADGET_COMMANDS -> GadgetCommandPolicy(
            risk = GadgetCommandRisk.READ_ONLY,
            reason = "Read-only command can be executed by Android worker",
        )
        in WRITE_GADGET_COMMANDS -> GadgetCommandPolicy(
            risk = GadgetCommandRisk.WRITE_REQUIRES_APPROVAL,
            reason = "Write command requires explicit approval policy before Android execution: $command",
        )
        else -> GadgetCommandPolicy(
            risk = GadgetCommandRisk.UNSUPPORTED,
            reason = "Unsupported gadget command for Android worker: $command",
        )
    }

internal fun isReadOnlyGadgetCommand(command: String): Boolean =
    gadgetCommandPolicy(command).risk == GadgetCommandRisk.READ_ONLY

private suspend fun WebSocketDeviceConnector.executeReadOnlyCommand(
    command: String,
    params: Map<String, Any?>,
): Result<DeviceCommandResponse> =
    when (command) {
        AquikDeviceProfile.COMMAND_GET_INFO,
        AquikDeviceProfile.COMMAND_GET_INFO_LEGACY -> getInfo()
        AquikDeviceProfile.COMMAND_GET_CONFIG,
        AquikDeviceProfile.COMMAND_GET_SETTINGS -> getConfig()
        AquikDeviceProfile.COMMAND_GET_SENSORS -> getSensors()
        AquikDeviceProfile.COMMAND_GET_ACTUATORS -> getActuators()
        else -> executeCommand(command, JSONObject(params).toString())
    }

private suspend fun WebSocketDeviceConnector.executeManualCommand(
    command: String,
    params: Map<String, Any?>,
): Result<DeviceCommandResponse> =
    when (command) {
        AquikDeviceProfile.COMMAND_SET_PUMP -> setPump(
            type = params.stringValue("type").toPumpType(),
            enabled = params.booleanValue("state", "enabled", "value"),
        )
        AquikDeviceProfile.COMMAND_SET_FAN -> setFan(
            enabled = params.booleanValue("state", "enabled", "value"),
        )
        AquikDeviceProfile.COMMAND_SET_LED -> setLed(
            type = params.stringValue("type").toLedType(),
            value = params.intValue("value", "brightness", "level").coerceIn(0, 255),
        )
        else -> executeCommand(command, JSONObject(params).toString())
    }

private fun DeviceCommandResponse.toExecutionResult(): GadgetCommandExecutionResult =
    GadgetCommandExecutionResult(
        payload = mapOf(
            "command" to command,
            "success" to success,
            "data_json" to dataJson,
            "raw_json" to rawJson,
            "timestamp" to timestamp,
        ),
    )

private val READ_ONLY_GADGET_COMMANDS = setOf(
    AquikDeviceProfile.COMMAND_GET_INFO,
    AquikDeviceProfile.COMMAND_GET_INFO_LEGACY,
    AquikDeviceProfile.COMMAND_GET_CONFIG,
    AquikDeviceProfile.COMMAND_GET_SETTINGS,
    AquikDeviceProfile.COMMAND_GET_SENSORS,
    AquikDeviceProfile.COMMAND_GET_ACTUATORS,
)

private val WRITE_GADGET_COMMANDS = setOf(
    AquikDeviceProfile.COMMAND_SET_SETTINGS,
    AquikDeviceProfile.COMMAND_SET_PUMP,
    AquikDeviceProfile.COMMAND_SET_FAN,
    AquikDeviceProfile.COMMAND_SET_LED,
    AquikDeviceProfile.COMMAND_ADD_SCHEDULE,
    AquikDeviceProfile.COMMAND_UPDATE_SCHEDULE,
    AquikDeviceProfile.COMMAND_DELETE_SCHEDULE,
    AquikDeviceProfile.COMMAND_CALIBRATE_SENSOR,
    AquikDeviceProfile.COMMAND_UPSERT_AUTOMATION,
    AquikDeviceProfile.COMMAND_DELETE_AUTOMATION,
    AquikDeviceProfile.COMMAND_SCAN_I2C,
)

private fun Map<String, Any?>.stringValue(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> this[key]?.toString()?.trim()?.takeIf { it.isNotBlank() } }
        ?: throw IllegalArgumentException("Параметр ${keys.first()} не задан")

private fun Map<String, Any?>.booleanValue(vararg keys: String): Boolean {
    val value = keys.firstNotNullOfOrNull { key -> this[key] }
        ?: throw IllegalArgumentException("Параметр ${keys.first()} не задан")
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes", "on", "enabled", "вкл", "да" -> true
            "false", "0", "no", "off", "disabled", "выкл", "нет" -> false
            else -> throw IllegalArgumentException("Параметр ${keys.first()} должен быть boolean")
        }
        else -> throw IllegalArgumentException("Параметр ${keys.first()} должен быть boolean")
    }
}

private fun Map<String, Any?>.intValue(vararg keys: String): Int {
    val value = keys.firstNotNullOfOrNull { key -> this[key] }
        ?: throw IllegalArgumentException("Параметр ${keys.first()} не задан")
    return when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Параметр ${keys.first()} должен быть числом")
        else -> throw IllegalArgumentException("Параметр ${keys.first()} должен быть числом")
    }
}

private fun String.toPumpType(): DevicePumpType =
    DevicePumpType.entries.firstOrNull { it.wireName.equals(this, ignoreCase = true) || it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalArgumentException("Неизвестный насос: $this")

private fun String.toLedType(): DeviceLedType =
    DeviceLedType.entries.firstOrNull { it.wireName.equals(this, ignoreCase = true) || it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalArgumentException("Неизвестный LED: $this")
