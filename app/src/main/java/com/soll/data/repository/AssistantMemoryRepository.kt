package com.soll.data.repository

import com.soll.data.local.dao.AssistantMemoryDao
import com.soll.data.local.entity.AssistantMemoryEntity
import com.soll.domain.assistant.memory.AssistantMemory
import com.soll.domain.assistant.memory.AssistantMemoryCategory
import com.soll.domain.assistant.memory.AssistantMemoryExporter
import com.soll.domain.assistant.proactive.ProactiveSuggestion
import com.soll.domain.soll.SollGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantMemorySyncResult(
    val queued: Boolean,
    val message: String,
)

@Singleton
class AssistantMemoryRepository @Inject constructor(
    private val assistantMemoryDao: AssistantMemoryDao,
    private val settingsRepository: SettingsRepository,
    private val sollGateway: SollGateway,
    private val syncQueueRepository: SollSyncQueueRepository,
) {
    fun observeRecent(limit: Int = 100): Flow<List<AssistantMemory>> =
        assistantMemoryDao.observeRecent(limit).map { memories -> memories.map { it.toDomain() } }

    suspend fun rememberAcceptedSuggestion(suggestion: ProactiveSuggestion) {
        if (!settingsRepository.assistantMemoryEnabled) return

        val now = System.currentTimeMillis()
        val key = "suggestion:${suggestion.id}"
        val memory = AssistantMemory(
            id = key.stableMemoryId(),
            category = AssistantMemoryCategory.SUGGESTION,
            key = key,
            title = suggestion.title,
            summary = "Пользователь принял предложение: ${suggestion.detail}",
            source = "home.proactive",
            confidence = suggestion.confidence,
            payloadJson = JSONObject()
                .put("suggestion_id", suggestion.id)
                .put("priority", suggestion.priority.name.lowercase())
                .put("action", suggestion.action.name.lowercase())
                .toString(),
            createdAt = now,
            updatedAt = now,
            lastUsedAt = now,
        )
        assistantMemoryDao.upsert(AssistantMemoryEntity.fromDomain(memory))
    }

    suspend fun exportAsMarkdown(): String =
        AssistantMemoryExporter.toMarkdown(
            assistantMemoryDao.getAllForExport().map { it.toDomain() }
        )

    suspend fun sendSummaryToSoll(): AssistantMemorySyncResult {
        val memories = assistantMemoryDao.getAllForExport().map { it.toDomain() }
        require(memories.isNotEmpty()) { "Память пока пуста" }

        val title = "Память Soll App ${syncTitleTimestamp()}"
        val content = AssistantMemoryExporter.toServerSummaryMarkdown(memories)
        val tags = listOf("soll-app", "assistant-memory", "summary")

        return sollGateway.createRawNote(
            title = title,
            content = content,
            tags = tags,
        ).fold(
            onSuccess = { note ->
                AssistantMemorySyncResult(
                    queued = false,
                    message = "Summary памяти отправлен в Soll: ${note.filename}",
                )
            },
            onFailure = { error ->
                syncQueueRepository.enqueueRawNote(
                    title = title,
                    content = content,
                    tags = tags,
                    reason = error.message,
                )
                AssistantMemorySyncResult(
                    queued = true,
                    message = "Сервер недоступен. Summary памяти сохранен в очередь синхронизации.",
                )
            },
        )
    }

    suspend fun delete(id: String) {
        assistantMemoryDao.deleteById(id)
    }

    suspend fun deleteAll() {
        assistantMemoryDao.deleteAll()
    }

    private fun String.stableMemoryId(): String =
        lowercase().replace(Regex("[^a-z0-9_.:-]"), "_")

    private fun syncTitleTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
