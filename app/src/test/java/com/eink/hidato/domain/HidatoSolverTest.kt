package com.eink.hidato.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidatoSolverTest {
    private val solver = HidatoSolver()

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
    fun `search budget exhaustion is unknown never unique`() {
        val path = HamiltonianPaths.randomTour(4, 4, Random(12))
        val clues = BooleanArray(17) { it == 1 || it == 16 }
        assertTrue(solver.findAlternative(4, 4, path, clues, nodeLimit = 1) is AlternativeResult.Unknown)
        assertEquals(-1, solver.countSolutions(4, 4, path, clues, nodeLimit = 1))
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
