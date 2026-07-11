package com.wavora.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ===== Wavora Brand Colors — source of truth =====
val wavoraPrimary = Color(0xFFA259FF)       // Electric Purple — main accent
val wavoraSecondary = Color(0xFF00D4FF)     // Neon Cyan — secondary accent / active state
val wavoraAccentDark = Color(0xFF1A0B2E)
val wavoraBackground = Color(0xFF0B0B0F)
val wavoraSurface = Color(0xFF12121A)
val wavoraBorder = Color(0xFF1F1F2E)
val wavoraTextPrimary = Color(0xFFFFFFFF)
val wavoraTextSecondary = Color(0xFFB3B3C6)
val wavoraTextDisabled = Color(0xFF6B6B7A)
val wavoraGradientStart = Color(0xFFA259FF)
val wavoraGradientMid = Color(0xFF6A5CFF)
val wavoraGradientEnd = Color(0xFF00D4FF)

// Single shared brush instance for the 3-stop brand gradient (violet -> mid -> cyan).
// Used both for progress-bar/track fills (as a background) and, via
// Modifier.wavoraIconGradient() in UIExt.kt, to paint icon glyphs directly with no
// background shape behind them.
val wavoraIconGradientBrush: Brush =
    Brush.horizontalGradient(
        colors = listOf(wavoraGradientStart, wavoraGradientMid, wavoraGradientEnd),
    )

// ===== Material Theme aliases (reference brand tokens) =====
val md_theme_dark_primary = wavoraPrimary
val md_theme_dark_onPrimary = Color(0xFFFFFFFF)
val md_theme_dark_primaryContainer = Color(0xFF2D1050)
val md_theme_dark_onPrimaryContainer = Color(0xFFDAE2FF)
val md_theme_dark_secondary = wavoraSecondary
val md_theme_dark_onSecondary = Color(0xFF2A3042)
val md_theme_dark_secondaryContainer = wavoraAccentDark
val md_theme_dark_onSecondaryContainer = Color(0xFFDCE2F9)
val md_theme_dark_tertiary = Color(0xFFE1BBDD)
val md_theme_dark_onTertiary = Color(0xFF412741)
val md_theme_dark_tertiaryContainer = Color(0xFF5A3D59)
val md_theme_dark_onTertiaryContainer = Color(0xFFFED7F9)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = wavoraBackground
val md_theme_dark_onBackground = Color(0xFFFFFFFF)
val md_theme_dark_surface = wavoraSurface
val md_theme_dark_onSurface = Color(0xFFFFFFFF)
val md_theme_dark_surfaceVariant = wavoraBorder
val md_theme_dark_onSurfaceVariant = wavoraTextSecondary
val md_theme_dark_outline = wavoraBorder
val md_theme_dark_inverseOnSurface = Color(0xFF1B1B1F)
val md_theme_dark_inverseSurface = Color(0xFFFFFFFF)
val md_theme_dark_inversePrimary = Color(0xFF3A5BA9)
val md_theme_dark_shadow = Color(0xFF000000)
val md_theme_dark_surfaceTint = wavoraSecondary
val md_theme_dark_outlineVariant = wavoraBorder
val md_theme_dark_scrim = Color(0xFF000000)

// ===== Utility colors =====
val colorPrimaryDark = Color(0x19000000)
val back_button_color = Color(0x197E7E7E)
val checkedFilterColor = Color(0xff4d4848)
val shimmerBackground = Color(0x7E383737)
val shimmerLine = Color(0xFF4D4848)
val overlay = Color(0x32242424)
val blackMoreOverlay = Color(0x8f242424)
// seed: replaced by wavoraSecondary for brand consistency (was #8ECAE6, an off-brand light blue)
val seed = wavoraSecondary
val bottomBarSeedDark = Color(0xff53a7d0)
val customGray = Color(0x40ECECEC)
val customDarkGray = Color(0x40383535)
val white = Color(0xFFFFFFFF)
val transparent = Color(0x00000000)
