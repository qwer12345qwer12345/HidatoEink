package com.eink.hidato.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography

private val HidatoTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * 墨水屏专用灰阶配色：纯黑/白/灰，最大化对比度（16 级灰阶下彩色会被压成
 * 难以区分的灰）。不再使用 Material You 动态取色——它从壁纸取色，在墨水屏
 * 设备上只会产生脏灰色调；surfaceContainer 系列也显式置灰，避免对话框、
 * 文本框等组件回落到默认紫色调。仅 error 保留深红：灰阶屏上呈深灰仍可辨识，
 * 彩色屏预览时语义更清楚。
 */
private val EinkLightColors = lightColorScheme(
    primary = Color(0xFF101010),
    onPrimary = Color(0xFFFAFAFA),
    primaryContainer = Color(0xFFDDDDDD),
    onPrimaryContainer = Color(0xFF101010),
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E4E4),
    onSecondaryContainer = Color(0xFF101010),
    tertiary = Color(0xFF3E3E3E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDEDEDE),
    onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFFCFCFC),
    onBackground = Color(0xFF101010),
    surface = Color(0xFFFCFCFC),
    onSurface = Color(0xFF101010),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF444444),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFEDEDED),
    surfaceContainerHighest = Color(0xFFE7E7E7),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFFC8C8C8),
    inverseSurface = Color(0xFF2A2A2A),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFFE0E0E0),
    error = Color(0xFF8C1C18),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF3DEDC),
    onErrorContainer = Color(0xFF3B0907),
)

/** 深色模式为反相灰阶，供墨水屏设备的系统级夜间反色使用。 */
private val EinkDarkColors = darkColorScheme(
    primary = Color(0xFFF0F0F0),
    onPrimary = Color(0xFF101010),
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = Color(0xFFF0F0F0),
    secondary = Color(0xFFB8B8B8),
    onSecondary = Color(0xFF161616),
    secondaryContainer = Color(0xFF3F3F3F),
    onSecondaryContainer = Color(0xFFEDEDED),
    tertiary = Color(0xFFC8C8C8),
    onTertiary = Color(0xFF141414),
    tertiaryContainer = Color(0xFF3C3C3C),
    onTertiaryContainer = Color(0xFFE8E8E8),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFCCCCCC),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF131313),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    outline = Color(0xFFB0B0B0),
    outlineVariant = Color(0xFF5C5C5C),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF303030),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF370004),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * 无涟漪指示：涟漪是逐帧动画，在墨水屏上无法呈现只会堆积多余的绘制与
 * 刷新。MaterialTheme 自身会提供带涟漪的 LocalIndication，因此必须在
 * MaterialTheme 内部覆盖，按钮等组件才会取到这个空实现。
 */
private object NoIndication : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance =
        NoIndicationInstance
}

private object NoIndicationInstance : IndicationInstance {
    override fun ContentDrawScope.drawIndication() = drawContent()
}

@Composable
fun HidatoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EinkDarkColors else EinkLightColors,
        typography = HidatoTypography,
    ) {
        CompositionLocalProvider(LocalIndication provides NoIndication) {
            content()
        }
    }
}
