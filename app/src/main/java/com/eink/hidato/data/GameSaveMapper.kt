package com.eink.hidato.data

import com.eink.hidato.data.db.GameSaveDefaults
import com.eink.hidato.data.db.GameSaveEntity
import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.GameState
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Puzzle

/** 领域状态 <-> Room 实体 映射 */
fun GameSaveEntity.toGameState(): GameState? = try {
    require(rows in 4..15 && cols in 4..15)
    val total = rows * cols
    val path = Csv.decode(solutionPath).toIntArray()
    require(HamiltonianPaths.isValid(path, rows, cols))
    val visible = Csv.decode(visibleClues)
    val hints = Csv.decode(revealedHints).toSet()
    val player = Csv.decode(playerPath)
    require(visible.all { it in 1..total } && hints.all { it in 1..total })
    require(player.size <= total && player.all { it in 0 until total })
    require(player.distinct().size == player.size)
    require(errorCount >= 0 && hintCount >= 0)
    val clues = BooleanArray(total + 1)
    for (n in visible) clues[n] = true
    // 关卡未完成时至少要能看到数字 1 与终点数字（生成器保证；此处兜底）
    clues[1] = true
    clues[total] = true
    val puzzle = Puzzle(rows, cols, path, clues)
    val placedSet = player.toSet()
    val placedCount = (1..total).count { n -> clues[n] || n in hints || puzzle.path[n - 1] in placedSet }
    require(completed == (placedCount == total))
    require(currentNumber == placedCount)
    GameState(
        puzzle = puzzle,
        revealedHints = hints,
        playerPath = player,
        focusedCell = player.lastOrNull(),
        errorCount = errorCount,
        hintCount = hintCount,
        completed = completed,
    )
} catch (_: IllegalArgumentException) {
    null
}

fun GameState.toEntity(difficulty: Difficulty): GameSaveEntity {
    val visible = (1..total).filter { puzzle.isClueVisible(it) }
    return GameSaveEntity(
        id = GameSaveDefaults.SINGLE_SLOT_ID,
        rows = puzzle.rows,
        cols = puzzle.cols,
        difficulty = difficulty.name,
        solutionPath = Csv.encode(puzzle.path.toList()),
        visibleClues = Csv.encode(visible),
        revealedHints = Csv.encode(revealedHints),
        playerPath = Csv.encode(playerPath),
        currentNumber = currentNumber,
        errorCount = errorCount,
        hintCount = hintCount,
        completed = completed,
        updatedAt = System.currentTimeMillis(),
    )
}
