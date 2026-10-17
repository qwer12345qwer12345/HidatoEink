package com.eink.hidato.offline

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.MinimumClueOptimizer
import java.io.File

/**
 * Offline exact optimization of a single certified catalog entry.
 *
 * Arguments: <catalog.tsv> <one-based-data-row> <budgetMillis>
 * Stdout contains exactly one certified unique puzzle row suitable for TSV.
 * Stderr records whether minimum-clue optimality for this FIXED path is proved.
 */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: MinimizeCatalogKt <catalog.tsv> <1-based-entry> <budgetMillis>" }
    val file = File(args[0])
    val entry = args[1].toInt()
    val millis = args[2].toLong()
    require(entry > 0 && millis in 1..3_600_000)
    val records = file.useLines { it.filter { line -> line.isNotBlank() && !line.startsWith("#") }.toList() }
    require(entry <= records.size) { "Only ${records.size} records found" }
    val (difficulty, original) = requireNotNull(PrebuiltPuzzleCodec.parse(records[entry - 1])) {
        "Invalid entry number $entry"
    }

    val report = MinimumClueOptimizer().optimize(
        original,
        timeLimitMillis = millis,
        oracleNodeLimit = 10_000_000,
        oracleTimeSliceMillis = minOf(5_000, millis),
        maxMasterNodes = 3_000_000,
    )
    System.err.println(
        "size=${original.rows}x${original.cols} difficulty=$difficulty " +
        "clues=${report.initialClues}->${report.finalClues} " +
        "proved-lower-bound=${report.certifiedLowerBoundClues} " +
        "status=${report.stopped} proof=${report.optimalityProven} " +
        "cuts=${report.constraints} alternatives=${report.alternativeTours} " +
        "oracleCalls=${report.solverCalls} masterNodes=${report.masterNodes} " +
        "elapsedMs=${report.elapsedMillis}"
    )
    // Even on timeout the incumbent remains independently certified unique.
    println(PrebuiltPuzzleCodec.encode(difficulty, report.puzzle))
}
