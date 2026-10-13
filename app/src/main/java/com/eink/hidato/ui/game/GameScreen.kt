package com.eink.hidato.ui.game

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.eink.hidato.ui.common.EinkButton

/**
 * 游戏界面：顶部仅显示网格行列数，中间为 Canvas 棋盘，
 * 底部为“撤销上一步 / 重置本关 / 提示”三个大按钮。
 */
@Composable
fun GameScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    // 监听走错等一次性 UI 事件，弹出 Toast 提示
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 顶部按需求只显示行列数，通过系统返回键离开本关
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val s = uiState) {
            is GameUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在生成谜题…", style = MaterialTheme.typography.titleMedium)
                }
            }

            is GameUiState.Failed -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.message, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        EinkButton(text = "返回", onClick = onBack)
                    }
                }
            }

            is GameUiState.Playing -> {
                val gameState = s.state

                // 顶部：当前网格行列数
                Text(
                    text = "${gameState.puzzle.rows} x ${gameState.puzzle.cols}",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                // 中间：自适应网格棋盘
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    BoardCanvas(
                        state = gameState,
                        onTap = viewModel::onCellTap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // 底部：三个大按钮（均不小于 48dp 点击区域）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EinkButton(
                        text = "撤销上一步",
                        onClick = viewModel::onUndo,
                        enabled = !gameState.completed,
                        modifier = Modifier.weight(1f),
                    )
                    EinkButton(
                        text = "重置本关",
                        onClick = { showResetDialog = true },
                        enabled = !gameState.completed,
                        modifier = Modifier.weight(1f),
                    )
                    val hintAvailable = (1..gameState.total).any { n ->
                        !gameState.isVisibleNumber(n)
                    }
                    EinkButton(
                        text = "提示",
                        onClick = viewModel::onHint,
                        enabled = hintAvailable,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (s.saving) Text("正在保存通关进度…")
            }
        }
    }

    // 重置确认弹窗
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置本关") },
            text = { Text("确定要重置当前关卡吗？所有已填写的数字和错误记录将被清空。") },
            confirmButton = {
                EinkButton(
                    text = "确认重置",
                    onClick = {
                        showResetDialog = false
                        viewModel.onReset()
                    },
                    emphasized = true,
                )
            },
            dismissButton = {
                EinkButton(
                    text = "取消",
                    onClick = { showResetDialog = false },
                )
            },
        )
    }

    // 通关结算
    val playing = uiState as? GameUiState.Playing
    if (playing != null && playing.state.completed && !playing.saving) {
        AlertDialog(
            onDismissRequest = { /* 必须点击“完成”返回主界面 */ },
            title = { Text("通关！") },
            text = {
                Text(
                    text = "你已按顺序连接全部 ${playing.state.total} 个数字。\n" +
                        "错误 ${playing.state.errorCount} 次 · 提示 ${playing.state.hintCount} 次",
                )
            },
            confirmButton = {
                EinkButton(
                    text = "完成",
                    onClick = onFinished,
                    emphasized = true,
                )
            },
        )
    }
}
