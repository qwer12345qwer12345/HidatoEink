package com.eink.hidato.domain

import java.util.BitSet

/**
 * Exact, offline minimum-clue search for ONE fixed solved Hidato path.
 *
 * Every other valid tour T must differ at a given digit. Thus each
 * alternative tour creates a hitting-set constraint; the master searches
 * for the smallest set hitting all known constraints, and the Hidato solver
 * either finds a counterexample or certifies uniqueness.
 *
 * BitSet indexes are the digit numbers themselves, independent of board size.
 */
class MinimumClueOptimizer(private val solver: HidatoSolver = HidatoSolver()) {
    enum class Stopped { PROVED_MINIMUM, TIME_LIMIT, MASTER_NODE_LIMIT, SOLVER_UNKNOWN }

    data class Result(
        val puzzle: Puzzle,
        val stopped: Stopped,
        val initialClues: Int,
        val finalClues: Int,
        /** Provable lower bound, including the two mandatory endpoints. */
        val certifiedLowerBoundClues: Int,
        val constraints: Int,
        val alternativeTours: Int,
        val solverCalls: Int,
        val masterNodes: Long,
        val elapsedMillis: Long,
    ) {
        val optimalityProven: Boolean get() = stopped == Stopped.PROVED_MINIMUM
    }

    /**
     * An initial certified unique puzzle is a safe upper bound. There is no
     * cell-count restriction in the mathematical model or the bit-set storage;
     * large boards can still exhaust computational budgets before proving an
     * optimal minimum.
     */
    fun optimize(
        initial: Puzzle,
        timeLimitMillis: Long = 30_000,
        oracleNodeLimit: Long = 5_000_000,
        oracleTimeSliceMillis: Long = 2_000,
        maxMasterNodes: Long = 2_000_000,
    ): Result {
        val n = initial.total
        require(initial.rows >= 2 && initial.cols >= 2 && n >= 4)
        require(initial.clues[1] && initial.clues[n])
        require(HamiltonianPaths.isValid(initial.path, initial.rows, initial.cols))
        require(timeLimitMillis in 1..3_600_000)
        require(oracleNodeLimit > 0 && oracleTimeSliceMillis > 0 && maxMasterNodes > 0)

        val started = System.nanoTime()
        val deadline = started + timeLimitMillis * 1_000_000L
        fun remainingMillis(): Long = (deadline - System.nanoTime()) / 1_000_000L
        fun elapsedMillis(): Long = (System.nanoTime() - started) / 1_000_000L
        var oracleCalls = 0

        fun query(clues: BooleanArray): AlternativeResult {
            oracleCalls++
            return solver.findAlternative(
                initial.rows, initial.cols, initial.path, clues,
                nodeLimit = oracleNodeLimit,
                timeLimitMillis = minOf(remainingMillis().coerceAtLeast(1), oracleTimeSliceMillis),
            )
        }

        require(query(initial.clues) is AlternativeResult.Unique) {
            "Initial puzzle must be CERTIFIED unique; an unverified upper bound is unsafe"
        }

        var incumbent = BitSet(n + 1)
        for (number in 2 until n) if (initial.clues[number]) incumbent.set(number)
        val initialClues = incumbent.cardinality() + 2

        val conflicts = ArrayList<BitSet>()
        for (witness in segmentReversalWitnesses(initial.path, initial.cols)) {
            addConstraint(conflicts, witness)
        }

        // Every bounded local rerouting is a real alternative full tour,
        // hence a necessary hitting-set constraint (never a uniqueness proof).
        for (witness in localReroutingWitnesses(initial.path, initial.cols)) {
            addConstraint(conflicts, witness)
        }

        var alternatives = 0
        var masterNodes = 0L
        var stopped = Stopped.TIME_LIMIT
        var minimumOptional = 0

        while (remainingMillis() > 0) {
            // Each pairwise-disjoint alternative-path difference set needs
            // a distinct given digit. If that count reaches the certified
            // upper bound, minimality follows without searching the master.
            minimumOptional = witnessLowerBound(conflicts)
            if (minimumOptional >= incumbent.cardinality()) {
                stopped = Stopped.PROVED_MINIMUM
                break
            }
            val master = HittingSetMaster(
                constraints = conflicts,
                minimumSelected = minimumOptional,
                maxSelected = incumbent.cardinality() - 1,
                maximumNodes = maxMasterNodes - masterNodes,
                deadline = deadline,
            ).solve()
            masterNodes += master.nodes

            when (master.kind) {
                HittingSetMaster.Kind.EXHAUSTED -> {
                    stopped = Stopped.PROVED_MINIMUM
                    break
                }
                HittingSetMaster.Kind.UNKNOWN -> {
                    stopped = if (remainingMillis() <= 0) Stopped.TIME_LIMIT else Stopped.MASTER_NODE_LIMIT
                    break
                }
                HittingSetMaster.Kind.FOUND -> {
                    val candidate = requireNotNull(master.mask)
                    if (remainingMillis() <= 0) break
                    when (val answer = query(toClues(candidate, n))) {
                        is AlternativeResult.Found -> {
                            val difference = differenceMask(initial.path, answer.path)
                            check(!difference.isEmpty && !candidate.intersects(difference)) {
                                "The second solution must disagree on an un-clued digit"
                            }
                            addConstraint(conflicts, difference)
                            alternatives++
                        }
                        is AlternativeResult.Unique -> {
                            incumbent = candidate
                            // The master found a minimum hitting set under
                            // necessary constraints; the oracle proved this
                            // candidate satisfies ALL alternative constraints.
                            stopped = Stopped.PROVED_MINIMUM
                            break
                        }
                        is AlternativeResult.Unknown -> {
                            stopped = Stopped.SOLVER_UNKNOWN
                            break
                        }
                    }
                }
            }
        }

        return Result(
            puzzle = Puzzle(initial.rows, initial.cols, initial.path.copyOf(), toClues(incumbent, n)),
            stopped = stopped,
            initialClues = initialClues,
            finalClues = incumbent.cardinality() + 2,
            certifiedLowerBoundClues = if (stopped == Stopped.PROVED_MINIMUM) incumbent.cardinality() + 2 else minimumOptional + 2,
            constraints = conflicts.size,
            alternativeTours = alternatives,
            solverCalls = oracleCalls,
            masterNodes = masterNodes,
            elapsedMillis = elapsedMillis(),
        )
    }

