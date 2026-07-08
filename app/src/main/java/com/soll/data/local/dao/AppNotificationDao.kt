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

    @Query("SELECT COUNT(*) FROM app_notifications WHERE status = 'UNREAD' AND channel_id = :channelId")
    suspend fun getUnreadCountForChannel(channelId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AppNotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(notification: AppNotificationEntity): Long

    @Query("SELECT * FROM app_notifications WHERE dedupe_key = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): AppNotificationEntity?

    @Query(
        """
        SELECT DISTINCT system_notification_id
        FROM app_notifications
        WHERE channel_id = :channelId AND system_notification_id IS NOT NULL
        """
    )
    suspend fun getSystemNotificationIdsForChannel(channelId: String): List<Int>

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
