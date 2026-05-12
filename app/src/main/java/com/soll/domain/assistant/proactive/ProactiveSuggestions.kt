package com.soll.domain.assistant.proactive

import java.time.DayOfWeek

enum class ProactiveSuggestionPriority {
    LOW,
    MEDIUM,
    HIGH,
}

enum class ProactiveSuggestionAction {
    NONE,
    START_BOT,
    OPEN_APP_SETTINGS,
    OPEN_BATTERY_SETTINGS,
}

enum class ProactiveSuggestionFeedback {
    ACCEPTED,
    DISMISSED,
    SNOOZED,
}

data class ProactiveSignalSnapshot(
    val nowMillis: Long,
    val hourOfDay: Int,
    val dayOfWeek: DayOfWeek,
    val hasToken: Boolean,
    val botRunning: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val systemNotificationsEnabled: Boolean,
    val activeToolJobs: Int,
    val failedRecentToolJobs: Int,
    val pendingTasks: Int,
    val openSyncQueueItems: Int,
    val sollServerConfigured: Boolean,
)

data class ProactiveScenario(
    val id: String,
    val title: String,
    val detail: String,
    val priority: ProactiveSuggestionPriority,
    val confidence: Float,
    val action: ProactiveSuggestionAction = ProactiveSuggestionAction.NONE,
)

data class ProactiveSuggestion(
    val id: String,
    val title: String,
    val detail: String,
    val priority: ProactiveSuggestionPriority,
    val confidence: Float,
    val action: ProactiveSuggestionAction,
)

class ScenarioDetector {
    fun detect(snapshot: ProactiveSignalSnapshot): List<ProactiveScenario> = buildList {
        if (!snapshot.hasToken) {
            add(
                ProactiveScenario(
                    id = "telegram_token_missing",
                    title = "Настроить Telegram",
                    detail = "Бот не сможет принимать команды, пока токен не задан.",
                    priority = ProactiveSuggestionPriority.HIGH,
                    confidence = 0.98f,
                    action = ProactiveSuggestionAction.OPEN_APP_SETTINGS,
                )
            )
        }

        if (snapshot.hasToken && !snapshot.botRunning) {
            add(
                ProactiveScenario(
                    id = "bot_stopped",
                    title = "Запустить фонового бота",
                    detail = "Токен есть, но сервис сейчас остановлен.",
                    priority = ProactiveSuggestionPriority.HIGH,
                    confidence = 0.94f,
                    action = ProactiveSuggestionAction.START_BOT,
                )
            )
        }

        if (!snapshot.batteryOptimizationIgnored) {
            add(
                ProactiveScenario(
                    id = "battery_optimization_blocks_background",
                    title = "Снять ограничение батареи",
                    detail = "Android может останавливать бота, музыку и фоновые задачи.",
                    priority = ProactiveSuggestionPriority.HIGH,
                    confidence = 0.9f,
                    action = ProactiveSuggestionAction.OPEN_BATTERY_SETTINGS,
                )
            )
        }

        if (!snapshot.systemNotificationsEnabled) {
            add(
                ProactiveScenario(
                    id = "notifications_disabled",
                    title = "Разрешить уведомления",
                    detail = "События сохраняются в логах, но Android не показывает их снаружи приложения.",
                    priority = ProactiveSuggestionPriority.MEDIUM,
                    confidence = 0.86f,
                    action = ProactiveSuggestionAction.OPEN_APP_SETTINGS,
                )
            )
        }

        if (snapshot.pendingTasks > 0) {
            add(
                ProactiveScenario(
                    id = "tasks_pending_today",
                    title = "Разобрать задачи на сегодня",
                    detail = "Открытых задач: ${snapshot.pendingTasks}.",
                    priority = ProactiveSuggestionPriority.MEDIUM,
                    confidence = 0.82f,
                )
            )
        }

        if (snapshot.openSyncQueueItems > 0) {
            add(
                ProactiveScenario(
                    id = "sync_queue_needs_attention",
                    title = "Проверить синхронизацию",
                    detail = "В очереди Soll есть элементы, которые еще не отправлены: ${snapshot.openSyncQueueItems}.",
                    priority = ProactiveSuggestionPriority.MEDIUM,
                    confidence = 0.84f,
                )
            )
        }

        if (snapshot.failedRecentToolJobs > 0) {
            add(
                ProactiveScenario(
                    id = "recent_tool_failures",
                    title = "Посмотреть ошибки инструментов",
                    detail = "Недавних ошибок задач: ${snapshot.failedRecentToolJobs}.",
                    priority = ProactiveSuggestionPriority.MEDIUM,
                    confidence = 0.8f,
                )
            )
        }

        if (
            snapshot.sollServerConfigured &&
            snapshot.hourOfDay in MORNING_HOURS &&
            snapshot.dayOfWeek !in WEEKEND_DAYS
        ) {
            add(
                ProactiveScenario(
                    id = "morning_briefing_window",
                    title = "Проверить утренний контекст",
                    detail = "Сейчас рабочее утро, можно сверить задачи и статус синхронизации Soll.",
                    priority = ProactiveSuggestionPriority.LOW,
                    confidence = 0.66f,
                )
            )
        }
    }

    private companion object {
        val MORNING_HOURS = 7..11
        val WEEKEND_DAYS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

class SuggestionEngine(
    private val dailyCap: Int = 3,
) {
    fun buildSuggestions(
        scenarios: List<ProactiveScenario>,
        isSuppressed: (String) -> Boolean,
    ): List<ProactiveSuggestion> =
        scenarios
            .filterNot { isSuppressed(it.id) }
            .sortedWith(
                compareByDescending<ProactiveScenario> { it.priority.weight }
                    .thenByDescending { it.confidence }
                    .thenBy { it.id }
            )
            .take(dailyCap.coerceAtLeast(0))
            .map { scenario ->
                ProactiveSuggestion(
                    id = scenario.id,
                    title = scenario.title,
                    detail = scenario.detail,
                    priority = scenario.priority,
                    confidence = scenario.confidence.coerceIn(0f, 1f),
                    action = scenario.action,
                )
            }

    private val ProactiveSuggestionPriority.weight: Int
        get() = when (this) {
            ProactiveSuggestionPriority.HIGH -> 3
            ProactiveSuggestionPriority.MEDIUM -> 2
            ProactiveSuggestionPriority.LOW -> 1
        }
}
