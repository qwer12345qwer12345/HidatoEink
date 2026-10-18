package com.eink.hidato.benchmark

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.HidatoSolver
import com.eink.hidato.domain.MinimumClueOptimizer
import com.eink.hidato.domain.Puzzle
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Reproducible pure-JVM benchmark for the fixed-path minimum-clue optimizer.
 *
 * Enabled only when Gradle passes -PhidatoBenchmark=true (CI runs nothing);
 * every invocation prints raw per-repeat rows and a median summary as TSV.
 *
 * Benchmark set:
 *  - exhaustible 3x3 boards with an independent brute-force oracle,
 *  - a 4x4 case whose optimum is proven by an existing unit test,
 *  - representative catalog entries,
 *  - 8x8 / 9x8 / 10x10 / 15x15 (>64 cells included).
 *
 * Budgets intentionally match tools/offline/MinimizeCatalog.kt so numbers are
 * directly comparable with the production offline workflow.
 */
class MinimumClueBenchmark {

    private val label: String = System.getProperty("hidato.benchmark.label") ?: "run"
    private val repeats: Int = (System.getProperty("hidato.benchmark.repeats") ?: "5").toInt()
    private val only: Set<String> =
        (System.getProperty("hidato.benchmark.only") ?: "")
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()

    private fun newOptimizer(): MinimumClueOptimizer = MinimumClueOptimizer()

    private data class Instance(
        val id: String,
        val puzzle: Puzzle,
        /** Documented expected optimum used as a hard assertion. */
        val expectedProved: Int? = null,
    )

    private data class Row(
        val stopped: String,
        val finalClues: Int,
        val lowerBound: Int,
        val constraints: Int,
        val alternatives: Int,
        val oracleCalls: Int,
        val masterNodes: Long,
        val elapsedMs: Long,
    )

