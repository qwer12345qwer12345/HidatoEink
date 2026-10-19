package com.eink.hidato.benchmark

import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.FrontierPathCounter
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.HidatoSolver
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Issue #12 measurements for the frontier DP prototype on narrow boards.
 *
 * Compares, on identical 3xN / 4xN instances with endpoint-only clues:
 *  - the frontier DP (exact count, states, time);
 *  - the production HidatoSolver finding a second path (findAlternative);
 *  - the production solver counting exhaustively (countSolutions).
 *
 * Also records the state-space multiplier when component sizes ride along
 * in the state key: any exact interior-clue extension must distinguish at
 * least the component-size profiles, so this multiplier lower-bounds the
 * blow-up such an extension would suffer.
 *
 * Enabled only with -PhidatoBenchmark=true; distinguish runs with
 * -PhidatoBenchmarkLabel=.
 */
class NarrowBoardFrontierBenchmark {

    private val label: String = System.getProperty("hidato.benchmark.label") ?: "run"

    @Test
    fun run() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        println(
            "ENV label=$label java=${System.getProperty("java.version")} " +
                "os=${System.getProperty("os.name")}/${System.getProperty("os.arch")}"
        )
        println("MED\tid\trows\tcols\tdpPaths\tdpMs\tdpMaxStates\tdpSizedMaxStates\tfindMs\tfindKind\tcountMs\tcountResult\tcountNodes")
        for ((rows, cols) in listOf(3 to 8, 3 to 10, 3 to 12, 3 to 16, 3 to 20, 3 to 24, 4 to 6, 4 to 8, 4 to 10, 4 to 12)) {
            runInstance(rows, cols)
        }
    }

    private fun runInstance(rows: Int, cols: Int) {
        val id = "board-${rows}x$cols"
        val total = rows * cols
        val tour = HamiltonianPaths.randomTour(rows, cols, Random(rows * 1000 + cols))
        val start = tour.first()
        val end = tour.last()
        assertTrue(HamiltonianPaths.isValid(tour, rows, cols))

        val dp = FrontierPathCounter.count(rows, cols, start, end)
        val dpSized = FrontierPathCounter.count(rows, cols, start, end, trackComponentSizes = true)
        assertEquals("sized tracking must not change the count", dp.paths, dpSized.paths)
        assertTrue("a witness tour exists, so the count must be positive", dp.paths >= 1)

        // Production solver: how fast does it find a second path?
        val clues = BooleanArray(total + 1)
        clues[1] = true
        clues[total] = true
        val solver = HidatoSolver()
        val findStart = System.nanoTime()
        val found = solver.findAlternative(
            rows, cols, tour, clues,
            nodeLimit = 50_000_000, timeLimitMillis = 20_000,
        )
        val findMs = (System.nanoTime() - findStart) / 1_000_000
        if (dp.paths >= 2) {
            // A uniqueness claim would be a real soundness bug; a budget
            // exhaustion (Unknown) is honest and simply slower than the DP.
            assertTrue(
                "DP count >= 2: the solver must never claim Unique",
                found !is AlternativeResult.Unique,
            )
        }

        // Production solver: exhaustive count with a bounded node budget.
        val countStart = System.nanoTime()
        val counted = solver.countSolutions(
            rows, cols, tour, clues,
            limit = Int.MAX_VALUE, nodeLimit = 20_000_000,
        )
        val countMs = (System.nanoTime() - countStart) / 1_000_000
        if (counted != -1) {
            assertEquals("exhaustive solver count must equal the DP count", dp.paths, counted.toLong())
        }
        val countNodes = when (found) {
            is AlternativeResult.Found -> found.nodes
            is AlternativeResult.Unique -> found.nodes
            is AlternativeResult.Unknown -> found.nodes
        }

        println(
            "MED\t$id\t$rows\t$cols\t${dp.paths}\t${dp.elapsedMillis}\t${dp.maxStatesPerColumn}\t" +
                "${dpSized.maxStatesPerColumn}\t$findMs\t${found::class.simpleName}\t$countMs\t" +
                "${if (counted == -1) "BUDGET_EXHAUSTED" else counted}\t$countNodes"
        )
    }
}
