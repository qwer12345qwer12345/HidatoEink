package com.eink.hidato.benchmark

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.HittingSetAblationReference
import com.eink.hidato.domain.MinimumClueOptimizer
import java.io.File
import java.util.BitSet
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Issue #11 ablation benchmark for the exact minimum hitting-set master.
 *
 * Compares the promoted incremental-coverage production master against the
 * rescan baseline and three traversal/bound variants (coverage-ordered
 * branching, most-constrained cut, frequency node bound) on:
 *
     *  - deterministic synthetic hypergraphs (overlapping small cuts, the shape
     *    produced by Hidato difference sets), and
     *  - REAL constraint families harvested from the bundled catalog paths via
     *    the seeded reversal + local-replacement witnesses (no solver calls,
     *    fully deterministic).
     *
     * A block-composed union of all 18 families (1076 digits) was tried and
     * deliberately dropped: the shared visited-state set of ~1000-bit BitSets
     * exhausts the default test JVM heap long before the node budget — the
     * master's real scaling limit is that memo, not the per-node scan.
     *
     * Enabled only with -PhidatoHsBenchmark=true; prints RAW per-repeat rows and
     * MED medians as TSV. All variants must agree on the optimum size and proof
     * kind for every instance (hard assertion), so a faster variant can never
     * silently change the answer.
     */
class HittingSetAblationBenchmark {

    private val repeats: Int = (System.getProperty("hidato.benchmark.repeats") ?: "3").toInt()

    private data class Instance(
        val id: String,
        val cuts: List<BitSet>,
        /** Minimum selectable digits (certified lower bound jump). */
        val minimumSelected: Int = 0,
        /** Maximum selectable digits (search may stop earlier). */
        val maxSelected: Int,
        /** Node budget; exhaustion reports UNKNOWN honestly. */
        val nodeCap: Long = 20_000_000L,
    )

    private data class Row(
        val kind: String,
        val size: Int,
        val nodes: Long,
        val elapsedMs: Long,
    ) {
        val microsPerNode: Double get() = if (nodes == 0L) 0.0 else elapsedMs * 1000.0 / nodes
    }

    @Test
    fun run() {
        assumeTrue("enable with -PhidatoHsBenchmark=true", System.getProperty("hidato.hs.benchmark") == "true")
        assumeTrue("repeats must be positive", repeats > 0)
        println(
            "ENV java=${System.getProperty("java.version")} os=${System.getProperty("os.name")}/" +
                "${System.getProperty("os.arch")} cpus=${Runtime.getRuntime().availableProcessors()} repeats=$repeats"
        )
        println("MED\tid\tcuts\tdigits\tvariant\tkind\tsize\tnodes\telapsedMs\tusPerNode")
        for (instance in instances()) runInstance(instance)
    }

    private fun instances(): List<Instance> {
        val out = ArrayList<Instance>()
        // Synthetic overlapping-cut families: cuts of size 2..5 over a pool of
        // digits, deterministic seeds. These mirror Hidato difference sets
        // (small, heavily overlapping) and stress the master search.
        data class Synth(val id: String, val digits: Int, val cutsCount: Int, val maxCutSize: Int)
        for (s in listOf(
            Synth("synth-a-16x40", 16, 40, 4),
            Synth("synth-b-24x90", 24, 90, 4),
            Synth("synth-c-32x180", 32, 180, 5),
            Synth("synth-d-40x300", 40, 300, 5),
            Synth("synth-e-48x450", 48, 450, 5),
        )) {
            val random = Random(s.id.hashCode().toLong() and 0x7fffffff)
            val cuts = ArrayList<BitSet>(s.cutsCount)
            repeat(s.cutsCount) {
                val cut = BitSet(s.digits + 2)
                val size = 2 + random.nextInt(s.maxCutSize - 1)
                repeat(size) { cut.set(2 + random.nextInt(s.digits - 1)) }
                if (!cut.isEmpty) cuts.add(cut)
            }
            out += Instance(s.id, cuts, maxSelected = s.digits / 2, nodeCap = 5_000_000L)
        }
        // Real families harvested from catalog paths (seeded witnesses only).
        // Workload mirrors production: start at the certified witness lower
        // bound and search a bounded window above it.
        val catalog = catalogPuzzles()
        val optimizer = MinimumClueOptimizer()
        catalog.forEachIndexed { index, puzzle ->
            val family = minimized(
                optimizer.segmentReversalWitnesses(puzzle.path, puzzle.cols) +
                    optimizer.localReplacementWitnesses(puzzle.path, puzzle.rows, puzzle.cols),
            )
            assertTrue("catalog row ${index + 1} must yield constraints", family.isNotEmpty())
            val lowerBound = optimizer.witnessLowerBound(family)
            out += Instance(
                "cat${index + 1}-${puzzle.rows}x${puzzle.cols}",
                family,
                minimumSelected = lowerBound,
                maxSelected = lowerBound + 8,
                // Large-digit boards widen the visited-state BitSets; a
                // floundering variant must not exhaust the test JVM heap
                // before its node budget.
                nodeCap = 1_000_000L,
            )
        }
        return out
    }

