package com.eink.hidato.benchmark

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.HidatoSolver
import com.eink.hidato.domain.MinimumClueOptimizer
import com.eink.hidato.domain.Puzzle
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Issue #10 ablation benchmark: local alternative-path segments as
 * counterexample generators, beyond the static seeding of PR #7.
 *
 * Variants (same budgets and inputs everywhere):
 *  - baseline: current master behaviour (seeding only);
 *  - farm: additionally harvest local replacements of every solver-found
 *    alternative as free constraints;
 *  - deep: additionally run a larger-budget local search before every
 *    solver call, rejecting candidates without spending oracle budget;
 *  - farm+deep: both generators.
 *
 * Metrics per instance (medians over repeats): solver calls, oracle-found
 * alternatives, locally found counterexamples, farmed net-new constraints,
 * total constraints, certified lower bound, final clue count, status,
 * elapsed. Overhead on instances where the local search finds nothing is
 * read directly from the deep column on the SOLVER_UNKNOWN boards.
 *
 * Enabled only with -PhidatoBenchmark=true (same gate as
 * MinimumClueBenchmark; distinguish runs with -PhidatoBenchmarkLabel=).
 */
class LocalSearchAblationBenchmark {

    private val label: String = System.getProperty("hidato.benchmark.label") ?: "run"
    private val repeats: Int = (System.getProperty("hidato.benchmark.repeats") ?: "5").toInt()
    private val only: Set<String> =
        (System.getProperty("hidato.benchmark.only") ?: "")
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()

    private data class Row(
        val stopped: String,
        val finalClues: Int,
        val lowerBound: Int,
        val constraints: Int,
        val alternatives: Int,
        val oracleCalls: Int,
        val localCounterexamples: Int,
        val farmedConstraints: Int,
        val masterNodes: Long,
        val elapsedMs: Long,
    )

