package com.silence.blocker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF5EEAD4)
private val TealDark = Color(0xFF0F766E)
private val Bg = Color(0xFF0B0F14)
private val SurfaceCol = Color(0xFF121821)
private val OnBg = Color(0xFFE8EEF5)
private val Muted = Color(0xFF94A3B8)
private val Danger = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF042F2E),
    secondary = TealDark,
    background = Bg,
    surface = SurfaceCol,
    onBackground = OnBg,
    onSurface = OnBg,
    onSurfaceVariant = Muted,
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = TealDark,
    onPrimary = Color.White,
    background = Color(0xFFF4F7FB),
    surface = Color.White,
    onBackground = Color(0xFF0B0F14),
    onSurface = Color(0xFF0B0F14),
)

@Composable
fun SilenceTheme(
    darkTheme: Boolean = true, // default dark — calm “quiet network” feel
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme || isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