    /** Replicates the optimizer's inclusion-minimal family reduction. */
    private fun minimized(masks: List<BitSet>): ArrayList<BitSet> {
        val family = ArrayList<BitSet>()
        for (mask in masks) {
            if (mask.isEmpty || family.any { isSubset(it, mask) }) continue
            family.removeAll { isSubset(mask, it) }
            family.add(mask.clone() as BitSet)
        }
        return family
    }

    private fun isSubset(a: BitSet, b: BitSet): Boolean {
        val rest = a.clone() as BitSet
        rest.andNot(b)
        return rest.isEmpty
    }

    private fun catalogPuzzles(): List<com.eink.hidato.domain.Puzzle> {
        val file = listOf(
            File("app/src/main/assets/unique-puzzles.tsv"),
            File("src/main/assets/unique-puzzles.tsv"),
        ).first { it.exists() }
        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { requireNotNull(PrebuiltPuzzleCodec.parse(it)).second }
    }

    private fun runInstance(instance: Instance) {
        val cuts = instance.cuts
        val digits = cuts.maxOf { it.length() }
        val variants: List<Pair<String, () -> Row>> = listOf(
            "incremental" to {
                measure(instance.nodeCap) { maximumNodes ->
                    val answer = MinimumClueOptimizer.HittingSetMaster(
                        cuts, instance.minimumSelected, instance.maxSelected, maximumNodes, Long.MAX_VALUE,
                    ).solve()
                    Triple(answer.kind.toString(), answer.mask?.cardinality() ?: -1, answer.nodes)
                }
            },
        ) + HittingSetAblationReference.Variant.entries.map { variant ->
            variant.name.lowercase() to {
                measure(instance.nodeCap) { maximumNodes ->
                    val answer = HittingSetAblationReference(
                        cuts, instance.minimumSelected, instance.maxSelected, maximumNodes, variant,
                    ).solve()
                    Triple(answer.kind.toString(), answer.mask?.cardinality() ?: -1, answer.nodes)
                }
            }
        }

        val medians = LinkedHashMap<String, Row>()
        for ((name, runner) in variants) {
            val rows = ArrayList<Row>(repeats)
            repeat(repeats) { rows += runner() }
            // --- hard agreement gate across variants ---
            for (row in rows) {
                medians[name]?.let { previous ->
                    assertEquals("${instance.id}: $name disagrees on kind", previous.kind, row.kind)
                    assertEquals("${instance.id}: $name disagrees on size", previous.size, row.size)
                }
            }
            val median = Row(
                rows.sortedBy { it.kind }[rows.size / 2].kind,
                rows.map { it.size }.sorted()[rows.size / 2],
                rows.map { it.nodes }.sorted()[rows.size / 2],
                rows.map { it.elapsedMs }.sorted()[rows.size / 2],
            )
            medians[name] = median
            for (row in rows) {
                println(
                    "RAW\t${instance.id}\t$name\t${row.kind}\t${row.size}\t${row.nodes}\t${row.elapsedMs}\t" +
                        "%.3f".format(row.microsPerNode)
                )
            }
            println(
                "MED\t${instance.id}\t${cuts.size}\t$digits\t$name\t${median.kind}\t${median.size}\t" +
                    "${median.nodes}\t${median.elapsedMs}\t%.3f".format(median.microsPerNode)
            )
        }
    }

    private inline fun measure(nodeCap: Long, block: (Long) -> Triple<String, Int, Long>): Row {
        val started = System.nanoTime()
        val (kind, size, nodes) = block(nodeCap)
        return Row(kind, size, nodes, (System.nanoTime() - started) / 1_000_000L)
    }
}
