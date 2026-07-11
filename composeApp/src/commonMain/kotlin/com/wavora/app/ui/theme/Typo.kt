package com.wavora.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.poppins_bold
import wavora.composeapp.generated.resources.poppins_medium

/**
 * CompositionLocal that provides the app's Typography.
 * Use [LocalAppTypography.current] instead of calling [typo()] directly —
 * [typo()] re-creates FontFamily + Typography on every call (428× in the codebase),
 * whereas the CompositionLocal reads a single already-computed instance.
 */
val LocalAppTypography: ProvidableCompositionLocal<Typography> =
    compositionLocalOf { error("No Typography provided — wrap your root composable in AppTheme") }

@Composable
fun fontFamily(): FontFamily =
    FontFamily(
        // Normal is intentionally backed by poppins_medium (not poppins_regular) — this is
        // Wavora's existing visual identity for body text and is left untouched.
        Font(Res.font.poppins_medium, FontWeight.Normal, FontStyle.Normal),
        // poppins_bold.ttf was already bundled in resources but never referenced anywhere, so
        // every FontWeight.Bold/SemiBold TextStyle below was rendered as OS-synthesized fake
        // bold instead of the real bundled typeface. Registering it here fixes FontWeight.Bold
        // exactly; FontWeight.SemiBold (there's no dedicated semibold asset bundled) now matches
        // against this closer neighbor instead of only ever synthesizing off Normal.
        Font(Res.font.poppins_bold, FontWeight.Bold, FontStyle.Normal),
    )

@Composable
fun typo(): Typography {
    val fontFamily = fontFamily()

    val typo =
        Typography(
            /***
             * This typo().is use for the title of the Playlist, Artist, Song, Album, etc. in Home, Mood, Genre, Playlist, etc.
             */
            titleSmall =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            titleMedium =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            titleLarge =
                TextStyle(
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            bodySmall =
                TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = wavoraTextSecondary,
                ),
            bodyMedium =
                TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = wavoraTextSecondary,
                ),
            bodyLarge =
                TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = wavoraTextSecondary,
                ),
            displayLarge =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = fontFamily,
                    color = wavoraTextSecondary,
                ),
            headlineMedium =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            headlineLarge =
                TextStyle(
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = Color.White,
                ),
            labelMedium =
                TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = wavoraTextSecondary,
                ),
            labelSmall =
                TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = fontFamily,
                    color = wavoraTextSecondary,
                ),
            // ...
        )
    return typo
}