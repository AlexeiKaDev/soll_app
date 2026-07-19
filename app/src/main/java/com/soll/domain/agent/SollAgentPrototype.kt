package com.soll.domain.agent

enum class AgentRuntimeCapability {
    SHELL,
    SKILLS,
    COMPACTION,
}

data class AgentShellTool(
    val id: String,
    val summary: String,
    val requiresConfirmation: Boolean = true,
)

data class AgentSkillSummary(
    val id: String,
    val summary: String,
)

data class AgentSkill(
    val id: String,
    val summary: String,
    val instructions: String,
) {
    fun toSummary(): AgentSkillSummary = AgentSkillSummary(id = id, summary = summary)
}

enum class AgentContextEventType {
    OBSERVATION,
    COMPLETED,
    PENDING,
}

data class AgentContextEvent(
    val type: AgentContextEventType,
    val summary: String,
    val evidenceRefs: List<String> = emptyList(),
)

data class AgentCompactionPolicy(
    val maxEventCount: Int = 48,
    val retainedRecentEventCount: Int = 8,
) {
    init {
        require(maxEventCount > 1) { "Лимит событий должен быть больше одного" }
        require(retainedRecentEventCount in 1 until maxEventCount) {
            "Число сохраняемых последних событий должно быть меньше общего лимита"
        }
    }
}

data class AgentCompactionCheckpoint(
    val objective: String,
    val compactedEventCount: Int,
    val completedSteps: List<String>,
    val pendingSteps: List<String>,
    val evidenceRefs: List<String>,
)

data class SollAgentPrototypeConfig(
    val capabilities: Set<AgentRuntimeCapability>,
    val shellTools: List<AgentShellTool> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val compactionPolicy: AgentCompactionPolicy = AgentCompactionPolicy(),
) {
    init {
        require(capabilities.isNotEmpty()) { "Нужно явно указать возможности прототипа агента" }
        requireUniqueIds("инструментов shell", shellTools.map { it.id })
        requireUniqueIds("skills", skills.map { it.id })
    }

    private fun requireUniqueIds(label: String, ids: List<String>) {
        require(ids.none { it.isBlank() }) { "ID $label не должен быть пустым" }
        require(ids.distinct().size == ids.size) { "ID $label должны быть уникальными" }
    }
}

data class SollAgentRunRequest(
    val objective: String,
    val requestedShellToolIds: Set<String> = emptySet(),
    val requestedSkillIds: Set<String> = emptySet(),
    val events: List<AgentContextEvent> = emptyList(),
)

data class SollAgentRunContext(
    val objective: String,
    val capabilities: Set<AgentRuntimeCapability>,
    val shellTools: List<AgentShellTool>,
    val skillIndex: List<AgentSkillSummary>,
    val loadedSkills: List<AgentSkill>,
    val checkpoint: AgentCompactionCheckpoint?,
    val recentEvents: List<AgentContextEvent>,
)

/**
 * Builds the safe context envelope for the Soll agent prototype.
 *
 * Shell entries are registry IDs rather than executable command strings, skill details are loaded
 * only after selection, and deterministic compaction keeps run state inspectable without requiring
 * an Android-side model or autonomous execution loop.
 */
class SollAgentContextAssembler(
    private val config: SollAgentPrototypeConfig,
) {
    fun assemble(request: SollAgentRunRequest): SollAgentRunContext {
        require(request.objective.isNotBlank()) { "Цель запуска агента не должна быть пустой" }

        val shellTools = selectShellTools(request.requestedShellToolIds)
        val loadedSkills = selectSkills(request.requestedSkillIds)
        val (checkpoint, recentEvents) = compactIfNeeded(request.objective, request.events)

        return SollAgentRunContext(
            objective = request.objective.trim(),
            capabilities = config.capabilities.toSet(),
            shellTools = shellTools,
            skillIndex = config.skills.map(AgentSkill::toSummary),
            loadedSkills = loadedSkills,
            checkpoint = checkpoint,
            recentEvents = recentEvents,
        )
    }

    private fun selectShellTools(requestedIds: Set<String>): List<AgentShellTool> {
        if (requestedIds.isEmpty()) return emptyList()
        require(AgentRuntimeCapability.SHELL in config.capabilities) {
            "Возможность SHELL не включена в явный профиль агента"
        }

        val knownIds = config.shellTools.mapTo(linkedSetOf()) { it.id }
        val unknownIds = requestedIds - knownIds
        require(unknownIds.isEmpty()) {
            "Неизвестные инструменты shell: ${unknownIds.sorted().joinToString()}"
        }
        return config.shellTools.filter { it.id in requestedIds }
    }

    private fun selectSkills(requestedIds: Set<String>): List<AgentSkill> {
        if (requestedIds.isEmpty()) return emptyList()
        require(AgentRuntimeCapability.SKILLS in config.capabilities) {
            "Возможность SKILLS не включена в явный профиль агента"
        }

        val knownIds = config.skills.mapTo(linkedSetOf()) { it.id }
        val unknownIds = requestedIds - knownIds
        require(unknownIds.isEmpty()) {
            "Неизвестные skills: ${unknownIds.sorted().joinToString()}"
        }
        return config.skills.filter { it.id in requestedIds }
    }

    private fun compactIfNeeded(
        objective: String,
        events: List<AgentContextEvent>,
    ): Pair<AgentCompactionCheckpoint?, List<AgentContextEvent>> {
        val policy = config.compactionPolicy
        if (
            AgentRuntimeCapability.COMPACTION !in config.capabilities ||
            events.size <= policy.maxEventCount
        ) {
            return null to events
        }

        val recentEvents = events.takeLast(policy.retainedRecentEventCount)
        val compactedEvents = events.dropLast(policy.retainedRecentEventCount)
        val checkpoint = AgentCompactionCheckpoint(
            objective = objective.trim(),
            compactedEventCount = compactedEvents.size,
            completedSteps = compactedEvents.summariesOf(AgentContextEventType.COMPLETED),
            pendingSteps = compactedEvents.summariesOf(AgentContextEventType.PENDING),
            evidenceRefs = compactedEvents.flatMap { it.evidenceRefs }.distinct(),
        )
        return checkpoint to recentEvents
    }

    private fun List<AgentContextEvent>.summariesOf(type: AgentContextEventType): List<String> =
        asSequence()
            .filter { it.type == type }
            .map { it.summary.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
}
