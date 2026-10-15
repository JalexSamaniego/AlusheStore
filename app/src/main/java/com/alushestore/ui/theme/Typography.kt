package com.alushestore.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alushestore.R

/**
 * Tipografía oficial AlusheStore:
 * - Montserrat 600/700/800  → navegación, títulos, botones, badges, precios
 * - Playfair Display 400/700 → títulos editoriales / display grandes
 * - DM Sans 300/400/500     → cuerpo, párrafos, notas
 *
 * NOTA: Agrega los archivos .ttf en res/font/ con estos nombres exactos:
 *   montserrat_semibold.ttf, montserrat_bold.ttf, montserrat_extrabold.ttf
 *   playfair_regular.ttf, playfair_bold.ttf
 *   dmsans_light.ttf, dmsans_regular.ttf, dmsans_medium.ttf
 *
 * Si todavía no tienes los archivos, el sistema usa la fuente del sistema
 * y la app igual funciona. Se pueden agregar después sin cambiar nada más.
 */

// Descomentar cuando tengas los archivos en res/font/
/*
val Montserrat = FontFamily(
    Font(R.font.montserrat_semibold,   FontWeight.SemiBold),
    Font(R.font.montserrat_bold,       FontWeight.Bold),
    Font(R.font.montserrat_extrabold,  FontWeight.ExtraBold),
)

val PlayfairDisplay = FontFamily(
    Font(R.font.playfair_regular, FontWeight.Normal),
    Font(R.font.playfair_bold,    FontWeight.Bold),
)

val DmSans = FontFamily(
    Font(R.font.dmsans_light,   FontWeight.Light),
    Font(R.font.dmsans_regular, FontWeight.Normal),
    Font(R.font.dmsans_medium,  FontWeight.Medium),
)
*/

// Mientras no estén los archivos de fuente, usamos estas familias temporales
// que reflejan los pesos correctos:
val Montserrat      = FontFamily.Default
val PlayfairDisplay = FontFamily.Default
val DmSans          = FontFamily.Default

val AlusheTypography = Typography(

    // ── Display / Hero — Montserrat ExtraBold ────────────────────────────────
    displayLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),

    // ── Títulos de sección — Playfair Display ────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize   = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Normal,
        fontSize   = 22.sp,
        lineHeight = 28.sp
    ),

    // ── Títulos de pantalla y cards — Montserrat SemiBold ────────────────────
    titleLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize   = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Cuerpo — DM Sans ──────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Light,
        fontSize   = 12.sp,
        lineHeight = 16.sp
    ),

    // ── Labels / badges / botones — Montserrat Bold ───────────────────────────
    labelLarge = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Bold,
        fontSize   = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.ExtraBold,
        fontSize   = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.8.sp
    ),
)
