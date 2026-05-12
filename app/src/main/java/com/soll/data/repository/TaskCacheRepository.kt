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
    suspend fun replaceWith(board: SollTaskBoard) {
        val now = System.currentTimeMillis()
        taskCacheDao.replaceAll(
            (board.today + board.inbox + board.stale + board.doneRecent)
                .distinctBy { it.id }
                .map { task -> TaskCacheEntity.fromDomain(task, now) }
        )
    }

    suspend fun getCachedBoard(): SollTaskBoard {
        val tasks = taskCacheDao.getAll().map { it.toDomain() }
        return SollTaskBoard(
            today = tasks.filter { it.status in TODAY_STATUSES },
            inbox = tasks.filter { it.status == "inbox" },
            stale = tasks.filter { it.status == "stale" },
            doneRecent = tasks.filter { it.status == "done" },
        )
    }

    private companion object {
        val TODAY_STATUSES = setOf("today", "in_progress", "blocked")
    }
}
