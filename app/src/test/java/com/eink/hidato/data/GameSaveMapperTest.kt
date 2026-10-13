package com.eink.hidato.data

import com.eink.hidato.domain.Difficulty
import com.eink.hidato.domain.GameState
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Puzzle
import org.junit.Assert.*
import org.junit.Test

class GameSaveMapperTest {
    private val path = HamiltonianPaths.randomTour(4, 4, kotlin.random.Random(1))
    private val clues = BooleanArray(17).apply { this[1] = true; this[16] = true }
    private val state = GameState(
        Puzzle(4, 4, path, clues),
        setOf(3), listOf(path[1]), path[1], 2, 1, false,
    )

    @Test
    fun `valid existing save round trips without losing progress`() {
        val entity = state.toEntity(Difficulty.HARD)
        val restored = requireNotNull(entity.toGameState())
        assertArrayEquals(path, restored.puzzle.path)
        assertEquals(state.playerPath, restored.playerPath)
        assertEquals(state.revealedHints, restored.revealedHints)
        assertEquals(state.errorCount, restored.errorCount)
        assertEquals(state.hintCount, restored.hintCount)
        assertEquals(state.completed, restored.completed)
    }

    @Test
    fun `corrupt save is rejected instead of throwing`() {
        val entity = state.toEntity(Difficulty.HARD)
        val corrupt = listOf(
            entity.copy(rows = 0),
            entity.copy(solutionPath = "x"),
            entity.copy(solutionPath = "999," + path.drop(1).joinToString(",")),
            entity.copy(solutionPath = path.joinToString(",") { "0" }),
            entity.copy(visibleClues = "17"),
            entity.copy(revealedHints = "-1"),
            entity.copy(playerPath = "999"),
            entity.copy(playerPath = "1,1"),
            entity.copy(currentNumber = 99),
            entity.copy(completed = true),
            entity.copy(errorCount = -1),
        )
        for (save in corrupt) assertNull(save.toGameState())
    }
}
