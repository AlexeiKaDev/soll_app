package com.soll.data.repository

import com.soll.data.local.dao.TaskCacheDao
import com.soll.data.local.entity.TaskCacheEntity
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import com.soll.domain.soll.SollTaskBoardCounts
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskCacheRepository @Inject constructor(
    private val taskCacheDao: TaskCacheDao,
) {
    suspend fun replaceWith(
        board: SollTaskBoard,
        pendingStatuses: Map<String, String> = emptyMap(),
    ): SollTaskBoard {
        val now = System.currentTimeMillis()
        val adjustedBoard = board.withPendingStatuses(pendingStatuses)
        taskCacheDao.replaceAll(
            adjustedBoard.allTasks()
                .distinctBy { it.id }
                .map { task -> TaskCacheEntity.fromDomain(task, now) }
        )
        return adjustedBoard
    }

    suspend fun getCachedBoard(
        pendingStatuses: Map<String, String> = emptyMap(),
    ): SollTaskBoard {
        val tasks = taskCacheDao.getAll()
            .map { it.toDomain() }
            .withPendingStatuses(pendingStatuses)
        return tasks.toBoard()
    }

    suspend fun applyOptimisticTaskStatus(
        task: SollTask,
        status: String,
        pendingStatuses: Map<String, String> = emptyMap(),
    ): SollTaskBoard {
        val now = System.currentTimeMillis()
        taskCacheDao.insertAll(
            listOf(TaskCacheEntity.fromDomain(task.copy(status = status), now))
        )
        return getCachedBoard(pendingStatuses + (task.id to status))
    }

}

private fun SollTaskBoard.allTasks(): List<SollTask> =
    today + blocked + inbox + stale + deferred + doneRecent

private fun SollTaskBoard.withPendingStatuses(pendingStatuses: Map<String, String>): SollTaskBoard =
    allTasks()
        .withPendingStatuses(pendingStatuses)
        .toBoard(counts = counts, limitPerSection = limitPerSection)

private fun List<SollTask>.withPendingStatuses(pendingStatuses: Map<String, String>): List<SollTask> =
    if (pendingStatuses.isEmpty()) {
        this
    } else {
        map { task ->
            pendingStatuses[task.id]?.let { status -> task.copy(status = status) } ?: task
        }
    }

private fun List<SollTask>.toBoard(
    counts: SollTaskBoardCounts? = null,
    limitPerSection: Int? = null,
): SollTaskBoard =
    SollTaskBoard(
        today = filter { it.status in TODAY_STATUSES },
        blocked = filter { it.status == "blocked" },
        inbox = filter { it.status == "inbox" },
        stale = filter { it.status == "stale" },
        deferred = filter { it.status == "deferred" },
        doneRecent = filter { it.status == "done" },
        counts = counts,
        limitPerSection = limitPerSection,
    )

private val TODAY_STATUSES = setOf("today", "in_progress")
