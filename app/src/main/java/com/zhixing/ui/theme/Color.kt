package com.zhixing.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── 薄荷核心色（保留品牌色 #5BBF9E）──────────────────────────────
val Mint = Color(0xFF5BBF9E)
val MintDark = Color(0xFF3A8D6E)
val MintLight = Color(0xFFE5F0EB)

// ── 功能性中性色（暖白清新风 —— 消除冷蓝/紫蓝底色）────────────────
// 暖白为底、暖灰为框、薄荷绿主色呼应，清新干净（Apple / Notion 风）。
val Surface = Color(0xFFFBFAF7)          // 暖白微乳（替代偏冷的 #FAFBFD）
val OnSurface = Color(0xFF2A2622)        // 暖近黑（避免冷蓝黑）
val OnSurfaceVariant = Color(0xFF5E5A54) // 暖灰（替代偏蓝的 #5B6472）
val Outline = Color(0xFFD5D0C7)          // 暖灰（替代偏蓝的 #B7C1CB）
val OutlineVariant = Color(0xFFE6E2DA)   // 暖浅灰（替代偏蓝的 #D5DDE5）
val SurfaceVariant = Color(0xFFEFECE6)   // 暖奶油灰（替代偏蓝的 #E2E8EC）

// ── 语义状态色（用于 StatusChip / 时间线 / 排期块等）─────────────
// 进行中 —— 暖米灰底（原本 0xFFECEEF1 蓝通道偏高，会被感知为薰衣草紫），
// 深棕灰前景承担"进行中"语义，底板与前景均走暖调（R≥G≥B），杜绝冷蓝紫。
val StatusActiveBg = Color(0xFFECE9E2)
val StatusActiveFg = Color(0xFF3F3A32)
// 已完成 —— 浅薄荷底 + 深薄荷前景
val StatusDoneBg = Color(0xFFE6F7EF)
val StatusDoneFg = Color(0xFF2E7D5B)
// 已放弃 —— 柔红底 + 深红前景
val StatusAbandonBg = Color(0xFFFBECEC)
val StatusAbandonFg = Color(0xFFB23A3A)
// 待排期 —— 暖米灰药丸底（消除偏冷蓝味）；fg 用深暖棕保证 ≥4.5:1 对比
val StatusBacklogBg = Color(0xFFEDEAE3)
val StatusBacklogFg = Color(0xFF4A4339)

/**
 * 不在 Material3 ColorScheme 里的「超集」语义色，通过 CompositionLocal 下发。
 * 用法：LocalZhixingStatus.current.doneBg
 */
@Immutable
data class ZhixingStatusColors(
    val activeBg: Color = StatusActiveBg,
    val activeFg: Color = StatusActiveFg,
    val doneBg: Color = StatusDoneBg,
    val doneFg: Color = StatusDoneFg,
    val abandonBg: Color = StatusAbandonBg,
    val abandonFg: Color = StatusAbandonFg,
    val backlogBg: Color = StatusBacklogBg,
    val backlogFg: Color = StatusBacklogFg,
)

val LocalZhixingStatus = staticCompositionLocalOf { ZhixingStatusColors() }
