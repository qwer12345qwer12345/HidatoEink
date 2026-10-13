package com.eink.hidato.domain

import kotlin.math.ceil
import kotlin.random.Random

/**
 * 逆向生成法关卡生成器：
 * 1. 采用纯随机 8 方向王棋哈密顿路径（无几何约束，直线与对角线比例自然形成）；
 * 2. 按路径顺序填入数字 1..M×N；
 * 3. 线索压缩：初始全部可见，按随机顺序逐个尝试隐藏，每次隐藏后用求解器验证唯一解，
 *    多解则恢复；按难度档的保留比例下限停止（简单 35%、中等 20%、困难压到极限最简），
 *    一轮无任何数字可隐藏为止（数字 1 与终点数字强制可见）。
 */
class PuzzleGenerator(private val solver: HidatoSolver = HidatoSolver()) {

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

        // 3. 线索压缩：按难度档保留比例下限停止，数字 1 与 total 强制保留。
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
        var changed = true
        while (changed && visible > targetVisible) {
            changed = false
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
                // 双解早停：唯一解则永久隐藏；多解则恢复并永久剔除
                // （隐藏集合单调收缩，当前多解的隐藏之后只会更多解，不可能变唯一）
                val count = solver.countSolutions(rows, cols, path, clues, limit = 2, nodeLimit = COMPRESS_NODE_LIMIT)
                if (count == 1) {
                    visible--
                    changed = true
                    hideable.remove(n)
                } else {
                    clues[n] = true
                    if (count >= 2) hideable.remove(n)
                }
            }
            if (System.currentTimeMillis() - startAt >= deadlineMillis) break
        }

        return Puzzle(rows, cols, path, clues)
    }

    /**
     * 难度档的题面保留数量下限（给出的数字个数）。
     * 简单 35%、中等 20%、困难不设下限（压到唯一解极限最简，只剩强制可见的 1 与终点）。
     */
    private fun targetVisible(total: Int, difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> ceil(total * 0.35).toInt()
        Difficulty.MEDIUM -> ceil(total * 0.20).toInt()
        Difficulty.HARD -> 2
    }

    companion object {
        const val DEFAULT_DEADLINE_MS: Long = 2_000L
        const val COMPRESS_NODE_LIMIT: Long = 10_000_000L
    }
}
