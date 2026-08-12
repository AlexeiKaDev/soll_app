package com.soll.data.repository

import com.squareup.moshi.Moshi
import com.soll.data.local.dao.TodaySnapshotDao
import com.soll.data.local.entity.TodaySnapshotEntity
import com.soll.domain.soll.SollCalendarEvent
import com.soll.domain.soll.SollCalendarSnapshot
import com.soll.domain.soll.SollFeedPage
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollTodaySnapshot
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyIntelligenceRepository @Inject constructor(
    private val gateway: SollGateway,
    private val todaySnapshotDao: TodaySnapshotDao,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(SollTodaySnapshot::class.java)

    suspend fun cachedToday(): SollTodaySnapshot? = todaySnapshotDao.get()?.let { entity ->
        runCatching { adapter.fromJson(entity.payloadJson) }.getOrNull()
    }

    suspend fun refreshToday(): Result<SollTodaySnapshot> {
        val result = gateway.getTodayIntelligence()
        result.getOrNull()?.let { snapshot ->
            todaySnapshotDao.put(
                TodaySnapshotEntity(
                    payloadJson = adapter.toJson(snapshot),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        return result
    }

    suspend fun feed(
        limit: Int = 30,
        cursor: String = "",
        category: String = "",
    ): Result<SollFeedPage> = gateway.getPersonalFeed(limit, cursor, category).also { result ->
        result.fold(
            onSuccess = { page ->
                Timber.i(
                    "Soll personal feed loaded: items=%d total=%d hasMore=%s",
                    page.items.size,
                    page.total,
                    page.hasMore,
                )
            },
            onFailure = { error ->
                Timber.w(
                    "Soll personal feed failed: type=%s message=%s",
                    error::class.java.simpleName,
                    error.message.orEmpty().take(160),
                )
            },
        )
    }

    suspend fun feedback(
        entityId: String,
        decision: String,
        topic: String,
        source: String,
    ): Result<Boolean> = gateway.sendFeedFeedback(entityId, decision, topic, source)

    suspend fun syncCalendar(
        timezone: String,
        events: List<SollCalendarEvent>,
    ): Result<SollCalendarSnapshot> = try {
        gateway.syncCalendarSnapshot(timezone, events)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