    private fun toClues(numbers: BitSet, total: Int): BooleanArray {
        val clues = BooleanArray(total + 1)
        clues[1] = true
        clues[total] = true
        var number = numbers.nextSetBit(2)
        while (number >= 0 && number < total) {
            clues[number] = true
            number = numbers.nextSetBit(number + 1)
        }
        return clues
    }

    private fun differenceMask(reference: IntArray, alternative: IntArray): BitSet {
        require(reference.size == alternative.size)
        val difference = BitSet(reference.size + 1)
        for (number in 2 until reference.size) {
            if (reference[number - 1] != alternative[number - 1]) difference.set(number)
        }
        return difference
    }

    /** A ⊆ B iff A \ B is empty; all inputs remain unmodified. */
    private fun subsetOf(a: BitSet, b: BitSet): Boolean {
        val remaining = a.clone() as BitSet
        remaining.andNot(b)
        return remaining.isEmpty
    }

    /** Keep only inclusion-minimal cuts; their logical meaning is unchanged. */
    private fun addConstraint(constraints: MutableList<BitSet>, newMask: BitSet) {
        if (newMask.isEmpty || constraints.any { subsetOf(it, newMask) }) return
        constraints.removeAll { subsetOf(newMask, it) }
        constraints.add(newMask.clone() as BitSet)
    }

    /**
     * Reversing any interior path segment gives a DIFFERENT valid full path
     * when both new boundary edges are legal. Each changed digit must be
     * distinguished by at least one given clue.
     */
    internal fun segmentReversalWitnesses(path: IntArray, cols: Int): List<BitSet> {
        val witnesses = ArrayList<BitSet>()
        for (start in 1 until path.size - 2) {
            for (end in start + 1 until path.size - 1) {
                if (!Grids.adjacent(path[start - 1], path[end], cols) ||
                    !Grids.adjacent(path[start], path[end + 1], cols)
                ) continue
                val changed = BitSet(path.size + 1)
                for (i in start..end) {
                    if (i * 2 != start + end) changed.set(i + 1)
                }
                if (!changed.isEmpty) witnesses.add(changed)
            }
        }
        return witnesses
    }

