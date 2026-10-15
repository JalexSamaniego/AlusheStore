package com.alushestore.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Light: Cream + Green + Ink ────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary             = AlusheInk,           // Header, botones primarios → negro de la marca
    onPrimary           = AlusheWhite,
    primaryContainer    = AlusheGreenSurface,  // Chips activos, containers de acción
    onPrimaryContainer  = AlusheGreenDark,

    secondary           = AlusheGreen,         // Acciones secundarias → verde WhatsApp
    onSecondary         = AlusheWhite,
    secondaryContainer  = AlusheGreenSurface,
    onSecondaryContainer = AlusheInk,

    tertiary            = AlusheGreenDark,
    onTertiary          = AlusheWhite,

    background          = AlusheCream,         // Fondo general → crema de la marca
    onBackground        = AlusheInk,

    surface             = AlusheWhite,         // Cards, modales
    onSurface           = AlusheInk,
    surfaceVariant      = AlusheCreamDark,     // Secciones alternas, stat cards
    onSurfaceVariant    = AlusheInkMid,

    outline             = AlusheBorder,
    outlineVariant      = AlusheCreamDark,

    error               = AlusheRed,
    errorContainer      = AlusheRedSurface,
    onError             = AlusheWhite,
    onErrorContainer    = AlusheRed,
)

// ── Dark ──────────────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary             = AlusheCreamDarkMode,
    onPrimary           = AlusheInk,
    primaryContainer    = Color(0xFF1A3D28),
    onPrimaryContainer  = AlusheGreen,

    secondary           = AlusheGreen,
    onSecondary         = AlusheInk,
    secondaryContainer  = Color(0xFF0F2E1C),
    onSecondaryContainer = AlusheGreen,

    background          = AlusheSurfaceDark,
    onBackground        = AlusheCreamDarkMode,

    surface             = AlusheCardDark,
    onSurface           = AlusheCreamDarkMode,
    surfaceVariant      = Color(0xFF2A2A22),
    onSurfaceVariant    = Color(0xFFB8B79A),

    outline             = AlusheBorderDark,
    outlineVariant      = Color(0xFF2A2A22),

    error               = Color(0xFFFF6B8A),
    errorContainer      = Color(0xFF3D0014),
    onError             = AlusheInk,
    onErrorContainer    = Color(0xFFFF6B8A),
)

@Composable
fun AlusheStoreTheme(
    darkTheme: Boolean = false,   // Siempre light — la identidad es el crema
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AlusheInk.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AlusheTypography,
        content     = content
    )
}
