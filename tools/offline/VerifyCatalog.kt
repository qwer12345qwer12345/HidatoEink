package com.eink.hidato.offline

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.HidatoSolver
import java.io.File

/** Fails the release check if any bundled entry is malformed or not proved unique. */
fun main(args: Array<String>) {
    val filename = args.firstOrNull() ?: "app/src/main/assets/unique-puzzles.tsv"
    val solver = HidatoSolver()
    var checked = 0
    for ((lineNumber, line) in File(filename).readLines().withIndex()) {
        if (line.isBlank() || line.startsWith("#")) continue
        val parsed = requireNotNull(PrebuiltPuzzleCodec.parse(line)) {
            "Malformed catalog entry at $filename:${lineNumber + 1}"
        }
        val (difficulty, puzzle) = parsed
        val result = solver.findAlternative(
            puzzle.rows, puzzle.cols, puzzle.path, puzzle.clues,
            nodeLimit = 50_000_000,
            timeLimitMillis = 30_000,
        )
        check(result is AlternativeResult.Unique) {
            "Cannot certify $filename:${lineNumber + 1} (${puzzle.rows}x${puzzle.cols} $difficulty): $result"
        }
        System.err.println("verified ${puzzle.rows}x${puzzle.cols} $difficulty clues=" +
            (1..puzzle.total).count(puzzle::isClueVisible) + " nodes=${result.nodes}")
        checked++
    }
    check(checked > 0) { "Empty catalog" }
    println("Verified $checked uniquely solvable puzzles")
}
