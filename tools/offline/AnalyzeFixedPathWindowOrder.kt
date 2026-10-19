package com.eink.hidato.offline

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.Grids
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.MinimumClueOptimizer
import com.eink.hidato.domain.Puzzle
import java.io.File
import java.security.MessageDigest
import java.util.BitSet
import java.util.Locale
import java.util.Random
import kotlin.math.ceil

private const val GAP = 4
private val budgets = listOf(25, 50, 100)
private val strategies = listOf("baseline", "gap4-distance-desc", "reverse-control")

private data class Case(val id: String, val source: String, val split: String, val puzzle: Puzzle)
private data class Window(val start: Int, val end: Int, val meanGapDistance: Double)
private data class WindowOutcome(val validTours: Int, val masks: List<BitSet>, val dfsNodes: Long)
private data class Evaluation(
    val case: Case, val strategy: String, val budgetPercent: Int, val totalWindows: Int,
    val selectedWindows: Int, val validTours: Int, val uniqueDifferenceSets: Int, val novelCuts: Int,
    val lowerBoundBefore: Int, val lowerBoundAfter: Int, val elapsedMillis: Double, val dfsNodes: Long,
)
private data class WindowDetail(
    val case: Case, val pathHash: String, val strategy: String, val budgetPercent: Int, val rank: Int,
    val window: Window, val validTours: Int, val uniqueDifferenceSets: Int, val dfsNodes: Long,
)

fun main(args: Array<String>) {
    require(args.size in 5..7) {
        "Usage: AnalyzeFixedPathWindowOrderKt <catalog.tsv> <corpus.tsv> <windows.csv> <summary.csv> <report.md> [maxWindow] [perWindowCap]"
    }
    val catalogFile = File(args[0]); val corpusFile = File(args[1])
    val windowsFile = File(args[2]); val summaryFile = File(args[3]); val reportFile = File(args[4])
    val maxWindow = args.getOrNull(5)?.toInt() ?: 8
    val perWindowCap = args.getOrNull(6)?.toInt() ?: 64
    require(catalogFile.isFile && corpusFile.isFile)
    require(maxWindow in 2..10 && perWindowCap in 1..1000)

    val catalogCases = loadCatalogCases(catalogFile, corpusFile)
    require(catalogCases.isNotEmpty() && catalogCases.map { it.id }.distinct().size == catalogCases.size)
    val holdoutCases = generateSyntheticHoldout(catalogCases)
    val cases = catalogCases + holdoutCases
    val optimizer = MinimumClueOptimizer()
    val evaluations = arrayListOf<Evaluation>()
    val details = arrayListOf<WindowDetail>()

    for (case in cases) {
        val puzzle = case.puzzle
        check(HamiltonianPaths.isValid(puzzle.path, puzzle.rows, puzzle.cols))
        val windows = makeWindows(puzzle.path, puzzle.cols, maxWindow)
        check(windows.isNotEmpty())
        val reversalCuts = reversalAntichain(puzzle.path, puzzle.rows, puzzle.cols)
        val lowerBoundBefore = optimizer.disjointWitnessLowerBound(reversalCuts)

        for (budget in budgets) {
            val count = ceil(windows.size * budget / 100.0).toInt().coerceIn(1, windows.size)
            for (strategy in strategies) {
                val ordered = when (strategy) {
                    "baseline" -> windows.sortedWith(windowOrder())
                    "gap4-distance-desc" -> windows.sortedWith(
                        compareByDescending<Window> { it.meanGapDistance }
                            .thenBy { it.start }.thenBy { it.end }
                    )
                    "reverse-control" -> windows.sortedWith(windowOrder()).asReversed()
                    else -> error("Unknown strategy: ${strategy}")
                }
                val selected = ordered.take(count)
                val pathHash = sha256(puzzle.path)
                val started = System.nanoTime()
                var validTours = 0
                var dfsNodes = 0L
                val allMasks = linkedMapOf<String, BitSet>()
                selected.forEachIndexed { index, window ->
                    val outcome = enumerateWindow(puzzle.path, puzzle.rows, puzzle.cols, window, perWindowCap)
                    validTours += outcome.validTours
                    dfsNodes += outcome.dfsNodes
                    outcome.masks.forEach { mask -> allMasks.putIfAbsent(maskKey(mask), mask) }
                    details.add(WindowDetail(case, pathHash, strategy, budget, index + 1, window,
                        outcome.validTours, outcome.masks.size, outcome.dfsNodes))
                }

                val cutsAfter = copyMasks(reversalCuts)
                var novelCuts = 0
                for (mask in allMasks.values.sortedWith(maskOrder())) {
                    if (addCut(cutsAfter, mask)) novelCuts++
                }
                val lowerBoundAfter = optimizer.disjointWitnessLowerBound(cutsAfter)
                val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
                evaluations.add(Evaluation(case, strategy, budget, windows.size, selected.size, validTours,
                    allMasks.size, novelCuts, lowerBoundBefore, lowerBoundAfter, elapsedMillis, dfsNodes))
            }
        }
    }

    validateFullBudgetEquivalence(evaluations)
    writeWindowCsv(windowsFile, details)
    writeSummaryCsv(summaryFile, evaluations)
    writeReport(reportFile, evaluations, cases, maxWindow, perWindowCap)
    println("FIXED_PATH_WINDOW_ORDER cases=${cases.size} catalog_cases=${catalogCases.size} holdout_cases=${holdoutCases.size}")
    println("FIXED_PATH_WINDOW_ORDER windows=${windowsFile.absolutePath} summary=${summaryFile.absolutePath} report=${reportFile.absolutePath}")
    println("FIXED_PATH_WINDOW_ORDER report_begin")
    reportFile.forEachLine { println(it) }
    println("FIXED_PATH_WINDOW_ORDER report_end")
}

