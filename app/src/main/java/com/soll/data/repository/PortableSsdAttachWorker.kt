package com.soll.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import com.soll.data.notification.SollNotificationChannels
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import com.soll.domain.portablessd.PortableSsdAttachNotificationPolicy
import com.soll.domain.portablessd.PortableSsdAttachNotice
import com.soll.domain.portablessd.PortableSsdAttachNoticeKind
import com.soll.presentation.navigation.AppLaunchTargets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.json.JSONObject
import timber.log.Timber

class PortableSsdAttachWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PortableSsdAttachWorkerEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        val snapshot = entryPoint.portableSsdRepository().refresh()
        val notice = PortableSsdAttachNotificationPolicy.noticeFor(
            snapshot = snapshot,
            hasSelectedRoot = settings.portableSsdTreeUri?.isNotBlank() == true,
        ) ?: return Result.success()

        val now = System.currentTimeMillis()
        if (now - settings.portableSsdLastAttachNoticeAt < ATTACH_NOTICE_COOLDOWN_MS) {
            Timber.d("Portable SSD attach notification suppressed by cooldown")
            return Result.success()
        }

        entryPoint.notificationCenter().post(notice.toNotificationRequest(snapshotRoot = snapshot.rootLabel))
        settings.portableSsdLastAttachNoticeAt = now
        return Result.success()
    }

    private fun PortableSsdAttachNotice.toNotificationRequest(snapshotRoot: String): SollNotificationRequest =
        SollNotificationRequest(
            channel = if (kind == PortableSsdAttachNoticeKind.VERIFIED) {
                SollNotificationChannel.ALERTS
            } else if (kind == PortableSsdAttachNoticeKind.NOT_READY) {
                SollNotificationChannel.ALERTS
            } else {
                SollNotificationChannel.EVENTS
            },
            type = "portable_ssd_attach_${kind.name.lowercase()}",
            source = "portable_ssd",
            title = title,
            message = message,
            payloadJson = JSONObject()
                .put("kind", kind.name)
                .put("rootLabel", snapshotRoot)
                .toString(),
            priority = when (kind) {
                PortableSsdAttachNoticeKind.VERIFIED -> SollNotificationPriority.HIGH
                PortableSsdAttachNoticeKind.NEED_SELECTION -> SollNotificationPriority.DEFAULT
                PortableSsdAttachNoticeKind.NOT_READY -> SollNotificationPriority.DEFAULT
            },
            systemNotificationId = SollNotificationChannels.PORTABLE_SSD_NOTIFICATION_ID,
            launchSection = AppLaunchTargets.SECTION_PORTABLE_SSD,
        )

    companion object {
        const val UNIQUE_WORK_NAME = "portable_ssd_attach_probe"
        private const val ATTACH_NOTICE_COOLDOWN_MS = 30_000L
    }
}

object PortableSsdAttachWorkScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<PortableSsdAttachWorker>()
            .addTag(PortableSsdAttachWorker.UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            PortableSsdAttachWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PortableSsdAttachWorkerEntryPoint {
    fun portableSsdRepository(): PortableSsdRepository
    fun settingsRepository(): SettingsRepository
    fun notificationCenter(): SollNotificationCenter
}
