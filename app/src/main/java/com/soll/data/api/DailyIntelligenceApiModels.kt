package com.soll.data.api

import com.squareup.moshi.Json
import com.soll.domain.soll.SollCalendarEvent
import com.soll.domain.soll.SollCalendarSnapshot
import com.soll.domain.soll.SollFeedFeedbackContract
import com.soll.domain.soll.SollFeedItem
import com.soll.domain.soll.SollFeedImportResult
import com.soll.domain.soll.SollFeedPage
import com.soll.domain.soll.SollTechnologyAssessment
import com.soll.domain.soll.SollTodayCard
import com.soll.domain.soll.SollTodayFreshness
import com.soll.domain.soll.SollTodayNextAction
import com.soll.domain.soll.SollTodaySnapshot

data class TodaySnapshotResponse(
    val date: String = "",
    @Json(name = "generated_at") val generatedAt: String = "",
    val status: String = "partial",
    val summary: String = "",
    @Json(name = "briefing_cards") val briefingCards: List<TodayCardResponse> = emptyList(),
    val urgent: List<FeedItemResponse> = emptyList(),
    @Json(name = "feed_preview") val feedPreview: List<FeedItemResponse> = emptyList(),
    @Json(name = "next_action") val nextAction: TodayNextActionResponse = TodayNextActionResponse(),
    val freshness: TodayFreshnessResponse = TodayFreshnessResponse(),
    val calendar: CalendarSnapshotResponse = CalendarSnapshotResponse(),
    val warnings: List<String> = emptyList(),
)

data class TodayCardResponse(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val summary: String = "",
    val priority: Int = 0,
    val url: String = "",
    @Json(name = "why_for_you") val whyForYou: String = "",
    @Json(name = "source_refs") val sourceRefs: List<String> = emptyList(),
)

data class TodayNextActionResponse(
    val kind: String = "none",
    val id: String = "",
    val title: String = "",
)

data class TodayFreshnessResponse(
    @Json(name = "generated_at") val generatedAt: String = "",
    @Json(name = "weather_checked_at") val weatherCheckedAt: String = "",
    @Json(name = "calendar_synced_at") val calendarSyncedAt: String = "",
    @Json(name = "feed_newest_at") val feedNewestAt: String = "",
)

data class FeedPageResponse(
    @Json(name = "generated_at") val generatedAt: String = "",
    val items: List<FeedItemResponse> = emptyList(),
    @Json(name = "next_cursor") val nextCursor: String = "",
    @Json(name = "has_more") val hasMore: Boolean = false,
    val total: Int = 0,
    val category: String = "",
    val warnings: List<String> = emptyList(),
)

data class FeedItemResponse(
    val id: String = "",
    @Json(name = "source_id") val sourceId: String = "",
    @Json(name = "source_name") val sourceName: String = "",
    val category: String = "",
    val title: String = "",
    val summary: String = "",
    val url: String = "",
    @Json(name = "published_at") val publishedAt: String = "",
    @Json(name = "rank_score") val rankScore: Int = 0,
    val urgency: String = "none",
    @Json(name = "why_for_you") val whyForYou: String = "",
    val freshness: String = "",
    val assessment: TechnologyAssessmentResponse? = null,
    val feedback: FeedFeedbackContractResponse = FeedFeedbackContractResponse(),
)

data class TechnologyAssessmentResponse(
    val method: String = "",
    val readiness: String = "watch",
    val targets: List<String> = emptyList(),
    @Json(name = "evidence_level") val evidenceLevel: String = "unknown",
    @Json(name = "project_fit") val projectFit: String = "unknown",
    val risk: String = "none",
    val requirements: List<String> = emptyList(),
    val experiment: String = "",
    @Json(name = "revisit_at") val revisitAt: String = "",
)

data class FeedFeedbackContractResponse(
    @Json(name = "entity_id") val entityId: String = "",
    val topic: String = "important_news",
    val source: String = "",
    val decisions: List<String> = emptyList(),
)

data class FeedFeedbackRequest(
    val decision: String,
    val topic: String,
    val source: String,
    val note: String = "",
    @Json(name = "client_id") val clientId: String,
    @Json(name = "idempotency_key") val idempotencyKey: String = clientId,
)

data class FeedFeedbackResponse(
    val success: Boolean = false,
)

data class FeedImportLinkRequest(
    val url: String,
    val title: String = "",
    @Json(name = "shared_text") val sharedText: String = "",
    val source: String = "android_share",
    @Json(name = "client_id") val clientId: String? = null,
    @Json(name = "idempotency_key") val idempotencyKey: String? = clientId,
)

