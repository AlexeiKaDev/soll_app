package com.soll.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import com.soll.domain.soll.SollMeshOutboxItem
import com.soll.domain.soll.SollGateway
import com.soll.presentation.navigation.AppLaunchTargets
import android.util.Base64
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONObject
import retrofit2.HttpException
import timber.log.Timber

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
        val notificationCenter = entryPoint.notificationCenter()
        refreshDeviceBearerIfConfigured(settings, gateway)
        val snapshotSync = syncServerSnapshots(gateway, deviceRepository)
        val meshSummary = if (settings.sollDeviceAccessToken.isNotBlank()) {
            syncMeshOutboxOnce(
                gateway = gateway,
                deviceRepository = deviceRepository,
                notificationCenter = notificationCenter,
                toPeer = settings.sollDeviceId.takeIf { it.isNotBlank() },
            )
        } else {
            MeshOutboxWorkerSummary()
        }
        val commandSummary = if (
            gadgetCommandAuthAvailable(
                deviceAccessToken = settings.sollDeviceAccessToken,
                userAccessToken = settings.sollAccessToken,
            )
        ) {
            syncGadgetCommandOnce(
                gateway = gateway,
                deviceRepository = deviceRepository,
                commandExecutor = commandExecutor,
                workerId = resolveGadgetWorkerId(
                    deviceId = settings.sollDeviceId,
                    remoteClientId = settings.sollRemoteClientId,
                ),
                enabledSnapshots = snapshotSync.enabledSnapshots,
            )
        } else {
            GadgetCommandWorkerSummary()
        }
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = snapshotSync.success,
            snapshotError = snapshotSync.lastError,
            meshSummary = meshSummary,
            commandSummary = commandSummary,
        )
        summary.lastError()?.let { error ->
            Timber.w("Gadget server sync will retry: %s", error)
        }
        return gadgetServerSyncWorkDecision(summary).toWorkerResult()
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
                snapshots.filter { it.enabled }.take(MAX_GADGET_EVENT_SYNC_CANDIDATES).forEach { snapshot ->
                    gateway.getGadgetEvents(snapshot.id, limit = 50).onSuccess { events ->
                        deviceRepository.persistServerEvents(events)
                    }
                }
                ServerSnapshotSyncResult(
                    success = true,
                    enabledSnapshots = snapshots.filter { it.enabled },
                )
            },
            onFailure = { error ->
                ServerSnapshotSyncResult(
                    success = false,
                    lastError = safeGadgetSyncError("snapshot", error),
                )
            },
        )

    private suspend fun syncMeshOutboxOnce(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
        notificationCenter: SollNotificationCenter,
        toPeer: String?,
    ): MeshOutboxWorkerSummary {
        val item = gateway.claimNextMeshOutbox(toPeer = toPeer).getOrElse {
            return MeshOutboxWorkerSummary(failed = 1)
        } ?: return MeshOutboxWorkerSummary()

        val decision = meshOutboxDeliveryDecision(item)
        return when (decision.action) {
            MeshOutboxDeliveryAction.ACK -> {
                deliverMeshOutboxLocally(item, notificationCenter)
                gateway.ackMeshOutbox(
                    outboundId = item.outboundId,
                    claimToken = item.claimToken.takeIf { it.isNotBlank() },
                ).fold(
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
                    claimToken = item.claimToken.takeIf { it.isNotBlank() },
                ).fold(
                    onSuccess = {
                        deviceRepository.logEvent(item.toMeshDeliveryEvent(decision))
                        MeshOutboxWorkerSummary(claimed = 1, failed = 1, unsupported = 1)
                    },
                    onFailure = { MeshOutboxWorkerSummary(claimed = 1, failed = 1) },
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
        val snapshotsById = enabledSnapshots.associateBy { it.id.trim() }
        val candidates = gadgetCommandCandidateIds(enabledSnapshots)
        var transportFailures = 0
        var lastError = ""

        for (gadgetId in candidates) {
            val claimResult = gateway.claimGadgetCommand(
                gadgetId = gadgetId,
                workerId = workerId,
                leaseSeconds = GADGET_COMMAND_LEASE_SECONDS,
            )
            if (claimResult.isFailure) {
                transportFailures += 1
                lastError = safeGadgetSyncError("claim:$gadgetId", claimResult.exceptionOrNull())
                continue
            }
            val command = claimResult.getOrNull() ?: continue

            val validation = validateClaimedGadgetCommand(
                requestedGadgetId = gadgetId,
                command = command,
            )
            if (!validation.valid) {
                return GadgetCommandWorkerSummary(
                    transportFailed = transportFailures,
                    protocolFailed = 1,
                    lastError = validation.error,
                )
            }

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
            ).withPriorTransportFailures(transportFailures, lastError)
        }
        return GadgetCommandWorkerSummary(
            transportFailed = transportFailures,
            lastError = lastError,
        )
    }

    private suspend fun handleClaimedGadgetCommand(
        gateway: SollGateway,
        deviceRepository: DeviceRepository,
        commandExecutor: GadgetReadOnlyCommandExecutor,
        localTarget: GadgetCommandLocalTarget,
        command: GadgetCloudCommand,
        workerId: String,
    ): GadgetCommandWorkerSummary {
        // Gadget ACK/result are lease transport state only. They must not be projected as
        // canonical assistant ActionReceipt/outcome or used to bypass approval policy.
        val decision = gadgetCommandExecutionDecision(
            command = command.command,
            serverRiskLevel = command.riskLevel,
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

        val alreadyStarted = try {
            deviceRepository.hasGadgetCommandExecutionMarker(command.id)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return GadgetCommandWorkerSummary(
                claimed = 1,
                transportFailed = 1,
                lastError = safeGadgetSyncError("execution-marker-read", error),
            )
        }
        if (shouldRefuseGadgetCommandReplay(alreadyStarted)) {
            return postTerminalCommandFailure(
                gateway = gateway,
                deviceRepository = deviceRepository,
                command = command,
                workerId = workerId,
                error = "Local execution was already started; refusing an uncertain replay",
            )
        }

        val ackResult = gateway.ackGadgetCommand(
            gadgetId = command.gadgetId,
            commandId = command.id,
            workerId = workerId,
        )
        if (ackResult.isFailure) {
            return GadgetCommandWorkerSummary(
                claimed = 1,
                transportFailed = 1,
                lastError = safeGadgetSyncError("ack:${command.id}", ackResult.exceptionOrNull()),
            )
        }
        val ackValidation = validateGadgetCommandResponse(
            requestedGadgetId = command.gadgetId,
            requestedCommandId = command.id,
            response = requireNotNull(ackResult.getOrNull()),
            expectedStatuses = setOf("acked"),
        )
        if (!ackValidation.valid) {
            return GadgetCommandWorkerSummary(
                claimed = 1,
                protocolFailed = 1,
                lastError = ackValidation.error,
            )
        }

        try {
            deviceRepository.markGadgetCommandExecutionStarted(
                commandId = command.id,
                gadgetId = command.gadgetId,
                command = command.command,
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return GadgetCommandWorkerSummary(
                claimed = 1,
                transportFailed = 1,
                lastError = safeGadgetSyncError("execution-marker-write", error),
            )
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
                    onSuccess = { response ->
                        val validation = validateGadgetCommandResponse(
                            requestedGadgetId = command.gadgetId,
                            requestedCommandId = command.id,
                            response = response,
                            expectedStatuses = setOf("done"),
                        )
                        if (!validation.valid) {
                            GadgetCommandWorkerSummary(
                                claimed = 1,
                                executed = 1,
                                protocolFailed = 1,
                                lastError = validation.error,
                            )
                        } else {
                            deviceRepository.logEvent(command.toGadgetCommandEvent(success = true))
                            GadgetCommandWorkerSummary(claimed = 1, executed = 1)
                        }
                    },
                    onFailure = { error ->
                        GadgetCommandWorkerSummary(
                            claimed = 1,
                            transportFailed = 1,
                            lastError = safeGadgetSyncError("result:${command.id}", error),
                        )
                    },
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
            onSuccess = { response ->
                val validation = validateGadgetCommandResponse(
                    requestedGadgetId = command.gadgetId,
                    requestedCommandId = command.id,
                    response = response,
                    expectedStatuses = setOf("failed"),
                )
                if (!validation.valid) {
                    GadgetCommandWorkerSummary(
                        claimed = 1,
                        protocolFailed = 1,
                        lastError = validation.error,
                    )
                } else {
                    deviceRepository.logEvent(command.toGadgetCommandEvent(success = false, error = error))
                    GadgetCommandWorkerSummary(claimed = 1, terminalFailed = 1)
                }
            },
            onFailure = { failure ->
                GadgetCommandWorkerSummary(
                    claimed = 1,
                    transportFailed = 1,
                    lastError = safeGadgetSyncError("failure-result:${command.id}", failure),
                )
            },
        )

    companion object {
        const val UNIQUE_WORK_NAME = "gadget_server_sync"
        private const val GADGET_COMMAND_LEASE_SECONDS = 60
        private const val MAX_GADGET_EVENT_SYNC_CANDIDATES = 20
    }
}

internal data class ServerSnapshotSyncResult(
    val success: Boolean,
    val enabledSnapshots: List<GadgetCloudSnapshot> = emptyList(),
    val lastError: String = "",
)

internal data class GadgetServerSyncSummary(
    val snapshotsSynced: Boolean,
    val snapshotError: String = "",
    val meshSummary: MeshOutboxWorkerSummary = MeshOutboxWorkerSummary(),
    val commandSummary: GadgetCommandWorkerSummary = GadgetCommandWorkerSummary(),
) {
    fun lastError(): String? = commandSummary.lastError.ifBlank { snapshotError }.ifBlank { null }
}

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
    val protocolFailed: Int = 0,
    val lastError: String = "",
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

internal data class LegacyMeshCommand(
    val command: String,
    val body: String = "",
    val error: String? = null,
    val isSupported: Boolean = true,
)

internal data class GadgetCommandExecutionDecision(
    val action: GadgetCommandExecutionAction,
    val error: String? = null,
)

internal data class GadgetCommandLocalTarget(
    val device: KnownDevice?,
    val error: String? = null,
)

internal data class GadgetClaimValidation(
    val valid: Boolean,
    val error: String = "",
)

internal fun gadgetCommandAuthAvailable(
    deviceAccessToken: String,
    userAccessToken: String,
): Boolean = deviceAccessToken.isNotBlank() || userAccessToken.isNotBlank()

internal fun resolveGadgetWorkerId(
    deviceId: String,
    remoteClientId: String,
): String = deviceId.trim().ifBlank {
    normalizeSollRemoteClientId(remoteClientId).ifBlank { DEFAULT_SOLL_REMOTE_CLIENT_ID }
}

internal fun gadgetCommandCandidateIds(
    enabledSnapshots: List<GadgetCloudSnapshot>,
    maxCandidates: Int = 20,
): List<String> = enabledSnapshots
    .asSequence()
    .filter { it.enabled }
    .map { it.id.trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .take(maxCandidates.coerceAtLeast(0))
    .toList()

internal fun shouldRefuseGadgetCommandReplay(hasExecutionMarker: Boolean): Boolean =
    hasExecutionMarker

internal fun validateClaimedGadgetCommand(
    requestedGadgetId: String,
    command: GadgetCloudCommand,
): GadgetClaimValidation = validateGadgetCommandResponse(
    requestedGadgetId = requestedGadgetId,
    requestedCommandId = null,
    response = command,
    expectedStatuses = setOf("claimed"),
)

internal fun validateGadgetCommandResponse(
    requestedGadgetId: String,
    requestedCommandId: String?,
    response: GadgetCloudCommand,
    expectedStatuses: Set<String>,
): GadgetClaimValidation = when {
    response.id.isBlank() -> GadgetClaimValidation(false, "Gadget command response has no id")
    response.command.isBlank() -> GadgetClaimValidation(false, "Gadget command response has no command")
    response.gadgetId != requestedGadgetId -> GadgetClaimValidation(false, "Gadget command target mismatch")
    requestedCommandId != null && response.id != requestedCommandId ->
        GadgetClaimValidation(false, "Gadget command id mismatch")
    response.status.trim() !in expectedStatuses ->
        GadgetClaimValidation(false, "Gadget command response has invalid status")
    else -> GadgetClaimValidation(true)
}

private fun GadgetCommandWorkerSummary.withPriorTransportFailures(
    failures: Int,
    priorError: String,
): GadgetCommandWorkerSummary = copy(
    transportFailed = transportFailed + failures,
    lastError = lastError.ifBlank { priorError },
)

private fun safeGadgetSyncError(operation: String, error: Throwable?): String {
    val reason = when (error) {
        is HttpException -> "HTTP ${error.code()}"
        null -> "unknown transport failure"
        else -> error::class.java.simpleName.ifBlank { "transport failure" }
    }
    return "$operation failed: $reason"
}

internal fun gadgetServerSyncWorkDecision(summary: GadgetServerSyncSummary): SyncWorkDecision =
    if (
        !summary.snapshotsSynced ||
        summary.meshSummary.failed > summary.meshSummary.unsupported ||
        summary.commandSummary.transportFailed > 0 ||
        summary.commandSummary.protocolFailed > 0
    ) {
        SyncWorkDecision.RETRY
    } else {
        SyncWorkDecision.SUCCESS
    }

internal fun meshOutboxDeliveryDecision(item: SollMeshOutboxItem): MeshOutboxDeliveryDecision {
    val payloadText = meshOutboxPayloadText(item)
    val type = meshPayloadType(payloadText)
    if (type != null) {
        return when (type.lowercase()) {
            "chat_message",
            "chat_action",
            "chat_notification" -> MeshOutboxDeliveryDecision(MeshOutboxDeliveryAction.ACK)
            "status",
            "brief",
            "note",
            "task" -> MeshOutboxDeliveryDecision(
                action = MeshOutboxDeliveryAction.FAIL,
                error = "No registered local consumer for mesh payload type: $type",
            )
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

    val legacyCommand = parseLegacyMeshCommand(payloadText)
    return if (legacyCommand != null) {
        MeshOutboxDeliveryDecision(
            action = MeshOutboxDeliveryAction.FAIL,
            error = legacyCommand.error
                ?: "No registered local consumer for legacy mesh payload: ${legacyCommand.command}",
        )
    } else {
        MeshOutboxDeliveryDecision(
            action = MeshOutboxDeliveryAction.FAIL,
            error = "Unsupported mesh payload: expected JSON object with type or legacy /command payload",
        )
    }
}

private fun parseLegacyMeshCommand(rawText: String): LegacyMeshCommand? {
    val text = rawText.trim()
    if (!text.startsWith("/")) return null
    val command = runCatching {
        val commandPart = text.substringBefore(" ")
        val body = text.substringAfter(" ", "").trim()
        when (commandPart.lowercase()) {
            "/note", "/task" -> {
                val command = commandPart.removePrefix("/").lowercase()
                LegacyMeshCommand(
                    command = command,
                    body = body,
                    isSupported = body.isNotBlank(),
                    error = if (body.isBlank()) "${command} payload is empty" else null,
                )
            }
            "/status", "/brief" -> LegacyMeshCommand(
                command = commandPart.removePrefix("/").lowercase(),
                body = body,
                isSupported = true,
            )
            "/command" -> {
                if (body.isBlank()) {
                    LegacyMeshCommand(
                        command = "command",
                        isSupported = false,
                        error = "Empty /command payload",
                    )
                } else {
                    val nestedCommand = body.substringBefore(" ")
                    val nestedBody = body.substringAfter(" ", "").trim()
                    when (nestedCommand.lowercase().removePrefix("/")) {
                        "note", "task" -> LegacyMeshCommand(
                            command = nestedCommand.lowercase().removePrefix("/"),
                            body = nestedBody,
                            isSupported = nestedBody.isNotBlank(),
                            error = if (nestedBody.isBlank()) "${nestedCommand.removePrefix("/")} payload is empty" else null,
                        )
                        "status", "brief" -> LegacyMeshCommand(
                            command = nestedCommand.lowercase().removePrefix("/"),
                            body = nestedBody,
                            isSupported = true,
                        )
                        else -> LegacyMeshCommand(
                            command = "command",
                            isSupported = false,
                            error = "Unsupported nested command: $nestedCommand",
                        )
                    }
                }
            }
            else -> LegacyMeshCommand(
                command = "unsupported",
                isSupported = false,
                error = "Unsupported command: $text",
            )
        }
    }.getOrElse {
        null
    }
    return command
}

internal suspend fun deliverMeshOutboxLocally(
    item: SollMeshOutboxItem,
    notificationCenter: SollNotificationCenter,
) {
    val payloadText = meshOutboxPayloadText(item)
    val payloadType = meshPayloadType(payloadText)?.lowercase()
    if (payloadType !in setOf("chat_message", "chat_action", "chat_notification")) return
    val payload = runCatching { JSONObject(payloadText.trim()) }.getOrNull() ?: return
    val type = payload.optString("type").trim()
    val title = payload.optString("title").trim().ifBlank { "Soll" }
    val message = payload.optString("message").trim().ifBlank { payloadText.take(180) }
    val priority = when (payload.optJSONObject("metadata")?.optString("priority")?.lowercase()) {
        "high", "alert" -> SollNotificationPriority.HIGH
        "low" -> SollNotificationPriority.LOW
        else -> SollNotificationPriority.DEFAULT
    }
    notificationCenter.post(
        SollNotificationRequest(
            channel = SollNotificationChannel.CHAT,
            type = type,
            source = "soll_server",
            title = title,
            message = message,
            payloadJson = payload.toString(),
            priority = priority,
            showSystem = true,
            onlyAlertOnce = true,
            systemNotificationId = item.outboundId.hashCode() and Int.MAX_VALUE,
            launchSection = AppLaunchTargets.SECTION_CHAT,
        )
    )
}

private fun meshOutboxPayloadText(item: SollMeshOutboxItem): String {
    val fallback = item.text.trim()
    val encoded = item.securePayload.trim()
    if (encoded.isBlank()) return fallback
    return runCatching {
        val decoded = Base64.decode(encoded, Base64.DEFAULT)
        val decodedText = String(decoded, Charsets.UTF_8).trim()
        decodedText.ifBlank { fallback }
    }.getOrElse { fallback }
}

private fun SollMeshOutboxItem.toMeshDeliveryEvent(decision: MeshOutboxDeliveryDecision): DeviceEvent {
    val payloadText = meshOutboxPayloadText(this)
    val payloadType = meshPayloadType(payloadText)?.lowercase() ?: parseLegacyMeshCommand(payloadText)?.command ?: "unknown"
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
        .put("text", payloadText)
        .put("error", decision.error)
        .toString()
    return DeviceEvent(
        deviceId = toPeer.ifBlank { "soll-mesh" },
        type = eventType,
        summary = summary,
        payloadJson = payload,
    )
}

internal fun gadgetCommandExecutionDecision(
    command: String,
    serverRiskLevel: String = "read_only",
    hasLocalDevice: Boolean,
    missingLocalDeviceError: String? = null,
): GadgetCommandExecutionDecision {
    if (!hasLocalDevice) {
        return GadgetCommandExecutionDecision(
            action = GadgetCommandExecutionAction.FAIL,
            error = missingLocalDeviceError ?: "Local gadget is not registered on this Android device",
        )
    }
    if (serverRiskLevel.trim() != "read_only") {
        return GadgetCommandExecutionDecision(
            action = GadgetCommandExecutionAction.FAIL,
            error = "Server gadget command risk is not read_only",
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

    fun runNow(context: Context, settingsRepository: SettingsRepository) {
        if (settingsRepository.sollServerUrl.isBlank()) return
        val networkType = if (settingsRepository.sollWifiOnlyUpload) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val request = OneTimeWorkRequestBuilder<GadgetServerSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    internal const val IMMEDIATE_WORK_NAME = "gadget_server_sync_now"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GadgetServerSyncWorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun sollGateway(): SollGateway
    fun deviceRepository(): DeviceRepository
    fun gadgetReadOnlyCommandExecutor(): GadgetReadOnlyCommandExecutor
    fun notificationCenter(): SollNotificationCenter
}
