package com.soll.data.repository

import com.soll.data.local.dao.TaskCacheDao
import com.soll.data.local.entity.TaskCacheEntity
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
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
    today + inbox + stale + doneRecent

private fun SollTaskBoard.withPendingStatuses(pendingStatuses: Map<String, String>): SollTaskBoard =
    allTasks()
        .withPendingStatuses(pendingStatuses)
        .toBoard()

private fun List<SollTask>.withPendingStatuses(pendingStatuses: Map<String, String>): List<SollTask> =
    if (pendingStatuses.isEmpty()) {
        this
    } else {
        map { task ->
            pendingStatuses[task.id]?.let { status -> task.copy(status = status) } ?: task
        }
    }

private fun List<SollTask>.toBoard(): SollTaskBoard =
    SollTaskBoard(
        today = filter { it.status in TODAY_STATUSES },
        inbox = filter { it.status == "inbox" },
        stale = filter { it.status == "stale" },
        doneRecent = filter { it.status == "done" },
    )

private val TODAY_STATUSES = setOf("today", "in_progress", "blocked")
