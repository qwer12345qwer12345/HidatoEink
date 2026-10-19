package com.eink.hidato.offline

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.AlternativeResult
import com.eink.hidato.domain.Grids
import com.eink.hidato.domain.HamiltonianPaths
import com.eink.hidato.domain.HidatoSolver
import com.eink.hidato.domain.MinimumClueOptimizer
import com.eink.hidato.domain.Puzzle
import java.io.File
import java.security.MessageDigest
import java.util.BitSet
import kotlin.math.sqrt

private val gaps = listOf(1, 2, 3, 4, 8, 16)
private data class Case(val id: String, val source: String, val puzzle: Puzzle)
private data class Witness(val start: Int, val end: Int, val mask: BitSet, var multiplicity: Int = 1, val tour: IntArray? = null)
private data class LocalResult(val windows: Int, val tours: Int, val witnesses: List<Witness>)
private data class Probe(val name: String, val status: String, val nodes: Long, val mask: BitSet? = null, var novel: Boolean? = null, val tour: IntArray? = null)
private data class GapStat(val distance: Double?, val slack: Double?, val exact: Double?)
private data class Geo(val turns: List<Int>, val runs: List<Int>, val gapStats: Map<Int, GapStat>) {
    val meanRun: Double get() = runs.average()
    val maxRun: Int get() = runs.maxOrNull() ?: 0
    val turnDensity: Double get() = turns.size.toDouble() / runs.sum().coerceAtLeast(1)
}
private data class Overlap(val meanJaccard: Double?, val disjointFraction: Double?)
private data class ResultRow(
    val case: Case, val hash: String, val geo: Geo,
    val reversalMillis: Double, val localMillis: Double, val solverMillis: Double,
    val reversals: List<Witness>, val reversalUnique: List<BitSet>, val reversalCuts: List<BitSet>,
    val local: LocalResult, val localCuts: List<BitSet>, val localNovel: Int, val localNovelKeys: Set<String>,
    val afterLocal: List<BitSet>, val probes: List<Probe>, val solverUnique: List<BitSet>,
    val solverCuts: List<BitSet>, val solverNovel: Int, val afterSolver: List<BitSet>,
    val lbReversal: Int, val lbLocal: Int, val lbSolver: Int, val tinyValidation: String,
)

fun main(args: Array<String>) {
    require(args.size in 5..9) {
        "Usage: AnalyzeFixedPathGeometryKt <catalog.tsv> <corpus.tsv> <witnesses.csv> <summary.csv> <report.md> [maxWindow] [perWindowCap] [solverNodeLimit] [solverTimeMillis]"
    }
    val catalog = File(args[0]); val corpus = File(args[1])
    val witnessFile = File(args[2]); val summaryFile = File(args[3]); val reportFile = File(args[4])
    val maxWindow = args.getOrNull(5)?.toInt() ?: 8
    val cap = args.getOrNull(6)?.toInt() ?: 64
    val nodeLimit = args.getOrNull(7)?.toLong() ?: 500_000L
    val timeLimit = args.getOrNull(8)?.toLong() ?: 500L
    require(catalog.isFile && corpus.isFile)
    require(maxWindow in 2..10 && cap in 1..1000 && nodeLimit > 0 && timeLimit > 0)

    val cases = loadCases(catalog, corpus)
    require(cases.isNotEmpty() && cases.map { it.id }.distinct().size == cases.size)
    val results = cases.map { analyze(it, maxWindow, cap, nodeLimit, timeLimit) }
    writeWitnesses(witnessFile, results)
    writeSummary(summaryFile, results)
    writeReport(reportFile, results, maxWindow, cap, nodeLimit, timeLimit)
    println("FIXED_PATH_GEOMETRY cases=${results.size} witnesses=${witnessFile.absolutePath} summary=${summaryFile.absolutePath}")
    println("FIXED_PATH_GEOMETRY report_begin")
    reportFile.forEachLine { println(it) }
    println("FIXED_PATH_GEOMETRY report_end")
}

