package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.field.FieldPoint
import com.soll.domain.field.FieldPointSource
import com.soll.domain.field.FieldPointStatus
import com.soll.domain.field.GeoCoordinate
import java.util.UUID

@Entity(
    tableName = "field_points",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
        Index(value = ["task_id"]),
    ],
)
data class FieldPointEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String,
    val note: String,

    val latitude: Double,
    val longitude: Double,

    @ColumnInfo(name = "accuracy_meters")
    val accuracyMeters: Float? = null,

    val source: String = FieldPointSource.MANUAL.storageKey,
    val status: String = FieldPointStatus.PLANNED.storageKey,

    @ColumnInfo(name = "task_id")
    val taskId: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "visited_at")
    val visitedAt: Long? = null,
) {
    fun toDomain(): FieldPoint =
        FieldPoint(
            id = id,
            title = title,
            note = note,
            coordinate = GeoCoordinate(latitude = latitude, longitude = longitude),
            accuracyMeters = accuracyMeters,
            source = FieldPointSource.fromStorage(source),
            status = FieldPointStatus.fromStorage(status),
            taskId = taskId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            visitedAt = visitedAt,
        )
}
