package com.soll.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.soll.data.local.dao.AppNotificationDao
import com.soll.data.local.dao.AssistantEventDao
import com.soll.data.local.dao.AssistantMemoryDao
import com.soll.data.local.dao.BookDao
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.dao.BreathingSessionDao
import com.soll.data.local.dao.CommandLogDao
import com.soll.data.local.dao.DeviceDao
import com.soll.data.local.dao.FieldPointDao
import com.soll.data.local.dao.MessageLogDao
import com.soll.data.local.dao.MusicDao
import com.soll.data.local.dao.NoteDao
import com.soll.data.local.dao.ScanDao
import com.soll.data.local.dao.SyncQueueDao
import com.soll.data.local.dao.TaskCacheDao
import com.soll.data.local.dao.ToolJobDao
import com.soll.data.local.entity.AppNotificationEntity
import com.soll.data.local.entity.AssistantEventEntity
import com.soll.data.local.entity.AssistantMemoryEntity
import com.soll.data.local.entity.BookEntity
import com.soll.data.local.entity.BreathingSessionEntity
import com.soll.data.local.entity.BotConfigEntity
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.local.entity.DeviceEventEntity
import com.soll.data.local.entity.DeviceProfileEntity
import com.soll.data.local.entity.FieldPointEntity
import com.soll.data.local.entity.KnownDeviceEntity
import com.soll.data.local.entity.MessageLogEntity
import com.soll.data.local.entity.MusicPlaybackStateEntity
import com.soll.data.local.entity.MusicPlaylistEntity
import com.soll.data.local.entity.MusicPlaylistTrackEntity
import com.soll.data.local.entity.MusicSourceEntity
import com.soll.data.local.entity.MusicSourceTrackEntity
import com.soll.data.local.entity.MusicTrackEntity
import com.soll.data.local.entity.NoteAttachmentEntity
import com.soll.data.local.entity.NoteEntity
import com.soll.data.local.entity.ScanItemEntity
import com.soll.data.local.entity.ScanSessionEntity
import com.soll.data.local.entity.SyncQueueEntity
import com.soll.data.local.entity.TaskCacheEntity
import com.soll.data.local.entity.ToolJobEntity

@Database(
    entities = [
        BotConfigEntity::class,
        MessageLogEntity::class,
        CommandLogEntity::class,
        BookEntity::class,
        BreathingSessionEntity::class,
        AssistantEventEntity::class,
        ToolJobEntity::class,
        SyncQueueEntity::class,
        TaskCacheEntity::class,
        DeviceProfileEntity::class,
        KnownDeviceEntity::class,
        DeviceEventEntity::class,
        MusicSourceEntity::class,
        MusicTrackEntity::class,
        MusicSourceTrackEntity::class,
        MusicPlaybackStateEntity::class,
        MusicPlaylistEntity::class,
        MusicPlaylistTrackEntity::class,
        ScanSessionEntity::class,
        ScanItemEntity::class,
        AppNotificationEntity::class,
        AssistantMemoryEntity::class,
        NoteEntity::class,
        NoteAttachmentEntity::class,
        FieldPointEntity::class,
    ],
    version = 23,
    exportSchema = true,
)
abstract class SollDatabase : RoomDatabase() {

    abstract fun botConfigDao(): BotConfigDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun commandLogDao(): CommandLogDao
    abstract fun assistantEventDao(): AssistantEventDao
    abstract fun assistantMemoryDao(): AssistantMemoryDao
    abstract fun toolJobDao(): ToolJobDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun taskCacheDao(): TaskCacheDao
    abstract fun deviceDao(): DeviceDao
    abstract fun musicDao(): MusicDao
    abstract fun scanDao(): ScanDao
    abstract fun bookDao(): BookDao
    abstract fun appNotificationDao(): AppNotificationDao
    abstract fun noteDao(): NoteDao
    abstract fun fieldPointDao(): FieldPointDao

    abstract fun breathingSessionDao(): BreathingSessionDao

    companion object {
        const val DATABASE_NAME = "soll_database"
    }
}
