package com.eink.hidato.domain

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HamiltonianPathsTest {

    @Test
    fun `randomTour covers all cells with valid 8-direction adjacency`() {
        val random = Random(123)
        for (size in 4..8) {
            val path = HamiltonianPaths.randomTour(size, size, random)
            assertTrue("size=$size 必须是合法路径", HamiltonianPaths.isValid(path, size, size))
        }
        // 非正方形网格与大网格
        for ((r, c) in listOf(4 to 7, 11 to 5, 4 to 15, 15 to 4, 12 to 12)) {
            val path = HamiltonianPaths.randomTour(r, c, random)
            assertTrue("${r}x${c} 必须是合法路径", HamiltonianPaths.isValid(path, r, c))
        }
    }

    @Test
    fun `randomTour start positions are distributed across the board`() {
        val random = Random(456)
        val starts = mutableSetOf<Int>()
        repeat(30) {
            val path = HamiltonianPaths.randomTour(6, 6, random)
            starts.add(path[0])
        }
        // 30 次生成不应该全都在同一位置（彻底告别固定左上角 0）
        assertTrue("起点应分布在棋盘不同格子，实际种类数: ${starts.size}", starts.size > 10)
        assertFalse("起点不应仅固定为 0", starts.size == 1 && starts.contains(0))
    }

    @Test
    fun `pure random paths keep natural straight runs and diagonal ratio`() {
        val random = Random(789)
        val count = 20
        var diagTotal = 0.0
        var maxStraightTotal = 0
        var longRunTotal = 0

        for (i in 0 until count) {
            val path = HamiltonianPaths.randomTour(8, 8, random)
            val (diag, maxStraight) = HamiltonianPaths.analyzePath(path, 8)
            diagTotal += diag
            maxStraightTotal += maxStraight

            // 统计长度 ≥3 格的直线段条数
            var cur = 0
            var lastDr = 0
            var lastDc = 0
            for (j in 1 until path.size) {
                val dr = path[j] / 8 - path[j - 1] / 8
                val dc = path[j] % 8 - path[j - 1] % 8
                if (abs(dr) == 1 && abs(dc) == 1) {
                    cur = 0
                    lastDr = 0
                    lastDc = 0
                } else {
                    if (dr == lastDr && dc == lastDc) {
                        cur++
                    } else {
                        cur = 1
                        lastDr = dr
                        lastDc = dc
                    }
                    if (cur == 3) longRunTotal++
                }
            }
        }

        val avgDiag = diagTotal / count
        val avgMaxStraight = maxStraightTotal.toDouble() / count
        val avgLongRuns = longRunTotal.toDouble() / count
        assertTrue("纯随机直线步占比应过半，实际对角线占比 $avgDiag", avgDiag <= 0.55)
        assertTrue("平均最长直线段应 ≥3 格，实际 $avgMaxStraight", avgMaxStraight >= 3.0)
        assertTrue("每题平均应出现 ≥1.5 条长度 ≥3 格的直线段，实际 $avgLongRuns", avgLongRuns >= 1.5)
    }

    @Test
    fun `isValid rejects broken path`() {
        val path = HamiltonianPaths.randomTour(4, 4, Random(1))
        val broken = path.copyOf()
        val tmp = broken[0]
        broken[0] = broken[broken.size - 1]
        broken[broken.size - 1] = tmp
        assertFalse(HamiltonianPaths.isValid(broken, 4, 4))
    }
}

class PuzzleGeneratorTest {

    private val generator = PuzzleGenerator()

    @Test
    fun `puzzle with all clues has exactly one solution`() {
        val rows = 6
        val cols = 6
        val path = HamiltonianPaths.randomTour(rows, cols, Random(42))
        val clues = BooleanArray(rows * cols + 1) { true }
        clues[0] = false
        assertEquals(1, HidatoSolver().countSolutions(rows, cols, path, clues, limit = 5))
    }

    @Test
    fun `removing clues creates multiple solutions`() {
        val rows = 4
        val cols = 4
        val path = HamiltonianPaths.randomTour(rows, cols, Random(42))
        val clues = BooleanArray(rows * cols + 1) { true }
        clues[0] = false
        // 只保留起点和终点两个线索
        for (n in 2 until (rows * cols)) clues[n] = false
        val count = HidatoSolver().countSolutions(rows, cols, path, clues, limit = 5)
        assertTrue("期望多解，实际 $count", count > 1)
    }

