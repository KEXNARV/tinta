package com.kevin.ink.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens semánticos del design system Pulsar con el acento ámbar de Ink.
 * Material3 solo cubre parte; el resto (texto terciario, superficies, acentos
 * sutiles, pista de gráficos…) vive aquí y se expone vía [LocalInkColors].
 */
class InkColors(
    val bgApp: Color,
    val bgSunken: Color,
    val surfaceCard: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val accentSubtle: Color,
    val accentBorder: Color,
    val chartTrack: Color,
    val isDark: Boolean,
)

/** Tema oscuro (por defecto en el diseño). */
val InkDarkColors = InkColors(
    bgApp = Color(0xFF0F1115),
    bgSunken = Color(0xFF0B0D10),
    surfaceCard = Color(0xFF14171C),
    surfaceRaised = Color(0xFF191D23),
    border = Color(0x14FFFFFF),       // blanco 8%
    borderStrong = Color(0x24FFFFFF), // blanco 14%
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFFA9B1BC),
    textTertiary = Color(0xFF7E8896),
    accent = Color(0xFFEAB45A),
    accentHover = Color(0xFFF2C878),
    accentPressed = Color(0xFFD29A3C),
    onAccent = Color(0xFF1C1305),
    accentSubtle = Color(0x24EAB45A), // ámbar 14%
    accentBorder = Color(0x6BEAB45A), // ámbar 42%
    chartTrack = Color(0x14FFFFFF),
    isDark = true,
)

/** Tema claro (listo para activarse más adelante). */
val InkLightColors = InkColors(
    bgApp = Color(0xFFF7F9FA),
    bgSunken = Color(0xFFEEF1F4),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0x1A0F1720),
    borderStrong = Color(0x2E0F1720),
    textPrimary = Color(0xFF11161C),
    textSecondary = Color(0xFF4A5462),
    textTertiary = Color(0xFF7A8493),
    accent = Color(0xFFB07E32),
    accentHover = Color(0xFFC28F3D),
    accentPressed = Color(0xFF946625),
    onAccent = Color(0xFFFFFFFF),
    accentSubtle = Color(0x21B07E32),
    accentBorder = Color(0x6BB07E32),
    chartTrack = Color(0x140F1720),
    isDark = false,
)
