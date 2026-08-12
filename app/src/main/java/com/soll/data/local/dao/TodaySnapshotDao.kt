package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.TodaySnapshotEntity

@Dao
interface TodaySnapshotDao {
    @Query("SELECT * FROM today_snapshots WHERE scope = :scope LIMIT 1")
    suspend fun get(scope: String = TodaySnapshotEntity.SCOPE_DEFAULT): TodaySnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: TodaySnapshotEntity)
}
