package com.eink.hidato.domain

/**
 * Hidato CSP 求解器（按线索分段搜索），混合两种剪枝：
 * - 小间隙段（g <= CHAIN_G_LIMIT）：枚举相邻线索 a→b 之间恰好 (b-a) 步的全部王棋链
 *   （切比雪夫可行性剪枝），位置候选集 = 存活链在该位置的格子集合，选定后按链过滤；
 * - 大间隙段：逐格 DFS，候选到下一线索的 BFS 最短路距离（已放置格子视为障碍）
 *   必须 <= 剩余步数，MRV 按 BFS 距离升序；
 * - 连通性剪枝：段边界与每 4 步 flood fill，剩余空格必须连通；
 * - 双解早停：找到 [limit] 个解立即终止。
 */
class HidatoSolver {

    companion object {
        const val DEFAULT_NODE_LIMIT: Long = 400_000L
        private const val MAX_CHAINS: Int = 20_000
        private const val CHAIN_G_LIMIT: Int = 8
        private const val CONNECTIVITY_EVERY = 4
    }

    /**
     * 统计解的数量（最多数到 [limit] 即停）。
     *
     * @param path  完整解路径，仅用于提供线索位置：path[n-1] 为数字 n 的格子
     * @param clues 数字可见性（下标 1..total；必须 clues[1] == true）
     * @return 找到的解数量；若搜索节点数超过 [nodeLimit] 则返回 -1（视为"无法在预算内验证"）
     */
    fun countSolutions(
        rows: Int,
        cols: Int,
        path: IntArray,
        clues: BooleanArray,
        limit: Int = 2,
        nodeLimit: Long = DEFAULT_NODE_LIMIT,
    ): Int {
        val total = rows * cols
        require(clues[1]) { "数字 1 必须作为线索可见" }

        // 已知线索位置：number -> cell
        val numPos = IntArray(total + 1) { -1 }
        for (n in 1..total) if (clues[n]) numPos[n] = path[n - 1]

        // 线索数字序列（升序），clueNums[0] == 1，clueNums[last] == total
        val clueNums = IntArray(total + 1)
        var m = 0
        for (n in 1..total) if (clues[n]) clueNums[m++] = n

        val placed = BooleanArray(total)
        placed[numPos[1]] = true
        var solutionCount = 0
        var nodes = 0L
        var budgetExceeded = false

        val dist = IntArray(total)
        val queue = IntArray(total)
        val neighbors = Array(total) { cell ->
            val r = cell / cols
            val c = cell % cols
            IntArray(8).let { out ->
                var size = 0
                for (d in Grids.DIRS) {
                    val nr = r + d[0]
                    val nc = c + d[1]
                    if (nr in 0 until rows && nc in 0 until cols) out[size++] = nr * cols + nc
                }
                if (size == out.size) out else out.copyOf(size)
            }
        }
        val floodStack = IntArray(total)
        val visitMarks = IntArray(total)
        var visitStamp = 0

        // 从目标线索格出发，在"未放置格子"上做多源 BFS，dist[cell] = 最短路步数；
        // 已放置格子视为障碍，dist 保持 -1（不可达）。
        fun bfsDist(target: Int) {
            dist.fill(-1)
            var qh = 0
            var qt = 0
            dist[target] = 0
            queue[qt++] = target
            while (qh < qt) {
                val cell = queue[qh++]
                val nd = dist[cell] + 1
                for (idx in neighbors[cell]) {
                    if (dist[idx] == -1 && !placed[idx]) {
                        dist[idx] = nd
                        queue[qt++] = idx
                    }
                }
            }
        }

        fun floodFillReachable(from: Int): Int {
            var sp = 0
            val stamp = ++visitStamp
            floodStack[sp++] = from
            visitMarks[from] = stamp
            var count = 0
            while (sp > 0) {
                val cell = floodStack[--sp]
                count++
                for (idx in neighbors[cell]) {
                    if (!placed[idx] && visitMarks[idx] != stamp) {
                        visitMarks[idx] = stamp
                        floodStack[sp++] = idx
                    }
                }
            }
            return count
        }

        fun connectivityOk(anchor: Int): Boolean {
            val freeCount = total - placed.count { it }
            return freeCount == 0 || floodFillReachable(anchor) == freeCount
        }

        // 枚举段 (a, b) 的全部可行链：长度为 (b-a-1) 的中间格子序列，
        // 起点为 numPos[a]，终点格子必须与 numPos[b] 相邻，链内格子互异且未放置。
        fun enumerateChains(a: Int, b: Int): List<IntArray> {
            val g = b - a
            val startCell = numPos[a]
            val endCell = numPos[b]
            val chains = ArrayList<IntArray>()
            val seq = IntArray(g - 1)
            val used = BooleanArray(total)

            fun walk(step: Int, cur: Int) {
                if (budgetExceeded) return
                if (++nodes > nodeLimit) {
                    budgetExceeded = true
                    return
                }
                if (step == g - 1) {
                    if (Grids.adjacent(cur, endCell, cols)) {
                        chains.add(seq.copyOf())
                        if (chains.size > MAX_CHAINS) budgetExceeded = true
                    }
                    return
                }
                for (idx in neighbors[cur]) {
                    if (placed[idx] || used[idx] || idx == endCell) continue
                    // 切比雪夫可行性：到终点的最短可能步数不能超过剩余步数
                    if (Grids.chebyshev(idx, endCell, cols) > g - 1 - step) continue
                    used[idx] = true
                    seq[step] = idx
                    walk(step + 1, idx)
                    used[idx] = false
                }
            }

            walk(0, startCell)
            return chains
        }

        // 大间隙段逐格 DFS 与分段 DFS 互递归，用函数变量打破 Kotlin 局部函数的前向引用限制
        var bfsWalk: (Int, Int, Int, Int) -> Unit = { _, _, _, _ -> }

        fun dfsSegment(idx: Int) {
            if (solutionCount >= limit || budgetExceeded) return
            if (idx == m - 1) {
                solutionCount++
                return
            }
            val a = clueNums[idx]
            val b = clueNums[idx + 1]
            val g = b - a

            if (g == 1) {
                if (Grids.adjacent(numPos[a], numPos[b], cols)) {
                    placed[numPos[b]] = true
                    dfsSegment(idx + 1)
                    placed[numPos[b]] = false
                }
                return
            }

            if (g <= CHAIN_G_LIMIT) {
                val chains = enumerateChains(a, b)
                if (budgetExceeded) {
                    // 链数超上限：退化为逐格 DFS 继续验证
                    budgetExceeded = false
                    bfsWalk(a + 1, numPos[a], b, idx)
                    return
                }
                if (chains.isEmpty()) return

                val alive = BooleanArray(chains.size) { true }
                if (!connectivityOk(numPos[b])) return

                fun dfsPos(pos: Int, cur: Int) {
                    if (solutionCount >= limit || budgetExceeded) return
                    if (++nodes > nodeLimit) {
                        budgetExceeded = true
                        return
                    }
                    val cand = IntArray(chains.size)
                    var cnt = 0
                    val seen = BooleanArray(total)
                    for (ci in chains.indices) {
                        if (!alive[ci]) continue
                        val cell = chains[ci][pos]
                        if (!seen[cell] && !placed[cell] && Grids.adjacent(cur, cell, cols)) {
                            seen[cell] = true
                            cand[cnt++] = cell
                        }
                    }
                    if (cnt == 0) return

                    if (pos == g - 2) {
                        for (i in 0 until cnt) {
                            val cell = cand[i]
                            val snapshot = alive.copyOf()
                            for (ci in chains.indices) {
                                if (alive[ci] && chains[ci][pos] != cell) alive[ci] = false
                            }
                            placed[cell] = true
                            placed[numPos[b]] = true
                            dfsSegment(idx + 1)
                            placed[numPos[b]] = false
                            placed[cell] = false
                            snapshot.forEachIndexed { ci, v -> alive[ci] = v }
                            if (solutionCount >= limit || budgetExceeded) return
                        }
                        return
                    }

                    for (i in 0 until cnt) {
                        val cell = cand[i]
                        val snapshot = alive.copyOf()
                        for (ci in chains.indices) {
                            if (alive[ci] && chains[ci][pos] != cell) alive[ci] = false
                        }
                        placed[cell] = true
                        dfsPos(pos + 1, cell)
                        placed[cell] = false
                        snapshot.forEachIndexed { ci, v -> alive[ci] = v }
                        if (solutionCount >= limit || budgetExceeded) return
                    }
                }

                dfsPos(0, numPos[a])
            } else {
                bfsWalk(a + 1, numPos[a], b, idx)
            }
        }

        bfsWalk = seg@{ k, cur, b, idx ->
            if (solutionCount >= limit || budgetExceeded) return@seg
            if (++nodes > nodeLimit) {
                budgetExceeded = true
                return@seg
            }
            if (k == b) {
                if (Grids.adjacent(cur, numPos[b], cols)) {
                    placed[numPos[b]] = true
                    dfsSegment(idx + 1)
                    placed[numPos[b]] = false
                }
                return@seg
            }
            bfsDist(numPos[b])
            val candidates = IntArray(8)
            var candCount = 0
            for (idx2 in neighbors[cur]) {
                if (placed[idx2]) continue
                if (dist[idx2] < 0 || dist[idx2] > b - k) continue
                candidates[candCount++] = idx2
            }
            // MRV：按 BFS 距离升序
            for (i in 1 until candCount) {
                val cv = candidates[i]
                val cd = dist[cv]
                var j = i - 1
                while (j >= 0 && dist[candidates[j]] > cd) {
                    candidates[j + 1] = candidates[j]
                    j--
                }
                candidates[j + 1] = cv
            }
            for (i in 0 until candCount) {
                val cand = candidates[i]
                placed[cand] = true
                if (k % CONNECTIVITY_EVERY == 0 && !connectivityOk(numPos[b])) {
                    placed[cand] = false
                    continue
                }
                bfsWalk(k + 1, cand, b, idx)
                placed[cand] = false
                if (solutionCount >= limit || budgetExceeded) return@seg
            }
        }

        dfsSegment(0)
        return if (budgetExceeded) -1 else solutionCount
    }

    /**
     * 验证在当前线索集下谜题是否恰有唯一解。
     * 搜索预算超限时返回 false（保守处理：视为"不能确认唯一"）。
     */
    fun isUniqueSolution(
        rows: Int,
        cols: Int,
        path: IntArray,
        clues: BooleanArray,
        nodeLimit: Long = DEFAULT_NODE_LIMIT,
    ): Boolean {
        val count = countSolutions(rows, cols, path, clues, limit = 2, nodeLimit = nodeLimit)
        return count == 1
    }
}
