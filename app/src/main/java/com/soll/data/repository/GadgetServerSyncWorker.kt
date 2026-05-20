package com.soll.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soll.data.device.GadgetReadOnlyCommandExecutor
import com.soll.data.device.GadgetCommandRisk
import com.soll.data.device.gadgetCommandPolicy
import com.soll.domain.device.DeviceEndpoint
import com.soll.domain.device.DeviceEvent
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.KnownDevice
import com.soll.domain.soll.SollMeshOutboxItem
import com.soll.domain.soll.SollGateway
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class GadgetServerSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            GadgetServerSyncWorkerEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return Result.success()
        }

        val gateway = entryPoint.sollGateway()
        val deviceRepository = entryPoint.deviceRepository()
        val commandExecutor = entryPoint.gadgetReadOnlyCommandExecutor()
        refreshDeviceBearerIfConfigured(settings, gateway)
        val snapshotSync = syncServerSnapshots(gateway, deviceRepository)
        val meshSummary = if (settings.sollDeviceAccessToken.isNotBlank()) {
            syncMeshOutboxOnce(
                gateway = gateway,
                deviceRepository = deviceRepository,
                toPeer = settings.sollDeviceId.takeIf { it.isNotBlank() },
            )
        } else {
            MeshOutboxWorkerSummary()
        }
        val commandSummary = if (settings.sollDeviceAccessToken.isNotBlank()) {
            syncGadgetCommandOnce(
                gateway = gateway,
                deviceRepository = deviceRepository,
                commandExecutor = commandExecutor,
                workerId = settings.sollDeviceId.ifBlank { "soll-app-android" },
                enabledSnapshots = snapshotSync.enabledSnapshots,
            )
        } else {
            GadgetCommandWorkerSummary()
        }
        return gadgetServerSyncWorkDecision(
            GadgetServerSyncSummary(
                snapshotsSynced = snapshotSync.success,
                meshSummary = meshSummary,
                commandSummary = commandSummary,
            )
        ).toWorkerResult()
    }

    private suspend fun refreshDeviceBearerIfConfigured(
        settings: SettingsRepository,
        gateway: SollGateway,
    ) {
        if (settings.sollDeviceAccessToken.isBlank()) return
        gateway.refreshDeviceToken().onSuccess { token ->
            settings.sollDeviceAccessToken = token.accessToken
            settings.sollDeviceTokenExpiresAt = token.expiresAt
        }
    }

    private suspend fun syncServerSnapshots(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
    ): ServerSnapshotSyncResult =
        gateway.getGadgetSnapshots().fold(
            onSuccess = { snapshots ->
                deviceRepository.persistServerSnapshots(snapshots)
                snapshots.filter { it.enabled }.forEach { snapshot ->
                    gateway.getGadgetEvents(snapshot.id, limit = 50).onSuccess { events ->
                        deviceRepository.persistServerEvents(events)
                    }
                }
                ServerSnapshotSyncResult(
                    success = true,
                    enabledSnapshots = snapshots.filter { it.enabled },
                )
            },
            onFailure = { ServerSnapshotSyncResult(success = false) },
        )

    private suspend fun syncMeshOutboxOnce(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
        toPeer: String?,
    ): MeshOutboxWorkerSummary {
        val item = gateway.claimNextMeshOutbox(toPeer = toPeer).getOrElse {
            return MeshOutboxWorkerSummary(failed = 1)
        } ?: return MeshOutboxWorkerSummary()

        val decision = meshOutboxDeliveryDecision(item)
        return when (decision.action) {
            MeshOutboxDeliveryAction.ACK -> {
                gateway.ackMeshOutbox(item.outboundId).fold(
                    onSuccess = {
                        deviceRepository.logEvent(item.toMeshDeliveryEvent(decision))
                        MeshOutboxWorkerSummary(claimed = 1, acked = 1)
                    },
                    onFailure = { MeshOutboxWorkerSummary(claimed = 1, failed = 1) },
                )
            }
            MeshOutboxDeliveryAction.FAIL -> {
                gateway.markMeshOutboxAttempt(
                    outboundId = item.outboundId,
                    success = false,
                    error = decision.error ?: "Unsupported mesh payload",
                ).fold(
                    onSuccess = {
                        deviceRepository.logEvent(item.toMeshDeliveryEvent(decision))
                        MeshOutboxWorkerSummary(claimed = 1, failed = 1, unsupported = 1)
                    },
                    onFailure = { MeshOutboxWorkerSummary(claimed = 1, failed = 1, unsupported = 1) },
                )
            }
        }
    }

    private suspend fun syncGadgetCommandOnce(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
        commandExecutor: GadgetReadOnlyCommandExecutor,
        workerId: String,
        enabledSnapshots: List<GadgetCloudSnapshot>,
    ): GadgetCommandWorkerSummary {
        val localDevices = deviceRepository.getKnownDevices()
        val snapshotsById = enabledSnapshots.associateBy { it.id }
        val candidates = (enabledSnapshots.map { it.id }.ifEmpty { localDevices.map { it.id } })
            .filter { it.isNotBlank() }
            .distinct()

        for (gadgetId in candidates) {
            val claimResult = gateway.claimGadgetCommand(
                gadgetId = gadgetId,
                workerId = workerId,
                leaseSeconds = GADGET_COMMAND_LEASE_SECONDS,
            )
            if (claimResult.isFailure) {
                continue
            }
            val command = claimResult.getOrNull() ?: continue

            return handleClaimedGadgetCommand(
                gateway = gateway,
                deviceRepository = deviceRepository,
                commandExecutor = commandExecutor,
                localTarget = resolveLocalDeviceForGadgetCommand(
                    command = command,
                    snapshotsById = snapshotsById,
                    localDevices = localDevices,
                ),
                command = command,
                workerId = workerId,
            )
        }
        return GadgetCommandWorkerSummary()
    }

    private suspend fun handleClaimedGadgetCommand(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
        commandExecutor: GadgetReadOnlyCommandExecutor,
        localTarget: GadgetCommandLocalTarget,
        command: GadgetCloudCommand,
        workerId: String,
    ): GadgetCommandWorkerSummary {
        val decision = gadgetCommandExecutionDecision(
            command = command.command,
            hasLocalDevice = localTarget.device != null,
            missingLocalDeviceError = localTarget.error,
        )
        if (decision.action == GadgetCommandExecutionAction.FAIL) {
            return postTerminalCommandFailure(
                gateway = gateway,
                deviceRepository = deviceRepository,
                command = command,
                workerId = workerId,
                error = decision.error ?: "Command cannot be executed by Android worker",
            )
        }

        val acked = gateway.ackGadgetCommand(
            gadgetId = command.gadgetId,
            commandId = command.id,
            workerId = workerId,
        ).isSuccess
        if (!acked) {
            return GadgetCommandWorkerSummary(claimed = 1, transportFailed = 1)
        }

        val result = commandExecutor.execute(
            device = requireNotNull(localTarget.device),
            command = command.command,
            params = command.params,
        )
        return result.fold(
            onSuccess = { execution ->
                gateway.postGadgetCommandResult(
                    gadgetId = command.gadgetId,
                    commandId = command.id,
                    success = true,
                    payload = execution.payload,
                    workerId = workerId,
                ).fold(
                    onSuccess = {
                        deviceRepository.logEvent(command.toGadgetCommandEvent(success = true))
                        GadgetCommandWorkerSummary(claimed = 1, executed = 1)
                    },
                    onFailure = { GadgetCommandWorkerSummary(claimed = 1, transportFailed = 1) },
                )
            },
            onFailure = { error ->
                postTerminalCommandFailure(
                    gateway = gateway,
                    deviceRepository = deviceRepository,
                    command = command,
                    workerId = workerId,
                    error = error.message ?: "Read-only command execution failed",
                )
            },
        )
    }

    private suspend fun postTerminalCommandFailure(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
        command: GadgetCloudCommand,
        workerId: String,
        error: String,
    ): GadgetCommandWorkerSummary =
        gateway.postGadgetCommandResult(
            gadgetId = command.gadgetId,
            commandId = command.id,
            success = false,
            payload = emptyMap(),
            error = error,
            workerId = workerId,
        ).fold(
            onSuccess = {
                deviceRepository.logEvent(command.toGadgetCommandEvent(success = false, error = error))
                GadgetCommandWorkerSummary(claimed = 1, terminalFailed = 1)
            },
            onFailure = { GadgetCommandWorkerSummary(claimed = 1, transportFailed = 1) },
        )

    companion object {
        const val UNIQUE_WORK_NAME = "gadget_server_sync"
        private const val GADGET_COMMAND_LEASE_SECONDS = 60
    }
}

