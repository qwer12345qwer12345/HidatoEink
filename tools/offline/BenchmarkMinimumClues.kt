package com.eink.hidato.offline

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.Grids
import com.eink.hidato.domain.MinimumClueOptimizer
import com.eink.hidato.domain.Puzzle
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToLong

/**
 * Reproducible fixed-path minimum-clue benchmark.
 *
 * Arguments: <catalog.tsv> <corpus.tsv> <raw.csv> <summary.csv>
 *            <budgetMillis> <repetitions> [masterNodeLimit] [oracleNodeLimit]
 *
 * Every case uses the same budgets. The raw file preserves each run; the
 * summary reports medians rather than presenting one timing as authoritative.
 */
fun main(args: Array<String>) {
    require(args.size in 6..9) {
        "Usage: BenchmarkMinimumCluesKt <catalog.tsv> <corpus.tsv> <raw.csv> <summary.csv> <budgetMillis> <repetitions> [masterNodeLimit] [oracleNodeLimit] [baseline|exact]"
    }
    val catalogFile = File(args[0])
    val corpusFile = File(args[1])
    val rawFile = File(args[2])
    val summaryFile = File(args[3])
    val budgetMillis = args[4].toLong()
    val repetitions = args[5].toInt()
    val masterNodeLimit = args.getOrNull(6)?.toLong() ?: 1_000_000L
    val oracleNodeLimit = args.getOrNull(7)?.toLong() ?: 50_000_000L
    val variant = args.getOrNull(8) ?: "exact"
    require(variant == "baseline" || variant == "exact") { "Variant must be baseline or exact" }
    require(budgetMillis in 1..3_600_000)
    require(repetitions in 1..20)
    require(masterNodeLimit > 0 && oracleNodeLimit > 0)
    require(catalogFile.isFile) { "Catalog file does not exist" }
    require(corpusFile.isFile) { "Corpus file does not exist" }

    val cases = loadCorpus(catalogFile, corpusFile).map { case ->
        if (case.id == "tiny-3x3") {
            require(case.puzzle.total == 9) { "tiny-3x3 must remain a 3x3 board" }
            case.copy(independentMinimum = exhaustiveMinimumClues(case.puzzle))
        } else {
            case
        }
    }
    require(cases.isNotEmpty())
    require(cases.map { it.id }.distinct().size == cases.size) { "Corpus IDs must be unique" }

    val rows = ArrayList<BenchmarkRow>()
    for (case in cases) {
        val pathHash = sha256(case.puzzle.path)
        val initialClues = (1..case.puzzle.total).count(case.puzzle::isClueVisible)
        val referenceMinimum = case.independentMinimum ?: case.knownMinimum
        val referenceType = when {
            case.independentMinimum != null -> "independent-exhaustive"
            case.knownMinimum != null -> "known-proven"
            else -> "none"
        }

        for (repetition in 1..repetitions) {
            val report = MinimumClueOptimizer(
                solver = com.eink.hidato.domain.HidatoSolver(enableExactIntervalPropagation = variant == "exact"),
            ).optimize(
                initial = case.puzzle,
                timeLimitMillis = budgetMillis,
                oracleNodeLimit = oracleNodeLimit,
                oracleTimeSliceMillis = budgetMillis,
                maxMasterNodes = masterNodeLimit,
            )

            val referenceCheck = when {
                referenceMinimum == null -> "not-applicable"
                !report.optimalityProven -> "not-proved"
                report.finalClues == referenceMinimum -> "matched"
                else -> "mismatch"
            }

            if (case.independentMinimum != null) {
                check(report.optimalityProven) {
                    "The tiny exhaustive case must finish with a proof under the configured budget"
                }
                check(report.finalClues == case.independentMinimum) {
                    "The optimizer disagrees with the independent exhaustive 3x3 oracle"
                }
            }
            if (case.knownMinimum != null) {
                check(report.optimalityProven && report.finalClues == case.knownMinimum) {
                    "The known 4x4 minimum must remain proved at its expected clue count"
                }
            }
            check(referenceCheck != "mismatch") { "A proved result disagrees with its reference minimum" }

            val stopped = report.stopped.name
            val timeoutOrUnknown = !report.optimalityProven
            rows.add(
                BenchmarkRow(
                    caseId = case.id,
                    variant = variant,
                    source = case.source,
                    rows = case.puzzle.rows,
                    cols = case.puzzle.cols,
                    seed = "fixed-corpus-path",
                    pathSha256 = pathHash,
                    repetition = repetition,
                    initialClues = report.initialClues,
                    finalClues = report.finalClues,
                    proofStatus = if (report.optimalityProven) "PROVED_MINIMUM" else "NOT_PROVED",
                    lowerBound = report.certifiedLowerBoundClues,
                    elapsedMillis = report.elapsedMillis,
                    masterNodes = report.masterNodes,
                    solverCalls = report.solverCalls,
                    alternativesFound = report.alternativeTours,
                    constraintsRetained = report.constraints,
                    stopped = stopped,
                    timeoutOrUnknown = timeoutOrUnknown,
                    referenceMinimum = referenceMinimum,
                    referenceType = referenceType,
                    referenceCheck = referenceCheck,
                    jvmVersion = System.getProperty("java.runtime.version", "unknown"),
                    javaVm = System.getProperty("java.vm.name", "unknown"),
                    osName = System.getProperty("os.name", "unknown"),
                    osArch = System.getProperty("os.arch", "unknown"),
                ),
            )
            println(
                case.id + " [" + variant + "] run " + repetition + "/" + repetitions +
                    ": status=" + stopped +
                    " clues=" + report.initialClues + "->" + report.finalClues +
                    " elapsedMs=" + report.elapsedMillis
            )
        }
    }

    writeCsv(rawFile, RAW_HEADER, rows.map(BenchmarkRow::toRawCsv))
    writeCsv(summaryFile, SUMMARY_HEADER, cases.map { case ->
        val group = rows.filter { it.caseId == case.id }
        summaryRow(group)
    })
    println("Wrote raw results to " + rawFile.absolutePath)
    println("Wrote median summary to " + summaryFile.absolutePath)
}