    /**
     * Reorder the SAME cells inside each interior digit window (2..6 cells).
     * The two boundary cells stay fixed; any adjacent permutation therefore
     * produces a different, fully legal Hamiltonian tour with fixed endpoints.
     * The changed digits form a necessary (not sufficient) uniqueness cut.
     *
     * The window cap is constant: O(N * sum(k*k!), k=2..6) time to enumerate
     * candidates, excluding later hitting-set cut minimization.
     */
    internal fun localReroutingWitnesses(path: IntArray, cols: Int): List<BitSet> {
        val witnesses = LinkedHashSet<BitSet>()
        for (length in 2..minOf(6, path.size - 2)) {
            for (start in 1 until path.size - length) {
                val cells = path.copyOfRange(start, start + length)
                val order = IntArray(length)
                val used = BooleanArray(length)
                val left = path[start - 1]
                val right = path[start + length]

                fun enumerate(depth: Int, previous: Int) {
                    if (depth == length) {
                        if (!Grids.adjacent(previous, right, cols)) return
                        val changed = BitSet(path.size + 1)
                        for (offset in 0 until length) {
                            if (order[offset] != cells[offset]) changed.set(start + offset + 1)
                        }
                        if (!changed.isEmpty) witnesses.add(changed)
                        return
                    }
                    for (i in cells.indices) {
                        if (used[i] || !Grids.adjacent(previous, cells[i], cols)) continue
                        used[i] = true
                        order[depth] = cells[i]
                        enumerate(depth + 1, cells[i])
                        used[i] = false
                    }
                }
                enumerate(0, left)
            }
        }
        return witnesses.toList()
    }

    /**
     * Any family of disjoint difference sets needs at least one DIFFERENT
     * clue per set. This greedy packing is only a lower bound, not an
     * attempt to find the maximum packing. Sorting smallest first is a
     * cheap deterministic choice, and never compromises the proof.
     */
    internal fun disjointWitnessLowerBound(cuts: List<BitSet>): Int {
        val occupied = BitSet()
        var count = 0
        for (cut in cuts.sortedBy(BitSet::cardinality)) {
            if (!occupied.intersects(cut)) {
                occupied.or(cut)
                count++
            }
        }
        return count
    }

    /** Each digit covers at most maxFrequency cuts; m cuts need ceil(m/maxFrequency) digits. */
    internal fun coverageWitnessLowerBound(cuts: List<BitSet>): Int {
        if (cuts.isEmpty()) return 0
        val frequencies = IntArray(cuts.maxOf { it.length() })
        var maxFrequency = 0
        for (cut in cuts) {
            var digit = cut.nextSetBit(0)
            while (digit >= 0) {
                val frequency = ++frequencies[digit]
                if (frequency > maxFrequency) maxFrequency = frequency
                digit = cut.nextSetBit(digit + 1)
            }
        }
        check(maxFrequency > 0) { "Difference sets must be nonempty" }
        return (cuts.size + maxFrequency - 1) / maxFrequency
    }

    internal fun witnessLowerBound(cuts: List<BitSet>): Int =
        maxOf(disjointWitnessLowerBound(cuts), coverageWitnessLowerBound(cuts))

    /** Exact minimum-cardinality hitting-set master with a node/time budget. */
    private class HittingSetMaster(
        private val constraints: List<BitSet>,
        private val minimumSelected: Int,
        private val maxSelected: Int,
        private val maximumNodes: Long,
        private val deadline: Long,
    ) {
        enum class Kind { FOUND, EXHAUSTED, UNKNOWN }
        data class Answer(val kind: Kind, val mask: BitSet? = null, val nodes: Long = 0L)
        private var nodes = 0L
        private var unknown = false
        private var found: BitSet? = null
        private lateinit var visited: HashSet<BitSet>

        fun solve(): Answer {
            if (maxSelected < 0) return Answer(Kind.EXHAUSTED)
            // Every cardinality below minimumSelected is already ruled out
            // by the disjoint-set proof; enumerating it only repeats work.
            for (bound in minimumSelected..maxSelected) {
                visited = HashSet()
                search(BitSet(), 0, bound)
                found?.let { return Answer(Kind.FOUND, it, nodes) }
                if (unknown) return Answer(Kind.UNKNOWN, nodes = nodes)
            }
            return Answer(Kind.EXHAUSTED, nodes = nodes)
        }

        private fun search(selectedMask: BitSet, selected: Int, bound: Int) {
            if (found != null || unknown) return
            nodes++
            if (nodes > maximumNodes ||
                (nodes and 1023L) == 0L && System.nanoTime() >= deadline
            ) {
                unknown = true
                return
            }

            // All BitSet keys are immutable after insertion. Branches clone.
            if (!visited.add(selectedMask)) return
            var smallest: BitSet? = null
            var minimumSize = Int.MAX_VALUE
            var lowerBound = 0
            val packed = BitSet()
            // Pairwise disjoint uncovered constraints each require one
            // distinct number, giving a safe (possibly weak) lower bound.
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

            val target = smallest
            if (target == null) {
                found = selectedMask
                return
            }
            if (selected + lowerBound > bound || selected == bound) return

            var choice = target.nextSetBit(0)
            while (choice >= 0) {
                val next = selectedMask.clone() as BitSet
                next.set(choice)
                search(next, selected + 1, bound)
                if (found != null || unknown) return
                choice = target.nextSetBit(choice + 1)
            }
        }
    }
}