internal data class ServerSnapshotSyncResult(
    val success: Boolean,
    val enabledSnapshots: List<GadgetCloudSnapshot> = emptyList(),
)

internal data class GadgetServerSyncSummary(
    val snapshotsSynced: Boolean,
    val meshSummary: MeshOutboxWorkerSummary = MeshOutboxWorkerSummary(),
    val commandSummary: GadgetCommandWorkerSummary = GadgetCommandWorkerSummary(),
)

internal data class MeshOutboxWorkerSummary(
    val claimed: Int = 0,
    val acked: Int = 0,
    val failed: Int = 0,
    val unsupported: Int = 0,
)

internal data class GadgetCommandWorkerSummary(
    val claimed: Int = 0,
    val executed: Int = 0,
    val terminalFailed: Int = 0,
    val transportFailed: Int = 0,
)

internal enum class MeshOutboxDeliveryAction {
    ACK,
    FAIL,
}

internal enum class GadgetCommandExecutionAction {
    EXECUTE,
    FAIL,
}

internal data class MeshOutboxDeliveryDecision(
    val action: MeshOutboxDeliveryAction,
    val error: String? = null,
)

internal data class GadgetCommandExecutionDecision(
    val action: GadgetCommandExecutionAction,
    val error: String? = null,
)

internal data class GadgetCommandLocalTarget(
    val device: KnownDevice?,
    val error: String? = null,
)

