package com.eink.hidato.offline

import com.eink.hidato.domain.*
import kotlin.random.Random

/**
 * Pure Kotlin offline generator. Prints exactly one TSV puzzle on stdout;
 * diagnostic evidence about each attempt goes to stderr.
 *
 * Usage: PreGenerateKt 8 8 HARD 10 10000 42
 */
fun main(args: Array<String>) {
    require(args.size == 6) {
        "Usage: PreGenerateKt <rows> <cols> <EASY|MEDIUM|HARD> <attempts> <millis-per-attempt> <seed>"
    }
    val rows = args[0].toInt()
    val cols = args[1].toInt()
    val difficulty = Difficulty.valueOf(args[2].uppercase())
    val attempts = args[3].toInt()
    val millis = args[4].toLong()
    val seed = args[5].toInt()
    require(rows in 4..15 && cols in 4..15 && attempts in 1..10_000 && millis in 1..3_600_000)

    val random = Random(seed)
    val generator = PuzzleGenerator()
    var best: GeneratedPuzzle? = null
    for (attempt in 1..attempts) {
        val result = generator.generateDetailed(
            rows, cols, difficulty, random,
            deadlineMillis = millis,
            perCheckMillis = minOf(5_000L, millis),
            nodeLimit = 5_000_000,
        )
        val previous = best
        if (previous == null || result.stats.retainedClues < previous.stats.retainedClues ||
            result.stats.retainedClues == previous.stats.retainedClues &&
            result.stats.unresolvedChecks < previous.stats.unresolvedChecks
        ) {
            best = result
        }
        System.err.println("$rows x $cols $difficulty attempt=$attempt clues=${result.stats.retainedClues} " +
            "verified-removals=${result.stats.verifiedRemovals} " +
            "local-reversals=${result.stats.rejectedByLocalReversal} cached-alternatives=${result.stats.rejectedByCachedAlternative} " +
            "unknown=${result.stats.unresolvedChecks} elapsedMs=${result.stats.elapsedMillis}")
    }
    val winner = checkNotNull(best)
    // Defensive post-generation check, with more budget than live gameplay.
    val verification = HidatoSolver().findAlternative(
        rows, cols, winner.puzzle.path, winner.puzzle.clues,
        nodeLimit = 50_000_000,
        timeLimitMillis = 30_000,
    )
    require(verification is AlternativeResult.Unique) {
        "Final verification returned $verification; do not include this puzzle in assets."
    }
    val pathText = winner.puzzle.path.joinToString(",")
    val givenText = (1..winner.puzzle.total).filter(winner.puzzle::isClueVisible).joinToString(",")
    println("$rows\t$cols\t${difficulty.name}\t$pathText\t$givenText")
}
