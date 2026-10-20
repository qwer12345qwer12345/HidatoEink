package com.eink.hidato.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 难度分级：控制路径几何形态复杂度（连续直线限制与对角线比例） */
enum class Difficulty(val label: String) {
    EASY("简单"),
    MEDIUM("中等"),
    HARD("困难");

    companion object {
        fun from(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

/** 网格几何工具：格子下标 = row * cols + col */
object Grids {

    /** 8 方向：上下左右 + 四条对角线 */
    val DIRS: Array<IntArray> = arrayOf(
        intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1),
        intArrayOf(0, -1), intArrayOf(0, 1),
        intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
    )

    /** 两格之间的切比雪夫距离（8 方向移动的最少步数） */
    fun chebyshev(a: Int, b: Int, cols: Int): Int =
        max(abs(a / cols - b / cols), abs(a % cols - b % cols))

    /** 是否 8 方向相邻（距离恰为 1） */
    fun adjacent(a: Int, b: Int, cols: Int): Boolean = chebyshev(a, b, cols) == 1

    /** 返回 cell 在网格内的全部合法相邻格子 */
    fun neighbors(cell: Int, rows: Int, cols: Int): List<Int> {
        val r = cell / cols
        val c = cell % cols
        val out = ArrayList<Int>(8)
        for (d in DIRS) {
            val nr = r + d[0]
            val nc = c + d[1]
            if (nr in 0 until rows && nc in 0 until cols) out.add(nr * cols + nc)
        }
        return out
    }

    fun clamp(v: Int, lo: Int, hi: Int): Int = min(max(v, lo), hi)
}

/**
 * 谜题不可变数据。
 * @param path 完整解路径：path[n-1] = 数字 n 所在格子下标
 * @param clues 数字可见性：clues[n] = 数字 n 初始是否作为线索显示（下标 1..total）
 */
class Puzzle(
    val rows: Int,
    val cols: Int,
    val path: IntArray,
    val clues: BooleanArray,
) {
    val total: Int = rows * cols

    /** 数字 -> 格子 的逆映射 */
    val solution: IntArray = IntArray(total).also { sol ->
        for (i in 0 until total) sol[path[i]] = i + 1
    }

    init {
        require(path.size == total) { "path size ${path.size} != rows*cols $total" }
        require(clues.size == total + 1) { "clues size ${clues.size} != total+1" }
    }

    fun numberAt(cell: Int): Int = solution[cell]

    fun isClueVisible(n: Int): Boolean = clues[n]
}

/**
 * 一局游戏的完整状态（纯数据，所有变更通过 [GameEngine] 生成新实例）。
 */
data class GameState(
    val puzzle: Puzzle,
    /** 通过“提示”显示出来但尚未作为初始线索的数字集合 */
    val revealedHints: Set<Int>,
    /** 玩家已填入格子的历史记录（按操作顺序存放格子下标；用于全局时间栈撤销） */
    val playerPath: List<Int>,
    /** 当前活动焦点格子的下标；为 null 表示未选中任何焦点 */
    val focusedCell: Int? = null,
    val errorCount: Int,
    val hintCount: Int,
    val completed: Boolean,
) {
    val total: Int get() = puzzle.total

    /**
     * 玩家放置的所有格子集合。
     * GameState 不可变，构造时计算一次：此前每次访问都 toSet() 新建集合，
     * 而棋盘每次重绘会对每个格子调用 isVisibleNumber，热点路径分配量大。
     */
    val playerCells: Set<Int> = playerPath.toSet()

    /** 当前选中的活动数字；无焦点时为 null */
    val focusedNumber: Int? get() = focusedCell?.let { puzzle.numberAt(it) }

    /** 盘面上当前已出现的数字总数（包含初始线索、提示、玩家填入），构造时计算一次 */
    val placedCount: Int = run {
        var count = 0
        for (n in 1..total) if (isVisibleNumber(n)) count++
        count
    }

    /** 是否还有可提示的隐藏数字（等价于 placedCount < total） */
    val hasHiddenNumbers: Boolean get() = placedCount < total

    /** 兼容字段：已填入数字数量 */
    val currentNumber: Int get() = placedCount

    /** 数字 n 当前是否已显示在棋盘上：初始线索 / 提示显示 / 玩家已填入 */
    fun isVisibleNumber(n: Int): Boolean =
        puzzle.isClueVisible(n) || n in revealedHints || puzzle.path[n - 1] in playerCells

    /** 格子 cell 当前是否已显示数字 */
    fun isPlacedCell(cell: Int): Boolean = isVisibleNumber(puzzle.numberAt(cell))

    constructor(
        puzzle: Puzzle,
        revealedHints: Set<Int>,
        playerPath: List<Int>,
        errorCount: Int,
        hintCount: Int,
        completed: Boolean,
    ) : this(puzzle, revealedHints, playerPath, null, errorCount, hintCount, completed)
}

/** 游戏规则引擎：纯函数式状态转移 */
object GameEngine {

    /**
     * 点击格子：
     * 1. 若点击已有数字格：设为当前活动焦点；
     * 2. 若点击空白格且有焦点 K：若相邻且数字为 K+1，则填入并推进焦点至 K+1；若完成盘面则通关；否则 errorCount + 1；
     * 3. 若无焦点点击空白格：忽略。
     */
    fun tap(state: GameState, cell: Int): GameState {
        if (state.completed) return state
        if (cell < 0 || cell >= state.total) return state

        val cellNumber = state.puzzle.numberAt(cell)
        val isAlreadyVisible = state.isVisibleNumber(cellNumber)

        // 1. 点击已显示的数字格 -> 直接切为活动焦点（若全盘已就绪则通关）
        if (isAlreadyVisible) {
            val newState = state.copy(focusedCell = cell)
            val isDone = newState.placedCount == state.total
            return newState.copy(completed = isDone)
        }

        // 2. 点击空白格 -> 需依赖当前焦点
        val focused = state.focusedCell ?: return state // 无焦点点击空格直接忽略
        val focusedNumber = state.puzzle.numberAt(focused)
        if (focusedNumber >= state.total) {
            return state.copy(errorCount = state.errorCount + 1)
        }

        val expected = focusedNumber + 1
        val isAdjacent = Grids.adjacent(focused, cell, state.puzzle.cols)
        val isCorrect = cellNumber == expected

        return if (isAdjacent && isCorrect) {
            val newPlayerPath = state.playerPath + cell
            val newState = state.copy(
                playerPath = newPlayerPath,
                focusedCell = cell,
            )
            val isDone = newState.placedCount == state.total
            newState.copy(completed = isDone)
        } else {
            state.copy(errorCount = state.errorCount + 1)
        }
    }

    /**
     * 全局时间栈撤销：按时间倒序撤销玩家最后一次填数，
     * 撤销后焦点自动跳回该位置的前一个数字（若在盘面可见）。
     */
    fun undo(state: GameState): GameState {
        if (state.playerPath.isEmpty()) return state
        val lastCell = state.playerPath.last()
        val newPlayerPath = state.playerPath.dropLast(1)
        val lastNumber = state.puzzle.numberAt(lastCell)
        val prevNumber = lastNumber - 1
        val prevCell = if (prevNumber >= 1) state.puzzle.path[prevNumber - 1] else null
        val prevVisible = prevCell != null && (state.puzzle.isClueVisible(prevNumber) || prevNumber in state.revealedHints || prevCell in newPlayerPath)
        val newFocused = if (prevVisible) prevCell else newPlayerPath.lastOrNull()
        return state.copy(
            playerPath = newPlayerPath,
            focusedCell = newFocused,
            completed = false,
        )
    }

    /**
     * 提示：从尚未显示的数字中随机选一个显现，并将焦点自动锁定到该提示格。
     */
    fun hint(state: GameState, random: kotlin.random.Random = kotlin.random.Random.Default): GameState {
        if (state.completed) return state
        val candidates = (1..state.total).filter { n -> !state.isVisibleNumber(n) }
        if (candidates.isEmpty()) return state
        val chosen = candidates.random(random)
        val chosenCell = state.puzzle.path[chosen - 1]
        val newState = state.copy(
            revealedHints = state.revealedHints + chosen,
            hintCount = state.hintCount + 1,
            focusedCell = chosenCell,
        )
        return newState.copy(completed = newState.placedCount == state.total)
    }

    /** 重置本关：清空路径、提示与焦点，恢复到初始空焦点状态 */
    fun reset(state: GameState): GameState = GameState(
        puzzle = state.puzzle,
        revealedHints = emptySet(),
        playerPath = emptyList(),
        focusedCell = null,
        errorCount = 0,
        hintCount = 0,
        completed = false,
    )
}
