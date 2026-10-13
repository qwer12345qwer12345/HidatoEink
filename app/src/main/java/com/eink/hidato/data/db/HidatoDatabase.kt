package com.eink.hidato.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [GameSaveEntity::class], version = 1, exportSchema = false)
abstract class HidatoDatabase : RoomDatabase() {
    abstract fun gameSaveDao(): GameSaveDao

    companion object {
        const val NAME = "hidato.db"
    }
}
