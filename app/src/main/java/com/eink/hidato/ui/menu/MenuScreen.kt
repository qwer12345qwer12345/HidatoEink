package com.eink.hidato.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.eink.hidato.domain.Difficulty
import com.eink.hidato.ui.common.EinkButton

/**
 * 主界面：新建游戏（输入行列数并选择难度）+ 继续游戏。
 * 行列数输入校验为 4~15 的整数。
 */
@Composable
fun MenuScreen(
    onStartGame: (rows: Int, cols: Int, difficulty: Difficulty) -> Unit,
    onContinueGame: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val hasUnfinished by viewModel.hasUnfinished.collectAsState()
    val unfinishedSummary by viewModel.unfinishedSummary.collectAsState()

    var rowsText by rememberSaveable { mutableStateOf("") }
    var colsText by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }
    var userEdited by rememberSaveable { mutableStateOf(false) }

    // 用户未手动编辑时，以持久化的用户偏好作为输入框与难度的当前值
    LaunchedEffect(Unit) {
        viewModel.settings.collect { s ->
            if (!userEdited) {
                rowsText = s.rows.toString()
                colsText = s.cols.toString()
                difficulty = s.difficulty
            }
        }
    }

    val rowsValue = rowsText.trim().toIntOrNull()
    val colsValue = colsText.trim().toIntOrNull()
    val inputValid = rowsValue != null && rowsValue in 4..15 && colsValue != null && colsValue in 4..15
    val showError = (rowsText.isNotEmpty() && (rowsValue == null || rowsValue !in 4..15)) ||
        (colsText.isNotEmpty() && (colsValue == null || colsValue !in 4..15))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "数连 Hidato", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "墨水屏专属版 · 从 1 开始按顺序连到最后一个数字",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Text(text = "新建游戏", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = rowsText,
                onValueChange = {
                    userEdited = true
                    rowsText = it.filter(Char::isDigit).take(2)
                },
                label = { Text("行数 (4-15)") },
                isError = rowsText.isNotEmpty() && (rowsValue == null || rowsValue !in 4..15),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = colsText,
                onValueChange = {
                    userEdited = true
                    colsText = it.filter(Char::isDigit).take(2)
                },
                label = { Text("列数 (4-15)") },
                isError = colsText.isNotEmpty() && (colsValue == null || colsValue !in 4..15),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (showError) {
            Text(
                text = "行数与列数必须为 4~15 的整数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(text = "难度", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Difficulty.entries.forEach { d ->
                EinkButton(
                    text = d.label,
                    onClick = {
                        userEdited = true
                        difficulty = d
                    },
                    emphasized = difficulty == d,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        EinkButton(
            text = "开始新游戏",
            onClick = {
                val r = rowsText.trim().toIntOrNull() ?: return@EinkButton
                val c = colsText.trim().toIntOrNull() ?: return@EinkButton
                if (r in 4..15 && c in 4..15) {
                    viewModel.onNewGameConfirmed(r, c, difficulty)
                    onStartGame(r, c, difficulty)
                }
            },
            emphasized = true,
            enabled = inputValid,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Text(text = "继续游戏", style = MaterialTheme.typography.titleLarge)

        // 有未完成游戏时高亮显示
        EinkButton(
            text = if (hasUnfinished) {
                "继续游戏" + (unfinishedSummary?.let { "\n$it" } ?: "")
            } else {
                "暂无进行中的游戏"
            },
            onClick = onContinueGame,
            enabled = hasUnfinished,
            emphasized = hasUnfinished,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}
