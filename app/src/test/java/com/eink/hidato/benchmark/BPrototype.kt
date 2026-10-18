package com.eink.hidato.benchmark

import com.eink.hidato.data.PrebuiltPuzzleCodec
import com.eink.hidato.domain.Grids
import com.eink.hidato.domain.HamiltonianPaths
import java.io.File
import java.util.BitSet
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Research prototype (issue #6, direction B): enumerate windowed LOCAL PATH
 * REPLACEMENTS of the fixed tour S — every re-ordering of a contiguous cell
 * block that reconnects to the same boundary anchors is another valid full
 * Hidato tour, generalizing segment reversal. Measures generation cost and
 * non-redundant constraint yield versus the reversal-only family. Not part of
 * any PR in this form.
 */
class BPrototype {

    private fun reversalWitnesses(path: IntArray, cols: Int): List<BitSet> {
        val witnesses = ArrayList<BitSet>()
        for (start in 1 until path.size - 2) {
            for (end in start + 1 until path.size - 1) {
                if (!Grids.adjacent(path[start - 1], path[end], cols) ||
                    !Grids.adjacent(path[start], path[end + 1], cols)
                ) continue
                val changed = BitSet(path.size + 1)
                for (i in start..end) if (i * 2 != start + end) changed.set(i + 1)
                if (!changed.isEmpty) witnesses.add(changed)
            }
        }
        return witnesses
    }

    /**
     * All replacements of the window from index i to j (i >= 1, j <= n-2):
     * orderings of exactly those cells forming a path whose ends attach to
     * path[i-1] and path[j+1]. Returns the difference sets of every ordering
     * distinct from the original. The cap bounds DFS enumeration per window.
     */
    private fun replacementWitnesses(
        path: IntArray,
        rows: Int,
        cols: Int,
        maxWindow: Int,
        perWindowCap: Int,
    ): List<BitSet> {
        val n = path.size
        val out = ArrayList<BitSet>()
        val window = IntArray(maxWindow)
        for (i in 1 until n - 1) {
            val maxJ = minOf(n - 2, i + maxWindow - 1)
            for (j in i..maxJ) {
                val m = j - i + 1
                for (k in 0 until m) window[k] = path[i + k]
                // local adjacency
                fun adj(a: Int, b: Int) = Grids.adjacent(window[a], window[b], cols)
                val anchorIn = path[i - 1]
                val anchorOut = if (j + 1 < n) path[j + 1] else -1
                val used = BooleanArray(m)
                val order = IntArray(m)
                var found = 0
                var dfsCalls = 0

                fun emit() {
                    // order holds local indices of the replacement
                    val cellOf = IntArray(m)
                    for (pos in 0 until m) cellOf[pos] = window[order[pos]]
                    val changed = BitSet(n + 1)
                    for (pos in 0 until m) {
                        if (cellOf[pos] != path[i + pos]) changed.set(i + pos + 1)
                    }
                    if (!changed.isEmpty) out.add(changed)
                    found++
                }

                fun dfs(pos: Int) {
                    if (found >= perWindowCap || dfsCalls > 20_000) return
                    dfsCalls++
                    if (pos == m) {
                        if (anchorOut < 0 || Grids.adjacent(window[order[m - 1]], anchorOut, cols)) emit()
                        return
                    }
                    for (cand in 0 until m) {
                        if (used[cand]) continue
                        if (pos == 0) {
                            if (!Grids.adjacent(anchorIn, window[cand], cols)) continue
                        } else {
                            if (!adj(order[pos - 1], cand)) continue
                        }
                        // last element must attach to anchorOut
                        if (pos == m - 1 && anchorOut >= 0 && !Grids.adjacent(window[cand], anchorOut, cols)) continue
                        used[cand] = true
                        order[pos] = cand
                        dfs(pos + 1)
                        used[cand] = false
                        if (found >= perWindowCap) return
                    }
                }
                if (m >= 2) dfs(0)
            }
        }
        return out
    }

    private fun nonRedundant(raw: List<BitSet>): List<BitSet> {
        val kept = ArrayList<BitSet>()
        fun subset(a: BitSet, b: BitSet): Boolean {
            val r = a.clone() as BitSet
            r.andNot(b)
            return r.isEmpty
        }
        for (mask in raw) {
            if (mask.isEmpty || kept.any { subset(it, mask) }) continue
            kept.removeAll { subset(mask, it) }
            kept.add(mask.clone() as BitSet)
        }
        return kept
    }

    private fun disjointPacking(cuts: List<BitSet>): Int {
        val occupied = BitSet()
        var count = 0
        for (cut in cuts.sortedBy(BitSet::cardinality)) {
            if (!occupied.intersects(cut)) {
                occupied.or(cut)
                count++
            }
        }
        return count
    }

    @Test
    fun measure() {
        assumeTrue("enable with -PhidatoBenchmark=true", System.getProperty("hidato.benchmark") == "true")
        val rows = mapOf(
            "cat1-4x4" to 1, "cat2-4x4" to 2, "cat4-5x5" to 4, "cat6-5x5" to 6,
            "cat8-6x6" to 8, "cat11-8x8" to 11, "cat16-10x10" to 16, "cat18-15x15" to 18,
        )
        println("BPROTO\tid\tsize\trevCuts\trepCuts\trevLB\trepLB\trevMs\trepMs\tnonredundant/s")
        for ((id, row) in rows) {
            val line = listOf(File("app/src/main/assets/unique-puzzles.tsv"), File("src/main/assets/unique-puzzles.tsv"))
                .first { it.exists() }.readLines().filter { it.isNotBlank() && !it.startsWith("#") }[row - 1]
            val (_, puzzle) = requireNotNull(PrebuiltPuzzleCodec.parse(line))
            val path = puzzle.path

            val t0 = System.nanoTime()
            val revRaw = reversalWitnesses(path, puzzle.cols)
            val revMs = (System.nanoTime() - t0) / 1_000_000

            val t1 = System.nanoTime()
            val repRaw = replacementWitnesses(path, puzzle.rows, puzzle.cols, maxWindow = 8, perWindowCap = 64)
            val repMs = (System.nanoTime() - t1) / 1_000_000

            val revCuts = nonRedundant(revRaw)
            val repAll = nonRedundant(revRaw + repRaw)
            val revLB = disjointPacking(revCuts)
            val repLB = disjointPacking(repAll)
            println(
                "BPROTO\t$id\t${puzzle.rows}x${puzzle.cols}\t${revCuts.size}\t${repAll.size}\t" +
                    "$revLB\t$repLB\t$revMs\t$repMs\t" +
                    String.format("%.0f", (repAll.size - revCuts.size).toDouble() / (repMs + revMs + 1).toDouble() * 1000)
            )
        }
    }
}