private fun loadCatalogCases(catalogFile: File, corpusFile: File): List<Case> {
    val catalogRows = catalogFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
    return corpusFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val fields = line.split('\t')
        require(fields.size == 7) { "Expected seven TSV fields: ${line}" }
        when (fields[1].trim()) {
            "catalog" -> {
                val row = fields[2].toInt()
                require(fields[3] == "-" && fields[4] == "-" && fields[5] == "-" && row in 1..catalogRows.size)
                val puzzle = requireNotNull(PrebuiltPuzzleCodec.parse(catalogRows[row - 1])).second
                Case(fields[0].trim(), "catalog-row-${row}", "catalog-corpus", puzzle)
            }
            "inline" -> {
                val rows = fields[2].toInt(); val cols = fields[3].toInt()
                val path = parseInts(fields[4]); val givens = parseInts(fields[5])
                require(path.size == rows * cols && HamiltonianPaths.isValid(path, rows, cols))
                val clues = BooleanArray(path.size + 1)
                givens.forEach { clues[it] = true }
                require(clues[1] && clues[path.size])
                Case(fields[0].trim(), "inline-fixed", "catalog-corpus", Puzzle(rows, cols, path, clues))
            }
            else -> error("Unknown corpus type: ${fields[1]}")
        }
    }
}

private fun parseInts(value: String) = value.split(',').map { it.trim().toInt() }.toIntArray()

/**
 * Synthetic validation cases use board sizes absent from the catalog corpus.
 * Each path starts as a row-wise snake and is perturbed only by legal segment reversals.
 */
private fun generateSyntheticHoldout(existing: List<Case>): List<Case> {
    val sizes = listOf(7, 9, 11, 12, 14)
    for (size in sizes) {
        require(existing.none { it.puzzle.rows == size && it.puzzle.cols == size }) {
            "Holdout size ${size}x${size} is now present in the catalog; choose a new unseen size."
        }
    }
    val out = arrayListOf<Case>()
    for (size in sizes) for (variant in 1..3) {
        val seed = size.toLong() * 100_003L + variant * 7_919L
        val random = Random(seed)
        val path = snakePath(size, size)
        repeat(18 + variant * 3) {
            val moves = legalReversalIntervals(path, size, size)
            if (moves.isNotEmpty()) {
                val (start, end) = moves[random.nextInt(moves.size)]
                reverse(path, start, end)
            }
        }
        check(HamiltonianPaths.isValid(path, size, size))
        val clues = BooleanArray(path.size + 1).also { it[1] = true; it[path.size] = true }
        out.add(Case("holdout-${size}x${size}-v${variant}", "synthetic-perturbed-snake",
            "synthetic-heldout", Puzzle(size, size, path, clues)))
    }
    check(out.map { sha256(it.puzzle.path) }.distinct().size == out.size) {
        "Synthetic holdout generator produced duplicate paths"
    }
    return out
}

