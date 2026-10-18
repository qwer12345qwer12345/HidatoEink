package com.eink.hidato.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Soundness validation for the geometric reachability pruning (issue #6,
 * direction A). A prune is only acceptable if it can NEVER change the
 * answer, so every test compares the pruned solver against an independent
 * clean-room oracle (or the already-validated plain solver) — never against
 * itself. The clean-room recursion deliberately shares no code with the
 * production search.
 */
class HidatoSolverReachabilityTest {

    private val pruned = HidatoSolver(freeSpaceReachability = true)
    private val plain = HidatoSolver()

    // ---------------------------------------------------------------- 3x3 exhaustive

    @Test
    fun `all 3x3 clue subsets agree with clean-room enumeration`() {
        val paths = listOf(
            intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8),
            intArrayOf(3, 0, 1, 2, 5, 8, 7, 6, 4),
            intArrayOf(0, 3, 6, 7, 4, 1, 2, 5, 8),
            intArrayOf(4, 3, 0, 1, 2, 5, 8, 7, 6),
            intArrayOf(8, 7, 6, 3, 0, 1, 2, 5, 4),
        )
        for (path in paths) {
            assertTrue(HamiltonianPaths.isValid(path, 3, 3))
            for (mask in 0 until 128) {
                val clues = BooleanArray(10)
                clues[1] = true
                clues[9] = true
                for (n in 2..8) clues[n] = mask and (1 shl (n - 2)) != 0
                val expected = cleanRoomCount(3, 3, path, clues, limit = 2)
                val actual = pruned.countSolutions(3, 3, path, clues, limit = 2, nodeLimit = 1_000_000)
                assertEquals("path=${path.contentToString()} mask=$mask", expected, actual)
            }
        }
    }

    // ---------------------------------------------------------------- 4x4 random vs clean-room

    @Test
    fun `4x4 random sparse subsets agree with clean-room oracle`() {
        val random = Random(4242)
        var checked = 0
        repeat(90) {
            val path = HamiltonianPaths.randomTour(4, 4, random)
            for (density in doubleArrayOf(0.15, 0.4, 0.7)) {
                val clues = BooleanArray(17)
                clues[1] = true
                clues[16] = true
                for (n in 2..15) if (random.nextDouble() < density) clues[n] = true
                val expected = cleanRoomCount(4, 4, path, clues, limit = 2)
                val actual = pruned.countSolutions(4, 4, path, clues, limit = 2, nodeLimit = 5_000_000)
                assertEquals("density=$density clues=${(1..16).filter { clues[it] }}", expected, actual)
                checked++
            }
        }
        assertTrue("expected well over a hundred checks, got $checked", checked > 200)
    }

    /** The solver API does not force the final digit; pruning must stay sound there too. */
    @Test
    fun `4x4 subsets without the final clue stay sound`() {
        val random = Random(9)
        repeat(30) {
            val path = HamiltonianPaths.randomTour(4, 4, random)
            val clues = BooleanArray(17)
            clues[1] = true
            for (n in 2..16) if (random.nextDouble() < 0.5) clues[n] = true
            val expected = cleanRoomCount(4, 4, path, clues, limit = 2)
            assertEquals(expected, pruned.countSolutions(4, 4, path, clues, limit = 2, nodeLimit = 5_000_000))
        }
    }

    // ---------------------------------------------------------------- larger boards: pruned vs plain

    @Test
    fun `5x5 and 6x6 answers are never changed by pruning`() {
        val random = Random(77)
        for (size in intArrayOf(5, 6)) {
            repeat(24) {
                val path = HamiltonianPaths.randomTour(size, size, random)
                val total = size * size
                val clues = BooleanArray(total + 1)
                clues[1] = true
                clues[total] = true
                for (n in 2 until total) if (random.nextDouble() < 0.35) clues[n] = true
                val reference = plain.findAlternative(size, size, path, clues, nodeLimit = 20_000_000)
                val prunedResult = pruned.findAlternative(size, size, path, clues, nodeLimit = 20_000_000)
                // The pruned tree is a subset of the plain tree: whenever the
                // plain search decided within the budget, the pruned one must
                // decide identically (it cannot need MORE nodes).
                if (reference !is AlternativeResult.Unknown) {
                    assertEquals(
                        "clues=${(1..total).filter { clues[it] }}",
                        reference.javaClass, prunedResult.javaClass,
                    )
                }
                if (prunedResult is AlternativeResult.Found) {
                    val alternative = prunedResult.path
                    assertTrue(HamiltonianPaths.isValid(alternative, size, size))
                    for (n in 1..total) if (clues[n]) assertEquals(path[n - 1], alternative[n - 1])
                }
            }
        }
    }

    @Test
    fun `pruned search never needs more nodes than the plain search`() {
        val random = Random(5150)
        repeat(40) {
            val path = HamiltonianPaths.randomTour(4, 4, random)
            val clues = BooleanArray(17)
            clues[1] = true
            clues[16] = true
            for (n in 2..15) if (random.nextDouble() < 0.45) clues[n] = true
            val reference = plain.findAlternative(4, 4, path, clues, nodeLimit = 5_000_000)
            val prunedResult = pruned.findAlternative(4, 4, path, clues, nodeLimit = 5_000_000)
            if (reference !is AlternativeResult.Unknown) {
                assertTrue(
                    "prune must only remove work (plain=${reference.nodes}, pruned=${prunedResult.nodes})",
                    prunedResult.nodes <= reference.nodes,
                )
            }
        }
    }

    // ---------------------------------------------------------------- clean-room oracle

    /**
     * Deliberately independent implementation: own adjacency arithmetic, no
     * production Grids call, no reservation trick — just plain recursion.
     */
    private fun cleanRoomCount(rows: Int, cols: Int, path: IntArray, clues: BooleanArray, limit: Int): Int {
        val total = rows * cols
        val used = BooleanArray(total)
        var solutions = 0
        fun walk(number: Int, cell: Int) {
            if (solutions >= limit) return
            if (number == total) {
                solutions++
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
                    if (used[next]) continue
                    if (clues[number + 1] && next != path[number]) continue
                    used[next] = true
                    walk(number + 1, next)
                    used[next] = false
                    if (solutions >= limit) return
                }
            }
        }
        used[path[0]] = true
        walk(1, path[0])
        return solutions
    }
}
