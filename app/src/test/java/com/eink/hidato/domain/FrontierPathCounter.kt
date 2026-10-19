package com.eink.hidato.domain

/**
 * Frontier (transfer-matrix) dynamic program that EXACTLY counts Hamil-
 * tonian paths between two cells on narrow king grids (issue #12).
 *
 * With only the two endpoints clued, a valid Hidato tour is exactly a
 * Hamiltonian path from cell(a) to cell(b): every such path induces its own
 * digit labelling. Counting them therefore decides endpoint-only uniqueness
 * (count == 1) and gives an exhaustive alternative-path count for narrow
 * boards without enumerating the paths themselves.
 *
 * Method. A virtual vertex v0 with two FORCED edges (v0,a) and (v0,b) turns
 * "Hamiltonian path a->b" into "Hamiltonian cycle through v0"; every real
 * cell then has target degree 2 (the virtual edge preloads one degree for a
 * and b). The DP sweeps the board column-major. After each cell the
 * interface to the unprocessed region is at most one full column plus three
 * cells of the previous column (W+3 slots). A state stores, per slot, the
 * current degree and a component label (0 when saturated), the number of
 * endpoint cells not yet processed, and optionally per-component cell
 * counts. Invariants: every component has exactly two open plugs (degree
 * deficits, plus the virtual plugs of unprocessed endpoints); a label whose
 * slots all saturate vanishes, which is an early seal and is pruned unless
 * it happens on the very last cell; bridging the two plugs of one component
 * (cycle closure) is legal only on the last cell. A last-cell transition is
 * accepted iff exactly one component was open, everything saturates and no
 * endpoint is pending -- i.e. a single Hamiltonian cycle through v0.
 *
 * State count is bounded by a function of the width alone; the harness
 * measures it per column. Research prototype, deliberately isolated in test
 * sources: production keeps HidatoSolver as the only oracle.
 */
internal object FrontierPathCounter {

    data class Statistics(
        val paths: Long,
        /** Largest number of live states observed at any column boundary. */
        val maxStatesPerColumn: Int,
        val totalStates: Long,
        val totalTransitions: Long,
        val elapsedMillis: Long,
        /** Distinct state keys when component sizes are tracked, else 0. */
        val sizedStates: Long = 0L,
    )

    private const val VIRTUAL = 1