private fun snakePath(rows: Int, cols: Int): IntArray {
    val path = IntArray(rows * cols)
    var index = 0
    for (r in 0 until rows) {
        if (r % 2 == 0) for (c in 0 until cols) path[index++] = r * cols + c
        else for (c in cols - 1 downTo 0) path[index++] = r * cols + c
    }
    return path
}

private fun legalReversalIntervals(path: IntArray, rows: Int, cols: Int): List<Pair<Int, Int>> {
    val out = arrayListOf<Pair<Int, Int>>()
    for (start in 1 until path.size - 2) for (end in start + 1 until path.size - 1) {
        if (Grids.adjacent(path[start - 1], path[end], cols) &&
            Grids.adjacent(path[start], path[end + 1], cols)) {
            val candidate = path.copyOf()
            reverse(candidate, start, end)
            if (HamiltonianPaths.isValid(candidate, rows, cols)) out.add(start to end)
        }
    }
    return out
}

private fun reverse(path: IntArray, start: Int, end: Int) {
    var left = start; var right = end
    while (left < right) {
        val tmp = path[left]; path[left] = path[right]; path[right] = tmp
        left++; right--
    }
}

private fun makeWindows(path: IntArray, cols: Int, maxWindow: Int): List<Window> {
    val out = arrayListOf<Window>()
    for (start in 1 until path.size - 2) {
        val last = minOf(path.size - 2, start + maxWindow - 1)
        for (end in start + 1..last) {
            val firstPair = maxOf(0, start - GAP)
            val lastPair = minOf(path.size - GAP - 1, end)
            val distances = if (firstPair <= lastPair) {
                (firstPair..lastPair).map { Grids.chebyshev(path[it], path[it + GAP], cols) }
            } else emptyList()
            out.add(Window(start, end, if (distances.isEmpty()) 0.0 else distances.average()))
        }
    }
    return out
}

private fun windowOrder(): Comparator<Window> = compareBy<Window> { it.start }.thenBy { it.end }

/**
 * Enumerate alternative paths within one fixed window. The cap counts validated tours,
 * not DFS nodes. Every accepted tour is independently checked for Hamiltonian validity.
 */
private fun enumerateWindow(path: IntArray, rows: Int, cols: Int, window: Window, cap: Int): WindowOutcome {
    val start = window.start; val end = window.end
    val size = end - start + 1
    val cells = IntArray(size) { path[start + it] }
    val used = BooleanArray(size); val order = IntArray(size)
    val masks = linkedMapOf<String, BitSet>()
    val before = path[start - 1]; val after = path[end + 1]
    var emitted = 0; var nodes = 0L

    fun dfs(pos: Int) {
        if (emitted >= cap) return
        nodes++
        if (pos == size) {
            val tour = path.copyOf()
            for (i in 0 until size) tour[start + i] = cells[order[i]]
            if (tour.contentEquals(path)) return
            check(HamiltonianPaths.isValid(tour, rows, cols)) { "Invalid local replacement: window $start..$end" }
            check(tour.first() == path.first() && tour.last() == path.last())
            val mask = difference(path, tour)
            check(!mask.isEmpty)
            emitted++
            masks.putIfAbsent(maskKey(mask), mask)
            return
        }
        for (candidate in 0 until size) {
            if (used[candidate]) continue
            val cell = cells[candidate]
            if (pos == 0) {
                if (!Grids.adjacent(before, cell, cols)) continue
            } else if (!Grids.adjacent(cells[order[pos - 1]], cell, cols)) continue
            if (pos == size - 1 && !Grids.adjacent(cell, after, cols)) continue
            used[candidate] = true; order[pos] = candidate
            dfs(pos + 1)
            used[candidate] = false
            if (emitted >= cap) return
        }
    }
    dfs(0)
    return WindowOutcome(emitted, masks.values.map { it.clone() as BitSet }, nodes)
}

