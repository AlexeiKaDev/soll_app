package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.soll.data.local.entity.DeviceEventEntity
import com.soll.data.local.entity.DeviceProfileEntity
import com.soll.data.local.entity.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device_profiles ORDER BY name ASC")
    fun observeProfiles(): Flow<List<DeviceProfileEntity>>

    @Query("SELECT * FROM device_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfile(id: String): DeviceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: DeviceProfileEntity)

    @Query("SELECT * FROM known_devices ORDER BY updated_at DESC")
    fun observeKnownDevices(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices ORDER BY updated_at DESC")
    suspend fun getKnownDevices(): List<KnownDeviceEntity>

    @Query("SELECT * FROM known_devices WHERE id = :id LIMIT 1")
    suspend fun getKnownDevice(id: String): KnownDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKnownDevice(device: KnownDeviceEntity)

    @Query(
        """
        UPDATE known_devices
        SET last_status = :status,
            last_seen_at = :lastSeenAt,
            updated_at = :updatedAt
        WHERE id = :deviceId
        """
    )
    suspend fun updateStatus(
        deviceId: String,
        status: String,
        lastSeenAt: Long?,
        updatedAt: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DeviceEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<DeviceEventEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM device_events WHERE id = :eventId)")
    suspend fun hasEvent(eventId: String): Boolean

    @Query("DELETE FROM device_events WHERE type = 'server_snapshot'")
    suspend fun deleteServerSnapshotEvents()

    @Transaction
    suspend fun replaceServerSnapshotEvents(events: List<DeviceEventEntity>) {
        deleteServerSnapshotEvents()
        if (events.isNotEmpty()) {
            insertEvents(events)
        }
    }

    @Query("SELECT * FROM device_events WHERE device_id = :deviceId ORDER BY created_at DESC LIMIT :limit")
    fun observeEvents(deviceId: String, limit: Int = 50): Flow<List<DeviceEventEntity>>
}
