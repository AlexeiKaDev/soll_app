package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.device.DeviceEvent

@Entity(
    tableName = "device_events",
    indices = [
        Index(value = ["device_id"]),
        Index(value = ["created_at"]),
    ],
)
data class DeviceEventEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    val type: String,
    val summary: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    fun toDomain(): DeviceEvent =
        DeviceEvent(
            id = id,
            deviceId = deviceId,
            type = type,
            summary = summary,
            payloadJson = payloadJson,
            createdAt = createdAt,
        )

    companion object {
        fun fromDomain(event: DeviceEvent): DeviceEventEntity =
            DeviceEventEntity(
                id = event.id,
                deviceId = event.deviceId,
                type = event.type,
                summary = event.summary,
                payloadJson = event.payloadJson,
                createdAt = event.createdAt,
            )
    }
}
