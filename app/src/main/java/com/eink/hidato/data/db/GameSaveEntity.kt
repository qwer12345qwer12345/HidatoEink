package com.eink.hidato.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 游戏进度存档（单槽位：id 恒为 1，开始新关卡时整体覆盖）。
 */
@Entity(tableName = "game_saves")
data class GameSaveEntity(
    @PrimaryKey val id: Long = 1,
    val rows: Int,
    val cols: Int,
    val difficulty: String,
    /** 完整解路径：按数字顺序的格子下标，CSV */
    val solutionPath: String,
    /** 初始可见线索数字集合，CSV */
    val visibleClues: String,
    /** 已通过“提示”显示的数字集合，CSV */
    val revealedHints: String,
    /** 玩家已连接路径（按连接顺序的格子下标），CSV */
    val playerPath: String,
    /** 当前连接到的数字 */
    val currentNumber: Int,
    val errorCount: Int,
    val hintCount: Int,
    val completed: Boolean,
    val updatedAt: Long,
)

object GameSaveDefaults {
    const val SINGLE_SLOT_ID: Long = 1L
}
