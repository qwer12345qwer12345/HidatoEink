package com.eink.hidato.domain

import java.util.BitSet

/**
 * Test-source ablation harness for the exact minimum hitting-set master
 * (issue #11). Variants are experiment-only; production keeps a single
 * promoted implementation in [MinimumClueOptimizer.HittingSetMaster].
 *
 *  - [Variant.RESCAN] is the pre-change baseline, rescanning every
 *    constraint at every node (verbatim copy of the old master).
 *  - [Variant.COVERAGE_ORDER] keeps the shortest-uncovered-cut rule but
 *    tries the cut's digits by descending number of currently uncovered
 *    constraints they would hit (traversal order only).
 *  - [Variant.MOST_CONSTRAINED] branches on the active cut whose best digit
 *    covers the fewest other active cuts, i.e. the locally hardest cut
 *    (traversal order only, deterministic tie-breaking).
 *  - [Variant.FREQUENCY_BOUND] adds the safe ceil(active / max digit
 *    frequency) node bound on top of the disjoint packing bound.
 *
 * All variants must return the same optimal size and proof semantics;
 * [Variant.RESCAN] must match the production master node-for-node.
 */
internal class HittingSetAblationReference(
    private val constraints: List<BitSet>,
    private val minimumSelected: Int,
    private val maxSelected: Int,
    private val maximumNodes: Long,
    private val variant: Variant,
) {
    enum class Variant { RESCAN, COVERAGE_ORDER, MOST_CONSTRAINED, FREQUENCY_BOUND }

    data class Answer(
        val kind: MinimumClueOptimizer.HittingSetMaster.Kind,
        val mask: BitSet? = null,
        val nodes: Long = 0L,
    )

    private var nodes = 0L
    private var unknown = false
    private var found: BitSet? = null
    private lateinit var visited: HashSet<BitSet>

    fun solve(): Answer {
        if (maxSelected < 0) return Answer(MinimumClueOptimizer.HittingSetMaster.Kind.EXHAUSTED)
        for (bound in minimumSelected..maxSelected) {
            visited = HashSet()
            search(BitSet(), 0, bound)
            found?.let { return Answer(MinimumClueOptimizer.HittingSetMaster.Kind.FOUND, it, nodes) }
            if (unknown) return Answer(MinimumClueOptimizer.HittingSetMaster.Kind.UNKNOWN, nodes = nodes)
        }
        return Answer(MinimumClueOptimizer.HittingSetMaster.Kind.EXHAUSTED, nodes = nodes)
    }

    private fun search(selectedMask: BitSet, selected: Int, bound: Int) {
        if (found != null || unknown) return
        nodes++
        if (nodes > maximumNodes) {
            unknown = true
            return
        }

        if (!visited.add(selectedMask)) return
        var smallest: BitSet? = null
        var minimumSize = Int.MAX_VALUE
        var lowerBound = 0
        val packed = BitSet()
        for (cut in constraints) {
            if (selectedMask.intersects(cut)) continue
            val count = cut.cardinality()
            if (count < minimumSize) {
                minimumSize = count
                smallest = cut
            }
            if (!packed.intersects(cut)) {
                packed.or(cut)
                lowerBound++
            }
        }

        // Variant-only bookkeeping; RESCAN performs exactly the baseline scan.
        var activeFrequency: IntArray? = null
        if (variant != Variant.RESCAN) {
            val frequency = IntArray(maxDigit() + 1)
            var uncoveredCount = 0
            for (cut in constraints) {
                if (selectedMask.intersects(cut)) continue
                uncoveredCount++
                var digit = cut.nextSetBit(0)
                while (digit >= 0) {
                    frequency[digit]++
                    digit = cut.nextSetBit(digit + 1)
                }
            }
            activeFrequency = frequency
            if (variant == Variant.MOST_CONSTRAINED) {
                // Deterministic reselection: among active cuts, take the one
                // whose strongest digit covers the fewest other active cuts;
                // ties keep the earliest cut in list order.
                var candidate: BitSet? = null
                var candidateScore = Int.MAX_VALUE
                for (cut in constraints) {
                    if (selectedMask.intersects(cut)) continue
                    var digit = cut.nextSetBit(0)
                    var best = -1
                    while (digit >= 0) {
                        if (best < frequency[digit]) best = frequency[digit]
                        digit = cut.nextSetBit(digit + 1)
                    }
                    if (best < candidateScore) {
                        candidateScore = best
                        candidate = cut
                    }
                }
                smallest = candidate
            }
            if (variant == Variant.FREQUENCY_BOUND) {
                val maxFrequency = frequency.maxOrNull() ?: 0
                if (maxFrequency > 0) {
                    lowerBound = maxOf(lowerBound, (uncoveredCount + maxFrequency - 1) / maxFrequency)
                }
            }
        }

        val target = smallest
        if (target == null) {
            found = selectedMask
            return
        }
        if (selected + lowerBound > bound || selected == bound) return

        if (variant == Variant.COVERAGE_ORDER) {
            val frequency = requireNotNull(activeFrequency)
            val ordered = ArrayList<Int>()
            var digit = target.nextSetBit(0)
            while (digit >= 0) {
                ordered.add(digit)
                digit = target.nextSetBit(digit + 1)
            }
            ordered.sortByDescending { frequency[it] }
            for (choice in ordered) {
                val next = selectedMask.clone() as BitSet
                next.set(choice)
                search(next, selected + 1, bound)
                if (found != null || unknown) return
            }
            return
        }

        var choice = target.nextSetBit(0)
        while (choice >= 0) {
            val next = selectedMask.clone() as BitSet
            next.set(choice)
            search(next, selected + 1, bound)
            if (found != null || unknown) return
            choice = target.nextSetBit(choice + 1)
        }
    }

    private fun maxDigit(): Int = constraints.maxOfOrNull { it.length() } ?: 0
}
