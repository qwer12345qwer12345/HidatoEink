package com.eink.hidato.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidatoSolverTest {
    private val solver = HidatoSolver()
    private val baselineSolver = HidatoSolver(enableExactIntervalPropagation = false)

    @Test
    fun `fixed future clues cannot be used as hidden numbers`() {
        val path = intArrayOf(0, 1, 2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12)
        val clues = BooleanArray(17) { true }
        clues[2] = false
        assertEquals(1, solver.countSolutions(4, 4, path, clues))
        assertTrue(solver.findAlternative(4, 4, path, clues) is AlternativeResult.Unique)
    }

    @Test
    fun `any alternative returned is an actual valid path respecting every clue`() {
        val path = HamiltonianPaths.randomTour(4, 4, Random(42))
        val clues = BooleanArray(17) { it == 1 || it == 16 }
        val found = solver.findAlternative(4, 4, path, clues, nodeLimit = 2_000_000)
        assertTrue(found is AlternativeResult.Found)
        val alternative = (found as AlternativeResult.Found).path
        assertTrue(!alternative.contentEquals(path))
        assertTrue(HamiltonianPaths.isValid(alternative, 4, 4))
        for (n in 1..16) if (clues[n]) assertEquals(path[n - 1], alternative[n - 1])
    }

    @Test
    fun `new solver matches a separate brute force oracle`() {
        val random = Random(801)
        repeat(35) {
            val path = HamiltonianPaths.randomTour(4, 4, random)
            val clues = BooleanArray(17) { n -> n == 1 || n == 16 || random.nextDouble() < 0.72 }
            val expected = bruteCount(4, 4, path, clues)
            assertEquals(expected, solver.countSolutions(4, 4, path, clues, limit = 2, nodeLimit = 2_000_000))
            val result = solver.findAlternative(4, 4, path, clues, nodeLimit = 2_000_000)
            assertEquals(expected == 1, result is AlternativeResult.Unique)
        }
    }

    @Test
    fun `exact boundary interval only takes distance reducing moves`() {
        val path = intArrayOf(0, 1, 2, 3, 7, 6, 5, 4, 8, 9, 10, 11, 15, 14, 13, 12)
        val clues = BooleanArray(17) { it == 1 || it == 4 || it == 16 }
        val target = path[3]

        val result = solver.findAlternative(4, 4, path, clues, nodeLimit = 2_000_000)
        assertTrue(result is AlternativeResult.Found)
        val alternative = (result as AlternativeResult.Found).path
        for (number in 1..4) {
            assertEquals(4 - number, Grids.chebyshev(alternative[number - 1], target, 4))
        }
    }

    @Test
    fun `exact diagonal interval forces diagonal moves`() {
        val path = intArrayOf(0, 5, 10, 15, 11, 14, 13, 12, 8, 4, 9, 6, 1, 2, 3, 7)
        val clues = BooleanArray(17) { it == 1 || it == 3 || it == 16 }

        val result = solver.findAlternative(4, 4, path, clues, nodeLimit = 2_000_000)
        assertTrue(result is AlternativeResult.Found)
        val alternative = (result as AlternativeResult.Found).path
        assertTrue(alternative.copyOfRange(0, 3).contentEquals(path.copyOfRange(0, 3)))
    }

    @Test
    fun `reserved future clue blocks a geometrically shortest move`() {
        // Cell 5 is a shortest first move from cell 0 toward clue 4 at cell 14,
        // but it is reserved for clue 8 and therefore cannot be used as digit 2.
        val path = intArrayOf(0, 4, 9, 14, 15, 11, 6, 5, 1, 2, 3, 7, 10, 13, 8, 12)
        val clues = BooleanArray(17) { it == 1 || it == 4 || it == 8 || it == 16 }
        val expected = bruteCount(4, 4, path, clues)

        assertEquals(1, expected)
        assertEquals(expected, solver.countSolutions(4, 4, path, clues, limit = 2, nodeLimit = 2_000_000))
        assertTrue(solver.findAlternative(4, 4, path, clues, nodeLimit = 2_000_000) is AlternativeResult.Unique)
    }

    @Test
    fun `all 2x3 paths and endpoint fixed clue subsets match independent oracle`() {
        val rows = 2
        val cols = 3
        val total = rows * cols
        val paths = allHamiltonianPaths(rows, cols)
        assertEquals(96, paths.size)

        for (path in paths) {
            for (mask in 0 until (1 shl (total - 2))) {
                val clues = BooleanArray(total + 1)
                clues[1] = true
                clues[total] = true
                for (number in 2 until total) {
                    clues[number] = mask and (1 shl (number - 2)) != 0
                }
                val context = "path=${path.joinToString(",")} mask=$mask"
                val expected = bruteCount(rows, cols, path, clues)
                assertEquals(
                    context,
                    expected,
                    solver.countSolutions(rows, cols, path, clues, limit = 2, nodeLimit = 100_000),
                )
                val result = solver.findAlternative(rows, cols, path, clues, nodeLimit = 100_000)
                assertFalse("$context should finish within the budget", result is AlternativeResult.Unknown)
                assertEquals(context, expected == 1, result is AlternativeResult.Unique)

                assertEquals(
                    "$context baseline count",
                    expected,
                    baselineSolver.countSolutions(rows, cols, path, clues, limit = 2, nodeLimit = 100_000),
                )
                val baselineResult = baselineSolver.findAlternative(rows, cols, path, clues, nodeLimit = 100_000)
                assertFalse("$context baseline should finish within the budget", baselineResult is AlternativeResult.Unknown)
                assertEquals("$context baseline uniqueness", expected == 1, baselineResult is AlternativeResult.Unique)
            }
        }
    }

    @Test
    fun `search budget exhaustion is unknown never unique`() {
        val path = HamiltonianPaths.randomTour(4, 4, Random(12))
        val clues = BooleanArray(17) { it == 1 || it == 16 }
        assertTrue(solver.findAlternative(4, 4, path, clues, nodeLimit = 1) is AlternativeResult.Unknown)
        assertEquals(-1, solver.countSolutions(4, 4, path, clues, nodeLimit = 1))
    }

    /** Exhaustively enumerate reference paths without using the production path generator. */
    private fun allHamiltonianPaths(rows: Int, cols: Int): List<IntArray> {
        val total = rows * cols
        val path = IntArray(total)
        val visited = BooleanArray(total)
        val result = ArrayList<IntArray>()

        fun extend(length: Int) {
            if (length == total) {
                result.add(path.copyOf())
                return
            }
            for (next in Grids.neighbors(path[length - 1], rows, cols)) {
                if (visited[next]) continue
                visited[next] = true
                path[length] = next
                extend(length + 1)
                visited[next] = false
            }
        }

        for (start in 0 until total) {
            path[0] = start
            visited[start] = true
            extend(1)
            visited[start] = false
        }
        return result
    }

    /** Deliberately simple independent algorithm: no optimized pruning or witnesses. */
    private fun bruteCount(rows: Int, cols: Int, path: IntArray, clues: BooleanArray): Int {
        val total = rows * cols
        val fixedOwner = IntArray(total)
        for (number in 1..total) {
            if (clues[number]) fixedOwner[path[number - 1]] = number
        }
        val used = BooleanArray(total)
        var solutions = 0
        fun walk(number: Int, cell: Int) {
            if (solutions >= 2) return
            if (number == total) {
                solutions++
                return
            }
            for (next in Grids.neighbors(cell, rows, cols)) {
                if (used[next]) continue
                if (fixedOwner[next] != 0 && fixedOwner[next] != number + 1) continue
                if (clues[number + 1] && path[number] != next) continue
                used[next] = true
                walk(number + 1, next)
                used[next] = false
            }
        }
        used[path[0]] = true
        walk(1, path[0])
        return solutions
    }
}
