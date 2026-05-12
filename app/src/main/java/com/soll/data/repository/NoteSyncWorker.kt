package com.soll.data.repository

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class NoteSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NoteSyncWorkerEntryPoint::class.java,
        )
        val summary = entryPoint.noteRepository().syncPending(limit = 20)
        return noteSyncWorkDecision(summary).toWorkerResult()
    }
}

internal enum class SyncWorkDecision {
    SUCCESS,
    RETRY,
}

internal fun noteSyncWorkDecision(summary: NoteSyncSummary): SyncWorkDecision =
    if (summary.failed > 0) SyncWorkDecision.RETRY else SyncWorkDecision.SUCCESS

internal fun SyncWorkDecision.toWorkerResult(): Result = when (this) {
    SyncWorkDecision.SUCCESS -> Result.success()
    SyncWorkDecision.RETRY -> Result.retry()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NoteSyncWorkerEntryPoint {
    fun noteRepository(): NoteRepository
}
