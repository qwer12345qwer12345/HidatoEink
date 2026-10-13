package com.eink.hidato.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSaveDao {

    @Query("SELECT * FROM game_saves WHERE id = 1")
    fun observeCurrent(): Flow<GameSaveEntity?>

    @Query("SELECT * FROM game_saves WHERE id = 1")
    suspend fun getCurrent(): GameSaveEntity?

    @Upsert
    suspend fun upsert(entity: GameSaveEntity)
}
