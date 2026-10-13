package com.eink.hidato.domain

import kotlin.math.abs
import kotlin.random.Random

/**
 * 8 方向王棋哈密顿路径（King's Hamiltonian Path）生成与校验。
 *
 * 纯随机 Warnsdorff：起点全盘随机，每步在 8 方向未访问邻居中选"未访问度数最小"者，
 * 随机扰动破平局。无任何几何偏好——不奖励对角线、不惩罚连走直线、不设直线步数上限，
 * 直线段与对角线比例由纯随机自然形成（8x8 实测：直线步占比约 65%，最长直线段中位 5 格）。
 * 中途陷入死胡同则整次重试，毫秒级成功。
 */
object HamiltonianPaths {

    /**
     * 校验 path 是否为合法 Hamiltonian 路径：
     * 恰好覆盖全部格子各一次，且相邻两格 8 方向相邻。
     */
    fun isValid(path: IntArray, rows: Int, cols: Int): Boolean {
        val n = rows * cols
        if (path.size != n) return false
        val seen = BooleanArray(n)
        for (i in 0 until n) {
            val cell = path[i]
            if (cell < 0 || cell >= n || seen[cell]) return false
            seen[cell] = true
            if (i > 0 && !Grids.adjacent(path[i - 1], cell, cols)) return false
        }
        return true
    }

    /**
     * 计算路径的对角线走步比例与最大连续同向直线步数（供测试统计使用）。
     */
    fun analyzePath(path: IntArray, cols: Int): Pair<Double, Int> {
        if (path.size <= 1) return 0.0 to 0
        var diagCount = 0
        var maxStraight = 0
        var curStraight = 0
        var lastDr = 0
        var lastDc = 0

        for (i in 1 until path.size) {
            val prev = path[i - 1]
            val cur = path[i]
            val dr = cur / cols - prev / cols
            val dc = cur % cols - prev % cols
            if (abs(dr) == 1 && abs(dc) == 1) {
                diagCount++
                curStraight = 0
                lastDr = 0
                lastDc = 0
            } else {
                if (dr == lastDr && dc == lastDc) {
                    curStraight++
                } else {
                    curStraight = 1
                    lastDr = dr
                    lastDc = dc
                }
                if (curStraight > maxStraight) {
                    maxStraight = curStraight
                }
            }
        }
        val ratio = diagCount.toDouble() / (path.size - 1)
        return ratio to maxStraight
    }

    /**
     * 生成纯随机 8 方向王棋哈密顿路径。
     */
    fun randomTour(
        rows: Int,
        cols: Int,
        random: Random = Random.Default,
    ): IntArray {
        val total = rows * cols
        require(rows in 2..30 && cols in 2..30) { "网格尺寸不合法: ${rows}x${cols}" }

        val pathBuffer = IntArray(total)
        val visited = BooleanArray(total)

        while (true) {
            if (findTour(rows, cols, random, pathBuffer, visited)) {
                return pathBuffer.copyOf()
            }
        }
    }

    /**
     * 单次 Warnsdorff 贪心寻路（纯随机，无几何偏好）。
     * 若成功走满全部格子返回 true，中途陷入死胡同返回 false。
     */
    private fun findTour(
        rows: Int,
        cols: Int,
        random: Random,
        path: IntArray,
        visited: BooleanArray,
    ): Boolean {
        val total = rows * cols
        visited.fill(false)

        // 起点全盘随机（中心、腹地、边缘、四角均可能）
        val start = random.nextInt(total)
        path[0] = start
        visited[start] = true

        val candIndices = IntArray(8)
        val candScores = IntArray(8)

        for (step in 1 until total) {
            val cur = path[step - 1]
            val curR = cur / cols
            val curC = cur % cols

            var candCount = 0

            // 扫描 8 个相邻格子
            for (d in Grids.DIRS) {
                val nr = curR + d[0]
                val nc = curC + d[1]
                if (nr !in 0 until rows || nc !in 0 until cols) continue
                val nIdx = nr * cols + nc
                if (visited[nIdx]) continue

                // 计算该候选格的未访问度数（Warnsdorff 核心）
                var unvisitedDegree = 0
                for (d2 in Grids.DIRS) {
                    val nnr = nr + d2[0]
                    val nnc = nc + d2[1]
                    if (nnr in 0 until rows && nnc in 0 until cols) {
                        val nnIdx = nnr * cols + nnc
                        if (!visited[nnIdx]) unvisitedDegree++
                    }
                }

                // 评分规则：度数越小越优先（乘 100 保持度数为主序），随机扰动破平局
                candIndices[candCount] = nIdx
                candScores[candCount] = unvisitedDegree * 100 + random.nextInt(8)
                candCount++
            }

            if (candCount == 0) {
                return false // 遇到死胡同
            }

            // 选取评分最小（最受约束）的候选格
            var bestIdx = 0
            var bestScore = candScores[0]
            for (i in 1 until candCount) {
                if (candScores[i] < bestScore) {
                    bestScore = candScores[i]
                    bestIdx = i
                }
            }

            val chosen = candIndices[bestIdx]
            path[step] = chosen
            visited[chosen] = true
        }

        return true
    }
}
