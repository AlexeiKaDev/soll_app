package com.soll.di

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.soll.data.api.TelegramApiService
import com.soll.data.local.SollDatabase
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.dao.CommandLogDao
import com.soll.data.local.dao.MessageLogDao
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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
    fun provideEncryptedSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
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
}
