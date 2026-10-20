package com.eink.hidato.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameState 缓存字段（playerCells / placedCount / hasHiddenNumbers）
 * 必须与逐次扫描的结果一致，且在引擎状态迁移后重新计算。
 */
class GameStateBookkeepingTest {

    private fun puzzle(rows: Int = 4, cols: Int = 4, seed: Long = 1): Puzzle =
        PuzzleGenerator().generate(
            rows, cols, Difficulty.HARD,
            Random(seed), deadlineMillis = 15_000,
        )

    private fun state(p: Puzzle): GameState = GameState(
        puzzle = p,
        revealedHints = emptySet(),
        playerPath = emptyList(),
        focusedCell = null,
        errorCount = 0,
        hintCount = 0,
        completed = false,
    )

    /** 朴素实现：逐个数字检查可见性，作为缓存结果的对照。 */
    private fun GameState.naivePlacedCount(): Int = (1..total).count { isVisibleNumber(it) }

    @Test
    fun `placedCount matches naive scan for clues only`() {
        val s = state(puzzle())
        assertEquals(s.naivePlacedCount(), s.placedCount)
        assertTrue(s.hasHiddenNumbers)
        assertTrue(s.playerCells.isEmpty())
    }

    @Test
    fun `placedCount stays correct across play undo and hint transitions`() {
        val p = puzzle()
        var s = state(p)
        // 点击线索 1 后沿解路径连若干步（中途若遇到本来就可见的数字，仅刷新焦点）
        s = GameEngine.tap(s, p.path[0])
        for (step in 1..5) {
            s = GameEngine.tap(s, p.path[step])
            assertEquals(s.naivePlacedCount(), s.placedCount)
            assertEquals(s.playerPath.toSet(), s.playerCells)
        }
        // 撤销两步
        s = GameEngine.undo(s)
        s = GameEngine.undo(s)
        assertEquals(s.naivePlacedCount(), s.placedCount)
        assertEquals(s.playerPath.toSet(), s.playerCells)
        // 提示
        s = GameEngine.hint(s, Random(7))
        assertEquals(s.naivePlacedCount(), s.placedCount)
        assertEquals(1, s.hintCount)
    }

    @Test
    fun `hasHiddenNumbers false exactly when board is full`() {
        val p = puzzle()
        var s = state(p)
        // 沿完整解路径依次点击：已可见的格子仅刷新焦点，隐藏的格子被正确连入
        for (step in 0 until p.total) {
            s = GameEngine.tap(s, p.path[step])
        }
        assertEquals(p.total, s.placedCount)
        assertFalse(s.hasHiddenNumbers)
        assertTrue(s.completed)
    }

    @Test
    fun `cached fields recompute after copy and keep data-class semantics`() {
        val p = puzzle()
        val base = state(p)
        val movedCells = listOf(p.path[1], p.path[2])
        val withMove = base.copy(playerPath = movedCells)
        assertEquals(movedCells.toSet(), withMove.playerCells)
        // 新增可见数 = 既非线索也非提示的那几个（与缓存实现无关的独立推算）
        val expectedGrowth = movedCells.count { cell ->
            val n = p.numberAt(cell)
            !p.isClueVisible(n) && n !in base.revealedHints
        }
        assertEquals(base.placedCount + expectedGrowth, withMove.placedCount)
        assertNotEquals(base, withMove)
        // 相同字段的两个实例仍相等（缓存字段不参与 equals）
        val same = base.copy()
        assertEquals(base, same)
        assertEquals(base.playerCells, same.playerCells)
    }
}
