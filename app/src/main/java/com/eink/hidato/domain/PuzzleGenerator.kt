package com.eink.hidato.domain

import kotlin.math.ceil
import kotlin.random.Random

/**
 * 逆向生成法关卡生成器：
 * 1. 采用纯随机 8 方向王棋哈密顿路径（无几何约束，直线与对角线比例自然形成）；
 * 2. 按路径顺序填入数字 1..M×N；
 * 3. 线索压缩：初始全部可见，按随机顺序隐藏到难度档目标比例（数字 1 与终点强制可见）。
 */
class PuzzleGenerator {

    /**
     * @param deadlineMillis 压缩阶段的时间预算，超时则以当前线索集结束（保证生成时间有界）
     */
    fun generate(
        rows: Int,
        cols: Int,
        difficulty: Difficulty,
        random: Random = Random.Default,
        deadlineMillis: Long = DEFAULT_DEADLINE_MS,
    ): Puzzle {
        require(rows in 4..15 && cols in 4..15) { "网格尺寸必须在 4~15 之间" }
        val total = rows * cols
        val startAt = System.currentTimeMillis()

        // 1. 生成完整路径：纯随机王棋哈密顿路径
        val path = HamiltonianPaths.randomTour(rows, cols, random)
        check(HamiltonianPaths.isValid(path, rows, cols)) { "生成的路径非法" }

        // 2. 填补数字 1..total（Puzzle 构造时由 path 生成 solution）

        // 3. 线索压缩：按难度档目标比例停止，数字 1 与 total 强制保留。
        // 隐藏顺序按"可见线索邻居数"降序（每轮重排、并列随机破平）：优先隐藏紧贴线索的数字，
        // 保持线索间距紧凑，让求解器的距离剪枝始终有效；随机顺序会先挖出孤立数字、
        // 制造长空隙，验证成本爆炸，压缩卡死在 33~37% 压不到难度刻度。
        val targetVisible = targetVisible(total, difficulty)
        val clues = BooleanArray(total + 1) { true }
        clues[1] = true
        clues[total] = true
        var visible = total
        val cellToNumber = IntArray(total)
        for (i in path.indices) cellToNumber[path[i]] = i + 1

        val hideable = (2 until total).toMutableList()
        val order = hideable.shuffled(random).sortedByDescending { n ->
            val cell = path[n - 1]
            val r = cell / cols
            val c = cell % cols
            var score = 0
            for (d in Grids.DIRS) {
                val nr = r + d[0]
                val nc = c + d[1]
                if (nr in 0 until rows && nc in 0 until cols) {
                    val k = cellToNumber[nr * cols + nc]
                    if (clues[k]) score++
                }
            }
            score
        }
        for (n in order) {
            if (visible <= targetVisible) break
            if (System.currentTimeMillis() - startAt >= deadlineMillis) break
            clues[n] = false
            visible--
        }

        return Puzzle(rows, cols, path, clues)
    }

    /**
     * 难度档的题面保留数量（给出的数字个数）。
     * 简单 35%、中等 20%、困难只保留强制可见的 1 与终点。
     */
    private fun targetVisible(total: Int, difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> ceil(total * 0.35).toInt()
        Difficulty.MEDIUM -> ceil(total * 0.20).toInt()
        Difficulty.HARD -> 2
    }

    companion object {
        const val DEFAULT_DEADLINE_MS: Long = 2_000L
    }
}
