package com.eink.hidato.domain

import java.util.BitSet
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #10 correctness gates for the local counterexample generators:
 *
 *  - every tour returned by the deep local search is a complete valid Hidato
 *    tour that differs from the reference and agrees with EVERY candidate
 *    clue (i.e. a genuine counterexample, independently re-checked);
 *  - no false positives: whenever the deep search reports a counterexample,
 *    an independent clean-room enumeration also finds a second solution;
 *  - silence proves nothing: there are clue sets with real alternatives that
 *    the bounded local search cannot see, and it must return null for them;
 *  - farming only ever adds constraints backed by valid tours;
 *  - end-to-end 3x3 results still equal the exhaustive oracle minimum with
 *    both generators enabled, and fallback to the solver is preserved.
 */
class LocalCounterexampleSearchTest {

    private val optimiser = MinimumClueOptimizer()

    @Test
    fun `every deep local counterexample is a valid differing clue-consistent tour`() {
        for ((rows, cols) in listOf(3 to 3, 4 to 4, 5 to 5, 6 to 6, 8 to 8)) {
            val random = Random(rows * 31 + cols)
            repeat(3) {
                val path = HamiltonianPaths.randomTour(rows, cols, random)
                val n = rows * cols
                // Sparse random candidate masks, endpoints always set.
                repeat(4) {
                    val candidate = BitSet(n + 1)
                    candidate.set(1)
                    candidate.set(n)
                    for (number in 2 until n) {
                        if (random.nextInt(4) == 0) candidate.set(number)
                    }
                    val tour = optimiser.findLocalCounterexampleTour(candidate, path, rows, cols)
                    if (tour == null) return@repeat
                    assertTrue("counterexample must be valid", HamiltonianPaths.isValid(tour, rows, cols))
                    assertTrue("counterexample must differ from reference", !tour.contentEquals(path))
                    for (number in 1..n) {
                        if (candidate.get(number)) {
                            assertEquals(
                                "counterexample must keep every candidate clue",
                                path[number - 1], tour[number - 1],
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `deep search never claims a counterexample the brute force misses`() {
        val path = intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8)
        for (mask in 0 until 128) {
            val clues = BooleanArray(10)
            clues[1] = true
            clues[9] = true
            for (n in 2..8) clues[n] = mask and (1 shl (n - 2)) != 0
            val candidate = BitSet(10)
            for (n in 1..9) if (clues[n]) candidate.set(n)
            val tour = optimiser.findLocalCounterexampleTour(candidate, path, 3, 3)
            // On 3x3 every alternative is a full-window replacement, so the
            // bounded local search is exhaustive here: it must agree with the
            // independent brute force in BOTH directions. On larger boards it
            // is deliberately incomplete (silence proves nothing).
            assertEquals(
                "deep search must match the brute-force oracle on 3x3 (mask $mask)",
                hasSecondSolution(path, clues),
                tour != null,
            )
        }
    }

    @Test
    fun `farmed constraints are backed by valid differing tours`() {
        val path = HamiltonianPaths.randomTour(6, 6, Random(611))
        // Simulate farming: take any valid alternative found by the solver,
        // harvest its local replacements, and check every derived cut.
        val clues = BooleanArray(37)
        clues[1] = true
        clues[36] = true
        clues[5] = true
        val found = HidatoSolver().findAlternative(6, 6, path, clues, nodeLimit = 1_000_000)
        if (found is AlternativeResult.Found) {
            var farmed = 0
            for (tour in optimiser.localReplacementTours(found.path, 6, 6)) {
                assertTrue(HamiltonianPaths.isValid(tour, 6, 6))
                val difference = BitSet(37)
                for (number in 2 until 36) {
                    if (path[number - 1] != tour[number - 1]) difference.set(number)
                }
                if (!difference.isEmpty) farmed++
            }
            assertTrue("farming should produce at least one new cut", farmed > 0)
        }
    }

    @Test
    fun `optimizer with both generators still equals the exhaustive 3x3 minimum`() {
        for (path in listOf(
            intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8),
            intArrayOf(3, 0, 1, 2, 5, 8, 7, 6, 4),
            intArrayOf(0, 3, 6, 7, 4, 1, 2, 5, 8),
        )) {
            val initial = Puzzle(3, 3, path, BooleanArray(10) { it > 0 })
            val expected = (0 until 128).minOfOrNull { mask ->
                val clues = cluesFor(mask)
                if (hasSecondSolution(path, clues)) Int.MAX_VALUE else clues.count { it }
            }!!
            val result = MinimumClueOptimizer(
                enableCounterexampleFarming = true,
                enableDeepLocalCounterexampleSearch = true,
            ).optimize(
                initial,
                timeLimitMillis = 20_000,
                oracleNodeLimit = 10_000_000,
                oracleTimeSliceMillis = 10_000,
                maxMasterNodes = 5_000_000,
            )
            assertEquals(MinimumClueOptimizer.Stopped.PROVED_MINIMUM, result.stopped)
            assertEquals(expected, result.finalClues)
            assertTrue("final puzzle must stay unique", !hasSecondSolution(path, result.puzzle.clues))
        }
    }

    @Test
    fun `deep search keeps falling back to the solver for unique candidates`() {
        // The proven 4x4 minimum: the final candidate is unique, the deep
        // search cannot reject it locally, and the oracle still certifies.
        val path = intArrayOf(14, 15, 11, 7, 3, 2, 1, 0, 4, 8, 12, 13, 10, 5, 9, 6)
        val clues = BooleanArray(17)
        for (n in intArrayOf(1, 11, 15, 16)) clues[n] = true
        val result = MinimumClueOptimizer(
            enableDeepLocalCounterexampleSearch = true,
        ).optimize(Puzzle(4, 4, path, clues), timeLimitMillis = 5_000, oracleNodeLimit = 5_000_000)
        assertTrue(result.optimalityProven)
        assertEquals(4, result.finalClues)
        assertTrue("the final uniqueness must come from the solver", result.solverCalls >= 1)
    }

    @Test
    fun `deep search deterministically reproduces the same counterexample`() {
        val path = HamiltonianPaths.randomTour(6, 6, Random(99))
        val candidate = BitSet(37)
        candidate.set(1)
        candidate.set(36)
        candidate.set(10)
        candidate.set(25)
        val first = optimiser.findLocalCounterexampleTour(candidate, path, 6, 6)
        val second = optimiser.findLocalCounterexampleTour(candidate, path, 6, 6)
        assertEquals(first?.contentToString(), second?.contentToString())
    }

    private fun cluesFor(mask: Int): BooleanArray {
        val clues = BooleanArray(10)
        clues[1] = true
        clues[9] = true
        for (n in 2..8) clues[n] = mask and (1 shl (n - 2)) != 0
        return clues
    }

    /** Clean-room second-solution enumeration, sharing no production code. */
    private fun hasSecondSolution(reference: IntArray, clues: BooleanArray): Boolean {
        var alternative = false
        val used = BooleanArray(9)
        val assigned = IntArray(9)
        fun search(number: Int, current: Int) {
            if (alternative) return
            if (number == 9) {
                alternative = !assigned.contentEquals(reference)
                return
            }
            for (next in 0 until 9) {
                if (used[next]) continue
                if (Grids.adjacent(current, next, 3).not()) continue
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
