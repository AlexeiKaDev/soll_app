package com.soll.domain.device

import kotlinx.coroutines.flow.StateFlow

interface DeviceConnector {
    val state: StateFlow<DeviceConnectionState>

    suspend fun connect(config: DeviceConnectionConfig): Result<Unit>
    fun disconnect()
    suspend fun authenticate(token: String): Result<DeviceCommandResponse>
    suspend fun getInfo(): Result<DeviceCommandResponse>
    suspend fun getConfig(): Result<DeviceCommandResponse>
    suspend fun getSensors(): Result<DeviceCommandResponse>
    suspend fun getActuators(): Result<DeviceCommandResponse>
    suspend fun executeCommand(command: String, paramsJson: String = "{}"): Result<DeviceCommandResponse>
    suspend fun setPump(type: DevicePumpType, enabled: Boolean): Result<DeviceCommandResponse>
    suspend fun setFan(enabled: Boolean): Result<DeviceCommandResponse>
    suspend fun setLed(type: DeviceLedType, value: Int): Result<DeviceCommandResponse>
}
