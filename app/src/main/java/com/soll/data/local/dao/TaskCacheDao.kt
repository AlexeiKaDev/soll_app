package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.soll.data.local.entity.TaskCacheEntity

@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM task_cache ORDER BY updated_at DESC")
    suspend fun getAll(): List<TaskCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskCacheEntity>)

    @Query("DELETE FROM task_cache")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(tasks: List<TaskCacheEntity>) {
        clear()
        insertAll(tasks)
    }
}
