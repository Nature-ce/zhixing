package com.zhixing.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── 薄荷核心色（保留品牌色 #5BBF9E）──────────────────────────────
val Mint = Color(0xFF5BBF9E)
val MintDark = Color(0xFF3A8D6E)
val MintLight = Color(0xFFE5F0EB)

// ── 功能性中性色 ─────────────────────────────────────────────────
val Surface = Color(0xFFFAFBFD)
val OnSurface = Color(0xFF1F2937)
val OnSurfaceVariant = Color(0xFF5B6472)
val Outline = Color(0xFFB7C1CB)
val OutlineVariant = Color(0xFFD5DDE5)
val SurfaceVariant = Color(0xFFE2E8EC)

// ── 语义状态色（用于 StatusChip / 时间线 / 排期块等）─────────────
// 进行中 —— 柔和蓝灰
val StatusActiveBg = Color(0xFFE8EFF6)
val StatusActiveFg = Color(0xFF3E5A74)
// 已完成 —— 深薄荷
val StatusDoneBg = Color(0xFFE6F7EF)
val StatusDoneFg = Color(0xFF2E7D5B)
// 已放弃 —— 柔和红
val StatusAbandonBg = Color(0xFFFBECEC)
val StatusAbandonFg = Color(0xFFB23A3A)
// 待排期 —— 浅灰（药丸背景）；fg 用更深色保证 ≥4.5:1 对比
val StatusBacklogBg = Color(0xFFEDEFF1)
val StatusBacklogFg = Color(0xFF4A5460)

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
