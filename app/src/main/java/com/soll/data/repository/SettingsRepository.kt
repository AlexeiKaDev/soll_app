package com.soll.data.repository

import android.content.SharedPreferences
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.entity.BotConfigEntity
import com.soll.domain.tts.TtsBookPerformanceProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val botConfigDao: BotConfigDao
) {
    companion object {
        private const val KEY_BOT_TOKEN = "bot_token"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_LAST_OFFSET = "last_offset"
        private const val KEY_POLLING_TIMEOUT = "polling_timeout"
        private const val KEY_TTS_ENGINE = "tts_engine"
        private const val KEY_TTS_AUTO_ADVANCE = "tts_auto_advance"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_ENGINE_TYPE = "tts_engine_type"
        private const val KEY_TTS_SILERO_SPEAKER = "tts_silero_speaker"
        private const val KEY_TTS_UTROBIN_SPEAKER = "tts_utrobin_speaker"
        private const val KEY_TTS_UTROBIN_ORT_THREADS = "tts_utrobin_ort_threads"
        private const val KEY_TTS_NATASHA_ORT_THREADS = "tts_natasha_ort_threads"
        private const val KEY_TTS_SHERPA_NUM_THREADS = "tts_sherpa_num_threads"
        private const val KEY_TTS_BOOK_PERF_PROFILE = "tts_book_perf_profile"
        private const val KEY_BOOK_READER_S200_BOOTSTRAP = "book_reader_s200_bootstrap_done"
        private const val KEY_TTS_SYSTEM_PITCH = "tts_system_pitch"
    }

    // Bot Token (encrypted storage)
    var botToken: String?
        get() = sharedPreferences.getString(KEY_BOT_TOKEN, null)
        set(value) = sharedPreferences.edit().putString(KEY_BOT_TOKEN, value).apply()

    // Auto-start on boot
    var autoStartEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_START, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_START, value).apply()

    // Service running state
    var isServiceRunning: Boolean
        get() = sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    // Last update offset for polling
    var lastOffset: Long
        get() = sharedPreferences.getLong(KEY_LAST_OFFSET, 0)
        set(value) = sharedPreferences.edit().putLong(KEY_LAST_OFFSET, value).apply()

    // Polling timeout in seconds
    var pollingTimeout: Int
        get() = sharedPreferences.getInt(KEY_POLLING_TIMEOUT, 30)
        set(value) = sharedPreferences.edit().putInt(KEY_POLLING_TIMEOUT, value).apply()

    // TTS engine package name
    var ttsEngine: String?
        get() = sharedPreferences.getString(KEY_TTS_ENGINE, null)
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ENGINE, value).apply()

    // TTS auto-advance to next chapter
    var ttsAutoAdvance: Boolean
        get() = sharedPreferences.getBoolean(KEY_TTS_AUTO_ADVANCE, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_TTS_AUTO_ADVANCE, value).apply()

    // TTS speech rate
    var ttsSpeechRate: Float
        get() = sharedPreferences.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) = sharedPreferences.edit().putFloat(KEY_TTS_SPEECH_RATE, value).apply()

    // TTS engine type: "system" or "silero"
    var ttsEngineType: String
        get() = sharedPreferences.getString(KEY_TTS_ENGINE_TYPE, "system") ?: "system"
        set(value) = sharedPreferences.edit().putString(KEY_TTS_ENGINE_TYPE, value).apply()

    // Piper / Silero voice id (e.g. irina)
    var ttssileroSpeaker: String
        get() = sharedPreferences.getString(KEY_TTS_SILERO_SPEAKER, "irina") ?: "irina"
        set(value) = sharedPreferences.edit().putString(KEY_TTS_SILERO_SPEAKER, value).apply()

    /** Utrobin speaker index as string: "0" / "1" */
    var ttsUtrobinSpeaker: String
        get() = sharedPreferences.getString(KEY_TTS_UTROBIN_SPEAKER, "0") ?: "0"
        set(value) = sharedPreferences.edit().putString(KEY_TTS_UTROBIN_SPEAKER, value).apply()

    /** ONNX Runtime intra-op threads for Utrobin (1–4). */
    var ttsUtrobinOrtIntraThreads: Int
        get() = sharedPreferences.getInt(KEY_TTS_UTROBIN_ORT_THREADS, 2).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_UTROBIN_ORT_THREADS, value.coerceIn(1, 4))
            .apply()

    /** ONNX intra-op threads for Natasha book engine (1–4). */
    var ttsNatashaOrtIntraThreads: Int
        get() = sharedPreferences.getInt(KEY_TTS_NATASHA_ORT_THREADS, 2).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_NATASHA_ORT_THREADS, value.coerceIn(1, 4))
            .apply()

    /** Sherpa OfflineTts numThreads for Piper (1–4). */
    var ttsSherpaNumThreads: Int
        get() = sharedPreferences.getInt(
            KEY_TTS_SHERPA_NUM_THREADS,
            TtsBookPerformanceProfile.sherpaNumThreads(
                TtsBookPerformanceProfile.BALANCED,
                Runtime.getRuntime().availableProcessors(),
            ),
        ).coerceIn(1, 4)
        set(value) = sharedPreferences.edit()
            .putInt(KEY_TTS_SHERPA_NUM_THREADS, value.coerceIn(1, 4))
            .apply()

    /**
     * Book reader preset: battery / balanced / quality (threads + chunk merge).
     * See [com.soll.domain.tts.TtsBookPerformanceProfile] and docs/tts-s200-model-shortlist.md.
     */
    var ttsBookPerformanceProfile: String
        get() = sharedPreferences.getString(
            KEY_TTS_BOOK_PERF_PROFILE,
            TtsBookPerformanceProfile.BALANCED.storageKey,
        ) ?: TtsBookPerformanceProfile.BALANCED.storageKey
        set(value) = sharedPreferences.edit().putString(KEY_TTS_BOOK_PERF_PROFILE, value).apply()

    /** One-time defaults for Doogee S200–class devices (Piper + Balanced). */
    var bookReaderS200BootstrapDone: Boolean
        get() = sharedPreferences.getBoolean(KEY_BOOK_READER_S200_BOOTSTRAP, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_BOOK_READER_S200_BOOTSTRAP, value).apply()

    /** Writes thread prefs to match a performance preset (e.g. when user taps Battery/Balanced/Quality). */
    fun syncThreadPrefsFromProfile(profile: TtsBookPerformanceProfile) {
        val ort = TtsBookPerformanceProfile.ortIntraThreads(profile)
        val sh = TtsBookPerformanceProfile.sherpaNumThreads(
            profile,
            Runtime.getRuntime().availableProcessors(),
        )
        sharedPreferences.edit()
            .putInt(KEY_TTS_UTROBIN_ORT_THREADS, ort)
            .putInt(KEY_TTS_NATASHA_ORT_THREADS, ort)
            .putInt(KEY_TTS_SHERPA_NUM_THREADS, sh)
            .apply()
    }

    /** System TTS pitch (1 = default). */
    var ttsSystemPitch: Float
        get() = sharedPreferences.getFloat(KEY_TTS_SYSTEM_PITCH, 1.0f)
        set(value) = sharedPreferences.edit().putFloat(KEY_TTS_SYSTEM_PITCH, value).apply()

    // Bot configs from database
    fun getAllBotConfigs(): Flow<List<BotConfigEntity>> = botConfigDao.getAllConfigs()

    suspend fun getActiveConfig(): BotConfigEntity? = botConfigDao.getActiveConfig()

    fun getActiveConfigFlow(): Flow<BotConfigEntity?> = botConfigDao.getActiveConfigFlow()

    suspend fun saveBotConfig(name: String, token: String): Long {
        val config = BotConfigEntity(
            name = name,
            token = token,
            isActive = true
        )
        // Deactivate all other configs
        botConfigDao.deactivateAll()
        return botConfigDao.insert(config)
    }

    suspend fun updateBotConfig(config: BotConfigEntity) {
        botConfigDao.update(config)
    }

    suspend fun setActiveConfig(configId: Int) {
        botConfigDao.setActiveConfig(configId)
    }

    suspend fun updateOffset(configId: Int, offset: Long) {
        botConfigDao.updateOffset(configId, offset)
        lastOffset = offset
    }

    suspend fun updateBotInfo(configId: Int, username: String, botId: Long) {
        botConfigDao.updateBotInfo(configId, username, botId)
    }

    suspend fun deleteConfig(config: BotConfigEntity) {
        botConfigDao.delete(config)
    }

    fun hasValidToken(): Boolean {
        return !botToken.isNullOrBlank() && botToken!!.contains(":")
    }

    fun validateToken(token: String): Boolean {
        // Telegram bot tokens have format: 123456789:ABC-DEF1234ghIkl-zyx57W2v1u123ew11
        val regex = Regex("^\\d+:[A-Za-z0-9_-]{35,}$")
        return regex.matches(token.trim())
    }
}
