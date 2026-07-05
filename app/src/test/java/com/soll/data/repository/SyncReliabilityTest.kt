package com.soll.data.repository

import com.soll.domain.device.DeviceAuthMode
import com.soll.domain.device.DeviceTransport
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.KnownDevice
import com.soll.domain.soll.SollMeshOutboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReliabilityTest {
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
    fun `mesh worker acks allowlisted status payload`() {
        val decision = meshOutboxDeliveryDecision(meshItem("""{"type":"status","message":"ok"}"""))

        assertEquals(MeshOutboxDeliveryAction.ACK, decision.action)
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
    fun `gadget command worker executes read only commands`() {
        val decision = gadgetCommandExecutionDecision(command = "getSensors", hasLocalDevice = true)

        assertEquals(GadgetCommandExecutionAction.EXECUTE, decision.action)
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
        localIp: String? = null,
        heartbeatPayload: Map<String, Any?> = emptyMap(),
    ): GadgetCloudSnapshot =
        GadgetCloudSnapshot(
            id = id,
            name = id,
            profileId = "aquik-v2",
            enabled = true,
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

    private fun gadgetCommand(gadgetId: String): GadgetCloudCommand =
        GadgetCloudCommand(
            id = "cmd-1",
            gadgetId = gadgetId,
            command = "getSensors",
            params = emptyMap(),
            status = "claimed",
            reason = "",
            result = emptyMap(),
            createdAt = "2026-05-15T00:00:00Z",
            expiresAt = null,
            completedAt = null,
        )
}