private fun loadCases(catalog: File, corpus: File): List<Case> {
    val catalogRows = catalog.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
    return corpus.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val f = line.split('\t')
        require(f.size == 7) { "Expected seven TSV fields: ${line}" }
        when (f[1].trim()) {
            "catalog" -> {
                val row = f[2].toInt()
                require(f[3] == "-" && f[4] == "-" && f[5] == "-" && row in 1..catalogRows.size)
                Case(f[0].trim(), "catalog-row-${row}", requireNotNull(PrebuiltPuzzleCodec.parse(catalogRows[row - 1])).second)
            }
            "inline" -> {
                val r = f[2].toInt(); val c = f[3].toInt()
                val path = parseInts(f[4]); val givens = parseInts(f[5])
                require(path.size == r * c && HamiltonianPaths.isValid(path, r, c))
                val clues = BooleanArray(path.size + 1)
                givens.forEach { clues[it] = true }
                require(clues[1] && clues[path.size])
                Case(f[0].trim(), "inline-fixed", Puzzle(r, c, path, clues))
            }
            else -> error("Unknown corpus type: ${f[1]}")
        }
    }
}
private fun parseInts(value: String) = value.split(',').map { it.trim().toInt() }.toIntArray()

private fun analyze(c: Case, maxWindow: Int, cap: Int, nodeLimit: Long, timeLimit: Long): ResultRow {
    val p = c.puzzle
    val geo = geometry(p.path, p.cols)
    val reversalStarted = System.nanoTime()
    val reversals = enumerateReversals(p.path, p.rows, p.cols)
    val revUnique = uniqueMasks(reversals.map { it.mask })
    val revCuts = antichain(reversals.map { it.mask })
    val reversalMillis = elapsedMillis(reversalStarted)
    val localStarted = System.nanoTime()
    val local = enumerateLocal(p.path, p.rows, p.cols, maxWindow, cap)
    val localCuts = antichain(local.witnesses.map { it.mask })
    val afterLocal = copyMasks(revCuts)
    val localNovelKeys = linkedSetOf<String>()
    var localNovel = 0
    for (w in local.witnesses.sortedWith(witnessOrder())) {
        if (addCut(afterLocal, w.mask)) { localNovel++; localNovelKeys.add(key(w.mask)) }
    }
    val localMillis = elapsedMillis(localStarted)

    val solverStarted = System.nanoTime()
    val probes = solverProbes(p, nodeLimit, timeLimit)
    val solverUnique = uniqueMasks(probes.mapNotNull { it.mask })
    val solverCuts = antichain(probes.mapNotNull { it.mask })
    val afterSolver = copyMasks(afterLocal)
    var solverNovel = 0
    for (probe in probes) {
        val mask = probe.mask ?: continue
        probe.novel = addCut(afterSolver, mask)
        if (probe.novel == true) solverNovel++
    }
    val solverMillis = elapsedMillis(solverStarted)

    val optimizer = MinimumClueOptimizer()
    val lbRev = optimizer.disjointWitnessLowerBound(revCuts)
    val lbLocal = optimizer.disjointWitnessLowerBound(afterLocal)
    val lbSolver = optimizer.disjointWitnessLowerBound(afterSolver)
    val tinyValidation = if (p.total == 9) {
        val stages = listOf("reversal" to revCuts, "reversal+local" to afterLocal, "all" to afterSolver)
        "PASS(" + stages.joinToString(";") { (name, cuts) ->
            val lb = optimizer.disjointWitnessLowerBound(cuts)
            val exact = exactHittingSet(cuts, p.total)
            check(lb <= exact) { "Lower bound exceeds exact hitting-set optimum for ${name}" }
            "$name=$lb<=$exact"
        } + ")"
    } else "not-run"

    return ResultRow(c, sha256(p.path), geo, reversalMillis, localMillis, solverMillis,
        reversals, revUnique, revCuts, local, localCuts, localNovel, localNovelKeys, afterLocal,
        probes, solverUnique, solverCuts, solverNovel, afterSolver, lbRev, lbLocal, lbSolver, tinyValidation)
}

