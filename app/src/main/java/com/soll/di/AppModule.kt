package com.soll.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.soll.BuildConfig
import com.soll.data.local.SollDatabase
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
import com.soll.data.repository.AssistantEventRepository
import com.soll.data.repository.BookRepository
import com.soll.data.repository.BreathingRepository
import com.soll.data.repository.DeviceRepository
import com.soll.data.repository.MusicRepository
import com.soll.data.repository.NoteRepository
import com.soll.data.repository.ScannerRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollNotificationRepository
import com.soll.data.repository.SollRepository
import com.soll.data.repository.ToolJobRepository
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.assistant.CapabilitySettings
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.tool.ToolJobRunner
import com.soll.domain.tool.ToolJobStore
import com.soll.domain.soll.SollGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createCoreTables(db)
        }
    }

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createCoreTables(db)
            createBreathingTable(db)
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createCoreTables(db)
            createBreathingTable(db)
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `assistant_events` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `payload_json` TEXT,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_assistant_events_created_at` ON `assistant_events` (`created_at`)"
            )
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tool_jobs` (
                    `id` TEXT NOT NULL,
                    `tool_id` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `progress_percent` INTEGER,
                    `input_json` TEXT NOT NULL,
                    `output_json` TEXT,
                    `log_text` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `finished_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_jobs_created_at` ON `tool_jobs` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_jobs_status` ON `tool_jobs` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tool_jobs_tool_id` ON `tool_jobs` (`tool_id`)")
        }
    }

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_queue` (
                    `id` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `payload_json` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL,
                    `last_error` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `next_attempt_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status` ON `sync_queue` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_kind` ON `sync_queue` (`kind`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_next_attempt_at` ON `sync_queue` (`next_attempt_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_created_at` ON `sync_queue` (`created_at`)")
            createSyncQueueReliabilityIndexes(db)
        }
    }

    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `task_cache` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `source_ref` TEXT NOT NULL,
                    `project_name` TEXT,
                    `status` TEXT NOT NULL,
                    `priority` TEXT NOT NULL,
                    `due_date` TEXT,
                    `tags_json` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_cache_status` ON `task_cache` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_cache_updated_at` ON `task_cache` (`updated_at`)")
        }
    }

    private val migration8To9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `device_profiles` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `transport` TEXT NOT NULL,
                    `auth_mode` TEXT NOT NULL,
                    `command_schema_version` TEXT NOT NULL,
                    `capabilities_json` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `known_devices` (
                    `id` TEXT NOT NULL,
                    `profile_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `host` TEXT NOT NULL,
                    `port` INTEGER NOT NULL,
                    `path` TEXT NOT NULL,
                    `transport` TEXT NOT NULL,
                    `auth_mode` TEXT NOT NULL,
                    `last_status` TEXT NOT NULL,
                    `last_seen_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `device_events` (
                    `id` TEXT NOT NULL,
                    `device_id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `payload_json` TEXT,
                    `created_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_profile_id` ON `known_devices` (`profile_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_host_port` ON `known_devices` (`host`, `port`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_devices_updated_at` ON `known_devices` (`updated_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_device_events_device_id` ON `device_events` (`device_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_device_events_created_at` ON `device_events` (`created_at`)")
        }
    }

    private val migration9To10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `music_sources` (
                    `uri` TEXT NOT NULL,
                    `display_name` TEXT NOT NULL,
                    `source_type` TEXT NOT NULL,
                    `track_count` INTEGER NOT NULL,
                    `last_error` TEXT,
                    `added_at` INTEGER NOT NULL,
                    `last_scanned_at` INTEGER,
                    PRIMARY KEY(`uri`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `music_tracks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `source_uri` TEXT,
                    `uri` TEXT NOT NULL,
                    `display_name` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT,
                    `album` TEXT,
                    `duration_ms` INTEGER,
                    `mime_type` TEXT,
                    `size_bytes` INTEGER,
                    `added_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `last_played_at` INTEGER,
                    `play_count` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_music_tracks_uri` ON `music_tracks` (`uri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_source_uri` ON `music_tracks` (`source_uri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_title` ON `music_tracks` (`title`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_artist` ON `music_tracks` (`artist`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `music_playback_state` (
                    `id` TEXT NOT NULL,
                    `current_track_id` INTEGER,
                    `position_ms` INTEGER NOT NULL,
                    `shuffle_enabled` INTEGER NOT NULL,
                    `repeat_mode` TEXT NOT NULL,
                    `queue_track_ids_csv` TEXT NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    private val migration10To11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `music_source_tracks` (
                    `source_uri` TEXT NOT NULL,
                    `track_uri` TEXT NOT NULL,
                    `linked_at` INTEGER NOT NULL,
                    PRIMARY KEY(`source_uri`, `track_uri`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_source_tracks_source_uri` ON `music_source_tracks` (`source_uri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_source_tracks_track_uri` ON `music_source_tracks` (`track_uri`)")
            db.execSQL(
                """
                INSERT OR IGNORE INTO `music_source_tracks` (`source_uri`, `track_uri`, `linked_at`)
                SELECT `source_uri`, `uri`, `added_at`
                FROM `music_tracks`
                WHERE `source_uri` IS NOT NULL
                """.trimIndent()
            )
        }
    }

    private val migration11To12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            listOf(
                "course_reminders",
                "course_session_logs",
                "course_day_progress",
                "course_day_plans",
                "course_lessons",
                "course_modules",
                "courses",
            ).forEach { table ->
                db.execSQL("DROP TABLE IF EXISTS `$table`")
            }
        }
    }

    private val migration12To13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scan_sessions` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `scan_items` (
                    `id` TEXT NOT NULL,
                    `session_id` TEXT NOT NULL,
                    `raw_value` TEXT NOT NULL,
                    `normalized_value` TEXT NOT NULL,
                    `format` TEXT NOT NULL,
                    `count` INTEGER NOT NULL,
                    `first_scanned_at` INTEGER NOT NULL,
                    `last_scanned_at` INTEGER NOT NULL,
                    `exported_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_items_session_id` ON `scan_items` (`session_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_scan_items_session_id_format_normalized_value` ON `scan_items` (`session_id`, `format`, `normalized_value`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_items_last_scanned_at` ON `scan_items` (`last_scanned_at`)")
        }
    }

    private val migration13To14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `album_artist` TEXT")
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `genre` TEXT")
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `year` INTEGER")
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `track_number` INTEGER")
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `disc_number` INTEGER")
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `composer` TEXT")
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `bitrate` INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_album` ON `music_tracks` (`album`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_album_artist` ON `music_tracks` (`album_artist`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_tracks_genre` ON `music_tracks` (`genre`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `music_playlists` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `mood` TEXT,
                    `track_count` INTEGER NOT NULL,
                    `cover_seed` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_playlists_name` ON `music_playlists` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_playlists_updated_at` ON `music_playlists` (`updated_at`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `music_playlist_tracks` (
                    `playlist_id` TEXT NOT NULL,
                    `track_id` INTEGER NOT NULL,
                    `position` INTEGER NOT NULL,
                    `added_at` INTEGER NOT NULL,
                    PRIMARY KEY(`playlist_id`, `track_id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_playlist_tracks_playlist_id` ON `music_playlist_tracks` (`playlist_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_playlist_tracks_track_id` ON `music_playlist_tracks` (`track_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_music_playlist_tracks_playlist_id_position` ON `music_playlist_tracks` (`playlist_id`, `position`)")
        }
    }

    private val migration14To15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_notifications` (
                    `id` TEXT NOT NULL,
                    `channel_id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `message` TEXT NOT NULL,
                    `payload_json` TEXT,
                    `priority` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `shown_at` INTEGER,
                    `read_at` INTEGER,
                    `dismissed_at` INTEGER,
                    `system_notification_id` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_created_at` ON `app_notifications` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_status` ON `app_notifications` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_channel_id` ON `app_notifications` (`channel_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_notifications_source` ON `app_notifications` (`source`)")
        }
    }

    private val migration15To16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `assistant_memories` (
                    `id` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `memory_key` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `confidence` REAL NOT NULL,
                    `payload_json` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `last_used_at` INTEGER,
                    `pinned` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_memories_category` ON `assistant_memories` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_memories_source` ON `assistant_memories` (`source`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_assistant_memories_updated_at` ON `assistant_memories` (`updated_at`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_assistant_memories_category_memory_key` ON `assistant_memories` (`category`, `memory_key`)")
        }
    }

    private val migration16To17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notes` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `tags_json` TEXT NOT NULL,
                    `color_key` TEXT NOT NULL,
                    `pinned` INTEGER NOT NULL,
                    `archived` INTEGER NOT NULL,
                    `deleted` INTEGER NOT NULL,
                    `sync_status` TEXT NOT NULL,
                    `sync_attempts` INTEGER NOT NULL,
                    `next_sync_attempt_at` INTEGER NOT NULL,
                    `synced_filename` TEXT,
                    `synced_path` TEXT,
                    `last_error` TEXT,
                    `source` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `synced_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updated_at` ON `notes` (`updated_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_created_at` ON `notes` (`created_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_sync_status` ON `notes` (`sync_status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_pinned` ON `notes` (`pinned`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived` ON `notes` (`archived`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_deleted` ON `notes` (`deleted`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `note_attachments` (
                    `id` TEXT NOT NULL,
                    `note_id` TEXT NOT NULL,
                    `local_path` TEXT NOT NULL,
                    `display_name` TEXT NOT NULL,
                    `mime_type` TEXT,
                    `size_bytes` INTEGER,
                    `sync_status` TEXT NOT NULL,
                    `sync_attempts` INTEGER NOT NULL,
                    `next_sync_attempt_at` INTEGER NOT NULL,
                    `uploaded_filename` TEXT,
                    `uploaded_path` TEXT,
                    `last_error` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachments_note_id` ON `note_attachments` (`note_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachments_sync_status` ON `note_attachments` (`sync_status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachments_created_at` ON `note_attachments` (`created_at`)")
        }
    }

    private val migration17To18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `field_points` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `accuracy_meters` REAL,
                    `source` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `task_id` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `visited_at` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_points_status` ON `field_points` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_points_updated_at` ON `field_points` (`updated_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_points_task_id` ON `field_points` (`task_id`)")
        }
    }

    private val migration18To19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createReliabilityIndexes(db)
        }
    }

    private val migration19To20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `approval_id` TEXT")
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `tool_job_id` TEXT")
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `execution_state` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `outcome_artifacts_json` TEXT NOT NULL DEFAULT '[]'")
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `value_metric` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `branch` TEXT NOT NULL DEFAULT 'innovation'")
            db.execSQL("ALTER TABLE `task_cache` ADD COLUMN `pair_id` TEXT")
        }
    }

    private val migration20To21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_notifications` ADD COLUMN `dedupe_key` TEXT")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_app_notifications_dedupe_key` " +
                    "ON `app_notifications` (`dedupe_key`)"
            )
        }
    }

    private const val ENCRYPTED_PREFS_NAME = "soll_secure_prefs"

    private fun createCoreTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `bot_configs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `token` TEXT NOT NULL,
                `is_active` INTEGER NOT NULL,
                `last_offset` INTEGER NOT NULL,
                `bot_username` TEXT,
                `bot_id` INTEGER,
                `created_at` INTEGER NOT NULL,
                `last_used_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `message_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `update_id` INTEGER NOT NULL,
                `message_id` INTEGER NOT NULL,
                `chat_id` INTEGER NOT NULL,
                `chat_type` TEXT NOT NULL,
                `chat_title` TEXT,
                `user_id` INTEGER,
                `username` TEXT,
                `user_full_name` TEXT,
                `text` TEXT,
                `has_document` INTEGER NOT NULL,
                `has_photo` INTEGER NOT NULL,
                `has_location` INTEGER NOT NULL,
                `message_date` INTEGER NOT NULL,
                `received_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `command_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `command` TEXT NOT NULL,
                `args` TEXT,
                `chat_id` INTEGER NOT NULL,
                `user_id` INTEGER,
                `username` TEXT,
                `status` TEXT NOT NULL,
                `error_message` TEXT,
                `response_text` TEXT,
                `execution_time_ms` INTEGER,
                `executed_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `books` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `author` TEXT,
                `filePath` TEXT NOT NULL,
                `coverPath` TEXT,
                `totalChapters` INTEGER NOT NULL,
                `currentChapter` INTEGER NOT NULL,
                `currentPosition` INTEGER NOT NULL,
                `lastReadAt` INTEGER NOT NULL,
                `addedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        createLogReliabilityIndexes(db)
    }

    private fun createReliabilityIndexes(db: SupportSQLiteDatabase) {
        createLogReliabilityIndexes(db)
        createSyncQueueReliabilityIndexes(db)
    }

    private fun createLogReliabilityIndexes(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_logs_update_id` ON `message_logs` (`update_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_logs_received_at` ON `message_logs` (`received_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_logs_chat_id_received_at` ON `message_logs` (`chat_id`, `received_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_command_logs_executed_at` ON `command_logs` (`executed_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_command_logs_command_executed_at` ON `command_logs` (`command`, `executed_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_command_logs_status_executed_at` ON `command_logs` (`status`, `executed_at`)")
    }

    private fun createSyncQueueReliabilityIndexes(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_updated_at` ON `sync_queue` (`updated_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_queue_status_next_attempt_at_created_at` ON `sync_queue` (`status`, `next_attempt_at`, `created_at`)")
    }

    private fun createBreathingTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `breathing_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `startedAtMillis` INTEGER NOT NULL,
                `endedAtMillis` INTEGER NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `completedFully` INTEGER NOT NULL,
                `roundsCompleted` INTEGER NOT NULL,
                `holdRecordsCsv` TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val tokenRegex = Regex("""bot\d+:(?:[A-Za-z0-9_-]{20,})""")
        val encodedTokenRegex = Regex("""bot\d+%3A(?:[A-Za-z0-9_-]{20,})""")
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            val sanitized = message
                .replace(tokenRegex, "bot<redacted>")
                .replace(encodedTokenRegex, "bot<redacted>")
            Timber.tag("OkHttp").d(sanitized)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SollDatabase =
        Room.databaseBuilder(
            context,
            SollDatabase::class.java,
            "soll_database"
        )
            .addMigrations(
                migration1To2,
                migration2To3,
                migration3To4,
                migration4To5,
                migration5To6,
                migration6To7,
                migration7To8,
                migration8To9,
                migration9To10,
                migration10To11,
                migration11To12,
                migration12To13,
                migration13To14,
                migration14To15,
                migration15To16,
                migration16To17,
                migration17To18,
                migration18To19,
                migration19To20,
                migration20To21,
            )
            .build()

    @Provides
    @Singleton
    fun provideBotConfigDao(database: SollDatabase): BotConfigDao =
        database.botConfigDao()

    @Provides
    @Singleton
    fun provideMessageLogDao(database: SollDatabase): MessageLogDao =
        database.messageLogDao()

    @Provides
    @Singleton
    fun provideCommandLogDao(database: SollDatabase): CommandLogDao =
        database.commandLogDao()

    @Provides
    @Singleton
    fun provideSyncQueueDao(database: SollDatabase): SyncQueueDao =
        database.syncQueueDao()

    @Provides
    @Singleton
    fun provideTaskCacheDao(database: SollDatabase): TaskCacheDao =
        database.taskCacheDao()

    @Provides
    @Singleton
    fun provideDeviceDao(database: SollDatabase): DeviceDao =
        database.deviceDao()

    @Provides
    @Singleton
    fun provideMusicDao(database: SollDatabase): MusicDao =
        database.musicDao()

    @Provides
    @Singleton
    fun provideScanDao(database: SollDatabase): ScanDao =
        database.scanDao()

    @Provides
    @Singleton
    fun provideAssistantEventDao(database: SollDatabase): AssistantEventDao =
        database.assistantEventDao()

    @Provides
    @Singleton
    fun provideAssistantMemoryDao(database: SollDatabase): AssistantMemoryDao =
        database.assistantMemoryDao()

    @Provides
    @Singleton
    fun provideAppNotificationDao(database: SollDatabase): AppNotificationDao =
        database.appNotificationDao()

    @Provides
    @Singleton
    fun provideNoteDao(database: SollDatabase): NoteDao =
        database.noteDao()

    @Provides
    @Singleton
    fun provideFieldPointDao(database: SollDatabase): FieldPointDao =
        database.fieldPointDao()

    @Provides
    @Singleton
    fun provideToolJobDao(database: SollDatabase): ToolJobDao =
        database.toolJobDao()

    @Provides
    @Singleton
    fun provideBookDao(database: SollDatabase): BookDao =
        database.bookDao()

    @Provides
    @Singleton
    fun provideBreathingSessionDao(database: SollDatabase): BreathingSessionDao =
        database.breathingSessionDao()

    @Provides
    @Singleton
    fun provideBreathingRepository(dao: BreathingSessionDao): BreathingRepository =
        BreathingRepository(dao)

    @Provides
    @Singleton
    fun provideDeviceRepository(deviceDao: DeviceDao): DeviceRepository =
        DeviceRepository(deviceDao)

    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        musicDao: MusicDao,
        settingsRepository: SettingsRepository,
    ): MusicRepository = MusicRepository(context, musicDao, settingsRepository)

    @Provides
    @Singleton
    fun provideScannerRepository(scanDao: ScanDao): ScannerRepository =
        ScannerRepository(scanDao)

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        sharedPreferences: android.content.SharedPreferences,
        botConfigDao: BotConfigDao
    ): SettingsRepository = SettingsRepository(sharedPreferences, botConfigDao)

    @Provides
    @Singleton
    fun provideCapabilitySettings(settingsRepository: SettingsRepository): CapabilitySettings =
        settingsRepository

    @Provides
    @Singleton
    fun provideAssistantEventLogger(repository: AssistantEventRepository): AssistantEventLogger =
        repository

    @Provides
    @Singleton
    fun provideSollNotificationCenter(repository: SollNotificationRepository): SollNotificationCenter =
        repository

    @Provides
    @Singleton
    fun provideToolJobStore(repository: ToolJobRepository): ToolJobStore =
        repository

    @Provides
    @Singleton
    fun provideSollGateway(repository: SollRepository): SollGateway =
        repository

    @Provides
    @Singleton
    fun provideBookRepository(
        @ApplicationContext context: Context,
        bookDao: BookDao
    ): BookRepository = BookRepository(context, bookDao)
}
