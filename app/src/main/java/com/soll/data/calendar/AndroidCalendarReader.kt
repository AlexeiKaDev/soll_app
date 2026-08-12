package com.soll.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.soll.domain.soll.SollCalendarEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarReadResult(
    val timezone: String,
    val events: List<SollCalendarEvent>,
)

@Singleton
class AndroidCalendarReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    fun readUpcoming(days: Long = 14): CalendarReadResult {
        check(hasPermission()) { "Нет разрешения на чтение календаря" }
        val now = Instant.now()
        val until = now.plus(days.coerceIn(1, 30), ChronoUnit.DAYS)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            android.content.ContentUris.appendId(this, now.toEpochMilli())
            android.content.ContentUris.appendId(this, until.toEpochMilli())
        }.build()
        val events = buildList {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                while (cursor.moveToNext() && size < 200) {
                    val startMillis = cursor.getLong(beginIndex)
                    val endMillis = cursor.getLong(endIndex)
                    add(
                        SollCalendarEvent(
                            eventId = calendarOccurrenceId(
                                eventId = cursor.getLong(idIndex),
                                startMillis = startMillis,
                            ),
                            title = cursor.getString(titleIndex)?.trim().orEmpty().ifBlank { "Событие" },
                            startAt = Instant.ofEpochMilli(startMillis).toString(),
                            endAt = if (endMillis > 0) Instant.ofEpochMilli(endMillis).toString() else "",
                            allDay = cursor.getInt(allDayIndex) == 1,
                            location = cursor.getString(locationIndex)?.trim().orEmpty(),
                        )
                    )
                }
            }
        }
        return CalendarReadResult(TimeZone.getDefault().id, events)
    }
}

internal fun calendarOccurrenceId(eventId: Long, startMillis: Long): String =
    "$eventId@$startMillis"
