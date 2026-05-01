package com.soll.data.repository

import android.content.SharedPreferences
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.entity.BotConfigEntity
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
