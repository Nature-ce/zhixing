package com.zhixing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 薄荷微风 palette (Variant A decision)
private val Mint = Color(0xFF5BBF9E)
private val MintLight = Color(0xFFE5F0EB)
private val MintDark = Color(0xFF3A8D6E)

private val LightScheme = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = MintLight,
    onPrimaryContainer = MintDark,
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF1F2937),
)

@Composable
fun ZhixingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        content = content,
    )
}
