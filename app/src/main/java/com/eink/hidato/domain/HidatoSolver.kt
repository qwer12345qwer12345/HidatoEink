package com.eink.hidato.domain

/** The known complete path proves that at least one solution exists. */
sealed interface AlternativeResult {
    val nodes: Long

    data class Found(val path: IntArray, override val nodes: Long) : AlternativeResult
    data class Unique(override val nodes: Long) : AlternativeResult
    data class Unknown(override val nodes: Long) : AlternativeResult
}

/**
 * Exact Hidato path search, subject to explicit node/time budgets.
 * Every given digit reserves its cell throughout the search, including
 * before the DFS reaches that digit. UNKNOWN is never considered unique.
 * Every invocation has its own independent search workspace.
 *
 * @param freeSpaceReachability research toggle (issue #6, direction A):
 * provably-safe geometric reachability pruning. The suffix from any partial
 * path to the next given digit can only cross cells that are currently free
 * (unvisited and reserved by no other given); a breadth-first shortest path
 * over exactly those cells is therefore a valid lower bound on the remaining
 * edge count, and strictly dominates the pure Chebyshev bound.
 */
open class HidatoSolver(
    private val freeSpaceReachability: Boolean = false,
) {
    companion object {
        const val DEFAULT_NODE_LIMIT = 400_000L

        /** Per-candidate BFS only pays off for close targets; farther gaps are covered by the periodic checkpoint. */
        internal const val CANDIDATE_REACH_BUDGET_CAP = 8
    }

    open fun findAlternative(
        rows: Int,
        cols: Int,
        path: IntArray,
        clues: BooleanArray,
        nodeLimit: Long = DEFAULT_NODE_LIMIT,
        timeLimitMillis: Long = Long.MAX_VALUE,
    ): AlternativeResult {
        val search = Search(rows, cols, path, clues, nodeLimit, timeLimitMillis, findAlternative = true, countLimit = 1, reachability = freeSpaceReachability)
        search.run()
        return when {
            search.alternative != null -> AlternativeResult.Found(search.alternative!!, search.nodes)
            search.exhausted -> AlternativeResult.Unknown(search.nodes)
            else -> AlternativeResult.Unique(search.nodes)
        }
    }

    /** Return -1 when the node budget prevented complete counting (up to limit). */
    fun countSolutions(
        rows: Int,
        cols: Int,
        path: IntArray,
        clues: BooleanArray,
        limit: Int = 2,
        nodeLimit: Long = DEFAULT_NODE_LIMIT,
    ): Int {
        require(limit > 0)
        val search = Search(rows, cols, path, clues, nodeLimit, Long.MAX_VALUE, findAlternative = false, countLimit = limit, reachability = freeSpaceReachability)
        search.run()
        return if (search.exhausted) -1 else search.solutionCount
    }

    fun isUniqueSolution(
        rows: Int,
        cols: Int,
        path: IntArray,
        clues: BooleanArray,
        nodeLimit: Long = DEFAULT_NODE_LIMIT,
    ): Boolean = findAlternative(rows, cols, path, clues, nodeLimit) is AlternativeResult.Unique

    private class Search(
        private val rows: Int,
        private val cols: Int,
        private val reference: IntArray,
        clues: BooleanArray,
        private val nodeLimit: Long,
        timeLimitMillis: Long,
        private val findAlternative: Boolean,
        private val countLimit: Int,
        private val reachability: Boolean,
    ) {
        private val total = rows * cols
        private val neighbors: Array<IntArray>
        private val reservedBy: IntArray
        private val givenPosition: IntArray
        private val nextGiven: IntArray
        private val visited = BooleanArray(total)
        private val assignment = IntArray(total)
        private val seen = IntArray(total)
        private val queue = IntArray(total)
        private var seenStamp = 0
        private val startedAt = System.nanoTime()
        private val timeBudgetNanos = if (timeLimitMillis >= Long.MAX_VALUE / 1_000_000) Long.MAX_VALUE else timeLimitMillis.coerceAtLeast(0) * 1_000_000
        var nodes = 0L
            private set
        var solutionCount = 0
            private set
        var alternative: IntArray? = null
            private set
        var exhausted = false
            private set

        init {
            require(rows > 0 && cols > 0 && total >= 2)
            require(HamiltonianPaths.isValid(reference, rows, cols)) { "参考答案不是合法完整路径" }
            require(clues.size == total + 1 && clues[1]) { "线索必须包含数字 1" }
            require(nodeLimit > 0)
            neighbors = Array(total) { Grids.neighbors(it, rows, cols).toIntArray() }
            reservedBy = IntArray(total)
            givenPosition = IntArray(total + 1) { -1 }
            nextGiven = IntArray(total + 1) { -1 }
            for (number in 1..total) {
                if (clues[number]) {
                    val cell = reference[number - 1]
                    reservedBy[cell] = number
                    givenPosition[number] = cell
                }
            }
            var next = -1
            for (number in total downTo 1) {
                nextGiven[number] = next
                if (givenPosition[number] >= 0) next = number
            }
        }

        fun run() {
            assignment[0] = reference[0]
            visited[reference[0]] = true
            dfs(1, reference[0], differs = false)
        }

        private fun stop(): Boolean {
            if (alternative != null || solutionCount >= countLimit || exhausted) return true
            nodes++
            if (nodes > nodeLimit || (nodes and 127L) == 0L && System.nanoTime() - startedAt >= timeBudgetNanos) {
                exhausted = true
                return true
            }
            return false
        }

        private fun dfs(number: Int, cell: Int, differs: Boolean) {
            if (stop()) return
            if (number == total) {
                if (findAlternative) {
                    if (differs) alternative = assignment.copyOf()
                } else {
                    solutionCount++
                }
                return
            }
            val nextNumber = number + 1
            val fixedNext = givenPosition[nextNumber]
            if (fixedNext >= 0) {
                if (!visited[fixedNext] && Grids.adjacent(cell, fixedNext, cols)) {
                    follow(nextNumber, fixedNext, differs)
                }
                return
            }
            // Try deviations before the reference edge: genuine counterexamples appear sooner.
            val refNext = reference[nextNumber - 1]
            if (findAlternative) {
                for (candidate in neighbors[cell]) {
                    if (candidate != refNext) tryCandidate(number, candidate, differs)
                    if (alternative != null || exhausted) return
                }
                if (Grids.adjacent(cell, refNext, cols)) tryCandidate(number, refNext, differs)
            } else {
                for (candidate in neighbors[cell]) {
                    tryCandidate(number, candidate, differs)
                    if (solutionCount >= countLimit || exhausted) return
                }
            }
        }

        private fun tryCandidate(number: Int, candidate: Int, differs: Boolean) {
            // No future given number can be used as an intermediate free square.
            if (visited[candidate] || reservedBy[candidate] != 0) return
            val nextNumber = number + 1
            val targetNumber = nextGiven[nextNumber]
            if (targetNumber >= 0) {
                val remainingSteps = targetNumber - nextNumber
                if (Grids.chebyshev(candidate, givenPosition[targetNumber], cols) > remainingSteps) return
                // Geometric reachability (direction A): the suffix from the
                // candidate to the next given may only step on cells that are
                // free right now, so the free-space BFS distance is a valid
                // (strictly stronger than Chebyshev) lower bound. Bounded by
                // the remaining-step budget, the BFS work stays local.
                if (reachability && remainingSteps <= CANDIDATE_REACH_BUDGET_CAP &&
                    !reachesTargetWithin(candidate, givenPosition[targetNumber], remainingSteps)
                ) return
            }
            follow(nextNumber, candidate, differs || candidate != reference[number])
        }

        private fun follow(number: Int, cell: Int, differs: Boolean) {
            visited[cell] = true
            assignment[number - 1] = cell
            // All unused cells must form one connected suffix in any completion.
            if (number == total || number % 5 != 0 || suffixStaysFeasible(number, cell)) {
                dfs(number, cell, differs)
            }
            visited[cell] = false
        }

        private fun suffixStaysFeasible(used: Int, current: Int): Boolean {
            if (!remainingConnected(used)) return false
            if (!reachability) return true
            // nextGiven[x] is the smallest given STRICTLY BEYOND x; when the
            // immediate next number is itself a given, its reserved cell is a
            // mandatory waypoint, not an obstacle, so target that digit.
            val nextNumber = used + 1
            val targetNumber = if (givenPosition[nextNumber] >= 0) nextNumber else nextGiven[nextNumber]
            if (targetNumber < 0) return true
            return reachesTargetWithin(current, givenPosition[targetNumber], targetNumber - used)
        }

        /**
         * True when a path of at most [edgeBudget] edges from [from] to
         * [target] exists whose intermediate cells are all currently free
         * (unvisited and reserved by no given). Soundness: any completion
         * places the numbers strictly between the current one and the target
         * on distinct cells that are free now and never reserved for another
         * given, so its prefix towards the target lives in exactly this
         * subgraph. Level-order BFS shares the workspace buffers with
         * [remainingConnected]; the stamp isolates concurrent markers.
         */
        private fun reachesTargetWithin(from: Int, target: Int, edgeBudget: Int): Boolean {
            if (from == target) return true
            if (edgeBudget <= 0) return false
            val stamp = ++seenStamp
            var head = 0
            var tail = 0
            queue[tail++] = from
            seen[from] = stamp
            var depth = 0
            while (depth < edgeBudget && head < tail) {
                val levelEnd = tail
                while (head < levelEnd) {
                    for (candidate in neighbors[queue[head++]]) {
                        if (candidate == target) return true
                        if (seen[candidate] == stamp || visited[candidate] || reservedBy[candidate] != 0) continue
                        seen[candidate] = stamp
                        queue[tail++] = candidate
                    }
                }
                depth++
            }
            return false
        }

        private fun remainingConnected(used: Int): Boolean {
            val free = total - used
            if (free == 0) return true
            var start = -1
            for (i in 0 until total) {
                if (!visited[i]) { start = i; break }
            }
            if (start < 0) return true
            val stamp = ++seenStamp
            var head = 0
            var tail = 1
            queue[0] = start
            seen[start] = stamp
            while (head < tail) {
                for (candidate in neighbors[queue[head++]]) {
                    if (!visited[candidate] && seen[candidate] != stamp) {
                        queue[tail++] = candidate
                        seen[candidate] = stamp
                    }
                }
            }
            return tail == free
        }
    }
}
