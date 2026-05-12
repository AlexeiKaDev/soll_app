package com.soll.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soll.domain.soll.SollGateway
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

class GadgetServerSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            GadgetServerSyncWorkerEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return Result.success()
        }

        val gateway = entryPoint.sollGateway()
        return gateway.getGadgetSnapshots().fold(
            onSuccess = { snapshots ->
                val deviceRepository = entryPoint.deviceRepository()
                deviceRepository.persistServerSnapshots(snapshots)
                snapshots.filter { it.enabled }.forEach { snapshot ->
                    gateway.getGadgetEvents(snapshot.id, limit = 50).onSuccess { events ->
                        deviceRepository.persistServerEvents(events)
                    }
                }
                Result.success()
            },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "gadget_server_sync"
    }
}

object GadgetServerSyncScheduler {
    fun schedule(context: Context, settingsRepository: SettingsRepository) {
        if (settingsRepository.sollServerUrl.isBlank()) {
            WorkManager.getInstance(context).cancelUniqueWork(GadgetServerSyncWorker.UNIQUE_WORK_NAME)
            return
        }
        val intervalMinutes = settingsRepository.sollSyncIntervalMinutes.coerceAtLeast(15)
        val networkType = if (settingsRepository.sollWifiOnlyUpload) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val request = PeriodicWorkRequestBuilder<GadgetServerSyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            GadgetServerSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GadgetServerSyncWorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun sollGateway(): SollGateway
    fun deviceRepository(): DeviceRepository
}
