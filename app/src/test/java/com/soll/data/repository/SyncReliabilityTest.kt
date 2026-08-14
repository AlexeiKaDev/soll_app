package com.soll.data.repository

import androidx.work.ExistingWorkPolicy
import com.soll.domain.device.DeviceAuthMode
import com.soll.domain.device.DeviceTransport
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.KnownDevice
import com.soll.domain.soll.SollMeshOutboxItem
import com.soll.data.local.entity.SyncQueueEntity
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReliabilityTest {
    @Test
    fun `new durable command replaces stale unique work backoff`() {
        assertEquals(ExistingWorkPolicy.REPLACE, syncQueueWorkPolicy(replaceExisting = true))
        assertEquals(ExistingWorkPolicy.KEEP, syncQueueWorkPolicy(replaceExisting = false))
    }

    @Test
    fun `background server sync interval stays inside WorkManager safe bounds`() {
        assertEquals(TimeUnit.MINUTES.toMillis(15), serverSyncDelayMs(1))
        assertEquals(TimeUnit.MINUTES.toMillis(15), serverSyncDelayMs(15))
        assertEquals(TimeUnit.MINUTES.toMillis(60), serverSyncDelayMs(90))
    }

    @Test
    fun `periodic and immediate server sync use separate unique work names`() {
        assertTrue(SollServerSyncScheduler.PERIODIC_WORK_NAME.isNotBlank())
        assertTrue(SollServerSyncScheduler.IMMEDIATE_WORK_NAME.isNotBlank())
        assertTrue(SollServerSyncScheduler.PERIODIC_WORK_NAME != SollServerSyncScheduler.IMMEDIATE_WORK_NAME)
        assertTrue(SollServerSyncScheduler.PERIODIC_WORK_NAME != SollServerSyncWorker.UNIQUE_WORK_NAME)
    }

    @Test
    fun `feed import treats permanent client responses as terminal`() {
        listOf(400, 401, 403, 404, 409, 413, 422).forEach { statusCode ->
            assertEquals(
                FeedImportFailureDisposition.TERMINAL,
                feedImportHttpFailureDisposition(statusCode),
            )
        }
    }

    @Test
    fun `feed import retries transient http responses`() {
        listOf(408, 425, 429, 500, 502, 503).forEach { statusCode ->
            assertEquals(
                FeedImportFailureDisposition.RETRYABLE,
                feedImportHttpFailureDisposition(statusCode),
            )
        }
    }

    @Test
    fun `durable feedback retries unauthorized until device auth can refresh`() {
        assertEquals(
            FeedImportFailureDisposition.RETRYABLE,
            durableCommandHttpFailureDisposition(401),
        )
        assertEquals(
            FeedImportFailureDisposition.TERMINAL,
            durableCommandHttpFailureDisposition(403),
        )
    }

    @Test
    fun `terminal feed import does not keep worker retrying`() {
        val summary = SyncRetrySummary(
            retried = 1,
            succeeded = 0,
            failed = 0,
            terminal = 1,
            remainingOpen = 0,
        )

        assertEquals(SyncWorkDecision.SUCCESS, syncQueueWorkDecision(summary))
    }

    @Test
    fun `interrupted feed import is returned to pending without losing idempotency payload`() {
        val running = SyncQueueEntity(
            id = "feed-import:share-1",
            kind = SyncQueueEntity.KIND_FEED_IMPORT,
            status = SyncQueueEntity.STATUS_RUNNING,
            payloadJson = """{"url":"https://example.com","client_id":"share-1"}""",
            attempts = 1,
            lastError = null,
            createdAt = 1L,
            updatedAt = 2L,
            nextAttemptAt = 3L,
        )

        val recovered = requireNotNull(interruptedDurableDeliveryRecovery(running, recoveredAt = 10L))

        assertEquals(SyncQueueEntity.STATUS_PENDING, recovered.status)
        assertEquals(running.payloadJson, recovered.payloadJson)
        assertEquals(running.attempts, recovered.attempts)
        assertEquals(0L, recovered.nextAttemptAt)
        assertEquals(10L, recovered.updatedAt)
    }

    @Test
    fun `interrupted recovery does not reset unrelated running actions`() {
        val running = SyncQueueEntity(
            id = "note-1",
            kind = SyncQueueEntity.KIND_RAW_NOTE,
            status = SyncQueueEntity.STATUS_RUNNING,
            payloadJson = "{}",
            attempts = 1,
            lastError = null,
            createdAt = 1L,
            updatedAt = 2L,
            nextAttemptAt = 0L,
        )

        assertNull(interruptedDurableDeliveryRecovery(running, recoveredAt = 10L))
    }

    @Test
    fun `interrupted durable feedback preserves stable client id for retry`() {
        val running = SyncQueueEntity(
            id = "assistant-feedback:feedback-1",
            kind = SyncQueueEntity.KIND_ASSISTANT_FEEDBACK,
            status = SyncQueueEntity.STATUS_RUNNING,
            payloadJson = """{"entity_type":"initiative","entity_id":"initiative-1","decision":"accepted","client_id":"feedback-1"}""",
            attempts = 2,
            lastError = null,
            createdAt = 1L,
            updatedAt = 2L,
            nextAttemptAt = 3L,
        )

        val recovered = requireNotNull(interruptedDurableDeliveryRecovery(running, recoveredAt = 20L))

        assertEquals(SyncQueueEntity.STATUS_PENDING, recovered.status)
        assertEquals(running.payloadJson, recovered.payloadJson)
        assertTrue(recovered.payloadJson.contains("\"client_id\":\"feedback-1\""))
    }

    @Test
    fun `notification receipt id is stable per event and receipt state`() {
        val first = notificationReceiptClientId("event-42", "received")

        assertEquals(first, notificationReceiptClientId("event-42", "received"))
        assertTrue(first != notificationReceiptClientId("event-42", "opened"))
    }

    @Test
    fun `refresh aware auth accepts renewed device token and rejects stale token`() {
        assertEquals(
            "Bearer renewed-device",
            selectRefreshAwareAuthorizationHeader(
                deviceAuthorization = "Bearer renewed-device",
                deviceTokenNeedsRefresh = false,
                fallbackAuthorization = "Bearer owner",
            ),
        )
        assertEquals(
            "Bearer owner",
            selectRefreshAwareAuthorizationHeader(
                deviceAuthorization = "Bearer stale-device",
                deviceTokenNeedsRefresh = true,
                fallbackAuthorization = "Bearer owner",
            ),
        )
        assertNull(
            selectRefreshAwareAuthorizationHeader(
                deviceAuthorization = "Bearer stale-device",
                deviceTokenNeedsRefresh = true,
                fallbackAuthorization = null,
            )
        )
    }

    @Test
    fun `note worker retries mixed success and failure batches`() {
        val summary = NoteSyncSummary(
            processed = 2,
            succeeded = 1,
            failed = 1,
        )

        assertEquals(SyncWorkDecision.RETRY, noteSyncWorkDecision(summary))
    }

    @Test
    fun `note worker succeeds only when no items failed`() {
        val summary = NoteSyncSummary(
            processed = 2,
            succeeded = 2,
            failed = 0,
        )

        assertEquals(SyncWorkDecision.SUCCESS, noteSyncWorkDecision(summary))
    }

    @Test
    fun `sync queue worker retries failed batches`() {
        val summary = SyncRetrySummary(
            retried = 2,
            succeeded = 1,
            failed = 1,
            remainingOpen = 1,
        )

        assertEquals(SyncWorkDecision.RETRY, syncQueueWorkDecision(summary))
    }

    @Test
    fun `sync queue worker retries when deferred open items remain`() {
        val summary = SyncRetrySummary(
            retried = 0,
            succeeded = 0,
            failed = 0,
            remainingOpen = 1,
        )

        assertEquals(SyncWorkDecision.RETRY, syncQueueWorkDecision(summary))
    }

    @Test
    fun `sync queue worker succeeds after queue is drained`() {
        val summary = SyncRetrySummary(
            retried = 2,
            succeeded = 2,
            failed = 0,
            remainingOpen = 0,
        )

        assertEquals(SyncWorkDecision.SUCCESS, syncQueueWorkDecision(summary))
    }

    @Test
    fun `gadget server sync retries when snapshot sync failed`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = false,
            meshSummary = MeshOutboxWorkerSummary(),
        )

        assertEquals(SyncWorkDecision.RETRY, gadgetServerSyncWorkDecision(summary))
    }

    @Test
    fun `gadget server sync does not immediately retry unsupported mesh payloads`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = true,
            meshSummary = MeshOutboxWorkerSummary(claimed = 1, failed = 1, unsupported = 1),
        )

        assertEquals(SyncWorkDecision.SUCCESS, gadgetServerSyncWorkDecision(summary))
    }

    @Test
    fun `gadget server sync retries transport mesh failures`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = true,
            meshSummary = MeshOutboxWorkerSummary(claimed = 1, failed = 1, unsupported = 0),
        )

        assertEquals(SyncWorkDecision.RETRY, gadgetServerSyncWorkDecision(summary))
    }

    @Test
    fun `gadget server sync retries when unsupported mesh failure report is not delivered`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = true,
            meshSummary = MeshOutboxWorkerSummary(claimed = 1, failed = 1),
        )

        assertEquals(SyncWorkDecision.RETRY, gadgetServerSyncWorkDecision(summary))
    }

    @Test
    fun `gadget server sync retries command transport failures`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = true,
            commandSummary = GadgetCommandWorkerSummary(claimed = 1, transportFailed = 1),
        )

        assertEquals(SyncWorkDecision.RETRY, gadgetServerSyncWorkDecision(summary))
    }

    @Test
    fun `gadget server sync succeeds after terminal command failure is reported`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = true,
            commandSummary = GadgetCommandWorkerSummary(claimed = 1, terminalFailed = 1),
        )

        assertEquals(SyncWorkDecision.SUCCESS, gadgetServerSyncWorkDecision(summary))
    }

    @Test
    fun `mesh worker fails closed for status payload without local consumer`() {
        val decision = meshOutboxDeliveryDecision(meshItem("""{"type":"status","message":"ok"}"""))

        assertEquals(MeshOutboxDeliveryAction.FAIL, decision.action)
        assertTrue(decision.error.orEmpty().contains("No registered local consumer"))
    }

    @Test
    fun `mesh worker acks server chat payloads`() {
        val decision = meshOutboxDeliveryDecision(meshItem("""{"type":"chat_message","message":"Новая задача"}"""))

        assertEquals(MeshOutboxDeliveryAction.ACK, decision.action)
    }

    @Test
    fun `mesh worker rejects command payload until command executor exists`() {
        val decision = meshOutboxDeliveryDecision(meshItem("""{"type":"command","name":"toggle"}"""))

        assertEquals(MeshOutboxDeliveryAction.FAIL, decision.action)
    }

    @Test
    fun `mesh worker rejects plain text payloads`() {
        val decision = meshOutboxDeliveryDecision(meshItem("turn pump on"))

        assertEquals(MeshOutboxDeliveryAction.FAIL, decision.action)
    }

    @Test
    fun `mesh worker fails closed for legacy note until note consumer exists`() {
        val decision = meshOutboxDeliveryDecision(meshItem("/note сохранить идею"))

        assertEquals(MeshOutboxDeliveryAction.FAIL, decision.action)
        assertTrue(decision.error.orEmpty().contains("No registered local consumer"))
    }

    @Test
    fun `gadget command worker executes read only commands`() {
        val decision = gadgetCommandExecutionDecision(command = "getSensors", hasLocalDevice = true)

        assertEquals(GadgetCommandExecutionAction.EXECUTE, decision.action)
    }

    @Test
    fun `ordinary relay bearer enables gadget command plane without device bearer`() {
        assertTrue(gadgetCommandAuthAvailable(deviceAccessToken = "", userAccessToken = "relay-bearer"))
        assertTrue(gadgetCommandAuthAvailable(deviceAccessToken = "device-bearer", userAccessToken = ""))
        assertEquals(false, gadgetCommandAuthAvailable(deviceAccessToken = "", userAccessToken = ""))
    }

    @Test
    fun `remote worker identity uses validated pairing client id`() {
        assertEquals("android-main", resolveGadgetWorkerId(deviceId = "", remoteClientId = "android-main"))
        assertEquals("phone-local", resolveGadgetWorkerId(deviceId = "phone-local", remoteClientId = "android-main"))
        assertEquals("_android", normalizeSollRemoteClientId("_android"))
        assertEquals("-android", normalizeSollRemoteClientId("-android"))
        listOf("bad.id", "bad:id", "../android", "a".repeat(65)).forEach { invalid ->
            assertEquals("", normalizeSollRemoteClientId(invalid))
        }
        assertEquals(DEFAULT_SOLL_REMOTE_CLIENT_ID, resolveGadgetWorkerId(deviceId = "", remoteClientId = "bad.id"))
    }

    @Test
    fun `authoritative empty snapshot never falls back to local gadget ids`() {
        assertTrue(gadgetCommandCandidateIds(emptyList()).isEmpty())
    }

    @Test
    fun `gadget claims are bounded and exclude disabled snapshots`() {
        val snapshots = (1..25).map { index ->
            serverSnapshot(id = "gadget-$index", enabled = index != 2)
        }

        val candidates = gadgetCommandCandidateIds(snapshots)

        assertEquals(20, candidates.size)
        assertTrue("gadget-2" !in candidates)
    }

    @Test
    fun `claimed command target and lifecycle must match request`() {
        assertTrue(validateClaimedGadgetCommand("aquik-1", gadgetCommand(gadgetId = "aquik-1")).valid)
        assertEquals(
            false,
            validateClaimedGadgetCommand("aquik-1", gadgetCommand(gadgetId = "aquik-2")).valid,
        )
        assertEquals(
            false,
            validateClaimedGadgetCommand(
                "aquik-1",
                gadgetCommand(gadgetId = "aquik-1", status = "approval_required"),
            ).valid,
        )
    }

    @Test
    fun `ack and result responses remain bound to claimed command`() {
        val command = gadgetCommand(gadgetId = "aquik-1", status = "acked")

        assertTrue(
            validateGadgetCommandResponse(
                requestedGadgetId = "aquik-1",
                requestedCommandId = "cmd-1",
                response = command,
                expectedStatuses = setOf("acked"),
            ).valid
        )
        assertEquals(
            false,
            validateGadgetCommandResponse(
                requestedGadgetId = "aquik-1",
                requestedCommandId = "other-command",
                response = command,
                expectedStatuses = setOf("acked"),
            ).valid,
        )
        assertEquals(
            false,
            validateGadgetCommandResponse(
                requestedGadgetId = "aquik-1",
                requestedCommandId = "cmd-1",
                response = command,
                expectedStatuses = setOf("done"),
            ).valid,
        )
    }

    @Test
    fun `write risk from server remains non executable even for read command name`() {
        val decision = gadgetCommandExecutionDecision(
            command = "getSensors",
            serverRiskLevel = "write_requires_approval",
            hasLocalDevice = true,
        )

        assertEquals(GadgetCommandExecutionAction.FAIL, decision.action)
    }

    @Test
    fun `protocol failures keep gadget worker in retry state`() {
        val summary = GadgetServerSyncSummary(
            snapshotsSynced = true,
            commandSummary = GadgetCommandWorkerSummary(
                protocolFailed = 1,
                lastError = "Gadget command target mismatch",
            ),
        )

        assertEquals(SyncWorkDecision.RETRY, gadgetServerSyncWorkDecision(summary))
        assertEquals("Gadget command target mismatch", summary.lastError())
    }

    @Test
    fun `execution marker refuses replay after ack or result uncertainty`() {
        assertTrue(shouldRefuseGadgetCommandReplay(hasExecutionMarker = true))
        assertEquals(false, shouldRefuseGadgetCommandReplay(hasExecutionMarker = false))
        assertEquals(
            gadgetCommandExecutionMarkerId("cmd-1"),
            gadgetCommandExecutionMarkerId(" cmd-1 "),
        )
    }

    @Test
    fun `gadget command worker rejects actuator commands`() {
        val decision = gadgetCommandExecutionDecision(command = "setPump", hasLocalDevice = true)

        assertEquals(GadgetCommandExecutionAction.FAIL, decision.action)
        assertTrue(decision.error.orEmpty().contains("requires explicit approval"))
    }

    @Test
    fun `gadget command worker rejects unknown commands as unsupported`() {
        val decision = gadgetCommandExecutionDecision(command = "factoryReset", hasLocalDevice = true)

        assertEquals(GadgetCommandExecutionAction.FAIL, decision.action)
        assertTrue(decision.error.orEmpty().contains("Unsupported gadget command"))
    }

    @Test
    fun `gadget command worker rejects missing local device`() {
        val decision = gadgetCommandExecutionDecision(command = "getSensors", hasLocalDevice = false)

        assertEquals(GadgetCommandExecutionAction.FAIL, decision.action)
    }

    @Test
    fun `gadget command resolver maps server local ip to known device`() {
        val snapshot = serverSnapshot(
            id = "aquik-cloud-1",
            localIp = "192.168.4.20",
            heartbeatPayload = mapOf("wsPort" to 81, "path" to "ws"),
        )
        val local = knownDevice(id = "aquik-v2:192.168.4.20:81", host = "192.168.4.20")

        val target = resolveLocalDeviceForGadgetCommand(
            command = gadgetCommand(gadgetId = snapshot.id),
            snapshotsById = mapOf(snapshot.id to snapshot),
            localDevices = listOf(local),
        )

        assertEquals(local.id, target.device?.id)
    }

    @Test
    fun `gadget command resolver prefers exact local id`() {
        val local = knownDevice(id = "aquik-cloud-1", host = "10.0.0.12")

        val target = resolveLocalDeviceForGadgetCommand(
            command = gadgetCommand(gadgetId = local.id),
            snapshotsById = emptyMap(),
            localDevices = listOf(local),
        )

        assertEquals(local.id, target.device?.id)
    }

    @Test
    fun `gadget command resolver rejects ambiguous endpoint matches`() {
        val snapshot = serverSnapshot(id = "aquik-cloud-1", localIp = "192.168.4.20")

        val target = resolveLocalDeviceForGadgetCommand(
            command = gadgetCommand(gadgetId = snapshot.id),
            snapshotsById = mapOf(snapshot.id to snapshot),
            localDevices = listOf(
                knownDevice(id = "aquik-v2:192.168.4.20:81", host = "192.168.4.20", port = 81),
                knownDevice(id = "aquik-v2:192.168.4.20:82", host = "192.168.4.20", port = 82),
            ),
        )

        assertNull(target.device)
        assertTrue(target.error.orEmpty().contains("Ambiguous"))
    }

    private fun meshItem(text: String): SollMeshOutboxItem =
        SollMeshOutboxItem(
            outboundId = "out-1",
            toPeer = "android-1",
            text = text,
            status = "sent",
            retryCount = 0,
            maxRetries = 3,
            lastError = null,
            createdAt = "2026-05-15T00:00:00",
            lastAttemptAt = null,
            ackedAt = null,
        )

    private fun knownDevice(
        id: String,
        host: String,
        port: Int = 81,
        path: String = "ws",
    ): KnownDevice =
        KnownDevice(
            id = id,
            profileId = "aquik-v2",
            name = id,
            host = host,
            port = port,
            path = path,
            transport = DeviceTransport.WEBSOCKET,
            authMode = DeviceAuthMode.TOKEN,
            lastStatus = "AUTHENTICATED",
            lastSeenAt = 1L,
            createdAt = 1L,
            updatedAt = 1L,
        )

    private fun serverSnapshot(
        id: String,
        enabled: Boolean = true,
        localIp: String? = null,
        heartbeatPayload: Map<String, Any?> = emptyMap(),
    ): GadgetCloudSnapshot =
        GadgetCloudSnapshot(
            id = id,
            name = id,
            profileId = "aquik-v2",
            enabled = enabled,
            localIp = localIp,
            heartbeatPayload = heartbeatPayload,
            lastHeartbeatAt = "2026-05-15T00:00:00Z",
            lastTelemetryAt = null,
            latestTelemetry = emptyMap(),
            latestEventType = null,
            latestEventSummary = null,
            stale = false,
            updatedAt = "2026-05-15T00:00:00Z",
        )

    private fun gadgetCommand(
        gadgetId: String,
        status: String = "claimed",
    ): GadgetCloudCommand =
        GadgetCloudCommand(
            id = "cmd-1",
            gadgetId = gadgetId,
            command = "getSensors",
            params = emptyMap(),
            status = status,
            reason = "",
            result = emptyMap(),
            createdAt = "2026-05-15T00:00:00Z",
            expiresAt = null,
            completedAt = null,
        )
}
