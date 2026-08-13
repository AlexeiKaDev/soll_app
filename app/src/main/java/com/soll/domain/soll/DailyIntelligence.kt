package com.soll.domain.soll

const val SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH = 80
const val SOLL_DURABLE_CLIENT_ID_MAX_LENGTH = 80

data class SollTodaySnapshot(
    val date: String = "",
    val generatedAt: String = "",
    val status: String = "partial",
    val summary: String = "",
    val briefingCards: List<SollTodayCard> = emptyList(),
    val urgent: List<SollFeedItem> = emptyList(),
    val feedPreview: List<SollFeedItem> = emptyList(),
    val nextAction: SollTodayNextAction = SollTodayNextAction(),
    val freshness: SollTodayFreshness = SollTodayFreshness(),
    val calendar: SollCalendarSnapshot = SollCalendarSnapshot(),
    val warnings: List<String> = emptyList(),
)

data class SollTodayCard(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val summary: String = "",
    val priority: Int = 0,
    val url: String = "",
    val whyForYou: String = "",
    val sourceRefs: List<String> = emptyList(),
)

data class SollTodayNextAction(
    val kind: String = "none",
    val id: String = "",
    val title: String = "",
)

data class SollTodayFreshness(
    val generatedAt: String = "",
    val weatherCheckedAt: String = "",
    val calendarSyncedAt: String = "",
    val feedNewestAt: String = "",
)

data class SollFeedPage(
    val generatedAt: String = "",
    val items: List<SollFeedItem> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false,
    val total: Int = 0,
    val category: String = "",
    val warnings: List<String> = emptyList(),
)

data class SollFeedImportResult(
    val success: Boolean = false,
    val status: String = "",
    val message: String = "",
    val entityId: String = "",
    val duplicate: Boolean = false,
    val url: String = "",
    val title: String = "",
    val sourceId: String = "",
    val clusterId: String = "",
)

data class SollFeedbackCommandResult(
    val accepted: Boolean = false,
    val duplicate: Boolean = false,
    val actionId: String = "",
    val status: String = "",
)

data class SollFeedItem(
    val id: String = "",
    val sourceId: String = "",
    val sourceName: String = "",
    val category: String = "",
    val title: String = "",
    val summary: String = "",
    val url: String = "",
    val publishedAt: String = "",
    val rankScore: Int = 0,
    val urgency: String = "none",
    val whyForYou: String = "",
    val freshness: String = "",
    val assessment: SollTechnologyAssessment? = null,
    val feedback: SollFeedFeedbackContract = SollFeedFeedbackContract(),
)

data class SollTechnologyAssessment(
    val method: String = "",
    val readiness: String = "watch",
    val targets: List<String> = emptyList(),
    val evidenceLevel: String = "unknown",
    val projectFit: String = "unknown",
    val risk: String = "none",
    val requirements: List<String> = emptyList(),
    val experiment: String = "",
    val revisitAt: String = "",
)

data class SollFeedFeedbackContract(
    val entityId: String = "",
    val topic: String = "important_news",
    val source: String = "",
    val decisions: List<String> = emptyList(),
)

data class SollCalendarEvent(
    val eventId: String = "",
    val title: String = "Событие",
    val startAt: String,
    val endAt: String = "",
    val allDay: Boolean = false,
    val location: String = "",
)

data class SollCalendarSnapshot(
    val available: Boolean = false,
    val syncedAt: String = "",
    val timezone: String = "",
    val events: List<SollCalendarEvent> = emptyList(),
    val count: Int = 0,
)
