package com.eink.hidato.domain

import java.util.BitSet
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #11 correctness gates for the incremental coverage accounting in
 * [MinimumClueOptimizer.HittingSetMaster]:
 *
 *  - it must traverse exactly like the old full-rescan implementation
 *    (same answer, same mask, same node count);
 *  - every ablation variant must return the exhaustive minimum;
 *  - budget exhaustion must report UNKNOWN, never EXHAUSTED.
 */
class HittingSetMasterIncrementalTest {

    private fun master(
        constraints: List<BitSet>,
        minimumSelected: Int = 0,
        maxSelected: Int,
        maximumNodes: Long = Long.MAX_VALUE / 2,
    ): MinimumClueOptimizer.HittingSetMaster.Answer =
        MinimumClueOptimizer.HittingSetMaster(
            constraints, minimumSelected, maxSelected, maximumNodes, Long.MAX_VALUE,
        ).solve()

    private fun reference(
        constraints: List<BitSet>,
        minimumSelected: Int = 0,
        maxSelected: Int,
        maximumNodes: Long = Long.MAX_VALUE / 2,
        variant: HittingSetAblationReference.Variant = HittingSetAblationReference.Variant.RESCAN,
    ): HittingSetAblationReference.Answer =
        HittingSetAblationReference(constraints, minimumSelected, maxSelected, maximumNodes, variant).solve()

    /** Independent minimum hitting set by full subset enumeration. */
    private fun exhaustiveMinimum(constraints: List<BitSet>, universe: Int): Int {
        var best = Int.MAX_VALUE
        for (mask in 0 until (1 shl universe)) {
            if (Integer.bitCount(mask) >= best) continue
            val hits = constraints.all { cut ->
                var digit = cut.nextSetBit(0)
                var covered = false
                while (digit >= 0) {
                    if (digit < universe && mask and (1 shl digit) != 0) covered = true
                    digit = cut.nextSetBit(digit + 1)
                }
                covered
            }
            if (hits) best = Integer.bitCount(mask)
        }
        return best
    }

    private fun randomHypergraph(random: Random, digits: Int, cuts: Int, maxCutSize: Int): List<BitSet> {
        val constraints = ArrayList<BitSet>(cuts)
        repeat(cuts) {
            val cut = BitSet(digits + 1)
            val size = 2 + random.nextInt(maxCutSize - 1)
            repeat(size) { cut.set(2 + random.nextInt(digits - 1)) }
            if (!cut.isEmpty) constraints.add(cut)
        }
        return constraints
    }

    @Test
    fun `incremental master traverses exactly like the rescan baseline`() {
        val random = Random(4211)
        repeat(40) {
            val constraints = randomHypergraph(random, digits = 10, cuts = 8, maxCutSize = 4)
            for ((lo, hi) in listOf(0 to 12, 2 to 5, 3 to 3, 0 to 0, 5 to 4)) {
                val production = master(constraints, lo, hi)
                val baseline = reference(constraints, lo, hi)
                assertEquals("kind mismatch on $constraints", baseline.kind, production.kind)
                assertEquals("nodes mismatch on $constraints", baseline.nodes, production.nodes)
                assertEquals(baseline.mask, production.mask)
            }
        }
    }

    @Test
    fun `every variant returns the exhaustive minimum`() {
        val random = Random(991)
        repeat(30) {
            val constraints = randomHypergraph(random, digits = 9, cuts = 9, maxCutSize = 4)
            if (constraints.isEmpty()) return@repeat
            val expected = exhaustiveMinimum(constraints, 11)
            for (variant in HittingSetAblationReference.Variant.entries) {
                val answer = reference(constraints, 0, 11, variant = variant)
                assertEquals("$variant must find the exhaustive minimum", expected, answer.mask?.cardinality())
                assertEquals(
                    "$variant must find the exhaustive minimum",
                    MinimumClueOptimizer.HittingSetMaster.Kind.FOUND,
                    answer.kind,
                )
            }
            val production = master(constraints, 0, 11)
            assertEquals(expected, production.mask?.cardinality())
        }
    }

    @Test
    fun `larger hypergraphs keep all variants in agreement`() {
        val random = Random(73)
        repeat(6) {
            val constraints = randomHypergraph(random, digits = 16, cuts = 24, maxCutSize = 5)
            val baseline = reference(constraints, 0, 18)
            for (variant in HittingSetAblationReference.Variant.entries) {
                val answer = reference(constraints, 0, 18, variant = variant)
                assertEquals(baseline.kind, answer.kind)
                assertEquals(baseline.mask?.cardinality(), answer.mask?.cardinality())
                answer.mask?.let { mask ->
                    assertTrue("every reported mask must hit every cut", constraints.all { it.intersects(mask) })
                }
            }
        }
    }

    @Test
    fun `node budget exhaustion reports UNKNOWN and never a proof`() {
        val random = Random(5)
        // A block-heavy family whose search cannot finish in one node.
        val constraints = randomHypergraph(random, digits = 12, cuts = 16, maxCutSize = 4)
        val production = master(constraints, 0, 10, maximumNodes = 1)
        assertEquals(MinimumClueOptimizer.HittingSetMaster.Kind.UNKNOWN, production.kind)
        val baseline = reference(constraints, 0, 10, maximumNodes = 1)
        assertEquals(MinimumClueOptimizer.HittingSetMaster.Kind.UNKNOWN, baseline.kind)
    }

    @Test
    fun `empty and trivially satisfied constraint families are handled`() {
        // No constraints: the empty selection is a complete hitting set.
        val empty = master(emptyList(), 0, 0)
        assertEquals(MinimumClueOptimizer.HittingSetMaster.Kind.FOUND, empty.kind)
        assertEquals(0, empty.mask?.cardinality())
        // maxSelected < 0 keeps the EXHAUSTED short-circuit of the baseline.
        assertEquals(
            MinimumClueOptimizer.HittingSetMaster.Kind.EXHAUSTED,
            master(emptyList(), 0, -1).kind,
        )
        val single = listOf(BitSet().apply { set(2); set(3) })
        val answer = master(single, 0, 1)
        assertEquals(MinimumClueOptimizer.HittingSetMaster.Kind.FOUND, answer.kind)
        assertEquals(1, answer.mask?.cardinality())
    }
}
