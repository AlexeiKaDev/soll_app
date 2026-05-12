package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.device.DeviceAuthMode
import com.soll.domain.device.DeviceTransport
import com.soll.domain.device.KnownDevice

@Entity(
    tableName = "known_devices",
    indices = [
        Index(value = ["profile_id"]),
        Index(value = ["host", "port"]),
        Index(value = ["updated_at"]),
    ],
)
data class KnownDeviceEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "profile_id")
    val profileId: String,

    val name: String,
    val host: String,
    val port: Int,
    val path: String,
    val transport: String,

    @ColumnInfo(name = "auth_mode")
    val authMode: String,

    @ColumnInfo(name = "last_status")
    val lastStatus: String,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    fun toDomain(): KnownDevice =
        KnownDevice(
            id = id,
            profileId = profileId,
            name = name,
            host = host,
            port = port,
            path = path,
            transport = runCatching { DeviceTransport.valueOf(transport) }
                .getOrDefault(DeviceTransport.WEBSOCKET),
            authMode = runCatching { DeviceAuthMode.valueOf(authMode) }
                .getOrDefault(DeviceAuthMode.NONE),
            lastStatus = lastStatus,
            lastSeenAt = lastSeenAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun fromDomain(device: KnownDevice): KnownDeviceEntity =
            KnownDeviceEntity(
                id = device.id,
                profileId = device.profileId,
                name = device.name,
                host = device.host,
                port = device.port,
                path = device.path,
                transport = device.transport.name,
                authMode = device.authMode.name,
                lastStatus = device.lastStatus,
                lastSeenAt = device.lastSeenAt,
                createdAt = device.createdAt,
                updatedAt = device.updatedAt,
            )
    }
}
