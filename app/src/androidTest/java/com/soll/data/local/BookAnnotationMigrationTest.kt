package com.soll.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.soll.data.local.entity.BookAnnotationEntity
import com.soll.di.AppModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookAnnotationMigrationTest {
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
    fun migrate25To26CreatesUsableAnnotationStore() {
        migrationHelper.createDatabase(DATABASE_NAME, 25).close()
        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            26,
            true,
            AppModule.migration25To26,
        ).use { database ->
            database.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_book_annotations_bookId_chapterIndex_startOffset'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        }

        val database = Room.databaseBuilder(context, SollDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppModule.migration25To26)
            .build()
        try {
            runBlocking {
                val dao = database.bookAnnotationDao()
                val later = annotation(id = "later", chapterIndex = 2, startOffset = 40)
                val earlier = annotation(id = "earlier", chapterIndex = 1, startOffset = 10)

                dao.upsert(later)
                dao.upsert(earlier)

                assertEquals(listOf("earlier", "later"), dao.getForBook(BOOK_ID).map { it.id })
                assertTrue(dao.getForBook(BOOK_ID + 1).isEmpty())

                dao.delete(earlier)
                assertEquals(listOf("later"), dao.getForBook(BOOK_ID).map { it.id })
            }
        } finally {
            database.close()
        }
    }

    private fun annotation(
        id: String,
        chapterIndex: Int,
        startOffset: Int,
    ) = BookAnnotationEntity(
        id = id,
        bookId = BOOK_ID,
        kind = "highlight",
        chapterIndex = chapterIndex,
        startOffset = startOffset,
        endOffset = startOffset + 5,
        selectedText = id,
        color = "blue",
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private companion object {
        const val DATABASE_NAME = "book-annotation-migration-test"
        const val BOOK_ID = 42L
    }
}