    @Test
    fun run() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        assumeTrue("repeats must be positive", repeats > 0)
        val instances = instances().filter { only.isEmpty() || it.id in only }
        println(
            "ENV label=$label java=${System.getProperty("java.version")} " +
                "os=${System.getProperty("os.name")}/${System.getProperty("os.arch")} " +
                "cpus=${Runtime.getRuntime().availableProcessors()} repeats=$repeats"
        )
        println("MED\tid\trows\tcols\tinitial\tfinal\tstatusDist\tlb\tcuts\talts\toracleCalls\tmasterNodes\telapsedMs")
        for (instance in instances) runInstance(instance)
    }

    private fun instances(): List<Instance> {
        val tiny = listOf(
            intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8),
            intArrayOf(3, 0, 1, 2, 5, 8, 7, 6, 4),
            intArrayOf(0, 3, 6, 7, 4, 1, 2, 5, 8),
        )
        val out = ArrayList<Instance>()
        tiny.forEachIndexed { i, path ->
            out += Instance("3x3-${'a' + i}", Puzzle(3, 3, path, BooleanArray(10) { it > 0 }))
        }
        // 4x4 with the known 5-clue global optimum (covered by a unit test).
        out += Instance(
            "4x4-proven5",
            Puzzle(4, 4, intArrayOf(2, 3, 7, 11, 15, 14, 10, 13, 12, 8, 9, 6, 1, 0, 5, 4), BooleanArray(17) { it > 0 }),
            expectedProved = 5,
        )
        // Representative catalog rows (1-based data rows of unique-puzzles.tsv).
        for ((id, row, expected) in listOf(
            Triple("cat1-4x4-easy", 1, null as Int?),
            Triple("cat2-4x4-medium", 2, 4),
            Triple("cat4-5x5-easy", 4, null),
            Triple("cat5-5x5-medium", 5, null),
            Triple("cat6-5x5-hard", 6, null),
            Triple("cat8-6x6-medium", 8, null),
            Triple("cat11-8x8-medium", 11, null),
            Triple("cat16-10x10-medium", 16, null),
            Triple("cat18-15x15-hard", 18, null),
        )) {
            out += catalogInstance(id, row, expected)
        }
        // Synthetic >64-cell case: deterministic random 9x8 tour, full clues.
        val path98 = HamiltonianPaths.randomTour(9, 8, Random(20261018))
        out += Instance("synth-9x8-full", Puzzle(9, 8, path98, BooleanArray(73) { it > 0 }))
        return out
    }

    private fun catalogInstance(id: String, dataRow: Int, expected: Int?): Instance {
        val candidates = listOf(
            File("app/src/main/assets/unique-puzzles.tsv"),
            File("src/main/assets/unique-puzzles.tsv"),
        )
        val file = candidates.first { it.exists() }
        val line = file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }[dataRow - 1]
        val (_, puzzle) = requireNotNull(PrebuiltPuzzleCodec.parse(line))
        return Instance(id, puzzle, expectedProved = expected)
    }

    private fun runInstance(instance: Instance) {
        val puzzle = instance.puzzle
        val exhaustive = if (puzzle.total == 9) exhaustive3x3Minimum(puzzle.path) else null
        val rows = ArrayList<Row>(repeats)
        repeat(repeats) { repeatIndex ->
            val result = newOptimizer().optimize(
                puzzle,
                timeLimitMillis = 30_000,
                oracleNodeLimit = 10_000_000,
                oracleTimeSliceMillis = 5_000,
                maxMasterNodes = 3_000_000,
            )
            val row = Row(
                stopped = result.stopped.toString(),
                finalClues = result.finalClues,
                lowerBound = result.certifiedLowerBoundClues,
                constraints = result.constraints,
                alternatives = result.alternativeTours,
                oracleCalls = result.solverCalls,
                masterNodes = result.masterNodes,
                elapsedMs = result.elapsedMillis,
            )
            println(
                "RAW\t$label\t${instance.id}\t${repeatIndex + 1}\t${row.stopped}\t${row.finalClues}\t" +
                    "${row.lowerBound}\t${row.constraints}\t${row.alternatives}\t${row.oracleCalls}\t" +
                    "${row.masterNodes}\t${row.elapsedMs}"
            )
            // --- correctness gates (hard failures, not statistics) ---
            assertTrue("final clues must not exceed initial", result.finalClues <= result.initialClues)
            if (result.optimalityProven) {
                val verify = HidatoSolver().findAlternative(
                    puzzle.rows, puzzle.cols, puzzle.path, result.puzzle.clues,
                    nodeLimit = 50_000_000, timeLimitMillis = 30_000,
                )
                assertTrue("PROVED_MINIMUM result must reverify unique (got $verify)", verify is AlternativeResult.Unique)
            }
            exhaustive?.let {
                assertEquals("3x3 PROVED_MINIMUM must equal exhaustive oracle", it, result.finalClues)
                assertEquals(MinimumClueOptimizer.Stopped.PROVED_MINIMUM, result.stopped)
            }
            instance.expectedProved?.let {
                assertTrue("${instance.id} must prove optimality", result.optimalityProven)
                assertEquals(it, result.finalClues)
            }
            rows += row
        }
        val med = { select: (Row) -> Long -> median(rows.map(select)) }
        val statusDist = rows.groupingBy { it.stopped }.eachCount()
            .entries.joinToString("|") { "${it.key}:${it.value}" }
        println(
            "MED\t${instance.id}\t${puzzle.rows}\t${puzzle.cols}\t${initialClueCount(puzzle)}\t" +
                "${median(rows.map { it.finalClues.toLong() })}\t$statusDist\t" +
                "${med { it.lowerBound.toLong() }}\t${med { it.constraints.toLong() }}\t" +
                "${med { it.alternatives.toLong() }}\t${med { it.oracleCalls.toLong() }}\t" +
                "${med { it.masterNodes }}\t${med { it.elapsedMs }}"
        )
    }

    private fun initialClueCount(puzzle: Puzzle): Int = (1..puzzle.total).count(puzzle::isClueVisible)

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * Clean-room exhaustive 3x3 oracle: enumerate all 2^7 interior clue masks,
     * count consistent Hamiltonian paths with a plain recursion sharing no
     * code with the production solver, and return the minimum clue total
     * (endpoints always given) that keeps the solution unique.
     */
    private fun exhaustive3x3Minimum(path: IntArray): Int {
        fun adj(a: Int, b: Int): Boolean {
            val dr = abs(a / 3 - b / 3)
            val dc = abs(a % 3 - b % 3)
            return max(dr, dc) == 1
        }

        fun countSolutions(clues: BooleanArray, limit: Int): Int {
            val used = BooleanArray(9)
            val assigned = IntArray(9)
            var count = 0
            fun walk(number: Int, cell: Int) {
                if (count >= limit) return
                if (number == 9) {
                    count++
                    return
                }
                for (next in 0 until 9) {
                    if (used[next] || !adj(cell, next)) continue
                    if (clues[number + 1] && next != path[number]) continue
                    used[next] = true
                    assigned[number] = next
                    walk(number + 1, next)
                    used[next] = false
                    if (count >= limit) return
                }
            }
            assigned[0] = path[0]
            used[path[0]] = true
            walk(1, path[0])
            return count
        }

        var best = Int.MAX_VALUE
        for (mask in 0 until 128) {
            val clues = BooleanArray(10)
            clues[1] = true
            clues[9] = true
            for (n in 2..8) clues[n] = mask and (1 shl (n - 2)) != 0
            if (countSolutions(clues, 2) == 1) {
                best = minOf(best, (2..8).count { clues[it] } + 2)
            }
        }
        return best
    }
}
