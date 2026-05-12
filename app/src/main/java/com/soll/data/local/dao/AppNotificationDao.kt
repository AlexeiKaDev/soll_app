package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY created_at DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<AppNotificationEntity>>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE status = 'UNREAD'")
    fun getUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AppNotificationEntity)

    @Query(
        """
        UPDATE app_notifications
        SET shown_at = :shownAt, system_notification_id = :systemNotificationId
        WHERE id = :id
        """
    )
    suspend fun markShown(id: String, shownAt: Long, systemNotificationId: Int)

    @Query(
        """
        UPDATE app_notifications
        SET status = 'READ', read_at = :readAt
        WHERE id = :id AND status != 'DISMISSED'
        """
    )
    suspend fun markRead(id: String, readAt: Long)

    @Query(
        """
        UPDATE app_notifications
        SET status = 'READ', read_at = :readAt
        WHERE status = 'UNREAD'
        """
    )
    suspend fun markAllRead(readAt: Long)

    @Query(
        """
        UPDATE app_notifications
        SET status = 'DISMISSED', dismissed_at = :dismissedAt
        WHERE id = :id
        """
    )
    suspend fun dismiss(id: String, dismissedAt: Long)

    @Query("DELETE FROM app_notifications")
    suspend fun deleteAll()

    @Query("DELETE FROM app_notifications WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