private fun geometry(path: IntArray, cols: Int): Geo {
    val dirs = (0 until path.size - 1).map { i ->
        intArrayOf(Integer.signum(path[i + 1] / cols - path[i] / cols),
            Integer.signum(path[i + 1] % cols - path[i] % cols))
    }
    val turns = arrayListOf<Int>(); val runs = arrayListOf<Int>()
    var run = 1
    for (i in 1 until dirs.size) {
        if (dirs[i][0] == dirs[i - 1][0] && dirs[i][1] == dirs[i - 1][1]) run++
        else { runs.add(run); run = 1; turns.add(i + 1) }
    }
    if (dirs.isNotEmpty()) runs.add(run)
    val stats = linkedMapOf<Int, GapStat>()
    for (gap in gaps) {
        if (gap >= path.size) stats[gap] = GapStat(null, null, null)
        else {
            val distances = (0 until path.size - gap).map { Grids.chebyshev(path[it], path[it + gap], cols) }
            stats[gap] = GapStat(distances.average(), distances.map { gap - it }.average(),
                distances.count { it == gap }.toDouble() / distances.size)
        }
    }
    return Geo(turns, runs, stats)
}

private fun enumerateReversals(path: IntArray, rows: Int, cols: Int): List<Witness> {
    val out = arrayListOf<Witness>()
    for (start in 1 until path.size - 2) for (end in start + 1 until path.size - 1) {
        if (!Grids.adjacent(path[start - 1], path[end], cols) ||
            !Grids.adjacent(path[start], path[end + 1], cols)) continue
        val tour = path.copyOf()
        for (i in start..end) tour[i] = path[start + end - i]
        if (tour.contentEquals(path)) continue
        check(HamiltonianPaths.isValid(tour, rows, cols))
        val mask = difference(path, tour)
        if (!mask.isEmpty) out.add(Witness(start, end, mask, tour = tour))
    }
    return out
}

