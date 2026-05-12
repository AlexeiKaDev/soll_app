package com.soll.data.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.soll.SollApplication
import com.soll.data.repository.CourseProgramRepository
import com.soll.presentation.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CourseReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun courseProgramRepository(): CourseProgramRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CourseReminderScheduler.ACTION_REMIND) return
        val courseId = intent.getLongExtra(CourseReminderScheduler.EXTRA_COURSE_ID, -1L)
        val sessionType = intent.getStringExtra(CourseReminderScheduler.EXTRA_SESSION_TYPE) ?: return
        if (courseId <= 0) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java
        )
        val repository = entryPoint.courseProgramRepository()

        runBlocking {
            val activeCourse = repository.getActiveCourseSnapshot()
            if (activeCourse == null || activeCourse.id != courseId) {
                Timber.d("Skip reminder: no active course or mismatched id")
                return@runBlocking
            }
            val current = repository.getCurrentDayPlan(courseId) ?: return@runBlocking
            val plan = current.first
            val progress = current.second
            val payload = when (sessionType) {
                "morning" -> repository.decodeSessionPayload(plan.morningPayloadJson)
                else -> repository.decodeSessionPayload(plan.eveningPayloadJson)
            } ?: return@runBlocking

            val alreadyDone = when (sessionType) {
                "morning" -> progress?.morningCompletedAtMillis != null
                else -> progress?.eveningCompletedAtMillis != null
            }
            if (alreadyDone || progress?.skippedAtMillis != null) {
                Timber.d("Skip reminder: session already done or day skipped")
                return@runBlocking
            }

            val tapIntent = Intent(context, MainActivity::class.java)
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                (courseId.toInt() * 17) + sessionType.hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = when (sessionType) {
                "morning" -> "Утренний блок"
                else -> "Вечерний блок"
            }

            val notification = NotificationCompat.Builder(context, SollApplication.COURSE_REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("$title • ${activeCourse.title}")
                .setContentText(payload.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.summary))
                .setContentIntent(tapPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify((courseId.toInt() * 13) + sessionType.hashCode(), notification)
        }
    }
}
