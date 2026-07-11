package com.zhixing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ── 完整浅色色板（基于薄荷核心色补全）──────────────────────────────
private val ZhixingLightScheme = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = MintLight,
    onPrimaryContainer = MintDark,
    // secondary 用稍深的蓝灰做辅助色，与薄荷主色形成冷暖互补
    secondary = Color(0xFF546E7A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8EC),
    onSecondaryContainer = Color(0xFF2C3E48),
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Color(0xFFB23A3A),
    onError = Color.White,
    errorContainer = StatusAbandonBg,
    onErrorContainer = StatusAbandonFg,
)

/**
 * 整站设计系统入口。
 *
 * 通过 CompositionLocalProvider 把语义状态色 / 间距 / 圆角 / elevation 注入树，
 * 子树内用 LocalZhixingStatus.current / LocalZhixingSpacing.current 取用。
 *
 * 暂不做深色模式（用户决策）；预留 dark 分支占位便于后续接入。
 */
@Composable
fun ZhixingTheme(content: @Composable () -> Unit) {
    val colorScheme = ZhixingLightScheme

    CompositionLocalProvider(
        LocalZhixingStatus provides ZhixingStatusColors(),
        LocalZhixingSpacing provides ZhixingSpacing(),
        LocalZhixingRadii provides ZhixingRadii(),
        LocalZhixingElevation provides ZhixingElevation(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZhixingTypography,
            content = content,
        )
    }
}
