package com.soll.project

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteGadgetCompatibilityGuardTest {
    @Test
    fun `worker records durable replay guard after ack and before local execute`() {
        val source = File("src/main/java/com/soll/data/repository/GadgetServerSyncWorker.kt").readText()
        val ack = source.indexOf("val ackResult = gateway.ackGadgetCommand")
        val marker = source.indexOf("deviceRepository.markGadgetCommandExecutionStarted")
        val execute = source.indexOf("val result = commandExecutor.execute")

        assertTrue(ack >= 0)
        assertTrue(marker > ack)
        assertTrue(execute > marker)
    }

    @Test
    fun `authoritative snapshots replace cache and command candidates never use local fallback`() {
        val deviceRepository = File("src/main/java/com/soll/data/repository/DeviceRepository.kt").readText()
        val worker = File("src/main/java/com/soll/data/repository/GadgetServerSyncWorker.kt").readText()

        assertTrue(deviceRepository.contains("deviceDao.replaceServerSnapshotEvents"))
        assertTrue(worker.contains("val candidates = gadgetCommandCandidateIds(enabledSnapshots)"))
        assertTrue(!worker.contains("ifEmpty { localDevices.map { it.id } }"))
    }

    @Test
    fun `remote pairing persists client identity without logging bearer`() {
        val settings = File("src/main/java/com/soll/data/repository/SettingsRepository.kt").readText()
        val scanner = File("src/main/java/com/soll/presentation/screens/tools/scanner/ScannerViewModel.kt").readText()

        assertTrue(settings.contains("KEY_SOLL_REMOTE_CLIENT_ID"))
        assertTrue(settings.contains("payload.clientId"))
        assertTrue(!scanner.contains("payload.accessToken"))
    }
}
