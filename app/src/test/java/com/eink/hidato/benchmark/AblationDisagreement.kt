package com.eink.hidato.benchmark

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.HidatoSolver
import com.eink.hidato.domain.MinimumClueOptimizer
import com.eink.hidato.domain.Puzzle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Research-only (issue #6): catch semantic disagreements between the control
 * solver and the direction-A pruned solver on the EXACT queries the optimizer
 * issues, plus randomized sparse-subset hunting. Not part of any PR.
 */
class AblationDisagreement {

    private class SpySolver(private val tag: String, pruneA: Boolean, val log: MutableList<String>) : HidatoSolver(freeSpaceReachability = pruneA) {
        override fun findAlternative(
            rows: Int, cols: Int, path: IntArray, clues: BooleanArray,
            nodeLimit: Long, timeLimitMillis: Long,
        ): AlternativeResult {
            val given = (1..rows * cols).filter { clues[it] }
            val result = super.findAlternative(rows, cols, path, clues, nodeLimit, timeLimitMillis)
            log += "$tag clues=${given.joinToString(",")} -> ${result.javaClass.simpleName} nodes=${result.nodes}"
            return result
        }
    }

    @Test
    fun optimizerQueriesAgree() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        val row = (System.getProperty("hidato.disagree.row") ?: "11").toInt()
        val line = catalogLine(row)
        val (_, puzzle) = requireNotNull(PrebuiltPuzzleCodec.parse(line))
        val logA = ArrayList<String>()
        val logC = ArrayList<String>()
        val resultA = MinimumClueOptimizer(SpySolver("A", true, logA)).optimize(
            Puzzle(puzzle.rows, puzzle.cols, puzzle.path.copyOf(), puzzle.clues.copyOf()),
            timeLimitMillis = 30_000, oracleNodeLimit = 10_000_000, oracleTimeSliceMillis = 5_000,
        )
        val resultC = MinimumClueOptimizer(SpySolver("C", false, logC)).optimize(
            Puzzle(puzzle.rows, puzzle.cols, puzzle.path.copyOf(), puzzle.clues.copyOf()),
            timeLimitMillis = 30_000, oracleNodeLimit = 10_000_000, oracleTimeSliceMillis = 5_000,
        )
        println("DISAGREE row=$row statusA=${resultA.stopped} statusC=${resultC.stopped} cluesA=${resultA.finalClues} cluesC=${resultC.finalClues}")
        for (i in 0 until maxOf(logA.size, logC.size)) {
            val a = logA.getOrNull(i) ?: "-"
            val c = logC.getOrNull(i) ?: "-"
            val flag = if (a.substringAfter("->").substringBefore("nodes") ==
                c.substringAfter("->").substringBefore("nodes")) "" else "   <<< SEMANTIC DISAGREEMENT"
            println("Q$i\n  $a\n  $c$flag")
        }
        // Same fixed inputs must produce identical final clue sets whenever
        // both proved optimality (the model is deterministic given semantics).
        if (resultA.optimalityProven && resultC.optimalityProven) {
            assertEquals(resultC.puzzle.clues.contentToString(), resultA.puzzle.clues.contentToString())
        }
    }

    /** Random sparse subsets: control and A must classify identically. */
    @Test
    fun randomSparseSubsetsAgree() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        val row = (System.getProperty("hidato.disagree.row") ?: "11").toInt()
        val trials = (System.getProperty("hidato.disagree.trials") ?: "40").toInt()
        val line = catalogLine(row)
        val (_, puzzle) = requireNotNull(PrebuiltPuzzleCodec.parse(line))
        val total = puzzle.total
        var disagreements = 0
        for (seed in 1..trials) {
            val random = kotlin.random.Random(seed * 7919)
            val clues = BooleanArray(total + 1)
            clues[1] = true
            clues[total] = true
            // Sparse: keep each catalog clue with prob 0.65, add a few random extras.
            for (n in 2 until total) {
                if (puzzle.clues[n] && random.nextDouble() < 0.65) clues[n] = true
            }
            repeat(3) { clues[2 + random.nextInt(total - 2)] = true }
            val c = HidatoSolver().findAlternative(puzzle.rows, puzzle.cols, puzzle.path, clues, nodeLimit = 30_000_000)
            val a = HidatoSolver(freeSpaceReachability = true).findAlternative(puzzle.rows, puzzle.cols, puzzle.path, clues, nodeLimit = 30_000_000)
            if (c.javaClass != a.javaClass) {
                disagreements++
                println("MISMATCH seed=$seed given=${(1..total).filter { clues[it] }} control=${c.javaClass.simpleName} A=${a.javaClass.simpleName}")
            }
        }
        println("randomSparseSubsetsAgree: $disagreements disagreements over $trials trials")
        assertEquals(0, disagreements)
    }

    private fun catalogLine(row: Int): String =
        listOf(File("app/src/main/assets/unique-puzzles.tsv"), File("src/main/assets/unique-puzzles.tsv"))
            .first { it.exists() }.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }[row - 1]
}
