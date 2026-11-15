package com.example.grokflipclock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.grokflipclock.data.local.ClockThemeMode

@Immutable
data class ClockPalette(
    val background: Color,
    val panelSurface: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val hingeLine: Color,
    val digitText: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val aiBubble: Color,
    val aiBubbleText: Color,
    val divider: Color
)

val OledBlackPalette = ClockPalette(
    background = Color(0xFF000000),
    panelSurface = Color(0xFF0A0A0A),
    cardBackground = Color(0xFF141414),
    cardBorder = Color(0xFF262626),
    hingeLine = Color(0xFF000000),
    digitText = Color(0xFFF2F2F2),
    primaryText = Color(0xFFEAEAEA),
    secondaryText = Color(0xFF8E8E93),
    accent = Color(0xFF64D2FF),
    userBubble = Color(0xFF1E293B),
    userBubbleText = Color(0xFFF8FAFC),
    aiBubble = Color(0xFF161618),
    aiBubbleText = Color(0xFFE5E5EA),
    divider = Color(0xFF1F1F1F)
)

val JapaneseMinimalPalette = ClockPalette(
    background = Color(0xFFF6F4EE),      // 暖和紙米白
    panelSurface = Color(0xFFEFECE4),    // 亞麻淺灰米
    cardBackground = Color(0xFFE6E1D5),  // 溫潤木紙卡片底
    cardBorder = Color(0xFFD5CFC0),
    hingeLine = Color(0xFFC7C0B0),
    digitText = Color(0xFF2B2B2B),       // 深炭灰數字
    primaryText = Color(0xFF2B2B2B),
    secondaryText = Color(0xFF6E6A61),
    accent = Color(0xFF6B705C),          // 抹茶綠點綴
    userBubble = Color(0xFF6B705C),
    userBubbleText = Color(0xFFFAF9F5),
    aiBubble = Color(0xFFE6E1D5),
    aiBubbleText = Color(0xFF2B2B2B),
    divider = Color(0xFFDCD6C8)
)

val LocalClockPalette = staticCompositionLocalOf { OledBlackPalette }

@Composable
fun GrokFlipClockTheme(
    themeMode: ClockThemeMode,
    content: @Composable () -> Unit
) {
    val palette = when (themeMode) {
        ClockThemeMode.OLED_BLACK -> OledBlackPalette
        ClockThemeMode.JAPANESE_MINIMAL -> JapaneseMinimalPalette
    }

    val m3Scheme = when (themeMode) {
        ClockThemeMode.OLED_BLACK -> darkColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.panelSurface,
            onBackground = palette.primaryText,
            onSurface = palette.primaryText
        )
        ClockThemeMode.JAPANESE_MINIMAL -> lightColorScheme(
            primary = palette.accent,
            background = palette.background,
            surface = palette.panelSurface,
            onBackground = palette.primaryText,
            onSurface = palette.primaryText
        )
    }

    CompositionLocalProvider(LocalClockPalette provides palette) {
        MaterialTheme(
            colorScheme = m3Scheme,
            content = content
        )
    }
}