private fun reversalAntichain(path: IntArray, rows: Int, cols: Int): MutableList<BitSet> {
    val masks = arrayListOf<BitSet>()
    for ((start, end) in legalReversalIntervals(path, rows, cols)) {
        val tour = path.copyOf(); reverse(tour, start, end)
        val mask = difference(path, tour)
        if (!mask.isEmpty) masks.add(mask)
    }
    return antichain(masks)
}

private fun difference(path: IntArray, tour: IntArray): BitSet {
    val mask = BitSet(path.size + 1)
    for (i in 1 until path.size - 1) if (path[i] != tour[i]) mask.set(i + 1)
    return mask
}

private fun maskKey(mask: BitSet) = mask.stream().toArray().joinToString("|")
private fun maskOrder(): Comparator<BitSet> = compareBy<BitSet> { it.cardinality() }.thenBy(::maskKey)

private fun subset(a: BitSet, b: BitSet): Boolean {
    val rest = a.clone() as BitSet
    rest.andNot(b)
    return rest.isEmpty
}

private fun addCut(cuts: MutableList<BitSet>, mask: BitSet): Boolean {
    if (mask.isEmpty || cuts.any { subset(it, mask) }) return false
    cuts.removeAll { subset(mask, it) }
    cuts.add(mask.clone() as BitSet)
    cuts.sortWith(maskOrder())
    return true
}

private fun antichain(masks: List<BitSet>): MutableList<BitSet> {
    val out = arrayListOf<BitSet>()
    for (mask in masks.sortedWith(maskOrder())) addCut(out, mask)
    return out
}

private fun copyMasks(masks: List<BitSet>) = masks.mapTo(ArrayList<BitSet>()) { it.clone() as BitSet }

