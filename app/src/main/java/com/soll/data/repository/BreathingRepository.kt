package com.soll.data.repository

import com.soll.data.local.dao.BreathingSessionDao
import com.soll.data.local.entity.BreathingSessionEntity
import com.soll.domain.breathing.BreathingWeekDayStat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BreathingRepository @Inject constructor(
    private val dao: BreathingSessionDao,
) {

    fun observeRecentSessions(limit: Int = 40): Flow<List<BreathingSessionEntity>> =
        dao.observeRecent(limit)

    suspend fun insertSession(session: BreathingSessionEntity) {
        dao.insert(session)
    }

    /**
     * Последние 7 календарных дней включая сегодня; суммирует длительность по [BreathingSessionEntity.durationSeconds].
     */
    suspend fun weekStatsEndingToday(zoneId: ZoneId = ZoneId.systemDefault()): List<BreathingWeekDayStat> {
        val today = LocalDate.now(zoneId)
        val startDay = today.minusDays(6)
        val sinceMillis = startDay.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val sessions = dao.sessionsEndedSince(sinceMillis)
        val secondsByDay = mutableMapOf<LocalDate, Int>()
        for (s in sessions) {
            val date = Instant.ofEpochMilli(s.endedAtMillis).atZone(zoneId).toLocalDate()
            if (!date.isBefore(startDay) && !date.isAfter(today)) {
                secondsByDay[date] = (secondsByDay[date] ?: 0) + s.durationSeconds.coerceAtLeast(0)
            }
        }
        val ruShort = arrayOf("", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        return (0..6).map { offset ->
            val d = startDay.plusDays(offset.toLong())
            val minutes = (secondsByDay[d] ?: 0) / 60f
            val label = ruShort[d.dayOfWeek.value.coerceIn(1, 7)]
            BreathingWeekDayStat(label = label, minutes = minutes)
        }
    }
}
