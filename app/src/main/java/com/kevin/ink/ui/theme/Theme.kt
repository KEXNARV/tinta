package com.kevin.ink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalInkColors = staticCompositionLocalOf { InkDarkColors }

/** Acceso a los tokens Ink desde cualquier composable: `InkTheme.colors.accent`. */
object InkTheme {
    val colors: InkColors
        @Composable @ReadOnlyComposable get() = LocalInkColors.current
}

@Composable
fun InkTheme(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) InkDarkColors else InkLightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bgApp,
            onBackground = colors.textPrimary,
            surface = colors.surfaceCard,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.bgApp,
            onBackground = colors.textPrimary,
            surface = colors.surfaceCard,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
        )
    }
    CompositionLocalProvider(LocalInkColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = InkTypography, content = content)
    }
}
