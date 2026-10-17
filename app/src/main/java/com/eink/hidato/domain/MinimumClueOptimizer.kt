package com.eink.hidato.domain

/**
 * Exact, offline minimum-clue optimization for ONE fixed solved Hidato path.
 *
 * For every different valid tour T, some numbered clue in
 * D(T) = { n : T[n-1] != solution[n-1] } must be shown. The task
 * therefore is a minimum hitting set with a separation oracle:
 * the master proposes minimal clue sets under known cuts, and the
 * Hidato solver either proves uniqueness or supplies a new cut.
 */
class MinimumClueOptimizer(private val solver: HidatoSolver = HidatoSolver()) {
    enum class Stopped { PROVED_MINIMUM, TIME_LIMIT, MASTER_NODE_LIMIT, SOLVER_UNKNOWN }

    data class Result(
        val puzzle: Puzzle,
        val stopped: Stopped,
        val initialClues: Int,
        val finalClues: Int,
        val constraints: Int,
        val alternativeTours: Int,
        val solverCalls: Int,
        val masterNodes: Long,
        val elapsedMillis: Long,
    ) {
        val optimalityProven: Boolean get() = stopped == Stopped.PROVED_MINIMUM
    }

    /**
     * The upper bound must first be CERTIFIED unique, never merely assumed.
     * For up to 64 cells, the (at most) 62 optional numbers fit in a Long.
     * Larger boards use the existing certified greedy / offline catalog.
     */
    fun optimize(
        initial: Puzzle,
        timeLimitMillis: Long = 30_000,
        oracleNodeLimit: Long = 5_000_000,
        oracleTimeSliceMillis: Long = 2_000,
        maxMasterNodes: Long = 2_000_000,
    ): Result {
        val n = initial.total
        require(n in 4..64 && initial.rows >= 2 && initial.cols >= 2)
        require(initial.clues[1] && initial.clues[n])
        require(HamiltonianPaths.isValid(initial.path, initial.rows, initial.cols))
        require(timeLimitMillis in 1..3_600_000)
        require(oracleNodeLimit > 0 && oracleTimeSliceMillis > 0 && maxMasterNodes > 0)
        val started = System.nanoTime()
        val budgetNanos = timeLimitMillis * 1_000_000L
        fun remainingMillis(): Long = (budgetNanos - (System.nanoTime() - started)) / 1_000_000L
        fun elapsedMillis(): Long = (System.nanoTime() - started) / 1_000_000L
        var oracleCalls = 0
        fun query(clues: BooleanArray): AlternativeResult {
            oracleCalls++
            return solver.findAlternative(
                initial.rows, initial.cols, initial.path, clues,
                nodeLimit = oracleNodeLimit,
                timeLimitMillis = minOf(remainingMillis().coerceAtLeast(1L), oracleTimeSliceMillis),
            )
        }

        require(query(initial.clues) is AlternativeResult.Unique) {
            "Initial puzzle must be CERTIFIED unique; an unverified upper bound is unsafe"
        }
        var incumbentMask = 0L
        for (number in 2 until n) if (initial.clues[number]) incumbentMask = incumbentMask or bit(number)
        val initialClues = java.lang.Long.bitCount(incumbentMask) + 2
        val conflicts = ArrayList<Long>()
        for (witness in segmentReversalWitnesses(initial.path, initial.cols)) {
            addConstraint(conflicts, witness)
        }
        var witnesses = 0
        var totalMasterNodes = 0L
        var stop = Stopped.TIME_LIMIT

        while (remainingMillis() > 0) {
            val master = HittingSetMaster(
                conflicts,
                maxSelected = java.lang.Long.bitCount(incumbentMask) - 1,
                maximumNodes = maxMasterNodes - totalMasterNodes,
                deadline = started + budgetNanos,
            ).solve()
            totalMasterNodes += master.nodes
            when (master.kind) {
                HittingSetMaster.Kind.EXHAUSTED -> {
                    stop = Stopped.PROVED_MINIMUM
                    break
                }
                HittingSetMaster.Kind.UNKNOWN -> {
                    stop = if (remainingMillis() <= 0) Stopped.TIME_LIMIT else Stopped.MASTER_NODE_LIMIT
                    break
                }
                HittingSetMaster.Kind.FOUND -> {
                    val candidateMask = master.mask
                    val candidateClues = BooleanArray(n + 1)
                    candidateClues[1] = true
                    candidateClues[n] = true
                    for (number in 2 until n) candidateClues[number] = candidateMask and bit(number) != 0L
                    if (remainingMillis() <= 0) break
                    when (val answer = query(candidateClues)) {
                        is AlternativeResult.Found -> {
                            val witness = differenceMask(initial.path, answer.path)
                            check(witness != 0L && candidateMask and witness == 0L) {
                                "Alternative must differ at an un-clued number"
                            }
                            addConstraint(conflicts, witness)
                            witnesses++
                        }
                        is AlternativeResult.Unique -> {
                            incumbentMask = candidateMask
                            // All smaller hitting sets were rejected by the
                            // exact master. The separation oracle certifies
                            // this minimum candidate really is unique.
                            stop = Stopped.PROVED_MINIMUM
                            break
                        }
                        is AlternativeResult.Unknown -> {
                            stop = Stopped.SOLVER_UNKNOWN
                            break
                        }
                    }
                }
            }
        }
        val clues = BooleanArray(n + 1)
        clues[1] = true
        clues[n] = true
        for (number in 2 until n) clues[number] = incumbentMask and bit(number) != 0L
        return Result(
            puzzle = Puzzle(initial.rows, initial.cols, initial.path.copyOf(), clues),
            stopped = stop,
            initialClues = initialClues,
            finalClues = java.lang.Long.bitCount(incumbentMask) + 2,
            constraints = conflicts.size,
            alternativeTours = witnesses,
            solverCalls = oracleCalls,
            masterNodes = totalMasterNodes,
            elapsedMillis = elapsedMillis(),
        )
    }