/** Research-only bounded-window local replacement prototype. */
private fun enumerateLocal(path: IntArray, rows: Int, cols: Int, maxWindow: Int, cap: Int): LocalResult {
    val unique = linkedMapOf<String, Witness>()
    var windows = 0; var tours = 0
    val used = BooleanArray(maxWindow); val order = IntArray(maxWindow); val cells = IntArray(maxWindow)
    for (start in 1 until path.size - 2) {
        val last = minOf(path.size - 2, start + maxWindow - 1)
        for (end in start + 1..last) {
            windows++
            val size = end - start + 1
            for (i in 0 until size) cells[i] = path[start + i]
            val before = path[start - 1]; val after = path[end + 1]
            var emitted = 0
            fun dfs(pos: Int) {
                if (emitted >= cap) return
                if (pos == size) {
                    val tour = path.copyOf()
                    for (i in 0 until size) tour[start + i] = cells[order[i]]
                    if (tour.contentEquals(path)) return
                    check(HamiltonianPaths.isValid(tour, rows, cols))
                    check(tour.first() == path.first() && tour.last() == path.last())
                    val mask = difference(path, tour)
                    check(!mask.isEmpty)
                    emitted++; tours++
                    val k = key(mask); val old = unique[k]
                    if (old == null) unique[k] = Witness(start, end, mask, tour = tour.copyOf()) else old.multiplicity++
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
            used.fill(false)
            dfs(0)
        }
    }
    return LocalResult(windows, tours, unique.values.toList())
}

private fun solverProbes(p: Puzzle, nodeLimit: Long, timeLimit: Long): List<Probe> {
    val n = p.total
    fun endpoints() = BooleanArray(n + 1).also { it[1] = true; it[n] = true }
    fun stride(k: Int) = endpoints().also { clues -> for (i in 2 until n) if (i % k == 0) clues[i] = true }
    val halfCatalog = endpoints().also { clues -> for (i in 2 until n) if (p.clues[i] && i % 2 == 0) clues[i] = true }
    val candidates = listOf("endpoints-only" to endpoints(), "stride-4" to stride(4),
        "stride-3" to stride(3), "stride-2" to stride(2), "catalog-clues" to p.clues.copyOf(),
        "catalog-even-clues" to halfCatalog)
    val seen = hashSetOf<String>(); val solver = HidatoSolver(); val out = arrayListOf<Probe>()
    for ((name, clues) in candidates) {
        if (!seen.add(clues.joinToString("") { if (it) "1" else "0" })) {
            out.add(Probe(name, "SKIPPED_DUPLICATE", 0L)); continue
        }
        when (val answer = solver.findAlternative(p.rows, p.cols, p.path, clues, nodeLimit, timeLimit)) {
            is AlternativeResult.Found -> {
                check(HamiltonianPaths.isValid(answer.path, p.rows, p.cols))
                check(!answer.path.contentEquals(p.path))
                val mask = difference(p.path, answer.path)
                val given = BitSet(n + 1)
                for (i in 1..n) if (clues[i]) given.set(i)
                check(!mask.isEmpty && !given.intersects(mask))
                out.add(Probe(name, "FOUND", answer.nodes, mask, tour = answer.path.copyOf()))
            }
            is AlternativeResult.Unique -> out.add(Probe(name, "UNIQUE", answer.nodes))
            is AlternativeResult.Unknown -> out.add(Probe(name, "UNKNOWN", answer.nodes))
        }
    }
    return out
}

private fun difference(path: IntArray, tour: IntArray): BitSet {
    val mask = BitSet(path.size + 1)
    for (i in 1 until path.size - 1) if (path[i] != tour[i]) mask.set(i + 1)
    return mask
}
private fun key(mask: BitSet) = mask.stream().toArray().joinToString("|")
private fun uniqueMasks(masks: List<BitSet>) =
    masks.associateBy(::key).values.map { it.clone() as BitSet }.sortedWith(maskOrder())
private fun antichain(masks: List<BitSet>): MutableList<BitSet> {
    val out = arrayListOf<BitSet>()
    for (m in masks.sortedWith(maskOrder())) addCut(out, m)
    return out
}
private fun addCut(cuts: MutableList<BitSet>, mask: BitSet): Boolean {
    if (mask.isEmpty || cuts.any { subset(it, mask) }) return false
    cuts.removeAll { subset(mask, it) }
    cuts.add(mask.clone() as BitSet); cuts.sortWith(maskOrder())
    return true
}
private fun subset(a: BitSet, b: BitSet): Boolean {
    val rest = a.clone() as BitSet; rest.andNot(b); return rest.isEmpty
}
private fun copyMasks(masks: List<BitSet>) = masks.mapTo(ArrayList<BitSet>()) { it.clone() as BitSet }
private fun maskOrder(): Comparator<BitSet> = compareBy<BitSet> { it.cardinality() }.thenBy(::key)
private fun witnessOrder(): Comparator<Witness> = compareBy<Witness> { it.mask.cardinality() }.thenBy { key(it.mask) }
private fun exactHittingSet(cuts: List<BitSet>, total: Int): Int {
    val internal = total - 2
    for (size in 0..internal) for (mask in 0 until (1 shl internal)) {
        if (Integer.bitCount(mask) != size) continue
        val selected = BitSet(total + 1)
        for (i in 0 until internal) if (mask and (1 shl i) != 0) selected.set(i + 2)
        if (cuts.all { it.intersects(selected) }) return size
    }
    error("Finite cut family unexpectedly has no hitting set")
}
private fun overlap(masks: List<BitSet>): Overlap {
    if (masks.size < 2) return Overlap(null, null)
    val sorted = masks.sortedWith(maskOrder())
    val count = minOf(sorted.size, 400)
    val sample = if (count == sorted.size) sorted else (0 until count).map { sorted[it * (sorted.size - 1) / (count - 1)] }
    var sum = 0.0; var pairs = 0; var disjoint = 0
    for (i in sample.indices) for (j in i + 1 until sample.size) {
        val both = sample[i].clone() as BitSet; both.and(sample[j])
        val intersection = both.cardinality()
        if (intersection == 0) disjoint++
        val union = sample[i].clone() as BitSet; union.or(sample[j])
        sum += intersection.toDouble() / union.cardinality().coerceAtLeast(1); pairs++
    }
    return Overlap(sum / pairs, disjoint.toDouble() / pairs)
}
private fun sha256(path: IntArray) =
    MessageDigest.getInstance("SHA-256").digest(path.joinToString(",").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
private fun fmt(v: Double) = if (!v.isFinite()) "" else "%.4f".format(java.util.Locale.ROOT, v)
private fun rate(count: Int, millis: Double) = if (millis <= 0.0) "" else fmt(count * 1000.0 / millis)
private fun elapsedMillis(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000.0

private fun writeWitnesses(file: File, rows: List<ResultRow>) {
    val header = listOf("instance_id","source","path_sha256","family","witness_id","probe","outcome",
        "valid_witness","start_digit","end_digit","window_length","multiplicity","difference_size",
        "difference_digits","alternative_path_cells","novel_non_subsumed_at_insertion","solver_nodes")
    val out = arrayListOf<List<Any?>>()
    for (r in rows) {
        val revPrior = arrayListOf<BitSet>()
        r.reversals.forEachIndexed { i, w ->
            val novel = addCut(revPrior, w.mask)
            out.add(listOf(r.case.id,r.case.source,r.hash,"segment-reversal","reverse-${i+1}","","VALIDATED",true,
                w.start+1,w.end+1,w.end-w.start+1,1,w.mask.cardinality(),key(w.mask),w.tour?.joinToString(",") ?: "",novel,""))
        }
        val localPrior = copyMasks(r.reversalCuts)
        for (w in r.local.witnesses.sortedWith(witnessOrder())) {
            val novel = addCut(localPrior, w.mask)
            out.add(listOf(r.case.id,r.case.source,r.hash,"local-replacement",
                "local-${w.start+1}-${w.end+1}-${key(w.mask)}","","VALIDATED",true,
                w.start+1,w.end+1,w.end-w.start+1,w.multiplicity,w.mask.cardinality(),key(w.mask),w.tour?.joinToString(",") ?: "",novel,""))
        }
        for (p in r.probes) {
            val m = p.mask
            out.add(listOf(r.case.id,r.case.source,r.hash,"full-solver","solver-${p.name}",p.name,p.status,m != null,
                "","", "",if (m == null) "" else 1,m?.cardinality() ?: "",m?.let(::key) ?: "",p.tour?.joinToString(",") ?: "",
                p.novel ?: "",p.nodes))
        }
    }
    writeCsv(file, header, out)
}

private fun writeSummary(file: File, rows: List<ResultRow>) {
    val header = arrayListOf("instance_id","source","rows","cols","cells","path_sha256","turn_count","turn_density",
        "turn_digits","straight_run_count","straight_run_lengths","max_straight_run","mean_straight_run")
    for (g in gaps) {
        header += "mean_chebyshev_gap_${g}"; header += "mean_slack_gap_${g}"; header += "fraction_exact_shortest_gap_${g}"
    }
    header += listOf("reversal_legal_intervals","reversal_unique_difference_sets","reversal_antichain_cuts",
        "reversal_mean_pairwise_jaccard","reversal_disjoint_pair_fraction","reversal_disjoint_lb_optional","reversal_elapsed_ms","reversal_minimal_cuts_per_second",
        "local_windows","local_valid_tours","local_unique_difference_sets","local_antichain_cuts",
        "local_novel_non_subsumed_cuts","local_mean_pairwise_jaccard","local_disjoint_pair_fraction","local_elapsed_ms","local_novel_cuts_per_second",
        "combined_cuts_after_local","disjoint_lb_after_local_optional","disjoint_lb_delta_local",
        "solver_probe_count","solver_found_probes","solver_unique_probes","solver_unknown_probes",
        "solver_unique_difference_sets","solver_antichain_cuts","solver_novel_non_subsumed_cuts","solver_elapsed_ms","solver_novel_cuts_per_second",
        "solver_mean_pairwise_jaccard","solver_disjoint_pair_fraction","combined_cuts_after_solver",
        "disjoint_lb_after_solver_optional","disjoint_lb_delta_solver","solver_probe_outcomes","tiny_bound_validation")
    val data = rows.map { r ->
        val p = r.case.puzzle
        val revOverlap = overlap(r.reversalUnique)
        val localUnique = uniqueMasks(r.local.witnesses.map { it.mask })
        val localOverlap = overlap(localUnique)
        val solverOverlap = overlap(r.solverUnique)
        val found = r.probes.count { it.status == "FOUND" }
        val unique = r.probes.count { it.status == "UNIQUE" }
        val unknown = r.probes.count { it.status == "UNKNOWN" }
        val row = arrayListOf<Any?>(r.case.id,r.case.source,p.rows,p.cols,p.total,r.hash,r.geo.turns.size,
            fmt(r.geo.turnDensity),r.geo.turns.joinToString("|"),r.geo.runs.size,r.geo.runs.joinToString("|"),
            r.geo.maxRun,fmt(r.geo.meanRun))
        for (g in gaps) {
            val stat = r.geo.gapStats.getValue(g)
            row += stat.distance?.let(::fmt) ?: ""; row += stat.slack?.let(::fmt) ?: ""; row += stat.exact?.let(::fmt) ?: ""
        }
        row.addAll(listOf(r.reversals.size,r.reversalUnique.size,r.reversalCuts.size,
            revOverlap.meanJaccard?.let(::fmt) ?: "",revOverlap.disjointFraction?.let(::fmt) ?: "",r.lbReversal,fmt(r.reversalMillis),rate(r.reversalCuts.size,r.reversalMillis),
            r.local.windows,r.local.tours,localUnique.size,r.localCuts.size,r.localNovel,
            localOverlap.meanJaccard?.let(::fmt) ?: "",localOverlap.disjointFraction?.let(::fmt) ?: "",fmt(r.localMillis),rate(r.localNovel,r.localMillis),
            r.afterLocal.size,r.lbLocal,r.lbLocal-r.lbReversal,
            r.probes.count { it.status != "SKIPPED_DUPLICATE" },found,unique,unknown,r.solverUnique.size,r.solverCuts.size,
            r.solverNovel,fmt(r.solverMillis),rate(r.solverNovel,r.solverMillis),solverOverlap.meanJaccard?.let(::fmt) ?: "",solverOverlap.disjointFraction?.let(::fmt) ?: "",
            r.afterSolver.size,r.lbSolver,r.lbSolver-r.lbLocal,r.probes.joinToString(";") { "${it.name}=${it.status}" },
            r.tinyValidation))
        row
    }
    writeCsv(file, header, data)
}

private data class Corr(val feature: String, val outcome: String, val n: Int, val rho: Double?)
private fun corr(feature: String, outcome: String, x: List<Double?>, y: List<Double?>): Corr {
    val pairs = x.zip(y).mapNotNull { (a,b) -> if (a == null || b == null) null else a to b }
    return Corr(feature,outcome,pairs.size,if (pairs.size < 3) null else spearman(pairs.map { it.first },pairs.map { it.second }))
}
private fun ranks(values: List<Double>): List<Double> {
    val sorted = values.indices.sortedBy { values[it] }; val result = DoubleArray(values.size); var start = 0
    while (start < sorted.size) {
        var end = start + 1
        while (end < sorted.size && values[sorted[end]].compareTo(values[sorted[start]]) == 0) end++
        val rank = ((start + 1) + end).toDouble() / 2.0
        for (i in start until end) result[sorted[i]] = rank
        start = end
    }
    return result.toList()
}
private fun spearman(x: List<Double>, y: List<Double>): Double {
    val a = ranks(x); val b = ranks(y); val ma = a.average(); val mb = b.average()
    var numerator = 0.0; var xx = 0.0; var yy = 0.0
    for (i in a.indices) {
        val da = a[i] - ma; val db = b[i] - mb
        numerator += da * db; xx += da * da; yy += db * db
    }
    return if (xx == 0.0 || yy == 0.0) 0.0 else numerator / sqrt(xx * yy)
}

private fun writeReport(file: File, rows: List<ResultRow>, maxWindow: Int, cap: Int, nodeLimit: Long, timeLimit: Long) {
    val totalRev = rows.sumOf { it.reversals.size }
    val totalRevUnique = rows.sumOf { it.reversalUnique.size }
    val totalLocalTours = rows.sumOf { it.local.tours }
    val totalLocalUnique = rows.sumOf { it.local.witnesses.size }
    val totalLocalNovel = rows.sumOf { it.localNovel }
    val totalSolverFound = rows.sumOf { r -> r.probes.count { it.status == "FOUND" } }
    val totalSolverUnique = rows.sumOf { it.solverUnique.size }
    val totalSolverNovel = rows.sumOf { it.solverNovel }
    val totalRevMillis = rows.sumOf { it.reversalMillis }
    val totalLocalMillis = rows.sumOf { it.localMillis }
    val totalSolverMillis = rows.sumOf { it.solverMillis }
    val correlations = listOf(
        corr("turn count","local novel cuts per 100 windows",rows.map { it.geo.turns.size.toDouble() },
            rows.map { if (it.local.windows == 0) null else it.localNovel * 100.0 / it.local.windows }),
        corr("mean straight-run length","unique reversal cuts per 100 legal intervals",rows.map { it.geo.meanRun },
            rows.map { if (it.reversals.isEmpty()) null else it.reversalUnique.size * 100.0 / it.reversals.size }),
        corr("mean Chebyshev distance at label gap 4","solver novel cuts per found probe",rows.map { it.geo.gapStats[4]?.distance },
            rows.map { r -> val found = r.probes.count { it.status == "FOUND" }; if (found == 0) null else r.solverNovel.toDouble()/found }),
        corr("turn density","lower-bound delta after local replacement",rows.map { it.geo.turnDensity },
            rows.map { (it.lbLocal-it.lbReversal).toDouble() }),
    )
    val text = buildString {
        appendLine("# Fixed-path geometry and counterexample analysis")
        appendLine()
        appendLine("Generated by tools/offline/analyze-fixed-path-geometry.sh; counts and timings come from this measured run.")
        appendLine()
        appendLine("## Setup")
        appendLine()
        appendLine("- Corpus: **${rows.size}** deterministic cases (the 3×3 fixture plus all current catalog rows).")
        appendLine("- Local replacement prototype: window length ≤ ${maxWindow}; at most ${cap} alternative tours emitted per window.")
        appendLine("- Full solver probes: endpoints-only, label strides 4/3/2, actual catalog clues, and actual even-numbered clues. Duplicate clue masks are skipped.")
        appendLine("- Each full-solver probe is capped at ${nodeLimit} nodes and ${timeLimit} ms. UNKNOWN is recorded as unknown, never as uniqueness.")
        appendLine("- Runtime: ${System.getProperty("java.runtime.version","unknown")} / ${System.getProperty("os.name","unknown")} ${System.getProperty("os.arch","unknown")}; commit ${System.getenv("GITHUB_SHA") ?: "local"}.")
        appendLine()
        appendLine("## Aggregate counts")
        appendLine()
        appendLine("| Measure | Count |")
        appendLine("|---|---:|")
        appendLine("| Legal segment-reversal intervals | ${totalRev} |")
        appendLine("| Unique reversal difference sets | ${totalRevUnique} |")
        appendLine("| Valid local-replacement tours emitted | ${totalLocalTours} |")
        appendLine("| Unique local-replacement difference sets | ${totalLocalUnique} |")
        appendLine("| Local-replacement cuts not subsumed by the reversal antichain at insertion | ${totalLocalNovel} |")
        appendLine("| Full-solver alternative witnesses returned | ${totalSolverFound} |")
        appendLine("| Unique full-solver difference sets | ${totalSolverUnique} |")
        appendLine("| Full-solver cuts not subsumed by seeded cuts at insertion | ${totalSolverNovel} |")
        appendLine("| Reversal enumeration + antichain time (ms) | ${fmt(totalRevMillis)} |")
        appendLine("| Local replacement + antichain time (ms) | ${fmt(totalLocalMillis)} |")
        appendLine("| Full-solver probes + antichain time (ms) | ${fmt(totalSolverMillis)} |")
        appendLine("| Reversal minimal cuts per second | ${rate(rows.sumOf { it.reversalCuts.size }, totalRevMillis)} |")
        appendLine("| Local novel cuts per second | ${rate(totalLocalNovel, totalLocalMillis)} |")
        appendLine("| Full-solver novel cuts per second | ${rate(totalSolverNovel, totalSolverMillis)} |")
        appendLine()
        appendLine("## Per-instance results")
        appendLine()
        appendLine("| Instance | Board | Turns | Mean run | Mean Chebyshev gap 4 | Reversals: intervals/unique/minimal | Local: tours/unique/novel | Solver: found/unique/novel | Time ms: reversal/local/solver | Disjoint lower bound: reversal → +local → +solver | Probe outcomes |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")
        for (r in rows) {
            val found = r.probes.count { it.status == "FOUND" }
            val unique = r.probes.count { it.status == "UNIQUE" }
            val unknown = r.probes.count { it.status == "UNKNOWN" }
            val gap4 = r.geo.gapStats[4]?.distance?.let(::fmt) ?: "n/a"
            appendLine("| ${r.case.id} | ${r.case.puzzle.rows}×${r.case.puzzle.cols} | ${r.geo.turns.size} | ${fmt(r.geo.meanRun)} | ${gap4} | ${r.reversals.size}/${r.reversalUnique.size}/${r.reversalCuts.size} | ${r.local.tours}/${r.local.witnesses.size}/${r.localNovel} | ${found}/${r.solverUnique.size}/${r.solverNovel} | ${fmt(r.reversalMillis)}/${fmt(r.localMillis)}/${fmt(r.solverMillis)} | ${r.lbReversal} → ${r.lbLocal} → ${r.lbSolver} | F${found}/U${unique}/?${unknown} |")
        }
        appendLine()
        appendLine("## Exploratory Spearman correlations")
        appendLine()
        appendLine("Descriptive associations only: this is a small, size-heterogeneous corpus. Correlation is not causality and is not a pruning proof.")
        appendLine()
        appendLine("| Feature | Outcome | n | Spearman rho |")
        appendLine("|---|---|---:|---:|")
        for (c in correlations) appendLine("| ${c.feature} | ${c.outcome} | ${c.n} | ${c.rho?.let(::fmt) ?: "insufficient data"} |")
        appendLine()
        appendLine("## Interpretation and next falsifiable experiment")
        appendLine()
        appendLine("- Segment-reversal witnesses are complete valid alternative tours. Every local replacement is checked with HamiltonianPaths.isValid before its difference set is accepted.")
        appendLine("- A full-solver cut is counted only when the solver returns Found and the returned path validates. Unique means search completed without an alternative; UNKNOWN is not a uniqueness claim.")
        appendLine("- Disjoint-packing lower bounds are in optional clues. A zero delta means the new family did not strengthen this greedy bound; a negative delta can occur when a smaller cut replaces a superset and changes the greedy packing. This does not invalidate the lower bound.")
        appendLine("- The 3×3 fixture exhaustively enumerates every internal clue subset for each cut family to verify the packing bound does not exceed that family’s exact hitting-set optimum. Larger boards report witness-derived lower bounds, not true minimum-clue counts.")
        appendLine("- Family timings are single-run diagnostics, not stable benchmark claims; compare repeated medians before deciding on an optimization.")
        appendLine("- Overlap statistics use all unique sets up to 400; larger families use a deterministic evenly spaced sample of 400 to bound pairwise work.")
        appendLine()
        appendLine("Next falsifiable experiment: use at most one promising geometry feature only to order local windows, then compare against the current order on identical paths and budgets. Measure novel non-subsumed cuts per second and full-solver calls saved; hold out entire paths or board sizes. Never use correlation alone to discard candidates.")
        appendLine()
        appendLine("## Output files")
        appendLine()
        appendLine("- fixed-path-geometry-witnesses.csv: legal reversal intervals, unique local-replacement difference sets with multiplicity, and every full-solver probe.")
        appendLine("- fixed-path-geometry-summary.csv: per-instance geometry, witness counts, overlap, and lower-bound changes.")
    }
    file.parentFile?.mkdirs()
    file.writeText(text, Charsets.UTF_8)
}
private fun writeCsv(file: File, header: List<String>, rows: List<List<Any?>>) {
    file.parentFile?.mkdirs()
    file.bufferedWriter(Charsets.UTF_8).use { w ->
        w.write(header.joinToString(",") { csv(it) }); w.newLine()
        for (row in rows) { w.write(row.joinToString(",") { csv(it) }); w.newLine() }
    }
}
private fun csv(value: Any?): String {
    val s = value?.toString() ?: ""
    return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"","\"\"") + "\"" else s
}
