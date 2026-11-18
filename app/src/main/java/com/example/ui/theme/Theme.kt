package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = PrimaryBlueFg,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = PrimaryBlueFg,
    secondary = PrimaryBlueDark,
    onSecondary = PrimaryBlueFg,
    secondaryContainer = SlateSurfaceHigh,
    onSecondaryContainer = SlateText,
    tertiary = WarnAmber,
    onTertiary = Color.Black,
    background = SlateBg,
    onBackground = SlateText,
    surface = SlateSurface,
    onSurface = SlateText,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = SlateTextDim,
    outline = SlateBorder,
    outlineVariant = SlateBorderStrong,
    error = DangerRed,
    onError = Color.Black,
    errorContainer = DangerRedBg,
    onErrorContainer = DangerRed
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextDim,
    outline = LightBorder,
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp)
)

@Composable
fun TmdbEmbedTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