    @Test
    fun `generated puzzles keep endpoints visible`() {
        val random = Random(42)
        for (size in intArrayOf(4, 5, 6, 7, 8, 9, 10)) {
            val puzzle = generator.generate(size, size, Difficulty.HARD, random, deadlineMillis = 3_000)
            val total = size * size
            assertTrue("起点必须可见", puzzle.isClueVisible(1))
            assertTrue("终点必须可见", puzzle.isClueVisible(total))
            assertTrue(HamiltonianPaths.isValid(puzzle.path, size, size))
        }
    }

    @Test
    fun `non square grid 4x15 and 15x4 generates valid puzzle`() {
        val random = Random(7)
        for ((r, c) in listOf(4 to 15, 15 to 4)) {
            val puzzle = generator.generate(r, c, Difficulty.MEDIUM, random, deadlineMillis = 3_000)
            assertTrue(HamiltonianPaths.isValid(puzzle.path, r, c))
            assertTrue(puzzle.isClueVisible(1))
            assertTrue(puzzle.isClueVisible(r * c))
        }
    }

    @Test
    fun `max size 15x15 hard generates puzzle within deadline`() {
        val random = Random(2026)
        val startAt = System.currentTimeMillis()
        val puzzle = generator.generate(15, 15, Difficulty.HARD, random, deadlineMillis = 3_000)
        val elapsed = System.currentTimeMillis() - startAt
        assertTrue("生成耗时应有限：$elapsed ms", elapsed < 60_000)
        assertTrue(puzzle.isClueVisible(1))
        assertTrue(puzzle.isClueVisible(225))
        assertTrue(HamiltonianPaths.isValid(puzzle.path, 15, 15))
    }

    private fun visibleRatio(puzzle: Puzzle): Double =
        (1..puzzle.total).count { puzzle.isClueVisible(it) }.toDouble() / puzzle.total

    @Test
    fun `easy puzzle does not go below the configured minimum`() {
        val puzzle = generator.generate(8, 8, Difficulty.EASY, Random(11), deadlineMillis = 3_000)
        val ratio = visibleRatio(puzzle)
        assertTrue("简单档保留比例应 ≥35%，实际 $ratio", ratio >= 0.35)
    }

    @Test
    fun `medium puzzle does not go below the configured minimum`() {
        val puzzle = generator.generate(8, 8, Difficulty.MEDIUM, Random(12), deadlineMillis = 3_000)
        val ratio = visibleRatio(puzzle)
        assertTrue("中等档保留比例应 ≥20%，实际 $ratio", ratio >= 0.20)
    }

    @Test
    fun `hard puzzle is proven unique with no fixed percentage target`() {
        val puzzle = generator.generate(8, 8, Difficulty.HARD, Random(13), deadlineMillis = 3_000)
        val ratio = visibleRatio(puzzle)
        assertTrue("困难档必须保留至少两个固定端点", ratio >= 2.0 / 64)
        // No fixed percentage can safely be promised for unique Hidato.
        val result = HidatoSolver().findAlternative(8, 8, puzzle.path, puzzle.clues, nodeLimit = 5_000_000, timeLimitMillis = 10_000)
        assertTrue("困难题必须只有一个完整解，实际 $result", result is AlternativeResult.Unique)
    }

    @Test
    fun `unique generation keeps correctness when time budget is short`() {
        val generator = PuzzleGenerator()
        for (seed in 1..5) {
            val generated = generator.generateDetailed(
                6, 6, Difficulty.HARD, Random(seed),
                deadlineMillis = 300, perCheckMillis = 75,
            )
            val puzzle = generated.puzzle
            assertTrue(puzzle.clues[1])
            assertTrue(puzzle.clues[36])
            assertEquals(puzzle.total - generated.stats.verifiedRemovals, generated.stats.retainedClues)
            assertTrue(HidatoSolver().findAlternative(6, 6, puzzle.path, puzzle.clues, nodeLimit = 3_000_000) is AlternativeResult.Unique)
        }
    }

    @Test
    fun `default 2s deadline bounds generation time`() {
        val startAt = System.currentTimeMillis()
        val puzzle = generator.generate(8, 8, Difficulty.MEDIUM, Random(14))
        val elapsed = System.currentTimeMillis() - startAt
        assertTrue("默认 2s 预算下生成耗时应有限：$elapsed ms", elapsed < 10_000)
        assertTrue(HamiltonianPaths.isValid(puzzle.path, 8, 8))
    }
}
