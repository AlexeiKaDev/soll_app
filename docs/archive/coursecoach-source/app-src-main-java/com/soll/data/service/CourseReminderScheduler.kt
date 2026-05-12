package com.soll.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.soll.data.local.entity.CourseEntity
import com.soll.data.local.entity.CourseReminderEntity
import java.time.LocalDateTime
import java.time.ZoneId
import timber.log.Timber

object CourseReminderScheduler {

    const val ACTION_REMIND = "com.soll.coursecoach.REMIND"
    const val EXTRA_COURSE_ID = "extra_course_id"
    const val EXTRA_SESSION_TYPE = "extra_session_type"

    fun scheduleCourse(
        context: Context,
        course: CourseEntity,
        reminders: List<CourseReminderEntity>,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        reminders.forEach { reminder ->
            val pendingIntent = buildPendingIntent(context, course.id, reminder.sessionType)
            alarmManager.cancel(pendingIntent)
            if (!reminder.enabled) {
                pendingIntent.cancel()
                return@forEach
            }

            val triggerAtMillis = nextTriggerMillis(reminder.hour, reminder.minute)
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent,
            )
            Timber.d(
                "Scheduled reminder courseId=%s type=%s at %02d:%02d",
                course.id,
                reminder.sessionType,
                reminder.hour,
                reminder.minute,
            )
        }
    }

    private fun buildPendingIntent(
        context: Context,
        courseId: Long,
        sessionType: String,
    ): PendingIntent {
        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_COURSE_ID, courseId)
            putExtra(EXTRA_SESSION_TYPE, sessionType)
        }
        val requestCode = (courseId.toInt() * 31) + sessionType.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        var dt = LocalDateTime.now(zone)
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (dt.isBefore(LocalDateTime.now(zone).plusMinutes(1))) {
            dt = dt.plusDays(1)
        }
        return dt.atZone(zone).toInstant().toEpochMilli()
    }
}