    private fun bit(number: Int): Long = 1L shl (number - 2)

    private fun differenceMask(reference: IntArray, alternative: IntArray): Long {
        require(reference.size == alternative.size)
        var mask = 0L
        for (number in 2 until reference.size) {
            if (reference[number - 1] != alternative[number - 1]) mask = mask or bit(number)
        }
        return mask
    }

    /** Keep only inclusion-minimal cuts: hitting a subset also hits every superset. */
    private fun addConstraint(constraints: MutableList<Long>, newMask: Long) {
        if (newMask == 0L || constraints.any { it and newMask == it }) return
        constraints.removeAll { it and newMask == newMask }
        constraints.add(newMask)
    }

    /**
     * A reversed interior section is another valid tour when the two
     * new boundary edges are legal. Fixed endpoints never move.
     */
    internal fun segmentReversalWitnesses(path: IntArray, cols: Int): List<Long> {
        val masks = ArrayList<Long>()
        for (start in 1 until path.size - 2) {
            for (end in start + 1 until path.size - 1) {
                if (!Grids.adjacent(path[start - 1], path[end], cols) ||
                    !Grids.adjacent(path[start], path[end + 1], cols)
                ) continue
                var mask = 0L
                for (i in start..end) {
                    if (i != start + end - i) mask = mask or bit(i + 1)
                }
                if (mask != 0L) masks.add(mask)
            }
        }
        return masks
    }

    /** Exact minimum-cardinality hitting-set master with a global budget. */
    private class HittingSetMaster(
        private val constraints: List<Long>,
        private val maxSelected: Int,
        private val maximumNodes: Long,
        private val deadline: Long,
    ) {
        enum class Kind { FOUND, EXHAUSTED, UNKNOWN }
        data class Answer(val kind: Kind, val mask: Long = 0L, val nodes: Long = 0L)
        private var nodes = 0L
        private var unknown = false
        private var found: Long? = null
        private lateinit var visited: HashSet<Long>

        fun solve(): Answer {
            if (maxSelected < 0) return Answer(Kind.EXHAUSTED)
            for (bound in 0..maxSelected) {
                visited = HashSet()
                search(0L, 0, bound)
                if (found != null) return Answer(Kind.FOUND, found!!, nodes)
                if (unknown) return Answer(Kind.UNKNOWN, nodes = nodes)
            }
            return Answer(Kind.EXHAUSTED, nodes = nodes)
        }

        private fun search(mask: Long, selected: Int, bound: Int) {
            if (found != null || unknown) return
            nodes++
            if (nodes > maximumNodes || (nodes and 1023L) == 0L && System.nanoTime() >= deadline) {
                unknown = true
                return
            }
            if (!visited.add(mask)) return
            var target = 0L
            var minimumSize = Int.MAX_VALUE
            var lowerBound = 0
            var packed = 0L
            // Packing pairwise-disjoint unmet cuts is a valid lower bound.
            for (cut in constraints) {
                if (cut and mask != 0L) continue
                val size = java.lang.Long.bitCount(cut)
                if (size < minimumSize) {
                    minimumSize = size
                    target = cut
                }
                if (cut and packed == 0L) {
                    packed = packed or cut
                    lowerBound++
                }
            }
            if (target == 0L) {
                found = mask
                return
            }
            if (selected + lowerBound > bound || selected == bound) return
            var options = target
            while (options != 0L) {
                val choice = options and -options
                options = options xor choice
                search(mask or choice, selected + 1, bound)
                if (found != null || unknown) return
            }
        }
    }
}
