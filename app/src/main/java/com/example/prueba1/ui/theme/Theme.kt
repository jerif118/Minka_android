package com.example.prueba1.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// IMPORTAMOS LAS PALETAS que acabamos de definir en Color.kt:
import com.example.prueba1.ui.theme.LightColors
import com.example.prueba1.ui.theme.DarkColors

@Composable
fun Prueba1Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    //dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Seleccionamos la paleta dinámica si Android >= 12 y dynamicColor == true.
    // En caso contrario, usamos LightColors o DarkColors según darkTheme.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,  // Si ya tienes tipografías predefinidas
        shapes      = Shapes(),
        content     = content
    )
}