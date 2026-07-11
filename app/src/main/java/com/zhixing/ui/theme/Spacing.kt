package com.zhixing.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8pt 栅格间距 / 圆角 / elevation scale。
 *
 * 通过 CompositionLocal 下发，使全 app 引用统一 token，避免各处 16.dp、12.dp 散弹枪。
 */
@Immutable
data class ZhixingSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

@Immutable
data class ZhixingRadii(
    val sm: Dp = 8.dp,   // 排期块 / 药丸
    val md: Dp = 12.dp,  // 卡片
    val lg: Dp = 16.dp,  // 弹窗 / 大卡
    val pill: Dp = 999.dp, // 胶囊
)

@Immutable
data class ZhixingElevation(
    val flat: Dp = 0.dp,
    val low: Dp = 1.dp,    // 子项目行
    val medium: Dp = 2.dp, // 任务卡
    val high: Dp = 6.dp,   // glimpse / 弹窗
)

val LocalZhixingSpacing = staticCompositionLocalOf { ZhixingSpacing() }
val LocalZhixingRadii = staticCompositionLocalOf { ZhixingRadii() }
val LocalZhixingElevation = staticCompositionLocalOf { ZhixingElevation() }