private fun sha256(path: IntArray) =
    MessageDigest.getInstance("SHA-256").digest(path.joinToString(",").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

private fun fmt(value: Double) = if (!value.isFinite()) "" else "%.4f".format(Locale.ROOT, value)
private fun rate(count: Int, millis: Double) = if (millis <= 0.0) "" else fmt(count * 1000.0 / millis)

private fun validateFullBudgetEquivalence(rows: List<Evaluation>) {
    for (case in rows.map { it.case }.distinctBy { it.id }) {
        val full = rows.filter { it.case.id == case.id && it.budgetPercent == 100 }
        val baseline = full.single { it.strategy == "baseline" }
        check(full.size == strategies.size)
        for (candidate in full) {
            check(candidate.validTours == baseline.validTours)
            check(candidate.uniqueDifferenceSets == baseline.uniqueDifferenceSets)
            check(candidate.novelCuts == baseline.novelCuts)
            check(candidate.lowerBoundAfter == baseline.lowerBoundAfter)
            check(candidate.dfsNodes == baseline.dfsNodes)
        }
    }
}

private fun writeWindowCsv(file: File, rows: List<WindowDetail>) {
    val header = listOf("instance_id", "split", "source", "path_sha256", "strategy", "budget_percent",
        "window_rank", "window_start_label", "window_end_label", "window_length",
        "mean_gap4_chebyshev_distance", "valid_tours", "unique_difference_sets", "dfs_nodes")
    val data = rows.map { r ->
        listOf<Any?>(r.case.id, r.case.split, r.case.source, r.pathHash, r.strategy, r.budgetPercent,
            r.rank, r.window.start + 1, r.window.end + 1, r.window.end - r.window.start + 1,
            fmt(r.window.meanGapDistance), r.validTours, r.uniqueDifferenceSets, r.dfsNodes)
    }
    writeCsv(file, header, data)
}

private fun writeSummaryCsv(file: File, rows: List<Evaluation>) {
    val header = listOf("instance_id", "split", "source", "rows", "cols", "cells", "path_sha256",
        "strategy", "budget_percent", "total_candidate_windows", "selected_windows", "valid_tours",
        "unique_difference_sets", "novel_non_subsumed_cuts", "disjoint_lb_before", "disjoint_lb_after",
        "disjoint_lb_delta", "elapsed_ms", "dfs_nodes", "novel_cuts_per_second",
        "novel_cuts_per_100_windows", "full_budget_equivalence")
    val data = rows.map { r ->
        val p = r.case.puzzle
        listOf<Any?>(r.case.id, r.case.split, r.case.source, p.rows, p.cols, p.total, sha256(p.path),
            r.strategy, r.budgetPercent, r.totalWindows, r.selectedWindows, r.validTours,
            r.uniqueDifferenceSets, r.novelCuts, r.lowerBoundBefore, r.lowerBoundAfter,
            r.lowerBoundAfter - r.lowerBoundBefore, fmt(r.elapsedMillis), r.dfsNodes,
            rate(r.novelCuts, r.elapsedMillis),
            fmt(r.novelCuts * 100.0 / r.selectedWindows),
            if (r.budgetPercent == 100) "PASS" else "n/a")
    }
    writeCsv(file, header, data)
}

private fun writeReport(file: File, rows: List<Evaluation>, allCases: List<Case>, maxWindow: Int, cap: Int) {
    val holdoutSizes = allCases.filter { it.split == "synthetic-heldout" }
        .map { it.puzzle.rows to it.puzzle.cols }.distinct()
        .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
    val text = buildString {
        appendLine("# Geometry-guided local-window ordering benchmark")
        appendLine()
        appendLine("Generated by tools/offline/benchmark-fixed-path-window-order.sh; all counts and timings come from this run.")
        appendLine()
        appendLine("## Protocol")
        appendLine()
        appendLine("- Existing corpus: ${allCases.count { it.split == "catalog-corpus" }} paths from the 3×3 fixture and current catalog.")
        appendLine("- Held-out corpus: ${allCases.count { it.split == "synthetic-heldout" }} deterministic, perturbed snake paths across previously absent square-board sizes.")
        appendLine("- Held-out sizes: ${holdoutSizes.joinToString(", ") { "${it.first}×${it.second}" }}.")
        appendLine("- Each candidate window contains 2..${maxWindow} internal path positions; at most ${cap} validated alternative tours are emitted per window.")
        appendLine("- Budgets select the first 25%, 50%, or 100% of windows under each ordering. Strategies are baseline start/end order, descending window-local mean Chebyshev distance for label gap 4, and reverse-order control.")
        appendLine("- The gap-4 score averages Chebyshev distances for all label-gap-4 pairs whose index interval intersects the candidate window. Ties are resolved by start and end indices.")
        appendLine("- All strategies use the same per-window enumeration order and cap. DFS node counts and elapsed time are recorded because equal window counts are a matched work quota, not an assertion of identical runtime.")
        appendLine("- Every emitted path is checked with HamiltonianPaths.isValid. Difference masks must be nonempty. The 100% results must be exactly equivalent across all orderings; a failed check aborts the run.")
        appendLine()
        appendLine("## Aggregate outcomes")
        appendLine()
        appendLine("| Dataset | Window budget | Baseline novel cuts | Gap-4 novel cuts | Reverse-control novel cuts | Gap-4 delta | Wins/ties/losses vs baseline | Baseline ms | Gap-4 ms | Gap-4 novel cuts/sec |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        for (split in listOf("catalog-corpus", "synthetic-heldout")) {
            for (budget in budgets) {
                val subset = rows.filter { it.case.split == split && it.budgetPercent == budget }
                val baseline = subset.filter { it.strategy == "baseline" }
                val feature = subset.filter { it.strategy == "gap4-distance-desc" }
                val reverse = subset.filter { it.strategy == "reverse-control" }
                val byId = baseline.associateBy { it.case.id }
                val featureById = feature.associateBy { it.case.id }
                var wins = 0; var ties = 0; var losses = 0
                for (id in byId.keys) {
                    when {
                        featureById.getValue(id).novelCuts > byId.getValue(id).novelCuts -> wins++
                        featureById.getValue(id).novelCuts == byId.getValue(id).novelCuts -> ties++
                        else -> losses++
                    }
                }
                val baseNovel = baseline.sumOf { it.novelCuts }
                val featureNovel = feature.sumOf { it.novelCuts }
                val baseMs = baseline.sumOf { it.elapsedMillis }
                val featureMs = feature.sumOf { it.elapsedMillis }
                appendLine("| ${split} | ${budget}% | ${baseNovel} | ${featureNovel} | ${reverse.sumOf { it.novelCuts }} | ${featureNovel - baseNovel} | ${wins}/${ties}/${losses} | ${fmt(baseMs)} | ${fmt(featureMs)} | ${rate(featureNovel, featureMs)} |")
            }
        }
        appendLine()
        appendLine("## Held-out results by board size")
        appendLine()
        appendLine("These rows are the primary generalization check. The heuristic was selected from exploratory catalog results; the held-out board sizes do not appear in that catalog corpus. Synthetic paths are useful for controlled coverage but are not a substitute for additional real puzzle paths.")
        appendLine()
        appendLine("| Board size | Budget | Cases | Baseline novel cuts | Gap-4 novel cuts | Delta | Wins/ties/losses | Gap-4 lower-bound delta sum |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|")
        for ((rowsCount, colsCount) in holdoutSizes) for (budget in budgets) {
            val subset = rows.filter {
                it.case.split == "synthetic-heldout" && it.case.puzzle.rows == rowsCount &&
                    it.case.puzzle.cols == colsCount && it.budgetPercent == budget
            }
            val baseline = subset.filter { it.strategy == "baseline" }.associateBy { it.case.id }
            val feature = subset.filter { it.strategy == "gap4-distance-desc" }.associateBy { it.case.id }
            var wins = 0; var ties = 0; var losses = 0
            for (id in baseline.keys) {
                when {
                    feature.getValue(id).novelCuts > baseline.getValue(id).novelCuts -> wins++
                    feature.getValue(id).novelCuts == baseline.getValue(id).novelCuts -> ties++
                    else -> losses++
                }
            }
            appendLine("| ${rowsCount}×${colsCount} | ${budget}% | ${baseline.size} | ${baseline.values.sumOf { it.novelCuts }} | ${feature.values.sumOf { it.novelCuts }} | ${feature.values.sumOf { it.novelCuts } - baseline.values.sumOf { it.novelCuts }} | ${wins}/${ties}/${losses} | ${feature.values.sumOf { it.lowerBoundAfter - it.lowerBoundBefore }} |")
        }
        appendLine()
        appendLine("## Interpretation rules")
        appendLine()
        appendLine("- Treat 25% and 50% as the useful early-budget comparisons; 100% is a correctness control and must match exactly regardless of order.")
        appendLine("- A positive novel-cut delta is a better set of discovered constraints under that window quota, not proof of faster end-to-end minimum-clue solving. Runtime, DFS nodes, and lower-bound deltas are reported separately.")
        appendLine("- The reverse-order control checks whether an apparent gain is simply due to choosing later windows. The geometry order is deterministic and has no random tie-breaking.")
        appendLine("- The holdout is synthetic and intentionally uses board sizes absent from the current catalog. It tests size transfer but cannot establish performance on every style of Hidato path.")
        appendLine("- No production search order or pruning rule is changed by this tool. A future production change should require repeated-run medians, real held-out puzzle paths, and evidence of end-to-end benefit.")
        appendLine()
        appendLine("## Output files")
        appendLine()
        appendLine("- fixed-path-window-order-windows.csv: each selected window, rank, score, validated tour count, unique masks, and DFS nodes.")
        appendLine("- fixed-path-window-order-summary.csv: per-instance strategy/budget metrics, constraint yield, lower-bound deltas, runtime, and the full-budget equivalence check.")
    }
    file.parentFile?.mkdirs()
    file.writeText(text, Charsets.UTF_8)
}

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
    } else text
}
