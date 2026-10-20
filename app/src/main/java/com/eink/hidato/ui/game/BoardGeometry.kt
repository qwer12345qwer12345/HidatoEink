package com.eink.hidato.ui.game

/**
 * 棋盘几何换算：格子下标 <-> 像素坐标。
 * 纯 Kotlin、无 Android 依赖，点击命中的正确性由 JVM 单元测试覆盖。
 * 约定：棋盘从 (0,0) 开始连续排布，格子边长 cellPx，下标 = row * cols + col。
 */
object BoardGeometry {

    /** 屏幕坐标 -> 格子下标；cellPx 非法或坐标落在棋盘外时返回 -1。 */
    fun cellAt(x: Float, y: Float, rows: Int, cols: Int, cellPx: Float): Int {
        if (cellPx <= 0f || x < 0f || y < 0f) return -1
        val col = (x / cellPx).toInt()
        val row = (y / cellPx).toInt()
        if (row >= rows || col >= cols) return -1
        return row * cols + col
    }

    /** 格子中心横坐标。 */
    fun centerX(cell: Int, cols: Int, cellPx: Float): Float = (cell % cols + 0.5f) * cellPx

    /** 格子中心纵坐标。 */
    fun centerY(cell: Int, cols: Int, cellPx: Float): Float = (cell / cols + 0.5f) * cellPx
}
