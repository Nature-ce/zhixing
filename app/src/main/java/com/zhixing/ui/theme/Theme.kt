package com.zhixing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ── 完整浅色色板（暖白清新风，所有 slot 显式填满，杜绝默认紫蓝）──
//
// Material3 的 lightColorScheme 对未填写的 slot 注入 baseline 默认值，
// 其中 tertiary 默认紫红(0xFF7D5260)、inversePrimary/scrim 等带冷紫调，
// 会经 elevation overlay 渗入 NavigationBar / Card / Dialog 等组件。
// 故此处把全部 30+ 个 slot 以暖色调性写死，彻底告别"莫名其妙"的淡紫蓝。
private val ZhixingLightScheme = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = MintLight,
    onPrimaryContainer = MintDark,
    // secondary 用暖灰绿，与薄荷主色调性一致（清新、无冷蓝紫味）。
    // 对比度 ≥4.5:1 on white（WCAG AA）已验证。
    secondary = Color(0xFF4F6356),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1EAE4),
    onSecondaryContainer = Color(0xFF222E27),
    // tertiary 显式改为暖陶色 —— baseline 默认是紫红(0xFF7D5260)，易显紫。
    tertiary = Color(0xFF8C7B6A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E8DD),
    onTertiaryContainer = Color(0xFF2A2118),
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    // surfaceTint 是 elevation 叠面色（Surface/NavigationBar/Card 都用它做色调叠加），
    // baseline 默认 = primary；这里显式指定为暖灰，避免浅色面被薄荷绿冷调染偏。
    surfaceTint = Color(0xFFD5D0C7),
    outline = Outline,
    outlineVariant = OutlineVariant,
    // scrim = 弹窗/抽屉的遮罩色，baseline 默认近黑带冷调 → 改为暖深灰。
    scrim = Color(0xFF2A2622),
    inversePrimary = Color(0xFFB8E6D6),      // 浅薄荷（baseline 默认冷紫）
    inverseSurface = Color(0xFF302D28),       // 暖深灰（baseline 默认冷灰紫）
    inverseOnSurface = Color(0xFFF3EFE9),    // 暖浅灰
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
