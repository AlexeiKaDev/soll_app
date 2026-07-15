package com.soll.data.local.entity

import com.soll.domain.soll.SollTask
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCacheEntityTest {
    @Test
    fun `task cache round trip keeps execution metadata`() {
        val task = SollTask(
            id = "task-approval-1",
            title = "Review Android push payload",
            description = "Keep execution metadata in the offline task cache",
            sourceRef = "manual/android-push",
            projectName = "Soll",
            status = "today",
            priority = "A",
            dueDate = "2026-07-04",
            tags = listOf("android", "push"),
            approvalId = "approval-1",
            toolJobId = "tool-job-1",
            executionState = "waiting_approval",
            outcomeArtifacts = listOf("wiki/task-board.md", "logs/fcm.txt"),
            valueMetric = "closed_app_delivery",
            branch = "product",
            pairId = "pair-1",
            assignedNodeId = "soll-home",
            requiredCapabilities = listOf("android_adb", "usb_otg"),
            routingState = "waiting_for_android_adb_node",
            executionPhase = "needs_user",
            executionReason = "blocked: connect an ADB-visible phone",
        )

        val restored = TaskCacheEntity.fromDomain(task, updatedAt = 123L).toDomain()

        assertEquals(task, restored)
    }
}
