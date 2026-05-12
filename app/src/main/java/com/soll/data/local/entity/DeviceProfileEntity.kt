package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.soll.domain.device.DeviceAuthMode
import com.soll.domain.device.DeviceProfile
import com.soll.domain.device.DeviceTransport
import org.json.JSONArray

@Entity(tableName = "device_profiles")
data class DeviceProfileEntity(
    @PrimaryKey
    val id: String,

    val name: String,
    val transport: String,

    @ColumnInfo(name = "auth_mode")
    val authMode: String,

    @ColumnInfo(name = "command_schema_version")
    val commandSchemaVersion: String,

    @ColumnInfo(name = "capabilities_json")
    val capabilitiesJson: String,
) {
    fun toDomain(): DeviceProfile =
        DeviceProfile(
            id = id,
            name = name,
            transport = runCatching { DeviceTransport.valueOf(transport) }
                .getOrDefault(DeviceTransport.WEBSOCKET),
            authMode = runCatching { DeviceAuthMode.valueOf(authMode) }
                .getOrDefault(DeviceAuthMode.NONE),
            commandSchemaVersion = commandSchemaVersion,
            capabilities = JSONArray(capabilitiesJson).toStringList(),
        )

    companion object {
        fun fromDomain(profile: DeviceProfile): DeviceProfileEntity =
            DeviceProfileEntity(
                id = profile.id,
                name = profile.name,
                transport = profile.transport.name,
                authMode = profile.authMode.name,
                commandSchemaVersion = profile.commandSchemaVersion,
                capabilitiesJson = JSONArray(profile.capabilities).toString(),
            )
    }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
