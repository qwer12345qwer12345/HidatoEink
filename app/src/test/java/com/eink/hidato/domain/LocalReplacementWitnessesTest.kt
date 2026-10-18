package com.eink.hidato.domain

import java.util.BitSet
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every seeded local-replacement constraint must be backed by a strictly
 * different, complete, valid Hidato tour. A constraint without such a
 * witness would be unsound for the hitting-set model.
 */
class LocalReplacementWitnessesTest {

    private val optimiser = MinimumClueOptimizer()

    @Test
    fun `every replacement tour is a valid different full path`() {
        for ((rows, cols) in listOf(3 to 3, 4 to 4, 5 to 5, 6 to 6, 8 to 8)) {
            val random = Random(rows * 100 + cols)
            repeat(3) {
                val path = HamiltonianPaths.randomTour(rows, cols, random)
                val tours = optimiser.localReplacementTours(path, rows, cols)
                assertTrue("no replacements found on $rows x $cols", tours.isNotEmpty())
                for (tour in tours) {
                    assertTrue(HamiltonianPaths.isValid(tour, rows, cols))
                    assertTrue(tour.contentEquals(path).not())
                    // The tour keeps the endpoints of the reference path.
                    assertEquals(path.first(), tour.first())
                    assertEquals(path.last(), tour.last())
                }
            }
        }
    }

    @Test
    fun `witnesses are exactly the difference sets of the tours`() {
        val path = HamiltonianPaths.randomTour(6, 6, Random(31))
        val tours = optimiser.localReplacementTours(path, 6, 6)
        val witnesses = optimiser.localReplacementWitnesses(path, 6, 6).toSet()
        // Several distinct orderings of the same window can change exactly
        // the same digits, so witnesses are the DISTINCT difference sets.
        val distinct = tours.map { tour ->
            val difference = BitSet(path.size + 1)
            for (number in 2 until path.size) {
                if (path[number - 1] != tour[number - 1]) difference.set(number)
            }
            difference
        }.toSet()
        assertEquals(distinct.size, witnesses.size)
        assertTrue(distinct.all { it in witnesses })
    }

    @Test
    fun `replacement witnesses never exceed the exhaustive 3x3 minimum`() {
        val paths = listOf(
            intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8),
            intArrayOf(3, 0, 1, 2, 5, 8, 7, 6, 4),
            intArrayOf(0, 3, 6, 7, 4, 1, 2, 5, 8),
        )
        for (path in paths) {
            val initial = Puzzle(3, 3, path, BooleanArray(10) { it > 0 })
            val result = MinimumClueOptimizer().optimize(
                initial,
                timeLimitMillis = 20_000,
                oracleNodeLimit = 10_000_000,
                oracleTimeSliceMillis = 10_000,
                maxMasterNodes = 5_000_000,
            )
            assertEquals(MinimumClueOptimizer.Stopped.PROVED_MINIMUM, result.stopped)
            // The exhaustive minimum for these fixtures is verified by
            // MinimumClueOptimizerTest; the seeded constraints must never
            // push the optimizer below it (impossible if sound).
            assertTrue(result.finalClues >= 3)
        }
    }

    @Test
    fun `seeding keeps the constraint family at least as strong`() {
        val path = HamiltonianPaths.randomTour(5, 5, Random(77))
        val reversals = optimiser.segmentReversalWitnesses(path, 5)
        val combined = ArrayList<BitSet>()
        val seeded = optimiser.localReplacementWitnesses(path, 5, 5)
        // Reproduce addConstraint minimisation over seeds + reversals.
        for (mask in seeded + reversals) {
            if (mask.isEmpty || combined.any { isSubset(it, mask) }) continue
            combined.removeAll { isSubset(mask, it) }
            combined.add(mask.clone() as BitSet)
        }
        val reversalsOnly = ArrayList<BitSet>()
        for (mask in reversals) {
            if (mask.isEmpty || reversalsOnly.any { isSubset(it, mask) }) continue
            reversalsOnly.removeAll { isSubset(mask, it) }
            reversalsOnly.add(mask.clone() as BitSet)
        }
        val lowerBoundSeeded = optimiser.disjointWitnessLowerBound(combined)
        val lowerBoundReversal = optimiser.disjointWitnessLowerBound(reversalsOnly)
        assertTrue(
            "seeding must not weaken the certified lower bound",
            lowerBoundSeeded >= lowerBoundReversal,
        )
    }

    private fun isSubset(a: BitSet, b: BitSet): Boolean {
        val rest = a.clone() as BitSet
        rest.andNot(b)
        return rest.isEmpty
    }
}
