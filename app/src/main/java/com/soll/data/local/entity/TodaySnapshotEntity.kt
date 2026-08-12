package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "today_snapshots")
data class TodaySnapshotEntity(
    @PrimaryKey val scope: String = SCOPE_DEFAULT,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val SCOPE_DEFAULT = "owner"
    }
}
