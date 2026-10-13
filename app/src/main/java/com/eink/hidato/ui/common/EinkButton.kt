package com.eink.hidato.ui.common

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 共用 Material 按钮，保留主操作高亮与至少 48dp 的触摸区域。 */
@Composable
fun EinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val buttonModifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    if (emphasized) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
        ) { Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = RoundedCornerShape(12.dp),
        ) { Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
    }
}
