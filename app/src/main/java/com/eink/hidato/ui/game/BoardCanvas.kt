package com.eink.hidato.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.eink.hidato.domain.GameState

/**
 * 单一 Canvas 自绘整张棋盘，pointerInput 统一处理点击命中。
 *
 * 此前每个格子是一个 Material OutlinedButton：15x15 棋盘需组合约 225 个
 * 按钮节点（各自的语义节点、最小交互尺寸与涟漪指示），任意一步走棋都会
 * 引发全棋盘重组与多次绘制，墨水屏上刷新慢且易残影。
 *
 * 现在组合复杂度从 O(cells) 降为 O(1)：
 * - 点击命中由 [BoardGeometry.cellAt]（纯函数、单测覆盖）完成；
 * - 数字文本布局按数字缓存，同一状态重绘不重复测量；
 * - 无涟漪、无每格语义节点，保留整体 contentDescription 与 48dp 最小格宽。
 */
@Composable
fun BoardCanvas(
    state: GameState,
    onTap: (cell: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rows = state.puzzle.rows
    val cols = state.puzzle.cols
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cell = minOf(maxWidth / cols, maxHeight / rows).coerceAtLeast(48.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(cell * cols, cell * rows)
                    .semantics {
                        contentDescription = boardContentDescription(state)
                    }
                    .pointerInput(cols, rows, state.completed) {
                        detectTapGestures { offset ->
                            if (state.completed) return@detectTapGestures
                            val tapped = BoardGeometry.cellAt(
                                x = offset.x,
                                y = offset.y,
                                rows = rows,
                                cols = cols,
                                cellPx = size.width / cols.toFloat(),
                            )
                            if (tapped >= 0) onTap(tapped)
                        }
                    },
            ) {
                // 数字 -> 文本布局缓存；格子尺寸变化时随之失效重建。
                val textLayouts = remember(cell) { HashMap<Int, TextLayoutResult>() }
                Canvas(Modifier.fillMaxSize()) {
                    val step = size.width / cols
                    val corner = CornerRadius(step * 0.12f)
                    val gap = step * 0.04f
                    val border = Stroke(width = 1.dp.toPx())

                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val index = r * cols + c
                            val n = state.puzzle.numberAt(index)
                            val visible = state.isVisibleNumber(n)
                            val focused = state.focusedCell == index
                            val topLeft = Offset(c * step + gap, r * step + gap)
                            val side = Size(step - 2 * gap, step - 2 * gap)

                            val fill = when {
                                focused -> colors.primary
                                visible -> colors.primaryContainer
                                else -> colors.surface
                            }
                            drawRoundRect(fill, topLeft, side, corner)
                            drawRoundRect(colors.outline, topLeft, side, corner, style = border)

                            if (visible) {
                                val layout = textLayouts.getOrPut(n) {
                                    textMeasurer.measure(
                                        AnnotatedString(n.toString()),
                                        style = TextStyle(
                                            fontSize = (step * 0.42f).toSp(),
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    )
                                }
                                val textTopLeft = Offset(
                                    c * step + (step - layout.size.width) / 2f,
                                    r * step + (step - layout.size.height) / 2f,
                                )
                                drawText(
                                    textLayoutResult = layout,
                                    color = if (focused) colors.onPrimary else colors.onPrimaryContainer,
                                    topLeft = textTopLeft,
                                )
                            }
                        }
                    }

                    // 已出现的相邻数字对 (k, k+1) 之间连线；只连接格子边缘，
                    // 避免连线遮住数字。与旧按钮实现保持一致。
                    val lineWidth = 2.dp.toPx()
                    for (k in 1 until state.total) {
                        if (state.isVisibleNumber(k) && state.isVisibleNumber(k + 1)) {
                            val a = state.puzzle.path[k - 1]
                            val b = state.puzzle.path[k]
                            val from = Offset(BoardGeometry.centerX(a, cols, step), BoardGeometry.centerY(a, cols, step))
                            val to = Offset(BoardGeometry.centerX(b, cols, step), BoardGeometry.centerY(b, cols, step))
                            val shift = (to - from) * 0.35f
                            drawLine(colors.primary, from + shift, to - shift, lineWidth)
                        }
                    }
                }
            }
        }
    }
}

/** 整体语义描述：替代原先每格一个语义节点，供读屏用户了解盘面概况。 */
internal fun boardContentDescription(state: GameState): String {
    val cols = state.puzzle.cols
    val board = "棋盘 ${state.puzzle.rows} 乘 ${state.puzzle.cols}，共 ${state.total} 格，已显示 ${state.placedCount} 个数字"
    val focus = state.focusedCell?.let { "，当前焦点是第 ${it / cols + 1} 行第 ${it % cols + 1} 列" } ?: ""
    return board + focus
}
