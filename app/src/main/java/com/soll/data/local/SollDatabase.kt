package com.soll.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.soll.data.local.dao.BookDao
import com.soll.data.local.dao.BotConfigDao
import com.soll.data.local.dao.CommandLogDao
import com.soll.data.local.dao.MessageLogDao
import com.soll.data.local.entity.BookEntity
import com.soll.data.local.entity.BotConfigEntity
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.local.entity.MessageLogEntity

@Database(
    entities = [
        BotConfigEntity::class,
        MessageLogEntity::class,
        CommandLogEntity::class,
        BookEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class SollDatabase : RoomDatabase() {

    abstract fun botConfigDao(): BotConfigDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun commandLogDao(): CommandLogDao
    abstract fun bookDao(): BookDao

    companion object {
        const val DATABASE_NAME = "soll_database"
    }
}
