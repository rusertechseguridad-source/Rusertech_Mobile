package com.rusertech.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RusertechColors = darkColorScheme(
    primary = TechGlowCyan, onPrimary = DeepSpaceTop,
    secondary = TechGlowBlue, onSecondary = DeepSpaceTop,
    tertiary = TechGlowGreen, onTertiary = DeepSpaceTop,
    background = DeepSpaceTop, onBackground = TextPrimary,
    surface = DeepSpaceBottom, onSurface = TextPrimary,
    surfaceVariant = SurfaceCard, onSurfaceVariant = TextSecondary,
    error = SOSRed, onError = TextPrimary, outline = SurfaceBorder
)

@Composable
fun RusertechTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RusertechColors, typography = RusertechTypography, content = content)
}