internal fun gadgetServerSyncWorkDecision(summary: GadgetServerSyncSummary): SyncWorkDecision =
    if (
        !summary.snapshotsSynced ||
        summary.meshSummary.failed > summary.meshSummary.unsupported ||
        summary.commandSummary.transportFailed > 0
    ) {
        SyncWorkDecision.RETRY
    } else {
        SyncWorkDecision.SUCCESS
    }

internal fun meshOutboxDeliveryDecision(item: SollMeshOutboxItem): MeshOutboxDeliveryDecision {
    val type = meshPayloadType(item.text)
        ?: return MeshOutboxDeliveryDecision(
            action = MeshOutboxDeliveryAction.FAIL,
            error = "Unsupported mesh payload: expected JSON object with type",
        )

    return when (type.lowercase()) {
        "status",
        "brief",
        "note",
        "task" -> MeshOutboxDeliveryDecision(MeshOutboxDeliveryAction.ACK)
        "command" -> MeshOutboxDeliveryDecision(
            action = MeshOutboxDeliveryAction.FAIL,
            error = "Command mesh delivery is not enabled in Android worker yet",
        )
        else -> MeshOutboxDeliveryDecision(
            action = MeshOutboxDeliveryAction.FAIL,
            error = "Unsupported mesh payload type: $type",
        )
    }
}

internal fun gadgetCommandExecutionDecision(
    command: String,
    hasLocalDevice: Boolean,
    missingLocalDeviceError: String? = null,
): GadgetCommandExecutionDecision {
    if (!hasLocalDevice) {
        return GadgetCommandExecutionDecision(
            action = GadgetCommandExecutionAction.FAIL,
            error = missingLocalDeviceError ?: "Local gadget is not registered on this Android device",
        )
    }
    val policy = gadgetCommandPolicy(command)
    if (policy.risk != GadgetCommandRisk.READ_ONLY) {
        return GadgetCommandExecutionDecision(action = GadgetCommandExecutionAction.FAIL, error = policy.reason)
    }
    return GadgetCommandExecutionDecision(GadgetCommandExecutionAction.EXECUTE)
}

internal fun resolveLocalDeviceForGadgetCommand(
    command: GadgetCloudCommand,
    snapshotsById: Map<String, GadgetCloudSnapshot>,
    localDevices: Collection<KnownDevice>,
): GadgetCommandLocalTarget {
    localDevices.firstOrNull { it.id == command.gadgetId }?.let {
        return GadgetCommandLocalTarget(device = it)
    }

    val snapshot = snapshotsById[command.gadgetId]
        ?: return GadgetCommandLocalTarget(
            device = null,
            error = "Local gadget is not registered on this Android device: ${command.gadgetId}",
        )
    val endpointHints = snapshot.endpointHints()
    if (endpointHints.isEmpty()) {
        return GadgetCommandLocalTarget(
            device = null,
            error = "Server gadget ${command.gadgetId} has no endpoint hint for local matching",
        )
    }

    val candidates = localDevices
        .filter { device -> endpointHints.any { hint -> device.matchesServerSnapshot(snapshot, hint) } }
        .distinctBy { it.id }
    return when (candidates.size) {
        1 -> GadgetCommandLocalTarget(device = candidates.first())
        0 -> GadgetCommandLocalTarget(
            device = null,
            error = "No local gadget matches server gadget ${command.gadgetId}",
        )
        else -> GadgetCommandLocalTarget(
            device = null,
            error = "Ambiguous local gadget mapping for server gadget ${command.gadgetId}",
        )
    }
}

private fun meshPayloadType(text: String): String? =
    runCatching {
        JSONObject(text.trim()).optString("type").trim().takeIf { it.isNotBlank() }
    }.getOrNull()

private data class ServerGadgetEndpointHint(
    val host: String,
    val port: Int? = null,
    val path: String? = null,
)

