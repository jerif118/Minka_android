
package com.example.prueba1.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/* ─────────────────────   CORE BRAND COLORS   ───────────────────── */
val Blue40         = Color(0xFF00629E)
val Blue80         = Color(0xFF8ACAFF)
val Magenta40      = Color(0xFFB81D57)
val Magenta80      = Color(0xFFFFB1CA)
val Neutral10      = Color(0xFF141414)  // casi negro
val Neutral98      = Color(0xFFFAFAFA)  // casi blanco
val MiFondoClaro   = Color(0xFFFFFBF5)
val Teal40 = Color(0xFF006A6A)
val Teal80 = Color(0xFF5DD5D5)

/* ─────────────────── COMPLETE SCHEMES (light / dark) ────────────────── */

// 1) PALETA PARA MODO CLARO
val LightColors = lightColorScheme(
    background           = Color.White,
    surface              = Color.White,
    primary              = Blue40,
    onPrimary            = Color.White,
    primaryContainer     = Blue80,
    onPrimaryContainer   = Neutral10,

    secondary            = Blue80,
    onSecondary          = Color.White,
    secondaryContainer   = Blue80,
    onSecondaryContainer = Neutral10,

    //background           = Neutral98,
    onBackground         = Neutral10,
    //surface              = Neutral98,
    onSurface            = Neutral10,

    error                = Color(0xFFB00020),
    onError              = Color.White
    // Si necesitas más tokens (surfaceVariant, outline, etc.), agrégalos aquí.
)

// 2) PALETA PARA MODO OSCURO
val DarkColors = darkColorScheme(
    primary              = Blue80,
    onPrimary            = Blue40,
    primaryContainer     = Blue40,
    onPrimaryContainer   = Blue80,

    secondary            = Blue40,
    onSecondary          = Blue40,
    secondaryContainer   = Blue40,
    onSecondaryContainer = Blue80,

    background           = Neutral10,
    onBackground         = Neutral98,
    surface              = Neutral10,
    onSurface            = Neutral98,

    error                = Color(0xFFCF6679),
    onError              = Color.Black
    // Agrega aquí los demás tokens que te hagan falta (surfaceVariant, outline, etc.).
)