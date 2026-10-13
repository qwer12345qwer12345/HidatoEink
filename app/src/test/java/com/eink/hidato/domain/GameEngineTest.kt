package com.eink.hidato.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private fun puzzle(rows: Int = 4, cols: Int = 4, seed: Long = 1): Puzzle =
        PuzzleGenerator(HidatoSolver()).generate(
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

    @Test
    fun `tapping blank cell without focus is ignored without error`() {
        val p = puzzle()
        var s = state(p)
        assertNull(s.focusedCell)
        // 查找一个隐藏的非线索格子
        val hiddenCell = (0 until p.total).first { !s.isVisibleNumber(p.numberAt(it)) }
        s = GameEngine.tap(s, hiddenCell)
        assertNull(s.focusedCell)
        assertEquals(0, s.errorCount)
        assertEquals(0, s.playerPath.size)
    }

    @Test
    fun `tap visible clue sets focus and extending builds path`() {
        val p = puzzle()
        var s = state(p)

        // 起点：数字 1 的格子是可见线索，点击设为活动焦点
        val cell1 = p.path[0]
        s = GameEngine.tap(s, cell1)
        assertEquals(cell1, s.focusedCell)
        assertEquals(1, s.focusedNumber)
        assertEquals(0, s.errorCount)

        // 错误：点击不相邻的隐藏格子
        val wrongCell = (0 until p.total).first { !s.isVisibleNumber(p.numberAt(it)) && !Grids.adjacent(cell1, it, p.cols) }
        s = GameEngine.tap(s, wrongCell)
        assertEquals(1, s.errorCount)
        assertEquals(cell1, s.focusedCell)

        // 正确：沿解路径连到数字 2（与 1 相邻）
        val cell2 = p.path[1]
        s = GameEngine.tap(s, cell2)
        assertEquals(cell2, s.focusedCell)
        assertEquals(2, s.focusedNumber)
        assertEquals(1, s.errorCount)
        assertTrue(s.isVisibleNumber(2))
        assertEquals(listOf(cell2), s.playerPath)
    }

    @Test
    fun `can start from any visible clue and switch focus between clues`() {
        val path = HamiltonianPaths.randomTour(4, 4, Random(1))
        // 显式指定 1 和 10 为线索
        val clues = BooleanArray(17) { it == 1 || it == 10 || it == 16 }
        val p = Puzzle(4, 4, path, clues)
        var s = state(p)

        // 点击线索 10
        val cell10 = path[9]
        s = GameEngine.tap(s, cell10)
        assertEquals(cell10, s.focusedCell)
        assertEquals(10, s.focusedNumber)

        // 从 10 延伸填入 11
        val cell11 = path[10]
        s = GameEngine.tap(s, cell11)
        assertEquals(cell11, s.focusedCell)
        assertEquals(11, s.focusedNumber)
        assertTrue(s.isVisibleNumber(11))
        assertEquals(listOf(cell11), s.playerPath)

        // 自由切换焦点至线索 1
        val cell1 = path[0]
        s = GameEngine.tap(s, cell1)
        assertEquals(cell1, s.focusedCell)
        assertEquals(1, s.focusedNumber)

        // 从 1 延伸填入 2
        val cell2 = path[1]
        s = GameEngine.tap(s, cell2)
        assertEquals(cell2, s.focusedCell)
        assertEquals(2, s.focusedNumber)
        assertTrue(s.isVisibleNumber(2))
        assertEquals(listOf(cell11, cell2), s.playerPath)
    }

    @Test
    fun `undo removes last step in global time stack and restores focus`() {
        val path = HamiltonianPaths.randomTour(4, 4, Random(1))
        val clues = BooleanArray(17) { it == 1 || it == 16 }
        val p = Puzzle(4, 4, path, clues)
        var s = state(p)

        // 连 1 -> 2 -> 3
        s = GameEngine.tap(s, path[0])
        s = GameEngine.tap(s, path[1])
        s = GameEngine.tap(s, path[2])
        assertEquals(listOf(path[1], path[2]), s.playerPath)
        assertEquals(path[2], s.focusedCell)

        // 撤销 3 -> 焦点切回 2
        s = GameEngine.undo(s)
        assertEquals(listOf(path[1]), s.playerPath)
        assertFalse(s.isVisibleNumber(3))
        assertEquals(path[1], s.focusedCell)

        // 撤销 2 -> 焦点切回 1
        s = GameEngine.undo(s)
        assertEquals(emptyList<Int>(), s.playerPath)
        assertFalse(s.isVisibleNumber(2))
        assertEquals(path[0], s.focusedCell)

        // 空路径再撤销不变
        assertEquals(s, GameEngine.undo(s))
    }

    @Test
    fun `hint reveals hidden number and automatically focuses on it`() {
        val p = puzzle()
        var s = state(p)
        val hiddenBefore = (1..p.total).count { !s.isVisibleNumber(it) }
        if (hiddenBefore == 0) return
        val before = s.hintCount
        s = GameEngine.hint(s, Random(5))
        assertEquals(before + 1, s.hintCount)
        assertEquals(1, s.revealedHints.size)
        val revealed = s.revealedHints.first()
        assertTrue(s.isVisibleNumber(revealed))
        // 自动聚焦于该新提示格
        assertEquals(p.path[revealed - 1], s.focusedCell)
        assertEquals(revealed, s.focusedNumber)
    }

    @Test
    fun `completing full path marks completed`() {
        val p = puzzle()
        var s = state(p)
        for (i in 0 until p.total) {
            s = GameEngine.tap(s, p.path[i])
        }
        assertTrue(s.completed)
        assertEquals(p.total, s.placedCount)
    }

    @Test
    fun `reset clears everything and restores empty focus`() {
        val p = puzzle()
        var s = state(p)
        s = GameEngine.tap(s, p.path[0])
        s = GameEngine.hint(s)
        s = GameEngine.tap(s, p.path[1])
        s = GameEngine.tap(s, 999.coerceAtMost(p.total - 1)) // 触发一次错误
        val reset = GameEngine.reset(s)
        assertEquals(0, reset.errorCount)
        assertEquals(0, reset.hintCount)
        assertEquals(0, reset.revealedHints.size)
        assertNull(reset.focusedCell)
        assertFalse(reset.completed)
        assertEquals(p, reset.puzzle)
    }
}
