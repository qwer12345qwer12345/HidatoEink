package com.eink.hidato.domain

import kotlin.math.ceil
import kotlin.random.Random

/** Counters show which deductions actually reduced solver work. */
data class GenerationStats(
    val retainedClues: Int,
    val verifiedRemovals: Int,
    val rejectedByWitness: Int,
    val rejectedBySolver: Int,
    val unresolvedChecks: Int,
    val solverCalls: Int,
    val completedPass: Boolean,
    val elapsedMillis: Long,
)

data class GeneratedPuzzle(val puzzle: Puzzle, val stats: GenerationStats)

/**
 * Monotone clue removal: every accepted deletion is PROVED to leave exactly
 * one solution. Known counterexamples (including valid segment reversals)
 * avoid needless solver calls. The deadline only affects how sparse a puzzle
 * becomes: UNKNOWN always means keep the clue.
 */
class PuzzleGenerator(private val solver: HidatoSolver = HidatoSolver()) {
    fun generate(
        rows: Int,
        cols: Int,
        difficulty: Difficulty,
        random: Random = Random.Default,
        deadlineMillis: Long = DEFAULT_DEADLINE_MS,
    ): Puzzle = generateDetailed(rows, cols, difficulty, random, deadlineMillis).puzzle

    fun generateDetailed(
        rows: Int,
        cols: Int,
        difficulty: Difficulty,
        random: Random = Random.Default,
        deadlineMillis: Long = DEFAULT_DEADLINE_MS,
        perCheckMillis: Long = DEFAULT_PER_CHECK_MS,
        nodeLimit: Long = HidatoSolver.DEFAULT_NODE_LIMIT,
    ): GeneratedPuzzle {
        require(rows in 4..15 && cols in 4..15)
        require(deadlineMillis > 0 && perCheckMillis > 0)
        val path = HamiltonianPaths.randomTour(rows, cols, random)
        return minimizeClues(rows, cols, difficulty, path, random, deadlineMillis, perCheckMillis, nodeLimit)
    }

    /** Repeatable offline experiments can optimize several clue orders for the same path. */
    fun minimizeClues(
        rows: Int,
        cols: Int,
        difficulty: Difficulty,
        path: IntArray,
        random: Random = Random.Default,
        deadlineMillis: Long = DEFAULT_DEADLINE_MS,
        perCheckMillis: Long = DEFAULT_PER_CHECK_MS,
        nodeLimit: Long = HidatoSolver.DEFAULT_NODE_LIMIT,
    ): GeneratedPuzzle {
        require(HamiltonianPaths.isValid(path, rows, cols))
        require(rows in 4..15 && cols in 4..15)
        require(deadlineMillis > 0 && perCheckMillis > 0 && nodeLimit > 0)
        val started = System.nanoTime()
        val total = rows * cols
        val clues = BooleanArray(total + 1) { true }
        var visible = total
        val minimum = when (difficulty) {
            Difficulty.EASY -> ceil(total * 0.35).toInt().coerceAtLeast(2)
            Difficulty.MEDIUM -> ceil(total * 0.20).toInt().coerceAtLeast(2)
            Difficulty.HARD -> 2 // lower bound, NOT a claim that two clues imply uniqueness
        }
        val witnesses = localReversalWitnesses(path, cols).toMutableList()
        val order = (2 until total).shuffled(random)
        var verified = 0
        var fastRejected = 0
        var solverRejected = 0
        var unknown = 0
        var calls = 0
        var attempted = 0
        fun elapsed(): Long = (System.nanoTime() - started) / 1_000_000L

        for (number in order) {
            if (visible <= minimum) break
            val remaining = deadlineMillis - elapsed()
            if (remaining <= 0) break
            attempted++
            clues[number] = false
            // If one already known alternative agrees with every visible
            // clue, there are definitely at least two valid solutions.
            if (witnesses.any { difference -> difference.none { clues[it] } }) {
                fastRejected++
                clues[number] = true
                continue
            }
            calls++
            when (val result = solver.findAlternative(
                rows, cols, path, clues,
                nodeLimit = nodeLimit,
                timeLimitMillis = minOf(perCheckMillis, remaining),
            )) {
                is AlternativeResult.Unique -> {
                    visible--
                    verified++
                }
                is AlternativeResult.Found -> {
                    solverRejected++
                    witnesses.add((1..total).filter { result.path[it - 1] != path[it - 1] }.toIntArray())
                    clues[number] = true
                }
                is AlternativeResult.Unknown -> {
                    unknown++
                    clues[number] = true
                }
            }
        }
        // A fully given puzzle is unique. We only remove clues after a
        // complete uniqueness proof, so this invariant holds on timeout too.
        val stats = GenerationStats(visible, verified, fastRejected, solverRejected, unknown, calls, attempted == order.size, elapsed())
        return GeneratedPuzzle(Puzzle(rows, cols, path.copyOf(), clues), stats)
    }

    /**
     * Reversing a section of a complete path gives another valid path if the
     * two new boundary edges exist. Every changed digit is a possible
     * distinguishing clue. A missing distinguishing clue set proves multi-
     * solution without DFS; the full solver is still needed to prove UNIQUE.
     */
    private fun localReversalWitnesses(path: IntArray, cols: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (start in 1 until path.size - 2) {
            for (end in start + 1..minOf(path.size - 2, start + MAX_LOCAL_REVERSAL_LENGTH - 1)) {
                if (!Grids.adjacent(path[start - 1], path[end], cols)) continue
                if (!Grids.adjacent(path[start], path[end + 1], cols)) continue
                val changed = (start..end).filter { start + end != it * 2 }.map { it + 1 }.toIntArray()
                if (changed.isNotEmpty()) out.add(changed)
            }
        }
        return out
    }

    companion object {
        const val DEFAULT_DEADLINE_MS = 2_000L
        const val DEFAULT_PER_CHECK_MS = 250L
        const val MAX_LOCAL_REVERSAL_LENGTH = 10
    }
}