data class FeedImportLinkResponse(
    val success: Boolean? = null,
    val accepted: Boolean? = null,
    val status: String = "",
    val message: String = "",
    val id: String = "",
    @Json(name = "entity_id") val entityId: String = "",
    @Json(name = "item_id") val itemId: String = "",
    val duplicate: Boolean = false,
    val url: String = "",
    val title: String = "",
    @Json(name = "source_id") val sourceId: String = "",
    @Json(name = "cluster_id") val clusterId: String = "",
)

data class CalendarSnapshotRequest(
    val timezone: String,
    val events: List<CalendarEventRequest>,
)

data class CalendarEventRequest(
    @Json(name = "event_id") val eventId: String,
    val title: String,
    @Json(name = "start_at") val startAt: String,
    @Json(name = "end_at") val endAt: String,
    @Json(name = "all_day") val allDay: Boolean,
    val location: String,
)

data class CalendarSnapshotResponse(
    val available: Boolean = false,
    @Json(name = "synced_at") val syncedAt: String = "",
    val timezone: String = "",
    val events: List<CalendarEventResponse> = emptyList(),
    val count: Int = 0,
)

data class CalendarEventResponse(
    @Json(name = "event_id") val eventId: String = "",
    val title: String = "Событие",
    @Json(name = "start_at") val startAt: String = "",
    @Json(name = "end_at") val endAt: String = "",
    @Json(name = "all_day") val allDay: Boolean = false,
    val location: String = "",
)

fun TodaySnapshotResponse.toDomain(): SollTodaySnapshot = SollTodaySnapshot(
    date = date,
    generatedAt = generatedAt,
    status = status,
    summary = summary,
    briefingCards = briefingCards.map(TodayCardResponse::toDomain),
    urgent = urgent.map(FeedItemResponse::toDomain),
    feedPreview = feedPreview.map(FeedItemResponse::toDomain),
    nextAction = nextAction.toDomain(),
    freshness = freshness.toDomain(),
    calendar = calendar.toDomain(),
    warnings = warnings,
)

fun FeedPageResponse.toDomain(): SollFeedPage = SollFeedPage(
    generatedAt = generatedAt,
    items = items.map(FeedItemResponse::toDomain),
    nextCursor = nextCursor,
    hasMore = hasMore,
    total = total,
    category = category,
    warnings = warnings,
)

fun FeedImportLinkResponse.toDomain(): SollFeedImportResult {
    val normalizedStatus = status.trim().lowercase()
    val resolvedEntityId = entityId.ifBlank { itemId.ifBlank { id } }
    val resolvedSuccess = success
        ?: accepted
        ?: (normalizedStatus in setOf("accepted", "created", "duplicate", "ok", "queued", "success") ||
            resolvedEntityId.isNotBlank())
    return SollFeedImportResult(
        success = resolvedSuccess,
        status = status,
        message = message,
        entityId = resolvedEntityId,
        duplicate = duplicate || normalizedStatus == "duplicate",
        url = url,
        title = title,
        sourceId = sourceId,
        clusterId = clusterId,
    )
}

fun CalendarSnapshotResponse.toDomain(): SollCalendarSnapshot = SollCalendarSnapshot(
    available = available,
    syncedAt = syncedAt,
    timezone = timezone,
    events = events.map(CalendarEventResponse::toDomain),
    count = count,
)

private fun TodayCardResponse.toDomain() = SollTodayCard(id, kind, title, summary, priority, url, whyForYou, sourceRefs)
private fun TodayNextActionResponse.toDomain() = SollTodayNextAction(kind, id, title)
private fun TodayFreshnessResponse.toDomain() = SollTodayFreshness(generatedAt, weatherCheckedAt, calendarSyncedAt, feedNewestAt)
private fun FeedItemResponse.toDomain() = SollFeedItem(
    id, sourceId, sourceName, category, title, summary, url, publishedAt, rankScore, urgency,
    whyForYou, freshness, assessment?.toDomain(), feedback.toDomain(),
)
private fun TechnologyAssessmentResponse.toDomain() = SollTechnologyAssessment(
    method, readiness, targets, evidenceLevel, projectFit, risk, requirements, experiment, revisitAt,
)
private fun FeedFeedbackContractResponse.toDomain() = SollFeedFeedbackContract(entityId, topic, source, decisions)
private fun CalendarEventResponse.toDomain() = SollCalendarEvent(eventId, title, startAt, endAt, allDay, location)
