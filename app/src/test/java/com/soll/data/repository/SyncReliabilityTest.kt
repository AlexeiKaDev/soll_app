package com.soll.data.repository

import org.junit.Assert.assertEquals
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
}
