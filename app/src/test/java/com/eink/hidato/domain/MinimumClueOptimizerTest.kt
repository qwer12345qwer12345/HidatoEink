package com.eink.hidato.domain

import java.util.BitSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustively enumerate ALL clue subsets of tiny 3x3 examples using an
 * independent path oracle. If the optimizer reports PROVED_MINIMUM, it must
 * exactly equal the independently computed true minimum.
 */
class MinimumClueOptimizerTest {
    private val paths = listOf(
        intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8),
        intArrayOf(3, 0, 1, 2, 5, 8, 7, 6, 4),
        intArrayOf(0, 3, 6, 7, 4, 1, 2, 5, 8),
    )

    @Test
    fun `3x3 solution has mathematically minimum clue count`() {
        for (path in paths) {
            assertTrue(HamiltonianPaths.isValid(path, 3, 3))
            val initial = Puzzle(3, 3, path, BooleanArray(10) { it > 0 })
            val expected = (0 until 128).minOfOrNull { mask ->
                val clues = cluesForMask(mask)
                if (hasAlternativeByIndependentBruteForce(path, clues)) Int.MAX_VALUE
                else clues.count { it }
            }!!
            val result = MinimumClueOptimizer().optimize(
                initial,
                timeLimitMillis = 20_000,
                oracleNodeLimit = 10_000_000,
                oracleTimeSliceMillis = 10_000,
                maxMasterNodes = 5_000_000,
            )
            assertEquals(MinimumClueOptimizer.Stopped.PROVED_MINIMUM, result.stopped)
            assertEquals(expected, result.finalClues)
            assertFalse(hasAlternativeByIndependentBruteForce(path, result.puzzle.clues))
        }
    }

    @Test
    fun `4x4 path has a five clue globally minimum puzzle`() {
        // This fixed path exposes the difference between greedy deletion
        // and minimizing the number of clues globally for one known tour.
        val path = intArrayOf(2, 3, 7, 11, 15, 14, 10, 13, 12, 8, 9, 6, 1, 0, 5, 4)
        assertTrue(HamiltonianPaths.isValid(path, 4, 4))
        val result = MinimumClueOptimizer().optimize(
            Puzzle(4, 4, path, BooleanArray(17) { it > 0 }),
            timeLimitMillis = 10_000,
            oracleNodeLimit = 10_000_000,
            oracleTimeSliceMillis = 5_000,
            maxMasterNodes = 1_000_000,
        )
        assertTrue(result.optimalityProven)
        assertEquals(5, result.finalClues)
        assertTrue(
            HidatoSolver().findAlternative(4, 4, path, result.puzzle.clues,
                nodeLimit = 10_000_000, timeLimitMillis = 5_000) is AlternativeResult.Unique,
        )
    }

    @Test
    fun `limited master budget does not issue a false optimality proof`() {
        val path = paths[0]
        val result = MinimumClueOptimizer().optimize(
            Puzzle(3, 3, path, BooleanArray(10) { it > 0 }),
            maxMasterNodes = 1,
        )
        assertFalse(result.optimalityProven)
        assertEquals(9, result.finalClues)
        assertFalse(hasAlternativeByIndependentBruteForce(path, result.puzzle.clues))
    }

    @Test
    fun `every reversal cut corresponds to a real different full tour`() {
        val path = paths[0]
        val optimiser = MinimumClueOptimizer()
        val cuts = optimiser.segmentReversalWitnesses(path, 3).toSet()
        for (start in 1 until path.size - 2) {
            for (end in start + 1 until path.size - 1) {
                val reversed = path.copyOf().apply { reverse(start, end + 1) }
                val difference = BitSet(path.size + 1)
                for (number in 2 until path.size) {
                    if (reversed[number - 1] != path[number - 1]) difference.set(number)
                }
                if (difference in cuts) assertTrue(HamiltonianPaths.isValid(reversed, 3, 3))
            }
        }
    }

    @Test
    fun `bitsets distinguish clues beyond the old 64-cell ceiling`() {
        // No special-casing 8x8, 9x8 or 15x15: all use the same code path.
        for ((rows, cols) in listOf(8 to 8, 9 to 8, 15 to 15)) {
            val path = snakePath(rows, cols)
            val n = rows * cols
            assertTrue(HamiltonianPaths.isValid(path, rows, cols))
            val puzzle = Puzzle(rows, cols, path, BooleanArray(n + 1) { it > 0 })
            val result = MinimumClueOptimizer().optimize(
                puzzle,
                timeLimitMillis = 10_000,
                oracleNodeLimit = 1_000_000,
                oracleTimeSliceMillis = 3_000,
                maxMasterNodes = 1,
            )
            // The tight master budget may NOT produce a proof. The input
            // full-clue puzzle remains the safe certified upper bound.
            assertFalse(result.optimalityProven)
            assertEquals(n, result.finalClues)
            assertEquals(n, (1..n).count(result.puzzle::isClueVisible))
        }
    }

    @Test
    fun `reversal masks preserve digit positions above 64 without wraparound`() {
        val rows = 15
        val cols = 15
        val path = snakePath(rows, cols)
        val witnesses = MinimumClueOptimizer().segmentReversalWitnesses(path, cols)
        assertTrue("Large boards should produce nontrivial reversal constraints", witnesses.isNotEmpty())
        assertTrue("Some constraints must reference digits above 64",
            witnesses.any { it.nextSetBit(65) >= 65 })
        // Bit 65 and bit 1 are different: the previous Long approach would wrap.
        val mask = BitSet(226)
        mask.set(65)
        assertFalse(mask.get(1))
        assertTrue(mask.get(65))
    }

    private fun snakePath(rows: Int, cols: Int): IntArray =
        IntArray(rows * cols) { i ->
            val row = i / cols
            val column = i % cols
            row * cols + if (row % 2 == 0) column else cols - 1 - column
        }

    @Test
    fun `disjoint alternative sets certify a minimum without master search`() {
        val path = intArrayOf(14, 15, 11, 7, 3, 2, 1, 0, 4, 8, 12, 13, 10, 5, 9, 6)
        val clues = BooleanArray(17)
        for (n in intArrayOf(1, 11, 15, 16)) clues[n] = true
        val result = MinimumClueOptimizer().optimize(
            Puzzle(4, 4, path, clues),
            timeLimitMillis = 5_000,
            oracleNodeLimit = 5_000_000,
        )
        assertTrue(result.optimalityProven)
        assertEquals(4, result.finalClues)
        assertEquals(4, result.certifiedLowerBoundClues)
        assertEquals("The disjoint-path bound alone matches a unique upper bound", 0L, result.masterNodes)
        assertEquals("No second search is required", 1, result.solverCalls)
    }

    @Test
    fun `disjoint witness packing never overestimates minimum hitting set`() {
        val optimiser = MinimumClueOptimizer()
        val random = kotlin.random.Random(2707)
        repeat(100) {
            val cuts = (0 until 7).map {
                val cut = BitSet(9)
                repeat(3) { cut.set(2 + random.nextInt(7)) }
                cut
            }
            val lowerBound = optimiser.witnessLowerBound(cuts)
            val exhaustiveMinimum = (0 until 128)
                .filter { mask -> cuts.all { cut ->
                    (2..8).any { n -> (mask and (1 shl (n - 2))) != 0 && cut.get(n) }
                } }
                .minOf { Integer.bitCount(it) }
            assertTrue(
                "Every disjoint packing needs at least that many distinct chosen digits",
                lowerBound <= exhaustiveMinimum,
            )
        }
        val disjoint = listOf(
            BitSet().apply { set(2); set(3) },
            BitSet().apply { set(5); set(6) },
            BitSet().apply { set(8); set(9) },
        )
        assertEquals(3, optimiser.disjointWitnessLowerBound(disjoint))
        val triangle = listOf(
            BitSet().apply { set(2); set(3) },
            BitSet().apply { set(3); set(4) },
            BitSet().apply { set(2); set(4) },
        )
        assertEquals(1, optimiser.disjointWitnessLowerBound(triangle))
        assertEquals(2, optimiser.coverageWitnessLowerBound(triangle))
        assertEquals(2, optimiser.witnessLowerBound(triangle))
        assertEquals(0, optimiser.witnessLowerBound(emptyList()))
    }

    private fun cluesForMask(mask: Int): BooleanArray {
        val clues = BooleanArray(10)
        clues[1] = true
        clues[9] = true
        for (number in 2..8) clues[number] = mask and (1 shl (number - 2)) != 0
        return clues
    }

    /** Deliberately uses neither the production solver nor optimization pruning. */
    private fun hasAlternativeByIndependentBruteForce(reference: IntArray, clues: BooleanArray): Boolean {
        val n = 9
        val assigned = IntArray(n)
        val used = BooleanArray(n)
        var alternative = false
        fun search(number: Int, current: Int) {
            if (alternative) return
            if (number == n) {
                alternative = !assigned.contentEquals(reference)
                return
            }
            for (next in 0 until n) {
                if (used[next] || !Grids.adjacent(current, next, 3)) continue
                if (clues[number + 1] && next != reference[number]) continue
                used[next] = true
                assigned[number] = next
                search(number + 1, next)
                used[next] = false
                if (alternative) return
            }
        }
        assigned[0] = reference[0]
        used[reference[0]] = true
        search(1, reference[0])
        return alternative
    }
}