private fun GadgetCloudSnapshot.endpointHints(): List<ServerGadgetEndpointHint> {
    val urlHint = heartbeatPayload.firstString("websocketUrl", "webSocketUrl", "wsUrl")
        ?.let { rawUrl ->
            val endpoint = DeviceEndpoint.normalize(rawUrl, port = 81, path = "ws")
            ServerGadgetEndpointHint(host = endpoint.host, port = endpoint.port, path = endpoint.path)
        }
    val directHosts = listOfNotNull(
        localIp.asNonBlankString(),
        heartbeatPayload.firstString("local_ip", "ipAddress", "ip", "host"),
    )
    val directHints = directHosts.map { host ->
        val rawHasEndpoint = host.contains("://") || host.contains("/") || host.count { it == ':' } == 1
        val port = heartbeatPayload.firstInt("wsPort", "ws_port", "websocketPort", "websocket_port", "port")
        val path = heartbeatPayload.firstString("path", "websocketPath", "websocket_path", "wsPath", "ws_path")
        val endpoint = DeviceEndpoint.normalize(host, port ?: 81, path ?: "ws")
        ServerGadgetEndpointHint(
            host = endpoint.host,
            port = port?.let { endpoint.port } ?: endpoint.port.takeIf { rawHasEndpoint },
            path = path?.let { endpoint.path } ?: endpoint.path.takeIf { rawHasEndpoint },
        )
    }
    return (listOfNotNull(urlHint) + directHints)
        .filter { it.host.isNotBlank() }
        .distinctBy { "${it.host}|${it.port}|${it.path}" }
}

private fun KnownDevice.matchesServerSnapshot(
    snapshot: GadgetCloudSnapshot,
    hint: ServerGadgetEndpointHint,
): Boolean {
    if (snapshot.profileId.isNotBlank() && profileId != snapshot.profileId) return false
    val endpoint = endpoint()
    if (endpoint.host != hint.host) return false
    if (hint.port != null && endpoint.port != hint.port) return false
    if (hint.path != null && endpoint.path.trim('/') != hint.path.trim('/')) return false
    return true
}

private fun Map<String, Any?>.firstString(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key].asNonBlankString()
        if (value != null) return value
    }
    return null
}

private fun Map<String, Any?>.firstInt(vararg keys: String): Int? {
    for (key in keys) {
        val value = when (val raw = this[key]) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }?.takeIf { it in 1..65535 }
        if (value != null) return value
    }
    return null
}

private fun Any?.asNonBlankString(): String? =
    when (this) {
        null -> null
        is String -> trim().takeIf { it.isNotBlank() }
        else -> toString().trim().takeIf { it.isNotBlank() }
    }

private fun SollMeshOutboxItem.toMeshDeliveryEvent(decision: MeshOutboxDeliveryDecision): DeviceEvent {
    val payloadType = meshPayloadType(text) ?: "unknown"
    val delivered = decision.action == MeshOutboxDeliveryAction.ACK
    val eventType = if (delivered) "mesh_outbox_delivered" else "mesh_outbox_rejected"
    val summary = if (delivered) {
        "Mesh outbox ${outboundId.take(8)} доставлен как $payloadType"
    } else {
        "Mesh outbox ${outboundId.take(8)} отклонен: ${decision.error ?: "unsupported"}"
    }
    val payload = JSONObject()
        .put("outbound_id", outboundId)
        .put("to_peer", toPeer)
        .put("payload_type", payloadType)
        .put("text", text)
        .put("error", decision.error)
        .toString()
    return DeviceEvent(
        deviceId = toPeer.ifBlank { "soll-mesh" },
        type = eventType,
        summary = summary,
        payloadJson = payload,
    )
}

private fun GadgetCloudCommand.toGadgetCommandEvent(success: Boolean, error: String = ""): DeviceEvent {
    val payload = JSONObject()
        .put("command_id", id)
        .put("gadget_id", gadgetId)
        .put("command", command)
        .put("params", JSONObject(params))
        .put("success", success)
        .put("error", error)
        .toString()
    return DeviceEvent(
        deviceId = gadgetId,
        type = if (success) "server_command_executed" else "server_command_failed",
        summary = if (success) {
            "Server command $command выполнена через Android worker"
        } else {
            "Server command $command отклонена: $error"
        },
        payloadJson = payload,
    )
}

object GadgetServerSyncScheduler {
    fun schedule(context: Context, settingsRepository: SettingsRepository) {
        if (settingsRepository.sollServerUrl.isBlank()) {
            WorkManager.getInstance(context).cancelUniqueWork(GadgetServerSyncWorker.UNIQUE_WORK_NAME)
            return
        }
        val intervalMinutes = settingsRepository.sollSyncIntervalMinutes.coerceAtLeast(15)
        val networkType = if (settingsRepository.sollWifiOnlyUpload) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val request = PeriodicWorkRequestBuilder<GadgetServerSyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GadgetServerSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GadgetServerSyncWorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun sollGateway(): SollGateway
    fun deviceRepository(): DeviceRepository
    fun gadgetReadOnlyCommandExecutor(): GadgetReadOnlyCommandExecutor
}