    /** Column-major processing order: index k = c * rows + r. */
    fun count(
        rows: Int,
        cols: Int,
        start: Int,
        end: Int,
        trackComponentSizes: Boolean = false,
    ): Statistics {
        require(rows >= 2 && cols >= 2)
        require(start in 0 until rows * cols && end in 0 until rows * cols && start != end)
        val began = System.nanoTime()
        val total = rows * cols
        // Processing order k = c * rows + r; cell index follows the Grids
        // convention r * cols + c.
        fun cellOf(k: Int): Int = (k % rows) * cols + (k / rows)

        /**
         * Processed cells still adjacent to unprocessed cells: the already
         * processed prefix of the current column (any of them touches the
         * next column) plus the suffix of the previous column from row r-1
         * down (those still touch the unprocessed rows of the current
         * column). Size is at most rows + 1 -- a function of width alone.
         */
        fun frontierBefore(k: Int): List<Int> {
            val r = k % rows
            val c = k / rows
            val out = ArrayList<Int>(rows + 1)
            for (i in 0 until r) out.add(i * cols + c)
            if (c >= 1) {
                for (j in maxOf(0, r - 1) until rows) out.add(j * cols + (c - 1))
            }
            return out
        }

        // State key: degrees, labels, pending, optional sizes.
        var states: HashMap<String, Long> = HashMap()
        states["|2"] = 1L
        var maxStatesPerColumn = 1
        var totalStates = 0L
        var totalTransitions = 0L
        var sizedStates = 0L
        var accepted = 0L

        for (k in 0 until total) {
            val v = cellOf(k)
            val slots = frontierBefore(k)
            val isEndpoint = v == start || v == end
            val last = k == total - 1
            val nextSlots = if (last) emptyList() else frontierBefore(k + 1)
            val slotIndexOf = HashMap<Int, Int>(slots.size * 2)
            slots.forEachIndexed { i, cell -> slotIndexOf[cell] = i }
            val neighbours = ArrayList<Int>(4)
            for (s in slots.indices) {
                if (Grids.adjacent(slots[s], v, cols)) neighbours.add(s)
            }
            val maxEdges = if (isEndpoint) 1 else 2

            val next = HashMap<String, Long>()
            for ((key, weight) in states) {
                val parts = if (key.substringBefore('|').isEmpty()) emptyList() else key.substringBefore('|').split(';')
                val pending = key.substringAfter('|').substringBefore('#').toInt()
                val deg = IntArray(parts.size)
                val lab = IntArray(parts.size)
                parts.forEachIndexed { i, p ->
                    val d = p.split(',').map { it.toInt() }
                    deg[i] = d[0]
                    lab[i] = d[1]
                }

                val n = neighbours.size
                for (mask in 0 until (1 shl n)) {
                    if (Integer.bitCount(mask) > maxEdges) continue
                    val picked = ArrayList<Int>(2)
                    for (i in 0 until n) if (mask and (1 shl i) != 0) picked.add(neighbours[i])
                    totalTransitions++
                    if (picked.any { deg[it] >= 2 }) continue
                    val preload = if (isEndpoint) 1 else 0
                    val vDeg = preload + picked.size
                    if (vDeg > 2) continue

                    val newDeg = deg.copyOf()
                    for (s in picked) newDeg[s]++

                    val pickedLabels = picked.map { lab[it] }.distinct()
                    val rename = HashMap<Int, Int>()
                    var bridge = false
                    if (picked.size == 2 && pickedLabels.size == 1) {
                        bridge = true
                    } else if (picked.size == 2) {
                        val (l1, l2) = pickedLabels[0] to pickedLabels[1]
                        rename[maxOf(l1, l2)] = minOf(l1, l2)
                    } else if (isEndpoint && picked.size == 1) {
                        // The endpoint merges the virtual component with
                        // the component it attaches to.
                        val other = lab[picked[0]]
                        rename[maxOf(VIRTUAL, other)] = minOf(VIRTUAL, other)
                    }
                    if (bridge && !last) continue

                    fun relabel(l: Int): Int = rename[l] ?: l
                    fun newDegreeOf(cell: Int): Int =
                        if (cell == v) vDeg else newDeg[slotIndexOf.getValue(cell)]

                    var vLabel = 0
                    if (vDeg < 2) {
                        vLabel = when {
                            isEndpoint -> VIRTUAL
                            picked.isEmpty() -> -1 // fresh label
                            else -> lab[picked[0]]
                        }
                    }

                    // Degrees of every slot after the transition.
                    val effLabelOfOld = IntArray(slots.size)
                    for (s in slots.indices) {
                        effLabelOfOld[s] = if (newDeg[s] >= 2) 0 else relabel(lab[s])
                    }
                    val vEffLabel = if (vDeg >= 2) 0 else if (vLabel == -1) -1 else relabel(vLabel)

                    val labelsBefore = buildSet {
                        for (s in slots.indices) if (deg[s] < 2 && lab[s] > 0) add(lab[s])
                        if (pending == 2) add(VIRTUAL)
                    }
                    val newPending = if (isEndpoint) pending - 1 else pending
                    val labelsAfter = buildSet {
                        for (s in slots.indices) if (effLabelOfOld[s] > 0) add(effLabelOfOld[s])
                        if (vEffLabel > 0) add(vEffLabel)
                        if (newPending == 2) add(VIRTUAL)
                    }

                    // Dropped cells must be saturated.
                    var saturated = true
                    for (s in slots.indices) {
                        if (slots[s] !in nextSlots && newDeg[s] != 2) saturated = false
                    }

                    if (last) {
                        if (!saturated) continue
                        if (newPending != 0) continue
                        // Exactly one component may remain open right before
                        // the closing edge; everything else sealed earlier
                        // and would already have been pruned.
                        if (labelsBefore.size != 1) continue
                        accepted += weight
                        continue
                    }
                    if (!saturated) continue
                    // A label whose component survives only through a merge
                    // rename is NOT sealed; only components with no plug on
                    // either side of the rename actually closed.
                    val sealed = labelsBefore.filter { relabel(it) !in labelsAfter }
                    if (sealed.isNotEmpty()) continue

                    // Per-label sizes (optional). Compute the post-transition
                    // sizes in the OLD label space first: apply the merge
                    // rename, then add v's cell to the component it joins.
                    val sizesOld = if (trackComponentSizes) {
                        val raw = key.substringAfter('#', "")
                        if (raw.isEmpty()) HashMap() else {
                            HashMap<Int, Int>().apply {
                                raw.split(';').forEach { e ->
                                    val (l, sz) = e.split('=').map { it.toInt() }
                                    put(l, sz)
                                }
                            }
                        }
                    } else null
                    val sizesNew = if (sizesOld != null) {
                        val grown = HashMap<Int, Int>()
                        for ((l, sz) in sizesOld) {
                            val target = relabel(l)
                            grown[target] = (grown[target] ?: 0) + sz
                        }
                        when {
                            bridge -> Unit
                            picked.size == 2 -> {
                                // v is internal to the merged component.
                                val target = relabel(lab[picked[0]])
                                grown[target] = (grown[target] ?: 0) + 1
                            }
                            vEffLabel == -1 -> grown[-1] = 1
                            vEffLabel > 0 -> grown[vEffLabel] = (grown[vEffLabel] ?: 0) + 1
                            else -> {
                                // v saturated: joins the merged/endpoint
                                // component whose target label is the rename
                                // destination of the picked label.
                                if (isEndpoint && picked.size == 1) {
                                    grown[VIRTUAL] = (grown[VIRTUAL] ?: 0) + 1
                                } else if (picked.size == 2) {
                                    val target = relabel(lab[picked[0]])
                                    grown[target] = (grown[target] ?: 0) + 1
                                }
                            }
                        }
                        grown
                    } else null

                    // Canonicalise labels: VIRTUAL keeps id 1, others get
                    // ids 2.. in order of first appearance. Sizes ride along
                    // as id=size pairs in the same canonical id space.
                    val canon = HashMap<Int, Int>()
                    val sizePairs = ArrayList<String>()
                    var otherLabels = 0
                    val sb = StringBuilder()
                    for (cell in nextSlots) {
                        val d = newDegreeOf(cell)
                        var l = if (cell == v) vEffLabel else {
                            effLabelOfOld[slotIndexOf.getValue(cell)]
                        }
                        if (d >= 2) l = 0
                        if (l == 0) {
                            sb.append(d).append(",0;")
                        } else {
                            val mapped = canon.getOrPut(l) {
                                val id = if (l == VIRTUAL) VIRTUAL else {
                                    otherLabels += 1
                                    otherLabels + 1
                                }
                                if (sizesNew != null) sizePairs.add("$id=${sizesNew[l] ?: 0}")
                                id
                            }
                            sb.append(d).append(',').append(mapped).append(';')
                        }
                    }
                    var stateKey = sb.toString().trimEnd(';') + "|$newPending"
                    if (sizesNew != null) {
                        stateKey += "#" + sizePairs.joinToString(";")
                    }
                    next.merge(stateKey, weight) { a, b -> a + b }
                }
            }
            states = next
            if (k % rows == rows - 1 || k == total - 1) {
                maxStatesPerColumn = maxOf(maxStatesPerColumn, states.size)
            }
            totalStates += states.size
            if (trackComponentSizes) sizedStates += states.size
        }

        return Statistics(
            paths = accepted,
            maxStatesPerColumn = maxStatesPerColumn,
            totalStates = totalStates,
            totalTransitions = totalTransitions,
            elapsedMillis = (System.nanoTime() - began) / 1_000_000L,
            sizedStates = sizedStates,
        )
    }
}
