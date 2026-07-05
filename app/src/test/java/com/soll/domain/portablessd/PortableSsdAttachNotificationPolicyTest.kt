package com.soll.domain.portablessd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableSsdAttachNotificationPolicyTest {
    @Test
    fun `ready snapshot announces recognized Soll SSD`() {
        val notice = PortableSsdAttachNotificationPolicy.noticeFor(
            snapshot = PortableSsdSnapshot(
                status = PortableSsdSnapshotStatus.READY,
                portableIdentityLabel = "Work SSD",
                hasPortableIdentity = true,
                wiki = listOf(entry("wiki")),
                daily = listOf(entry("daily")),
                tasks = listOf(entry("tasks")),
            ),
            hasSelectedRoot = true,
        )

        requireNotNull(notice)
        assertEquals(PortableSsdAttachNoticeKind.VERIFIED, notice.kind)
        assertEquals("Soll SSD подключен", notice.title)
        assertTrue(notice.message.contains("Work SSD"))
        assertTrue(notice.message.contains("Wiki: 1"))
        assertTrue(notice.message.contains("Daily: 1"))
        assertTrue(notice.message.contains("Tasks: 1"))
    }

    @Test
    fun `missing SAF root asks user to select SSD Wiki root`() {
        val notice = PortableSsdAttachNotificationPolicy.noticeFor(
            snapshot = PortableSsdSnapshot(status = PortableSsdSnapshotStatus.NO_ROOT),
            hasSelectedRoot = false,
        )

        requireNotNull(notice)
        assertEquals(PortableSsdAttachNoticeKind.NEED_SELECTION, notice.kind)
        assertTrue(notice.message.contains("SSD Wiki"))
    }

    @Test
    fun `invalid saved root does not claim the disk is recognized`() {
        val notice = PortableSsdAttachNotificationPolicy.noticeFor(
            snapshot = PortableSsdSnapshot(
                status = PortableSsdSnapshotStatus.INVALID,
                message = "Не найден portable Soll vault",
            ),
            hasSelectedRoot = true,
        )

        requireNotNull(notice)
        assertEquals(PortableSsdAttachNoticeKind.NOT_READY, notice.kind)
        assertTrue(notice.title.contains("не распознан"))
    }

    private fun entry(id: String): PortableSsdEntry =
        PortableSsdEntry(
            id = id,
            title = id,
            relativePath = "$id.md",
            section = PortableSsdSection.WIKI,
        )
}
