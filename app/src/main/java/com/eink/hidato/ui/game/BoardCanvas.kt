package com.eink.hidato.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eink.hidato.domain.GameState

/** 每格是 Material 按钮；棋盘较大时滚动，保持至少 48dp 的触摸区域。 */
@Composable
fun BoardCanvas(
    state: GameState,
    onTap: (cell: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rows = state.puzzle.rows
    val cols = state.puzzle.cols

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cell = minOf(maxWidth / cols, maxHeight / rows).coerceAtLeast(48.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(cell * cols, cell * rows)) {
                Column {
                    for (r in 0 until rows) {
                        Row {
                            for (c in 0 until cols) {
                                val index = r * cols + c
                                val n = state.puzzle.numberAt(index)
                                val visible = state.isVisibleNumber(n)
                                val isFocused = state.focusedCell == index
                                val container = when {
                                    isFocused -> colors.primary
                                    visible -> colors.primaryContainer
                                    else -> colors.surface
                                }
                                val content = when {
                                    isFocused -> colors.onPrimary
                                    visible -> colors.onPrimaryContainer
                                    else -> colors.onSurface
                                }
                                OutlinedButton(
                                    onClick = { onTap(index) },
                                    modifier = Modifier.size(cell).semantics {
                                        contentDescription = "第 ${r + 1} 行，第 ${c + 1} 列，" +
                                            if (visible) (if (isFocused) "数字 $n (当前焦点)" else "数字 $n") else "空白"
                                        selected = isFocused
                                    },
                                    enabled = !state.completed,
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = container,
                                        contentColor = content,
                                        disabledContainerColor = container,
                                        disabledContentColor = content,
                                    ),
                                ) {
                                    Text(if (visible) n.toString() else "")
                                }
                            }
                        }
                    }
                }
                // 只连接格子边缘，避免连线遮住按钮里的数字。
                // 遍历盘面上所有已出现的相邻数字对 (k, k+1) 绘制连线
                Canvas(Modifier.fillMaxSize()) {
                    val step = cell.toPx()
                    for (k in 1 until state.total) {
                        if (state.isVisibleNumber(k) && state.isVisibleNumber(k + 1)) {
                            val a = state.puzzle.path[k - 1]
                            val b = state.puzzle.path[k]
                            val from = Offset((a % cols + 0.5f) * step, (a / cols + 0.5f) * step)
                            val to = Offset((b % cols + 0.5f) * step, (b / cols + 0.5f) * step)
                            val inset = (to - from) * 0.35f
                            drawLine(
                                color = colors.primary,
                                start = from + inset,
                                end = to - inset,
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
            }
        }
    }
}
