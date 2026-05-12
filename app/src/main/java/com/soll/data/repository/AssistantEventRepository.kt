package com.soll.data.repository

import com.soll.data.local.dao.AssistantEventDao
import com.soll.data.local.entity.AssistantEventEntity
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.AssistantEventSummaryExporter
import com.soll.domain.soll.SollGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantEventSummarySyncResult(
    val queued: Boolean,
    val message: String,
)

@Singleton
class AssistantEventRepository @Inject constructor(
    private val assistantEventDao: AssistantEventDao,
    private val sollGateway: SollGateway,
    private val syncQueueRepository: SollSyncQueueRepository,
) : AssistantEventLogger {
    fun getRecentEvents(limit: Int = 100): Flow<List<AssistantEvent>> =
        assistantEventDao.getRecentEvents(limit).map { events ->
            events.map { it.toDomain() }
        }

    suspend fun sendSafeSummaryToSoll(limit: Int = 100): AssistantEventSummarySyncResult {
        val events = assistantEventDao.getRecentEventsSnapshot(limit).map { it.toDomain() }
        require(events.isNotEmpty()) { "Событий ассистента пока нет" }

        val title = "События Soll App ${syncTitleTimestamp()}"
        val content = AssistantEventSummaryExporter.toServerSummaryMarkdown(events)
        val tags = listOf("soll-app", "assistant-events", "summary")

        return sollGateway.createRawNote(
            title = title,
            content = content,
            tags = tags,
        ).fold(
            onSuccess = { note ->
                AssistantEventSummarySyncResult(
                    queued = false,
                    message = "Summary событий отправлен в Soll: ${note.filename}",
                )
            },
            onFailure = { error ->
                syncQueueRepository.enqueueRawNote(
                    title = title,
                    content = content,
                    tags = tags,
                    reason = error.message,
                )
                AssistantEventSummarySyncResult(
                    queued = true,
                    message = "Сервер недоступен. Summary событий сохранен в очередь синхронизации.",
                )
            },
        )
    }

    override suspend fun logEvent(event: AssistantEvent) {
        try {
            assistantEventDao.insert(AssistantEventEntity.fromDomain(event))
        } catch (e: Exception) {
            Timber.e(e, "Failed to log assistant event")
        }
    }

    suspend fun deleteAll() {
        assistantEventDao.deleteAll()
    }

    private fun syncTitleTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