private data class BenchmarkCase(
    val id: String,
    val source: String,
    val puzzle: Puzzle,
    val knownMinimum: Int? = null,
    val independentMinimum: Int? = null,
)

private data class BenchmarkRow(
    val caseId: String,
    val variant: String,
    val source: String,
    val rows: Int,
    val cols: Int,
    val seed: String,
    val pathSha256: String,
    val repetition: Int,
    val initialClues: Int,
    val finalClues: Int,
    val proofStatus: String,
    val lowerBound: Int,
    val elapsedMillis: Long,
    val masterNodes: Long,
    val solverCalls: Int,
    val alternativesFound: Int,
    val constraintsRetained: Int,
    val stopped: String,
    val timeoutOrUnknown: Boolean,
    val referenceMinimum: Int?,
    val referenceType: String,
    val referenceCheck: String,
    val jvmVersion: String,
    val javaVm: String,
    val osName: String,
    val osArch: String,
) {
    fun toRawCsv(): List<Any?> = listOf(
        caseId, variant, source, rows, cols, seed, pathSha256, repetition,
        initialClues, finalClues, proofStatus, lowerBound, elapsedMillis,
        masterNodes, solverCalls, alternativesFound, constraintsRetained,
        stopped, timeoutOrUnknown, referenceMinimum ?: "", referenceType,
        referenceCheck, jvmVersion, javaVm, osName, osArch,
    )
}

private val RAW_HEADER = listOf(
    "input_id", "variant", "source", "rows", "cols", "seed_or_path_source", "path_sha256",
    "repetition", "initial_clues", "final_clues", "proof_status",
    "certified_lower_bound", "elapsed_ms", "master_nodes", "solver_calls",
    "alternatives_found", "constraints_retained", "stopped_status",
    "timeout_or_unknown", "reference_min_clues", "reference_type",
    "reference_check", "jvm_version", "java_vm", "os_name", "os_arch",
)

private val SUMMARY_HEADER = listOf(
    "input_id", "variant", "source", "rows", "cols", "path_sha256", "runs",
    "initial_clues", "median_final_clues", "min_final_clues", "max_final_clues",
    "median_elapsed_ms", "median_master_nodes", "median_solver_calls",
    "median_alternatives_found", "median_constraints_retained",
    "proved_runs", "timeout_or_unknown_runs", "stopped_statuses", "all_proved",
    "reference_min_clues", "reference_type", "reference_check",
    "jvm_version", "java_vm", "os_name", "os_arch",
)

private fun loadCorpus(catalogFile: File, corpusFile: File): List<BenchmarkCase> {
    val catalogRecords = catalogFile.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.toList()
    }
    return corpusFile.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val fields = line.split('\t')
            require(fields.size == 7) { "Each benchmark row must have seven tab-separated fields" }
            val id = fields[0].trim()
            val sourceKind = fields[1].trim()
            val knownMinimum = fields[6].trim().takeIf { it != "-" }?.toInt()
            val puzzle: Puzzle
            val source: String

            when (sourceKind) {
                "catalog" -> {
                    val row = fields[2].toInt()
                    require(fields[3] == "-" && fields[4] == "-" && fields[5] == "-") {
                        "Catalog rows use '-' for unused fields"
                    }
                    require(row in 1..catalogRecords.size) { "Catalog row is out of range" }
                    val parsed = requireNotNull(PrebuiltPuzzleCodec.parse(catalogRecords[row - 1])) {
                        "Invalid catalog row referenced by the benchmark corpus"
                    }
                    puzzle = parsed.second
                    source = "catalog-row-" + row
                }
                "inline" -> {
                    val boardRows = fields[2].toInt()
                    val boardCols = fields[3].toInt()
                    val path = parseInts(fields[4])
                    val givenNumbers = parseInts(fields[5])
                    val total = boardRows * boardCols
                    require(path.size == total) { "Inline path length does not match its board" }
                    require(HamiltonianPaths.isValid(path, boardRows, boardCols)) {
                        "Inline path is not a valid complete Hidato path"
                    }
                    require(givenNumbers.distinct().size == givenNumbers.size)
                    require(givenNumbers.all { it in 1..total })
                    val clues = BooleanArray(total + 1)
                    givenNumbers.forEach { clues[it] = true }
                    require(clues[1] && clues[total]) { "Both endpoint clues are mandatory" }
                    puzzle = Puzzle(boardRows, boardCols, path, clues)
                    source = "inline-fixed"
                }
                else -> error("Unknown benchmark source kind")
            }

            if (knownMinimum != null) {
                require(knownMinimum in 2..puzzle.total)
            }
            BenchmarkCase(id, source, puzzle, knownMinimum)
        }
}

