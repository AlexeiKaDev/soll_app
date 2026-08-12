package com.soll.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedImportLinkApiModelsTest {
    @Test
    fun `request carries share context and stable idempotency value`() {
        val request = FeedImportLinkRequest(
            url = "https://example.com/article",
            title = "Article",
            sharedText = "Article https://example.com/article",
            clientId = "share-123",
            idempotencyKey = "share-123",
        )

        assertEquals("android_share", request.source)
        assertEquals("share-123", request.clientId)
        assertEquals(request.clientId, request.idempotencyKey)
    }

    @Test
    fun `response mapping accepts explicit and legacy success shapes`() {
        val explicit = FeedImportLinkResponse(
            success = true,
            status = "created",
            entityId = "feed:1",
            sourceId = "android_share",
            clusterId = "cluster:1",
        ).toDomain()
        val legacy = FeedImportLinkResponse(status = "duplicate", itemId = "feed:2").toDomain()

        assertTrue(explicit.success)
        assertEquals("feed:1", explicit.entityId)
        assertEquals("cluster:1", explicit.clusterId)
        assertTrue(legacy.success)
        assertTrue(legacy.duplicate)
        assertEquals("feed:2", legacy.entityId)
    }

    @Test
    fun `explicit rejection wins over a success-like status`() {
        val rejected = FeedImportLinkResponse(
            success = false,
            status = "queued",
            message = "rejected by policy",
        ).toDomain()

        assertFalse(rejected.success)
        assertEquals("rejected by policy", rejected.message)
    }
}
