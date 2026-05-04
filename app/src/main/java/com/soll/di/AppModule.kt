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
import com.soll.data.api.TelegramApiService
import com.soll.data.local.SollDatabase
import com.soll.data.local.dao.BookDao
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.dao.BreathingSessionDao
import com.soll.data.local.dao.CommandLogDao
import com.soll.data.local.dao.CourseProgramDao
import com.soll.data.local.dao.MessageLogDao
import com.soll.data.repository.BookRepository
import com.soll.data.repository.BreathingRepository
import com.soll.data.repository.CourseProgramRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `courses` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `slug` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `version` TEXT NOT NULL,
                    `reviewStatus` TEXT NOT NULL,
                    `contentQuality` TEXT,
                    `mascotStyle` TEXT,
                    `sourceFolder` TEXT NOT NULL,
                    `packageFileName` TEXT NOT NULL,
                    `totalDays` INTEGER NOT NULL,
                    `installedAt` INTEGER NOT NULL,
                    `lastUpdatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_courses_slug` ON `courses` (`slug`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_modules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `summary` TEXT NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_modules_courseId` ON `course_modules` (`courseId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_modules_courseId_moduleKey` ON `course_modules` (`courseId`, `moduleKey`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_lessons` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `lessonKey` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `summary` TEXT NOT NULL,
                    `sourceName` TEXT NOT NULL,
                    `sourcePath` TEXT,
                    `lessonType` TEXT NOT NULL,
                    `sessionTag` TEXT,
                    `required` INTEGER NOT NULL,
                    `estimatedMinutes` INTEGER,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_lessons_courseId` ON `course_lessons` (`courseId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_lessons_courseId_lessonKey` ON `course_lessons` (`courseId`, `lessonKey`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_day_plans` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `dayIndex` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `theme` TEXT NOT NULL,
                    `morningPayloadJson` TEXT,
                    `eveningPayloadJson` TEXT,
                    `diaryPromptJson` TEXT,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_day_plans_courseId` ON `course_day_plans` (`courseId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_day_plans_courseId_dayIndex` ON `course_day_plans` (`courseId`, `dayIndex`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_day_progress` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `dayIndex` INTEGER NOT NULL,
                    `morningCompletedAtMillis` INTEGER,
                    `eveningCompletedAtMillis` INTEGER,
                    `skippedAtMillis` INTEGER,
                    `updatedAtMillis` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_day_progress_courseId` ON `course_day_progress` (`courseId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_day_progress_courseId_dayIndex` ON `course_day_progress` (`courseId`, `dayIndex`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_session_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `dayIndex` INTEGER NOT NULL,
                    `sessionType` TEXT NOT NULL,
                    `startedAtMillis` INTEGER NOT NULL,
                    `endedAtMillis` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `completedExercises` INTEGER NOT NULL,
                    `totalExercises` INTEGER NOT NULL,
                    `durationSeconds` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_session_logs_courseId` ON `course_session_logs` (`courseId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_session_logs_endedAtMillis` ON `course_session_logs` (`endedAtMillis`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `course_reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `courseId` INTEGER NOT NULL,
                    `sessionType` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    FOREIGN KEY(`courseId`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_reminders_courseId` ON `course_reminders` (`courseId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_course_reminders_courseId_sessionType` ON `course_reminders` (`courseId`, `sessionType`)")
        }
    }

    private const val TELEGRAM_API_BASE_URL = "https://api.telegram.org/"
    private const val ENCRYPTED_PREFS_NAME = "soll_secure_prefs"

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
            .readTimeout(60, TimeUnit.SECONDS) // Long polling needs longer timeout
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit = Retrofit.Builder()
        .baseUrl(TELEGRAM_API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideTelegramApiService(retrofit: Retrofit): TelegramApiService =
        retrofit.create(TelegramApiService::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SollDatabase =
        Room.databaseBuilder(
            context,
            SollDatabase::class.java,
            "soll_database"
        )
            .addMigrations(migration2To3, migration3To4)
            .fallbackToDestructiveMigration()
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
    fun provideBookDao(database: SollDatabase): BookDao =
        database.bookDao()

    @Provides
    @Singleton
    fun provideBreathingSessionDao(database: SollDatabase): BreathingSessionDao =
        database.breathingSessionDao()

    @Provides
    @Singleton
    fun provideCourseProgramDao(database: SollDatabase): CourseProgramDao =
        database.courseProgramDao()

    @Provides
    @Singleton
    fun provideBreathingRepository(dao: BreathingSessionDao): BreathingRepository =
        BreathingRepository(dao)

    @Provides
    @Singleton
    fun provideCourseProgramRepository(
        @ApplicationContext context: Context,
        database: SollDatabase,
        dao: CourseProgramDao
    ): CourseProgramRepository = CourseProgramRepository(context, database, dao)

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
    fun provideTelegramRepository(
        apiService: TelegramApiService,
        settingsRepository: SettingsRepository,
        messageLogDao: MessageLogDao,
        commandLogDao: CommandLogDao
    ): TelegramRepository = TelegramRepository(
        apiService,
        settingsRepository,
        messageLogDao,
        commandLogDao
    )

    @Provides
    @Singleton
    fun provideCommandProcessor(
        @ApplicationContext context: Context,
        telegramRepository: TelegramRepository
    ): CommandProcessor = CommandProcessor(context, telegramRepository)

    @Provides
    @Singleton
    fun provideBookRepository(
        @ApplicationContext context: Context,
        bookDao: BookDao
    ): BookRepository = BookRepository(context, bookDao)
}