    @Test
    fun run() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        assumeTrue("repeats must be positive", repeats > 0)
        println(
            "ENV label=$label java=${System.getProperty("java.version")} " +
                "os=${System.getProperty("os.name")}/${System.getProperty("os.arch")} " +
                "cpus=${Runtime.getRuntime().availableProcessors()} repeats=$repeats"
        )
        println(
            "MED\tid\trows\tcols\tvariant\tstatus\tfinal\tlb\tcuts\talts\toracleCalls\tlocalFinds\tfarmedCuts\tmasterNodes\telapsedMs"
        )
        for (instance in instances().filter { only.isEmpty() || it.first in only }) runInstance(instance)
    }

    private fun instances(): List<Pair<String, Puzzle>> {
        val out = ArrayList<Pair<String, Puzzle>>()
        out += "3x3-a" to Puzzle(3, 3, intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8), BooleanArray(10) { it > 0 })
        out += "4x4-proven5" to Puzzle(
            4, 4, intArrayOf(2, 3, 7, 11, 15, 14, 10, 13, 12, 8, 9, 6, 1, 0, 5, 4),
            BooleanArray(17) { it > 0 },
        )
        for ((id, row) in listOf(
            "cat4-5x5-easy" to 4,
            "cat5-5x5-medium" to 5,
            "cat8-6x6-medium" to 8,
            "cat11-8x8-medium" to 11,
            "cat16-10x10-medium" to 16,
            "cat18-15x15-hard" to 18,
        )) {
            val file = listOf(
                File("app/src/main/assets/unique-puzzles.tsv"),
                File("src/main/assets/unique-puzzles.tsv"),
            ).first { it.exists() }
            val line = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }[row - 1]
            out += id to requireNotNull(PrebuiltPuzzleCodec.parse(line)).second
        }
        out += "synth-9x8-full" to Puzzle(
            9, 8, HamiltonianPaths.randomTour(9, 8, Random(20261018)), BooleanArray(73) { it > 0 },
        )
        return out
    }

    private fun runInstance(instance: Pair<String, Puzzle>) {
        val (id, puzzle) = instance
        val exhaustive = if (puzzle.total == 9) exhaustive3x3Minimum(puzzle.path) else null
        val variants = listOf(
            "baseline" to MinimumClueOptimizer(
                enableCounterexampleFarming = false,
                enableDeepLocalCounterexampleSearch = false,
            ),
            "farm" to MinimumClueOptimizer(
                enableCounterexampleFarming = true,
                enableDeepLocalCounterexampleSearch = false,
            ),
            "deep" to MinimumClueOptimizer(enableDeepLocalCounterexampleSearch = true),
            "farm+deep" to MinimumClueOptimizer(
                enableCounterexampleFarming = true,
                enableDeepLocalCounterexampleSearch = true,
            ),
        )
        val medians = LinkedHashMap<String, Row>()
        for ((name, optimizer) in variants) {
            val rows = ArrayList<Row>(repeats)
            repeat(repeats) {
                val result = optimizer.optimize(
                    puzzle,
                    timeLimitMillis = 30_000,
                    oracleNodeLimit = 10_000_000,
                    oracleTimeSliceMillis = 5_000,
                    maxMasterNodes = 3_000_000,
                )
                val row = Row(
                    result.stopped.toString(), result.finalClues, result.certifiedLowerBoundClues,
                    result.constraints, result.alternativeTours, result.solverCalls,
                    result.localCounterexamples, result.farmedConstraints,
                    result.masterNodes, result.elapsedMillis,
                )
                println(
                    "RAW\t$label\t$id\t$name\t${row.stopped}\t${row.finalClues}\t${row.lowerBound}\t" +
                        "${row.constraints}\t${row.alternatives}\t${row.oracleCalls}\t${row.localCounterexamples}\t" +
                        "${row.farmedConstraints}\t${row.masterNodes}\t${row.elapsedMs}"
                )
                // --- correctness gates ---
                assertTrue("final clues must not exceed initial", result.finalClues <= result.initialClues)
                if (result.optimalityProven) {
                    val verify = HidatoSolver().findAlternative(
                        puzzle.rows, puzzle.cols, puzzle.path, result.puzzle.clues,
                        nodeLimit = 50_000_000, timeLimitMillis = 30_000,
                    )
                    assertTrue("PROVED_MINIMUM must reverify unique (got $verify)", verify is AlternativeResult.Unique)
                }
                exhaustive?.let {
                    assertEquals("3x3 must equal exhaustive oracle", it, result.finalClues)
                    assertEquals(MinimumClueOptimizer.Stopped.PROVED_MINIMUM, result.stopped)
                }
                rows += row
            }
            val sortedStatus = rows.map { it.stopped }.sorted()
            val median = Row(
                sortedStatus[rows.size / 2],
                rows.map { it.finalClues }.sorted()[rows.size / 2],
                rows.map { it.lowerBound }.sorted()[rows.size / 2],
                rows.map { it.constraints }.sorted()[rows.size / 2],
                rows.map { it.alternatives }.sorted()[rows.size / 2],
                rows.map { it.oracleCalls }.sorted()[rows.size / 2],
                rows.map { it.localCounterexamples }.sorted()[rows.size / 2],
                rows.map { it.farmedConstraints }.sorted()[rows.size / 2],
                rows.map { it.masterNodes }.sorted()[rows.size / 2],
                rows.map { it.elapsedMs }.sorted()[rows.size / 2],
            )
            medians[name] = median
            println(
                "MED\t$id\t${puzzle.rows}\t${puzzle.cols}\t$name\t${median.stopped}\t${median.finalClues}\t" +
                    "${median.lowerBound}\t${median.constraints}\t${median.alternatives}\t${median.oracleCalls}\t" +
                    "${median.localCounterexamples}\t${median.farmedConstraints}\t${median.masterNodes}\t${median.elapsedMs}"
            )
        }
        // The final clue count must never differ between variants: the
        // generators add necessary constraints only. The specific honest
        // non-proof stop reason may differ (SOLVER_UNKNOWN vs
        // MASTER_NODE_LIMIT vs TIME_LIMIT) when extra constraints shift
        // which budget dies first, so only the proof status is compared.
        val distinctFinals = medians.values.map { it.finalClues to it.stopped.contains("PROVED") }.toSet()
        assertEquals("variants must agree on final clues and proof status", 1, distinctFinals.size)
    }

    /** Clean-room 3x3 minimum (same construction as MinimumClueBenchmark). */
    private fun exhaustive3x3Minimum(path: IntArray): Int {
        fun adj(a: Int, b: Int): Boolean =
            maxOf(Math.abs(a / 3 - b / 3), Math.abs(a % 3 - b % 3)) == 1

        fun unique(clues: BooleanArray): Boolean {
            var count = 0
            val used = BooleanArray(9)
            fun walk(number: Int, cell: Int) {
                if (count >= 2) return
                if (number == 9) {
                    count++
                    return
                }
                for (next in 0 until 9) {
                    if (used[next] || !adj(cell, next)) continue
                    if (clues[number + 1] && next != path[number]) continue
                    used[next] = true
                    walk(number + 1, next)
                    used[next] = false
                    if (count >= 2) return
                }
            }
            used[path[0]] = true
            walk(1, path[0])
            return count == 1
        }

        var best = Int.MAX_VALUE
        for (mask in 0 until 128) {
            val clues = BooleanArray(10)
            clues[1] = true
            clues[9] = true
            for (n in 2..8) clues[n] = mask and (1 shl (n - 2)) != 0
            if (unique(clues)) best = minOf(best, (2..8).count { clues[it] } + 2)
        }
        return best
    }
}
