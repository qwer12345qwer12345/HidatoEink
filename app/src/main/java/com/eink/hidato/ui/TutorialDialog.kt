package com.eink.hidato.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eink.hidato.ui.common.EinkButton

@Composable
fun TutorialDialog(onFinish: () -> Unit, error: String?) {
    AlertDialog(
        onDismissRequest = onFinish,
        title = { Text("玩法教程") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("1. 找到数字 1，点击它开始连线。")
                Text("2. 按 1 → 2 → 3 的顺序点击下一格。每一步只能走到上下、左右或对角相邻的格子。")
                Text("3. 根据已显示的线索推断隐藏数字的位置，点击正确的空白格会补上数字；点错不会前进。连到最大数字并填满棋盘即通关。")
                Text("4. 撤销可退回一步；提示会显示一个隐藏数字；重置会清空本关的连线、提示和计数。")
                Text("棋盘较大时可上下、左右滑动查看。完成或跳过本教程后，下次启动不再显示。")
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            EinkButton(text = "开始游玩", onClick = onFinish, emphasized = true)
        },
        dismissButton = {
            EinkButton(text = "跳过", onClick = onFinish)
        },
    )
}