private fun parseInts(value: String): IntArray =
    value.split(',').map { it.trim().toInt() }.toIntArray()

/**
 * Independent oracle for a tiny board: enumerate all internal clue subsets,
 * then enumerate complete paths directly without using HidatoSolver or the
 * production optimizer's cuts, bounds, or master search.
 */
private fun exhaustiveMinimumClues(puzzle: Puzzle): Int {
    require(puzzle.total == 9 && puzzle.rows == 3 && puzzle.cols == 3)
    var minimum = Int.MAX_VALUE
    for (mask in 0 until (1 shl (puzzle.total - 2))) {
        val clues = BooleanArray(puzzle.total + 1)
        clues[1] = true
        clues[puzzle.total] = true
        for (number in 2 until puzzle.total) {
            clues[number] = mask and (1 shl (number - 2)) != 0
        }
        if (!hasAlternativeByBruteForce(puzzle.rows, puzzle.cols, puzzle.path, clues)) {
            minimum = minOf(minimum, 2 + Integer.bitCount(mask))
        }
    }
    check(minimum != Int.MAX_VALUE) { "No uniquely-clued subset found for the tiny board" }
    return minimum
}

private fun hasAlternativeByBruteForce(
    rows: Int,
    cols: Int,
    reference: IntArray,
    clues: BooleanArray,
): Boolean {
    val total = rows * cols
    val assigned = IntArray(total)
    val used = BooleanArray(total)
    var alternativeFound = false

    fun search(number: Int, current: Int) {
        if (alternativeFound) return
        if (number == total) {
            alternativeFound = !assigned.contentEquals(reference)
            return
        }

        val nextNumber = number + 1
        val candidates = if (clues[nextNumber]) {
            intArrayOf(reference[nextNumber - 1])
        } else {
            Grids.neighbors(current, rows, cols).toIntArray()
        }
        for (next in candidates) {
            if (used[next] || !Grids.adjacent(current, next, cols)) continue
            if (clues[nextNumber] && next != reference[nextNumber - 1]) continue
            used[next] = true
            assigned[nextNumber - 1] = next
            search(nextNumber, next)
            used[next] = false
            if (alternativeFound) return
        }
    }

    assigned[0] = reference[0]
    used[reference[0]] = true
    search(1, reference[0])
    return alternativeFound
}

private fun summaryRow(group: List<BenchmarkRow>): List<Any?> {
    require(group.isNotEmpty())
    val first = group.first()
    val referenceCheck = when {
        first.referenceMinimum == null -> "not-applicable"
        group.any { it.referenceCheck == "mismatch" } -> "mismatch"
        group.all { it.referenceCheck == "matched" } -> "matched"
        else -> "not-proved-in-some-runs"
    }
    return listOf(
        first.caseId, first.variant, first.source, first.rows, first.cols, first.pathSha256,
        group.size, first.initialClues,
        medianInt(group.map { it.finalClues }),
        group.minOf { it.finalClues }, group.maxOf { it.finalClues },
        medianLong(group.map { it.elapsedMillis }),
        medianLong(group.map { it.masterNodes }),
        medianInt(group.map { it.solverCalls }),
        medianInt(group.map { it.alternativesFound }),
        medianInt(group.map { it.constraintsRetained }),
        group.count { it.proofStatus == "PROVED_MINIMUM" },
        group.count { it.timeoutOrUnknown },
        group.map { it.stopped }.distinct().joinToString("|"),
        group.all { it.proofStatus == "PROVED_MINIMUM" },
        first.referenceMinimum ?: "", first.referenceType, referenceCheck,
        first.jvmVersion, first.javaVm, first.osName, first.osArch,
    )
}

private fun medianInt(values: List<Int>): Long = medianLong(values.map(Int::toLong))

private fun medianLong(values: List<Long>): Long {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        ((sorted[middle - 1].toDouble() + sorted[middle].toDouble()) / 2.0).roundToLong()
    }
}

private fun sha256(path: IntArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(path.joinToString(",").toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun writeCsv(file: File, header: List<String>, rows: List<List<Any?>>) {
    file.parentFile?.mkdirs()
    file.bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(header.joinToString(",") { csv(it) })
        writer.newLine()
        for (row in rows) {
            writer.write(row.joinToString(",") { csv(it) })
            writer.newLine()
        }
    }
}

private fun csv(value: Any?): String {
    val text = value?.toString() ?: ""
    return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + text.replace("\"", "\"\"") + "\""
    } else {
        text
    }
}
