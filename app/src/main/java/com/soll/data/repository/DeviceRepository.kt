package com.soll.data.repository

import com.soll.data.local.dao.DeviceDao
import com.soll.data.local.entity.DeviceEventEntity
import com.soll.data.local.entity.DeviceProfileEntity
import com.soll.data.local.entity.KnownDeviceEntity
import com.soll.domain.device.BuiltInDeviceProfiles
import com.soll.domain.device.DeviceConnectionConfig
import com.soll.domain.device.DeviceConnectionStatus
import com.soll.domain.device.DeviceEvent
import com.soll.domain.device.DeviceProfile
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.KnownDevice
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
) {
    fun observeProfiles(): Flow<List<DeviceProfile>> =
        deviceDao.observeProfiles().map { profiles -> profiles.map { it.toDomain() } }

    fun observeKnownDevices(): Flow<List<KnownDevice>> =
        deviceDao.observeKnownDevices().map { devices -> devices.map { it.toDomain() } }

    suspend fun getKnownDevices(): List<KnownDevice> =
        deviceDao.getKnownDevices().map { it.toDomain() }

    suspend fun getKnownDevice(deviceId: String): KnownDevice? =
        deviceDao.getKnownDevice(deviceId.trim())?.toDomain()

    fun observeEvents(deviceId: String): Flow<List<DeviceEvent>> =
        deviceDao.observeEvents(deviceId).map { events -> events.map { it.toDomain() } }

    suspend fun ensureBuiltInProfiles() {
        BuiltInDeviceProfiles.all.forEach { profile ->
            deviceDao.upsertProfile(DeviceProfileEntity.fromDomain(profile))
        }
    }

    suspend fun upsertManualDevice(
        config: DeviceConnectionConfig,
        status: DeviceConnectionStatus,
        nameOverride: String? = null,
    ): KnownDevice {
        val now = System.currentTimeMillis()
        val endpoint = config.endpoint()
        val existing = deviceDao.getKnownDevice(config.deviceId)?.toDomain()
        val device = KnownDevice(
            id = config.deviceId,
            profileId = config.profile.id,
            name = nameOverride?.takeIf { it.isNotBlank() } ?: existing?.name ?: "${config.profile.name} ${endpoint.host}",
            host = endpoint.storageHost(),
            port = endpoint.port,
            path = endpoint.path,
            transport = config.profile.transport,
            authMode = config.profile.authMode,
            lastStatus = status.name,
            lastSeenAt = if (status == DeviceConnectionStatus.AUTHENTICATED || status == DeviceConnectionStatus.CONNECTED) {
                now
            } else {
                existing?.lastSeenAt
            },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        deviceDao.upsertKnownDevice(KnownDeviceEntity.fromDomain(device))
        return device
    }

    suspend fun updateDeviceStatus(deviceId: String, status: DeviceConnectionStatus) {
        val now = System.currentTimeMillis()
        val existing = deviceDao.getKnownDevice(deviceId)?.toDomain()
        deviceDao.updateStatus(
            deviceId = deviceId,
            status = status.name,
            lastSeenAt = if (status == DeviceConnectionStatus.AUTHENTICATED || status == DeviceConnectionStatus.CONNECTED) {
                now
            } else {
                existing?.lastSeenAt
            },
            updatedAt = now,
        )
    }

    suspend fun logEvent(event: DeviceEvent) {
        deviceDao.insertEvent(DeviceEventEntity.fromDomain(event))
    }

    suspend fun persistServerSnapshots(snapshots: List<GadgetCloudSnapshot>) {
        if (snapshots.isEmpty()) return
        val now = System.currentTimeMillis()
        deviceDao.insertEvents(
            snapshots.map { snapshot ->
                DeviceEventEntity.fromDomain(
                    DeviceEvent(
                        id = "server_snapshot:${snapshot.id}",
                        deviceId = snapshot.id,
                        type = "server_snapshot",
                        summary = snapshot.serverSummary(),
                        payloadJson = snapshot.toPayloadJson(),
                        createdAt = now,
                    )
                )
            }
        )
    }

    suspend fun persistServerEvents(events: List<GadgetCloudEvent>) {
        if (events.isEmpty()) return
        deviceDao.insertEvents(
            events.map { event ->
                DeviceEventEntity.fromDomain(
                    DeviceEvent(
                        id = "server_event:${event.id}",
                        deviceId = event.gadgetId,
                        type = "server_${event.type.ifBlank { "event" }}",
                        summary = event.summary.ifBlank { "Серверное событие ${event.type}" },
                        payloadJson = JSONObject(event.payload).toString(),
                        createdAt = event.createdAt.toEpochMillisOrNow(),
                    )
                )
            }
        )
    }
}

private fun GadgetCloudSnapshot.serverSummary(): String =
    when {
        !enabled -> "Серверный гаджет $name отключен"
        stale -> "Серверный снимок $name устарел"
        latestEventSummary.isNullOrBlank() -> "Серверный снимок $name обновлен"
        else -> latestEventSummary
    }

private fun GadgetCloudSnapshot.toPayloadJson(): String =
    JSONObject()
        .put("id", id)
        .put("name", name)
        .put("profileId", profileId)
        .put("enabled", enabled)
        .put("firmwareVersion", firmwareVersion)
        .put("localIp", localIp)
        .put("uptimeMs", uptimeMs)
        .put("capabilities", capabilities)
        .put("heartbeatPayload", JSONObject(heartbeatPayload))
        .put("lastHeartbeatAt", lastHeartbeatAt)
        .put("lastTelemetryAt", lastTelemetryAt)
        .put("latestTelemetry", JSONObject(latestTelemetry))
        .put("latestEventType", latestEventType)
        .put("latestEventSummary", latestEventSummary)
        .put("stale", stale)
        .put("updatedAt", updatedAt)
        .toString()

private fun String.toEpochMillisOrNow(): Long =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
