package com.eink.hidato.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** 点击命中与中心点换算：单 Canvas 棋盘用这些纯函数把点击坐标映射回格子下标。 */
class BoardGeometryTest {

    @Test
    fun `cellAt maps coordinates to the containing cell`() {
        // 4x4、每格 100px：(x=150, y=250) -> 第 2 行第 1 列（下标从 0 起）
        assertEquals(2 * 4 + 1, BoardGeometry.cellAt(150f, 250f, rows = 4, cols = 4, cellPx = 100f))
        // 每格左上角与右下角内侧仍命中同一格
        assertEquals(0, BoardGeometry.cellAt(0f, 0f, 4, 4, 100f))
        assertEquals(5, BoardGeometry.cellAt(199.99f, 149.99f, 4, 4, 100f))
    }

    @Test
    fun `cellAt returns -1 outside the board or for invalid cell size`() {
        assertEquals(-1, BoardGeometry.cellAt(-0.01f, 50f, 4, 4, 100f))
        assertEquals(-1, BoardGeometry.cellAt(50f, -0.01f, 4, 4, 100f))
        // 恰好压在右/下边界之外
        assertEquals(-1, BoardGeometry.cellAt(400f, 50f, 4, 4, 100f))
        assertEquals(-1, BoardGeometry.cellAt(50f, 400f, 4, 4, 100f))
        assertEquals(-1, BoardGeometry.cellAt(50f, 50f, 4, 4, 0f))
        assertEquals(-1, BoardGeometry.cellAt(50f, 50f, 4, 4, -10f))
    }

    @Test
    fun `cellAt covers every cell of a non-square board`() {
        val rows = 5
        val cols = 7
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val tapped = BoardGeometry.cellAt(
                    x = c * 64f + 32f,
                    y = r * 64f + 32f,
                    rows = rows,
                    cols = cols,
                    cellPx = 64f,
                )
                assertEquals(r * cols + c, tapped)
            }
        }
        // 越过最后一列右边界属于棋盘外，不回绕到下一行
        assertEquals(
            -1,
            BoardGeometry.cellAt(x = cols * 64f + 32f, y = 1 * 64f + 32f, rows = rows, cols = cols, cellPx = 64f),
        )
    }

    @Test
    fun `center helpers match cell centers`() {
        // 3x5 棋盘、每格 60px：下标 7 = 第 1 行第 2 列 -> 中心 (150, 90)
        assertEquals(150f, BoardGeometry.centerX(7, cols = 5, cellPx = 60f), 0f)
        assertEquals(90f, BoardGeometry.centerY(7, cols = 5, cellPx = 60f), 0f)
    }
}
