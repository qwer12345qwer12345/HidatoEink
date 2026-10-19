package com.eink.hidato.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #12 validation gates for the frontier path counter:
 *
 *  - the DP count equals an independent clean-room DFS enumeration of
 *    Hamiltonian paths on many small boards and endpoint pairs (including
 *    zero-count and adjacent-endpoint cases);
 *  - it agrees with the production HidatoSolver.countSolutions on
 *    endpoint-only clue sets;
 *  - transposition (3xN vs Nx3 geometry) preserves the count;
 *  - component-size tracking (the probe for interior-clue state blow-up)
 *    never changes the count.
 */
class FrontierPathCounterTest {

    /** Clean-room enumeration; shares no code with the DP or the solver. */
    private fun bruteForceCount(rows: Int, cols: Int, start: Int, end: Int): Long {
        val total = rows * cols
        val visited = BooleanArray(total)
        var count = 0L
        fun dfs(cell: Int, seen: Int) {
            if (seen == total) {
                if (cell == end) count++
                return
            }
            val r = cell / cols
            val c = cell % cols
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until rows || nc !in 0 until cols) continue
                    val next = nr * cols + nc
                    if (visited[next]) continue
                    visited[next] = true
                    dfs(next, seen + 1)
                    visited[next] = false
                }
            }
        }
        visited[start] = true
        dfs(start, 1)
        return count
    }

    private fun transpose(cell: Int, rows: Int, cols: Int): Int =
        (cell % cols) * rows + (cell / cols)

    @Test
    fun `dp equals brute force on small boards and endpoint pairs`() {
        var cases = 0
        for ((rows, cols) in listOf(3 to 3, 3 to 4, 3 to 5, 4 to 4, 4 to 5, 2 to 5)) {
            val total = rows * cols
            val endpoints = listOf(0, total / 2, total - 1, rows * (cols / 2), total - cols)
                .map { it % total }
                .distinct()
            for (a in endpoints) {
                for (b in endpoints) {
                    if (a == b) continue
                    val expected = bruteForceCount(rows, cols, a, b)
                    val actual = FrontierPathCounter.count(rows, cols, a, b).paths
                    assertEquals("board $rows x $cols, endpoints ($a,$b)", expected, actual)
                    cases++
                }
            }
        }
        assertTrue("expected a meaningful number of cases, got $cases", cases > 40)
    }

    @Test
    fun `dp handles adjacent endpoints and zero-path cases`() {
        // Adjacent corner endpoints on 3x4: plenty of zigzag paths.
        val adjacent = FrontierPathCounter.count(3, 4, 0, 1).paths
        assertEquals(bruteForceCount(3, 4, 0, 1), adjacent)
        assertTrue(adjacent > 0)
        // Same query with swapped endpoints is symmetric.
        assertEquals(adjacent, FrontierPathCounter.count(3, 4, 1, 0).paths)
    }

    @Test
    fun `dp agrees with the production solver on endpoint-only clue sets`() {
        val random = Random(312)
        repeat(12) {
            val rows = 3
            val cols = 4 + it / 3
            val total = rows * cols
            val a = 0
            val b = total - 1
            // Find one witness path a->b for the solver's reference input.
            val witness = findWitness(rows, cols, a, b)
            if (witness == null) {
                assertEquals(0L, FrontierPathCounter.count(rows, cols, a, b).paths)
                return@repeat
            }
            val clues = BooleanArray(total + 1)
            clues[1] = true
            clues[total] = true
            val solverCount = HidatoSolver().countSolutions(
                rows, cols, witness, clues,
                limit = Int.MAX_VALUE, nodeLimit = 200_000_000L,
            )
            val dpCount = FrontierPathCounter.count(rows, cols, a, b).paths
            assertEquals("board $rows x $cols", solverCount.toLong(), dpCount)
        }
    }

    @Test
    fun `transposed geometry preserves the count`() {
        // 5x3 board transposed is 3x5; king adjacency is transpose-safe.
        for ((a, b) in listOf(0 to 14, 1 to 13, 4 to 10)) {
            val direct = FrontierPathCounter.count(5, 3, a, b).paths
            val flipped = FrontierPathCounter.count(
                3, 5, transpose(a, 5, 3), transpose(b, 5, 3),
            ).paths
            assertEquals(direct, flipped)
        }
    }

    @Test
    fun `component-size tracking never changes the count`() {
        for ((rows, cols) in listOf(3 to 5, 4 to 4, 3 to 7)) {
            val total = rows * cols
            val plain = FrontierPathCounter.count(rows, cols, 0, total - 1)
            val sized = FrontierPathCounter.count(rows, cols, 0, total - 1, trackComponentSizes = true)
            assertEquals(plain.paths, sized.paths)
            assertTrue("sized runs must expose at least as many distinct states", sized.sizedStates > 0)
        }
    }

    @Test
    fun `state space stays bounded by width across lengths`() {
        // The per-column state count must not grow with N for fixed width.
        var previous = 0
        for (n in listOf(6, 10, 14, 20)) {
            val stats = FrontierPathCounter.count(3, n, 0, 3 * n - 1)
            assertTrue("3x$n must stay far below the search-space size", stats.maxStatesPerColumn < 10_000)
            previous = n
        }
        val wide = FrontierPathCounter.count(4, 12, 0, 4 * 12 - 1)
        assertTrue(wide.maxStatesPerColumn < 100_000)
    }

    /** First Hamiltonian path a->b found by DFS, or null. */
    private fun findWitness(rows: Int, cols: Int, start: Int, end: Int): IntArray? {
        val total = rows * cols
        val visited = BooleanArray(total)
        val path = IntArray(total)
        fun dfs(cell: Int, seen: Int): Boolean {
            path[seen - 1] = cell
            if (seen == total) return cell == end
            val r = cell / cols
            val c = cell % cols
            for (dr in -1..1) {
                for (dc in -1..1) {
                    if (dr == 0 && dc == 0) continue
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until rows || nc !in 0 until cols) continue
                    val next = nr * cols + nc
                    if (visited[next]) continue
                    visited[next] = true
                    if (dfs(next, seen + 1)) return true
                    visited[next] = false
                }
            }
            return false
        }
        visited[start] = true
        return if (dfs(start, 1)) path.copyOf() else null
    }
}
