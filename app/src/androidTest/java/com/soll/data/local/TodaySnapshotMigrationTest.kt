package com.soll.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soll.di.AppModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodaySnapshotMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation,
        SollDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Before
    fun clearDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate24To25KeepsDurableFeedImportQueueAndCreatesTodaySnapshot() {
        migrationHelper.createDatabase(DATABASE_NAME, 24).use { database ->
            database.execSQL(
                """
                INSERT INTO sync_queue (
                    id, kind, status, payload_json, attempts, last_error,
                    created_at, updated_at, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    "feed-import:share-1",
                    "FEED_IMPORT",
                    "PENDING",
                    "{\"url\":\"https://example.com/article\",\"client_id\":\"share-1\"}",
                    0,
                    null,
                    1_000L,
                    1_000L,
                    0L,
                ),
            )
        }

        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            25,
            true,
            AppModule.migration24To25,
        ).use { database ->
            database.query(
                "SELECT kind, status, payload_json FROM sync_queue WHERE id = ?",
                arrayOf("feed-import:share-1"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FEED_IMPORT", cursor.getString(0))
                assertEquals("PENDING", cursor.getString(1))
                assertTrue(cursor.getString(2).contains("example.com/article"))
            }
            database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'today_snapshots'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("today_snapshots", cursor.getString(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "today-snapshot-migration-test"
    }
}
