package com.soll.data.local.dao

import androidx.room.*
import com.soll.data.local.entity.BotConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BotConfigDao {

    @Query("SELECT * FROM bot_configs ORDER BY last_used_at DESC")
    fun getAllConfigs(): Flow<List<BotConfigEntity>>

    @Query("SELECT * FROM bot_configs WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveConfig(): BotConfigEntity?

    @Query("SELECT * FROM bot_configs WHERE is_active = 1 LIMIT 1")
    fun getActiveConfigFlow(): Flow<BotConfigEntity?>

    @Query("SELECT * FROM bot_configs WHERE id = :id")
    suspend fun getConfigById(id: Int): BotConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: BotConfigEntity): Long

    @Update
    suspend fun update(config: BotConfigEntity)

    @Delete
    suspend fun delete(config: BotConfigEntity)

    @Query("UPDATE bot_configs SET is_active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE bot_configs SET is_active = 1, last_used_at = :timestamp WHERE id = :id")
    suspend fun setActive(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE bot_configs SET last_offset = :offset WHERE id = :id")
    suspend fun updateOffset(id: Int, offset: Long)

    @Query("UPDATE bot_configs SET bot_username = :username, bot_id = :botId WHERE id = :id")
    suspend fun updateBotInfo(id: Int, username: String, botId: Long)

    @Transaction
    suspend fun setActiveConfig(id: Int) {
        deactivateAll()
        setActive(id)
    }
}
