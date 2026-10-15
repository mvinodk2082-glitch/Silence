package com.silence.blocker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** AMOLED palette: true black, neutral greys, one desaturated sage accent. */
object SilencePalette {
    val Black = Color(0xFF000000)
    val Surface1 = Color(0xFF0A0A0A)
    val Surface2 = Color(0xFF121212)
    val Surface3 = Color(0xFF1A1A1A)
    val Outline = Color(0xFF262626)
    val TextPrimary = Color(0xFFEDEDED)
    val TextSecondary = Color(0xFFA1A1A1)
    val TextMuted = Color(0xFF6B6B6B)
    val Accent = Color(0xFF7FB8A4)
    val OnAccent = Color(0xFF0B1F18)
    val AccentContainer = Color(0xFF16231E)
    val Error = Color(0xFFE5807A)
    val OnError = Color(0xFF2B0E0C)
    val ErrorContainer = Color(0xFF241514)
}

private val AmoledColors = with(SilencePalette) {
    darkColorScheme(
        primary = Accent,
        onPrimary = OnAccent,
        primaryContainer = AccentContainer,
        onPrimaryContainer = Accent,
        secondary = TextSecondary,
        onSecondary = Black,
        secondaryContainer = Surface3,
        onSecondaryContainer = TextPrimary,
        tertiary = Accent,
        onTertiary = OnAccent,
        tertiaryContainer = AccentContainer,
        onTertiaryContainer = TextPrimary,
        background = Black,
        onBackground = TextPrimary,
        surface = Black,
        onSurface = TextPrimary,
        surfaceVariant = Surface3,
        onSurfaceVariant = TextSecondary,
        surfaceTint = Color.Transparent,
        surfaceDim = Black,
        surfaceBright = Surface3,
        surfaceContainerLowest = Black,
        surfaceContainerLow = Surface1,
        surfaceContainer = Surface1,
        surfaceContainerHigh = Surface2,
        surfaceContainerHighest = Surface3,
        inverseSurface = TextPrimary,
        inverseOnSurface = Black,
        inversePrimary = OnAccent,
        outline = TextMuted,
        outlineVariant = Outline,
        scrim = Black,
        error = Error,
        onError = OnError,
        errorContainer = ErrorContainer,
        onErrorContainer = Error,
    )
}

private val base = Typography()
private val SilenceTypography = Typography(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium,
)

private val SilenceShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)

/** Always dark AMOLED; dynamic color is intentionally not used so the palette stays consistent. */
@Composable
fun SilenceTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AmoledColors,
        typography = SilenceTypography,
        shapes = SilenceShapes,
        content = content,
    )
}
