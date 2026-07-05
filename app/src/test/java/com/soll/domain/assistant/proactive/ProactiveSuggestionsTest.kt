package com.soll.domain.assistant.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ProactiveSuggestionsTest {
    @Test
    fun detectorBuildsDeterministicSuggestionsFromAssistantSignals() {
        val scenarios = ScenarioDetector().detect(
            snapshot(
                hasToken = true,
                botRunning = false,
                batteryOptimizationIgnored = false,
                systemNotificationsEnabled = false,
                pendingTasks = 4,
                openSyncQueueItems = 2,
                failedRecentToolJobs = 1,
            )
        )

        val ids = scenarios.map { it.id }.toSet()
        assertTrue(ids.contains("soll_server_url_missing"))
        assertTrue(ids.contains("battery_optimization_blocks_background"))
        assertTrue(ids.contains("notifications_disabled"))
        assertTrue(ids.contains("tasks_pending_today"))
        assertTrue(ids.contains("sync_queue_needs_attention"))
        assertTrue(ids.contains("recent_tool_failures"))
        assertEquals(
            ProactiveSuggestionAction.OPEN_BATTERY_SETTINGS,
            scenarios.first { it.id == "battery_optimization_blocks_background" }.action,
        )
    }

    @Test
    fun detectorSuggestsMorningBriefingOnlyOnConfiguredWorkdayMorning() {
        val scenarios = ScenarioDetector().detect(
            snapshot(
                hourOfDay = 9,
                dayOfWeek = DayOfWeek.MONDAY,
                sollServerConfigured = true,
            )
        )

        assertTrue(scenarios.any { it.id == "morning_briefing_window" })
        assertTrue(
            ScenarioDetector().detect(
                snapshot(
                    hourOfDay = 9,
                    dayOfWeek = DayOfWeek.SUNDAY,
                    sollServerConfigured = true,
                )
            ).none { it.id == "morning_briefing_window" }
        )
    }

    @Test
    fun engineSortsByPriorityConfidenceAndAppliesDailyCap() {
        val suggestions = SuggestionEngine(dailyCap = 2).buildSuggestions(
            scenarios = listOf(
                scenario("low", ProactiveSuggestionPriority.LOW, 0.99f),
                scenario("medium", ProactiveSuggestionPriority.MEDIUM, 0.9f),
                scenario("high", ProactiveSuggestionPriority.HIGH, 0.4f),
                scenario("high_suppressed", ProactiveSuggestionPriority.HIGH, 0.99f),
            ),
            isSuppressed = { it == "high_suppressed" },
        )

        assertEquals(listOf("high", "medium"), suggestions.map { it.id })
    }

    private fun snapshot(
        hourOfDay: Int = 14,
        dayOfWeek: DayOfWeek = DayOfWeek.TUESDAY,
        hasToken: Boolean = true,
        botRunning: Boolean = true,
        batteryOptimizationIgnored: Boolean = true,
        systemNotificationsEnabled: Boolean = true,
        activeToolJobs: Int = 0,
        failedRecentToolJobs: Int = 0,
        pendingTasks: Int = 0,
        openSyncQueueItems: Int = 0,
        sollServerConfigured: Boolean = false,
    ): ProactiveSignalSnapshot =
        ProactiveSignalSnapshot(
            nowMillis = 1_000L,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek,
            hasToken = hasToken,
            botRunning = botRunning,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            systemNotificationsEnabled = systemNotificationsEnabled,
            activeToolJobs = activeToolJobs,
            failedRecentToolJobs = failedRecentToolJobs,
            pendingTasks = pendingTasks,
            openSyncQueueItems = openSyncQueueItems,
            sollServerConfigured = sollServerConfigured,
        )

    private fun scenario(
        id: String,
        priority: ProactiveSuggestionPriority,
        confidence: Float,
    ): ProactiveScenario =
        ProactiveScenario(
            id = id,
            title = id,
            detail = id,
            priority = priority,
            confidence = confidence,
        )
}
