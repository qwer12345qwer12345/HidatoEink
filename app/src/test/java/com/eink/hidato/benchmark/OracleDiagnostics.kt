package com.eink.hidato.benchmark

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.HidatoSolver
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Research-only diagnostic (issue #6): understand WHY sparse-candidate oracle
 * calls return Unknown. For a catalog entry, drop each interior clue once and
 * ask findAlternative with a generous budget, with and without direction-A
 * reachability pruning. Not part of any PR.
 */
class OracleDiagnostics {

    @Test
    fun diagnose() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        val row = (System.getProperty("hidato.diag.row") ?: "11").toInt()
        val nodeBudget = (System.getProperty("hidato.diag.nodes") ?: "100000000").toLong()
        val line = listOf(File("app/src/main/assets/unique-puzzles.tsv"), File("src/main/assets/unique-puzzles.tsv"))
            .first { it.exists() }.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }[row - 1]
        val (_, puzzle) = requireNotNull(PrebuiltPuzzleCodec.parse(line))
        println("DIAG row=$row size=${puzzle.rows}x${puzzle.cols} clues=${(1..puzzle.total).count(puzzle::isClueVisible)} budget=$nodeBudget")

        for (solver in listOf(HidatoSolver() to "control", HidatoSolver(freeSpaceReachability = true) to "pruneA")) {
            for (dropped in 2 until puzzle.total) {
                if (!puzzle.clues[dropped]) continue
                val clues = puzzle.clues.copyOf()
                clues[dropped] = false
                val t0 = System.nanoTime()
                val r = solver.first.findAlternative(
                    puzzle.rows, puzzle.cols, puzzle.path, clues,
                    nodeLimit = nodeBudget, timeLimitMillis = 20_000,
                )
                val ms = (System.nanoTime() - t0) / 1_000_000
                println("DIAG ${solver.second} drop=$dropped -> ${r.javaClass.simpleName} nodes=${r.nodes} ms=$ms")
            }
        }
    }
}
